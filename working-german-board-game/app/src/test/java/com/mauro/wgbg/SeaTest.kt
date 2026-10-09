package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.GameConfig
import wgbg.engine.Rules
import kotlin.math.abs
import kotlin.math.hypot

class SeaTest {
    private val board = Rules.newGame(GameConfig(), 1).board
    private val dt = 1 / 60f

    @Test
    fun `clouds stay over the water, never the island or the frame`() {
        val sea = Sea(board)
        repeat(20_000) { sea.update(dt) } // over five minutes of drifting
        for (c in sea.clouds) assertTrue(hypot(c.x, c.y) in sea.landR..Sea.SEA_R, "cloud at ${c.x},${c.y}")
    }

    @Test
    fun `a cloud takes several pokes before it poofs, then a new one drifts in`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        repeat(Sea.POKES_TO_POOF - 1) { n ->
            val hit = sea.cloudAt(Offset(c.x, c.y))
            assertNotNull(hit, "still pokeable after $n pokes")
            sea.poof(hit!!)
            assertTrue(c.poof < 0, "poke ${n + 1} only wobbles it")
            assertTrue(c.wobble > 0)
            repeat(30) { sea.update(dt) }
        }
        sea.poof(sea.cloudAt(Offset(c.x, c.y))!!)
        assertTrue(c.poof >= 0, "poke ${Sea.POKES_TO_POOF} poofs it")
        assertEquals(null, sea.cloudAt(Offset(c.x, c.y)).takeIf { it === c }, "a poofed cloud cannot be poked again")
        repeat(300) { sea.update(dt) } // 5 s
        assertFalse(c in sea.clouds, "the poofed cloud is gone")
        assertEquals(4, sea.clouds.size, "and replaced")
    }

    @Test
    fun `dragged clouds are kept on the sea`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        sea.move(c, Offset(0f, 0f))          // onto the island
        assertTrue(hypot(c.x, c.y) >= sea.landR)
        sea.move(c, Offset(40f, -40f))       // far past the frame
        assertTrue(hypot(c.x, c.y) <= Sea.SEA_R)
    }

    /** Bearing of a cloud around the sea's center, so its passive orbit drift can be told apart from a fling. */
    private fun travel(sea: Sea, c: Sea.Cloud, frames: Int): Float {
        val x0 = c.x; val y0 = c.y
        repeat(frames) { sea.update(dt) }
        return hypot(c.x - x0, c.y - y0)
    }

    @Test
    fun `a flung cloud travels, slows smoothly and comes to rest in about two seconds`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        // Throw it along its orbit (tangentially) so it doesn't hit a bound.
        val d = hypot(c.x, c.y)
        sea.fling(c, -c.y / d * 3f, c.x / d * 3f)
        val first = travel(sea, c, 30)                // first half second
        assertTrue(first > 0.6f, "moved only $first in 0.5 s")
        repeat(90) { sea.update(dt) }                 // to 2 s
        assertEquals(0f, hypot(c.vx, c.vy), "still moving after 2 s")
        val drift = travel(sea, c, 60)                // only the slow passive drift remains
        assertTrue(drift < 0.2f, "still travelling $drift per second")
    }

    @Test
    fun `hard flicks are capped and a flung cloud never leaves the water`() {
        val sea = Sea(board)
        val rnd = java.util.Random(9)
        repeat(200) {
            val c = sea.clouds[rnd.nextInt(sea.clouds.size)]
            sea.fling(c, (rnd.nextFloat() - 0.5f) * 200f, (rnd.nextFloat() - 0.5f) * 200f)
            assertTrue(hypot(c.vx, c.vy) <= Sea.FLING_MAX + 1e-3f)
            repeat(20) {
                sea.update(dt)
                for (k in sea.clouds) assertTrue(hypot(k.x, k.y) in sea.landR + 0.24f..Sea.SEA_R - 0.24f, "cloud off the water at ${k.x},${k.y}")
            }
        }
    }

    @Test
    fun `hitting the rim bounces a cloud back softly instead of stopping it dead`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        val d = hypot(c.x, c.y)
        sea.fling(c, c.x / d * 5f, c.y / d * 5f)      // straight out at the rim
        var inward = false
        repeat(60) { sea.update(dt); if (c.vx * c.x + c.vy * c.y < 0) inward = true }
        assertTrue(inward, "it should rebound inward")
    }

    @Test
    fun `grabbing a moving cloud stops it, and a zero fling does nothing`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        sea.fling(c, 3f, 1f)
        sea.update(dt)
        sea.move(c, Offset(c.x, c.y))
        assertEquals(0f, c.vx); assertEquals(0f, c.vy)
        sea.fling(c, 0f, 0f)
        assertEquals(0f, hypot(c.vx, c.vy))
        sea.fling(c, Float.NaN, 1f)                   // garbage is ignored
        assertEquals(0f, hypot(c.vx, c.vy))
    }

    @Test
    fun `water taps are recognised and land or frame taps are not`() {
        val sea = Sea(board)
        assertTrue(sea.isWater(Offset(0f, -(sea.landR + Sea.SEA_R) / 2)))
        assertFalse(sea.isWater(Offset(0f, 0f)))
        assertFalse(sea.isWater(Offset(Sea.SEA_R + 0.5f, 0f)))
    }

    @Test
    fun `a long session with splashes and pokes stays bounded and finite`() {
        val sea = Sea(board)
        val rnd = java.util.Random(3)
        repeat(36_000) { i -> // ten minutes at 60 fps
            if (i % 30 == 0) sea.splash(Offset((rnd.nextFloat() - 0.5f) * 10, (rnd.nextFloat() - 0.5f) * 10))
            if (i % 50 == 0) sea.clouds.firstOrNull { it.poof < 0 }?.let(sea::poof)
            sea.update(dt)
        }
        assertEquals(4, sea.clouds.size)
        for (c in sea.clouds) assertTrue(c.x.isFinite() && c.y.isFinite())
    }

    @Test
    fun `wavelet placement is deterministic for a seed and differs between seeds`() {
        val key = { s: Sea -> s.wavelets.map { listOf(it.x, it.y, it.angle, it.len, it.period, it.phase) } }
        assertEquals(key(Sea(board, 5)), key(Sea(board, 5)))
        assertTrue(key(Sea(board, 5)) != key(Sea(board, 6)))
    }

    @Test
    fun `wavelets stay on the water inside the frame at every moment of their loops`() {
        val sea = Sea(board)
        for (w in sea.wavelets) {
            var t = 0f
            while (t < 60f) { // a minute covers many loops (and many hops) of every wavelet
                val x = sea.waveletX(w, t); val y = sea.waveletY(w, t)
                val reach = w.len * 0.75f
                assertTrue(sea.coastDist(x, y) > reach * 0.5f, "wavelet ${w.id} on the coast at t=$t")
                assertTrue(hypot(x, y) + reach < Sea.SEA_R, "wavelet ${w.id} over the rim at t=$t")
                t += 0.25f
            }
        }
    }

    @Test
    fun `wavelets are small, with a few double-size crests out in deeper water`() {
        val sea = Sea(board)
        val small = sea.wavelets.filter { !it.big }
        val big = sea.wavelets.filter { it.big }
        assertTrue(small.size >= Sea.SMALL_WAVELETS, "${small.size} small")
        assertEquals(Sea.BIG_WAVELETS, big.size)
        // About 3-12 px at the reference resolution (~75 px per hex side), only a few longer.
        assertTrue(small.all { it.len in 0.04f..0.22f })
        assertTrue(small.count { it.len > 0.16f } < small.size / 5)
        assertEquals(2f, big.map { it.len }.average().toFloat() / small.map { it.len }.average().toFloat(), 0.35f)
        assertTrue(big.all { sea.coastDist(it.x, it.y) > 1f }, "big crests stay offshore")
        // Busier near the shore than in open water.
        val near = small.count { sea.coastDist(it.x, it.y) < 0.6f }
        assertTrue(near > small.size / 4, "only $near near the coast")
    }

    @Test
    fun `the loop is seamless - a wavelet is invisible whenever it hops to a new spot`() {
        val sea = Sea(board)
        for (w in sea.wavelets) {
            var t = 0f
            var lastX = sea.waveletX(w, 0f); var lastY = sea.waveletY(w, 0f)
            while (t < 30f) {
                t += dt
                val x = sea.waveletX(w, t); val y = sea.waveletY(w, t)
                if (hypot(x - lastX, y - lastY) > 0.01f) // jumped to its next spot
                    assertTrue(sea.waveletAlpha(w, t) < 0.03f && sea.waveletAlpha(w, t - dt) < 0.03f, "wavelet ${w.id} hopped while visible")
                lastX = x; lastY = y
            }
        }
    }

    @Test
    fun `wavelets are out of step with each other`() {
        val sea = Sea(board)
        val visible = sea.wavelets.count { sea.waveletAlpha(it, 10f) / it.alpha > 0.5f }
        assertTrue(visible in sea.wavelets.size / 5..sea.wavelets.size * 4 / 5, "$visible of ${sea.wavelets.size} at once")
        assertTrue(sea.wavelets.map { it.period }.distinct().size > sea.wavelets.size / 2, "periods vary")
    }

    @Test
    fun `breaking spots sit just off real coastline edges and are not a continuous border`() {
        val sea = Sea(board)
        assertTrue(sea.breaks.size in 10..40, "${sea.breaks.size} breaks")
        for (b in sea.breaks) {
            val cd = sea.coastDist(b.x, b.y)
            assertTrue(abs(cd) < 0.15f, "break ${b.id} is $cd from the coast")
            assertTrue(b.x * b.nx + b.y * b.ny > 0f, "break ${b.id} faces inland")
        }
        // Intermittent: at any moment only some are showing.
        val showing = sea.breaks.count { ((10f / it.period + it.phase) % 1f) < 0.62f }
        assertTrue(showing < sea.breaks.size, "all breaking at once")
    }

    @Test
    fun `tonal patch textures tile seamlessly and stay translucent`() {
        val sea = Sea(board)
        for (px in sea.patchPixels) {
            val n = Sea.PATCH_TEX
            assertEquals(n * n, px.size)
            for (i in 0 until n) {
                // Opposite edges sit next to each other once tiled, so they must nearly match.
                val la = px[i * n] ushr 24; val ra = px[i * n + n - 1] ushr 24
                assertTrue(abs(la - ra) < 40, "row $i seam $la vs $ra")
                val ta = px[i] ushr 24; val ba = px[(n - 1) * n + i] ushr 24
                assertTrue(abs(ta - ba) < 40, "column $i seam $ta vs $ba")
            }
            assertTrue(px.all { (it ushr 24) <= 120 }, "patches never get opaque")
        }
    }

    @Test
    fun `hop noise is deterministic and spread across -1 to 1`() {
        assertEquals(Sea.hop(3, 7, 1), Sea.hop(3, 7, 1))
        val v = (0 until 2000).map { Sea.hop(it, it * 3, 0) }
        assertTrue(v.all { it in -1f..1f })
        assertTrue(v.average() in -0.1..0.1)
    }
}
