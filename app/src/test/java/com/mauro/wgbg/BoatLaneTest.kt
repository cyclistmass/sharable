package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import wgbg.engine.GameConfig
import wgbg.engine.Rules
import kotlin.math.*

class BoatLaneTest {
    @Test fun `lanes keep their side and headings turn at a bounded rate`() {
        for (seed in 1..6) {
            val sea = Sea(Rules.newGame(GameConfig(), 1).board, seed)
            val lastTurn = FloatArray(sea.boats.size) { -1000f }
            val lastHeadingReversal = FloatArray(sea.boats.size) { -1000f }
            var reversals = 0
            repeat(120 * 60) { frame ->
                val before = sea.boats.map { Triple(it.heading, it.laneDir, it.passSide) }
                sea.update(1f / 60)
                sea.boats.forEachIndexed { i, b ->
                    assertTrue(abs(b.heading - before[i].first) <= Sea.BOAT_TURN_RATE / 60 + 0.00001f)
                    if ((cos(b.heading) >= 0f) != (cos(before[i].first) >= 0f)) {
                        assertTrue(frame / 60f - lastHeadingReversal[i] >= 20f)
                        lastHeadingReversal[i] = frame / 60f
                    }
                    if (b.laneDir != before[i].second) {
                        assertTrue(frame / 60f - lastTurn[i] >= 20f)
                        assertTrue(abs(b.x) > sea.landR + 3f)
                        lastTurn[i] = frame / 60f
                        reversals++
                    }
                    if (abs(b.x) < sea.landR + 3f) assertEquals(before[i].third, b.passSide)
                }
            }
            assertTrue(reversals > 0, "must exercise a reversal")
        }
    }
}
