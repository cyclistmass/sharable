package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeterminismTest {
    /** Plays a random game, returning the actions and the state hash after each one. */
    private fun play(config: GameConfig, seed: Long, botSeed: Long): Pair<List<Action>, List<Long>> {
        val s = Rules.newGame(config, seed)
        val bot = Rng(botSeed)
        val buf = ActionBuffer()
        val actions = mutableListOf<Action>()
        val hashes = mutableListOf<Long>()
        while (!s.isOver) {
            Rules.legalActions(s, buf)
            val a = buf[bot.nextInt(buf.size)]
            Rules.apply(s, a)
            actions += a
            hashes += s.stateHash()
        }
        return actions to hashes
    }

    @Test
    fun `same seed and actions give identical states`() {
        val config = GameConfig(generateTradeOffers = true)
        val (actions, hashes) = play(config, 77, 1)
        val (actions2, hashes2) = play(config, 77, 1)
        assertEquals(actions, actions2)
        assertEquals(hashes, hashes2)

        val replay = Game.replay(config, 77, actions)
        assertEquals(hashes.last(), replay.state.stateHash())
        assertEquals(actions, replay.actions)
    }

    @Test
    fun `different seeds diverge`() {
        val a = Rules.newGame(GameConfig(), 1)
        val b = Rules.newGame(GameConfig(), 2)
        assertNotEquals(a.stateHash(), b.stateHash())
    }

    @Test
    fun `pinned game hash guards against accidental rule or RNG changes`() {
        // If this fails after an intentional change, saved games/replays are invalidated: update the value.
        val (_, hashes) = play(GameConfig(), 2024, 7)
        assertEquals(1172, hashes.size)
        assertEquals(-2966776835976735846L, hashes.last())
    }

    @Test
    fun `copy is deep and independent`() {
        val s = Rules.newGame(GameConfig(), 5)
        T.randomSetup(s, Rng(3))
        val c = s.copy()
        assertEquals(s.stateHash(), c.stateHash())
        Rules.apply(c, Action.ROLL)
        assertNotEquals(s.stateHash(), c.stateHash())
        // Original still rolls the same dice as the copy did.
        Rules.apply(s, Action.ROLL)
        assertEquals(c.lastRoll, s.lastRoll)
        assertEquals(c.stateHash(), s.stateHash())
    }

    @Test
    fun `game rejects illegal actions`() {
        val g = Game(GameConfig(), 1)
        assertThrows(IllegalArgumentException::class.java) { g.apply(Action.ROLL) }
        assertThrows(IllegalArgumentException::class.java) { g.apply(Action.buildRoad(0)) }
        g.apply(g.legalActions().first())
        assertEquals(1, g.actionCount)
        assertTrue(g.state.phase == Phase.BUILD_INITIAL_ROAD)
    }
}
