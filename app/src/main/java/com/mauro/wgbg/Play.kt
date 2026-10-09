package com.mauro.wgbg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wgbg.engine.Action
import wgbg.engine.ActionType
import wgbg.engine.Board
import wgbg.engine.CARD_TYPES
import wgbg.engine.DevCard
import wgbg.engine.GameState
import wgbg.engine.Phase
import kotlin.math.hypot
import kotlin.math.sqrt

// ------------------------------------------------------------------ board targets (issue #5)

/** Where the human can act on the board right now, derived from the legal moves. */
class Targets(val vertices: Set<Int>, val edges: Set<Int>, val hexes: Set<Int>) {
    val isEmpty get() = vertices.isEmpty() && edges.isEmpty() && hexes.isEmpty()

    companion object {
        fun of(legal: List<Action>) = Targets(
            vertices = legal.filter { it.type == ActionType.BUILD_SETTLEMENT || it.type == ActionType.BUILD_CITY }.map { it.payload.toInt() }.toSet() +
                legal.filter { it.type in CK_VERTEX_KINDS }.map { ckVertex(it) }.filter { it >= 0 },
            edges = legal.filter { it.type == ActionType.BUILD_ROAD }.map { it.payload.toInt() }.toSet(),
            hexes = legal.filter { it.type == ActionType.MOVE_ROBBER }.map { it.robberHex }.toSet(),
        )
    }
}

private val SQRT3 = sqrt(3f)
fun vertexLattice(b: Board, v: Int) = b.topology.vertexPos[v].let { Offset(it.x * SQRT3 / 2f, it.y / 2f) }
fun edgeLattice(b: Board, e: Int) = b.topology.edgeVertices[e].let { (vertexLattice(b, it[0]) + vertexLattice(b, it[1])) / 2f }
fun hexLattice(b: Board, h: Int) = b.topology.hexVertices[h].map { vertexLattice(b, it) }.let { ps ->
    Offset(ps.map { it.x }.average().toFloat(), ps.map { it.y }.average().toFloat())
}

/**
 * The legal moves a tap at lattice point [p] means: a build at the nearest glowing corner or
 * edge, or the bandit moves for a tile (several when there is a choice of victim). Empty if the
 * tap is not near any target.
 */
fun movesAt(b: Board, legal: List<Action>, p: Offset): List<Action> {
    var best: List<Action> = emptyList()
    var bestD = 0.42f // hex sides; generous enough for fingers, tight enough to be unambiguous
    for (a in legal) {
        val at = when (a.type) {
            ActionType.BUILD_SETTLEMENT, ActionType.BUILD_CITY -> vertexLattice(b, a.payload.toInt())
            ActionType.BUILD_ROAD -> edgeLattice(b, a.payload.toInt())
            ActionType.MOVE_ROBBER -> hexLattice(b, a.robberHex)
            in CK_VERTEX_KINDS -> ckVertex(a).takeIf { it >= 0 }?.let { vertexLattice(b, it) } ?: continue
            else -> continue
        }
        val d = hypot(at.x - p.x, at.y - p.y) * if (a.type == ActionType.MOVE_ROBBER) 0.5f else 1f // tiles are big targets
        if (d < bestD - 1e-4f) { bestD = d; best = listOf(a) }
        else if (kotlin.math.abs(d - bestD) < 1e-4f) best = best + a
    }
    return best
}

// ------------------------------------------------------------------ the human's controls

private val RES = RESOURCE_NAMES
private val FORTUNE = listOf("Sentinel", "Windfall", "Corner the Market", "Trailblazers", "Landmark")
private val PANEL = Color(0xE6141210)
private val INK_LIGHT = Color(0xFFEDE6D8)

/** One-line instruction for what the human should do now. */
fun prompt(s: GameState, legal: List<Action>, names: List<String>): String = ckPrompt(s, legal) ?: when {
    s.phase == Phase.BUILD_INITIAL_SETTLEMENT -> "Tap a glowing corner to place a homestead"
    s.phase == Phase.BUILD_INITIAL_ROAD -> "Tap a glowing edge to place a road"
    s.phase == Phase.MOVE_ROBBER -> "Tap a tile to move the bandit"
    s.phase == Phase.DISCARD -> "Too many cards on a 7: discard ${s.discardOwed[s.actingPlayer]}"
    s.phase == Phase.DECIDE_TRADE -> "${names[s.currentPlayer]} offers ${cards { s.tradeOffer[it] }} for ${cards { s.tradeOffer[CARD_TYPES + it] }}"
    s.phase == Phase.DECIDE_COUNTER -> "${names[s.counterFrom]} made a counter-offer"
    s.phase == Phase.DECIDE_ACCEPTEES -> "Pick who to trade with, or cancel"
    legal.any { it.type == ActionType.ROLL } -> "Your turn: roll the dice"
    s.freeRoads > 0 -> "Trailblazers: tap glowing edges for ${s.freeRoads} free road${if (s.freeRoads > 1) "s" else ""}"
    legal.any { it.type == ActionType.BUILD_ROAD || it.type == ActionType.BUILD_SETTLEMENT || it.type == ActionType.BUILD_CITY } ->
        "Tap a glowing spot to build, or trade, or end your turn"
    else -> "Trade, buy a Fortune card, or end your turn"
}

private fun cards(n: (Int) -> Int) = (0 until CARD_TYPES).filter { n(it) > 0 }.joinToString(", ") { "${n(it)} ${RES[it]}" }.ifEmpty { "nothing" }

/**
 * Buttons for everything that isn't a tap on the board: roll, end turn, Fortune cards, bank and
 * discarding (trades live in [TradePanel]). Every button sends a move from [legal] (or a trade
 * offer the engine validates), so nothing here can bypass the rules.
 */
@Composable
fun HumanControls(
    s: GameState,
    me: Int,
    legal: List<Action>,
    names: List<String>,
    onMove: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<String?>(null) } // which picker is open
    var ckChoices by remember { mutableStateOf<Pair<String, List<Action>>?>(null) }
    val types = legal.map { it.type }.toSet()
    // Info on the left, the buttons in a column hugging the right edge with the main one (Roll /
    // End turn) at the very bottom: everything within reach of a right thumb.
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Board A: the prompt, points and hand live elsewhere (TurnPrompt, PlayerStrip, HandRow); this
        // panel only appears for Fortune cards and the pickers.
        val fortune = (0 until DevCard.COUNT).filter { s.devHand[me * DevCard.COUNT + it] > 0 }
        if (fortune.isNotEmpty() || sheet != null) Column(
            Modifier.widthIn(max = 300.dp).brassEdgedWood().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (fortune.isNotEmpty()) Text("Fortune cards: " + fortune.joinToString(", ") { "${s.devHand[me * DevCard.COUNT + it]} ${FORTUNE[it]}" }, color = INK_LIGHT, fontSize = 12.sp)
            when (sheet) {
                "yop" -> Picker("Windfall: take", legal.filter { it.type == ActionType.PLAY_YEAR_OF_PLENTY }
                    .map { a -> (RES[a.yopFirst] + if (a.yopSecond >= 0) " + ${RES[a.yopSecond]}" else "") to a }, onMove) { sheet = null }
                "mono" -> Picker("Corner the Market: everyone hands you all their", legal.filter { it.type == ActionType.PLAY_MONOPOLY }
                    .map { a -> RES[a.payload.toInt()] to a }, onMove) { sheet = null }
                "ck" -> ckChoices?.let { (label, acts) ->
                    Picker(label, acts.map { a -> (if (a.type == wgbg.engine.CkActionType.PLAY_PROGRESS) progressOption(a) else if (a.type == ActionType.MARITIME_TRADE) bankLabel(a) else ckLabel(s, a, names) ?: a.toString()) to a }, onMove) { sheet = null }
                }
            }
        }
        val buttonTypes = setOf(ActionType.ROLL, ActionType.END_TURN, ActionType.DISCARD, ActionType.BUY_DEVELOPMENT_CARD,
            ActionType.PLAY_KNIGHT_CARD, ActionType.PLAY_ROAD_BUILDING, ActionType.PLAY_YEAR_OF_PLENTY, ActionType.PLAY_MONOPOLY)
        if (sheet == null && (types.any { it in buttonTypes } || ckButtonGroups(s, legal, names).isNotEmpty())) Column(
            Modifier.width(176.dp).testTag("thumb-buttons"),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            @Composable fun single(t: ActionType, label: String, primary: Boolean = false) {
                legal.firstOrNull { it.type == t }?.let { a -> MoveButton(label, primary) { onMove(a) } }
            }
            if (ActionType.DISCARD in types) for (a in legal.filter { it.type == ActionType.DISCARD })
                MoveButton(if (a.discardResource < 0) "Discard at random" else "Discard ${ALL_CARD_NAMES[a.discardResource]}") { onMove(a) }
            single(ActionType.BUY_DEVELOPMENT_CARD, "Buy Fortune card")
            single(ActionType.PLAY_KNIGHT_CARD, "Play Sentinel")
            single(ActionType.PLAY_ROAD_BUILDING, "Play Trailblazers")
            if (ActionType.PLAY_YEAR_OF_PLENTY in types) MoveButton("Play Windfall…") { sheet = "yop" }
            if (ActionType.PLAY_MONOPOLY in types) MoveButton("Play Corner the Market…") { sheet = "mono" }
            // Age of Chivalry moves that aren't board taps: one button per kind (or card), a picker if it has choices.
            for ((label, acts) in ckButtonGroups(s, legal, names)) {
                if (acts.size == 1) MoveButton(label) { onMove(acts[0]) }
                else MoveButton("$label…") { ckChoices = label to acts; sheet = "ck" }
            }
            // The main move last, in the bottom-right corner.
            single(ActionType.ROLL, "Roll", primary = true)
            single(ActionType.END_TURN, "End turn", primary = ActionType.ROLL !in types && legal.none { it.type == ActionType.BUILD_SETTLEMENT || it.type == ActionType.BUILD_CITY })
        }
    }
}

@Composable
private fun MoveButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    // Primary (Roll / End turn): a big brass plate; the rest: wood with a brass rim.
    if (primary) BrassButton(label, "move-$label", Modifier.fillMaxWidth().height(54.dp), size = 18.sp, onClick = onClick)
    else WoodButton(label, "move-$label", Modifier.fillMaxWidth(), onClick = onClick)
}

@Composable
private fun Picker(title: String, options: List<Pair<String, Action>>, onMove: (Action) -> Unit, close: () -> Unit) {
    Text(title, color = INK_LIGHT, fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, a) in options) MoveButton(label) { close(); onMove(a) }
        MoveButton("Back") { close() }
    }
}

// ------------------------------------------------------------------ confirming board taps

/** The question asked before a tapped board move is made. */
fun confirmTitle(s: GameState, a: Action): String = when (a.type) {
    ActionType.BUILD_SETTLEMENT -> if (s.phase == Phase.BUILD_INITIAL_SETTLEMENT) "Place your homestead here?" else "Build a homestead here?"
    ActionType.BUILD_CITY -> "Upgrade this homestead to a town?"
    ActionType.BUILD_ROAD -> if (s.phase == Phase.BUILD_INITIAL_ROAD || s.freeRoads > 0) "Place a free road here?" else "Build a road here?"
    ActionType.MOVE_ROBBER -> "Move the bandit here?"
    else -> "Choose what to do here"
}

/** A warning worth a second look (the bandit on your own land), or null. */
fun confirmWarning(s: GameState, me: Int, a: Action): String? {
    if (a.type != ActionType.MOVE_ROBBER) return null
    val h = a.robberHex
    val mine = s.board.topology.hexVertices[h].any { s.vertexOwner[it] == me }
    if (!mine) return null
    val r = s.board.hexResource[h]
    val what = if (r in 0 until CARD_TYPES) "your own ${RES[r]}" else "nothing"
    return "Careful: this tile touches your own buildings. The bandit would block $what."
}

/** Button text for one confirming choice. */
fun confirmLabel(s: GameState, a: Action, names: List<String>): String =
    ckLabel(s, a, names) ?: if (a.type != ActionType.MOVE_ROBBER) "Confirm"
    else if (a.robberVictim < 0) "Move it (nobody to rob)"
    else "Rob ${names[a.robberVictim]} (${s.cardCount(a.robberVictim)} cards)"
