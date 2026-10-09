package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.ai.GreedyBot
import wgbg.engine.ActionBuffer
import wgbg.engine.Expansion
import wgbg.engine.GameConfig
import wgbg.engine.Rules

class AgentHooksTest {
    @Test
    fun `every legal move a driver reads back from WGBG-TURN parses to the same move`() {
        for (exp in Expansion.entries) {
            val s = Rules.newGame(GameConfig(expansion = exp, numPlayers = 3), 7)
            val bots = List(3) { GreedyBot(it.toLong()) }
            val buf = ActionBuffer()
            var checked = 0
            while (!s.isOver && s.turn < 25) {
                val legal = Rules.legalActions(s)
                val line = AgentHooks.line("local", s, legal, "x")
                val listed = line.substringAfter("legal=").split(",")
                assertEquals(legal.size, listed.size)
                for ((a, text) in legal.zip(listed)) { assertEquals(a, AgentHooks.parse(text), text); checked++ }
                Rules.legalActions(s, buf)
                val a = bots[s.actingPlayer].choose(s, buf)
                Rules.apply(s, a)
            }
            assertTrue(checked > 100, "$exp")
        }
    }

    @Test
    fun `garbage moves are refused`() {
        for (bad in listOf("", "ROLL", "NOPE:1", "ROLL:-1", "ROLL:x", "1:2:3:4", "BUILD_ROAD:99999999999999999999")) assertNull(AgentHooks.parse(bad), bad)
    }
}
