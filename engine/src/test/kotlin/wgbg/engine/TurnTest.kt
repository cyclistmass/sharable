package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.Resource.BRICK
import wgbg.engine.Resource.ORE
import wgbg.engine.Resource.SHEEP
import wgbg.engine.Resource.WHEAT
import wgbg.engine.Resource.WOOD

/** Rolling, production, the robber and discards. */
class TurnTest {
    private val brick6 = Hex(0, -1) // beginner board: Brick 6

    @Test
    fun `before rolling only dev cards and ROLL are allowed`() {
        val s = T.playState()
        T.giveAll(s, 0, Cost.SETTLEMENT, 3)
        assertEquals(listOf(Action.ROLL), T.legal(s))
        T.roll(s, 8)
        assertTrue(Action.END_TURN in T.legal(s))
        assertFalse(Action.ROLL in T.legal(s))
    }

    @Test
    fun `production pays settlements 1 and cities 2`() {
        val s = T.playState()
        T.settle(s, 0, T.corner(s, brick6.q, brick6.r, 0))
        T.settle(s, 1, T.corner(s, brick6.q, brick6.r, 3), city = true)
        T.roll(s, 6)
        assertEquals(1, s.hand(0, BRICK))
        assertEquals(2, s.hand(1, BRICK))
        assertEquals(16, s.bank[BRICK])
        T.checkInvariants(s)
    }

    @Test
    fun `robber blocks production`() {
        val s = T.playState()
        T.settle(s, 0, T.corner(s, brick6.q, brick6.r, 0))
        s.robberHex = s.board.hexId(brick6)
        T.roll(s, 6)
        assertEquals(0, s.handSize(0))
    }

    @Test
    fun `bank shortage - nobody is paid when several players are owed`() {
        val s = T.playState()
        T.settle(s, 0, T.corner(s, brick6.q, brick6.r, 0))
        T.settle(s, 1, T.corner(s, brick6.q, brick6.r, 3), city = true)
        T.give(s, 3, BRICK, 17) // bank has 2, owed 3
        T.roll(s, 6)
        assertEquals(0, s.hand(0, BRICK))
        assertEquals(0, s.hand(1, BRICK))
        assertEquals(2, s.bank[BRICK])
    }

    @Test
    fun `bank shortage - a single owed player takes what is left`() {
        val s = T.playState()
        T.settle(s, 1, T.corner(s, brick6.q, brick6.r, 3), city = true)
        T.give(s, 3, BRICK, 18) // bank has 1, owed 2
        T.roll(s, 6)
        assertEquals(1, s.hand(1, BRICK))
        assertEquals(0, s.bank[BRICK])
    }

    @Test
    fun `rolling 7 makes players with more than 7 cards discard half, rounded down`() {
        val s = T.playState()
        T.give(s, 0, WOOD, 9)
        T.give(s, 1, BRICK, 7)
        T.give(s, 2, SHEEP, 6); T.give(s, 2, ORE, 3)
        T.roll(s, 7)
        assertEquals(Phase.DISCARD, s.phase)
        assertEquals(0, s.actingPlayer)
        assertEquals(listOf(4, 0, 4, 0), s.discardOwed.toList())
        assertEquals(listOf(Action.discard(WOOD)), T.legal(s))
        repeat(4) { Rules.applyChecked(s, Action.discard(WOOD)) }
        assertEquals(2, s.actingPlayer)
        assertEquals(setOf(Action.discard(SHEEP), Action.discard(ORE)), T.legal(s).toSet())
        repeat(3) { Rules.applyChecked(s, Action.discard(ORE)) }
        Rules.applyChecked(s, Action.discard(SHEEP))
        assertEquals(Phase.MOVE_ROBBER, s.phase)
        assertEquals(0, s.actingPlayer)
        assertEquals(listOf(5, 7, 5, 0), (0 until 4).map(s::handSize))
        T.checkInvariants(s)
    }

    @Test
    fun `random discard mode drops half the hand in one action`() {
        val s = T.playState(config = GameConfig(layout = BoardLayout.BEGINNER, randomDiscard = true))
        T.give(s, 1, WOOD, 5); T.give(s, 1, ORE, 6)
        T.roll(s, 7)
        assertEquals(1, s.actingPlayer)
        assertEquals(listOf(Action.discard(-1)), T.legal(s))
        Rules.applyChecked(s, Action.discard(-1))
        assertEquals(6, s.handSize(1))
        assertEquals(Phase.MOVE_ROBBER, s.phase)
        T.checkInvariants(s)
    }

    @Test
    fun `no discards goes straight to robber, which must move and steals one card`() {
        val s = T.playState()
        val h = s.board.hexId(brick6)
        T.settle(s, 1, T.corner(s, brick6.q, brick6.r, 0))
        T.settle(s, 0, T.corner(s, brick6.q, brick6.r, 3))
        T.roll(s, 7)
        assertEquals(Phase.MOVE_ROBBER, s.phase)
        var moves = T.legal(s)
        assertEquals(18, moves.size) // every hex but the robber's; nobody has cards
        assertTrue(moves.none { it.robberHex == s.robberHex })
        assertTrue(Action.moveRobber(h, -1) in moves)

        T.give(s, 1, ORE, 2)
        moves = T.legal(s)
        assertTrue(Action.moveRobber(h, 1) in moves)
        assertFalse(Action.moveRobber(h, -1) in moves, "must steal when possible")
        assertFalse(Action.moveRobber(h, 0) in moves, "cannot steal from yourself")
        Rules.applyChecked(s, Action.moveRobber(h, 1))
        assertEquals(h, s.robberHex)
        assertEquals(1, s.hand(0, ORE))
        assertEquals(1, s.hand(1, ORE))
        assertEquals(Phase.PLAY_TURN, s.phase)
        T.checkInvariants(s)
    }

    @Test
    fun `turn limit ends the game as a draw`() {
        val s = T.playState(config = GameConfig(layout = BoardLayout.BEGINNER, maxTurns = 3))
        repeat(3) {
            T.roll(s, 8)
            Rules.applyChecked(s, Action.END_TURN)
        }
        assertTrue(s.isOver)
        assertEquals(-1, s.winner)
        assertTrue(T.legal(s).isEmpty())
    }

    @Test
    fun `end turn passes to the next seat and resets turn flags`() {
        val s = T.playState(3)
        T.roll(s, 8)
        Rules.applyChecked(s, Action.END_TURN)
        assertEquals(1, s.currentPlayer)
        assertEquals(1, s.actingPlayer)
        assertFalse(s.hasRolled)
        assertEquals(1, s.turn)
        T.roll(s, 8); Rules.applyChecked(s, Action.END_TURN)
        T.roll(s, 8); Rules.applyChecked(s, Action.END_TURN)
        assertEquals(0, s.currentPlayer)
    }

    @Test
    fun `win is only checked on the current player's turn`() {
        val s = T.playState(config = GameConfig(layout = BoardLayout.BEGINNER, vpToWin = 3))
        T.settle(s, 1, T.corner(s, 0, -2, 0))
        T.settle(s, 1, T.corner(s, 0, 2, 3))
        T.giveDev(s, 1, DevCard.VICTORY_POINT)
        assertEquals(3, s.victoryPoints(1))
        T.roll(s, 8)
        assertFalse(s.isOver)
        Rules.applyChecked(s, Action.END_TURN)
        assertTrue(s.isOver)
        assertEquals(1, s.winner)
    }
}
