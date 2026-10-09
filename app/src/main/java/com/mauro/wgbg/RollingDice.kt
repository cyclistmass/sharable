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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
 * Two hand-made dice (Codex art via mauro-7a, Normal or Pirate set) are thrown across the screen and land
 * square on the turn panel's dice icons. In the air they cycle through 4 tumbling renders while spinning;
 * at the first touch-down they turn into the panel's resting face for the roll, so the hand-over is seamless.
 */
object DiceSprites {
    const val TUMBLE = 4
    /** Seconds from release to rest. */
    const val FALL_S = 1.6f
    /** Die size at rest when the panel's slots aren't known yet, dp. */
    const val SIDE_DP = 55
    /** Share of a tumbling render's width the cube fills (the resting faces fill their whole image). */
    const val TUMBLE_SHARE = 0.70f
    /** Animation time of the first touch-down: from here on the die shows its rolled face, square. */
    const val LAND = 0.62f
    /** Animation time when both dice have settled and the panel's own dice take over. */
    const val HANDOVER = 0.97f

    /** Tumbling render (0 until TUMBLE) for a die at animation time [t], or -1 once it shows its face. */
    fun frame(t: Float, spin: Int): Int = if (t >= LAND) -1 else (spin + (t * 22).toInt()) % TUMBLE

    /** Spin in degrees: fast in the air, slowing to exactly 0 (square on the pad) at the first touch-down. */
    fun spin(t: Float, turns: Float): Float = if (t >= LAND) 0f else (1f - t / LAND).let { it * it } * turns * 360f

    /** Share of the way from the throw to the pad: arrives exactly at the first touch-down and stays there. */
    fun fall(t: Float): Float = if (t >= LAND) 1f else (t / LAND).let { it * it }

    /** Height of the in-place bounces after touch-down, as a share of the die's size (0 = on the pad). */
    fun bounce(t: Float): Float {
        if (t <= LAND) return 0f
        val u = (t - LAND) / (1f - LAND)
        return abs(kotlin.math.sin(u * 2f * Math.PI.toFloat())) * 0.22f * (1f - u) * (1f - u)
    }
}

private val TUMBLE_NR = listOf(R.drawable.dice_tumble_nr_1, R.drawable.dice_tumble_nr_2, R.drawable.dice_tumble_nr_3, R.drawable.dice_tumble_nr_4)
private val TUMBLE_NI = listOf(R.drawable.dice_tumble_ni_1, R.drawable.dice_tumble_ni_2, R.drawable.dice_tumble_ni_3, R.drawable.dice_tumble_ni_4)
private val TUMBLE_PR = listOf(R.drawable.dice_tumble_pr_1, R.drawable.dice_tumble_pr_2, R.drawable.dice_tumble_pr_3, R.drawable.dice_tumble_pr_4)
private val TUMBLE_PB = listOf(R.drawable.dice_tumble_pb_1, R.drawable.dice_tumble_pb_2, R.drawable.dice_tumble_pb_3, R.drawable.dice_tumble_pb_4)

/** The 4 tumbling renders of one die in a theme (paired like [diceFace]: red + ivory, or red timber + bone). */
fun diceTumble(red: Boolean, pirate: Boolean): List<Int> = when {
    pirate && red -> TUMBLE_PR; pirate -> TUMBLE_PB; red -> TUMBLE_NR; else -> TUMBLE_NI
}

/**
 * Full-screen dice throw: each roll comes in from a random direction around the board, from more than
 * halfway out (often off screen), tumbles in and settles square on the panel's dice icons ([slots]), or
 * beside [rest] (the HUD's top-right corner) before those are measured. Pure decoration.
 */
@Composable
fun RollingDice(die1: Int, die2: Int, rollKey: Any?, rest: Offset?, modifier: Modifier = Modifier,
                /** The panel's dice icons (root coordinates): each die lands exactly on its icon, at its size. */
                slots: Pair<Rect, Rect>? = null,
                /** Settings > Theme: the Pirate dice instead of the Normal ones. */
                pirate: Boolean = false,
                /** True while the dice are in the air; the panel hides its resting dice until they land. */
                onFlying: (Boolean) -> Unit = {}) {
    var origin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val redTumble = diceTumble(red = true, pirate).map { ImageBitmap.imageResource(it) }
    val ivoryTumble = diceTumble(red = false, pirate).map { ImageBitmap.imageResource(it) }
    val redFace = ImageBitmap.imageResource(diceFace(red = true, pirate, die1.coerceIn(1, 6)))
    val ivoryFace = ImageBitmap.imageResource(diceFace(red = false, pirate, die2.coerceIn(1, 6)))
    val t = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        if (rollKey == null || die1 <= 0) return@LaunchedEffect
        t.snapTo(0f)
        onFlying(true)
        var landed = false
        try {
            t.animateTo(1f, tween((DiceSprites.FALL_S * 1000).toInt(), easing = LinearEasing)) {
                // Hand over to the panel's resting dice once both have settled (the second die lags 7%).
                if (!landed && value >= DiceSprites.HANDOVER) { landed = true; onFlying(false) }
            }
        } finally { onFlying(false) }
    }
    if (die1 <= 0 || rest == null) return
    // Where this roll is thrown from: a direction and distance picked from the roll itself (same roll, same throw).
    val rnd = remember(rollKey) { java.util.Random(rollKey.hashCode().toLong() * 7919L + 17) }
    val angle = remember(rollKey) { rnd.nextFloat() * 2 * Math.PI.toFloat() }
    val reach = remember(rollKey) { 0.55f + rnd.nextFloat() * 0.5f }
    Canvas(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        val p = t.value
        if (p >= DiceSprites.HANDOVER) return@Canvas        // settled: the panel's own dice are showing
        val q = ((p - 0.07f) / 0.93f).coerceIn(0f, 1f)
        val center = Offset(size.width / 2, size.height / 2)
        val half = kotlin.math.hypot(size.width / 2, size.height / 2)
        val start = center + Offset(cos(angle), kotlin.math.sin(angle)) * (half * reach)
        // Each die's resting square: its panel icon, or beside the HUD's corner until the icons are measured.
        val side = DiceSprites.SIDE_DP.dp.toPx()
        val rest1 = slots?.first?.translate(-origin) ?: Rect(rest + Offset(-side * 2.25f, -side * 0.8f), Size(side, side))
        val rest2 = slots?.second?.translate(-origin) ?: rest1.translate(Offset(side * 1.12f, 0f))
        val dir = (rest1.center - start).let { it / it.getDistance().coerceAtLeast(1f) }
        val perp = Offset(-dir.y, dir.x)
        drawDie(rest1, p, start, perp, redTumble, redFace, spin = 1, turns = 1.4f)
        drawDie(rest2, q, start, perp, ivoryTumble, ivoryFace, spin = 3, turns = -1.6f)
    }
}

/** One die at animation time [u], flying from [start] to its resting square [r] (drawn as the panel draws it). */
private fun DrawScope.drawDie(r: Rect, u: Float, start: Offset, perp: Offset, tumble: List<ImageBitmap>, face: ImageBitmap,
                              spin: Int, turns: Float) {
    val cube = r.width * DIE_FACE_SHARE                                  // exactly the size Die3d draws it
    val hop = kotlin.math.sin(u.coerceAtMost(DiceSprites.LAND) / DiceSprites.LAND * Math.PI.toFloat())
    val b = DiceSprites.bounce(u)
    val c = start + (r.center - start) * DiceSprites.fall(u) + perp * (hop * cube * 0.6f) - Offset(0f, b * cube)
    val lift = 1f + 0.35f * hop + 0.3f * b                               // a little bigger in the air: it's up off the table
    val f = DiceSprites.frame(u, spin)
    val img = if (f < 0) face else tumble[f]
    val drawn = cube * lift / (if (f < 0) 1f else DiceSprites.TUMBLE_SHARE)
    rotate(DiceSprites.spin(u, turns), pivot = c) {
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height),
            IntOffset((c.x - drawn / 2).toInt(), (c.y - drawn / 2).toInt()), IntSize(drawn.toInt(), drawn.toInt()),
            filterQuality = FilterQuality.Medium)
    }
}
