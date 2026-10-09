package com.mauro.wgbg

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wgbg.engine.Action
import wgbg.engine.ActionType
import wgbg.engine.CARD_TYPES
import wgbg.engine.GameState
import wgbg.engine.Phase
import wgbg.engine.Port
import wgbg.engine.Rules

// ------------------------------------------------------------------ trade logic (UI-free, unit tested)

/** What the trade panel is for right now, from [me]'s seat. */
enum class TradeMode {
    /** Our turn after rolling: build an offer to the players, or trade with the bank. */
    COMPOSE,
    /** Someone offered us a trade: Yes, No, or Counter. */
    RESPOND,
    /** Someone countered our offer: Yes or No. */
    REVIEW_COUNTER,
    /** Our offer went round the table: pick who to trade with, or cancel. */
    PICK_PARTNER,
    /** A trade is on but someone else has to answer. */
    WAITING,
    /** No trading now (before rolling, other turns, setup...). */
    CLOSED,
}

fun tradeMode(s: GameState, me: Int): TradeMode {
    val acting = s.actingPlayer == me
    return when (s.phase) {
        Phase.DECIDE_TRADE -> if (acting) TradeMode.RESPOND else TradeMode.WAITING
        Phase.DECIDE_COUNTER -> if (acting) TradeMode.REVIEW_COUNTER else TradeMode.WAITING
        Phase.DECIDE_ACCEPTEES -> if (acting) TradeMode.PICK_PARTNER else TradeMode.WAITING
        Phase.PLAY_TURN -> if (acting && s.currentPlayer == me && s.hasRolled && s.freeRoads == 0) TradeMode.COMPOSE else TradeMode.CLOSED
        else -> TradeMode.CLOSED
    }
}

/** True when the game is waiting on [me] to answer a trade: the panel opens itself. */
fun tradeNeedsMe(s: GameState, me: Int) = tradeMode(s, me).let {
    it == TradeMode.RESPOND || it == TradeMode.REVIEW_COUNTER || it == TradeMode.PICK_PARTNER
}

/**
 * The open terms seen from [me]'s side: (what I get, what I give). For the offerer that is the
 * offer itself; for anyone else it is mirrored. A pending counter replaces the offer.
 */
/** Card kinds a trade covers: the five resources, plus three commodities in Age of Chivalry. */
fun tradeCards(s: GameState) = if (s.ck != null) wgbg.engine.CK_CARD_TYPES else CARD_TYPES

/** Cards of kind [i] in [p]'s hand (resources, or commodities in Age of Chivalry). */
fun holding(s: GameState, p: Int, i: Int) = if (i < CARD_TYPES) s.hand(p, i) else s.card(p, i)

/** The offer action for these counts, in the ruleset's own encoding. */
fun offerFor(s: GameState, give: IntArray, get: IntArray): Action? = runCatching {
    if (s.ck != null) Action.offerTradeCk(give, get) else Action.offerTrade(give, get)
}.getOrNull()

fun termsFor(s: GameState, me: Int): Pair<IntArray, IntArray> {
    val ck = s.ck
    if (ck != null) {
        val n = wgbg.engine.CK_CARD_TYPES
        val gives = IntArray(n) { if (it < CARD_TYPES) s.tradeOffer[it] else ck.offer[it - CARD_TYPES] }
        val gets = IntArray(n) { if (it < CARD_TYPES) s.tradeOffer[CARD_TYPES + it] else ck.offer[wgbg.engine.Commodity.COUNT + it - CARD_TYPES] }
        return if (me == s.currentPlayer) gets to gives else gives to gets
    }
    val c = s.counterOffer
    val offerGives = IntArray(tradeCards(s)) { c?.offerGive(it) ?: s.tradeOffer[it] }
    val offerGets = IntArray(tradeCards(s)) { c?.offerWant(it) ?: s.tradeOffer[CARD_TYPES + it] }
    return if (me == s.currentPlayer) offerGets to offerGives else offerGives to offerGets
}

/** The counter a responder means by "I get [get], I give [give]" (COUNTER_TRADE is offerer-side). */
fun counterFor(get: IntArray, give: IntArray) = Action.counterTrade(give = get, want = give)

fun bankRate(s: GameState, p: Int, r: Int): Int {
    if (s.ck != null) return wgbg.engine.CitiesAndKnights.tradeRate(s, p, r)
    val m = s.portMask[p]
    return if (m and (1 shl r) != 0) 2 else if (m and Port.GENERIC_BIT != 0) 3 else 4
}

// ------------------------------------------------------------------ the chest

/** Parchment, Weave, Silver edge colours (Academy green, Guilds gold, Crown blue). */
private val COMMODITY_COLORS = listOf(Color(0xFF4CAF50), Color(0xFFE0B23A), Color(0xFF3F7FD6))

/** Illustrated resource cards (Mauro's art), indexed like RESOURCE_NAMES. */
val CARD_ART = listOf(R.drawable.card_timber, R.drawable.card_clay, R.drawable.card_fleece, R.drawable.card_grain, R.drawable.card_stone)

private val WOOD_DARK = Color(0xFF2A1B10)
private val WOOD_MID = Color(0xFF4A3020)
private val BRASS = Color(0xFFC9A04A)
private val BRASS_LIGHT = Color(0xFFF1D58A)
private val BRASS_DARK = Color(0xFF7A5A22)
private val INK = Color(0xFFEDE6D8)
private val YES = Color(0xFF3E9B4F)
private val NO = Color(0xFFC0392B)
private val COUNTER = Color(0xFFE0B23A)

/** A dark wooden chest with brass bands, corners and rivets; shared by the trade and other prompts. */
fun Modifier.chest(): Modifier = this
    .drawBehind {
        val r = CornerRadius(14.dp.toPx())
        drawRoundRect(Brush.verticalGradient(listOf(WOOD_MID, WOOD_DARK, WOOD_MID, WOOD_DARK)), cornerRadius = r)
        // Planks.
        val plank = size.height / 5
        for (i in 1 until 5) drawLine(Color.Black.copy(alpha = 0.35f), Offset(8f, plank * i), Offset(size.width - 8f, plank * i), 2f)
        // Brass bands and rim.
        val brass = Brush.linearGradient(listOf(BRASS_DARK, BRASS_LIGHT, BRASS, BRASS_DARK), Offset.Zero, Offset(size.width, size.height))
        drawRoundRect(brass, cornerRadius = r, style = Stroke(4.dp.toPx()))
        // Corner plates and rivets.
        val c = 18.dp.toPx()
        for (x in listOf(0f, size.width - c)) for (y in listOf(0f, size.height - c)) {
            drawRoundRect(brass, Offset(x, y), Size(c, c), CornerRadius(4.dp.toPx()))
            drawCircle(BRASS_DARK, 2.5.dp.toPx(), Offset(x + c / 2, y + c / 2))
        }
    }
    .padding(horizontal = 14.dp, vertical = 8.dp)

// ------------------------------------------------------------------ the panel

/**
 * The trade panel: Players and Maritime tabs; the top row is what you get, the bottom row what
 * you give (tap a card to add one, tap its "−" to take one back); Yes / No / Counter on the right.
 * Every button sends a legal move (or a trade the engine validates), so nothing bypasses the rules.
 */
@Composable
fun TradePanel(
    s: GameState,
    me: Int,
    legal: List<Action>,
    names: List<String>,
    onMove: (Action) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Answers to our offer by seat: ✓ yes, ✗ no, ⇄ countered. */
    responses: Map<Int, String> = emptyMap(),
    /** Changes after every move; [s] is mutated in place, so this makes the panel redraw. */
    version: Int = 0,
) {
    version
    val mode = tradeMode(s, me)
    // Fresh selections whenever the trade situation changes.
    val key = listOf(s.turn, s.phase, s.actingPlayer, s.counterFrom, s.hands.sum())
    var maritime by remember { mutableStateOf(false) }
    var countering by remember(key) { mutableStateOf(false) }
    var get by remember(key) { mutableStateOf(IntArray(tradeCards(s))) }
    var give by remember(key) { mutableStateOf(IntArray(tradeCards(s))) }
    val (termGet, termGive) = termsFor(s, me)
    val editing = mode == TradeMode.COMPOSE || countering
    val showGet = if (editing) get else termGet
    val showGive = if (editing) give else termGive
    val bankTab = maritime && mode == TradeMode.COMPOSE

    if (mode == TradeMode.CLOSED) {
        Text(status(s, me, mode, names, false, false), color = INK, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = modifier.chest().testTag("trade-panel"))
        return
    }
    Row(modifier.chest().testTag("trade-panel"), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tab("Players", !bankTab) { maritime = false }
                if (mode == TradeMode.COMPOSE) Tab("Maritime", bankTab) { maritime = true; get = IntArray(tradeCards(s)); give = IntArray(tradeCards(s)) }
                // Everyone's answer to your offer, up here beside the tabs: a row at the bottom ran off the
                // chest on a landscape phone.
                if (!bankTab && s.currentPlayer == me) for (p in 0 until s.numPlayers) if (p != me) {
                    val mark = responses[p] ?: if (mode == TradeMode.WAITING && s.actingPlayer == p) "…" else ""
                    Text("${names[p]} $mark", color = when (mark) { "✓" -> Color(0xFF8BD18B); "✗" -> Color(0xFFE57373); "⇄" -> COUNTER; else -> INK.copy(alpha = 0.7f) },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.testTag("answer-$p"))
                }
            }
            Text(status(s, me, mode, names, countering, bankTab), color = INK, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.width(214.dp))
            Text(if (bankTab) "You get 1" else "You get", color = INK.copy(alpha = 0.7f), fontSize = 11.sp)
            CardRow(showGet, editing, single = bankTab, badge = { null }) { r, d ->
                get = if (bankTab) IntArray(tradeCards(s)).also { it[r] = 1 } else get.copyOf().also { it[r] = (it[r] + d).coerceIn(0, 9) }
            }
            Text("You give", color = INK.copy(alpha = 0.7f), fontSize = 11.sp)
            CardRow(showGive, editing, single = bankTab, badge = { r -> if (bankTab) "${bankRate(s, me, r)}:1" else "have ${holding(s, me, r)}" }) { r, d ->
                give = if (bankTab) IntArray(tradeCards(s)).also { it[r] = bankRate(s, me, r) }
                else give.copyOf().also { it[r] = (it[r] + d).coerceIn(0, holding(s, me, r)) }
            }
            if (mode == TradeMode.PICK_PARTNER) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in legal.filter { it.type == ActionType.CONFIRM_TRADE })
                    Pill("Trade with ${names[a.payload.toInt()]}", YES, tag = "partner-${a.payload}") { onMove(a) }
            }
        }
        Spacer(Modifier.width(10.dp))
        // Yes / No / Counter.
        val yes: Action? = when {
            bankTab -> {
                val g = give.indexOfFirst { it > 0 }
                val w = get.indexOfFirst { it > 0 }
                if (g >= 0 && w >= 0) Action.maritimeTrade(g, w).takeIf { it in legal } else null
            }
            mode == TradeMode.COMPOSE -> offerFor(s, give, get)?.takeIf { Rules.isLegal(s, it) }
            countering -> counterFor(get, give).takeIf { Rules.isLegal(s, it) }
            mode == TradeMode.RESPOND || mode == TradeMode.REVIEW_COUNTER -> Action.ACCEPT_TRADE.takeIf { it in legal }
            mode == TradeMode.PICK_PARTNER -> legal.filter { it.type == ActionType.CONFIRM_TRADE }.singleOrNull()
            else -> null
        }
        val no: (() -> Unit)? = when {
            countering -> { { countering = false } }
            mode == TradeMode.COMPOSE -> { { if (get.any { it > 0 } || give.any { it > 0 }) { get = IntArray(tradeCards(s)); give = IntArray(tradeCards(s)) } else onClose() } }
            mode == TradeMode.RESPOND || mode == TradeMode.REVIEW_COUNTER -> { { onMove(Action.REJECT_TRADE) } }
            mode == TradeMode.PICK_PARTNER -> { { onMove(Action.CANCEL_TRADE) } }
            else -> null
        }
        val counter: (() -> Unit)? = if (mode == TradeMode.RESPOND && !countering && s.ck == null) {
            { get = termGet.copyOf(); give = termGive.copyOf(); countering = true }
        } else null
        val labels = tradeButtonLabels(mode, countering, bankTab, get.any { it > 0 } || give.any { it > 0 })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Several takers: the "Trade with …" buttons choose, so no greyed-out Trade beside them.
            if (!(mode == TradeMode.PICK_PARTNER && yes == null))
                BigButton(labels.first, YES, "trade-yes", yes?.let { a -> { onMove(a); if (mode == TradeMode.COMPOSE) { get = IntArray(tradeCards(s)); give = IntArray(tradeCards(s)) } } })
            BigButton(labels.second, NO, "trade-no", no)
            // Counter only means something when answering someone else's offer.
            if (mode == TradeMode.RESPOND) BigButton("Counter", COUNTER, "trade-counter", counter)
        }
    }
}

/**
 * What the green and red buttons say: Yes/No only when answering someone; your own offer is "Offer"
 * (or "Trade" with the bank) and "Clear"/"Close"; picking a partner is "Trade"/"Cancel"; a counter is "Send"/"Back".
 */
internal fun tradeButtonLabels(mode: TradeMode, countering: Boolean, bank: Boolean, entered: Boolean): Pair<String, String> = when {
    countering -> "Send" to "Back"
    mode == TradeMode.COMPOSE -> (if (bank) "Trade" else "Offer") to (if (entered) "Clear" else "Close")
    mode == TradeMode.PICK_PARTNER -> "Trade" to "Cancel"
    else -> "Yes" to "No"
}

private fun status(s: GameState, me: Int, mode: TradeMode, names: List<String>, countering: Boolean, bank: Boolean): String = when {
    bank -> "Trade with the bank"
    countering -> "Your counter to ${names[s.currentPlayer]}"
    mode == TradeMode.COMPOSE -> "Offer the other players"
    mode == TradeMode.RESPOND -> "${names[s.currentPlayer]} offers you a trade"
    mode == TradeMode.REVIEW_COUNTER -> "${names[s.counterFrom]} counters your offer"
    mode == TradeMode.PICK_PARTNER -> "Pick who to trade with"
    mode == TradeMode.WAITING -> "Waiting for ${names[s.actingPlayer]}…"
    else -> if (s.currentPlayer == me && !s.hasRolled) "Roll first, then trade" else "Trade on your turn"
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) BRASS else WOOD_DARK, RoundedCornerShape(8.dp))
            .border(1.dp, BRASS, RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp).testTag("tab-$label"),
    ) { Text(label, color = if (selected) WOOD_DARK else INK, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** Five resource cards; tap adds one (or picks it, for the bank), the "−" under a card takes one back. */
@Composable
private fun CardRow(counts: IntArray, editable: Boolean, single: Boolean, badge: (Int) -> String?, change: (Int, Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in counts.indices) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val n = counts[r]
            val lit = n > 0
            Box(
                Modifier.size(width = 34.dp, height = 48.dp) // small enough that the chest fits a landscape phone
                    .then(if (lit) Modifier.border(2.dp, BRASS_LIGHT, RoundedCornerShape(5.dp)) else Modifier)
                    .then(if (editable) Modifier.clickable { change(r, 1) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                // The illustrated card; dimmed until it is part of the trade.
                if (r < CARD_ART.size) Image(painterResource(CARD_ART[r]), RESOURCE_NAMES[r], Modifier.fillMaxSize(), alpha = if (lit) 1f else 0.45f)
                else Box(Modifier.fillMaxSize().background(Color(0xFFEFE3C8).copy(alpha = if (lit) 1f else 0.45f), RoundedCornerShape(5.dp))
                    .border(2.dp, COMMODITY_COLORS[r - CARD_ART.size], RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                    // Commodities (Age of Chivalry) until their card art lands.
                    Text(ALL_CARD_NAMES[r], color = Color(0xFF2B2118), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (lit) Text("$n", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xCC2A1B10), CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
            }
            Box(Modifier.height(15.dp), contentAlignment = Alignment.Center) {
                if (editable && lit && !single) Text("−", color = INK, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { change(r, -1) }.padding(horizontal = 10.dp))
                else badge(r)?.let { Text(it, color = INK.copy(alpha = 0.6f), fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, color: Color, tag: String, onClick: (() -> Unit)?) {
    val on = onClick != null
    Box(
        Modifier.size(width = 78.dp, height = 38.dp)
            .background(if (on) color else color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .border(2.dp, if (on) BRASS_LIGHT else BRASS_DARK, RoundedCornerShape(10.dp))
            .then(if (on) Modifier.clickable { onClick!!() } else Modifier).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) Color.White else INK.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
private fun Pill(label: String, color: Color, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.background(color, RoundedCornerShape(16.dp)).border(1.dp, BRASS_LIGHT, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp).testTag(tag),
    ) { Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** The always-visible Trade toggle; glows when a trade is waiting on you. */
@Composable
fun TradeToggle(open: Boolean, attention: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = 84.dp, height = 40.dp)
            .background(if (open) BRASS else WOOD_DARK, RoundedCornerShape(10.dp))
            .border(if (attention) 3.dp else 2.dp, if (attention) COUNTER else BRASS, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).testTag("trade-toggle"),
        contentAlignment = Alignment.Center,
    ) { Text("Trade", color = if (open) WOOD_DARK else INK, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

/** The Trade button appears once setup is over (no trading while placing the first homesteads). */
fun tradeButtonVisible(s: GameState) = s.phase != Phase.BUILD_INITIAL_SETTLEMENT && s.phase != Phase.BUILD_INITIAL_ROAD
