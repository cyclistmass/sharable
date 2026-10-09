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

    @Test
    fun `card count reads 8 in base and 8 slash progress in Age of Chivalry`() {
        val base = wgbg.engine.Rules.newGame(wgbg.engine.GameConfig(), 1)
        base.hands[0] += 3
        assertEquals("3", cardsLabel(base, 0))
        val ck = wgbg.engine.Rules.newGame(wgbg.engine.GameConfig(expansion = wgbg.engine.Expansion.CITIES_AND_KNIGHTS), 1)
        ck.hands[0] += 2
        ck.ck!!.progressHand[0] += 1
        assertEquals("2/1", cardsLabel(ck, 0))
    }
}
