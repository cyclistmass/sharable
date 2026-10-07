package wgbg.engine

/**
 * A game record: config + seed + ordered actions. Wraps a [GameState] and
 * validates every action, so it is the entry point for UI and networking.
 * Search code should work on raw [GameState]s with [Rules] instead.
 */
class Game(val config: GameConfig, val seed: Long) {
    val state: GameState = Rules.newGame(config, seed)
    private var log = LongArray(256)
    var actionCount = 0
        private set

    val actions: List<Action> get() = List(actionCount) { Action(log[it]) }

    fun legalActions() = Rules.legalActions(state)

    fun apply(a: Action) {
        Rules.applyChecked(state, a)
        if (actionCount == log.size) log = log.copyOf(log.size * 2)
        log[actionCount++] = a.code
    }

    companion object {
        /** Rebuilds a game from its record; throws if any action is illegal. */
        fun replay(config: GameConfig, seed: Long, actions: List<Action>): Game =
            Game(config, seed).also { g -> actions.forEach(g::apply) }
    }
}
