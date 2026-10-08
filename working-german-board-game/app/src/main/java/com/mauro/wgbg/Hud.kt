package com.mauro.wgbg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Card-border colors for Timber, Clay, Fleece, Grain, Stone (matches the card designs). */
private val CARD_COLORS = listOf(Color(0xFF2E7D32), Color(0xFFBF5B30), Color(0xFF6E9A45), Color(0xFFC9962C), Color(0xFF4A5E80))
private val FORTUNE_COLOR = Color(0xFF5B3A6E)
private val CARD_FACE = Color(0xFFF2E7D3)
private val HUD_TEXT = Color(0xFFEDE6D8)

/** The roll and what came of it, pinned to a corner of the board. */
@Composable
fun TurnHud(report: TurnReport?, previous: TurnReport?, modifier: Modifier = Modifier) {
    if (report == null) return
    Column(
        modifier.widthIn(max = 360.dp)
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val color = PLAYER_COLORS[report.player]
            if (report.rolled) {
                Die(report.die1, color)
                Die(report.die2, color)
                Text("= ${report.die1 + report.die2}", color = HUD_TEXT, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text("${PLAYER_NAMES[report.player]}'s turn", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        for (line in report.production) Text(line, color = HUD_TEXT, fontSize = 12.sp, maxLines = 1)
        for (line in report.events.takeLast(4)) Text(line, color = HUD_TEXT.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
        if (previous != null && previous.rolled) {
            Text(
                "Last turn: ${PLAYER_NAMES[previous.player]} rolled ${previous.die1 + previous.die2}. " +
                    previous.production.joinToString("; "),
                color = HUD_TEXT.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2,
            )
        }
    }
}

/** A small ivory die with pips, edged in the roller's color. */
@Composable
private fun Die(value: Int, edge: Color) {
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension * 0.18f
        drawRoundRect(Color(0xFFFBF5E8), cornerRadius = CornerRadius(r))
        drawRoundRect(edge, cornerRadius = CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.1f))
        val q = size.minDimension / 4f
        val spots = when (value) {
            1 -> listOf(2 to 2)
            2 -> listOf(1 to 1, 3 to 3)
            3 -> listOf(1 to 1, 2 to 2, 3 to 3)
            4 -> listOf(1 to 1, 3 to 1, 1 to 3, 3 to 3)
            5 -> listOf(1 to 1, 3 to 1, 2 to 2, 1 to 3, 3 to 3)
            else -> listOf(1 to 1, 3 to 1, 1 to 2, 3 to 2, 1 to 3, 3 to 3)
        }
        for ((x, y) in spots) drawCircle(Color(0xFF2B2118), size.minDimension * 0.09f, Offset(x * q, y * q))
    }
}

/** A player's hand as mini cards: one per resource they hold with a count, plus face-down Fortune cards. */
@Composable
fun MiniHand(hand: List<Int>, fortune: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        hand.forEachIndexed { r, n -> if (n > 0) MiniCard(CARD_COLORS[r], RESOURCE_NAMES[r].take(2), n, face = CARD_FACE) }
        if (fortune > 0) MiniCard(FORTUNE_COLOR, "F", fortune, face = Color(0xFF8E6BA3))
        if (hand.all { it == 0 } && fortune == 0) Text("no cards", color = HUD_TEXT.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun MiniCard(edge: Color, label: String, count: Int, face: Color) {
    Box(
        Modifier.size(width = 22.dp, height = 31.dp)
            .background(face, RoundedCornerShape(4.dp))
            .border(2.dp, edge, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = edge, fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp)
            Text("$count", color = Color(0xFF2B2118), fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
        }
    }
}
