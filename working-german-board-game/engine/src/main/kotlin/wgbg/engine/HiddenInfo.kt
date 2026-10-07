package wgbg.engine

/**
 * What one player is allowed to know: everything on the table, card counts of
 * opponents, and their own hand and development cards. Suitable for sending
 * over the network or rendering a player's screen.
 */
class PlayerView(val viewer: Int, s: GameState) {
    val config = s.config
    val board = s.board
    val phase = s.phase
    val currentPlayer = s.currentPlayer
    val actingPlayer = s.actingPlayer
    val turn = s.turn
    val lastRoll = s.lastRoll
    val robberHex = s.robberHex
    val hasRolled = s.hasRolled
    val freeRoads = s.freeRoads
    val winner = s.winner
    val longestRoadOwner = s.longestRoadOwner
    val largestArmyOwner = s.largestArmyOwner
    val vertexOwner = s.vertexOwner.copyOf()
    val vertexBuilding = s.vertexBuilding.copyOf()
    val edgeOwner = s.edgeOwner.copyOf()
    val bank = s.bank.copyOf()
    val devDeckSize = s.devDeck.sum()
    val devPlayed = s.devPlayed.copyOf()
    val roadLength = s.roadLength.copyOf()
    val discardOwed = s.discardOwed.copyOf()
    val tradeOffer = s.tradeOffer.copyOf()
    val tradeAccepted = s.tradeAccepted
    val handSize = IntArray(s.numPlayers) { s.handSize(it) }
    val devCount = IntArray(s.numPlayers) { s.devCount(it) }
    val publicVictoryPoints = IntArray(s.numPlayers) { s.publicVictoryPoints(it) }

    /** The viewer's own resource cards. */
    val hand = IntArray(CARD_TYPES) { s.hand(viewer, it) }
    /** The viewer's own unplayed dev cards and which of them were bought this turn. */
    val devHand = IntArray(DevCard.COUNT) { s.devHand[viewer * DevCard.COUNT + it] }
    val devNew = IntArray(DevCard.COUNT) { s.devNew[viewer * DevCard.COUNT + it] }
    val victoryPoints = s.victoryPoints(viewer)
}

object HiddenInfo {
    fun view(s: GameState, viewer: Int) = PlayerView(viewer, s)

    /**
     * Determinization for search (e.g. ISMCTS): a copy of [s] in which every
     * card [viewer] cannot see is resampled consistently with public counts,
     * and the RNG (future dice, draws, steals) is reseeded with [seed].
     *
     * Opponents' unplayed dev cards and the deck order are shuffled together.
     * Opponents' resource hands are kept as-is: they are mostly derivable by
     * card counting; TODO track unknown stolen cards and resample those too.
     */
    fun determinize(s: GameState, viewer: Int, seed: Long): GameState {
        val d = s.copy()
        d.rng.seed = seed
        val pool = d.devDeck.copyOf()
        for (q in 0 until d.numPlayers) {
            if (q == viewer) continue
            for (c in 0 until DevCard.COUNT) pool[c] += d.devHand[q * DevCard.COUNT + c]
        }
        val rng = Rng(seed xor 0x5DEECE66DL)
        for (q in 0 until d.numPlayers) {
            if (q == viewer) continue
            val base = q * DevCard.COUNT
            val total = (0 until DevCard.COUNT).sumOf { d.devHand[base + it] }
            var fresh = (0 until DevCard.COUNT).sumOf { d.devNew[base + it] }
            for (c in 0 until DevCard.COUNT) {
                d.devHand[base + c] = 0
                d.devNew[base + c] = 0
            }
            repeat(total) {
                val c = draw(pool, rng)
                d.devHand[base + c]++
                // The newest cards are a random subset of the hand; drawing them first is equivalent.
                if (fresh > 0) {
                    d.devNew[base + c]++
                    fresh--
                }
            }
        }
        pool.copyInto(d.devDeck)
        return d
    }

    private fun draw(pool: IntArray, rng: Rng): Int {
        var k = rng.nextInt(pool.sum())
        var c = 0
        while (k >= pool[c]) k -= pool[c++]
        pool[c]--
        return c
    }
}
