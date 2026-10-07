package wgbg.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HiddenInfoTest {
    private fun midGame(): GameState {
        val s = Rules.newGame(GameConfig(), 11)
        val bot = Rng(4)
        val buf = ActionBuffer()
        while (s.turn < 120 && !s.isOver) {
            Rules.legalActions(s, buf)
            Rules.apply(s, buf[bot.nextInt(buf.size)])
        }
        return s
    }

    @Test
    fun `view exposes only own cards and public counts`() {
        val s = T.playState()
        T.give(s, 1, Resource.ORE, 3)
        T.giveDev(s, 1, DevCard.VICTORY_POINT)
        T.giveDev(s, 0, DevCard.KNIGHT)
        val v = HiddenInfo.view(s, 0)
        assertEquals(3, v.handSize[1])
        assertEquals(1, v.devCount[1])
        assertEquals(0, v.publicVictoryPoints[1])
        assertEquals(listOf(0, 0, 0, 0, 0), v.hand.toList())
        assertEquals(1, v.devHand[DevCard.KNIGHT])
        assertEquals(23, v.devDeckSize)
    }

    @Test
    fun `determinize keeps public info and the viewer's own cards`() {
        val s = midGame()
        for (seed in 0L until 50) {
            val d = HiddenInfo.determinize(s, 0, seed)
            T.checkInvariants(d)
            for (p in 0 until s.numPlayers) {
                assertEquals(s.devCount(p), d.devCount(p))
                assertEquals(s.publicVictoryPoints(p), d.publicVictoryPoints(p))
                assertEquals(s.handSize(p), d.handSize(p))
                assertEquals(
                    (0 until DevCard.COUNT).sumOf { s.devNew[p * DevCard.COUNT + it] },
                    (0 until DevCard.COUNT).sumOf { d.devNew[p * DevCard.COUNT + it] },
                )
            }
            assertArrayEquals(s.devHand.copyOfRange(0, DevCard.COUNT), d.devHand.copyOfRange(0, DevCard.COUNT))
            assertArrayEquals(s.devPlayed, d.devPlayed)
            assertEquals(s.devDeck.sum(), d.devDeck.sum())
            assertEquals(seed, d.rng.seed)
        }
    }
}
