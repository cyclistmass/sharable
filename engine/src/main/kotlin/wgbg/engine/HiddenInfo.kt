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

    /** Cities & Knights information visible to the viewer, null in base games. */
    val ck: CkView? = s.ck?.let { CkView(viewer, s, it) }
}

/** What a player may see of the C&K state: all board pieces and counts, but only their own cards. */
class CkView(viewer: Int, s: GameState, ck: CkState) {
    private val n = s.numPlayers
    val hexNumber = ck.hexNumber.copyOf()
    val commodityBank = ck.commodityBank.copyOf()
    val trackLevel = ck.trackLevel.copyOf()
    val metroOwner = ck.metroOwner.copyOf()
    val metroVertex = ck.metroVertex.copyOf()
    val wall = ck.wall.copyOf()
    val knightOwner = ck.knightOwner.copyOf()
    val knightLevel = ck.knightLevel.copyOf()
    val knightFlags = ck.knightFlags.copyOf()
    val progressVp = ck.progressVp.copyOf()
    val defenderVp = ck.defenderVp.copyOf()
    val deckSize = ck.deckSize.copyOf()
    val barbarianPos = ck.barbarianPos
    val barbarianAttacks = ck.barbarianAttacks
    val lastRed = ck.lastRed
    val lastEvent = ck.lastEvent
    val merchantHex = ck.merchantHex
    val merchantOwner = ck.merchantOwner
    val commodityCount = IntArray(n) { ck.commodityCount(it) }
    val progressCount = IntArray(n) { ck.progressCount(it) }

    /** The viewer's own commodity cards (paper, cloth, coin). */
    val commodities = IntArray(Commodity.COUNT) { ck.commodity(viewer, it) }
    /** The viewer's own progress cards, per [Progress] id. */
    val progressHand = IntArray(Progress.COUNT) { ck.progressHand[viewer * Progress.COUNT + it] }

    /**
     * While the viewer resolves a Spy, the target's progress cards; while resolving a
     * Master Merchant, the target's resources then commodities. Null otherwise.
     */
    val peek: IntArray? = when {
        viewer != s.actingPlayer || ck.pendingTarget < 0 -> null
        s.phase == Phase.SPY -> IntArray(Progress.COUNT) { ck.progressHand[ck.pendingTarget * Progress.COUNT + it] }
        s.phase == Phase.MASTER_MERCHANT -> IntArray(CK_CARD_TYPES) { s.card(ck.pendingTarget, it) }
        else -> null
    }
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
        if (d.ck != null) shuffleProgress(d, viewer, rng)
        return d
    }

    /**
     * C&K: per colour, opponents' progress cards and the undrawn deck are pooled,
     * shuffled and dealt back with the same counts (opponents keep their per-colour
     * hand sizes; the deck keeps its size, in a fresh random order).
     */
    private fun shuffleProgress(d: GameState, viewer: Int, rng: Rng) {
        val ck = d.ck!!
        val n = d.numPlayers
        val per = Progress.PER_DECK
        for (t in 0 until Track.COUNT) {
            val pool = IntArray(Progress.COUNT)
            val held = IntArray(n)
            for (i in 0 until ck.deckSize[t]) pool[ck.deck[t * per + (ck.deckHead[t] + i) % per]]++
            for (q in 0 until n) {
                if (q == viewer) continue
                for (c in 0 until Progress.COUNT) {
                    if (Progress.track(c) != t) continue
                    val k = ck.progressHand[q * Progress.COUNT + c]
                    held[q] += k
                    pool[c] += k
                    ck.progressHand[q * Progress.COUNT + c] = 0
                }
            }
            for (q in 0 until n) repeat(held[q]) { ck.progressHand[q * Progress.COUNT + draw(pool, rng)]++ }
            val size = ck.deckSize[t]
            ck.deckHead[t] = 0
            for (i in 0 until size) ck.deck[t * per + i] = draw(pool, rng)
        }
    }

    private fun draw(pool: IntArray, rng: Rng): Int {
        var k = rng.nextInt(pool.sum())
        var c = 0
        while (k >= pool[c]) k -= pool[c++]
        pool[c]--
        return c
    }
}
