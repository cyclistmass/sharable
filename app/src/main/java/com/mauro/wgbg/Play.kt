package com.mauro.wgbg

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
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
        /** The glowing spots for a board card's choices (see [cardSpotIndex]). */
        fun ofCards(acts: List<Action>, second: Boolean = false): Targets {
            fun idx(k: CardSpot) = acts.filter { cardSpotKind(it.a) == k }.map { cardSpotIndex(it, second) }.toSet()
            return Targets(idx(CardSpot.VERTEX), idx(CardSpot.EDGE), idx(CardSpot.HEX))
        }

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
/** Where on the board (lattice units) a player taps for move [a], or null for moves that aren't board taps. */
fun spotOf(b: Board, a: Action, second: Boolean = false): Offset? = when (a.type) {
    wgbg.engine.CkActionType.PLAY_PROGRESS -> cardSpotKind(a.a)?.let { k ->
        val i = cardSpotIndex(a, second)
        when (k) { CardSpot.VERTEX -> vertexLattice(b, i); CardSpot.EDGE -> edgeLattice(b, i); CardSpot.HEX -> hexLattice(b, i) }
    }
    ActionType.BUILD_SETTLEMENT, ActionType.BUILD_CITY -> vertexLattice(b, a.payload.toInt())
    ActionType.BUILD_ROAD -> edgeLattice(b, a.payload.toInt())
    ActionType.MOVE_ROBBER -> hexLattice(b, a.robberHex)
    in CK_VERTEX_KINDS -> ckVertex(a).takeIf { it >= 0 }?.let { vertexLattice(b, it) }
    else -> null
}

/** How near a tap must land to a spot, in hex sides: generous enough for fingers, tight enough to be unambiguous. */
const val TAP_RADIUS = 0.42f

fun movesAt(b: Board, legal: List<Action>, p: Offset, second: Boolean = false): List<Action> {
    var best: List<Action> = emptyList()
    var bestD = TAP_RADIUS
    for (a in legal) {
        val at = spotOf(b, a, second) ?: continue
        val tile = a.type == ActionType.MOVE_ROBBER || (a.type == wgbg.engine.CkActionType.PLAY_PROGRESS && cardSpotKind(a.a) == CardSpot.HEX)
        val d = hypot(at.x - p.x, at.y - p.y) * if (tile) 0.5f else 1f // tiles are big targets
        if (d < bestD - 1e-4f) { bestD = d; best = listOf(a) }
        else if (kotlin.math.abs(d - bestD) < 1e-4f) best = best + a
    }
    return best
}

// ------------------------------------------------------------------ the human's controls

private val RES = RESOURCE_NAMES
internal val FORTUNE = listOf("Sentinel", "Windfall", "Corner the Market", "Trailblazers", "Landmark")

/** What each Fortune card does (same order as [FORTUNE]). */
val FORTUNE_HINTS = listOf(
    "Move the bandit and take a card from a player next to it. Three or more make your Army.",
    "Take any 2 resources from the bank.",
    "Name a resource: everyone gives you all of theirs.",
    "Build 2 roads for free.",
    "Worth 1 point (kept hidden until the end).",
)
private val PANEL = Color(0xE6141210)
private val INK_LIGHT = Color(0xFFEDE6D8)

/** One-line instruction for what the human should do now. */
fun prompt(s: GameState, legal: List<Action>, names: List<String>): String =
    if (legal.isEmpty() && !s.isOver) "Waiting for ${names[s.actingPlayer]}…" else ckPrompt(s, legal, names) ?: when {
    // Age of Chivalry: the second piece placed is a town, straight onto an empty corner.
    s.phase == Phase.BUILD_INITIAL_SETTLEMENT -> "Tap a glowing corner to place a ${if (legal.any { it.type == ActionType.BUILD_CITY }) "town" else "homestead"}"
    s.phase == Phase.BUILD_INITIAL_ROAD -> "Tap a glowing edge to place a road"
    s.phase == Phase.MOVE_ROBBER -> "Tap a tile to move the bandit"
    s.phase == Phase.DISCARD -> "Too many cards on a 7: discard ${s.discardOwed[s.actingPlayer]}"
    s.phase == Phase.DECIDE_TRADE -> "${names[s.currentPlayer]} offers ${cards { s.tradeOffer[it] }} for ${cards { s.tradeOffer[CARD_TYPES + it] }}"
    s.phase == Phase.DECIDE_COUNTER -> "${names[s.counterFrom]} made a counter-offer"
    s.phase == Phase.DECIDE_ACCEPTEES -> "Pick who to trade with, or cancel"
    legal.any { it.type == ActionType.ROLL } -> "Your turn: roll the dice"
    // Trailblazers, Pathfinders or Envoy (moving your own road): place the free road(s) before anything else.
    s.freeRoads > 0 -> "Place ${if (s.freeRoads > 1) "${s.freeRoads} free roads" else "your free road"} first: tap a glowing edge"
    legal.any { it.type == ActionType.BUILD_ROAD || it.type == ActionType.BUILD_SETTLEMENT || it.type == ActionType.BUILD_CITY } ->
        "Tap a glowing spot to build, or trade, or end your turn"
    else -> if (s.ck != null) "Trade, improve your towns, or end your turn" else "Trade, buy a Fortune card, or end your turn"
}

private fun cards(n: (Int) -> Int) = (0 until CARD_TYPES).filter { n(it) > 0 }.joinToString(", ") { "${n(it)} ${RES[it]}" }.ifEmpty { "nothing" }

/**
 * Why a tap on the board did nothing, when that's worth saying: moving the bandit onto the tile it
 * already stands on (it has to move). Null otherwise.
 */
fun boardTapHint(b: Board, legal: List<Action>, robberHex: Int, p: Offset): String? {
    if (robberHex < 0 || legal.none { it.type == ActionType.MOVE_ROBBER }) return null
    return if ((hexLattice(b, robberHex) - p).getDistance() < 0.75f) "The bandit is already here: pick another tile" else null
}

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
    /** A board card's choices: picked by tapping the board instead of from a list (Mauro, 2026-10-11). */
    onBoardPick: (List<Action>) -> Unit = {},
    /** The Trade button, set beside Roll / End turn in the bottom-right corner (Mauro: "next to each other"). */
    tradeButton: (@Composable () -> Unit)? = null,
    /** Why you must discard, when it isn't a rolled 7 (Wrecker played against you); null for a 7. */
    discardCause: String? = null,
) {
    var sheet by remember { mutableStateOf<String?>(null) } // which picker is open
    var ckChoices by remember { mutableStateOf<Pair<String, List<Action>>?>(null) }
    val types = legal.map { it.type }.toSet()
    // The buttons in a column hugging the right edge with the main one (Roll / End turn) at the very bottom, within
    // reach of a right thumb. Every card sheet (pick, give, discard, name a card) sits in one place: bottom centre,
    // the lower third of the screen, like the trade chest (Mauro: "same card puller/giver ... for thumb reach").
    Box(modifier) {
        // Board A: the prompt, points and hand live elsewhere (TurnPrompt, PlayerStrip, HandRow); this
        // panel only appears for Fortune cards and the pickers.
        val fortune = (0 until DevCard.COUNT).filter { s.devHand[me * DevCard.COUNT + it] > 0 }
        if (fortune.isNotEmpty() || sheet != null) Column(
            (if (sheet != null) Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).chest()
            else Modifier.align(Alignment.BottomEnd).padding(end = 184.dp).widthIn(max = 300.dp).brassEdgedWood().padding(12.dp)).testTag("card-sheet"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Each Fortune card you hold, with what it does (Mauro: "add some hints on what a card does").
            if (fortune.isNotEmpty() && sheet == null) for (c in fortune) Text("${s.devHand[me * DevCard.COUNT + c]} ${FORTUNE[c]}: ${FORTUNE_HINTS[c]}",
                color = INK_LIGHT, fontSize = 12.sp, modifier = Modifier.testTag("fortune-hint-$c"))
            when (sheet) {
                "yop" -> Picker("Windfall: ${FORTUNE_HINTS[DevCard.YEAR_OF_PLENTY]}", legal.filter { it.type == ActionType.PLAY_YEAR_OF_PLENTY }
                    .map { a -> (RES[a.yopFirst] + if (a.yopSecond >= 0) " + ${RES[a.yopSecond]}" else "") to a }, onMove) { sheet = null }
                "mono" -> Picker("Corner the Market: everyone hands you all of the one you name:", legal.filter { it.type == ActionType.PLAY_MONOPOLY }
                    .map { a -> RES[a.payload.toInt()] to a }, onMove) { sheet = null }
                "ck" -> ckChoices?.let { (label, acts) ->
                    val hint = acts.firstOrNull { it.type == wgbg.engine.CkActionType.PLAY_PROGRESS }?.let { progressHint(it.a) }
                    Picker(if (hint.isNullOrEmpty()) label else "$label: $hint", acts.map { a -> (if (a.type == wgbg.engine.CkActionType.PLAY_PROGRESS) progressOption(s, a, names) else if (a.type == ActionType.MARITIME_TRADE) bankLabel(a) else ckLabel(s, a, names) ?: a.toString()) to a }, onMove) { sheet = null }
                }
            }
        }
        // On a 7: pick all the cards first, then discard them with one press (playtest: a tap per card,
        // on buttons that moved as card types ran out, lost the wrong card with no undo).
        if (ActionType.DISCARD in types) { Box(Modifier.align(Alignment.BottomCenter)) { DiscardSheet(s, me, legal, onMove, discardCause) }; return@Box }
        if (wgbg.engine.CkActionType.CHOOSE_CARD in types) {
            CardChoiceSheet(s, me, legal.filter { it.type == wgbg.engine.CkActionType.CHOOSE_CARD }, names, onMove, Modifier.align(Alignment.BottomCenter)); return@Box
        }
        val buttonTypes = setOf(ActionType.ROLL, ActionType.END_TURN, ActionType.BUY_DEVELOPMENT_CARD,
            ActionType.PLAY_KNIGHT_CARD, ActionType.PLAY_ROAD_BUILDING, ActionType.PLAY_YEAR_OF_PLENTY, ActionType.PLAY_MONOPOLY)
        if (sheet == null && (types.any { it in buttonTypes } || ckButtonGroups(s, legal, names).isNotEmpty() || tradeButton != null)) Column(
            Modifier.align(Alignment.BottomEnd).width(176.dp).testTag("thumb-buttons"),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            @Composable fun single(t: ActionType, label: String, primary: Boolean = false) {
                legal.firstOrNull { it.type == t }?.let { a -> MoveButton(label, primary) { onMove(a) } }
            }
            // Buying a Fortune card lives in the build menu on the right edge now.
            single(ActionType.PLAY_KNIGHT_CARD, "Play Sentinel")
            single(ActionType.PLAY_ROAD_BUILDING, "Play Trailblazers")
            if (ActionType.PLAY_YEAR_OF_PLENTY in types) MoveButton("Play Windfall…") { sheet = "yop" }
            if (ActionType.PLAY_MONOPOLY in types) MoveButton("Play Corner the Market…") { sheet = "mono" }
            // Age of Chivalry moves that aren't board taps: one button per kind (or card), a picker if it has choices.
            for ((label, acts) in ckButtonGroups(s, legal, names)) {
                if (acts.size == 1 && !isBoardCard(acts)) MoveButton(label) { onMove(acts[0]) }
                else if (isBoardCard(acts)) MoveButton("$label…") { onBoardPick(acts) }
                else MoveButton("$label…") { ckChoices = label to acts; sheet = "ck" }
            }
            // The main move last, in the bottom-right corner, with Trade right beside it: icons, not words (Mauro).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                tradeButton?.invoke()
                legal.firstOrNull { it.type == ActionType.ROLL }?.let { a -> BrassButton("Roll", "move-Roll", Modifier.width(96.dp).height(54.dp), size = 18.sp) { onMove(a) } }
                legal.firstOrNull { it.type == ActionType.END_TURN }?.let { a -> IconPlateButton(MoveIcon.END_TURN, "End turn", "move-End turn") { onMove(a) } }
            }
        }
    }
}

/**
 * The discard pile after tapping card [r] ([add] = tap, else hold to take one back): never more of a card
 * than you hold, never more cards in all than you owe.
 */
fun discardPick(pile: IntArray, r: Int, add: Boolean, have: Int, owed: Int): IntArray {
    val next = pile.copyOf()
    if (add) { if (next[r] < have && next.sum() < owed) next[r]++ } else if (next[r] > 0) next[r]--
    return next
}

/** The discard moves for a full [pile], one card per move, in card order. */
fun discardMoves(pile: IntArray): List<Action> = pile.indices.flatMap { r -> List(pile[r]) { Action.discard(r) } }

@Composable
private fun DiscardSheet(s: GameState, me: Int, legal: List<Action>, onMove: (Action) -> Unit, cause: String? = null) {
    // The same chest as the trade and the science bonus: title, the cards with "have N" under them, the chest's
    // buttons on the right (Mauro: "the discard menu for 7 should be the same style as trade and science bonus").
    val owed = s.discardOwed[me]
    var pile by remember(s.turn, owed) { mutableStateOf(IntArray(tradeCards(s))) }
    val full = pile.sum() == owed
    Row(Modifier.chest().testTag("discard-sheet"), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${cause ?: "A 7: you hold too many cards"}. Choose $owed to discard (${pile.sum()} chosen)", color = CHEST_INK, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Tap a card to add it · hold to take it back", color = CHEST_INK.copy(alpha = 0.7f), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in pile.indices) {
                    val have = holding(s, me, r)
                    if (have == 0) continue
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(width = 42.dp, height = 60.dp)
                                .pointerInput(r, have, owed) {
                                    detectTapGestures(onTap = { pile = discardPick(pile, r, true, have, owed) },
                                        onLongPress = { pile = discardPick(pile, r, false, have, owed) })
                                }.testTag("discard-card-$r"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(ALL_CARD_ART[r]), ALL_CARD_NAMES[r], Modifier.fillMaxSize())
                            if (pile[r] > 0) Text("−${pile[r]}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xE0C0392B), CircleShape).padding(horizontal = 6.dp))
                        }
                        Text("have $have", color = CHEST_INK, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        Box(Modifier.width(10.dp))
        // Fixed buttons: the cards never move while you pick them.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChestButton("Discard $owed", CHEST_YES, "discard-go", 104.dp, if (full) ({ val moves = discardMoves(pile); pile = IntArray(pile.size); moves.forEach(onMove) }) else null)
            ChestButton("Clear", CHEST_NO, "discard-clear", 104.dp, if (pile.sum() > 0) ({ pile = IntArray(pile.size) }) else null)
            legal.firstOrNull { it.type == ActionType.DISCARD && it.discardResource < 0 }?.let { a -> ChestButton("Random", CHEST_GOLD, "discard-random", 104.dp) { onMove(a) } }
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
    Text(title, color = INK_LIGHT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    // Choices that are cards show the card art, as in the trade chest; the rest stay worded buttons.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, a) in options) {
            val cards = optionCards(a)
            if (cards != null) Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { close(); onMove(a) }.testTag("pick-$label")) {
                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    for (c in cards) Image(painterResource(ALL_CARD_ART[c]), ALL_CARD_NAMES[c], Modifier.size(width = 42.dp, height = 60.dp))
                }
                Text(label, color = INK_LIGHT, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            } else MoveButton(label) { close(); onMove(a) }
        }
    }
    ChestButton("Back", CHEST_NO, "pick-back") { close() }
}

/** The cards a picker choice stands for (Windfall's two, the card a Corner names), or null if it isn't cards. */
internal fun optionCards(a: Action): List<Int>? = when (a.type) {
    ActionType.PLAY_YEAR_OF_PLENTY -> listOfNotNull(a.yopFirst, a.yopSecond.takeIf { it >= 0 })
    ActionType.PLAY_MONOPOLY -> listOf(a.payload.toInt())
    wgbg.engine.CkActionType.PLAY_PROGRESS -> when (a.a) {
        wgbg.engine.Progress.RESOURCE_MONOPOLY, wgbg.engine.Progress.TRADE_MONOPOLY, wgbg.engine.Progress.MERCHANT_FLEET -> listOf(a.b)
        else -> null
    }
    else -> null
}.takeIf { cs -> cs != null && cs.all { it in ALL_CARD_ART.indices } }

// ------------------------------------------------------------------ confirming board taps

/** The question asked before a tapped board move is made. */
fun confirmTitle(s: GameState, a: Action): String = when (a.type) {
    ActionType.BUILD_SETTLEMENT -> if (s.phase == Phase.BUILD_INITIAL_SETTLEMENT) "Place your homestead here?" else "Build a homestead here?"
    ActionType.BUILD_CITY -> if (s.phase == Phase.BUILD_INITIAL_SETTLEMENT) "Place your town here?" else "Upgrade this homestead to a town?"
    ActionType.BUILD_ROAD -> if (s.phase == Phase.BUILD_INITIAL_ROAD || s.freeRoads > 0) "Place a free road here?" else "Build a road here?"
    ActionType.MOVE_ROBBER -> "Move the bandit here?"
    wgbg.engine.CkActionType.PLAY_PROGRESS -> "Play ${PROGRESS_NAMES.getOrElse(a.a) { "this card" }} here?"
    wgbg.engine.CkActionType.CHOOSE_VERTEX -> when (s.phase) {
        Phase.BARBARIAN_LOSS -> "The Raiders won: lose this town to them?"
        Phase.DESERTER_REMOVE -> "Turncoat: remove this knight of yours?"
        else -> "Choose what to do here"
    }
    wgbg.engine.CkActionType.IMPROVE_CITY -> "Improve your towns?"
    else -> "Choose what to do here"
}

/** The first homesteads and roads are being placed: no dice, no trading yet. */
fun inSetup(s: GameState): Boolean = s.phase == Phase.BUILD_INITIAL_SETTLEMENT || s.phase == Phase.BUILD_INITIAL_ROAD

/**
 * Whether the confirm box goes to the bottom of the screen instead of the top: when the tapped spot ([spotY] on a board
 * [boardHeight] tall) is in the top 45%, so the box never covers the spot itself. (0.2.7 online test: a Raiders town
 * near the top edge sat under the box, and a double tap on the town landed on its cancel button.)
 */
fun confirmBelow(spotY: Float?, boardHeight: Float): Boolean = spotY != null && boardHeight > 0f && spotY < boardHeight * 0.45f

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
