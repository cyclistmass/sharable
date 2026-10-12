package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every Fortune card in mid-game positions: it does what it says and the turn stays the player's (Mauro, 2026-10-12). */
class EveryFortuneCardTest {
    private fun position(seed: Long): GameState? {
        val s = Rules.newGame(GameConfig(numPlayers = 4), seed)
        val rng = Rng(seed * 17)
        val buf = ActionBuffer()
        var guard = 0
        while (!s.isOver && guard++ < 20_000) {
            if (s.turn >= 20 && s.phase == Phase.PLAY_TURN && s.currentPlayer == 0 && s.actingPlayer == 0 && s.hasRolled) return s
            Rules.legalActions(s, buf)
            Rules.apply(s, buf[rng.nextInt(buf.size)])
        }
        return null
    }

    /** Back to player 0's main turn: answer the bandit move and any other prompt with the first choice. */
    private fun settle(s: GameState) {
        var guard = 0
        while (s.phase != Phase.PLAY_TURN && !s.isOver && guard++ < 100) Rules.applyChecked(s, Rules.legalActions(s).first())
        var roads = 0
        while (s.freeRoads > 0 && roads++ < 3) Rules.applyChecked(s, Rules.legalActions(s).first { it.type == ActionType.BUILD_ROAD })
    }

    @Test
    fun `every Fortune card works and the turn goes on`() {
        val played = IntArray(DevCard.COUNT)
        for (seed in 1L..40L) {
            val start = position(seed) ?: continue
            for (card in listOf(DevCard.KNIGHT, DevCard.YEAR_OF_PLENTY, DevCard.MONOPOLY, DevCard.ROAD_BUILDING)) {
                val base = start.copy()
                if (base.devDeck[card] == 0 || base.devPlayedThisTurn) continue
                T.giveDev(base, 0, card)
                val plays = Rules.legalActions(base).filter { it.type.toString() == "PLAY_" + when (card) {
                    DevCard.KNIGHT -> "KNIGHT_CARD"; DevCard.YEAR_OF_PLENTY -> "YEAR_OF_PLENTY"; DevCard.MONOPOLY -> "MONOPOLY"; else -> "ROAD_BUILDING" } }
                for (a in plays.take(4)) {
                    val tag = "${DevCard.NAMES[card]} seed $seed $a"
                    val s = base.copy()
                    Rules.applyChecked(s, a)
                    settle(s)
                    if (s.isOver) continue
                    when (card) {
                        DevCard.KNIGHT -> assertTrue(s.robberHex != base.robberHex, "$tag: bandit moved")
                        DevCard.YEAR_OF_PLENTY -> assertEquals(base.handSize(0) + (if (a.yopSecond >= 0) 2 else 1), s.handSize(0), "$tag: cards from the bank")
                        DevCard.MONOPOLY -> {
                            val r = a.payload.toInt()
                            for (q in 1 until 4) assertEquals(0, s.hands[q * CARD_TYPES + r], "$tag: player $q gave theirs")
                            assertEquals((0 until 4).sumOf { base.hands[it * CARD_TYPES + r] }, s.hands[r], "$tag: all of it is yours")
                        }
                        else -> assertTrue(s.edgeOwner.count { it == 0 } > base.edgeOwner.count { it == 0 }, "$tag: free roads built")
                    }
                    assertEquals(0, s.currentPlayer, "$tag: still my turn")
                    assertEquals(0, s.actingPlayer, "$tag: I act")
                    val legal = Rules.legalActions(s)
                    assertFalse(legal.any { it.type == ActionType.ROLL }, "$tag: no second roll")
                    assertTrue(Action.END_TURN in legal, "$tag: I end my own turn")
                    assertEquals(0, s.devHand[card], "$tag: the card is used up")
                    T.checkInvariants(s)
                    played[card]++
                }
            }
        }
        for (card in listOf(DevCard.KNIGHT, DevCard.YEAR_OF_PLENTY, DevCard.MONOPOLY, DevCard.ROAD_BUILDING))
            assertTrue(played[card] > 0, "${DevCard.NAMES[card]} was never playable")
    }
}
