package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RollingDiceTest {
    @Test
    fun `from the first touch-down a die shows its rolled face, square`() {
        var t = DiceSprites.LAND
        while (t <= 1f) {
            assertEquals(-1, DiceSprites.frame(t, spin = 5))
            assertEquals(0f, DiceSprites.spin(t, turns = 1.4f))
            t += 0.01f
        }
    }

    @Test
    fun `in the air it tumbles through all four renders and its spin only slows`() {
        val seen = mutableSetOf<Int>()
        var t = 0f
        var last = Float.MAX_VALUE
        while (t < DiceSprites.LAND) {
            val f = DiceSprites.frame(t, spin = 7)
            assertTrue(f in 0 until DiceSprites.TUMBLE)
            seen += f
            val s = DiceSprites.spin(t, turns = 1.4f)
            assertTrue(s <= last + 1e-3f, "spin only slows down")
            last = s
            t += 0.005f
        }
        assertEquals(DiceSprites.TUMBLE, seen.size)
        assertTrue(DiceSprites.spin(DiceSprites.LAND - 0.005f, 1.4f) < 1f, "nearly square just before touch-down")
    }

    @Test
    fun `the die travels to its pad, arrives at touch-down and then only bounces in place`() {
        assertEquals(0f, DiceSprites.fall(0f), 1e-6f)
        var t = 0f
        var last = -1f
        while (t <= 1f) {
            val f = DiceSprites.fall(t)
            assertTrue(f in 0f..1f && f >= last, "never overshoots or backs away from the pad")
            if (t >= DiceSprites.LAND) assertEquals(1f, f)
            assertTrue(DiceSprites.bounce(t) in 0f..0.22f)
            last = f
            t += 0.005f
        }
        assertEquals(0f, DiceSprites.bounce(DiceSprites.LAND))
        assertEquals(0f, DiceSprites.bounce(1f), 1e-6f)
    }

    @Test
    fun `both dice have settled before the panel takes over`() {
        val second = ((DiceSprites.HANDOVER - 0.07f) / 0.93f)
        assertTrue(DiceSprites.bounce(second) < 0.002f)
    }

    @Test
    fun `every theme and colour has its own four tumbling renders`() {
        val sets = listOf(false, true).flatMap { pirate -> listOf(false, true).map { red -> diceTumble(red, pirate) } }
        for (s in sets) assertEquals(4, s.toSet().size)
        assertEquals(4, sets.toSet().size)
        assertNotEquals(diceTumble(red = true, pirate = false), diceTumble(red = true, pirate = true))
    }

    @Test
    fun `below Max speed the game waits for the dice to land before the next move`() {
        val throwMs = (DiceSprites.FALL_S * 1000 * DiceSprites.HANDOVER).toLong()
        for (speed in listOf(1, 2, 4, 8)) {
            assertTrue(pauseAfter(wgbg.engine.ActionType.ROLL, speed) > throwMs, "speed $speed")
            assertEquals(0L, pauseAfter(wgbg.engine.ActionType.END_TURN, speed))
        }
        assertTrue(pauseAfter(wgbg.engine.ActionType.BUILD_ROAD, 1) > pauseAfter(wgbg.engine.ActionType.BUILD_ROAD, 4))
    }
}
