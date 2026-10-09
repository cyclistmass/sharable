package com.mauro.wgbg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    /** Share of a sprite frame the cube's visible face fills; a die of size d draws its frame at d / CUBE_SHARE. */
    const val CUBE_SHARE = 0.42f // the resting (square) cube is 42% of its frame wide

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
fun RollingDice(die1: Int, die2: Int, rollKey: Any?, rest: Offset?, modifier: Modifier = Modifier,
                /** The panel's dice icons (root coordinates): each die lands exactly on its icon, at its size. */
                slots: Pair<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect>? = null,
                /** True while the dice are in the air; the panel hides its resting dice until they land. */
                onFlying: (Boolean) -> Unit = {}) {
    var origin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val t = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        if (rollKey == null || die1 <= 0) return@LaunchedEffect
        t.snapTo(0f)
        onFlying(true)
        var landed = false
        try {
            t.animateTo(1f, tween((DiceSprites.FALL_S * 1000).toInt(), easing = LinearEasing)) {
                // Hand over to the panel's resting dice the moment both dice settle.
                if (!landed && value >= 0.93f) { landed = true; onFlying(false) }
            }
        } finally { onFlying(false) }
    }
    if (die1 <= 0 || rest == null) return
    val ivory = ImageBitmap.imageResource(R.drawable.dice_ivory)
    val red = ImageBitmap.imageResource(R.drawable.dice_red)
    // Where this roll is thrown from: a direction and distance picked from the roll itself (same roll, same throw).
    val rnd = remember(rollKey) { java.util.Random(rollKey.hashCode().toLong() * 7919L + 17) }
    val angle = remember(rollKey) { rnd.nextFloat() * 2 * Math.PI.toFloat() }
    val reach = remember(rollKey) { 0.55f + rnd.nextFloat() * 0.5f }
    Canvas(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        val side = DiceSprites.SIDE_DP.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        val half = kotlin.math.hypot(size.width / 2, size.height / 2)
        val start = center + Offset(kotlin.math.cos(angle), kotlin.math.sin(angle)) * (half * reach)
        val land = rest + Offset(-side * 2.25f, -side * 0.8f)
        val dir = (land - start).let { it / it.getDistance().coerceAtLeast(1f) }
        val perp = Offset(-dir.y, dir.x)
        fun drawAt(sheet: ImageBitmap, face: Int, p: Float, lead: Float, frameSpin: Int, spin: Float) {
            val u = DiceSprites.fall(p)                                  // flies in, then settles with small bounces
            val hop = kotlin.math.sin(p.coerceAtMost(0.62f) / 0.62f * Math.PI.toFloat())
            val pos = start + (land + Offset(lead, 0f) - start) * u + perp * (hop * side * 0.6f)
            drawDie(sheet, DiceSprites.frame(p, face, frameSpin), pos, side * (1f + 0.35f * hop), spin)                             // a little bigger mid-air: it's up off the table
        }
        val p = t.value
        val q = ((p - 0.07f) / 0.93f).coerceIn(0f, 1f)
        if (slots != null) {
            if (p >= 0.86f && q >= 0.86f) return@Canvas   // settling: the panel's own (themed) dice take over
            // Land each die exactly on its icon: the cube fills ~60% of a sprite frame, so scale to match.
            fun settle(r: androidx.compose.ui.geometry.Rect, u: Float, frame: Int, sheet: ImageBitmap, spin: Float) {
                val restSide = r.width * 0.86f / DiceSprites.CUBE_SHARE
                val target = r.center - origin
                val from = start
                val e = DiceSprites.fall(u)
                val hop = kotlin.math.sin(u.coerceAtMost(0.62f) / 0.62f * Math.PI.toFloat())
                val c = from + (target - from) * e + perp * (hop * restSide * 0.6f)
                val sideNow = restSide * (1f + 0.35f * hop)
                drawDie(sheet, frame, c - Offset(sideNow / 2, sideNow / 2), sideNow, spin)
            }
            settle(slots.first, p, DiceSprites.frame(p, die1, 3), red, (1 - p) * 420f)
            settle(slots.second, q, DiceSprites.frame(q, die2, 9), ivory, (1 - q) * -480f)
            return@Canvas
        }
        drawAt(red, die1, p, 0f, 3, (1 - p) * 420f)
        drawAt(ivory, die2, q, side * 1.12f, 9, (1 - q) * -480f)
    }
}

private fun DrawScope.drawDie(sheet: ImageBitmap, frame: Int, topLeft: Offset, side: Float, spinDeg: Float) {
    val f = DiceSprites.FRAME
    rotate(spinDeg, pivot = topLeft + Offset(side / 2, side / 2)) {
        drawImage(sheet, IntOffset(frame * f, 0), IntSize(f, f), IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
            IntSize(side.toInt(), side.toInt()), filterQuality = FilterQuality.Medium)
    }
}
