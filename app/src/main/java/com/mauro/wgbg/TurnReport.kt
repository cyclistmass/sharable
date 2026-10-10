package com.mauro.wgbg

import wgbg.engine.Action
import wgbg.engine.ActionType
import wgbg.engine.CARD_TYPES
import wgbg.engine.GameState
import wgbg.engine.Phase
import wgbg.engine.Resource

/** Our names for the five resources, in engine order (WOOD, BRICK, SHEEP, WHEAT, ORE). */
val RESOURCE_NAMES = listOf("Timber", "Clay", "Fleece", "Grain", "Stone")
/** Every card a hand can hold, by engine card index: the resources, then the Age of Chivalry commodities. */
val ALL_CARD_NAMES = RESOURCE_NAMES + listOf("Parchment", "Weave", "Silver")

/** What happened in one player's turn: the roll, who got what, and what they did. */
data class TurnReport(
    val player: Int,
    val turn: Int,
    val die1: Int = 0,
    val die2: Int = 0,
    /** Age of Chivalry event die face 0..5 (0-2 the Raiders' ship, 3-5 a gate: trade, politics, science), -1 if none. */
    val event: Int = -1,
    /** Per-player resource changes from the roll (production), or empty on a 7. */
    val production: List<String> = emptyList(),
    val events: List<String> = emptyList(),
    /** Cards each player got from the roll, by player then resource (#47). */
    val rollIn: List<IntArray> = emptyList(),
    /** Cards each player discarded on a 7 (public: they go back to the bank face up). */
    val rollOut: List<IntArray> = emptyList(),
    /** The bandit's robbery this turn, if any (which card is secret but to the two players). */
    val steal: Steal? = null,
) {
    val rolled get() = die1 > 0
}

/** [thief] took one [card] from [victim]. */
data class Steal(val thief: Int, val victim: Int, val card: Int)

/**
 * Watches every action applied to a game and builds [TurnReport]s. Call [apply] instead of
 * `Rules.apply` so it can diff hands around each action.
 */
class TurnTracker(private val names: List<String>) {
    var current: TurnReport? = null
        private set
    var last: TurnReport? = null
        private set

    fun apply(s: GameState, who: Int, action: Action, applyFn: () -> Unit) {
        if (current == null || current!!.player != s.currentPlayer || current!!.turn != s.turn) {
            current?.let { if (it.rolled || it.events.isNotEmpty()) last = it }
            current = TurnReport(s.currentPlayer, s.turn)
        }
        val before = s.hands.copyOf()
        val phaseBefore = s.phase
        val counterer = s.counterFrom
        var d1 = 0
        var d2 = 0
        var ev = -1
        if (action.type == ActionType.ROLL && action.payload != 0L) {
            d1 = action.a; d2 = action.b // dice thrown outside the engine travel in the action
            ev = ((action.payload ushr 16) and 0xff).toInt() - 1
        } else if (action.type == ActionType.ROLL) {
            // The roll is the first thing that uses the RNG, so a copy shows the dice it will draw.
            val peek = s.rng.copy()
            d1 = peek.nextInt(6) + 1
            d2 = peek.nextInt(6) + 1
            if (s.ck != null) ev = peek.nextInt(6)
        }
        applyFn()
        val gained = Array(s.numPlayers) { p -> IntArray(CARD_TYPES) { r -> s.hands[p * CARD_TYPES + r] - before[p * CARD_TYPES + r] } }
        val r = current!!
        val n = names[who]
        current = when (action.type) {
            ActionType.ROLL -> r.copy(
                die1 = d1, die2 = d2, event = ev,
                rollIn = gained.map { g -> IntArray(g.size) { maxOf(0, g[it]) } },
                rollOut = List(s.numPlayers) { IntArray(CARD_TYPES) },
                production = if (d1 + d2 == 7) listOf("Bandit! Players over the limit must discard.")
                else (0 until s.numPlayers).mapNotNull { p -> changes(gained[p])?.let { "${names[p]} $it" } }
                    .ifEmpty { listOf("Nobody produced anything.") },
            )
            ActionType.END_TURN -> r
            ActionType.REJECT_TRADE, ActionType.COUNTER_TRADE -> r
            ActionType.ACCEPT_TRADE ->
                if (phaseBefore == Phase.DECIDE_COUNTER) r.plus("$n traded with ${names[counterer]}: ${tradeText(gained[who])}") else r
            ActionType.MOVE_ROBBER -> {
                val victim = action.robberVictim
                val card = if (victim >= 0) gained[victim].indexOfFirst { it < 0 } else -1
                r.plus(if (victim >= 0) "$n moved the bandit and took a card from ${names[victim]}" else "$n moved the bandit")
                    .let { if (victim >= 0 && card >= 0) it.copy(steal = Steal(who, victim, card)) else it }
            }
            ActionType.DISCARD -> r.plus("$n discarded " + gained[who].withIndex().filter { it.value < 0 }.joinToString(", ") { "${-it.value} ${RESOURCE_NAMES[it.index]}" })
                .let { rep -> if (rep.rollOut.size > who) rep.copy(rollOut = rep.rollOut.mapIndexed { p, out -> if (p != who) out else IntArray(out.size) { out[it] + maxOf(0, -gained[who][it]) } }) else rep }
            ActionType.MARITIME_TRADE -> r.plus("$n traded with the bank: ${tradeText(gained[who])}")
            ActionType.OFFER_TRADE -> r.plus("$n offered a trade")
            ActionType.CONFIRM_TRADE -> {
                val partner = action.payload.toInt()
                r.plus("$n traded with ${names[partner]}: ${tradeText(gained[who])}")
            }
            ActionType.CANCEL_TRADE -> r.plus("$n's trade offer found no takers")
            ActionType.PLAY_YEAR_OF_PLENTY, ActionType.PLAY_MONOPOLY ->
                r.plus("$n played ${describe(action.type)} and got ${changes(gained[who]) ?: "nothing"}")
            else -> r.plus("$n ${describe(action.type)}")
        }
    }

    private fun TurnReport.plus(e: String) = copy(events = (events + e).takeLast(6))

    /** "+2 Timber, +1 Grain" for the positive entries, null if none. */
    private fun changes(delta: IntArray): String? =
        delta.withIndex().filter { it.value > 0 }.joinToString(", ") { "+${it.value} ${RESOURCE_NAMES[it.index]}" }.ifEmpty { null }

    private fun tradeText(delta: IntArray): String {
        val gave = delta.withIndex().filter { it.value < 0 }.joinToString(", ") { "${-it.value} ${RESOURCE_NAMES[it.index]}" }
        val got = delta.withIndex().filter { it.value > 0 }.joinToString(", ") { "${it.value} ${RESOURCE_NAMES[it.index]}" }
        return "gave $gave, got $got"
    }

    companion object {
        init { check(Resource.COUNT == RESOURCE_NAMES.size) }
    }
}
