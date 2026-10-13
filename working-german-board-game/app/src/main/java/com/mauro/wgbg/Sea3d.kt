package com.mauro.wgbg

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The 3D sea props (#63): sloops and the Raiders' ship rendered at 16 headings and 8 tilt steps ([SeaArt]). Only the
 * frames for the current tilt step are kept in memory; they load on first use and are dropped when the tilt changes.
 */
class SeaSprites3d(private val res: Resources) {
    private var step = -1
    /** The frames in use, least recently drawn dropped first (the whale's 288 px frames add up fast). */
    private val cache = object : LinkedHashMap<Int, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?) = size > MAX_FRAMES
    }

    /** The frame of [table] for [heading] (0 until [SeaArt.HEADINGS]) at [tiltStep] (0 until [SeaArt.STEPS]). */
    fun frame(table: Array<IntArray>, heading: Int, tiltStep: Int): ImageBitmap {
        if (tiltStep != step) { cache.clear(); step = tiltStep }
        val id = table[heading][tiltStep]
        return cache.getOrPut(id) { ImageBitmap.imageResource(res, id) }
    }

    companion object {
        /** Frames kept at once: every heading of the sloop, its trim and wake, the Raiders and the gull, plus the whale's. */
        const val MAX_FRAMES = 160
        /** A sloop frame spans this many hex units (its hull about 0.76, as long as the old side-view boat's). */
        const val SLOOP_FRAME = 1.08f
        /** The Raiders' frame spans this many hex units (the hull about 1.0, like the old ship). */
        const val RAIDERS_FRAME = 1.4f
        /** A gull frame spans this many hex units (the wingspan about a quarter hex, like the painted gull). */
        const val GULL_FRAME = 0.32f

        /**
         * The rendered heading (bow direction on screen, counter-clockwise from east in 22.5 degree steps) for a
         * course of [radians] on the sea (y down), seen with the board squashed by [squash] (cos of the tilt).
         */
        fun headingIndex(radians: Float, squash: Float): Int {
            val screen = atan2(-sin(radians) * squash, cos(radians))
            val i = (screen / (2 * PI / SeaArt.HEADINGS)).roundToInt()
            return ((i % SeaArt.HEADINGS) + SeaArt.HEADINGS) % SeaArt.HEADINGS
        }

        /** The nearest rendered tilt step for [tilt] radians (tilt = 0.96 * i / 7). */
        fun tiltStep(tilt: Float): Int = (tilt / 0.96f * 7f).roundToInt().coerceIn(0, SeaArt.STEPS - 1)
    }
}

/**
 * Draws one 3D sea prop centred on [at] (its waterline), [side] px square: the [wake] at [wakeAlpha], then the
 * [body] (tinted to [tint] when given, as a sloop's sail) and the untinted [trim].
 */
internal fun DrawScope.drawSeaProp(sprites: SeaSprites3d, at: Offset, side: Float, heading: Int, tiltStep: Int,
                                   body: Array<IntArray>, trim: Array<IntArray>? = null, wake: Array<IntArray>? = null,
                                   tint: Color? = null, wakeAlpha: Float = 0.7f) {
    if (side < 2f || !side.isFinite()) return
    val dst = IntOffset((at.x - side / 2).toInt(), (at.y - side / 2).toInt())
    val size = IntSize(side.toInt(), side.toInt())
    fun layer(table: Array<IntArray>, alpha: Float = 1f, filter: ColorFilter? = null) {
        val img = sprites.frame(table, heading, tiltStep)
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), dst, size, alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.Medium)
    }
    wake?.let { layer(it, wakeAlpha) }
    layer(body, filter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) })
    trim?.let { layer(it) }
}
