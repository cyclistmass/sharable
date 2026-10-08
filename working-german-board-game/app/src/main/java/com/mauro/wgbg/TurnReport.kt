package com.mauro.wgbg

import wgbg.engine.Action
import wgbg.engine.ActionType
import wgbg.engine.CARD_TYPES
import wgbg.engine.GameState
import wgbg.engine.Resource

/** Our names for the five resources, in engine order (WOOD, BRICK, SHEEP, WHEAT, ORE). */
val RESOURCE_NAMES = listOf("Timber", "Clay", "Fleece", "Grain", "Stone")

/** What happened in one player's turn: the roll, who got what, and what they did. */
data class TurnReport(
    val player: Int,
    val turn: Int,
    val die1: Int = 0,
    val die2: Int = 0,
    /** Per-player resource changes from the roll (production), or empty on a 7. */
    val production: List<String> = emptyList(),
    val events: List<String> = emptyList(),
) {
    val rolled get() = die1 > 0
}

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
        var d1 = 0
        var d2 = 0
        if (action.type == ActionType.ROLL) {
            // The roll is the first thing that uses the RNG, so a copy shows the dice it will draw.
            val peek = s.rng.copy()
            d1 = peek.nextInt(6) + 1
            d2 = peek.nextInt(6) + 1
        }
        applyFn()
        val gained = Array(s.numPlayers) { p -> IntArray(CARD_TYPES) { r -> s.hands[p * CARD_TYPES + r] - before[p * CARD_TYPES + r] } }
        val r = current!!
        val n = names[who]
        current = when (action.type) {
            ActionType.ROLL -> r.copy(
                die1 = d1, die2 = d2,
                production = if (d1 + d2 == 7) listOf("Bandit! Players over the limit must discard.")
                else (0 until s.numPlayers).mapNotNull { p -> changes(gained[p])?.let { "${names[p]} $it" } }
                    .ifEmpty { listOf("Nobody produced anything.") },
            )
            ActionType.END_TURN, ActionType.REJECT_TRADE -> r
            ActionType.MOVE_ROBBER -> {
                val victim = action.robberVictim
                r.plus(if (victim >= 0) "$n moved the bandit and took a card from ${names[victim]}" else "$n moved the bandit")
            }
            ActionType.DISCARD -> r.plus("$n discarded " + gained[who].withIndex().filter { it.value < 0 }.joinToString(", ") { "${-it.value} ${RESOURCE_NAMES[it.index]}" })
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
