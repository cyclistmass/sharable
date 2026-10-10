package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.GameConfig
import wgbg.engine.Rules
import wgbg.engine.Track
import kotlin.math.sqrt

class BuildingsTest {
    @Test
    fun `every board edge gets the road sprite that matches its direction`() {
        val topo = Rules.newGame(GameConfig(), 1).board.topology
        val seen = IntArray(3)
        for (e in 0 until topo.edgeCount) {
            val (v1, v2) = topo.edgeVertices[e].let { it[0] to it[1] }
            val dx = (topo.vertexPos[v2].x - topo.vertexPos[v1].x) * sqrt(3f) / 2f
            val dy = (topo.vertexPos[v2].y - topo.vertexPos[v1].y) / 2f
            val k = BuildingSprites.roadKind(dx, dy)
            seen[k]++
            // Either end first gives the same sprite.
            assertEquals(k, BuildingSprites.roadKind(-dx, -dy))
        }
        assertTrue(seen.all { it > 10 }, "all three edge directions occur: ${seen.toList()}")
        assertEquals(0, BuildingSprites.roadKind(0f, 1f))
        assertEquals(1, BuildingSprites.roadKind(-0.866f, 0.5f))  // rising to the right on screen
        assertEquals(2, BuildingSprites.roadKind(0.866f, 0.5f))   // falling to the right
    }

    @Test
    fun `every sprite set has 8 tilt steps, distinct per piece, and the themes differ only where they should`() {
        val all = listOf(false, true).flatMap { p ->
            listOf(BuildingArt.homestead(p), BuildingArt.roadV(p), BuildingArt.roadP30(p), BuildingArt.roadM30(p), BuildingArt.town(p), BuildingArt.ramparts(p)) +
                BuildingArt.capitals(p)
        }
        for (piece in all) {
            assertEquals(8, piece.body.size); assertEquals(8, piece.trim.size)
            assertEquals(16, (piece.body + piece.trim).toSet().size)
        }
        assertNotEquals(BuildingArt.town(false).body, BuildingArt.town(true).body)
        assertNotEquals(BuildingArt.ramparts(false).body, BuildingArt.ramparts(true).body)
        // The Pirate harbour set has its own homestead and boardwalk roads.
        assertNotEquals(BuildingArt.homestead(false).body, BuildingArt.homestead(true).body)
        assertNotEquals(BuildingArt.roadV(false).body, BuildingArt.roadV(true).body)
        assertEquals(3, BuildingArt.capitals(true).map { it.body[0] }.toSet().size)
    }

    @Test
    fun `each track gets its own Capital - Guilds for trade, Crown for politics, Academy for science`() {
        assertEquals(0, BuildingSprites.capitalIndex(Track.TRADE))
        assertEquals(2, BuildingSprites.capitalIndex(Track.POLITICS))
        assertEquals(1, BuildingSprites.capitalIndex(Track.SCIENCE))
        assertEquals(-1, BuildingSprites.capitalIndex(7))
    }

    @Test
    fun `supplies face their owner`() {
        assertEquals(0f, faceOwnerDegrees(Supply.seatAngle(0, 0, 4)), 1e-3f)
        assertEquals(180f, faceOwnerDegrees(Supply.seatAngle(2, 0, 4)), 1e-3f)
    }
}
