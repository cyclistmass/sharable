package com.mauro.wgbg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * Two dice (Mauro's models, pre-rendered by tools/art/render_dice.py) tumble down the right side of
 * the board and come to rest showing the roll. Sheets: 16 tumble frames, then faces 1..6, 128 px each.
 */
object DiceSprites {
    const val FRAME = 128
    const val TUMBLE = 16
    /** Seconds from release to rest. */
    const val FALL_S = 1.15f

    /** Sheet frame for a die at animation time [t] (0..1) that lands on [face]; [spin] offsets the tumble. */
    fun frame(t: Float, face: Int, spin: Int): Int =
        if (t >= 0.86f) TUMBLE + (face - 1).coerceIn(0, 5)
        else (spin + (t * 26).toInt()) % TUMBLE

    /** Height as a share of the fall (0 at the top, 1 at rest), with two small bounces at the end. */
    fun fall(t: Float): Float {
        if (t < 0.62f) { val u = t / 0.62f; return u * u }
        val u = (t - 0.62f) / 0.38f
        return 1f - abs(cos(u * 2.5f * Math.PI.toFloat())) * 0.12f * (1f - u) * (1f - u)
    }
}

@Composable
fun RollingDice(die1: Int, die2: Int, rollKey: Any?, modifier: Modifier = Modifier) {
    val ivory = ImageBitmap.imageResource(R.drawable.dice_ivory)
    val red = ImageBitmap.imageResource(R.drawable.dice_red)
    val t = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        if (rollKey == null || die1 <= 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween((DiceSprites.FALL_S * 1000).toInt(), easing = LinearEasing))
    }
    if (die1 <= 0) return
    Canvas(modifier) {
        val p = t.value
        val side = min(size.width * 0.9f, size.height * 0.16f)
        val drop = DiceSprites.fall(p)
        val restY = size.height * 0.55f
        val y0 = -side
        // The red die lands a little higher and leads; the ivory one trails.
        drawDie(red, DiceSprites.frame(p, die1, 3), Offset(size.width / 2 - side * 0.55f, y0 + (restY - y0) * drop), side, (1 - p) * 220f)
        val q = ((p - 0.06f) / 0.94f).coerceIn(0f, 1f)
        drawDie(ivory, DiceSprites.frame(q, die2, 9), Offset(size.width / 2 - side * 0.45f, y0 + (restY + side * 1.05f - y0) * DiceSprites.fall(q)), side, (1 - q) * -260f)
    }
}

private fun DrawScope.drawDie(sheet: ImageBitmap, frame: Int, topLeft: Offset, side: Float, spinDeg: Float) {
    val f = DiceSprites.FRAME
    rotate(spinDeg, pivot = topLeft + Offset(side / 2, side / 2)) {
        drawImage(sheet, IntOffset(frame * f, 0), IntSize(f, f), IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
            IntSize(side.toInt(), side.toInt()), filterQuality = FilterQuality.Medium)
    }
}
