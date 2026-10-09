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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.imageResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Card-border colors for Timber, Clay, Fleece, Grain, Stone (matches the card designs). */
internal val CARD_COLORS = listOf(Color(0xFF2E7D32), Color(0xFFBF5B30), Color(0xFF6E9A45), Color(0xFFC9962C), Color(0xFF4A5E80))
private val FORTUNE_COLOR = Color(0xFF5B3A6E)
internal val CARD_FACE = Color(0xFFF2E7D3)
private val HUD_TEXT = Color(0xFFEDE6D8)

/** The roll and what came of it, pinned to a corner of the board. */
@Composable
fun TurnHud(report: TurnReport?, previous: TurnReport?, names: List<String> = PLAYER_NAMES, modifier: Modifier = Modifier,
            /** Reports where the two dice are (root coordinates), so thrown dice can fly to them. */
            onDiceSlots: ((androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
            /** False while thrown dice are still in the air (the resting dice appear when they land). */
            showDice: Boolean = true,
            /** Dice set: false = Normal (carved red + ivory), true = Pirate (ship-timber + bone, dark brass pips). */
            pirate: Boolean = false) {
    if (report == null) return
    // Fixed size, so the dice always sit in the same spot; longer turns scroll inside.
    Column(
        modifier.size(width = 360.dp, height = 210.dp)
            .brassEdgedWood()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val color = PLAYER_COLORS[report.player]
            run {
                var r1 by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                Die3d(report.die1, red = true, pirate = pirate, pad = color, visible = showDice && report.rolled, modifier = Modifier.onGloballyPositioned { r1 = it.boundsInRoot() })
                Die3d(report.die2, red = false, pirate = pirate, pad = color, visible = showDice && report.rolled, modifier = Modifier.onGloballyPositioned { c -> r1?.let { a -> onDiceSlots?.invoke(a, c.boundsInRoot()) } })
                if (report.rolled && showDice) Text("= ${report.die1 + report.die2}", color = HUD_TEXT, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(if (names[report.player] == "You") "Your turn" else "${names[report.player]}'s turn", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
private fun Die(value: Int, edge: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(34.dp)) {
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

/** A resting 3D die (the same sprites the throw uses) at the panel's dice size, showing [value] on top. */
@Composable
private fun Die3d(value: Int, red: Boolean, pirate: Boolean, pad: Color, visible: Boolean, modifier: Modifier = Modifier) {
    val face = if (visible) androidx.compose.ui.graphics.ImageBitmap.imageResource(diceFace(red, pirate, value)) else null
    val outline = androidx.compose.runtime.remember { androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f) }
    Canvas(modifier.size(DIE_DP.dp)) {
        // A pad in the roller's colour the die sits on (shown even while the dice are in the air).
        val r = CornerRadius(size.minDimension * 0.22f)
        drawRoundRect(pad.copy(alpha = 0.85f), topLeft = Offset(-size.width * 0.06f, size.height * 0.08f), size = Size(size.width * 1.12f, size.height * 0.98f), cornerRadius = r)
        drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(-size.width * 0.06f, size.height * 0.08f), size = Size(size.width * 1.12f, size.height * 0.98f), cornerRadius = r,
            style = outline)
        if (face == null) return@Canvas
        // The hand-made die (Codex art), square on the pad with the roll on top.
        val side = size.width * 0.9f
        drawImage(face, dstOffset = androidx.compose.ui.unit.IntOffset((size.width / 2 - side / 2).toInt(), (size.height * 0.57f - side / 2).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(side.toInt(), side.toInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
    }
}

/** The resting-face image for a die: [red] or ivory/bone, Normal or [pirate] set, showing [value]. */
fun diceFace(red: Boolean, pirate: Boolean, value: Int): Int {
    val v = value.coerceIn(1, 6) - 1
    return when {
        pirate && red -> DICE_PR[v]; pirate -> DICE_PB[v]; red -> DICE_NR[v]; else -> DICE_NI[v]
    }
}
private val DICE_NR = listOf(R.drawable.dice_nr_1, R.drawable.dice_nr_2, R.drawable.dice_nr_3, R.drawable.dice_nr_4, R.drawable.dice_nr_5, R.drawable.dice_nr_6)
private val DICE_NI = listOf(R.drawable.dice_ni_1, R.drawable.dice_ni_2, R.drawable.dice_ni_3, R.drawable.dice_ni_4, R.drawable.dice_ni_5, R.drawable.dice_ni_6)
private val DICE_PR = listOf(R.drawable.dice_pr_1, R.drawable.dice_pr_2, R.drawable.dice_pr_3, R.drawable.dice_pr_4, R.drawable.dice_pr_5, R.drawable.dice_pr_6)
private val DICE_PB = listOf(R.drawable.dice_pb_1, R.drawable.dice_pb_2, R.drawable.dice_pb_3, R.drawable.dice_pb_4, R.drawable.dice_pb_5, R.drawable.dice_pb_6)

/** Size of the panel's dice; thrown dice land at exactly this size. */
const val DIE_DP = 36
