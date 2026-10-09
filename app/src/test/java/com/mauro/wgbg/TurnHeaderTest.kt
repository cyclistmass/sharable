package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import wgbg.ai.GreedyBot
import wgbg.engine.ActionBuffer
import wgbg.engine.ActionType
import wgbg.engine.GameConfig
import wgbg.engine.Phase
import wgbg.engine.Rules

class TurnHeaderTest {
    @Test
    fun `the turn panel names whoever's turn it is, even before they roll`() {
        val s = Rules.newGame(GameConfig(numPlayers = 4), 9)
        val tracker = TurnTracker(listOf("You", "Blue", "Orange", "White"))
        val bots = List(4) { GreedyBot(it.toLong()) }
        val buf = ActionBuffer()
        var checked = 0
        while (!s.isOver && s.turn < 30) {
            Rules.legalActions(s, buf)
            val who = s.actingPlayer
            val a = bots[who].choose(s, buf)
            tracker.apply(s, who, a) { Rules.apply(s, a) }
            if (s.phase == Phase.PLAY_TURN && !s.hasRolled) {
                assertEquals(s.currentPlayer, turnHeader(tracker, s)!!.player, "turn ${s.turn}")
                checked++
            }
        }
        assert(checked > 10)
    }
}
