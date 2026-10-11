package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SavedSetupTest {
    @Test
    fun `the last game's settings and name come back exactly`() {
        val s = GameSetup(name = "Mauro | the \"Great\" ü", photo = "content://media/1?x=a|b").withRuleset(Ruleset.CHIVALRY)
            .withSeat(1, SeatKind.BOT).withSeat(2, SeatKind.EMPTY).withSeat(3, SeatKind.FRIEND).withDecks(2).withPoints(12)
        assertEquals(s, GameSetup.decode(s.encode()))
        assertEquals(GameSetup(), GameSetup.decode(GameSetup().encode()))
    }

    @Test
    fun `missing, old or damaged saves fall back to the defaults`() {
        for (bad in listOf(null, "", "v0|x", "v1|You||BASE|10|0", "v1|You||NOPE|10|0|FRIEND:GREEDY,BOT:GREEDY,BOT:GREEDY,BOT:GREEDY",
                "v1|You||BASE|ten|0|FRIEND:GREEDY,BOT:GREEDY,BOT:GREEDY,BOT:GREEDY", "v1|You||BASE|10|0|BOT:GREEDY,BOT:GREEDY,BOT:GREEDY,BOT:GREEDY",
                "v1|You||BASE|10|0|FRIEND:GREEDY,BOT:GREEDY"))
            assertNull(GameSetup.decode(bad), "$bad")
    }

    @Test
    fun `out-of-range numbers are clamped, a blank name becomes You`() {
        val s = GameSetup.decode("v1|||BASE|99|9|FRIEND:GREEDY,BOT:EASY,BOT:STRONG,EMPTY:GREEDY")!!
        assertEquals("You", s.name); assertNull(s.photo)
        assertEquals(GameSetup.POINTS_RANGE.last, s.points); assertEquals(GameSetup.MAX_DECKS, s.decks)
    }
}
