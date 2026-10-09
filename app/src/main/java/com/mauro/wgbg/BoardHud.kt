package com.mauro.wgbg

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
fun PlayerStrip(s: GameState, me: Int, names: List<String>, version: Int, modifier: Modifier = Modifier) {
    version // the state object is mutated in place; this changing value makes the strip redraw after each move
    Column(modifier.brassEdgedWood().padding(horizontal = 12.dp, vertical = 8.dp).testTag("player-strip"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                Text("${s.cardCount(p)} cards", color = Color(0xFFCDBFA6), fontSize = 11.sp, modifier = Modifier.width(52.dp))
                Text("${if (p == me) s.victoryPoints(p) else s.publicVictoryPoints(p)}", color = Color(0xFFF1D58A), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            }
        }
    }
}

/** Your hand along the bottom edge (no frame): the illustrated cards you hold, each with its count. */
@Composable
fun HandRow(s: GameState, me: Int, version: Int, modifier: Modifier = Modifier) {
    version // see PlayerStrip
    Row(modifier.testTag("hand-row"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        for (r in 0 until CARD_TYPES) {
            val n = s.hand(me, r)
            if (n == 0) continue
            Box(contentAlignment = Alignment.TopEnd) {
                Image(painterResource(CARD_ART[r]), RESOURCE_NAMES[r], Modifier.size(width = 50.dp, height = 70.dp))
                Text("$n", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(2.dp).background(Color(0xE62A1B10), CircleShape).border(1.dp, Color(0xFFC9A04A), CircleShape).padding(horizontal = 6.dp))
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
