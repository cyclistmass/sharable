package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Alchemist is played before rolling, fixes both production dice, and the player's turn goes on (Mauro, 2026-10-12). */
class AlchemistTest {
    @Test
    fun `alchemist fixes the dice and the turn goes on`() {
        for (seed in 0 until 200) {
            val s = Rules.newGame(CK.config(), seed.toLong()).also { it.phase = Phase.PLAY_TURN; it.setupStep = 2 * it.numPlayers; it.currentPlayer = 0; it.actingPlayer = 0 }
            CK.give(s, 0, Resource.WOOD, 4) // enough for a 4:1 bank trade
            CK.giveProgress(s, 0, Progress.ALCHEMIST)
            assertTrue(Action.playProgress(Progress.ALCHEMIST, 2, 4) in Rules.legalActions(s), "seed $seed: alchemist legal before rolling")
            Rules.applyChecked(s, Action.playProgress(Progress.ALCHEMIST, 2, 4))
            assertEquals(6, s.lastRoll, "seed $seed")
            assertTrue(s.hasRolled, "seed $seed")
            // Answer any prompts other players get (a barbarian attack); the turn must come back to player 0.
            var guard = 0
            while (s.phase != Phase.PLAY_TURN && s.phase != Phase.GAME_OVER && guard++ < 50) Rules.applyChecked(s, Rules.legalActions(s).first())
            if (s.phase == Phase.GAME_OVER) continue
            assertEquals(0, s.currentPlayer, "seed $seed: still my turn")
            assertEquals(0, s.actingPlayer, "seed $seed: I act")
            val legal = Rules.legalActions(s)
            assertFalse(legal.any { it.type == ActionType.ROLL }, "seed $seed: no second roll")
            assertTrue(Action.END_TURN in legal, "seed $seed: I end my own turn")
            assertTrue(legal.size > 1, "seed $seed: I can still do things")
            CK.checkInvariants(s)
        }
    }
}
