package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class SupplyTest {
    private val landR = 4.5f

    @Test
    fun `the viewer sits at the bottom and seats go round evenly for 2 to 6 players`() {
        for (n in 2..6) for (viewer in 0 until n) {
            assertEquals((PI / 2).toFloat(), Supply.seatAngle(viewer, viewer, n), 1e-5f)
            val angles = (0 until n).map { Supply.seatAngle(it, viewer, n) }
            for (k in 1 until n) {
                val step = angles[(viewer + k) % n] - angles[(viewer + k - 1) % n]
                assertEquals((2 * PI / n).toFloat(), step, 1e-4f, "n=$n viewer=$viewer")
            }
        }
    }

    @Test
    fun `a full supply is 5 homesteads, 4 towns and 15 roads in three rows of five`() {
        val spots = Supply.layout(5, 4, 15, (PI / 2).toFloat(), 0f, 6f)
        assertEquals(5, spots.count { it.kind == Supply.Kind.HOMESTEAD })
        assertEquals(4, spots.count { it.kind == Supply.Kind.TOWN })
        val roads = spots.filter { it.kind == Supply.Kind.ROAD }
        assertEquals(15, roads.size)
        assertEquals(Supply.ROAD_ROWS, roads.map { (it.y * 100).toInt() }.distinct().size, "three road rows")
        // Fewer roads fill the rows in order, and nothing is drawn for empty kinds.
        assertEquals(7, Supply.layout(0, 0, 7, 0f, 0f, 0f).size)
        assertTrue(Supply.layout(0, 0, 0, 0f, 0f, 0f).isEmpty())
        assertTrue(Supply.layout(-2, -1, -5, 0f, 0f, 0f).isEmpty())
    }

    @Test
    fun `rows face the owner - roads nearest the board, homesteads nearest the player`() {
        val spots = Supply.layout(5, 4, 15, (PI / 2).toFloat(), 0f, 6f)   // bottom seat
        val roadY = spots.filter { it.kind == Supply.Kind.ROAD }.maxOf { it.y }
        val townY = spots.filter { it.kind == Supply.Kind.TOWN }.map { it.y }.distinct().single()
        val homeY = spots.filter { it.kind == Supply.Kind.HOMESTEAD }.map { it.y }.distinct().single()
        assertTrue(roadY < townY && townY < homeY, "roads $roadY, towns $townY, homesteads $homeY")
        // The top seat is the same, turned round: its homesteads are the farthest up.
        val top = Supply.layout(5, 4, 15, (3 * PI / 2).toFloat(), 0f, -6f)
        assertTrue(top.filter { it.kind == Supply.Kind.HOMESTEAD }.all { it.y < top.filter { s -> s.kind == Supply.Kind.ROAD }.minOf { s -> s.y } })
    }

    @Test
    fun `supplies stay off the island and never overlap each other, 2 to 6 players`() {
        for (n in 2..6) {
            val all = (0 until n).flatMap { seat ->
                val (cx, cy) = Supply.home(seat, 0, n, landR)
                Supply.layout(5, 4, 15, Supply.seatAngle(seat, 0, n), cx, cy).map { seat to it }
            }
            for ((_, s) in all) assertTrue(hypot(s.x, s.y) > landR + 0.7f, "n=$n piece at ${s.x},${s.y} is on the island")
            for (i in all.indices) for (j in i + 1 until all.size) {
                if (all[i].first == all[j].first) continue
                val d = hypot(all[i].second.x - all[j].second.x, all[i].second.y - all[j].second.y)
                assertTrue(d > 0.4f, "n=$n seats ${all[i].first} and ${all[j].first} overlap ($d)")
            }
        }
    }

    @Test
    fun `a press hits the pile but never reaches in toward the board`() {
        val a = (PI / 2).toFloat()
        val (cx, cy) = Supply.home(0, 0, 4, landR)
        assertTrue(Supply.hit(cx, cy, cx, cy, a))
        assertTrue(Supply.hit(cx + 1.2f, cy + 0.5f, cx, cy, a))
        assertFalse(Supply.hit(cx, cy - 1.3f, cx, cy, a), "toward the coast")
        assertFalse(Supply.hit(cx + 2.6f, cy, cx, cy, a), "past the end of the rows")
        // Same shape turned for a side seat.
        val side = PI.toFloat()
        assertTrue(Supply.hit(-6f + 0.5f * cos(side), 1.2f, -6f, 0f, side))
        assertFalse(Supply.hit(-6f + 1.3f, 0f, -6f, 0f, side), "toward the coast from the left seat")
        assertTrue(abs(sin(side)) < 1e-6f)
    }

    @Test
    fun `the view can pan past the board to reach the supplies, but not forever`() {
        val screen = Size(2400f, 1080f)
        val c = Camera.FIT.transform(Offset(1200f, 540f), Offset(0f, -5000f), 1f, screen)
        assertEquals(-Camera.PAN_EXTRA * 1080f, c.pan.y, 1f)
        val z = Camera.FIT.transform(Offset(1200f, 540f), Offset(9000f, 0f), 2f, screen)
        assertEquals((0.5f + Camera.PAN_EXTRA * 2f) * 2400f, z.pan.x, 1f)
    }
}
