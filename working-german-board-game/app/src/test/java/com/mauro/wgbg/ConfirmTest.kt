package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import wgbg.ai.GreedyBot
import wgbg.engine.Action
import wgbg.engine.ActionBuffer
import wgbg.engine.GameConfig
import wgbg.engine.Phase
import wgbg.engine.Rules

/** Board taps ask first; the bandit warns before blocking your own land. */
class ConfirmTest {
    private fun afterSetup() = Rules.newGame(GameConfig(numPlayers = 3), 11).also { s ->
        val bots = List(3) { GreedyBot(it.toLong()) }
        val buf = ActionBuffer()
        while (s.phase == Phase.BUILD_INITIAL_SETTLEMENT || s.phase == Phase.BUILD_INITIAL_ROAD) {
            Rules.legalActions(s, buf)
            Rules.apply(s, bots[s.actingPlayer].choose(s, buf))
        }
    }

    @Test
    fun `the bandit on your own tile warns, elsewhere it does not`() {
        val s = afterSetup()
        val topo = s.board.topology
        val mine = (0 until s.board.hexCount).first { h -> topo.hexVertices[h].any { s.vertexOwner[it] == 0 } }
        val clear = (0 until s.board.hexCount).first { h -> topo.hexVertices[h].none { s.vertexOwner[it] == 0 } }
        assertNotNull(confirmWarning(s, 0, Action.moveRobber(mine, -1)))
        assertNull(confirmWarning(s, 0, Action.moveRobber(clear, -1)))
        assertNull(confirmWarning(s, 0, Action.buildRoad(0)), "only the bandit warns")
    }

    @Test
    fun `labels name the victim and their cards`() {
        val s = afterSetup()
        val names = listOf("You", "Blue", "Orange")
        assertEquals("Move it (nobody to rob)", confirmLabel(s, Action.moveRobber(3, -1), names))
        assertEquals("Rob Blue (${s.cardCount(1)} cards)", confirmLabel(s, Action.moveRobber(3, 1), names))
        assertEquals("Confirm", confirmLabel(s, Action.buildRoad(0), names))
        assertEquals("Move the bandit here?", confirmTitle(s, Action.moveRobber(3, -1)))
    }
}
