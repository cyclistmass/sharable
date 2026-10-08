package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.GameConfig
import wgbg.engine.Rules
import kotlin.math.hypot

class SeaTest {
    private val board = Rules.newGame(GameConfig(), 1).board

    @Test
    fun `clouds stay over the water, never the island or the frame`() {
        val sea = Sea(board)
        repeat(20_000) { sea.update(1 / 60f) } // over five minutes of drifting
        for (c in sea.clouds) assertTrue(hypot(c.x, c.y) in sea.landR..Sea.SEA_R, "cloud at ${c.x},${c.y}")
    }

    @Test
    fun `poking a cloud poofs it and a new one drifts in`() {
        val sea = Sea(board)
        val c = sea.clouds.first()
        val hit = sea.cloudAt(Offset(c.x, c.y))
        assertNotNull(hit)
        sea.poof(hit!!)
        assertEquals(null, sea.cloudAt(Offset(c.x, c.y)).takeIf { it === hit }, "a poofed cloud can't be poked again")
        repeat(300) { sea.update(1 / 60f) } // 5 s
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
            if (i % 200 == 0) sea.clouds.firstOrNull { it.poof < 0 }?.let(sea::poof)
            sea.update(1 / 60f)
        }
        assertEquals(4, sea.clouds.size)
        for (c in sea.clouds) assertTrue(c.x.isFinite() && c.y.isFinite())
    }
}
