package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `the menu keeps other players' hidden points and card kinds secret while you play`() {
        val s = wgbg.engine.Rules.newGame(wgbg.engine.GameConfig(numPlayers = 3), 4)
        s.devHand[1 * wgbg.engine.DevCard.COUNT + wgbg.engine.DevCard.VICTORY_POINT] = 1   // player 1 holds a Landmark
        s.hands[1 * wgbg.engine.CARD_TYPES + 2] = 3
        val asSeen = PlayerInfo.of(s, 1, me = 0)
        assertEquals(s.publicVictoryPoints(1), asSeen.vp)
        assertTrue(asSeen.hand.isEmpty())
        assertEquals(s.handSize(1), asSeen.cards)
        val own = PlayerInfo.of(s, 1, me = 1)
        assertEquals(s.publicVictoryPoints(1) + 1, own.vp)
        assertEquals(3, own.hand[2])
        assertEquals(s.victoryPoints(1), PlayerInfo.of(s, 1, me = -1).vp)   // watching bots shows all
    }
}
