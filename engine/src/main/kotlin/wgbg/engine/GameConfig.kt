package wgbg.engine

/**
 * RANDOM: terrain, numbers and ports shuffled (Catanatron's distribution). BEGINNER: the fixed rulebook board.
 * SPIRAL: the official variable setup: shuffled terrain, number tokens A-R laid in a spiral from an outer corner.
 */
enum class BoardLayout { RANDOM, BEGINNER, SPIRAL }

/** Rule set. Cities & Knights is described in CITIES_AND_KNIGHTS.md. */
enum class Expansion { BASE, CITIES_AND_KNIGHTS }

/** Immutable game settings. Together with a seed and the action list it fully determines a game. */
data class GameConfig(
    val expansion: Expansion = Expansion.BASE,
    val numPlayers: Int = 4,
    /** Defaults to 10 (base game) or 13 (Cities & Knights). */
    val vpToWin: Int = if (expansion == Expansion.CITIES_AND_KNIGHTS) 13 else 10,
    val layout: BoardLayout = BoardLayout.RANDOM,
    /** RANDOM layout only: reshuffle numbers until no 6/8 are adjacent. */
    val avoidAdjacentRedNumbers: Boolean = true,
    /** Players holding more cards than this must discard half on a 7 (C&K: +2 per city wall). */
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
    val citiesAndKnights get() = expansion == Expansion.CITIES_AND_KNIGHTS

    init {
        require(numPlayers in 2..4) { "base game supports 2-4 players" }
        require(!citiesAndKnights || numPlayers in 3..4) { "Age of Chivalry supports 3-4 players" }
    }
}
