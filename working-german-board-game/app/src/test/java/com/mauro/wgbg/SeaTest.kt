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
        for (c in sea.clouds) assertTrue(hypot(c.x, c.y) >= sea.landR && sea.inOpenSea(c.x, c.y), "cloud at ${c.x},${c.y}")
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
    fun `clouds may rest over the island, taps there reach the board, and they stay on the table`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        sea.grab(c, Offset(c.x, c.y))
        sea.move(c, Offset(0f, 0f))          // onto the island: it goes there while held
        repeat(60) { sea.update(dt) }
        assertTrue(hypot(c.x, c.y) < 0.2f, "follows the finger over the island")
        sea.release(c)
        repeat(60 * 15) { sea.update(dt) }   // let go: clouds may drift over land now (Mauro, 2026-10-10)
        assertTrue(hypot(c.x, c.y) < sea.landR, "stays over the island")
        assertTrue(sea.cloudAt(Offset(c.x, c.y)) == null, "a touch on the island goes to the board, not the cloud")
        sea.grab(c, Offset(c.x, c.y))
        sea.move(c, Offset(40f, -40f))       // far off the screen
        repeat(60) { sea.update(dt) }
        sea.release(c)
        repeat(60) { sea.update(dt) }
        assertTrue(sea.inOpenSea(c.x, c.y))
    }

    /** Bearing of a cloud around the sea's center, so its passive orbit drift can be told apart from a fling. */
    private fun travel(sea: Sea, c: Sea.Cloud, frames: Int): Float {
        val x0 = c.x; val y0 = c.y
        repeat(frames) { sea.update(dt) }
        return hypot(c.x - x0, c.y - y0)
    }

    @Test
    fun `a flung cloud travels, slows smoothly and comes to rest in a few seconds`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        // Throw it along its orbit (tangentially) so it doesn't hit a bound.
        val d = hypot(c.x, c.y)
        sea.fling(c, -c.y / d * 3f, c.x / d * 3f)
        val first = travel(sea, c, 30)                // first half second
        assertTrue(first > 0.6f, "moved only $first in 0.5 s")
        repeat(330) { sea.update(dt) }                // to 6 s: light and floaty, but it stops
        assertEquals(0f, hypot(c.vx, c.vy), "still moving after 6 s")
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
            assertTrue(hypot(c.vx, c.vy) <= CloudFeel.MAX_SPEED + 1e-3f)
            repeat(20) {
                sea.update(dt)
                // Never past the screen edge (they may cross the island).
                for (k in sea.clouds) assertTrue(sea.inOpenSea(k.x, k.y, Sea.CLOUD_EDGE - 0.01f), "cloud off the screen at ${k.x},${k.y}")
            }
        }
    }

    @Test
    fun `hitting the edge bounces a cloud back softly instead of stopping it dead`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        c.x = Sea.OPEN_RX - 1.5f; c.y = 0f               // near the right edge of the screen
        sea.fling(c, 5f, 0f)                             // straight out at it
        var inward = false
        repeat(600) { sea.update(dt); if (c.vx * c.x + c.vy * c.y < 0) inward = true }
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
        assertTrue(sea.isWater(Offset(Sea.SEA_R + 2f, 0f)), "the sea now runs to the screen edge")
        assertFalse(sea.isWater(Offset(Sea.OPEN_RX + 0.5f, 0f)))
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
        // A poofed cloud fades while its replacement drifts in, so allow one or two on their way out.
        assertTrue(sea.clouds.count { it.poof < 0 } in sea.cloudTarget - 1..sea.cloudTarget, "live clouds ${sea.clouds.count { it.poof < 0 }}")
        assertTrue(sea.clouds.size <= sea.cloudTarget + 2)
        for (c in sea.clouds) assertTrue(c.x.isFinite() && c.y.isFinite())
    }

    @Test
    fun `the Clouds slider sets how many clouds are on the sea, fading extras out`() {
        val sea = Sea(board)
        for ((amount, want) in listOf(0f to 0, 1f to 16, 0.25f to 1)) {
            sea.clouds01 = amount
            repeat(60 * 8) { sea.update(dt) }               // 8 s to settle
            assertEquals(want, sea.clouds.count { it.poof < 0 }, "at $amount")
        }
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
                assertTrue(sea.inOpenSea(x, y, reach), "wavelet ${w.id} off the screen at t=$t")
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
        // Busier near the shore than in open water: the 0.6-wide coastal band is under a tenth of
        // the screen-wide sea, so holding well over a seventh of the wavelets means denser there.
        val near = small.count { sea.coastDist(it.x, it.y) < 0.6f }
        assertTrue(near > small.size / 7, "only $near near the coast")
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

    @Test
    fun `a tap pushes the cloud away from the finger, and the fifth tap poofs it`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        val from = Offset(c.x - 0.1f, c.y)               // finger just left of center
        sea.poof(c, from)
        assertTrue(c.vx > 0f, "pushed to the right, away from the finger")
        repeat(3) { sea.poof(c, from) }
        assertTrue(c.poof < 0, "four taps only push it")
        sea.poof(c, from)
        assertTrue(c.poof >= 0, "the fifth tap poofs it")
    }

    @Test
    fun `a dragged cloud follows the finger smoothly and keeps where it was grabbed`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        val grabAt = Offset(c.x + 0.1f, c.y)             // grabbed a little right of its center
        sea.grab(c, grabAt)
        val to = Offset(grabAt.x, grabAt.y + 0.5f)
        sea.move(c, to)
        sea.update(dt)
        assertTrue(abs(c.y - (to.y)) > 0.05f, "it eases toward the finger rather than jumping")
        repeat(60) { sea.update(dt) }
        assertEquals(to.x - 0.1f, c.x, 0.02f)
        assertEquals(to.y, c.y, 0.02f)
        sea.release(c)
    }

    @Test
    fun `cloud momentum is the same at 30, 60 and 120 frames a second`() {
        val ends = listOf(30, 60, 120).map { fps ->
            val sea = Sea(board)
            val c = sea.clouds.first()
            c.x = 0f; c.y = -(sea.landR + 0.6f)
            val d = hypot(c.x, c.y)
            // Remove the passive drift from the comparison by measuring speed, not position.
            sea.fling(c, -c.y / d * 1.5f, c.x / d * 1.5f)
            repeat(fps) { sea.update(1f / fps) }
            hypot(c.vx, c.vy)
        }
        for (v in ends) assertEquals(1.5f * kotlin.math.exp(-CloudFeel.DAMPING), v, 0.02f)
    }

    @Test
    fun `a dragged cloud moves the way the finger moves, even inward or past the edge`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        sea.grab(c, Offset(c.x, c.y))
        val from = Offset(c.x, c.y)
        val to = Offset(from.x * 0.3f, from.y * 0.3f)     // straight toward the island's center
        sea.move(c, to)
        repeat(60) { sea.update(dt) }
        val moved = Offset(c.x - from.x, c.y - from.y)
        val wanted = Offset(to.x - from.x, to.y - from.y)
        val cos = (moved.x * wanted.x + moved.y * wanted.y) / (moved.getDistance() * wanted.getDistance())
        assertTrue(cos > 0.99f, "moved off the drag direction (cos $cos)")
    }

    @Test
    fun `a thrown cloud flies straight, not curving around the island`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        c.x = 0f; c.y = -(sea.landR + 0.6f)            // just above the island, on the water
        sea.fling(c, 1.2f, 0f)                          // throw it sideways
        repeat(30) { sea.update(dt) }
        assertEquals(-(sea.landR + 0.6f), c.y, 0.02f, "no curve while it flies")
    }

    @Test
    fun `boats cruise the open sea, never onto the island or off the screen`() {
        val sea = Sea(board)
        sea.boats01 = 1f
        assertEquals(Sea.MAX_BOATS, sea.boatCount)
        repeat(60 * 120) { // two minutes
            sea.update(dt)
            for (b in sea.boats) {
                assertTrue(hypot(b.x, b.y) > sea.landR - 0.2f, "boat on the island at ${b.x},${b.y}")
                assertTrue(sea.inOpenSea(b.x, b.y, -0.3f), "boat off the screen at ${b.x},${b.y}")
            }
        }
        sea.boats01 = 0f
        assertEquals(0, sea.boatCount)
    }

    @Test
    fun `boats never flip-flop, at most one turn every few seconds`() {
        val sea = Sea(board)
        sea.boats01 = 1f
        val last = sea.boats.map { kotlin.math.cos(it.heading) >= 0f }.toMutableList()
        val turns = IntArray(sea.boats.size)
        repeat(60 * 120) {
            sea.update(dt)
            sea.boats.forEachIndexed { i, b -> val r = kotlin.math.cos(b.heading) >= 0f; if (r != last[i]) { turns[i]++; last[i] = r } }
        }
        for (n in turns) assertTrue(n < 25, "a boat turned $n times in 2 minutes")
    }
}
