package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologyTest {
    private val board = Topology.hexagon(2)

    @Test
    fun `base board has 19 hexes, 54 intersections, 72 paths`() {
        assertEquals(19, board.hexes.size)
        assertEquals(54, board.vertexCount)
        assertEquals(72, board.edgeCount)
    }

    @Test
    fun `every intersection touches 1 to 3 hexes and 2 to 3 paths`() {
        for (v in 0 until board.vertexCount) {
            assertTrue(board.vertexHexes[v].size in 1..3)
            assertTrue(board.vertexEdges[v].size in 2..3)
            assertEquals(board.vertexEdges[v].size, board.vertexNeighbors[v].size)
        }
    }

    @Test
    fun `coastline has 30 intersections`() {
        // 54 total minus the 24 interior intersections that touch 3 hexes.
        assertEquals(30, (0 until board.vertexCount).count { board.vertexHexes[it].size < 3 })
    }

    @Test
    fun `larger boards work too`() {
        // Topology must not be hardcoded to the base board (expansions, Seafarers).
        assertEquals(37, Topology.hexagon(3).hexes.size)
    }
}
