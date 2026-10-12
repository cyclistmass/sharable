package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every Age of Chivalry progress card, played in real mid-game positions: each one does what it says, and
 * afterwards it is still the player's own turn (Mauro, 2026-10-12: "test every card works", after a card seemed
 * to end his turn). Printer and Constitution are revealed, not played; CkRulesTest covers them.
 */
class EveryCardTest {
    private val played = IntArray(Progress.COUNT)

    /** Mid-game positions from bot games: player 0's turn, after the roll (before it for the Transmuter). */
    private fun positions(beforeRoll: Boolean): List<GameState> = (1L..40L).mapNotNull { seed ->
        val s = Rules.newGame(CK.config(), seed)
        val bots = List(s.numPlayers) { Rng(seed * 31 + it) }
        val buf = ActionBuffer()
        var guard = 0
        // Play a while so there are towns, knights and roads on the board, then stop on player 0's turn.
        while (!s.isOver && guard++ < 20_000) {
            val ready = s.turn >= 24 && s.phase == Phase.PLAY_TURN && s.currentPlayer == 0 && s.actingPlayer == 0 && s.hasRolled != beforeRoll
            if (ready) break
            Rules.legalActions(s, buf)
            // Bots never play progress cards here, so the decks keep every card for the test to deal.
            val choices = (0 until buf.size).map { buf[it] }.filter { it.type != CkActionType.PLAY_PROGRESS }.ifEmpty { (0 until buf.size).map { buf[it] } }
            Rules.apply(s, choices[bots[s.actingPlayer].nextInt(choices.size)])
        }
        s.takeIf { !it.isOver && it.currentPlayer == 0 && it.phase == Phase.PLAY_TURN }
    }

    /** Answers prompts (player 0 ends theirs with CK_DONE when it can) until play is back in player 0's main turn. */
    private fun settle(s: GameState, tag: String) {
        var guard = 0
        while (s.phase != Phase.PLAY_TURN && !s.isOver) {
            if (guard++ > 300) throw AssertionError("$tag: stuck in ${s.phase}")
            val legal = Rules.legalActions(s)
            Rules.applyChecked(s, legal.firstOrNull { it == Action.CK_DONE } ?: legal.first())
        }
    }

    private fun knights(s: GameState, p: Int) = s.ck!!.knightOwner.count { it == p }

    /** Checks [card]'s effect from [b] (before) to [s] (after) for the play [a]. */
    private fun effect(card: Int, a: Action, b: GameState, s: GameState, tag: String) {
        val ck = s.ck!!
        val was = b.ck!!
        when (card) {
            Progress.ALCHEMIST -> assertEquals(a.b + a.c, s.lastRoll, "$tag: the chosen dice are the roll")
            Progress.CRANE -> assertEquals(was.trackLevel[a.b] + 1, ck.trackLevel[a.b], "$tag: improved one level")
            Progress.ENGINEER -> assertEquals(1, ck.wall[a.b], "$tag: Ramparts built")
            Progress.INVENTOR -> {
                assertEquals(was.hexNumber[a.b], ck.hexNumber[a.c], "$tag: numbers swapped")
                assertEquals(was.hexNumber[a.c], ck.hexNumber[a.b], "$tag: numbers swapped")
                assertEquals(ck.hexNumber[a.b], s.numberAt(a.b), "$tag: the board shows the new number")
            }
            Progress.IRRIGATION -> assertTrue(s.card(0, Resource.WHEAT) >= b.card(0, Resource.WHEAT), "$tag: grain gained")
            Progress.MINING -> assertTrue(s.card(0, Resource.ORE) >= b.card(0, Resource.ORE), "$tag: stone gained")
            Progress.MEDICINE -> assertEquals(Piece.CITY, s.vertexBuilding[a.b], "$tag: town built")
            Progress.ROAD_BUILDING -> assertEquals(minOf(2, Rules.piecesLeft(b, 0, Piece.ROAD)), s.freeRoads, "$tag: two free roads")
            Progress.SMITH -> assertEquals(2, ck.freePromotions, "$tag: two free promotions")
            Progress.COMMERCIAL_HARBOR -> assertEquals(b.cardCount(0), s.cardCount(0), "$tag: swaps keep your card count")
            Progress.MASTER_MERCHANT -> assertEquals(b.cardCount(0) + minOf(2, b.cardCount(a.b)), s.cardCount(0), "$tag: took up to 2 cards")
            Progress.MERCHANT -> { assertEquals(a.b, ck.merchantHex, tag); assertEquals(0, ck.merchantOwner, tag) }
            Progress.MERCHANT_FLEET -> assertTrue(ck.fleetMask and (1 shl a.b) != 0, "$tag: 2:1 for the turn")
            Progress.RESOURCE_MONOPOLY, Progress.TRADE_MONOPOLY -> {
                val most = if (card == Progress.RESOURCE_MONOPOLY) 2 else 1
                var expect = b.card(0, a.b)
                for (q in 1 until s.numPlayers) {
                    val take = minOf(most, b.card(q, a.b))
                    expect += take
                    assertEquals(b.card(q, a.b) - take, s.card(q, a.b), "$tag: player $q handed over")
                }
                assertEquals(expect, s.card(0, a.b), "$tag: all of it came to you")
            }
            Progress.BISHOP -> assertEquals(a.b, s.robberHex, "$tag: bandit moved")
            Progress.DESERTER -> assertEquals(knights(b, a.b) - 1, knights(s, a.b), "$tag: a knight deserted")
            Progress.DIPLOMAT -> assertTrue(s.edgeOwner[a.b] < 0 || b.edgeOwner[a.b] == 0, "$tag: road removed")
            Progress.INTRIGUE -> assertTrue(ck.knightOwner[a.b] != was.knightOwner[a.b] || s.phase != Phase.PLAY_TURN, "$tag: knight chased off")
            Progress.SABOTEUR -> {
                val mine = b.victoryPoints(0)
                for (q in 1 until s.numPlayers) if (b.victoryPoints(q) >= mine)
                    assertEquals(b.cardCount(q) - b.cardCount(q) / 2, s.cardCount(q), "$tag: player $q lost half")
            }
            Progress.SPY -> {
                // You give up the Spy and take one of the target's progress cards (Printer and Constitution are never held).
                val took = if (was.progressCount(a.b) > 0) 1 else 0
                assertEquals(was.progressCount(0) - 1 + took, ck.progressCount(0), "$tag: a card taken")
                assertEquals(was.progressCount(a.b) - took, ck.progressCount(a.b), "$tag: the target lost it")
            }
            Progress.WARLORD -> for (v in 0 until s.board.vertexCount) if (ck.knightOwner[v] == 0) assertTrue(ck.isActive(v), "$tag: knight $v active")
            Progress.WEDDING -> assertTrue(s.cardCount(0) >= b.cardCount(0), "$tag: gifts received")
            else -> throw AssertionError("$tag: no check for ${Progress.NAMES[card]}")
        }
    }

    @Test
    fun `every progress card works and the turn goes on`() {
        val after = positions(beforeRoll = false)
        val before = positions(beforeRoll = true)
        assertTrue(after.size >= 20 && before.size >= 20, "enough positions: ${after.size} after, ${before.size} before the roll")
        for (card in 0 until Progress.COUNT) {
            if (card == Progress.PRINTER || card == Progress.CONSTITUTION) continue
            for ((i, start) in (if (card == Progress.ALCHEMIST) before else after).withIndex()) {
                val base = start.copy()
                // Plenty to pay with (Medicine costs 2 stone and a grain) and a card to play.
                for (r in 0 until CARD_TYPES) CK.give(base, 0, r, minOf(3, base.bank[r]))
                if (base.ck!!.deckSize[Progress.track(card)] == 0 || base.ck!!.progressHand[card] > 0) continue
                if (runCatching { CK.giveProgress(base, 0, card) }.isFailure) continue // every copy is in a hand
                val plays = Rules.legalActions(base).filter { it.type == CkActionType.PLAY_PROGRESS && it.a == card }
                for (a in plays.take(6)) {
                    val tag = "${Progress.NAMES[card]} position $i $a"
                    val s = base.copy()
                    val held = s.ck!!.progressHand[card]
                    Rules.applyChecked(s, a)
                    settle(s, tag)
                    if (s.isOver) continue
                    effect(card, a, base, s, tag)
                    assertEquals(0, s.currentPlayer, "$tag: still my turn")
                    assertEquals(0, s.actingPlayer, "$tag: I act")
                    // Free roads (road building, Diplomat on your own road) are placed before the turn can end.
                    var roads = 0
                    while (s.freeRoads > 0 && roads++ < 3) Rules.applyChecked(s, Rules.legalActions(s).first { it.type == ActionType.BUILD_ROAD })
                    // The Smith's free promotions end with Done.
                    if (Action.CK_DONE in Rules.legalActions(s)) Rules.applyChecked(s, Action.CK_DONE)
                    val legal = Rules.legalActions(s)
                    assertFalse(legal.any { it.type == ActionType.ROLL }, "$tag: no second roll")
                    assertTrue(Action.END_TURN in legal, "$tag: I end my own turn")
                    // The Transmuter can draw its twin on the roll, the Spy can steal one.
                    if (card != Progress.ALCHEMIST && card != Progress.SPY) assertEquals(held - 1, s.ck!!.progressHand[card], "$tag: the card is used up")
                    CK.checkInvariants(s)
                    played[card]++
                }
            }
        }
        for (card in 0 until Progress.COUNT) {
            if (card == Progress.PRINTER || card == Progress.CONSTITUTION) continue
            assertTrue(played[card] > 0, "${Progress.NAMES[card]} was never playable in ${after.size} positions")
        }
    }
}
