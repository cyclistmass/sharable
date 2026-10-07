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

/** Building, connectivity, piece limits and maritime/domestic trade. */
class BuildTest {
    private fun rolled(players: Int = 4): GameState = T.playState(players).also { T.roll(it, 8) }

    @Test
    fun `roads extend from own pieces but not through opponent buildings`() {
        val s = rolled()
        val c = IntArray(6) { T.corner(s, 0, -1, it) }
        T.settle(s, 0, c[0])
        T.path(s, 0, c[0], c[1])
        T.settle(s, 1, c[2])
        T.giveAll(s, 0, Cost.ROAD)
        val roads = T.legal(s).filter { it.type == ActionType.BUILD_ROAD }.map { it.payload.toInt() }.toSet()
        // Any free edge touching c0 or c1 (c1-c2 may end at the opponent's settlement), nothing beyond c2.
        val expected = mutableSetOf<Int>()
        for (v in intArrayOf(c[0], c[1])) for (e in s.board.topology.vertexEdges[v]) if (s.edgeOwner[e] < 0) expected += e
        assertEquals(expected, roads)
        // Build up to the opponent settlement; we still cannot continue past it.
        Rules.applyChecked(s, Action.buildRoad(s.board.edgeBetween(c[1], c[2])))
        T.giveAll(s, 0, Cost.ROAD)
        val after = T.legal(s).filter { it.type == ActionType.BUILD_ROAD }.map { it.payload.toInt() }
        for (e in s.board.topology.vertexEdges[c[2]]) assertFalse(e in after && s.edgeOwner[e] < 0)
        T.checkInvariants(s)
    }

    @Test
    fun `settlements need an own road and respect the distance rule`() {
        val s = rolled()
        val c = IntArray(6) { T.corner(s, 0, -1, it) }
        T.settle(s, 0, c[0])
        T.path(s, 0, c[0], c[1], c[2], c[3])
        T.giveAll(s, 0, Cost.SETTLEMENT)
        val spots = T.legal(s).filter { it.type == ActionType.BUILD_SETTLEMENT }.map { it.payload.toInt() }.toSet()
        assertEquals(setOf(c[2], c[3]), spots)
        Rules.applyChecked(s, Action.buildSettlement(c[2]))
        assertEquals(0, s.handSize(0))
        assertEquals(2, s.victoryPoints(0))
        assertEquals(3, s.piecesLeft[0 * Piece.COUNT + Piece.SETTLEMENT])
        T.checkInvariants(s)
    }

    @Test
    fun `city upgrade returns the settlement piece`() {
        val s = rolled()
        val v = T.corner(s, 0, -1, 0)
        T.settle(s, 0, v)
        T.giveAll(s, 0, Cost.CITY)
        assertTrue(Action.buildCity(v) in T.legal(s))
        Rules.applyChecked(s, Action.buildCity(v))
        assertEquals(Piece.CITY, s.vertexBuilding[v])
        assertEquals(5, s.piecesLeft[Piece.SETTLEMENT])
        assertEquals(3, s.piecesLeft[Piece.CITY])
        assertEquals(2, s.victoryPoints(0))
        T.checkInvariants(s)
    }

    @Test
    fun `piece limits`() {
        val s = rolled()
        val c = IntArray(6) { T.corner(s, 0, -1, it) }
        T.settle(s, 0, c[0])
        T.path(s, 0, c[0], c[1], c[2])
        T.giveAll(s, 0, Cost.SETTLEMENT)
        T.giveAll(s, 0, Cost.CITY)
        T.giveAll(s, 0, Cost.ROAD)
        s.piecesLeft[Piece.SETTLEMENT] = 0
        s.piecesLeft[Piece.CITY] = 0
        s.piecesLeft[Piece.ROAD] = 0
        val types = T.legal(s).map { it.type }.toSet()
        assertFalse(ActionType.BUILD_SETTLEMENT in types)
        assertFalse(ActionType.BUILD_CITY in types)
        assertFalse(ActionType.BUILD_ROAD in types)
    }

    @Test
    fun `maritime trade rates 4, 3 and 2`() {
        val s = rolled()
        T.give(s, 0, WOOD, 3)
        assertTrue(T.legal(s).none { it.type == ActionType.MARITIME_TRADE })
        T.give(s, 0, WOOD, 1)
        val trades = T.legal(s).filter { it.type == ActionType.MARITIME_TRADE }
        assertEquals(setOf(BRICK, SHEEP, WHEAT, ORE), trades.map { it.maritimeGet }.toSet())
        assertTrue(trades.all { it.maritimeGive == WOOD })

        val orePort = s.board.ports.first { it.type == ORE }
        val genericSpot = s.board.ports.filter { it.type == Port.GENERIC }.flatMap { listOf(it.v1, it.v2) }
            .first { s.board.edgeBetween(it, orePort.v1) < 0 }
        T.settle(s, 0, genericSpot)
        T.settle(s, 0, orePort.v1)
        assertEquals(3, Rules.tradeRate(s, 0, WOOD))
        assertEquals(2, Rules.tradeRate(s, 0, ORE))
        T.give(s, 0, ORE, 2)
        Rules.applyChecked(s, Action.maritimeTrade(ORE, BRICK))
        assertEquals(0, s.hand(0, ORE))
        assertEquals(1, s.hand(0, BRICK))
        Rules.applyChecked(s, Action.maritimeTrade(WOOD, SHEEP))
        assertEquals(1, s.hand(0, WOOD))
        assertEquals(1, s.hand(0, SHEEP))
        T.checkInvariants(s)
    }

    @Test
    fun `maritime trade needs the bank to have the card`() {
        val s = rolled()
        T.give(s, 0, WOOD, 4)
        T.give(s, 1, ORE, 19)
        assertTrue(T.legal(s).none { it.type == ActionType.MARITIME_TRADE && it.maritimeGet == ORE })
    }

    @Test
    fun `domestic trade - offer, responses, confirm with one acceptor`() {
        val s = rolled()
        T.give(s, 0, WOOD, 2)
        T.give(s, 1, BRICK, 1)
        T.give(s, 3, BRICK, 1)
        val offer = Action.offerTrade(intArrayOf(1, 0, 0, 0, 0), intArrayOf(0, 1, 0, 0, 0))
        assertTrue(Rules.isLegal(s, offer))
        Rules.applyChecked(s, offer)
        assertEquals(Phase.DECIDE_TRADE, s.phase)
        assertEquals(1, s.actingPlayer)
        assertEquals(setOf(Action.ACCEPT_TRADE, Action.REJECT_TRADE), T.legal(s).toSet())
        Rules.applyChecked(s, Action.ACCEPT_TRADE)
        assertEquals(2, s.actingPlayer)
        assertEquals(listOf(Action.REJECT_TRADE), T.legal(s)) // has no brick
        Rules.applyChecked(s, Action.REJECT_TRADE)
        Rules.applyChecked(s, Action.ACCEPT_TRADE)
        assertEquals(Phase.DECIDE_ACCEPTEES, s.phase)
        assertEquals(0, s.actingPlayer)
        assertEquals(setOf(Action.CANCEL_TRADE, Action.confirmTrade(1), Action.confirmTrade(3)), T.legal(s).toSet())
        Rules.applyChecked(s, Action.confirmTrade(3))
        assertEquals(Phase.PLAY_TURN, s.phase)
        assertEquals(listOf(1, 1, 0, 0, 0), (0 until 5).map { s.hand(0, it) })
        assertEquals(listOf(1, 0, 0, 0, 0), (0 until 5).map { s.hand(3, it) })
        assertEquals(1, s.hand(1, BRICK))
        T.checkInvariants(s)
    }

    @Test
    fun `domestic trade - all reject returns to the turn, cancel too`() {
        val s = rolled(3)
        T.give(s, 0, WOOD, 1)
        T.give(s, 1, ORE, 1)
        val offer = Action.offerTrade(intArrayOf(1, 0, 0, 0, 0), intArrayOf(0, 0, 0, 0, 1))
        Rules.applyChecked(s, offer)
        Rules.applyChecked(s, Action.REJECT_TRADE)
        Rules.applyChecked(s, Action.REJECT_TRADE)
        assertEquals(Phase.PLAY_TURN, s.phase)
        assertEquals(0, s.actingPlayer)
        Rules.applyChecked(s, offer)
        Rules.applyChecked(s, Action.ACCEPT_TRADE)
        Rules.applyChecked(s, Action.REJECT_TRADE)
        Rules.applyChecked(s, Action.CANCEL_TRADE)
        assertEquals(Phase.PLAY_TURN, s.phase)
        assertEquals(1, s.hand(0, WOOD))
    }

    @Test
    fun `invalid domestic offers`() {
        val s = T.playState()
        T.give(s, 0, WOOD, 1)
        val ok = Action.offerTrade(intArrayOf(1, 0, 0, 0, 0), intArrayOf(0, 1, 0, 0, 0))
        assertFalse(Rules.isLegal(s, ok), "not before rolling")
        T.roll(s, 8)
        assertTrue(Rules.isLegal(s, ok))
        assertFalse(Rules.isLegal(s, Action.offerTrade(intArrayOf(1, 0, 0, 0, 0), IntArray(5))), "no gifts")
        assertFalse(Rules.isLegal(s, Action.offerTrade(IntArray(5), intArrayOf(1, 0, 0, 0, 0))), "no begging")
        assertFalse(Rules.isLegal(s, Action.offerTrade(intArrayOf(2, 0, 0, 0, 0), intArrayOf(0, 1, 0, 0, 0))), "not owned")
        assertFalse(Rules.isLegal(s, Action.offerTrade(intArrayOf(1, 0, 0, 0, 0), intArrayOf(1, 1, 0, 0, 0))), "same card")
    }
}
