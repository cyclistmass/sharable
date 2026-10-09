package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.mauro.wgbg.CloudFeel.Gesture
import kotlin.math.exp
import kotlin.math.hypot

class CloudFeelTest {
    private val slop = 18f

    @Test
    fun `gestures sort into tap, nudge, drag and flick`() {
        assertEquals(Gesture.TAP, CloudFeel.classify(5f, 120, 0f, slop))
        assertEquals(Gesture.DRAG, CloudFeel.classify(5f, 900, 0f, slop), "a long still press just lets go")
        assertEquals(Gesture.NUDGE, CloudFeel.classify(40f, 150, 400f, slop))
        assertEquals(Gesture.DRAG, CloudFeel.classify(40f, 600, 300f, slop), "held past the tap time")
        assertEquals(Gesture.DRAG, CloudFeel.classify(200f, 200, 500f, slop), "a long path is a drag")
        assertEquals(Gesture.FLICK, CloudFeel.classify(120f, 140, 2500f, slop))
        assertEquals(Gesture.FLICK, CloudFeel.classify(300f, 800, 1500f, slop), "a drag released fast is thrown")
    }

    @Test
    fun `a tap pushes the cloud away from the finger`() {
        val (vx, vy) = CloudFeel.impulseAway(cx = 2f, cy = 0f, tx = 1.8f, ty = 0f, fallbackAngle = 0f)
        assertTrue(vx > 0f && kotlin.math.abs(vy) < 1e-6f)
        assertEquals(CloudFeel.TAP_IMPULSE, hypot(vx, vy), 1e-5f)
        val (fx, fy) = CloudFeel.impulseAway(1f, 1f, 1f, 1f, fallbackAngle = 1.2f)
        assertEquals(CloudFeel.TAP_IMPULSE, hypot(fx, fy), 1e-5f, "dead-center taps still push")
    }

    @Test
    fun `damping and following do not depend on the frame rate`() {
        for (fps in listOf(30, 60, 120)) {
            val dt = 1f / fps
            var v = 3f
            var x = 0f
            repeat(fps * 2) { v *= CloudFeel.damp(dt); x += (1f - x) * CloudFeel.followFraction(dt) }
            assertEquals(3f * exp(-0.9f * 2f), v, 1e-3f, "speed after 2 s at $fps fps")
            assertTrue(x in 0.999f..1f, "the drag reaches the finger without overshoot at $fps fps")
        }
    }

    @Test
    fun `speeds are capped, and garbage is stopped`() {
        val (vx, vy) = CloudFeel.cap(30f, 40f)
        assertEquals(CloudFeel.MAX_SPEED, hypot(vx, vy), 1e-4f)
        assertEquals(0f to 0f, CloudFeel.cap(Float.NaN, 1f))
        assertEquals(1f to 1f, CloudFeel.cap(1f, 1f))
    }
}
