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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wgbg.engine.CARD_TYPES
import wgbg.engine.GameState
import wgbg.engine.Track

/** Town-improvement bars in the order and colours of the design: Guilds (gold), Academy (green), Crown (blue). */
internal val IMPROVEMENT_BARS = listOf(Track.TRADE to Color(0xFFE0B23A), Track.SCIENCE to Color(0xFF4CAF50), Track.POLITICS to Color(0xFF3F7FD6))

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
                roll: TurnReport? = null) {
    version // the state object is mutated in place; this changing value makes the strip redraw after each move
    Column(modifier.brassEdgedWood().padding(horizontal = 12.dp, vertical = 8.dp).testTag("player-strip"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Age of Chivalry: how near the Raiders are (a ship marker on a 7-step track).
        s.ck?.let { ck ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.testTag("raiders-track")) {
                Text("Raiders", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                for (i in 0 until wgbg.engine.CkPiece.BARBARIAN_TRACK) Box(
                    Modifier.size(width = 14.dp, height = 8.dp)
                        .background(if (i < ck.barbarianPos) Color(0xFFC0392B) else Color(0x73000000), RoundedCornerShape(2.dp)),
                )
                Text("${raidersStepsLeft(ck.barbarianPos)}", color = Color(0xFFE57373), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        for (p in 0 until s.numPlayers) {
            val turn = !s.isOver && p == s.currentPlayer
            Row(
                Modifier.then(if (turn) Modifier.border(1.dp, Color(0xFFC9A04A), RoundedCornerShape(8.dp)) else Modifier).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(14.dp).background(PLAYER_COLORS[p], CircleShape).border(1.dp, Color(0xFFF1D58A), CircleShape))
                Column(Modifier.width(74.dp)) {
                    Text(names[p], color = Color(0xFFEDE6D8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val ck = s.ck
                    if (ck != null) for ((track, color) in IMPROVEMENT_BARS) Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                        val filled = barSegments(ck.level(p, track))
                        for (i in 0 until 4) Box(Modifier.size(width = 14.dp, height = 4.dp).background(if (i < filled) color else Color(0x73000000), RoundedCornerShape(1.dp)))
                    }
                }
                // A tiny card icon and the count; in Age of Chivalry "cards/progress", e.g. 8/3.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(52.dp)) {
                    Box(Modifier.size(width = 10.dp, height = 14.dp).background(Color(0xFFEFE3C8), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFFC9A04A), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(cardsLabel(s, p), color = Color(0xFFEDE6D8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("${if (p == me) s.victoryPoints(p) else s.publicVictoryPoints(p)}", color = Color(0xFFF1D58A), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                // What this player got (+) and lost (-) on the last roll, as tiny cards (#47).
                val minis = rollMinis(roll, p, me)
                if (minis.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("roll-minis-$p")) {
                    for (m in minis) MiniRollCard(m)
                }
            }
        }
    }
}

/** Age of Chivalry deck colours by track: Guilds gold (trade), Crown blue (politics), Academy green (science). */
internal val TRACK_COLORS = listOf(Color(0xFFE0B23A), Color(0xFF3F7FD6), Color(0xFF4CAF50))

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
    @Composable
    fun namedCard(name: String, edge: Color, n: Int, tag: String, w: Int, h: Int, onTap: (() -> Unit)? = null) = Box(contentAlignment = Alignment.TopEnd,
        modifier = Modifier.testTag(tag).then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)) {
        Box(Modifier.size(width = w.dp, height = h.dp).background(Color(0xFFEFE3C8), RoundedCornerShape(5.dp)).border(2.dp, edge, RoundedCornerShape(5.dp)).padding(3.dp),
            contentAlignment = Alignment.Center) {
            Text(name, color = Color(0xFF2B2118), fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (n > 1) count(n)
    }
    Column(modifier) {
    hint?.let { Text(it, color = Color(0xFFEDE6D8), fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 4.dp).brassEdgedWood(2.dp).padding(horizontal = 10.dp, vertical = 6.dp).testTag("card-hint")) }
    Row(Modifier.testTag("hand-row").horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        for (r in 0 until CARD_TYPES) {
            val n = s.hand(me, r)
            if (n == 0) continue
            Box(contentAlignment = Alignment.TopEnd) {
                Image(painterResource(CARD_ART[r]), RESOURCE_NAMES[r], Modifier.size(width = 50.dp, height = 70.dp))
                count(n)
            }
        }
        val ck = s.ck ?: return@Row
        // Commodities: Parchment (Academy), Weave (Guilds), Silver (Crown), until their card art lands.
        val commodityTrack = listOf(wgbg.engine.Track.SCIENCE, wgbg.engine.Track.TRADE, wgbg.engine.Track.POLITICS)
        for (c in 0 until wgbg.engine.Commodity.COUNT) {
            val n = s.card(me, CARD_TYPES + c)
            if (n > 0) namedCard(ALL_CARD_NAMES[CARD_TYPES + c], TRACK_COLORS[commodityTrack[c]], n, "hand-commodity-$c", 50, 70)
        }
        for (c in 0 until wgbg.engine.Progress.COUNT) {
            val n = ck.progressHand[me * wgbg.engine.Progress.COUNT + c]
            if (n > 0) namedCard(PROGRESS_NAMES.getOrElse(c) { "Progress" }, TRACK_COLORS[wgbg.engine.Progress.track(c)], n, "hand-progress-$c", 44, 62) {
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
        // A slim badge on the card's foot, so the card itself stays readable.
        Text(if (m.n > 0) "+${m.n}" else "${m.n}", color = Color.White, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).background(if (m.n > 0) Color(0xE6388E3C) else Color(0xE6C0392B), RoundedCornerShape(topStart = 3.dp))
                .padding(horizontal = 2.dp))
    }
}
