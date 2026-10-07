package wgbg.engine

enum class BoardLayout { RANDOM, BEGINNER }

/** Immutable game settings. Together with a seed and the action list it fully determines a game. */
data class GameConfig(
    val numPlayers: Int = 4,
    val vpToWin: Int = 10,
    val layout: BoardLayout = BoardLayout.RANDOM,
    /** RANDOM layout only: reshuffle numbers until no 6/8 are adjacent. */
    val avoidAdjacentRedNumbers: Boolean = true,
    /** Players holding more cards than this must discard half on a 7. */
    val discardLimit: Int = 7,
    /** Draw safeguard for AI simulations: the game ends without a winner after this many turns. */
    val maxTurns: Int = 1000,
    /**
     * Catanatron-style discard: a single DISCARD action drops half the hand at random.
     * When false, players discard one chosen card per DISCARD action.
     */
    val randomDiscard: Boolean = false,
    /**
     * Include simple 1-for-1 OFFER_TRADE actions in [Rules.legalActions]. Off by default
     * (like Catanatron) because the full offer space is huge; any valid offer is
     * still accepted by [Rules.isLegal].
     */
    val generateTradeOffers: Boolean = false,
) {
    init {
        require(numPlayers in 2..4) { "base game supports 2-4 players" }
    }
}
