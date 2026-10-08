package wgbg.ai

import wgbg.engine.Action
import wgbg.engine.ActionBuffer
import wgbg.engine.ActionType
import wgbg.engine.GameState
import wgbg.engine.Piece
import wgbg.engine.Resource
import wgbg.engine.Rng

/**
 * Simple rule-of-thumb bot so demo games look sensible: builds the best-yield
 * spot it can afford, never offers player trades, robs the leader.
 * A stand-in until the Catanatron-style AlphaBeta bot lands in Phase 3.
 */
class GreedyBot(seed: Long = 0, override val name: String = "Greedy") : Bot {
    private val rng = Rng(seed)

    override fun choose(state: GameState, legal: ActionBuffer): Action {
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
                if (s.hand(me, a.maritimeGive) >= 4 && s.hand(me, a.maritimeGet) == 0) 200.0 else -1.0
            ActionType.MOVE_ROBBER -> robberScore(s, me, a.robberHex, a.robberVictim)
            ActionType.DISCARD -> s.hand(me, a.discardResource).toDouble()
            ActionType.ROLL -> 50.0
            ActionType.ACCEPT_TRADE, ActionType.OFFER_TRADE -> -10.0
            ActionType.END_TURN, ActionType.REJECT_TRADE, ActionType.CANCEL_TRADE -> 0.0
            else -> 1.0
        }
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
        fun pips(n: Int) = if (n == 0) 0 else 6 - kotlin.math.abs(7 - n)
    }
}
