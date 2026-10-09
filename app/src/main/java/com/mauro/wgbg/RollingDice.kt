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
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos

/**
 * Two dice (Mauro's models, pre-rendered by tools/art/render_dice.py) roll across the turn HUD and come
 * to rest on its top-right corner showing the roll. Sheets: 16 tumble frames, then faces 1..6, 128 px each.
 */
object DiceSprites {
    const val FRAME = 256
    const val TUMBLE = 16
    /** Seconds from release to rest. */
    const val FALL_S = 1.6f
    /** Die size at rest, dp. */
    const val SIDE_DP = 55

    /** Sheet frame for a die at animation time [t] (0..1) that lands on [face]; [spin] offsets the tumble. */
    fun frame(t: Float, face: Int, spin: Int): Int =
        if (t >= 0.86f) TUMBLE + (face - 1).coerceIn(0, 5)
        else (spin + (t * 30).toInt()) % TUMBLE

    /** Height as a share of the fall (0 at the top, 1 at rest), with two small bounces at the end. */
    fun fall(t: Float): Float {
        if (t < 0.62f) { val u = t / 0.62f; return u * u }
        val u = (t - 0.62f) / 0.38f
        return 1f - abs(cos(u * 2.5f * Math.PI.toFloat())) * 0.12f * (1f - u) * (1f - u)
    }
}

/**
 * Full-screen dice throw: each roll comes in from a random direction around the board, from more than
 * halfway out (often off screen), tumbles in and settles face-up at [rest] (the HUD's top-right corner,
 * in this canvas's coordinates). Pure decoration, no physics with anything else.
 */
@Composable
fun RollingDice(die1: Int, die2: Int, rollKey: Any?, rest: Offset?, modifier: Modifier = Modifier) {
    val ivory = ImageBitmap.imageResource(R.drawable.dice_ivory)
    val red = ImageBitmap.imageResource(R.drawable.dice_red)
    val t = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        if (rollKey == null || die1 <= 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween((DiceSprites.FALL_S * 1000).toInt(), easing = LinearEasing))
    }
    if (die1 <= 0 || rest == null) return
    // Where this roll is thrown from: a direction and distance picked from the roll itself (same roll, same throw).
    val rnd = remember(rollKey) { java.util.Random(rollKey.hashCode().toLong() * 7919L + 17) }
    val angle = remember(rollKey) { rnd.nextFloat() * 2 * Math.PI.toFloat() }
    val reach = remember(rollKey) { 0.55f + rnd.nextFloat() * 0.5f }
    Canvas(modifier) {
        val side = DiceSprites.SIDE_DP.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        val half = kotlin.math.hypot(size.width / 2, size.height / 2)
        val start = center + Offset(kotlin.math.cos(angle), kotlin.math.sin(angle)) * (half * reach)
        val land = rest + Offset(-side * 2.25f, -side * 0.8f)
        val dir = (land - start).let { it / it.getDistance().coerceAtLeast(1f) }
        val perp = Offset(-dir.y, dir.x)
        fun at(p: Float, lead: Float): Pair<Offset, Float> {
            val u = DiceSprites.fall(p)                                  // flies in, then settles with small bounces
            val hop = kotlin.math.sin(p.coerceAtMost(0.62f) / 0.62f * Math.PI.toFloat())
            val pos = start + (land + Offset(lead, 0f) - start) * u + perp * (hop * side * 0.6f)
            return pos to (1f + 0.35f * hop)                             // a little bigger mid-air: it's up off the table
        }
        val p = t.value
        val (a, ka) = at(p, 0f)
        drawDie(red, DiceSprites.frame(p, die1, 3), a, side * ka, (1 - p) * 420f)
        val q = ((p - 0.07f) / 0.93f).coerceIn(0f, 1f)
        val (b, kb) = at(q, side * 1.12f)
        drawDie(ivory, DiceSprites.frame(q, die2, 9), b, side * kb, (1 - q) * -480f)
    }
}

private fun DrawScope.drawDie(sheet: ImageBitmap, frame: Int, topLeft: Offset, side: Float, spinDeg: Float) {
    val f = DiceSprites.FRAME
    rotate(spinDeg, pivot = topLeft + Offset(side / 2, side / 2)) {
        drawImage(sheet, IntOffset(frame * f, 0), IntSize(f, f), IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
            IntSize(side.toInt(), side.toInt()), filterQuality = FilterQuality.Medium)
    }
}
