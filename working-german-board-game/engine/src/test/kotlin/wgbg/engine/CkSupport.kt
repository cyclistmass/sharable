package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

/** Cities & Knights test helpers and invariant checker. */
object CK {
    fun config(players: Int = 4, layout: BoardLayout = BoardLayout.BEGINNER, maxTurns: Int = 1000) =
        GameConfig(expansion = Expansion.CITIES_AND_KNIGHTS, numPlayers = players, layout = layout, maxTurns = maxTurns)

    /** Beginner board, setup skipped, player 0 to move before rolling. */
    fun playState(players: Int = 4): GameState = T.playState(config = config(players))

    fun ck(s: GameState) = s.ck!!

    /** Gives [n] cards of C&K card index [c] (resource or commodity) from the bank. */
    fun give(s: GameState, p: Int, c: Int, n: Int) = CitiesAndKnights.fromBank(s, p, c, n)

    fun knight(s: GameState, p: Int, v: Int, level: Int = 1, active: Boolean = false) {
        val ck = ck(s)
        check(ck.knightOwner[v] < 0 && s.vertexOwner[v] < 0)
        ck.knightOwner[v] = p
        ck.knightLevel[v] = level
        ck.knightFlags[v] = if (active) CkState.ACTIVE else 0
        ck.knightsLeft[p * 3 + level - 1]--
        CitiesAndKnights.recomputeRoads(s)
    }

    /** Sets a track level directly (and the metropolis, as if built in order, when it would be claimed). */
    fun level(s: GameState, p: Int, track: Int, level: Int, metroVertex: Int = -1) {
        val ck = ck(s)
        ck.trackLevel[p * Track.COUNT + track] = level
        if (level >= Track.METROPOLIS_LEVEL && ck.metroOwner[track] < 0) {
            val v = if (metroVertex >= 0) metroVertex
            else (0 until s.board.vertexCount).first {
                s.vertexOwner[it] == p && s.vertexBuilding[it] == Piece.CITY && !ck.isMetropolis(it)
            }
            ck.metroOwner[track] = p
            ck.metroVertex[track] = v
        }
    }

    fun giveProgress(s: GameState, p: Int, card: Int) {
        val ck = ck(s)
        val t = Progress.track(card)
        val per = Progress.PER_DECK
        // Take the card out of its deck so card counts stay conserved.
        for (i in 0 until ck.deckSize[t]) {
            val idx = t * per + (ck.deckHead[t] + i) % per
            if (ck.deck[idx] == card) {
                // Swap it to the top and draw it.
                val top = t * per + ck.deckHead[t]
                ck.deck[idx] = ck.deck[top]
                ck.deck[top] = card
                ck.deckHead[t] = (ck.deckHead[t] + 1) % per
                ck.deckSize[t]--
                ck.progressHand[p * Progress.COUNT + card]++
                return
            }
        }
        error("no ${Progress.NAMES[card]} left in deck")
    }

    /** Puts [card] on top of its deck (so the next draw of that colour yields it). */
    fun stackDeck(s: GameState, card: Int) {
        val ck = ck(s)
        val t = Progress.track(card)
        val per = Progress.PER_DECK
        for (i in 0 until ck.deckSize[t]) {
            val idx = t * per + (ck.deckHead[t] + i) % per
            if (ck.deck[idx] == card) {
                val top = t * per + ck.deckHead[t]
                ck.deck[idx] = ck.deck[top]
                ck.deck[top] = card
                return
            }
        }
        error("no ${Progress.NAMES[card]} in deck")
    }

    /** Reseeds the RNG so the next ROLL gives red [red], yellow [yellow] and event face [event] (0..5). */
    fun rig(s: GameState, red: Int, yellow: Int, event: Int) {
        var seed = 0L
        while (true) {
            val r = Rng(seed)
            if (r.nextInt(6) + 1 == red && r.nextInt(6) + 1 == yellow && r.nextInt(6) == event) break
            seed++
        }
        s.rng.seed = seed
    }

    /** Rolls with the given dice. Event 3 (trade gate) is harmless while nobody has trade improvements. */
    fun roll(s: GameState, red: Int, yellow: Int, event: Int = 3) {
        rig(s, red, yellow, event)
        Rules.applyChecked(s, Action.ROLL)
    }

    fun legal(s: GameState) = Rules.legalActions(s)

    fun legalOf(s: GameState, kind: ActionKind) = legal(s).filter { it.type == kind }

    fun hexAt(s: GameState, q: Int, r: Int) = s.board.hexId(Hex(q, r))

    fun checkInvariants(s: GameState) {
        val ck = ck(s)
        val b = s.board
        val n = s.numPlayers
        // Card conservation.
        for (r in 0 until Resource.COUNT) {
            var total = s.bank[r]
            assertTrue(s.bank[r] >= 0, "bank negative")
            for (p in 0 until n) {
                assertTrue(s.hand(p, r) >= 0, "hand negative")
                total += s.hand(p, r)
            }
            assertEquals(BANK_PER_RESOURCE, total, "resource $r not conserved")
        }
        for (l in 0 until Commodity.COUNT) {
            var total = ck.commodityBank[l]
            assertTrue(ck.commodityBank[l] >= 0, "commodity bank negative")
            for (p in 0 until n) {
                assertTrue(ck.commodity(p, l) >= 0, "commodity negative")
                total += ck.commodity(p, l)
            }
            assertEquals(Commodity.BANK, total, "commodity $l not conserved")
        }
        // No development cards in C&K.
        assertEquals(DevCard.DECK.sum(), s.devDeck.sum())
        assertEquals(0, s.devHand.sum())
        assertEquals(-1, s.largestArmyOwner)
        // Progress cards: hands + decks + revealed VP cards = the 54-card deck.
        val seen = IntArray(Progress.COUNT)
        for (t in 0 until Track.COUNT) {
            assertTrue(ck.deckSize[t] in 0..Progress.PER_DECK)
            for (i in 0 until ck.deckSize[t]) {
                val c = ck.deck[t * Progress.PER_DECK + (ck.deckHead[t] + i) % Progress.PER_DECK]
                assertEquals(t, Progress.track(c), "card in wrong deck")
                seen[c]++
            }
        }
        for (p in 0 until n) for (c in 0 until Progress.COUNT) {
            val k = ck.progressHand[p * Progress.COUNT + c]
            assertTrue(k >= 0)
            if (Progress.isVictoryPoint(c)) assertEquals(0, k, "VP progress card held in hand")
            seen[c] += k
        }
        val vpCards = ck.progressVp.sum()
        assertEquals(
            Progress.DECK[Progress.PRINTER] + Progress.DECK[Progress.CONSTITUTION],
            seen[Progress.PRINTER] + seen[Progress.CONSTITUTION] + vpCards, "VP progress cards",
        )
        for (c in 0 until Progress.COUNT) {
            if (!Progress.isVictoryPoint(c)) assertEquals(Progress.DECK[c], seen[c], "progress ${Progress.NAMES[c]} not conserved")
        }
        assertEquals(CkPiece.DEFENDER_CARDS, ck.defenderLeft + ck.defenderVp.sum())
        // Pieces, knights, walls, VP.
        for (p in 0 until n) {
            var settlements = 0
            var cities = 0
            var roads = 0
            var ports = 0
            var walls = 0
            val knights = IntArray(3)
            for (v in 0 until b.vertexCount) {
                if (s.vertexOwner[v] == p) {
                    if (s.vertexBuilding[v] == Piece.CITY) cities++ else settlements++
                    ports = ports or b.vertexPortBit[v]
                    if (ck.wall[v] != 0) walls++
                }
                if (ck.knightOwner[v] == p) knights[ck.knightLevel[v] - 1]++
            }
            for (e in 0 until b.edgeCount) if (s.edgeOwner[e] == p) roads++
            assertEquals(settlements, s.piecesOnBoard(p, Piece.SETTLEMENT))
            assertEquals(cities, s.piecesOnBoard(p, Piece.CITY))
            assertEquals(roads, s.piecesOnBoard(p, Piece.ROAD))
            for (k in 0 until Piece.COUNT) assertTrue(s.piecesLeft[p * Piece.COUNT + k] >= 0)
            assertEquals(ports, s.portMask[p], "port mask")
            assertEquals(CkPiece.WALLS, walls + ck.wallsLeft[p], "walls")
            for (l in 0 until 3) {
                // A displaced knight waiting to be relocated (heldFrom >= 0) is off the board but not in supply.
                val held = if (ck.heldFrom >= 0 && ck.heldOwner == p && (ck.heldKnight and 3) == l + 1) 1 else 0
                assertTrue(ck.knightsLeft[p * 3 + l] >= 0)
                assertEquals(CkPiece.KNIGHTS_PER_LEVEL, knights[l] + ck.knightsLeft[p * 3 + l] + held, "knights level ${l + 1}")
            }
            assertEquals(Rules.longestRoad(s, p), s.roadLength[p], "road length")
            for (t in 0 until Track.COUNT) assertTrue(ck.level(p, t) in 0..Track.MAX_LEVEL)
            assertEquals(
                settlements + 2 * cities + 2 * ck.metropolises(p) + (if (s.longestRoadOwner == p) 2 else 0) +
                    ck.defenderVp[p] + ck.progressVp[p] + (if (ck.merchantOwner == p) 1 else 0),
                s.victoryPoints(p), "VP",
            )
        }
        for (v in 0 until b.vertexCount) {
            if (ck.wall[v] != 0) assertEquals(Piece.CITY, s.vertexBuilding[v], "wall without city")
            val ko = ck.knightOwner[v]
            if (ko >= 0) {
                assertTrue(ck.knightLevel[v] in 1..3)
                assertTrue(s.vertexOwner[v] < 0, "knight on a building")
            } else {
                assertEquals(0, ck.knightLevel[v])
                assertEquals(0, ck.knightFlags[v])
            }
            if (ck.knightFlags[v] and CkState.FRESH != 0) assertTrue(ck.isActive(v))
            if (s.vertexOwner[v] < 0) continue
            for (i in 0 until 3) {
                val nb = b.vertexNbr[v * 3 + i]
                if (nb >= 0) assertTrue(s.vertexOwner[nb] < 0, "distance rule broken at $v")
            }
        }
        // Metropolises: unique, on the holder's city, holder at level >= 4, nobody can still claim one.
        for (t in 0 until Track.COUNT) {
            val o = ck.metroOwner[t]
            if (o >= 0) {
                val v = ck.metroVertex[t]
                assertEquals(o, s.vertexOwner[v])
                assertEquals(Piece.CITY, s.vertexBuilding[v])
                assertTrue(ck.level(o, t) >= Track.METROPOLIS_LEVEL)
                for (u in 0 until Track.COUNT) if (u != t) assertTrue(ck.metroVertex[u] != v, "two metropolises on one city")
                for (q in 0 until n) {
                    if (q != o && ck.level(o, t) < Track.MAX_LEVEL) assertTrue(ck.level(q, t) < Track.MAX_LEVEL, "metropolis not taken")
                }
            } else {
                assertEquals(-1, ck.metroVertex[t])
                for (q in 0 until n) assertTrue(ck.level(q, t) < Track.METROPOLIS_LEVEL, "unclaimed metropolis")
            }
        }
        if (ck.merchantOwner >= 0) assertTrue(b.hexResource[ck.merchantHex] >= 0)
        // Longest road.
        val maxRoad = s.roadLength.max()
        if (s.longestRoadOwner >= 0) {
            assertTrue(s.roadLength[s.longestRoadOwner] >= 5)
            assertEquals(maxRoad, s.roadLength[s.longestRoadOwner])
        } else if (maxRoad >= 5) {
            assertTrue(s.roadLength.count { it == maxRoad } > 1, "longest road unassigned")
        }
        assertTrue(s.robberHex in 0 until b.hexCount)
        if (!ck.robberActive) assertEquals(b.desertHex, s.robberHex, "robber moved before the first attack")
        assertTrue(ck.barbarianPos in 0 until CkPiece.BARBARIAN_TRACK)
        // Phase-specific.
        when (s.phase) {
            Phase.GAME_OVER -> {
                if (s.winner >= 0) assertTrue(s.victoryPoints(s.winner) >= s.config.vpToWin)
                else assertTrue(s.turn >= s.config.maxTurns)
            }
            else -> {
                assertTrue(s.victoryPoints(s.currentPlayer) < s.config.vpToWin)
                if (Rules.legalActions(s).isEmpty()) fail<Unit>("no legal actions in ${s.phase}")
            }
        }
        if (s.phase == Phase.PLAY_TURN) {
            for (p in 0 until n) assertTrue(ck.progressCount(p) <= Progress.HAND_LIMIT, "progress hand over limit")
            assertEquals(0, ck.barbLossMask)
            assertEquals(0, ck.defenderDrawMask)
            assertEquals(0, ck.aqueductMask)
            assertEquals(0, ck.heldKnight)
        }
        // A win can end the game in the middle of resolving a roll or card; nothing else may be left pending.
        if (s.phase == Phase.DISCARD) assertTrue(s.discardOwed[s.actingPlayer] > 0)
        else if (s.phase != Phase.GAME_OVER) assertTrue(s.discardOwed.all { it == 0 }, "discards outstanding in ${s.phase}")
        if (s.phase != Phase.WEDDING && s.phase != Phase.GAME_OVER) assertTrue(ck.giveOwed.all { it == 0 })
    }
}
