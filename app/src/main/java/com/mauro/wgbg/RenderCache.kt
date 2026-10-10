package com.mauro.wgbg

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

/** Two-size render-thread cache: hits neither box integer keys nor replace map entries. */
internal class RecentRenderSizes<T : Any> {
    private var newestSize = -1
    private var olderSize = -1
    private var newest: T? = null
    private var older: T? = null

    fun get(size: Int): T? {
        if (size == newestSize) return newest
        if (size != olderSize) return null
        val saved = newest
        newest = older
        older = saved
        val savedSize = newestSize
        newestSize = olderSize
        olderSize = savedSize
        return newest
    }

    fun put(size: Int, value: T) {
        if (size == newestSize) { newest = value; return }
        if (size == olderSize) { get(size); newest = value; return }
        olderSize = newestSize
        older = newest
        newestSize = size
        newest = value
    }
}

/** Reuses the exact existing 15% quantization for tiles drawn at the same camera scale. */
internal class RenderSizeLadder {
    private var previousScale = Float.NaN
    private var previousPixels = 0
    fun pixels(scale: Float): Int {
        if (scale == previousScale) return previousPixels
        previousScale = scale
        previousPixels = ceil(1.15.pow(ceil(ln(scale.coerceAtLeast(1f).toDouble()) / LN_LADDER))).toInt()
        return previousPixels
    }
    private companion object { val LN_LADDER = ln(1.15) }
}

/** Largest power-of-two sample that still covers both requested dimensions. */
internal fun renderDecodeSample(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
    var sample = 1
    while (width / sample / 2 >= targetWidth && height / sample / 2 >= targetHeight) sample *= 2
    return sample
}
