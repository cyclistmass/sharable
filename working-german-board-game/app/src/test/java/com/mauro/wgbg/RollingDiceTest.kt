package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RollingDiceTest {
    @Test
    fun `a die comes to rest on the face that was rolled`() {
        for (face in 1..6) assertEquals(DiceSprites.TUMBLE + face - 1, DiceSprites.frame(1f, face, spin = 5))
    }

    @Test
    fun `while falling it tumbles through the spin frames only`() {
        var t = 0f
        while (t < 0.86f) {
            assertTrue(DiceSprites.frame(t, 4, spin = 7) in 0 until DiceSprites.TUMBLE)
            t += 0.01f
        }
    }

    @Test
    fun `the fall starts at the top, ends at rest and never overshoots the table`() {
        assertEquals(0f, DiceSprites.fall(0f), 1e-6f)
        assertEquals(1f, DiceSprites.fall(1f), 1e-4f)
        var t = 0f
        while (t <= 1f) { assertTrue(DiceSprites.fall(t) in 0f..1.0001f); t += 0.005f }
    }
}
