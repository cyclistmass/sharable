package wgbg.ai

import wgbg.engine.Action
import wgbg.engine.ActionBuffer
import wgbg.engine.GameState
import wgbg.engine.Rng
import wgbg.engine.Rules

/**
 * A player controlled by code. Phase 3 adds a Kotlin port of Catanatron's
 * value function + AlphaBeta search, later guided by an ONNX network.
 */
interface Bot {
    val name: String

    /**
     * Picks one of [legal] (non-empty, generated for [state]'s acting player).
     * Implementations must not mutate [state]; copy it for search.
     */
    fun choose(state: GameState, legal: ActionBuffer): Action
}

/** Uniformly random legal action; seeded so games stay reproducible. */
class RandomBot(seed: Long = 0, override val name: String = "Random") : Bot {
    private val rng = Rng(seed)

    override fun choose(state: GameState, legal: ActionBuffer): Action = legal[rng.nextInt(legal.size)]
}

/** Plays a game to the end with one bot per seat; returns the winner (-1 for a draw). */
fun playOut(state: GameState, bots: List<Bot>): Int {
    require(bots.size == state.numPlayers)
    val buf = ActionBuffer()
    while (!state.isOver) {
        Rules.legalActions(state, buf)
        Rules.apply(state, bots[state.actingPlayer].choose(state, buf))
    }
    return state.winner
}
