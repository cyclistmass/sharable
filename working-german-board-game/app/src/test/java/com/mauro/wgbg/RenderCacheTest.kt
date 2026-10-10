package com.mauro.wgbg

import java.lang.management.ManagementFactory
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RenderCacheTest {
    @Test fun `two sizes promote hits and evict only least recent`() {
        val cache = RecentRenderSizes<Any>()
        val a = Any(); val b = Any(); val c = Any()
        assertNull(cache.get(512))
        cache.put(512, a); cache.put(1024, b)
        repeat(100) { assertSame(a, cache.get(512)); assertSame(b, cache.get(1024)) }
        cache.get(512); cache.put(2048, c)
        assertNull(cache.get(1024)); assertSame(a, cache.get(512)); assertSame(c, cache.get(2048))
        cache.put(512, b)
        assertSame(b, cache.get(512)); assertSame(c, cache.get(2048))
    }

    @Test fun `ladder retains old pixel sizes during random zooms and step boundaries`() {
        val ladder = RenderSizeLadder()
        val random = Random(714)
        fun check(scale: Float) {
            val previous = ceil(1.15.pow(ceil(ln(scale.coerceAtLeast(1f).toDouble()) / ln(1.15)))).toInt()
            assertEquals(previous, ladder.pixels(scale))
            assertEquals(previous, ladder.pixels(scale))
        }
        repeat(10_000) { check(random.nextFloat() * 1500) }
        repeat(55) {
            val boundary = 1.15.pow(it).toFloat()
            check(Math.nextDown(boundary)); check(boundary); check(Math.nextUp(boundary))
        }
        check(0f); check(-1f)
    }

    @Test fun `decode covers requested rectangular geometry without excessive sampling`() {
        val random = Random(194)
        repeat(10_000) {
            val w = random.nextInt(1, 16_000); val h = random.nextInt(1, 16_000)
            val tw = random.nextInt(1, 2000); val th = random.nextInt(1, 2000)
            val sample = renderDecodeSample(w, h, tw, th)
            assertEquals(1, Integer.bitCount(sample))
            assertTrue(sample == 1 || (w / sample >= tw && h / sample >= th))
            assertTrue(w / sample / 2 < tw || h / sample / 2 < th)
        }
        assertEquals(4, renderDecodeSample(4096, 2048, 1024, 512))
        assertEquals(1, renderDecodeSample(4096, 2048, 3000, 1600))
        assertEquals(1, renderDecodeSample(256, 128, 512, 256))
        assertThrows(IllegalArgumentException::class.java) { renderDecodeSample(0, 128, 10, 10) }
    }

    @Test fun `warmed cache and scale hits allocate no objects`() {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        org.junit.jupiter.api.Assumptions.assumeTrue(bean != null && bean.isThreadAllocatedMemorySupported)
        requireNotNull(bean).isThreadAllocatedMemoryEnabled = true
        val cache = RecentRenderSizes<Any>()
        val ladder = RenderSizeLadder()
        val a = Any(); val b = Any()
        cache.put(512, a); cache.put(1024, b); ladder.pixels(180f)
        // Warm the JVM/JIT before measuring only the primitive-key/cache/quantization hit loop.
        repeat(500_000) { cache.get(if (it and 1 == 0) 512 else 1024); ladder.pixels(180f) }
        val thread = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(thread)
        val start = System.nanoTime()
        repeat(1_000_000) { cache.get(if (it and 1 == 0) 512 else 1024); ladder.pixels(180f) }
        val elapsed = System.nanoTime() - start
        val bytes = bean.getThreadAllocatedBytes(thread) - before
        println("RENDER_CACHE_BENCH calls=1000000 nsPerPair=${elapsed / 1_000_000.0} allocatedBytes=$bytes")
        // A real per-call allocation would be at least 16 MB over a million calls; a few KB is the JVM itself
        // (JIT or safepoint bookkeeping on a busy CI runner), which made an exact-zero check flaky.
        assertTrue(bytes < 64 * 1024, "Warmed hits must not allocate per call ($bytes bytes over 1M calls); this does not measure Android drawing")
        assertSame(a, cache.get(512)); assertSame(b, cache.get(1024))
    }
}
