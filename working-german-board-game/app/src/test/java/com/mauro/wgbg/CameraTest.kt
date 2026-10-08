package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Regression tests for the monkey-test crash: bad gesture numbers must never reach the camera. */
class CameraTest {
    private val screen = Size(2400f, 1080f)
    private fun Camera.valid() = zoom.isFinite() && pan.x.isFinite() && pan.y.isFinite() && tilt.isFinite()

    @Test
    fun `NaN or infinite pinch factors are ignored`() {
        val c = Camera.FIT.transform(Offset(100f, 100f), Offset.Zero, 1.5f, screen)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -2f)) {
            val after = c.transform(Offset(10f, 10f), Offset(5f, 5f), bad, screen)
            assertEquals(c, after, "factor $bad")
        }
    }

    @Test
    fun `NaN pans, focus points and tilts are ignored, zero-size screens too`() {
        val c = Camera.FIT
        assertEquals(c, c.transform(Offset(Float.NaN, 0f), Offset.Zero, 1.2f, screen))
        assertEquals(c, c.transform(Offset.Zero, Offset(0f, Float.POSITIVE_INFINITY), 1.2f, screen))
        assertEquals(c, c.transform(Offset.Zero, Offset.Zero, 1.2f, Size.Zero))
        assertEquals(c, c.tilted(Float.NaN, 1080f))
        assertEquals(c, c.tilted(30f, 0f))
    }

    @Test
    fun `random gesture storms keep the camera valid and in range`() {
        val rnd = java.util.Random(7)
        var c = Camera.FIT
        repeat(200_000) {
            c = when (rnd.nextInt(3)) {
                0 -> c.transform(Offset(rnd.nextFloat() * 2400, rnd.nextFloat() * 1080),
                    Offset(rnd.nextGaussian().toFloat() * 80, rnd.nextGaussian().toFloat() * 80),
                    listOf(rnd.nextFloat() * 3, Float.NaN, 1f / 0f, 0f, 1.01f)[rnd.nextInt(5)], screen)
                1 -> c.tilted(rnd.nextGaussian().toFloat() * 60, 1080f)
                else -> c.transform(Offset.Zero, Offset.Zero, 1f, screen)
            }
            assertTrue(c.valid(), "camera went invalid: $c")
            assertTrue(c.zoom in Camera.MIN_ZOOM..Camera.MAX_ZOOM && c.tilt in 0f..Camera.MAX_TILT)
        }
    }
}
