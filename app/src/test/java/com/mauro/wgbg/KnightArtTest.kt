package com.mauro.wgbg

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnightArtTest {
    @Test
    fun `both themes have three levels of eight tilt steps with matching body and trim`() {
        for (pirate in listOf(false, true)) {
            val levels = KnightArt.levels(pirate)
            assertEquals(3, levels.size)
            for (p in levels) {
                assertEquals(8, p.body.size)
                assertEquals(8, p.trim.size)
                assertEquals(16, (p.body + p.trim).toSet().size, "no frame used twice")
            }
        }
    }

    @Test
    fun `an active knight's flag stands upright just right of it, the pennant pointing outward`() {
        val (foot, top, tip) = knightFlagPoints(100f)
        assertEquals(foot.x, top.x, 1e-4f)
        assertTrue(top.y < foot.y - 30f, "the pole rises")
        assertTrue(foot.x > 18f, "clear of the knight's base (it spans about +-22% of the frame)")
        assertTrue(tip.x > top.x && tip.y > top.y, "the pennant flies out and down from the top")
    }

    @Test
    fun `corners draw back to front, so the nearer piece is on top`() {
        val ys = floatArrayOf(5f, 1f, 3f, 1f, 9f)
        assertEquals(listOf(1, 3, 2, 0, 4), cornerDrawOrder(ys.size) { ys[it] })
        assertEquals(emptyList<Int>(), cornerDrawOrder(0) { 0f })
    }
}
