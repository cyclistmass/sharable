package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.lang.management.ManagementFactory
import kotlin.math.*

class TileGeometryTest {
    @Test fun `projection agrees with layout perspective at flat medium and strong tilt`() {
        for (degrees in listOf(0f, 35f, 60f)) for (y in listOf(-4f, 0f, 4f)) {
            val tilt = degrees * PI.toFloat() / 180f
            val q = cos(tilt)
            val k = 1 + .22f * sin(tilt) * y * q / 4.2f
            assertEquals(300f + 2f * 70f * k, TileGeometry.projectX(2f, y, 300f, 70f, tilt), .0001f)
            assertEquals(200f + y * q * 70f * k, TileGeometry.projectY(y, 200f, 70f, tilt), .0001f)
        }
    }
    @Test fun `corners form a closed regular inset hex at every tilt`() {
        for (angle in listOf(0f, 35f, 60f)) {
            val q = cos(angle * PI.toFloat() / 180f)
            for (i in 0..5) {
                val x = TileGeometry.cornerX(i)
                val y = TileGeometry.cornerY(i) * q
                assertEquals(.93f, hypot(x, y / q), .00001f)
                assertEquals(x, TileGeometry.cornerX(i + 6), .00001f)
                assertEquals(y, TileGeometry.cornerY(i + 6) * q, .00001f)
            }
        }
    }
    @Test fun `extrusion grows with tilt and scales linearly`() {
        var last = 0f
        for (angle in listOf(0f, 35f, 60f)) {
            val q = cos(angle * PI.toFloat() / 180f)
            val depth = TileGeometry.depth(100f, q)
            assertTrue(depth > last)
            assertEquals(depth * 2, TileGeometry.depth(200f, q), .00001f)
            last = depth
        }
    }
    @Test fun `upper left walls are brighter than lower right`() {
        assertTrue(TileGeometry.light(4) > TileGeometry.light(1))
        assertTrue(TileGeometry.light(5) > TileGeometry.light(2))
        for (i in 0..5) assertTrue(TileGeometry.light(i) in .1f.. .8f)
    }
    @Test fun `motion is deterministic periodic with continuous loop velocity`() {
        for (tile in 0..18) for (mark in 0..5) for (t in listOf(0f, .25f, 2f, 7.75f)) {
            assertEquals(TileGeometry.wave(t, tile, mark), TileGeometry.wave(t, tile, mark))
            assertEquals(TileGeometry.wave(t, tile, mark), TileGeometry.wave(t + 8f, tile, mark), .000001f)
        }
        val e = .001f
        val before = (TileGeometry.wave(8f, 2, 1) - TileGeometry.wave(8f-e, 2, 1))/e
        val after = (TileGeometry.wave(e, 2, 1) - TileGeometry.wave(0f, 2, 1))/e
        assertEquals(before, after, .002f)
    }
    @Test fun `off and density limits bound work`() {
        assertEquals(0, TileGeometry.marks(-1))
        assertEquals(0, TileGeometry.marks(0))
        assertEquals(6, TileGeometry.marks(100))
    }
    private var sink = 0f
    @Test fun `scalar frame kernel allocates zero bytes after warmup`() {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        assumeTrue(bean != null && bean.isThreadAllocatedMemorySupported)
        bean!! .isThreadAllocatedMemoryEnabled = true
        fun run() { var sum = 0f; repeat(100_000) { sum += TileGeometry.wave(it / 60f, it % 19, it % 3) }; sink = sum }
        repeat(5) { run() }
        val id = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(id)
        run()
        val bytes = bean.getThreadAllocatedBytes(id) - before
        assertEquals(0L, bytes, "Scalar kernel only; Compose/GPU allocation needs device profiling")
    }
}
