package com.mauro.wgbg

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wgbg.engine.CARD_TYPES
import wgbg.engine.GameState
import wgbg.engine.Track

/** Town-improvement bars in the order and colours of the design: Guilds (gold), Academy (green), Crown (blue). */
internal val IMPROVEMENT_BARS get() = listOf(Track.TRADE to TRACK_COLORS[Track.TRADE], Track.SCIENCE to TRACK_COLORS[Track.SCIENCE], Track.POLITICS to TRACK_COLORS[Track.POLITICS])

/** Segments filled on a 4-segment improvement bar for an engine level 0..5. */
fun barSegments(level: Int) = level.coerceIn(0, 4)

/**
 * Board A's player list (top-left): colour, name, points and cards for everyone; in Age of
 * Chivalry three 4-segment bars show each player's town improvements. Other players' hidden
 * points (Landmark cards) are not shown.
 */
@Composable
fun PlayerStrip(s: GameState, me: Int, names: List<String>, version: Int, modifier: Modifier = Modifier,
                /** The turn whose roll the mini cards show (#47); null shows none. */
                roll: TurnReport? = null,
                /** False inside the one left side panel (#73), which draws the frame for both halves. */
                framed: Boolean = true) {
    version // the state object is mutated in place; this changing value makes the strip redraw after each move
    Column((if (framed) modifier.brassEdgedWood() else modifier).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp).testTag("player-strip"), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        // Age of Chivalry: how near the Raiders are (a ship marker on a 7-step track).
        s.ck?.let { ck ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.testTag("raiders-track")) {
                Text("Raiders", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                for (i in 0 until wgbg.engine.CkPiece.BARBARIAN_TRACK) Box(
                    Modifier.size(width = 11.dp, height = 8.dp)
                        .background(if (i < ck.barbarianPos) Color(0xFFC0392B) else Color(0x73000000), RoundedCornerShape(2.dp)),
                )
                // Raiders' strength vs every active knight, on the track's own line (Mauro): green while the knights would hold.
                val raiders = wgbg.engine.CitiesAndKnights.barbarianStrength(s)
                val knights = (0 until s.numPlayers).sumOf { wgbg.engine.CitiesAndKnights.activeKnightStrength(s, it) }
                Text("$raiders vs $knights", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp).testTag("raiders-vs-knights"),
                    color = if (knights >= raiders) Color(0xFF8BD18B) else Color(0xFFFF6B5E))
            }
        }
        for (p in 0 until s.numPlayers) {
            val turn = !s.isOver && p == s.currentPlayer
            Row(
                Modifier.then(if (turn) Modifier.border(1.dp, Color(0xFFC9A04A), RoundedCornerShape(8.dp)) else Modifier).padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The score sits in the player's colour dot, so the strip needs no score column (Mauro).
                Box(Modifier.size(26.dp).background(PLAYER_COLORS[p], CircleShape).border(1.dp, Color(0xFFF1D58A), CircleShape).testTag("score-$p"),
                    contentAlignment = Alignment.Center) {
                    Text("${if (p == me) s.victoryPoints(p) else s.publicVictoryPoints(p)}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (PLAYER_COLORS[p].luminance() > 0.35f) Color(0xFF2B2118) else Color.White)
                }
                Column(Modifier.width(74.dp)) {
                    Text(names[p], color = Color(0xFFEDE6D8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val ck = s.ck
                    if (ck != null) for ((track, color) in IMPROVEMENT_BARS) Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                        val filled = barSegments(ck.level(p, track))
                        for (i in 0 until 4) Box(Modifier.size(width = 14.dp, height = 4.dp).background(if (i < filled) color else Color(0x73000000), RoundedCornerShape(1.dp)))
                    }
                }
                // The card count, and under it (right of the upgrade bars) the road length and knights: a squiggle and a
                // shield, gold for whoever holds Longest Road / Largest Army (Mauro).
                Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                // A tiny card icon and the count; in Age of Chivalry "cards/progress", e.g. 8/3.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 10.dp, height = 14.dp).background(Color(0xFFEFE3C8), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFFC9A04A), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    // Red when a rolled 7 would make them discard half (Mauro).
                    Text(cardsLabel(s, p), color = if (overCardLimit(s, p)) Color(0xFFFF6B5E) else Color(0xFFEDE6D8), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("cards-$p"))
                }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatBadge(StatIcon.ROAD, s.roadLength[p], s.longestRoadOwner == p, "road-$p")
                        StatBadge(StatIcon.SHIELD, knightsShown(s, p), s.ck == null && s.largestArmyOwner == p, "army-$p")
                    }
                }
                // The last roll's cards, each overlapping the one before by half in one fixed slot, so the frame never
                // stretches or jumps however many came in (Mauro); past four, the rest are in the history.
                val minis = rollMinis(roll, p, me)
                Box(Modifier.size(width = MINI_SLOT, height = 30.dp).testTag("roll-minis-$p")) {
                    minis.take(MINI_MAX).forEachIndexed { i, m -> Box(Modifier.offset(x = MINI_STEP * i)) { MiniRollCard(m) } }
                }
            }
        }
    }
}


/** The knights number on a player's shield: Sentinels played (base game), active knight strength (Age of Chivalry). */
fun knightsShown(s: GameState, p: Int): Int = if (s.ck != null) wgbg.engine.CitiesAndKnights.activeKnightStrength(s, p) else s.knightsPlayed(p)

enum class StatIcon { ROAD, SHIELD }

/** A tiny drawn icon and a number; gold when the player holds that award. */
@Composable
fun StatBadge(icon: StatIcon, value: Int, held: Boolean, tag: String) {
    val ink = if (held) Color(0xFFF1D58A) else Color(0xFFCDBFA6)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag(tag)) {
        androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
            val w = size.width; val h = size.height
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            when (icon) {
                StatIcon.ROAD -> drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(0.05f * w, 0.75f * h); cubicTo(0.3f * w, 0.1f * h, 0.45f * w, 1.0f * h, 0.7f * w, 0.35f * h); quadraticTo(0.82f * w, 0.1f * h, 0.95f * w, 0.25f * h) }, ink, style = stroke)
                StatIcon.SHIELD -> drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(0.12f * w, 0.1f * h); lineTo(0.88f * w, 0.1f * h); lineTo(0.88f * w, 0.5f * h)
                    quadraticTo(0.85f * w, 0.85f * h, 0.5f * w, 0.98f * h); quadraticTo(0.15f * w, 0.85f * h, 0.12f * w, 0.5f * h); close() }, ink, style = stroke)
            }
        }
        Text("$value", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Everything about one card, for the long-press view: its art, name, kind and what it's for. */
data class CardDetail(val art: Int, val title: String, val kind: String?, val lines: List<String>)

/** A resource or commodity card ([card] index): where it comes from and what it pays for, read from the build map. */
fun cardDetail(card: Int, chivalry: Boolean): CardDetail {
    val uses = buildCosts(chivalry).filter { card < it.cards.size && it.cards[card] > 0 }.map { "${it.name}: ${it.cards[card]}" }
    val improve = if (card >= CARD_TYPES) listOf("Improves the ${listOf("Academy", "Guilds", "Crown")[card - CARD_TYPES]} track of your towns") else emptyList()
    return CardDetail(ALL_CARD_ART[card], ALL_CARD_NAMES[card], if (card < CARD_TYPES) "Resource" else "Commodity",
        listOf("From: ${CARD_SOURCES[card]}") + (if (uses.isEmpty()) emptyList() else listOf("Pays for: " + uses.joinToString(", "))) + improve)
}

/** A progress card: its deck and its full meaning. */
fun progressDetail(c: Int): CardDetail = CardDetail(progressArt(c), PROGRESS_NAMES.getOrElse(c) { "Progress card" },
    "${DECK_NAMES[wgbg.engine.Progress.track(c)]} progress card", listOf(progressHint(c)))

/** The roll cards beside each player: up to [MINI_MAX], each overlapping the one before by half. */
private val MINI_STEP = 10.5.dp
private const val MINI_MAX = 4
private val MINI_SLOT = 21.dp + MINI_STEP * (MINI_MAX - 1)

/** A progress card's art: its deck's commodity card (Guilds Weave, Crown Silver, Academy Parchment). */
fun progressArt(card: Int): Int = ALL_CARD_ART[wgbg.engine.Track.commodity(wgbg.engine.Progress.track(card))]

/** Age of Chivalry deck colours by track: Guilds gold (trade), Crown blue (politics), Academy green (science). */
// Mauro: themed by commodity and its land. Guilds / Weave (pasture) pale white-green, Crown / Silver (mountains)
// blue-grey, Academy / Parchment (forest) forest green.
internal val TRACK_COLORS = listOf(Color(0xFFD3E8C4), Color(0xFF8A9FB2), Color(0xFF3E8E48))

/**
 * Your hand along the bottom edge (no frame): the illustrated cards you hold, each with its count; in Age of
 * Chivalry also your commodities and your progress cards (named, edged in their deck's colour) (#4).
 */
@Composable
fun HandRow(s: GameState, me: Int, version: Int, modifier: Modifier = Modifier) {
    version // see PlayerStrip
    @Composable
    fun count(n: Int) = Text("$n", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(2.dp).background(Color(0xE62A1B10), CircleShape).border(1.dp, Color(0xFFC9A04A), CircleShape).padding(horizontal = 6.dp))
    // Tap a progress card to see what it does (Mauro: "add some hints on what a card does").
    var hint by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(hint) { if (hint != null) { kotlinx.coroutines.delay(5000); hint = null } }
    // A long press on any card shows it big with everything about it (Mauro); a tap anywhere on it closes.
    var detail by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<CardDetail?>(null) }
    fun Modifier.longPressFor(d: CardDetail) = this.pointerInput(d) { detectTapGestures(onLongPress = { detail = d }) }
    @Composable
    fun namedCard(name: String, edge: Color, n: Int, tag: String, w: Int, h: Int, small: String? = null, art: Int? = null, long: CardDetail? = null, onTap: (() -> Unit)? = null) = Box(contentAlignment = Alignment.TopEnd,
        modifier = Modifier.testTag(tag).then(if (onTap != null || long != null) Modifier.pointerInput(long) {
            detectTapGestures(onTap = { onTap?.invoke() }, onLongPress = { long?.let { detail = it } })
        } else Modifier)) {
        Box(Modifier.size(width = w.dp, height = h.dp).background(Color(0xFFEFE3C8), RoundedCornerShape(5.dp))
            .clip(RoundedCornerShape(5.dp)).border(2.dp, edge, RoundedCornerShape(5.dp))) {
            // Themed by its deck's commodity (Mauro): Guilds on Weave, Crown on Silver, Academy on Parchment.
            if (art != null) Image(painterResource(art), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(3.dp)
                .background(Color(0xE6EFE3C8), RoundedCornerShape(3.dp)).padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, color = Color(0xFF2B2118), fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                // What it does, in small print on the card itself (Mauro); a tap still shows it large.
                if (small != null) Text(small, color = Color(0xFF4A3B2C), fontSize = 6.5.sp, lineHeight = 7.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (n > 1) count(n)
    }
    Column(modifier) {
    detail?.let { d ->
        Row(Modifier.padding(bottom = 6.dp).brassEdgedWood().padding(12.dp).widthIn(max = 520.dp).pointerInput(d) { detectTapGestures { detail = null } }.testTag("card-detail"),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(d.art), d.title, Modifier.size(width = 100.dp, height = 140.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d.title, color = Color(0xFFF1D58A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                d.kind?.let { Text(it, color = Color(0xFFCDBFA6), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                for (line in d.lines) Text(line, color = Color(0xFFEDE6D8), fontSize = 13.sp)
                Text("Tap to close", color = Color(0xFFCDBFA6), fontSize = 11.sp)
            }
        }
    }
    hint?.let { Text(it, color = Color(0xFFEDE6D8), fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 4.dp).brassEdgedWood(2.dp).padding(horizontal = 10.dp, vertical = 6.dp).testTag("card-hint")) }
    Row(Modifier.testTag("hand-row").horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        for (r in 0 until CARD_TYPES) {
            val n = s.hand(me, r)
            if (n == 0) continue
            Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.longPressFor(cardDetail(r, s.ck != null))) {
                Image(painterResource(CARD_ART[r]), RESOURCE_NAMES[r], Modifier.size(width = 50.dp, height = 70.dp))
                count(n)
            }
        }
        val ck = s.ck ?: return@Row
        // Commodities: Parchment, Weave, Silver, with their card art (#62).
        for (c in 0 until wgbg.engine.Commodity.COUNT) {
            val n = s.card(me, CARD_TYPES + c)
            if (n > 0) Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.testTag("hand-commodity-$c").longPressFor(cardDetail(CARD_TYPES + c, true))) {
                Image(painterResource(COMMODITY_ART[c]), ALL_CARD_NAMES[CARD_TYPES + c], Modifier.size(width = 50.dp, height = 70.dp))
                count(n)
            }
        }
        for (c in 0 until wgbg.engine.Progress.COUNT) {
            val n = ck.progressHand[me * wgbg.engine.Progress.COUNT + c]
            if (n > 0) namedCard(PROGRESS_NAMES.getOrElse(c) { "Progress" }, TRACK_COLORS[wgbg.engine.Progress.track(c)], n, "hand-progress-$c", 66, 92, small = progressHint(c),
                art = progressArt(c), long = progressDetail(c)) {
                hint = "${PROGRESS_NAMES.getOrElse(c) { "Progress" }}: ${progressHint(c)}"
            }
        }
    }
    }
}

/** A slim prompt across the top saying what to do now. */
@Composable
fun TurnPrompt(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color(0xFFEDE6D8), fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.brassEdgedWood().padding(horizontal = 16.dp, vertical = 6.dp).testTag("turn-prompt"))
}

/** Ship steps left before the Raiders land (attack) for track position [pos]. */
fun raidersStepsLeft(pos: Int) = (wgbg.engine.CkPiece.BARBARIAN_TRACK - pos).coerceAtLeast(0)

/** Hand size for the player list: "8", or "8/3" (cards / progress cards) in Age of Chivalry. */
/** True if [p] holds more cards than a rolled 7 allows (they would discard half): the engine's own limit and count. */
fun overCardLimit(s: GameState, p: Int): Boolean =
    if (s.ck != null) s.cardCount(p) > wgbg.engine.CitiesAndKnights.discardLimit(s, p) else s.handSize(p) > s.config.discardLimit

fun cardsLabel(s: GameState, p: Int): String = s.ck?.let { "${s.cardCount(p)}/${it.progressCount(p)}" } ?: "${s.cardCount(p)}"

/** One mini card in the player list: [card] (-1 = face down, a robbery others can't see) and [n] (+ got, - lost). */
data class RollMini(val card: Int, val n: Int)

/**
 * The mini cards for player [p] after [roll] (#47): production in, discards out (public: they go back to the
 * bank face up) and a robbery, whose card only the thief and the victim see; everyone else sees it face down.
 */
fun rollMinis(roll: TurnReport?, p: Int, me: Int): List<RollMini> {
    if (roll == null || !roll.rolled) return emptyList()
    val out = mutableListOf<RollMini>()
    roll.rollIn.getOrNull(p)?.forEachIndexed { r, n -> if (n > 0) out += RollMini(r, n) }
    roll.rollOut.getOrNull(p)?.forEachIndexed { r, n -> if (n > 0) out += RollMini(r, -n) }
    roll.steal?.let { st ->
        val seen = me == st.thief || me == st.victim || me < 0
        if (p == st.thief) out += RollMini(if (seen) st.card else -1, 1)
        if (p == st.victim) out += RollMini(if (seen) st.card else -1, -1)
    }
    return out
}

@Composable
private fun MiniRollCard(m: RollMini) {
    Box(Modifier.size(width = 21.dp, height = 30.dp), contentAlignment = Alignment.Center) {
        if (m.card in CARD_ART.indices) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(CARD_ART[m.card]),
            RESOURCE_NAMES[m.card], Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Color(0xFF5A4030), RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF8A6A2A), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center) { Text("?", color = Color(0xFFEFE3C8), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        // A slim badge on the card's left foot (the next card covers its right half), so the count stays readable.
        Text(if (m.n > 0) "+${m.n}" else "${m.n}", color = Color.White, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).background(if (m.n > 0) Color(0xE6388E3C) else Color(0xE6C0392B), RoundedCornerShape(topEnd = 3.dp))
                .padding(horizontal = 2.dp))
    }
}
