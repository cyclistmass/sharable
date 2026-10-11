package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.ai.GreedyBot
import wgbg.engine.ActionBuffer
import wgbg.engine.GameConfig
import wgbg.engine.Rules

class TurnHistoryTest {
    @Test
    fun `the info box keeps finished turns newest first, every player's, at most HISTORY of them`() {
        val s = Rules.newGame(GameConfig(numPlayers = 3), 11)
        val tracker = TurnTracker(listOf("You", "Blue", "Orange"))
        val bots = List(3) { GreedyBot(it.toLong()) }
        val buf = ActionBuffer()
        while (!s.isOver && s.turn < 80) {
            Rules.legalActions(s, buf)
            val who = s.actingPlayer
            val a = bots[who].choose(s, buf)
            tracker.apply(s, who, a) { Rules.apply(s, a) }
        }
        val h = tracker.history
        assertEquals(TurnTracker.HISTORY, h.size, "capped")
        assertEquals(h.first(), tracker.last, "newest first")
        assertTrue(h.zipWithNext().all { (newer, older) -> newer.turn >= older.turn }, "in order")
        assertEquals(setOf(0, 1, 2), h.map { it.player }.toSet(), "everyone's turns, not just yours")
        assertTrue(h.all { it.rolled || it.events.isNotEmpty() }, "only turns that did something")
    }
}
