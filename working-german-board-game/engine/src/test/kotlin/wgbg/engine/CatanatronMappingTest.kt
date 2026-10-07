package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CatanatronMappingTest {
    private val t = Topology.hexagon(2)

    @Test
    fun `ids are bijections onto Catanatron's ranges`() {
        assertEquals((0 until 19).toSet(), CatanatronMapping.tileIds(t).toSet())
        assertEquals((0 until 54).toSet(), CatanatronMapping.nodeIds(t).toSet())
        val edges = CatanatronMapping.edges(t)
        assertEquals(72, edges.toSet().size)
    }

    @Test
    fun `center tile is tile 0 with nodes 0 to 5 clockwise from north`() {
        val center = t.hexes.indexOf(Hex(0, 0))
        assertEquals(0, CatanatronMapping.tileIds(t)[center])
        val nodes = CatanatronMapping.nodeIds(t)
        assertEquals((0 until 6).toList(), t.hexVertices[center].map { nodes[it] })
        // Tile 1 is cube (1,-1,0), east of centre: it reuses nodes 1 (its NW) and 2 (its SW).
        val east = t.hexes.indexOf(Hex(1, 0))
        assertEquals(1, CatanatronMapping.tileIds(t)[east])
        val eastNodes = t.hexVertices[east].map { nodes[it] }
        assertEquals(1, eastNodes[5])
        assertEquals(2, eastNodes[4])
        assertEquals(listOf(6, 7, 8, 9), eastNodes.subList(0, 4))
    }

    /** Prints the full table used in CATANATRON_MAPPING.md. */
    @Test
    fun `print mapping table`() {
        val tiles = CatanatronMapping.tileIds(t)
        val nodes = CatanatronMapping.nodeIds(t)
        val edges = CatanatronMapping.edges(t)
        val sb = StringBuilder()
        sb.appendLine("hex: " + t.hexes.indices.joinToString { "$it=${tiles[it]}${CatanatronMapping.cubeOf(t.hexes[it])}" })
        sb.appendLine("vertex: " + nodes.withIndex().joinToString { "${it.index}=${it.value}" })
        sb.appendLine("edge: " + edges.withIndex().joinToString { "${it.index}=${it.value}" })
        println(sb)
    }
}
