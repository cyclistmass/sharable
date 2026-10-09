package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeabirdTest {
    @Test
    fun `birds glide most of the time and flap in bursts, never in sync`() {
        val birds = List(6) { Seabird(it, 5f) }
        val dt = 1 / 60f
        val flapping = IntArray(6)
        var together = 0
        val steps = 60 * 120
        repeat(steps) {
            birds.forEach { it.update(dt) }
            val f = birds.map { it.flapping }
            f.forEachIndexed { i, b -> if (b) flapping[i]++ }
            if (f.all { it }) together++
        }
        for (n in flapping) {
            val share = n / steps.toFloat()
            assertTrue(share in 0.15f..0.35f, "flapping $share of the time")
        }
        assertTrue(together < steps / 50, "birds flap together too often")
    }

    @Test
    fun `wings stay within a natural stroke`() {
        val b = Seabird(3, 5f)
        repeat(60 * 30) { b.update(1 / 60f); assertTrue(kotlin.math.abs(b.hinge()) <= 0.56f) }
    }
}
