package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import wgbg.engine.*

class SceneryDensityTest {
    @Test fun `all sliders increase with a wide spread and apply without advancing the clock`() {
        val sea = Sea(Rules.newGame(GameConfig(), 1).board)
        val counts = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { v ->
            sea.clouds01 = v; sea.birds01 = v; sea.boats01 = v; sea.waves01 = v
            assertEquals(sea.cloudTarget, sea.clouds.count { it.poof < 0 })
            listOf(sea.cloudTarget, sea.birdCount, sea.boatCount, sea.waveCount)
        }
        for (kind in 0..3) {
            assertEquals(0, counts[0][kind])
            for (step in 1..4) assertTrue(counts[step][kind] > counts[step - 1][kind])
            assertTrue(counts[4][kind] >= counts[2][kind] * 3)
            assertTrue(counts[1][kind] <= counts[2][kind] / 2)
        }
        assertEquals(listOf(4, 4, 4), counts[2].take(3))
        sea.clouds01 = 0f
        assertTrue(sea.clouds.none { it.poof < 0 })
        assertEquals(0f, sea.time)
    }
    @Test fun `whitecaps grow and brighten with the Waves slider`() {
        val sea = Sea(Rules.newGame(GameConfig(), 1).board)
        sea.waves01 = 0.25f
        val size = sea.whitecapScale; val alpha = sea.whitecapAlpha
        sea.waves01 = 1f
        assertTrue(sea.whitecapScale > size * 1.5f)
        assertTrue(sea.whitecapAlpha > alpha * 1.5f)
        assertEquals(0, Sea.sceneryCount(-1f, 16))
        assertEquals(16, Sea.sceneryCount(2f, 16))
    }

    @Test fun `Density scales every water object together - none, as set, double, capped`() {
        val sea = Sea(Rules.newGame(GameConfig(), 1).board)
        sea.clouds01 = 0.5f; sea.birds01 = 0.5f; sea.boats01 = 0.5f; sea.waves01 = 0.5f
        val base = listOf(sea.cloudTarget, sea.birdCount, sea.boatCount, sea.waveCount)
        sea.density01 = 0f
        org.junit.jupiter.api.Assertions.assertEquals(listOf(0, 0, 0, 0), listOf(sea.cloudTarget, sea.birdCount, sea.boatCount, sea.waveCount))
        org.junit.jupiter.api.Assertions.assertEquals(0, sea.clouds.count { it.poof < 0 }, "clouds leave at once")
        sea.density01 = 0.5f
        org.junit.jupiter.api.Assertions.assertEquals(base, listOf(sea.cloudTarget, sea.birdCount, sea.boatCount, sea.waveCount))
        sea.density01 = 1f
        val dbl = listOf(sea.cloudTarget, sea.birdCount, sea.boatCount, sea.waveCount)
        for (k in base.indices) org.junit.jupiter.api.Assertions.assertTrue(dbl[k] >= base[k] && dbl[k] <= 2 * base[k] + 1, "kind $k: ${base[k]} -> ${dbl[k]}")
        sea.clouds01 = 1f; sea.birds01 = 1f; sea.waves01 = 1f
        org.junit.jupiter.api.Assertions.assertTrue(sea.cloudTarget <= 16 && sea.birdCount <= 16, "capped at the pool size")
        sea.density01 = -3f
        org.junit.jupiter.api.Assertions.assertEquals(0, sea.birdCount)
    }

    @Test fun `off-screen birds keep flying while not drawn`() {
        val sea = Sea(Rules.newGame(GameConfig(), 1).board)
        sea.birds01 = 1f
        val before = sea.birds.map { it.x to it.y }
        repeat(60) { sea.update(1 / 60f) }
        val moved = sea.birds.zip(before).count { (b, p) -> b.x != p.first || b.y != p.second }
        org.junit.jupiter.api.Assertions.assertEquals(sea.birds.size, moved, "every bird's position is tracked, shown or not")
    }
}
