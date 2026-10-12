package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import wgbg.engine.*

/** Independent reference results for the distance, connectivity and longest-road rules used by board taps. */
class LiveBoardReferenceTest {
    @Test fun `a six road loop cut at one corner still has a six road trail`() {
        val s = Rules.newGame(GameConfig(), 1)
        val nodes = CatanatronMapping.nodeIds(s.board.topology)
        val edges = CatanatronMapping.edges(s.board.topology)
        // Fixture 93: Catanatron 3.2.1's helper returns 5 by refusing the last edge into node 40.
        // Both endpoints of this open trail touch that blocked corner; no road passes through it.
        for (edge in listOf(18 to 40, 17 to 18, 17 to 39, 39 to 41, 41 to 42, 40 to 42)) s.edgeOwner[edges.indexOf(edge)] = 0
        s.vertexOwner[nodes.indexOf(40)] = 1; s.vertexBuilding[nodes.indexOf(40)] = Piece.SETTLEMENT
        assertEquals(6, Rules.longestRoad(s, 0))
    }

    @Test fun `core board queries agree with 200 Catanatron fixtures`() {
        val rows = javaClass.getResourceAsStream("/filament-reference.tsv")!!.bufferedReader().readLines().filter { !it.startsWith("#") }
        assertEquals(200, rows.size)
        for ((index, row) in rows.withIndex()) {
            val columns = row.split('|')
            val s = Rules.newGame(GameConfig(), 1)
            val nodes = CatanatronMapping.nodeIds(s.board.topology)
            val edges = CatanatronMapping.edges(s.board.topology)
            fun pairs(text: String): List<Pair<Int, Int>> = if (text.isEmpty()) emptyList() else text.split(',').map {
                val (a, b) = it.split(':'); a.toInt() to b.toInt()
            }
            fun ids(text: String) = if (text.isEmpty()) emptySet() else text.split(',').map { it.toInt() }.toSet()
            for (p in 0..1) for (edge in pairs(columns[p])) s.edgeOwner[edges.indexOf(edge)] = p
            for ((node, owner) in pairs(columns[2])) {
                val v = nodes.indexOf(node); s.vertexOwner[v] = owner; s.vertexBuilding[v] = Piece.SETTLEMENT
            }
            val unblocked = s.copy().apply { vertexOwner.fill(-1); vertexBuilding.fill(0) }
            assertEquals(columns[3].toInt(), Rules.longestRoad(unblocked, 0), "fixture $index unblocked longest road")
            assertEquals(ids(columns[4]), nodes.indices.filter { Rules.isVertexFree(s, it) }.map { nodes[it] }.toSet(), "fixture $index distance")
            assertEquals(ids(columns[5]), nodes.indices.filter { Rules.canBuildSettlementAt(s, 0, it) }.map { nodes[it] }.toSet(), "fixture $index connectivity")
            assertEquals(pairs(columns[6]).toSet(), edges.indices.filter { Rules.canBuildRoadAt(s, 0, it) }.map { edges[it] }.toSet(), "fixture $index expansion")
        }
    }
}
