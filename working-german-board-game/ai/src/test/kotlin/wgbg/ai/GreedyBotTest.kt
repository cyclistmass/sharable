package wgbg.ai

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.GameConfig
import wgbg.engine.Rules

class GreedyBotTest {
    @Test
    fun `greedy bot beats random bots and finishes games quickly`() {
        var wins = 0
        var finished = 0
        val games = 200
        for (g in 0 until games) {
            val state = Rules.newGame(GameConfig(numPlayers = 4), seed = g.toLong())
            val seat = g % 4
            val bots = List(4) { if (it == seat) GreedyBot(g * 7L) else RandomBot(g * 13L + it) }
            val winner = playOut(state, bots)
            if (winner == seat) wins++
            if (winner >= 0) finished++
        }
        println("greedy win rate vs 3 random: ${100 * wins / games}% ($finished/$games finished)")
        assertTrue(wins > games / 2, "greedy should win most games, won $wins/$games")
    }
}
