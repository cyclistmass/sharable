package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetupTest {
    @Test
    fun `snake draft order, distance rule, roads next to new settlement`() {
        val s = Rules.newGame(GameConfig(numPlayers = 4), 3)
        val order = mutableListOf<Int>()
        val rng = Rng(5)
        while (s.phase != Phase.PLAY_TURN) {
            assertEquals(Phase.BUILD_INITIAL_SETTLEMENT, s.phase)
            order += s.actingPlayer
            val settlements = T.legal(s)
            assertTrue(settlements.all { it.type == ActionType.BUILD_SETTLEMENT })
            for (a in settlements) assertTrue(Rules.isVertexFree(s, a.payload.toInt()))
            val v = settlements[rng.nextInt(settlements.size)].payload.toInt()
            Rules.applyChecked(s, Action.buildSettlement(v))
            // Neighbours are no longer offered.
            for (n in s.board.topology.vertexNeighbors[v]) assertFalse(Action.buildSettlement(n) in T.legal(s))
            assertEquals(Phase.BUILD_INITIAL_ROAD, s.phase)
            val roads = T.legal(s)
            assertEquals(s.board.topology.vertexEdges[v].count { s.edgeOwner[it] < 0 }, roads.size)
            for (a in roads) assertTrue(v in s.board.topology.edgeVertices[a.payload.toInt()])
            Rules.applyChecked(s, roads[0])
        }
        assertEquals(listOf(0, 1, 2, 3, 3, 2, 1, 0), order)
        assertEquals(0, s.currentPlayer)
        assertEquals(0, s.actingPlayer)
        assertFalse(s.hasRolled)
        for (p in 0 until 4) {
            assertEquals(2, s.piecesOnBoard(p, Piece.SETTLEMENT))
            assertEquals(2, s.piecesOnBoard(p, Piece.ROAD))
            assertEquals(2, s.publicVictoryPoints(p))
        }
        T.checkInvariants(s)
    }

    @Test
    fun `only the second settlement yields starting resources`() {
        val s = Rules.newGame(GameConfig(numPlayers = 3, layout = BoardLayout.BEGINNER), 1)
        val b = s.board
        // A vertex touching three producing hexes (around Brick 6 / Sheep 4 / Wood 11... any interior one).
        val rich = (0 until b.vertexCount).filter { v ->
            b.topology.vertexHexes[v].size == 3 && b.topology.vertexHexes[v].none { b.hexResource[it] == Resource.NONE }
        }
        fun placeWithRoad(v: Int) {
            Rules.applyChecked(s, Action.buildSettlement(v))
            Rules.applyChecked(s, T.legal(s)[0])
        }
        val picks = mutableListOf<Int>()
        for (i in 0 until 6) {
            val v = rich.first { Rules.isVertexFree(s, it) }
            picks += v
            val p = s.actingPlayer
            val before = s.handSize(p)
            placeWithRoad(v)
            val gained = s.handSize(p) - before
            assertEquals(if (i < 3) 0 else 3, gained, "placement $i")
        }
        // Second settlement of player 2 (placement 3): hand equals the three adjacent terrains.
        val expected = IntArray(CARD_TYPES)
        for (h in b.topology.vertexHexes[picks[3]]) expected[b.hexResource[h]]++
        assertEquals(expected.toList(), (0 until CARD_TYPES).map { s.hand(2, it) })
        T.checkInvariants(s)
    }
}
