package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.ai.GreedyBot
import wgbg.engine.Action
import wgbg.engine.ActionBuffer
import wgbg.engine.ActionType
import wgbg.engine.CARD_TYPES
import wgbg.engine.GameConfig
import wgbg.engine.GameState
import wgbg.engine.Phase
import wgbg.engine.Resource
import wgbg.engine.Rules

/** The trade panel's logic: which mode it is in, how terms are mirrored, and counters. */
class TradePanelTest {
    private fun cards(vararg rc: Pair<Int, Int>) = IntArray(CARD_TYPES).also { for ((r, n) in rc) it[r] = n }

    /** Plays bots until [me] has rolled on their own turn (and holds cards), or [stop] says so. */
    private fun until(seed: Long, stop: (GameState) -> Boolean): GameState {
        val s = Rules.newGame(GameConfig(numPlayers = 4), seed)
        val bots = List(4) { GreedyBot(it.toLong()) }
        val buf = ActionBuffer()
        while (!s.isOver && !stop(s)) {
            Rules.legalActions(s, buf)
            Rules.apply(s, bots[s.actingPlayer].choose(s, buf))
        }
        return s
    }

    private fun myRolledTurn(s: GameState) =
        s.phase == Phase.PLAY_TURN && s.currentPlayer == 0 && s.hasRolled && s.handSize(0) >= 2 && s.handSize(1) >= 1

    @Test
    fun `modes follow the trade from compose to respond to counter`() {
        val s = until(3, ::myRolledTurn)
        assertEquals(TradeMode.COMPOSE, tradeMode(s, 0))
        assertEquals(TradeMode.CLOSED, tradeMode(s, 1))
        assertFalse(tradeNeedsMe(s, 0))

        // Offer one of our cards for something player 1 holds.
        val give = (0 until CARD_TYPES).first { s.hand(0, it) > 0 }
        val want = (0 until CARD_TYPES).first { it != give && s.hand(1, it) > 0 }
        Rules.applyChecked(s, Action.offerTrade(cards(give to 1), cards(want to 1)))
        assertEquals(TradeMode.RESPOND, tradeMode(s, 1))
        assertTrue(tradeNeedsMe(s, 1))
        assertEquals(TradeMode.WAITING, tradeMode(s, 0))

        // Seen by the responder, the terms are mirrored: they get what we give.
        val (get1, give1) = termsFor(s, 1)
        assertEquals(1, get1[give]); assertEquals(1, give1[want])
        val (get0, give0) = termsFor(s, 0)
        assertEquals(1, get0[want]); assertEquals(1, give0[give])

        // Player 1 counters: "I give the same, but I want 2". Only if we have 2.
        if (s.hand(0, give) >= 2) {
            val c = counterFor(get = cards(give to 2), give = cards(want to 1))
            assertTrue(Rules.isLegal(s, c), "$c")
            Rules.applyChecked(s, c)
            assertEquals(TradeMode.REVIEW_COUNTER, tradeMode(s, 0))
            val (g, gv) = termsFor(s, 0) // the counter replaces the offer for the offerer
            assertEquals(1, g[want]); assertEquals(2, gv[give])
            Rules.applyChecked(s, Action.REJECT_TRADE)
            assertEquals(Phase.DECIDE_TRADE, s.phase)
            assertEquals(2, s.actingPlayer)
        }
    }

    @Test
    fun `counterFor writes the counter from the offerer's side`() {
        val c = counterFor(get = cards(Resource.WOOD to 2), give = cards(Resource.ORE to 1))
        assertEquals(ActionType.COUNTER_TRADE, c.type)
        assertEquals(2, c.offerGive(Resource.WOOD)) // the offerer gives us 2 timber
        assertEquals(1, c.offerWant(Resource.ORE)) // and gets our stone
    }

    @Test
    fun `a free road to place closes trading with the real reason, not that it is not your turn`() {
        // Mauro's playtest: Envoy on his own road gave a road to place again; the chest said it wasn't his turn.
        val s = until(3, ::myRolledTurn)
        assertEquals(TradeMode.COMPOSE, tradeMode(s, 0))
        s.freeRoads = 1
        assertEquals(TradeMode.CLOSED, tradeMode(s, 0))
        assertEquals("Place your free road first, then trade", tradeClosedReason(s, 0))
        assertEquals("Trade on your turn", tradeClosedReason(s, 1))
        assertTrue(prompt(s, Rules.legalActions(s), listOf("You", "Blue", "Orange", "White")).startsWith("Place your free road first"))
        s.freeRoads = 0
        assertEquals(TradeMode.COMPOSE, tradeMode(s, 0))
    }

    @Test
    fun `in Age of Chivalry, after any progress card your own turn never says it is not your turn, and trading comes back`() {
        // Mauro wasn't sure which action card it was, so every card bots play in 40 games is covered.
        var checked = 0; var cardsPlayed = 0
        for (seed in 1L..40L) {
            val s = Rules.newGame(GameConfig(expansion = wgbg.engine.Expansion.CITIES_AND_KNIGHTS, numPlayers = 4), seed)
            val bots = List(4) { GreedyBot(it.toLong() + seed) }
            val buf = ActionBuffer()
            var afterCard = false
            while (!s.isOver && s.turn < 400) {
                val p = s.currentPlayer
                if (s.hasRolled && s.actingPlayer == p) {
                    val mode = tradeMode(s, p)
                    if (mode == TradeMode.CLOSED) assertTrue(tradeClosedReason(s, p) != "Trade on your turn", "seed $seed turn ${s.turn} phase ${s.phase}")
                    if (s.phase == Phase.PLAY_TURN && s.freeRoads == 0) {
                        assertEquals(TradeMode.COMPOSE, mode, "seed $seed turn ${s.turn}")
                        if (afterCard) { checked++; afterCard = false }
                    }
                }
                Rules.legalActions(s, buf)
                val a = bots[s.actingPlayer].choose(s, buf)
                if (a.type == wgbg.engine.CkActionType.PLAY_PROGRESS && s.actingPlayer == s.currentPlayer && s.hasRolled) { afterCard = true; cardsPlayed++ }
                Rules.apply(s, a)
            }
        }
        assertTrue(cardsPlayed > 50 && checked > 50, "cards $cardsPlayed, back to trading $checked")
    }

    @Test
    fun `every pick-a-card moment says what it is for`() {
        val seen = mutableSetOf<Phase>()
        for (seed in 1L..40L) {
            val s = Rules.newGame(GameConfig(expansion = wgbg.engine.Expansion.CITIES_AND_KNIGHTS, numPlayers = 4), seed)
            val bots = List(4) { GreedyBot(it.toLong() + seed) }
            val buf = ActionBuffer()
            while (!s.isOver && s.turn < 400) {
                Rules.legalActions(s, buf)
                if (Rules.legalActions(s).any { it.type == wgbg.engine.CkActionType.CHOOSE_CARD }) {
                    assertTrue(cardChoiceTitle(s, listOf("You", "Blue", "Orange", "White")) != "Choose a card", "${s.phase}")
                    seen += s.phase
                }
                Rules.apply(s, bots[s.actingPlayer].choose(s, buf))
            }
        }
        assertTrue(Phase.AQUEDUCT in seen, "science bonus seen: $seen")
    }

    @Test
    fun `before rolling the panel is closed`() {
        val s = until(5) { it.phase == Phase.PLAY_TURN && it.currentPlayer == 0 && !it.hasRolled }
        assertEquals(TradeMode.CLOSED, tradeMode(s, 0))
    }

    @Test
    fun `no Trade button while placing the first homesteads and roads`() {
        val s = Rules.newGame(GameConfig(numPlayers = 4), 1)
        assertFalse(tradeButtonVisible(s), "setup: first homestead")
        val after = until(1) { it.phase == Phase.PLAY_TURN }
        assertTrue(tradeButtonVisible(after), "visible from the first real turn")
    }

    @Test
    fun `bank rates follow ports`() {
        val s = Rules.newGame(GameConfig(numPlayers = 4), 1)
        assertEquals(4, bankRate(s, 0, Resource.WOOD))
    }

    @Test
    fun `Age of Chivalry trades cover the commodities, mirror terms, and send the C&K offer`() {
        val s = Rules.newGame(GameConfig(expansion = wgbg.engine.Expansion.CITIES_AND_KNIGHTS, numPlayers = 4), 3)
        assertEquals(8, tradeCards(s))
        // Give seat 0 a Parchment (card 5) and seat 1 a Timber, then offer one for the other.
        s.ck!!.commodities[0 * wgbg.engine.Commodity.COUNT + 0] += 1   // seat 0: a Parchment (card 5)
        s.hands[1 * wgbg.engine.CARD_TYPES + 0] += 1                    // seat 1: a Timber
        val give = IntArray(8).also { it[5] = 1 }
        val get = IntArray(8).also { it[0] = 1 }
        val offer = offerFor(s, give, get)!!
        assertEquals(wgbg.engine.CkActionType.OFFER_TRADE_CK, offer.type)
        assertEquals(1, holding(s, 0, 5))
        // Pretend it's our turn after rolling, so the offer applies.
        s.phase = Phase.PLAY_TURN; s.hasRolled = true; s.currentPlayer = 0; s.actingPlayer = 0
        Rules.apply(s, offer)
        val (get1, give1) = termsFor(s, 1)
        assertEquals(1, get1[5], "the responder gets the Parchment")
        assertEquals(1, give1[0], "and gives the Timber")
    }

    @Test
    fun `trade buttons say what they do - Offer and Clear for your own offer, Yes and No only when answering`() {
        assertEquals("Offer" to "Close", tradeButtonLabels(TradeMode.COMPOSE, countering = false, bank = false, entered = false))
        assertEquals("Offer" to "Clear", tradeButtonLabels(TradeMode.COMPOSE, countering = false, bank = false, entered = true))
        assertEquals("Trade" to "Clear", tradeButtonLabels(TradeMode.COMPOSE, countering = false, bank = true, entered = true))
        assertEquals("Yes" to "No", tradeButtonLabels(TradeMode.RESPOND, countering = false, bank = false, entered = false))
        assertEquals("Send" to "Back", tradeButtonLabels(TradeMode.RESPOND, countering = true, bank = false, entered = true))
        assertEquals("Yes" to "No", tradeButtonLabels(TradeMode.REVIEW_COUNTER, countering = false, bank = false, entered = false))
        assertEquals("Trade" to "Cancel", tradeButtonLabels(TradeMode.PICK_PARTNER, countering = false, bank = false, entered = false))
    }

    @Test
    fun `one row of cards - top asks, bottom offers, the other half switches, hold takes one back`() {
        val z = IntArray(5)
        var (g, v) = askOffer(z, z, 0, CardTap.ASK, have = 0)
        assertEquals(1, g[0]); assertEquals(0, v[0])
        askOffer(g, v, 0, CardTap.ASK, have = 0).let { g = it.first; v = it.second }
        assertEquals(2, g[0])
        askOffer(g, v, 0, CardTap.OFFER, have = 3).let { g = it.first; v = it.second }   // switch over
        assertEquals(0, g[0]); assertEquals(1, v[0])
        askOffer(g, v, 0, CardTap.OFFER, have = 1).let { g = it.first; v = it.second }   // can't offer more than held
        assertEquals(1, v[0])
        assertEquals(0, askOffer(z, z, 2, CardTap.OFFER, have = 0).second[2])              // nothing to offer
        askOffer(g, v, 0, CardTap.TAKE_BACK, have = 1).let { g = it.first; v = it.second }
        assertEquals(0, v[0]); assertEquals(0, g[0])
        for (i in 0..20) g = askOffer(g, v, 1, CardTap.ASK, have = 0).first
        assertEquals(9, g[1])                                                             // asks stop at 9
    }

    @Test
    fun `with the bank it is one card each way at your rate`() {
        val z = IntArray(5)
        var (g, v) = askOffer(z, z, 3, CardTap.ASK, have = 0, bankRate = 4)
        askOffer(g, v, 1, CardTap.OFFER, have = 4, bankRate = 4).let { g = it.first; v = it.second }
        assertEquals(1, g[3]); assertEquals(4, v[1])
        askOffer(g, v, 2, CardTap.ASK, have = 0, bankRate = 4).let { g = it.first; v = it.second }   // pick another
        assertEquals(0, g[3]); assertEquals(1, g[2])
        assertEquals(0, askOffer(g, z, 0, CardTap.OFFER, have = 3, bankRate = 4).second[0])           // can't pay 4 with 3
    }

    @Test
    fun `an offer chimes when you can pay it and counts down to No when you can't`() {
        val s = wgbg.engine.Rules.newGame(wgbg.engine.GameConfig(numPlayers = 3), 4)
        val buf = wgbg.engine.ActionBuffer()
        val bots = List(3) { wgbg.ai.GreedyBot(it.toLong()) }
        while (s.phase != wgbg.engine.Phase.PLAY_TURN) { wgbg.engine.Rules.legalActions(s, buf); wgbg.engine.Rules.apply(s, bots[s.actingPlayer].choose(s, buf)) }
        wgbg.engine.Rules.apply(s, wgbg.engine.Action.roll(2, 2))
        val from = s.currentPlayer; val to = (from + 1) % 3
        for (r in 0 until 5) { s.hands[from * wgbg.engine.CARD_TYPES + r] = 2; s.hands[to * wgbg.engine.CARD_TYPES + r] = 0 }
        s.hands[to * wgbg.engine.CARD_TYPES + 4] = 1                      // the other player holds 1 Stone only
        val offer = offerFor(s, IntArray(5).also { it[0] = 1 }, IntArray(5).also { it[4] = 1 })!!   // 1 Timber for their 1 Stone
        wgbg.engine.Rules.apply(s, offer)
        while (s.actingPlayer != to) { wgbg.engine.Rules.apply(s, wgbg.engine.Action.REJECT_TRADE) }
        assertEquals(TradeMode.RESPOND, tradeMode(s, to))
        assertTrue(canPayOffer(s, to))
        s.hands[to * wgbg.engine.CARD_TYPES + 4] = 0
        assertFalse(canPayOffer(s, to))
    }
}
