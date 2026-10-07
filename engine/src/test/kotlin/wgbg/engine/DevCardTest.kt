package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.Resource.BRICK
import wgbg.engine.Resource.ORE
import wgbg.engine.Resource.WHEAT
import wgbg.engine.Resource.WOOD

class DevCardTest {
    private fun passTurn(s: GameState) {
        if (!s.hasRolled) T.roll(s, 8)
        Rules.applyChecked(s, Action.END_TURN)
    }

    @Test
    fun `deck has 25 cards with the official mix`() {
        assertEquals(listOf(14, 2, 2, 2, 5), DevCard.DECK.toList())
    }

    @Test
    fun `buying draws a card that cannot be played until next turn`() {
        val s = T.playState()
        T.roll(s, 8)
        T.giveAll(s, 0, Cost.DEV_CARD)
        Rules.applyChecked(s, Action.BUY_DEVELOPMENT_CARD)
        assertEquals(24, s.devDeck.sum())
        assertEquals(1, s.devCount(0))
        assertEquals(1, s.devNew.sum())
        assertEquals(0, s.handSize(0))
        T.checkInvariants(s)

        val k = T.playState()
        T.giveDev(k, 0, DevCard.KNIGHT, new = true)
        assertFalse(Action.PLAY_KNIGHT_CARD in T.legal(k))
        repeat(4) { passTurn(k) }
        assertEquals(0, k.currentPlayer)
        assertTrue(Action.PLAY_KNIGHT_CARD in T.legal(k))
    }

    @Test
    fun `knight before rolling, then roll, and only one dev card per turn`() {
        val s = T.playState()
        T.giveDev(s, 0, DevCard.KNIGHT)
        T.giveDev(s, 0, DevCard.KNIGHT)
        T.giveDev(s, 0, DevCard.MONOPOLY)
        assertTrue(Action.PLAY_KNIGHT_CARD in T.legal(s))
        assertTrue(Action.ROLL in T.legal(s))
        Rules.applyChecked(s, Action.PLAY_KNIGHT_CARD)
        assertEquals(Phase.MOVE_ROBBER, s.phase)
        Rules.applyChecked(s, T.legal(s)[0])
        assertEquals(listOf(Action.ROLL), T.legal(s))
        T.roll(s, 8)
        assertTrue(T.legal(s).none { it.type == ActionType.PLAY_KNIGHT_CARD || it.type == ActionType.PLAY_MONOPOLY })
        assertEquals(1, s.knightsPlayed(0))
        T.checkInvariants(s)
    }

    @Test
    fun `largest army needs 3 knights and strictly more to take over`() {
        val s = T.playState()
        s.devPlayed[0 * DevCard.COUNT + DevCard.KNIGHT] = 2
        s.devDeck[DevCard.KNIGHT] -= 2
        T.giveDev(s, 0, DevCard.KNIGHT)
        Rules.applyChecked(s, Action.PLAY_KNIGHT_CARD)
        assertEquals(0, s.largestArmyOwner)
        assertEquals(2, s.victoryPoints(0))
        Rules.applyChecked(s, T.legal(s)[0])
        passTurn(s)
        // Player 1 reaches 3: a tie does not take the card; 4 does.
        s.devPlayed[1 * DevCard.COUNT + DevCard.KNIGHT] = 2
        s.devDeck[DevCard.KNIGHT] -= 2
        T.giveDev(s, 1, DevCard.KNIGHT)
        Rules.applyChecked(s, Action.PLAY_KNIGHT_CARD)
        assertEquals(0, s.largestArmyOwner)
        Rules.applyChecked(s, T.legal(s)[0])
        passTurn(s)
        repeat(3) { passTurn(s) }
        T.giveDev(s, 1, DevCard.KNIGHT)
        Rules.applyChecked(s, Action.PLAY_KNIGHT_CARD)
        assertEquals(1, s.largestArmyOwner)
        assertEquals(2, s.victoryPoints(1))
        assertEquals(0, s.victoryPoints(0))
        T.checkInvariants(s)
    }

    @Test
    fun `year of plenty takes two cards from the bank`() {
        val s = T.playState()
        T.giveDev(s, 0, DevCard.YEAR_OF_PLENTY)
        val options = T.legal(s).filter { it.type == ActionType.PLAY_YEAR_OF_PLENTY }
        assertEquals(15, options.size)
        Rules.applyChecked(s, Action.yearOfPlenty(WOOD, ORE))
        assertEquals(1, s.hand(0, WOOD))
        assertEquals(1, s.hand(0, ORE))
        assertEquals(18, s.bank[WOOD])
        T.checkInvariants(s)
    }

    @Test
    fun `year of plenty with a nearly empty bank`() {
        val s = T.playState()
        for (r in 0 until CARD_TYPES) T.give(s, 1, r, if (r == BRICK) 18 else 19)
        T.giveDev(s, 0, DevCard.YEAR_OF_PLENTY)
        assertEquals(
            listOf(Action.yearOfPlenty(BRICK)),
            T.legal(s).filter { it.type == ActionType.PLAY_YEAR_OF_PLENTY },
        )
    }

    @Test
    fun `monopoly takes every card of a type`() {
        val s = T.playState()
        T.give(s, 1, WHEAT, 3)
        T.give(s, 2, WHEAT, 2)
        T.give(s, 3, ORE, 2)
        T.giveDev(s, 0, DevCard.MONOPOLY)
        Rules.applyChecked(s, Action.monopoly(WHEAT))
        assertEquals(5, s.hand(0, WHEAT))
        assertEquals(0, s.hand(1, WHEAT) + s.hand(2, WHEAT))
        assertEquals(2, s.hand(3, ORE))
        T.checkInvariants(s)
    }

    @Test
    fun `road building places two free roads`() {
        val s = T.playState()
        val v = T.corner(s, 0, -1, 0)
        T.settle(s, 0, v)
        T.giveDev(s, 0, DevCard.ROAD_BUILDING)
        Rules.applyChecked(s, Action.PLAY_ROAD_BUILDING)
        assertEquals(2, s.freeRoads)
        repeat(2) {
            val legal = T.legal(s)
            assertTrue(legal.isNotEmpty() && legal.all { it.type == ActionType.BUILD_ROAD })
            Rules.applyChecked(s, legal[0])
        }
        assertEquals(0, s.freeRoads)
        assertEquals(0, s.handSize(0))
        assertEquals(13, s.piecesLeft[Piece.ROAD])
        assertEquals(listOf(Action.ROLL), T.legal(s))
        T.checkInvariants(s)
    }

    @Test
    fun `road building with one road left gives one road`() {
        val s = T.playState()
        T.settle(s, 0, T.corner(s, 0, -1, 0))
        s.piecesLeft[Piece.ROAD] = 1
        T.giveDev(s, 0, DevCard.ROAD_BUILDING)
        Rules.applyChecked(s, Action.PLAY_ROAD_BUILDING)
        assertEquals(1, s.freeRoads)
        Rules.applyChecked(s, T.legal(s)[0])
        assertEquals(0, s.freeRoads)
    }

    @Test
    fun `victory point card wins immediately on own turn`() {
        val s = T.playState(config = GameConfig(layout = BoardLayout.BEGINNER, vpToWin = 3))
        T.settle(s, 0, T.corner(s, 0, -2, 0))
        T.settle(s, 0, T.corner(s, 0, 2, 3))
        T.roll(s, 8)
        s.devDeck.fill(0)
        s.devDeck[DevCard.VICTORY_POINT] = 25 // rig the deck; conservation is not checked here
        T.giveAll(s, 0, Cost.DEV_CARD)
        Rules.applyChecked(s, Action.BUY_DEVELOPMENT_CARD)
        assertTrue(s.isOver)
        assertEquals(0, s.winner)
        assertEquals(2, s.publicVictoryPoints(0))
    }
}
