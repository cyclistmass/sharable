package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import wgbg.engine.Track

class BoardHudTest {
    @Test
    fun `improvement bars fill one segment per level and cap at four`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 4), (0..5).map(::barSegments))
    }

    @Test
    fun `bars are Guilds gold, Academy green, Crown blue`() {
        assertEquals(listOf(Track.TRADE, Track.SCIENCE, Track.POLITICS), IMPROVEMENT_BARS.map { it.first })
    }

    @Test
    fun `raiders countdown`() {
        assertEquals(wgbg.engine.CkPiece.BARBARIAN_TRACK, raidersStepsLeft(0))
        assertEquals(0, raidersStepsLeft(wgbg.engine.CkPiece.BARBARIAN_TRACK))
        assertEquals(0, raidersStepsLeft(99))
    }
}
