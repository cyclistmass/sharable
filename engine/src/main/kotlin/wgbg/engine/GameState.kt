package wgbg.engine

/**
 * Turn phases, modelled on Catanatron's ActionPrompt. Expansions add entries
 * (e.g. C&K's progress-card or barbarian prompts).
 */
enum class Phase {
    BUILD_INITIAL_SETTLEMENT,
    BUILD_INITIAL_ROAD,
    PLAY_TURN,
    DISCARD,
    MOVE_ROBBER,
    DECIDE_TRADE,
    DECIDE_ACCEPTEES,
    GAME_OVER,
}

/**
 * Complete mutable game state in flat primitive arrays. [config] and [board]
 * are immutable and shared; [copy] deep-clones everything else.
 *
 * Per-player arrays are indexed `player * stride + kind`.
 */
class GameState internal constructor(
    val config: GameConfig,
    val board: Board,
    val rng: Rng,
    /** Owner of the building at each vertex, -1 if empty. */
    val vertexOwner: IntArray,
    /** 0 = empty, [Piece.SETTLEMENT] or [Piece.CITY]. */
    val vertexBuilding: IntArray,
    val edgeOwner: IntArray,
    val bank: IntArray,
    /** Resource cards, `player * CARD_TYPES + resource`. */
    val hands: IntArray,
    /** Undrawn development cards per type. */
    val devDeck: IntArray,
    /** Unplayed dev cards (including VP cards), `player * DevCard.COUNT + card`. */
    val devHand: IntArray,
    /** Dev cards bought this turn (not yet playable), same layout as [devHand]. */
    val devNew: IntArray,
    val devPlayed: IntArray,
    /** Pieces still in supply, `player * Piece.COUNT + piece`. */
    val piecesLeft: IntArray,
    /** OR of [Port.bit] for every port a player has a building on. */
    val portMask: IntArray,
    /** Current longest-road length per player. */
    val roadLength: IntArray,
    /** Cards each player still has to discard after a 7. */
    val discardOwed: IntArray,
    /** Current domestic trade: offered cards then requested cards (2 * CARD_TYPES). */
    val tradeOffer: IntArray,
) {
    val numPlayers get() = config.numPlayers

    var phase = Phase.BUILD_INITIAL_SETTLEMENT
    /** Player whose turn it is. */
    var currentPlayer = 0
    /** Player who must choose the next action (differs during discards and trade responses). */
    var actingPlayer = 0
    /** Completed regular turns (setup not counted). */
    var turn = 0
    /** Index into the setup snake order (0 until 2 * numPlayers). */
    var setupStep = 0
    var lastSettlement = -1
    var robberHex = -1
    var hasRolled = false
    var devPlayedThisTurn = false
    /** Remaining free roads from a Road Building card. */
    var freeRoads = 0
    /** Sum of the last dice roll (0 before the first roll). */
    var lastRoll = 0
    var longestRoadOwner = -1
    var largestArmyOwner = -1
    /** Bitmask of players that accepted the current trade offer. */
    var tradeAccepted = 0
    /** Winning player, or -1 (also when the game ended by the turn limit). */
    var winner = -1

    val isOver get() = phase == Phase.GAME_OVER

    fun hand(p: Int, r: Int) = hands[p * CARD_TYPES + r]

    fun handSize(p: Int): Int {
        var n = 0
        val base = p * CARD_TYPES
        for (r in 0 until CARD_TYPES) n += hands[base + r]
        return n
    }

    fun devCount(p: Int): Int {
        var n = 0
        val base = p * DevCard.COUNT
        for (d in 0 until DevCard.COUNT) n += devHand[base + d]
        return n
    }

    fun piecesOnBoard(p: Int, piece: Int) = Piece.LIMITS[piece] - piecesLeft[p * Piece.COUNT + piece]

    fun knightsPlayed(p: Int) = devPlayed[p * DevCard.COUNT + DevCard.KNIGHT]

    /** Points everyone can see (no hidden VP cards). */
    fun publicVictoryPoints(p: Int): Int =
        piecesOnBoard(p, Piece.SETTLEMENT) + 2 * piecesOnBoard(p, Piece.CITY) +
            (if (longestRoadOwner == p) 2 else 0) + (if (largestArmyOwner == p) 2 else 0)

    /** Actual points, including hidden VP cards. */
    fun victoryPoints(p: Int) = publicVictoryPoints(p) + devHand[p * DevCard.COUNT + DevCard.VICTORY_POINT]

    fun copy(): GameState {
        val s = GameState(
            config, board, rng.copy(),
            vertexOwner.copyOf(), vertexBuilding.copyOf(), edgeOwner.copyOf(), bank.copyOf(),
            hands.copyOf(), devDeck.copyOf(), devHand.copyOf(), devNew.copyOf(), devPlayed.copyOf(),
            piecesLeft.copyOf(), portMask.copyOf(), roadLength.copyOf(), discardOwed.copyOf(),
            tradeOffer.copyOf(),
        )
        s.phase = phase
        s.currentPlayer = currentPlayer
        s.actingPlayer = actingPlayer
        s.turn = turn
        s.setupStep = setupStep
        s.lastSettlement = lastSettlement
        s.robberHex = robberHex
        s.hasRolled = hasRolled
        s.devPlayedThisTurn = devPlayedThisTurn
        s.freeRoads = freeRoads
        s.lastRoll = lastRoll
        s.longestRoadOwner = longestRoadOwner
        s.largestArmyOwner = largestArmyOwner
        s.tradeAccepted = tradeAccepted
        s.winner = winner
        return s
    }

    /** 64-bit FNV-1a hash of the whole state (including board and RNG), for determinism checks. */
    fun stateHash(): Long {
        var h = -0x340d631b7bdddcdbL
        fun mix(x: Int) {
            h = (h xor (x.toLong() and 0xffffffffL)) * 0x100000001b3L
        }
        fun mix(a: IntArray) {
            mix(a.size)
            for (x in a) mix(x)
        }
        mix(vertexOwner); mix(vertexBuilding); mix(edgeOwner); mix(bank); mix(hands)
        mix(devDeck); mix(devHand); mix(devNew); mix(devPlayed); mix(piecesLeft)
        mix(portMask); mix(roadLength); mix(discardOwed); mix(tradeOffer)
        mix(board.hexResource); mix(board.hexNumber)
        for (p in board.ports) mix(p.type)
        mix(phase.ordinal); mix(currentPlayer); mix(actingPlayer); mix(turn); mix(setupStep)
        mix(lastSettlement); mix(robberHex); mix(if (hasRolled) 1 else 0)
        mix(if (devPlayedThisTurn) 1 else 0); mix(freeRoads); mix(lastRoll)
        mix(longestRoadOwner); mix(largestArmyOwner); mix(tradeAccepted); mix(winner)
        mix((rng.seed ushr 32).toInt()); mix(rng.seed.toInt())
        return h
    }

    companion object {
        /** Fresh state at the start of setup. Prefer [Rules.newGame]. */
        fun initial(config: GameConfig, board: Board, rng: Rng): GameState {
            val n = config.numPlayers
            val s = GameState(
                config, board, rng,
                vertexOwner = IntArray(board.vertexCount) { -1 },
                vertexBuilding = IntArray(board.vertexCount),
                edgeOwner = IntArray(board.edgeCount) { -1 },
                bank = IntArray(CARD_TYPES) { BANK_PER_RESOURCE },
                hands = IntArray(n * CARD_TYPES),
                devDeck = DevCard.DECK.copyOf(),
                devHand = IntArray(n * DevCard.COUNT),
                devNew = IntArray(n * DevCard.COUNT),
                devPlayed = IntArray(n * DevCard.COUNT),
                piecesLeft = IntArray(n * Piece.COUNT) { Piece.LIMITS[it % Piece.COUNT] },
                portMask = IntArray(n),
                roadLength = IntArray(n),
                discardOwed = IntArray(n),
                tradeOffer = IntArray(2 * CARD_TYPES),
            )
            s.robberHex = board.desertHex
            return s
        }
    }
}
