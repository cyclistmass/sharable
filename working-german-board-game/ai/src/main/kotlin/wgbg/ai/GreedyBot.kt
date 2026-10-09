package wgbg.ai

import wgbg.engine.Action
import wgbg.engine.ActionBuffer
import wgbg.engine.ActionType
import wgbg.engine.CARD_TYPES
import wgbg.engine.GameState
import wgbg.engine.Phase
import wgbg.engine.Piece
import wgbg.engine.Rules
import wgbg.engine.Resource
import wgbg.engine.Rng

/**
 * Simple rule-of-thumb bot so demo games look sensible: builds the best-yield
 * spot it can afford, trades with players and the bank for what it is missing,
 * accepts trades that help it (not from a player close to winning), robs the leader.
 * A stand-in until the Catanatron-style AlphaBeta bot lands in Phase 3.
 */
class GreedyBot(seed: Long = 0, override val name: String = "Greedy") : Bot {
    private val rng = Rng(seed)
    private var offerTurn = -1
    private var offersThisTurn = 0

    override fun choose(state: GameState, legal: ActionBuffer): Action {
        tradeOffer(state, legal)?.let { return it }
        tradeResponse(state, legal)?.let { return it }
        var best = legal[0]
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in 0 until legal.size) {
            val a = legal[i]
            val score = score(state, a) + rng.nextInt(1000) / 1e5 // tie-break
            if (score > bestScore) {
                bestScore = score
                best = a
            }
        }
        return best
    }

    private fun score(s: GameState, a: Action): Double {
        val me = s.actingPlayer
        return when (a.type) {
            ActionType.BUILD_CITY -> 1000.0 + vertexValue(s, a.a.toInt())
            ActionType.BUILD_SETTLEMENT -> 900.0 + vertexValue(s, a.a.toInt())
            ActionType.BUY_DEVELOPMENT_CARD -> 300.0
            ActionType.PLAY_KNIGHT_CARD -> if (robberHurtsMe(s, me)) 950.0 else 100.0
            ActionType.PLAY_ROAD_BUILDING, ActionType.PLAY_YEAR_OF_PLENTY -> 800.0
            ActionType.PLAY_MONOPOLY -> 700.0 + s.handSizeOthers(me, a.payload.toInt())
            ActionType.BUILD_ROAD -> roadScore(s, me, a.payload.toInt())
            ActionType.MARITIME_TRADE ->
                // Card indices here may be commodities (Age of Chivalry), so read them with card(), not hand().
                if (s.card(me, a.maritimeGive) >= 4 && s.card(me, a.maritimeGet) == 0) 200.0 else -1.0
            ActionType.MOVE_ROBBER -> robberScore(s, me, a.robberHex, a.robberVictim)
            ActionType.DISCARD -> if (a.discardResource < 0) 0.0 else s.card(me, a.discardResource).toDouble()
            ActionType.ROLL -> 50.0
            ActionType.ACCEPT_TRADE -> if (if (s.phase == Phase.DECIDE_COUNTER) counterHelpsMe(s, me) else offerHelpsMe(s, me)) 5.0 else -1.0
            ActionType.CONFIRM_TRADE -> 10.0 - s.publicVictoryPoints(a.payload.toInt()) // weakest partner first
            ActionType.OFFER_TRADE, ActionType.COUNTER_TRADE -> -10.0
            ActionType.END_TURN, ActionType.REJECT_TRADE, ActionType.CANCEL_TRADE -> 0.0
            else -> 1.0
        }
    }

    // ------------------------------------------------------------------ trading

    /** What the bot is saving for right now: town, homestead, Fortune card, or a road. */
    private fun target(s: GameState, me: Int): IntArray {
        val hasTowns = s.piecesOnBoard(me, Piece.SETTLEMENT) > 0 && s.piecesLeft[me * Piece.COUNT + Piece.CITY] > 0
        val canSettle = (0 until s.board.topology.vertexCount).any { v ->
            s.vertexOwner[v] < 0 && s.board.topology.vertexEdges[v].any { s.edgeOwner[it] == me } &&
                s.board.topology.vertexNeighbors[v].all { s.vertexOwner[it] < 0 }
        }
        return when {
            hasTowns -> COST_CITY
            canSettle -> COST_SETTLEMENT
            else -> COST_ROAD.takeIf { s.piecesLeft[me * Piece.COUNT + Piece.ROAD] > 0 && rng.nextInt(2) == 0 } ?: COST_DEV
        }
    }

    /** How much one more card of [r] is worth to [me]: high if missing for the target, low if hoarded. */
    private fun worth(s: GameState, me: Int, goal: IntArray, r: Int, extra: Int = 0): Double {
        val have = s.hand(me, r) + extra
        return when {
            have < goal[r] -> 3.0
            have >= goal[r] + 2 -> 0.4
            else -> 1.0
        }
    }

    /**
     * After rolling, if one card short of the target and holding a spare, offer 1 spare for it
     * (at most twice a turn, so a refused offer can't loop).
     */
    private fun tradeOffer(s: GameState, legal: ActionBuffer): Action? {
        if (s.phase != Phase.PLAY_TURN || !s.hasRolled) return null
        if (offerTurn != s.turn) { offerTurn = s.turn; offersThisTurn = 0 }
        if (offersThisTurn >= 2) return null
        // Only bother when nothing can be built right now.
        for (i in 0 until legal.size) {
            val t = legal[i].type
            if (t == ActionType.BUILD_CITY || t == ActionType.BUILD_SETTLEMENT || t == ActionType.BUY_DEVELOPMENT_CARD) return null
        }
        val me = s.actingPlayer
        val goal = target(s, me)
        val missing = (0 until CARD_TYPES).filter { s.hand(me, it) < goal[it] }
        if (missing.size != 1 || goal[missing[0]] - s.hand(me, missing[0]) != 1) return null
        val spare = (0 until CARD_TYPES).filter { it != missing[0] && s.hand(me, it) > goal[it] }
            .maxByOrNull { s.hand(me, it) - goal[it] } ?: return null
        // First offer is 1-for-1; if nobody bites, sweeten it to 2-for-1 when we can spare it.
        val sweeten = offersThisTurn == 1 && s.hand(me, spare) >= goal[spare] + 2
        val give = IntArray(CARD_TYPES).also { it[spare] = if (sweeten) 2 else 1 }
        val want = IntArray(CARD_TYPES).also { it[missing[0]] = 1 }
        val offer = Action.offerTrade(give, want)
        if (!Rules.isLegal(s, offer)) return null
        offersThisTurn++
        return offer
    }

    /**
     * Answering someone's offer: accept it if it helps; otherwise, now and then, counter by asking
     * for a card we're missing in place of what they offered. Null outside DECIDE_TRADE.
     */
    private fun tradeResponse(s: GameState, legal: ActionBuffer): Action? {
        if (s.phase != Phase.DECIDE_TRADE || s.ck != null) return null
        val me = s.actingPlayer
        if (Action.ACCEPT_TRADE in legal && offerHelpsMe(s, me)) return Action.ACCEPT_TRADE
        val o = s.currentPlayer
        if (s.publicVictoryPoints(o) < s.config.vpToWin - 2 && rng.nextInt(100) < 50) {
            val goal = target(s, me)
            val want = IntArray(CARD_TYPES) { s.tradeOffer[CARD_TYPES + it] } // what the offerer still gets
            val missing = (0 until CARD_TYPES).filter { s.hand(me, it) < goal[it] && want[it] == 0 && s.hand(o, it) > 0 }
            for (m in missing.shuffled(java.util.Random(rng.nextInt(1 shl 30).toLong()))) {
                val give = IntArray(CARD_TYPES).also { it[m] = 1 }
                val counter = Action.counterTrade(give, want)
                if (Rules.isLegal(s, counter)) return counter
            }
        }
        return Action.REJECT_TRADE
    }

    /** As the offerer: take a counter unless it costs us more than it brings (or helps a near-winner). */
    private fun counterHelpsMe(s: GameState, me: Int): Boolean {
        val c = s.counterOffer ?: return false
        if (s.publicVictoryPoints(s.counterFrom) >= s.config.vpToWin - 2) return false
        val goal = target(s, me)
        var gain = 0.0
        for (r in 0 until CARD_TYPES) {
            repeat(c.offerWant(r)) { gain += worth(s, me, goal, r, it) }
            repeat(c.offerGive(r)) { gain -= worth(s, me, goal, r, -it - 1) }
        }
        return gain > -0.05
    }

    /** Accept if what we get is worth more to us than what we give, and the offerer isn't about to win. */
    private fun offerHelpsMe(s: GameState, me: Int): Boolean {
        if (s.publicVictoryPoints(s.currentPlayer) >= s.config.vpToWin - 2) return false
        val goal = target(s, me)
        var gain = 0.0
        for (r in 0 until CARD_TYPES) {
            val get = s.tradeOffer[r]              // the offerer gives this to us
            val give = s.tradeOffer[CARD_TYPES + r] // we give this
            repeat(get) { gain += worth(s, me, goal, r, it) }
            repeat(give) { gain -= worth(s, me, goal, r, -it - 1) }
        }
        // Clearly good: accept. About even: accept sometimes, like a friendly player would.
        return gain > 0.5 || (gain >= -0.05 && rng.nextInt(100) < 40)
    }

    /** Expected yield of a spot: dice combinations of touching hexes, plus a bonus for variety. */
    private fun vertexValue(s: GameState, v: Int): Double {
        val b = s.board
        var pips = 0
        val kinds = HashSet<Int>()
        for (h in b.topology.vertexHexes[v]) {
            if (b.hexResource[h] == Resource.NONE || h == s.robberHex) continue
            pips += pips(b.hexNumber[h])
            kinds += b.hexResource[h]
        }
        return pips + 1.5 * kinds.size
    }

    private fun roadScore(s: GameState, me: Int, edge: Int): Double {
        // Roads are only worth it while they lead somewhere we could settle.
        val roads = s.piecesOnBoard(me, Piece.ROAD)
        val towns = s.piecesOnBoard(me, Piece.SETTLEMENT) + s.piecesOnBoard(me, Piece.CITY)
        if (s.freeRoads == 0 && s.hasRolled && roads >= 2 * towns + 1) return -1.0
        val ends = s.board.topology.edgeVertices[edge]
        val reach = ends.maxOf { v -> if (canSettle(s, v)) vertexValue(s, v) else 0.0 }
        return 400.0 + reach
    }

    private fun canSettle(s: GameState, v: Int): Boolean =
        s.vertexOwner[v] < 0 && s.board.topology.vertexNeighbors[v].all { s.vertexOwner[it] < 0 }

    private fun robberHurtsMe(s: GameState, me: Int) =
        s.robberHex >= 0 && s.board.topology.hexVertices[s.robberHex].any { s.vertexOwner[it] == me }

    private fun robberScore(s: GameState, me: Int, hex: Int, victim: Int): Double {
        var score = 0.0
        val number = pips(s.board.hexNumber[hex]).toDouble()
        for (v in s.board.topology.hexVertices[hex]) {
            val owner = s.vertexOwner[v]
            if (owner < 0) continue
            val weight = if (s.vertexBuilding[v] == Piece.CITY) 2 else 1
            score += if (owner == me) -3 * number * weight else number * weight * (1 + s.publicVictoryPoints(owner) / 4.0)
        }
        if (victim >= 0) score += s.handSize(victim) / 2.0
        return score
    }

    private fun GameState.handSizeOthers(me: Int, r: Int) =
        (0 until numPlayers).filter { it != me }.sumOf { hand(it, r) }.toDouble()

    companion object {
        // Costs in engine resource order: wood, brick, sheep, wheat, ore.
        private val COST_ROAD = intArrayOf(1, 1, 0, 0, 0)
        private val COST_SETTLEMENT = intArrayOf(1, 1, 1, 1, 0)
        private val COST_CITY = intArrayOf(0, 0, 0, 2, 3)
        private val COST_DEV = intArrayOf(0, 0, 1, 1, 1)

        fun pips(n: Int) = if (n == 0) 0 else 6 - kotlin.math.abs(7 - n)
    }
}
