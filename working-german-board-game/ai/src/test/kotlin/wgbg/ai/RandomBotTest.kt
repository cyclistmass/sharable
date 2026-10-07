package wgbg.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.GameConfig
import wgbg.engine.Rules

class RandomBotTest {
    @Test
    fun `random bots finish games reproducibly`() {
        fun run(seed: Long): Pair<Int, Long> {
            val s = Rules.newGame(GameConfig(numPlayers = 4), seed)
            val winner = playOut(s, List(4) { RandomBot(seed * 10 + it) })
            return winner to s.stateHash()
        }
        for (seed in 0L until 20) {
            val (winner, hash) = run(seed)
            assertTrue(winner in -1..3)
            assertEquals(hash, run(seed).second)
        }
    }
}
