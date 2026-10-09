package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.Resource.BRICK
import wgbg.engine.Resource.ORE
import wgbg.engine.Resource.SHEEP
import wgbg.engine.Resource.WHEAT
import wgbg.engine.Resource.WOOD

/** Counter-offers: a responder proposes different terms, the offerer takes or refuses them. */
class CounterTradeTest {
    private fun cards(vararg rc: Pair<Int, Int>) = IntArray(CARD_TYPES).also { for ((r, n) in rc) it[r] = n }

    /** Player 0 offers 1 wood for 1 brick; players 1 and 2 hold brick, sheep and ore. */
    private fun offered(): GameState {
        val s = T.playState(4).also { T.roll(it, 8) }
        T.give(s, 0, WOOD, 2)
        T.give(s, 0, WHEAT, 1)
        T.give(s, 1, BRICK, 1)
        T.give(s, 1, SHEEP, 1)
        T.give(s, 2, ORE, 1)
        Rules.applyChecked(s, Action.offerTrade(cards(WOOD to 1), cards(BRICK to 1)))
        assertEquals(1, s.actingPlayer)
        return s
    }

    private fun hand(s: GameState, p: Int) = (0 until CARD_TYPES).map { s.hand(p, it) }

    @Test
    fun `offerer accepts a counter and the cards move on the countered terms`() {
        val s = offered()
        // Player 1: "give me wheat instead of wood".
        val counter = Action.counterTrade(cards(WHEAT to 1), cards(BRICK to 1))
        assertTrue(Rules.isLegal(s, counter))
        Rules.applyChecked(s, counter)
        assertEquals(Phase.DECIDE_COUNTER, s.phase)
        assertEquals(0, s.actingPlayer)
        assertEquals(1, s.counterFrom)
        assertEquals(counter, s.counterOffer)
        assertEquals(setOf(Action.ACCEPT_TRADE, Action.REJECT_TRADE), T.legal(s).toSet())
        Rules.applyChecked(s, Action.ACCEPT_TRADE)
        assertEquals(Phase.PLAY_TURN, s.phase)
        assertEquals(0, s.actingPlayer)
        assertEquals(listOf(2, 1, 0, 0, 0), hand(s, 0)) // wood 2, brick 1, no wheat
        assertEquals(listOf(0, 0, 1, 1, 0), hand(s, 1)) // sheep 1, wheat 1
        assertEquals(-1, s.counterFrom)
        assertNull(s.counterOffer)
        T.checkInvariants(s)
    }

    @Test
    fun `a refused counter sends the original offer on to the next player`() {
        val s = offered()
        Rules.applyChecked(s, Action.counterTrade(cards(WHEAT to 1), cards(BRICK to 1)))
        Rules.applyChecked(s, Action.REJECT_TRADE)
        assertEquals(Phase.DECIDE_TRADE, s.phase)
        assertEquals(2, s.actingPlayer)
        assertEquals(1, s.tradeOffer[WOOD])
        assertEquals(1, s.tradeOffer[CARD_TYPES + BRICK])
        assertEquals(-1, s.counterFrom)
        Rules.applyChecked(s, Action.REJECT_TRADE)
        Rules.applyChecked(s, Action.REJECT_TRADE)
        assertEquals(Phase.PLAY_TURN, s.phase, "nobody accepted: back to the turn")
        assertEquals(listOf(2, 0, 0, 1, 0), hand(s, 0))
    }

    @Test
    fun `earlier acceptances survive a refused counter`() {
        val s = T.playState(4).also { T.roll(it, 8) }
        T.give(s, 0, WOOD, 2)
        T.give(s, 1, BRICK, 1)
        T.give(s, 2, BRICK, 1)
        T.give(s, 2, SHEEP, 1)
        T.give(s, 0, ORE, 1)
        Rules.applyChecked(s, Action.offerTrade(cards(WOOD to 1), cards(BRICK to 1)))
        Rules.applyChecked(s, Action.ACCEPT_TRADE) // player 1
        Rules.applyChecked(s, Action.counterTrade(cards(ORE to 1), cards(BRICK to 1))) // player 2
        Rules.applyChecked(s, Action.REJECT_TRADE) // offerer refuses
        assertEquals(3, s.actingPlayer)
        Rules.applyChecked(s, Action.REJECT_TRADE)
        assertEquals(Phase.DECIDE_ACCEPTEES, s.phase)
        assertEquals(setOf(Action.CANCEL_TRADE, Action.confirmTrade(1)), T.legal(s).toSet())
    }

    @Test
    fun `counter state is part of the hash and of copies`() {
        val s = offered()
        val before = s.stateHash()
        Rules.applyChecked(s, Action.counterTrade(cards(WHEAT to 1), cards(BRICK to 1)))
        val a = s.copy()
        val b = s.copy().also { it.counterTerms = Action.counterTrade(cards(WOOD to 2), cards(BRICK to 1)).payload }
        assertEquals(s.stateHash(), a.stateHash())
        assertFalse(a.stateHash() == b.stateHash(), "different counters hash differently")
        assertFalse(before == s.stateHash())
    }

    @Test
    fun `invalid counters are refused`() {
        val s = offered()
        fun bad(give: IntArray, want: IntArray, why: String) =
            assertFalse(Rules.isLegal(s, Action.counterTrade(give, want)), why)
        bad(cards(WOOD to 1), cards(BRICK to 1), "same as the offer")
        bad(cards(ORE to 1), cards(BRICK to 1), "offerer has no ore")
        bad(cards(WOOD to 1), cards(ORE to 1), "responder has no ore")
        bad(cards(WOOD to 1), cards(), "gets nothing")
        bad(cards(), cards(BRICK to 1), "gives nothing")
        bad(cards(WOOD to 1, BRICK to 1), cards(BRICK to 1), "brick on both sides")
        assertFalse(Rules.isLegal(s, Action.of(ActionType.COUNTER_TRADE, 1L shl 44)), "junk high bits")
        // Not while it's the offerer's own turn, nor in an Age of Chivalry game.
        val turn = T.playState(4).also { T.roll(it, 8) }
        T.give(turn, 0, WOOD, 1)
        assertFalse(Rules.isLegal(turn, Action.counterTrade(cards(WOOD to 1), cards(BRICK to 1))))
        val ck = Rules.newGame(GameConfig(expansion = Expansion.CITIES_AND_KNIGHTS), 1)
        assertFalse(Rules.isLegal(ck, Action.counterTrade(cards(WOOD to 1), cards(BRICK to 1))))
    }

    @Test
    fun `counters are enumerated for bots when trade offers are generated`() {
        val s = offered()
        assertTrue(T.legal(s).none { it.type == ActionType.COUNTER_TRADE }, "not by default")
        val g = T.playState(4, GameConfig(generateTradeOffers = true)).also { T.roll(it, 8) }
        T.give(g, 0, WOOD, 1)
        T.give(g, 1, BRICK, 1)
        T.give(g, 1, SHEEP, 1)
        Rules.applyChecked(g, Action.offerTrade(cards(WOOD to 1), cards(BRICK to 1)))
        val counters = T.legal(g).filter { it.type == ActionType.COUNTER_TRADE }.toSet()
        assertEquals(setOf(Action.counterTrade(cards(WOOD to 1), cards(SHEEP to 1))), counters)
        for (c in counters) assertTrue(Rules.isLegal(g, c))
    }
}
