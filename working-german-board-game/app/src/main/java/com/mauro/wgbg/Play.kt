package com.mauro.wgbg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
            vertices = legal.filter { it.type == ActionType.BUILD_SETTLEMENT || it.type == ActionType.BUILD_CITY }.map { it.payload.toInt() }.toSet(),
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
fun prompt(s: GameState, legal: List<Action>, names: List<String>): String = when {
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
    val types = legal.map { it.type }.toSet()
    Column(
        modifier.widthIn(max = 520.dp).background(PANEL, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(prompt(s, legal, names), color = INK_LIGHT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You: ${s.victoryPoints(me)} VP", color = INK_LIGHT, fontSize = 13.sp)
            MiniHand(List(CARD_TYPES) { s.hand(me, it) }, s.devCount(me))
        }
        val fortune = (0 until DevCard.COUNT).filter { s.devHand[me * DevCard.COUNT + it] > 0 }
        if (fortune.isNotEmpty()) Text("Fortune cards: " + fortune.joinToString(", ") { "${s.devHand[me * DevCard.COUNT + it]} ${FORTUNE[it]}" }, color = INK_LIGHT, fontSize = 12.sp)

        when (sheet) {
            null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable fun single(t: ActionType, label: String, primary: Boolean = false) {
                    legal.firstOrNull { it.type == t }?.let { a -> MoveButton(label, primary) { onMove(a) } }
                }
                single(ActionType.ROLL, "Roll", primary = true)
                if (ActionType.DISCARD in types) for (a in legal.filter { it.type == ActionType.DISCARD })
                    MoveButton("Discard ${RES[a.discardResource]}") { onMove(a) }
                single(ActionType.BUY_DEVELOPMENT_CARD, "Buy Fortune card")
                single(ActionType.PLAY_KNIGHT_CARD, "Play Sentinel")
                single(ActionType.PLAY_ROAD_BUILDING, "Play Trailblazers")
                if (ActionType.PLAY_YEAR_OF_PLENTY in types) MoveButton("Play Windfall…") { sheet = "yop" }
                if (ActionType.PLAY_MONOPOLY in types) MoveButton("Play Corner the Market…") { sheet = "mono" }
                single(ActionType.END_TURN, "End turn", primary = ActionType.ROLL !in types && legal.none { it.type == ActionType.BUILD_SETTLEMENT || it.type == ActionType.BUILD_CITY })
            }
            "yop" -> Picker("Windfall: take", legal.filter { it.type == ActionType.PLAY_YEAR_OF_PLENTY }
                .map { a -> (RES[a.yopFirst] + if (a.yopSecond >= 0) " + ${RES[a.yopSecond]}" else "") to a }, onMove) { sheet = null }
            "mono" -> Picker("Corner the Market: everyone hands you all their", legal.filter { it.type == ActionType.PLAY_MONOPOLY }
                .map { a -> RES[a.payload.toInt()] to a }, onMove) { sheet = null }
        }
    }
}

@Composable
private fun MoveButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0B23A), contentColor = Color(0xFF2B2118))) { Text(label) }
    else FilledTonalButton(onClick = onClick) { Text(label) }
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
    else -> "Do this?"
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
    if (a.type != ActionType.MOVE_ROBBER) "Confirm"
    else if (a.robberVictim < 0) "Move it (nobody to rob)"
    else "Rob ${names[a.robberVictim]} (${s.cardCount(a.robberVictim)} cards)"
