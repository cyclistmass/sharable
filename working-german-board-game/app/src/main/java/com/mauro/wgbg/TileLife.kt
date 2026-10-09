package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import wgbg.engine.Resource

/** No paths, collections, random generators, brushes or boxed offsets in the frame loop. */
object TileLife {
    private val grain = Color(0xC0F7D977)
    private val leaves = Color(0x804D943D)
    private val wool = Color(0xC0FFF2DD)
    private val stone = Color(0x70E3EFF4)
    private val dust = Color(0x408A654F)
    fun draw(scope: DrawScope, resource: Int, tile: Int, center: Offset, scale: Float, squash: Float, time: Float) {
        if (resource == Resource.NONE) return
        for (j in 0 until TileGeometry.marks(TileGeometry.LIFE_DENSITY)) {
            val wave = TileGeometry.wave(time, tile, j)
            // Lower-right clear of the number token; all marks stay inside the inset hex.
            val x = center.x + (-.08f + j * .16f) * scale
            val y = center.y + (.28f + (j % 2) * .16f) * scale * squash
            when (resource) {
                Resource.WHEAT -> scope.drawLine(grain, Offset(x, y), Offset(x + wave * .022f * scale, y - .09f * scale * squash), .013f * scale)
                Resource.WOOD -> scope.drawCircle(leaves, .023f * scale, Offset(x + wave * .013f * scale, y))
                Resource.SHEEP -> {
                    val shift = wave * .012f * scale
                    scope.drawOval(wool, Offset(x + shift - .023f * scale, y - .017f * scale * squash), Size(.046f * scale, .034f * scale * squash))
                    scope.drawCircle(Color(0xB04C463A), .009f * scale, Offset(x + shift + .021f * scale, y))
                }
                Resource.ORE -> scope.drawLine(stone, Offset(x, y), Offset(x + .045f * scale, y - .018f * scale * squash), (.003f + .003f * (wave + 1)) * scale)
                Resource.BRICK -> scope.drawCircle(dust, (.012f + .008f * (wave + 1)) * scale, Offset(x + wave * .02f * scale, y - (wave + 1) * .025f * scale * squash))
            }
        }
    }
}

/** Positions are filled only when the still layer records, then reused for every frame. */
class TileLifeBoard(count: Int) {
    private val centers = LongArray(count)
    private val scales = FloatArray(count)
    private val squashes = FloatArray(count)
    private val resources = IntArray(count)
    fun set(tile: Int, resource: Int, center: Offset, scale: Float, squash: Float) {
        centers[tile] = center.packedValue
        scales[tile] = scale
        squashes[tile] = squash
        resources[tile] = resource
    }
    fun draw(scope: DrawScope, time: Float) {
        if (TileGeometry.LIFE_DENSITY == 0) return
        for (h in scales.indices) if (scales[h] > 0f)
            TileLife.draw(scope, resources[h], h, Offset(centers[h]), scales[h], squashes[h], time)
    }
    fun clear() { scales.fill(0f) }
}
