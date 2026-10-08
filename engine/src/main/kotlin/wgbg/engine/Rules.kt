package wgbg.engine

import kotlin.math.max
import kotlin.math.min

/**
 * The base-game rules: move generation and state transitions. Stateless; all
 * randomness comes from [GameState.rng], so replaying the same actions on a
 * state created with the same config and seed reproduces it exactly.
 */
object Rules {

    fun newGame(config: GameConfig, seed: Long, board: Board? = null): GameState {
        val rng = Rng(seed)
        val b = board ?: when (config.layout) {
            BoardLayout.RANDOM -> BoardGen.random(rng, config.avoidAdjacentRedNumbers)
            BoardLayout.BEGINNER -> BoardGen.beginner()
        }
        return GameState.initial(config, b, rng)
    }

    /** Seat that places at setup step [step]: 0..n-1 then n-1..0 (snake draft). */
    fun setupPlayer(step: Int, n: Int) = if (step < n) step else 2 * n - 1 - step

    // ---------------------------------------------------------------- queries

    fun canAfford(s: GameState, p: Int, cost: IntArray): Boolean {
        val base = p * CARD_TYPES
        for (r in 0 until CARD_TYPES) if (s.hands[base + r] < cost[r]) return false
        return true
    }

    fun piecesLeft(s: GameState, p: Int, piece: Int) = s.piecesLeft[p * Piece.COUNT + piece]

    fun hasRoadAt(s: GameState, p: Int, v: Int): Boolean {
        val b = s.board
        for (i in 0 until 3) {
            val e = b.vertexEdge[v * 3 + i]
            if (e >= 0 && s.edgeOwner[e] == p) return true
        }
        return false
    }

    /** Distance rule: vertex empty and all neighbours empty. */
    fun isVertexFree(s: GameState, v: Int): Boolean {
        if (s.vertexOwner[v] >= 0) return false
        // C&K: no settlement on a knight (knights do not count for the distance rule).
        val ck = s.ck
        if (ck != null && ck.knightOwner[v] >= 0) return false
        val b = s.board
        for (i in 0 until 3) {
            val n = b.vertexNbr[v * 3 + i]
            if (n >= 0 && s.vertexOwner[n] >= 0) return false
        }
        return true
    }

    /** Road network of [p] may extend from [v]: own building, or own road not cut by an opponent's building. */
    private fun canExtendFrom(s: GameState, p: Int, v: Int): Boolean {
        val owner = s.vertexOwner[v]
        if (owner == p) return true
        if (owner >= 0) return false
        // C&K: an opponent's knight blocks roads like a building.
        val ck = s.ck
        if (ck != null && ck.knightOwner[v] >= 0 && ck.knightOwner[v] != p) return false
        return hasRoadAt(s, p, v)
    }

    fun canBuildRoadAt(s: GameState, p: Int, e: Int): Boolean =
        s.edgeOwner[e] < 0 && (canExtendFrom(s, p, s.board.edgeA[e]) || canExtendFrom(s, p, s.board.edgeB[e]))

    fun anyRoadSpot(s: GameState, p: Int): Boolean {
        for (e in 0 until s.board.edgeCount) if (canBuildRoadAt(s, p, e)) return true
        return false
    }

    fun canBuildSettlementAt(s: GameState, p: Int, v: Int) = isVertexFree(s, v) && hasRoadAt(s, p, v)

    fun canPlayDev(s: GameState, p: Int, card: Int): Boolean {
        val i = p * DevCard.COUNT + card
        return !s.devPlayedThisTurn && s.devHand[i] - s.devNew[i] > 0
    }

    /** Best maritime rate for giving away [r]: 2 with its port, 3 with a generic port, else 4. */
    fun tradeRate(s: GameState, p: Int, r: Int): Int {
        val mask = s.portMask[p]
        return when {
            mask and (1 shl r) != 0 -> 2
            mask and Port.GENERIC_BIT != 0 -> 3
            else -> 4
        }
    }

    // ------------------------------------------------------- move generation

    fun legalActions(s: GameState): List<Action> = legalActions(s, ActionBuffer()).toList()

    /** Fills [out] with every legal action for [GameState.actingPlayer] (OFFER_TRADE only if configured). */
    fun legalActions(s: GameState, out: ActionBuffer): ActionBuffer {
        out.clear()
        if (s.ck != null) return CitiesAndKnights.legalActions(s, out)
        val p = s.actingPlayer
        val b = s.board
        when (s.phase) {
            Phase.BUILD_INITIAL_SETTLEMENT ->
                for (v in 0 until b.vertexCount) if (isVertexFree(s, v)) out.add(Action.buildSettlement(v))

            Phase.BUILD_INITIAL_ROAD -> {
                val v = s.lastSettlement
                for (i in 0 until 3) {
                    val e = b.vertexEdge[v * 3 + i]
                    if (e >= 0 && s.edgeOwner[e] < 0) out.add(Action.buildRoad(e))
                }
            }

            Phase.PLAY_TURN -> playTurnActions(s, p, out)
            Phase.DISCARD -> {
                if (s.config.randomDiscard) out.add(Action.discard(-1))
                else for (r in 0 until CARD_TYPES) if (s.hand(p, r) > 0) out.add(Action.discard(r))
            }

            Phase.MOVE_ROBBER -> robberActions(s, p, out)
            Phase.DECIDE_TRADE -> {
                out.add(Action.REJECT_TRADE)
                var ok = true
                for (r in 0 until CARD_TYPES) if (s.hand(p, r) < s.tradeOffer[CARD_TYPES + r]) ok = false
                if (ok) out.add(Action.ACCEPT_TRADE)
            }

            Phase.DECIDE_ACCEPTEES -> {
                out.add(Action.CANCEL_TRADE)
                for (q in 0 until s.numPlayers) if (s.tradeAccepted and (1 shl q) != 0) out.add(Action.confirmTrade(q))
            }

            Phase.GAME_OVER -> {}
            else -> {} // C&K-only prompts never occur in base games
        }
        return out
    }

    private fun playTurnActions(s: GameState, p: Int, out: ActionBuffer) {
        val b = s.board
        if (s.freeRoads > 0) {
            for (e in 0 until b.edgeCount) if (canBuildRoadAt(s, p, e)) out.add(Action.buildRoad(e))
            return
        }
        // Official rules (and Catanatron) allow one development card at any time in the turn, even before rolling.
        if (canPlayDev(s, p, DevCard.KNIGHT)) out.add(Action.PLAY_KNIGHT_CARD)
        if (canPlayDev(s, p, DevCard.YEAR_OF_PLENTY)) {
            val start = out.size
            for (i in 0 until CARD_TYPES) for (j in i until CARD_TYPES) {
                val ok = if (i == j) s.bank[i] >= 2 else s.bank[i] >= 1 && s.bank[j] >= 1
                if (ok) out.add(Action.yearOfPlenty(i, j))
            }
            // Only when no pair can be drawn, allow taking a single card.
            if (out.size == start) for (i in 0 until CARD_TYPES) if (s.bank[i] >= 1) out.add(Action.yearOfPlenty(i))
        }
        if (canPlayDev(s, p, DevCard.MONOPOLY)) for (r in 0 until CARD_TYPES) out.add(Action.monopoly(r))
        if (canPlayDev(s, p, DevCard.ROAD_BUILDING) && piecesLeft(s, p, Piece.ROAD) > 0 && anyRoadSpot(s, p)) {
            out.add(Action.PLAY_ROAD_BUILDING)
        }
        if (!s.hasRolled) {
            out.add(Action.ROLL)
            return
        }
        out.add(Action.END_TURN)
        if (piecesLeft(s, p, Piece.ROAD) > 0 && canAfford(s, p, Cost.ROAD)) {
            for (e in 0 until b.edgeCount) if (canBuildRoadAt(s, p, e)) out.add(Action.buildRoad(e))
        }
        if (piecesLeft(s, p, Piece.SETTLEMENT) > 0 && canAfford(s, p, Cost.SETTLEMENT)) {
            for (v in 0 until b.vertexCount) if (canBuildSettlementAt(s, p, v)) out.add(Action.buildSettlement(v))
        }
        if (piecesLeft(s, p, Piece.CITY) > 0 && canAfford(s, p, Cost.CITY)) {
            for (v in 0 until b.vertexCount) {
                if (s.vertexOwner[v] == p && s.vertexBuilding[v] == Piece.SETTLEMENT) out.add(Action.buildCity(v))
            }
        }
        if (canAfford(s, p, Cost.DEV_CARD) && s.devDeck.sum() > 0) out.add(Action.BUY_DEVELOPMENT_CARD)
        for (give in 0 until CARD_TYPES) {
            if (s.hand(p, give) < tradeRate(s, p, give)) continue
            for (get in 0 until CARD_TYPES) if (get != give && s.bank[get] > 0) out.add(Action.maritimeTrade(give, get))
        }
        if (s.config.generateTradeOffers) {
            val give = IntArray(CARD_TYPES)
            val want = IntArray(CARD_TYPES)
            for (g in 0 until CARD_TYPES) {
                if (s.hand(p, g) == 0) continue
                for (w in 0 until CARD_TYPES) {
                    if (w == g) continue
                    give[g] = 1; want[w] = 1
                    out.add(Action.offerTrade(give, want))
                    give[g] = 0; want[w] = 0
                }
            }
        }
    }

    private fun robberActions(s: GameState, p: Int, out: ActionBuffer) {
        val b = s.board
        for (h in 0 until b.hexCount) {
            if (h == s.robberHex) continue
            val victims = robberVictims(s, p, h)
            if (victims == 0) out.add(Action.moveRobber(h, -1))
            else for (q in 0 until s.numPlayers) if (victims and (1 shl q) != 0) out.add(Action.moveRobber(h, q))
        }
    }

    /** Bitmask of opponents with a building on [h] and at least one card. */
    fun robberVictims(s: GameState, p: Int, h: Int): Int {
        var mask = 0
        for (i in 0 until 6) {
            val o = s.vertexOwner[s.board.hexVerts[h * 6 + i]]
            if (o >= 0 && o != p && s.handSize(o) > 0) mask = mask or (1 shl o)
        }
        return mask
    }

    /** Domestic offers are not enumerated (see [GameConfig.generateTradeOffers]) so they are validated here. */
    fun isValidOffer(s: GameState, a: Action): Boolean {
        if (s.phase != Phase.PLAY_TURN || !s.hasRolled || s.freeRoads > 0) return false
        if (a.payload ushr 40 != 0L) return false
        val p = s.actingPlayer
        var gives = 0
        var wants = 0
        for (r in 0 until CARD_TYPES) {
            val g = a.offerGive(r)
            val w = a.offerWant(r)
            if (g > 0 && w > 0) return false
            if (s.hand(p, r) < g) return false
            gives += g
            wants += w
        }
        // Official rules forbid giving cards away for nothing.
        return gives > 0 && wants > 0
    }

    fun isLegal(s: GameState, a: Action): Boolean {
        if (s.ck != null) {
            if (a.type == ActionType.OFFER_TRADE) return false
            if (a.type == CkActionType.OFFER_TRADE_CK) return CitiesAndKnights.isValidOffer(s, a)
        }
        if (a.type == ActionType.OFFER_TRADE) return isValidOffer(s, a)
        return a in legalActions(s, ActionBuffer())
    }

    /** [apply] with a legality check; use for untrusted input (UI, network). */
    fun applyChecked(s: GameState, a: Action) {
        require(Action.isKnownType(a.typeId) && isLegal(s, a)) { "illegal action $a in ${s.phase}" }
        apply(s, a)
    }

    // ------------------------------------------------------------ transitions

    /** Applies a legal action for [GameState.actingPlayer]. Behaviour is undefined for illegal actions. */
    fun apply(s: GameState, a: Action) {
        if (s.ck != null) CitiesAndKnights.apply(s, a) else applyBase(s, a)
        if (s.phase != Phase.GAME_OVER && s.victoryPoints(s.currentPlayer) >= s.config.vpToWin) {
            // Victory is only claimed during your own turn (checked after every action, including turn start).
            s.winner = s.currentPlayer
            s.phase = Phase.GAME_OVER
        }
    }

    private fun applyBase(s: GameState, a: Action) {
        val p = s.actingPlayer
        when (a.type) {
            ActionType.BUILD_SETTLEMENT -> buildSettlement(s, p, a.payload.toInt())
            ActionType.BUILD_ROAD -> buildRoad(s, p, a.payload.toInt())
            ActionType.BUILD_CITY -> {
                val v = a.payload.toInt()
                pay(s, p, Cost.CITY)
                s.vertexBuilding[v] = Piece.CITY
                s.piecesLeft[p * Piece.COUNT + Piece.CITY]--
                s.piecesLeft[p * Piece.COUNT + Piece.SETTLEMENT]++
            }

            ActionType.BUY_DEVELOPMENT_CARD -> {
                pay(s, p, Cost.DEV_CARD)
                var k = s.rng.nextInt(s.devDeck.sum())
                var card = 0
                while (k >= s.devDeck[card]) k -= s.devDeck[card++]
                s.devDeck[card]--
                s.devHand[p * DevCard.COUNT + card]++
                s.devNew[p * DevCard.COUNT + card]++
            }

            ActionType.ROLL -> roll(s, p)
            ActionType.DISCARD -> discard(s, p, a.discardResource)
            ActionType.MOVE_ROBBER -> {
                s.robberHex = a.robberHex
                val victim = a.robberVictim
                if (victim >= 0) {
                    val r = randomCard(s, victim)
                    s.hands[victim * CARD_TYPES + r]--
                    s.hands[p * CARD_TYPES + r]++
                }
                s.phase = Phase.PLAY_TURN
            }

            ActionType.PLAY_KNIGHT_CARD -> {
                playDev(s, p, DevCard.KNIGHT)
                val k = s.knightsPlayed(p)
                val holder = s.largestArmyOwner
                if (k >= 3 && holder != p && (holder < 0 || k > s.knightsPlayed(holder))) s.largestArmyOwner = p
                s.phase = Phase.MOVE_ROBBER
            }

            ActionType.PLAY_YEAR_OF_PLENTY -> {
                playDev(s, p, DevCard.YEAR_OF_PLENTY)
                take(s, p, a.yopFirst, 1)
                if (a.yopSecond >= 0) take(s, p, a.yopSecond, 1)
            }

            ActionType.PLAY_MONOPOLY -> {
                playDev(s, p, DevCard.MONOPOLY)
                val r = a.a
                for (q in 0 until s.numPlayers) {
                    if (q == p) continue
                    s.hands[p * CARD_TYPES + r] += s.hands[q * CARD_TYPES + r]
                    s.hands[q * CARD_TYPES + r] = 0
                }
            }

            ActionType.PLAY_ROAD_BUILDING -> {
                playDev(s, p, DevCard.ROAD_BUILDING)
                s.freeRoads = min(2, piecesLeft(s, p, Piece.ROAD))
            }

            ActionType.MARITIME_TRADE -> {
                val give = a.maritimeGive
                val rate = tradeRate(s, p, give)
                s.hands[p * CARD_TYPES + give] -= rate
                s.bank[give] += rate
                take(s, p, a.maritimeGet, 1)
            }

            ActionType.OFFER_TRADE -> {
                for (r in 0 until CARD_TYPES) {
                    s.tradeOffer[r] = a.offerGive(r)
                    s.tradeOffer[CARD_TYPES + r] = a.offerWant(r)
                }
                s.tradeAccepted = 0
                s.phase = Phase.DECIDE_TRADE
                s.actingPlayer = (p + 1) % s.numPlayers
            }

            ActionType.ACCEPT_TRADE, ActionType.REJECT_TRADE -> {
                if (a.type == ActionType.ACCEPT_TRADE) s.tradeAccepted = s.tradeAccepted or (1 shl p)
                val next = (p + 1) % s.numPlayers
                when {
                    next != s.currentPlayer -> s.actingPlayer = next
                    s.tradeAccepted == 0 -> endTrade(s)
                    else -> {
                        s.phase = Phase.DECIDE_ACCEPTEES
                        s.actingPlayer = s.currentPlayer
                    }
                }
            }

            ActionType.CONFIRM_TRADE -> {
                val q = a.payload.toInt()
                for (r in 0 until CARD_TYPES) {
                    val give = s.tradeOffer[r]
                    val want = s.tradeOffer[CARD_TYPES + r]
                    s.hands[p * CARD_TYPES + r] += want - give
                    s.hands[q * CARD_TYPES + r] += give - want
                }
                endTrade(s)
            }

            ActionType.CANCEL_TRADE -> endTrade(s)
            ActionType.END_TURN -> endTurn(s, p)
            else -> error("${a.type} is not a base-game action")
        }
    }

    internal fun pay(s: GameState, p: Int, cost: IntArray) {
        val base = p * CARD_TYPES
        for (r in 0 until CARD_TYPES) {
            s.hands[base + r] -= cost[r]
            s.bank[r] += cost[r]
        }
    }

    internal fun take(s: GameState, p: Int, r: Int, n: Int) {
        s.bank[r] -= n
        s.hands[p * CARD_TYPES + r] += n
    }

    private fun playDev(s: GameState, p: Int, card: Int) {
        s.devHand[p * DevCard.COUNT + card]--
        s.devPlayed[p * DevCard.COUNT + card]++
        s.devPlayedThisTurn = true
    }

    private fun randomCard(s: GameState, p: Int): Int {
        var k = s.rng.nextInt(s.handSize(p))
        var r = 0
        while (k >= s.hands[p * CARD_TYPES + r]) k -= s.hands[p * CARD_TYPES + r++]
        return r
    }

    internal fun buildSettlement(s: GameState, p: Int, v: Int) {
        val b = s.board
        val setup = s.phase == Phase.BUILD_INITIAL_SETTLEMENT
        if (!setup) pay(s, p, Cost.SETTLEMENT)
        s.vertexOwner[v] = p
        s.vertexBuilding[v] = Piece.SETTLEMENT
        s.piecesLeft[p * Piece.COUNT + Piece.SETTLEMENT]--
        s.portMask[p] = s.portMask[p] or b.vertexPortBit[v]
        if (setup) {
            if (s.setupStep >= s.numPlayers) {
                // Second settlement: one card from each adjacent producing hex.
                for (i in 0 until 3) {
                    val h = b.vertexHex[v * 3 + i]
                    if (h >= 0 && b.hexResource[h] >= 0 && s.bank[b.hexResource[h]] > 0) take(s, p, b.hexResource[h], 1)
                }
            }
            s.lastSettlement = v
            s.phase = Phase.BUILD_INITIAL_ROAD
        }
        // A settlement on an opponent's road splits it.
        var changed = false
        for (q in 0 until s.numPlayers) {
            if (q == p) continue
            var n = 0
            for (i in 0 until 3) {
                val e = b.vertexEdge[v * 3 + i]
                if (e >= 0 && s.edgeOwner[e] == q) n++
            }
            if (n >= 2) {
                s.roadLength[q] = longestRoad(s, q)
                changed = true
            }
        }
        if (changed) updateLongestRoad(s)
    }

    internal fun buildRoad(s: GameState, p: Int, e: Int) {
        when {
            s.phase == Phase.BUILD_INITIAL_ROAD -> {}
            s.freeRoads > 0 -> s.freeRoads--
            else -> pay(s, p, Cost.ROAD)
        }
        s.edgeOwner[e] = p
        s.piecesLeft[p * Piece.COUNT + Piece.ROAD]--
        s.roadLength[p] = longestRoad(s, p)
        updateLongestRoad(s)
        if (s.phase == Phase.BUILD_INITIAL_ROAD) {
            s.setupStep++
            if (s.setupStep == 2 * s.numPlayers) {
                s.phase = Phase.PLAY_TURN
                s.currentPlayer = 0
            } else {
                s.phase = Phase.BUILD_INITIAL_SETTLEMENT
                s.currentPlayer = setupPlayer(s.setupStep, s.numPlayers)
            }
            s.actingPlayer = s.currentPlayer
        } else if (s.freeRoads > 0 && (piecesLeft(s, p, Piece.ROAD) == 0 || !anyRoadSpot(s, p))) {
            s.freeRoads = 0
        }
    }

    private fun roll(s: GameState, p: Int) {
        val d1 = s.rng.nextInt(6) + 1
        val d2 = s.rng.nextInt(6) + 1
        val sum = d1 + d2
        s.lastRoll = sum
        s.hasRolled = true
        if (sum != 7) {
            produce(s, sum)
            return
        }
        for (q in 0 until s.numPlayers) {
            val n = s.handSize(q)
            s.discardOwed[q] = if (n > s.config.discardLimit) n / 2 else 0
        }
        val debtor = nextDebtor(s, p)
        if (debtor >= 0) {
            s.phase = Phase.DISCARD
            s.actingPlayer = debtor
        } else {
            s.phase = Phase.MOVE_ROBBER
        }
    }

    /** First player from [start] in seat order who still owes discards, or -1. */
    internal fun nextDebtor(s: GameState, start: Int): Int {
        for (i in 0 until s.numPlayers) {
            val q = (start + i) % s.numPlayers
            if (s.discardOwed[q] > 0) return q
        }
        return -1
    }

    private fun discard(s: GameState, p: Int, r: Int) {
        val count = if (r < 0) s.discardOwed[p] else 1
        repeat(count) {
            val c = if (r < 0) randomCard(s, p) else r
            s.hands[p * CARD_TYPES + c]--
            s.bank[c]++
        }
        s.discardOwed[p] -= count
        val debtor = nextDebtor(s, p)
        if (debtor >= 0) {
            s.actingPlayer = debtor
        } else {
            s.phase = Phase.MOVE_ROBBER
            s.actingPlayer = s.currentPlayer
        }
    }

    /**
     * Pays out [roll]. Bank shortage rule: if the bank cannot pay everybody a
     * resource, nobody gets it, unless only one player is owed it, who then
     * takes whatever the bank has left.
     */
    private fun produce(s: GameState, roll: Int) {
        val b = s.board
        val n = s.numPlayers
        val hexes = b.hexesByNumber[roll]
        val owed = IntArray(n * CARD_TYPES)
        for (h in hexes) {
            if (h == s.robberHex) continue
            val r = b.hexResource[h]
            for (i in 0 until 6) {
                val v = b.hexVerts[h * 6 + i]
                val o = s.vertexOwner[v]
                if (o >= 0) owed[o * CARD_TYPES + r] += if (s.vertexBuilding[v] == Piece.CITY) 2 else 1
            }
        }
        for (r in 0 until CARD_TYPES) {
            var total = 0
            var recipients = 0
            var last = -1
            for (q in 0 until n) {
                val x = owed[q * CARD_TYPES + r]
                if (x > 0) {
                    total += x
                    recipients++
                    last = q
                }
            }
            if (total == 0) continue
            if (total <= s.bank[r]) {
                for (q in 0 until n) take(s, q, r, owed[q * CARD_TYPES + r])
            } else if (recipients == 1) {
                take(s, last, r, s.bank[r])
            }
        }
    }

    internal fun endTrade(s: GameState) {
        s.tradeOffer.fill(0)
        s.tradeAccepted = 0
        s.phase = Phase.PLAY_TURN
        s.actingPlayer = s.currentPlayer
    }

    private fun endTurn(s: GameState, p: Int) {
        for (d in 0 until DevCard.COUNT) s.devNew[p * DevCard.COUNT + d] = 0
        s.devPlayedThisTurn = false
        s.hasRolled = false
        s.freeRoads = 0
        s.turn++
        s.currentPlayer = (p + 1) % s.numPlayers
        s.actingPlayer = s.currentPlayer
        if (s.turn >= s.config.maxTurns) {
            s.phase = Phase.GAME_OVER
            s.winner = -1
        }
    }

    // ----------------------------------------------------------- longest road

    private val scratch = ThreadLocal.withInitial { BooleanArray(256) }

    /** Longest trail (no edge reused) in [p]'s roads; it cannot pass through an opponent's building. */
    fun longestRoad(s: GameState, p: Int): Int {
        val b = s.board
        var used = scratch.get()
        if (used.size < b.edgeCount) {
            used = BooleanArray(b.edgeCount)
            scratch.set(used)
        }
        var best = 0
        for (v in 0 until b.vertexCount) {
            if (!hasRoadAt(s, p, v)) continue
            best = max(best, dfs(s, p, v, used, true))
        }
        return best
    }

    private fun dfs(s: GameState, p: Int, v: Int, used: BooleanArray, start: Boolean): Int {
        val o = s.vertexOwner[v]
        if (!start && o >= 0 && o != p) return 0
        // C&K: opponents' knights break roads too.
        val ck = s.ck
        if (!start && ck != null && ck.knightOwner[v] >= 0 && ck.knightOwner[v] != p) return 0
        val b = s.board
        var best = 0
        for (i in 0 until 3) {
            val e = b.vertexEdge[v * 3 + i]
            if (e < 0 || used[e] || s.edgeOwner[e] != p) continue
            used[e] = true
            best = max(best, 1 + dfs(s, p, b.otherEnd(e, v), used, false))
            used[e] = false
        }
        return best
    }

    /**
     * Reassigns Longest Road after any road length change. The holder keeps it
     * while tied for the longest (>= 5); otherwise a unique longest road >= 5
     * takes it, and a tie between non-holders sets the card aside (5th-edition rule
     * for broken roads; for ordinary building it reduces to "must be strictly longer").
     */
    fun updateLongestRoad(s: GameState) {
        var best = 0
        var count = 0
        var who = -1
        for (q in 0 until s.numPlayers) {
            val len = s.roadLength[q]
            if (len > best) {
                best = len; count = 1; who = q
            } else if (len == best) {
                count++
            }
        }
        val holder = s.longestRoadOwner
        if (best >= 5 && holder >= 0 && s.roadLength[holder] == best) return
        s.longestRoadOwner = if (best >= 5 && count == 1) who else -1
    }
}
