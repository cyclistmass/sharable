package com.mauro.wgbg

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decode close to the draw size, keeping nodpi pixel geometry and the original aspect ratio.
 * Not for sprite strips unless the caller also adjusts its frame coordinates.
 */
internal fun decodeRenderBitmap(resources: Resources, @DrawableRes id: Int,
                                targetWidth: Int, targetHeight: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
    BitmapFactory.decodeResource(resources, id, bounds)
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inSampleSize = renderDecodeSample(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
    }
    return requireNotNull(BitmapFactory.decodeResource(resources, id, options)) { "Drawable could not be decoded" }
}

/** Width-sized texture decode; retained aspect ratio means no full-size intermediate is necessary. */
internal fun decodeRenderTexture(resources: Resources, @DrawableRes id: Int, width: Int): Bitmap {
    require(width > 0)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
    BitmapFactory.decodeResource(resources, id, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    val height = (width.toLong() * bounds.outHeight / bounds.outWidth).toInt().coerceAtLeast(1)
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inSampleSize = renderDecodeSample(bounds.outWidth, bounds.outHeight, width, height)
    }
    val sampled = requireNotNull(BitmapFactory.decodeResource(resources, id, options))
    val scaled = Bitmap.createScaledBitmap(sampled, width, height, true)
    if (scaled !== sampled) sampled.recycle() // intermediate has never escaped this function
    return scaled
}

/** Owner integration point: composition never waits for decode; null means the asset is loading. */
@Composable
fun rememberRenderBitmap(@DrawableRes id: Int, targetWidth: Int, targetHeight: Int): State<ImageBitmap?> {
    val resources = LocalContext.current.resources
    return key(resources, id, targetWidth, targetHeight) {
        produceState<ImageBitmap?>(null) {
            value = withContext(Dispatchers.IO) {
                decodeRenderBitmap(resources, id, targetWidth, targetHeight).asImageBitmap()
            }
        }
    }
}
