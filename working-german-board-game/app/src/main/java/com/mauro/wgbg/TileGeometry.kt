package com.mauro.wgbg

import kotlin.math.*

/** Scalar-only math shared by the static renderer and allocation-free life pass. */
object TileGeometry {
    const val A1 = 1
    const val A2 = 2
    const val OPTION = A1 // Mauro's palette choice: one-line switch.
    const val LIFE_DENSITY = 3 // 0 disables; at most 6 tiny marks per tile.
    fun perspective(ly: Float, squash: Float, tilt: Float): Float =
        1f + .22f * sin(tilt) * ly * squash / 4.2f
    fun projectX(lx: Float, ly: Float, center: Float, scale: Float, tilt: Float): Float =
        center + lx * scale * perspective(ly, cos(tilt), tilt)
    fun projectY(ly: Float, center: Float, scale: Float, tilt: Float): Float =
        center + ly * cos(tilt) * scale * perspective(ly, cos(tilt), tilt)
    fun squash(value: Float): Float = if (OPTION == A2) {
        val angle = acos(value.coerceIn(0f, 1f)) * 180f / PI.toFloat()
        cos((if (angle < 17.5f) 0f else if (angle < 47.5f) 35f else 60f) * PI.toFloat() / 180f)
    } else value
    fun cornerX(i: Int): Float = cos((-90 + i * 60) * PI.toFloat() / 180f) * .93f
    fun cornerY(i: Int): Float = sin((-90 + i * 60) * PI.toFloat() / 180f) * .93f
    fun depth(scale: Float, squash: Float): Float = scale * (.055f + .20f * sqrt((1f - squash * squash).coerceAtLeast(0f)))
    fun light(i: Int): Float {
        val a = (-60 + i * 60) * PI.toFloat() / 180f
        return .42f + .22f * (-cos(a) - sin(a)) / sqrt(2f)
    }
    const val PERIOD = 8f
    fun wave(time: Float, tile: Int, mark: Int): Float =
        sin(((time.toDouble() % PERIOD) / PERIOD + ((tile * 17 + mark * 7) % 31) / 31.0) * 2 * PI).toFloat()
    fun marks(density: Int): Int = density.coerceIn(0, 6)
}
