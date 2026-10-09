package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildMarksTest {
    @Test
    fun `build marks are the player's colour, a little lighter`() {
        for (p in PLAYER_COLORS.indices) {
            val base = PLAYER_COLORS[p]; val glow = targetGlow(p)
            assertTrue(glow.red >= base.red && glow.green >= base.green && glow.blue >= base.blue, "player $p")
        }
        assertTrue(targetGlow(1).blue > targetGlow(1).red)   // Blue stays blue
    }

    @Test
    fun `the pulse is a slow, smooth breath, not a blink`() {
        var t = 0f; var lo = 1f; var hi = 0f; var last = targetPulse(0f); var maxStep = 0f
        while (t < 3.6f) {
            val v = targetPulse(t)
            lo = minOf(lo, v); hi = maxOf(hi, v); maxStep = maxOf(maxStep, kotlin.math.abs(v - last)); last = v
            t += 1f / 60f
        }
        assertEquals(0f, lo, 0.01f); assertEquals(1f, hi, 0.01f)
        assertTrue(maxStep < 0.06f, "no jumps between frames")          // smooth at 60 fps
        assertEquals(targetPulse(0.3f), targetPulse(2.1f), 1e-4f)          // one breath every 1.8 s
    }
}
