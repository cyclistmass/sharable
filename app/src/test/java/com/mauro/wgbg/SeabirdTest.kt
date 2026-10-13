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

    @Test
    fun `paths are slow curves - heading never turns faster than the limit`() {
        val dt = 1 / 60f
        for (seed in 0 until 8) {
            val b = Seabird(seed, 4f)
            var last = b.heading
            repeat(60 * 90) {
                b.update(dt)
                var d = b.heading - last
                while (d > Math.PI) d -= (2 * Math.PI).toFloat()
                while (d < -Math.PI) d += (2 * Math.PI).toFloat()
                assertTrue(kotlin.math.abs(d) <= Seabird.MAX_TURN * dt + 1e-4f, "turned $d in one frame")
                assertTrue(kotlin.math.abs(b.bank) <= 0.6f + 1e-4f)
                last = b.heading
            }
        }
    }

    @Test
    fun `cruising birds stay near their home ring`() {
        for (seed in 0 until 8) {
            val b = Seabird(seed, 4f)
            var settle = 0
            repeat(60 * 120) {
                b.update(1 / 60f)
                val r = kotlin.math.hypot(b.x, b.y)
                // Outside a crossing of the island or a half-circle (and a few seconds after one, turning back), it keeps to its ring.
                if (!b.crossing && !b.arcing && settle-- <= 0) assertTrue(r in 2.6f..5.6f, "bird $seed wandered to r=$r")
                if (b.crossing) settle = 60 * 12
                if (b.arcing) settle = maxOf(settle, 60 * 15)
            }
        }
    }

    @Test
    fun `some birds go and circle a nearby boat, then come back`() {
        // A boat parked on each bird's ring: every bird should circle it at some point and loop close around it.
        var circled = 0
        for (seed in 0 until 8) {
            val b = Seabird(seed, 4f)
            val boat = floatArrayOf(4f, 0f, -4f, 0f)
            var near = 0; var wasCircling = false; var leftAgain = false
            repeat(60 * 180) {
                b.update(1 / 60f, boat)
                if (b.circling >= 0) {
                    wasCircling = true
                    val bx = boat[2 * b.circling]; val by = boat[2 * b.circling + 1]
                    if (kotlin.math.hypot(b.x - bx, b.y - by) < Seabird.CIRCLE_R * 2f) near++
                } else if (wasCircling) leftAgain = true
            }
            if (wasCircling && near > 60 * 5 && leftAgain) circled++
        }
        assertTrue(circled >= 6, "only $circled of 8 birds circled a boat and returned")
    }

    @Test
    fun `no boats or boats removed mid-circle never crash`() {
        val b = Seabird(1, 4f)
        val boats = floatArrayOf(4f, 0f, -4f, 0f)
        repeat(60 * 60) { b.update(1 / 60f, boats) }
        repeat(60 * 60) { b.update(1 / 60f, boats, 0); assertTrue(b.circling == -1) }
        repeat(60) { b.update(0f) }
        assertTrue(b.x.isFinite() && b.y.isFinite() && b.heading.isFinite())
    }

    @Test
    fun `flap frames cover the whole strip at any frame count`() {
        for (frames in listOf(8, 16)) {
            val b = Seabird(2, 4f)
            val seen = HashSet<Int>()
            repeat(60 * 60) { b.update(1 / 120f); val f = b.flapFrame(frames); if (f >= 0) { assertTrue(f < frames); seen += f } }
            assertTrue(seen.size == frames, "saw ${seen.size} of $frames frames")
        }
    }

    @Test
    fun `birds now and then fly across the island and come back out`() {
        var crossed = 0
        for (seed in 0 until 8) {
            val b = Seabird(seed, 4f)
            var minR = 9f; var backOut = false; var wasCrossing = false
            repeat(60 * 240) {
                b.update(1 / 60f)
                val r = kotlin.math.hypot(b.x, b.y)
                if (b.crossing) { wasCrossing = true; minR = minOf(minR, r) }
                else if (wasCrossing && r > 3f) backOut = true
            }
            if (minR < 1.5f && backOut) crossed++
        }
        assertTrue(crossed >= 6, "only $crossed of 8 birds crossed the island and came back out")
    }

    @Test
    fun `negative, zero and NaN frame steps never crash (Pixel 9 and 10 crash reports)`() {
        val b = Seabird(4, 4f)
        val boats = floatArrayOf(4f, 0f)
        repeat(100) { b.update(1 / 60f, boats) }
        for (dt in listOf(-5.7239004E-6f, -1.6353329E-4f, 0f, Float.NaN, -1f)) b.update(dt, boats)
        assertTrue(b.x.isFinite() && b.y.isFinite() && b.heading.isFinite())
    }

    @Test
    fun `gulls rise while they flap and sink while they glide, through most of their height range`() {
        for (seed in 0 until 8) {
            val b = Seabird(seed, 4f)
            var lo = 1f; var hi = 0f; var risesWhileFlapping = 0; var flapSteps = 0; var sinksWhileGliding = 0; var glideSteps = 0
            repeat(60 * 180) {
                val was = b.altitude
                b.update(1 / 60f)
                assertTrue(b.altitude in 0f..1f)
                if (b.flapping) { flapSteps++; if (b.altitude >= was) risesWhileFlapping++ } else { glideSteps++; if (b.altitude <= was) sinksWhileGliding++ }
                if (it > 60 * 20) { lo = minOf(lo, b.altitude); hi = maxOf(hi, b.altitude) }
            }
            assertTrue(hi - lo > 0.35f, "bird $seed only moved between ${"%.2f".format(lo)} and ${"%.2f".format(hi)}")
            assertTrue(risesWhileFlapping > flapSteps * 0.95 && sinksWhileGliding > glideSteps * 0.95, "bird $seed: climb follows the wings")
        }
    }

    @Test
    fun `gulls break off into half-circles to both sides, within the turn limit`() {
        var left = 0; var right = 0
        for (seed in 0 until 8) {
            val b = Seabird(seed, 4f)
            var was = false; var turned = 0f; var h = b.heading
            repeat(60 * 240) {
                b.update(1 / 60f)
                var d = b.heading - h; h = b.heading
                if (d > Math.PI) d -= (2 * Math.PI).toFloat(); if (d < -Math.PI) d += (2 * Math.PI).toFloat()
                assertTrue(kotlin.math.abs(d) <= Seabird.MAX_TURN / 60f + 1e-4f)
                if (b.arcing) turned += d
                if (was && !b.arcing) {
                    assertTrue(kotlin.math.abs(kotlin.math.abs(turned) - Math.PI.toFloat()) < 0.1f, "bird $seed: a half-circle turned $turned")
                    if (turned > 0) left++ else right++
                    turned = 0f
                }
                was = b.arcing
            }
        }
        assertTrue(left >= 8 && right >= 8, "half-circles left $left, right $right")
    }
}
