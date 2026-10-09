package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Static-layer-only geometry. Light remains fixed at the upper left. */
object TileRelief {
    private val angles = java.util.IdentityHashMap<ImageBitmap, Array<ImageBitmap?>>()
    /** Only invoked while recording: A2 retains at most three raster angles per source. */
    fun face(source: ImageBitmap, squash: Float): ImageBitmap {
        if (TileGeometry.OPTION == TileGeometry.A1) return source
        val q = TileGeometry.squash(squash)
        val i = if (q > .95f) 0 else if (q > .7f) 1 else 2
        if (!angles.containsKey(source) && angles.size >= 12) angles.clear()
        val views = angles.getOrPut(source) { arrayOfNulls(3) }
        return views[i] ?: android.graphics.Bitmap.createScaledBitmap(source.asAndroidBitmap(), source.width,
            (source.height * q).toInt().coerceAtLeast(1), true).asImageBitmap().also { views[i] = it }
    }
    fun sides(scope: DrawScope, center: Offset, scale: Float, squash: Float, base: Color) {
        val depth = TileGeometry.depth(scale, squash)
        for (i in 1..3) {
            val ax = center.x + TileGeometry.cornerX(i) * scale
            val ay = center.y + TileGeometry.cornerY(i) * scale * squash
            val bx = center.x + TileGeometry.cornerX(i + 1) * scale
            val by = center.y + TileGeometry.cornerY(i + 1) * scale * squash
            val wall = Path().apply { moveTo(ax, ay); lineTo(bx, by); lineTo(bx, by + depth); lineTo(ax, ay + depth); close() }
            val f = TileGeometry.light(i)
            scope.drawPath(wall, Color(base.red * f, base.green * f, base.blue * f, 1f))
            scope.drawLine(Color(0xFF30251B), Offset(ax, ay + depth), Offset(bx, by + depth), scale * .012f)
        }
    }
    fun bevel(scope: DrawScope, center: Offset, scale: Float, squash: Float) {
        for (i in 0..5) scope.drawLine(
            if (TileGeometry.light(i) > .42f) Color(0x70FFF0BE) else Color(0x60332317),
            Offset(center.x + TileGeometry.cornerX(i) * scale, center.y + TileGeometry.cornerY(i) * scale * squash),
            Offset(center.x + TileGeometry.cornerX((i + 1) % 6) * scale, center.y + TileGeometry.cornerY((i + 1) % 6) * scale * squash), scale * .018f)
    }
}
