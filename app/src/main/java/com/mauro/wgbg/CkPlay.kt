package com.mauro.wgbg

import wgbg.engine.Action
import wgbg.engine.ActionKind
import wgbg.engine.CkActionType
import wgbg.engine.GameState
import wgbg.engine.Progress
import wgbg.engine.Track

// ------------------------------------------------------------------ Age of Chivalry: our names and labels (#4)

/** Improvement tracks / progress decks by engine track index: Guilds (trade), Crown (politics), Academy (science). */
val DECK_NAMES = listOf("Guilds", "Crown", "Academy")

/** Progress cards in engine order, with our own names (never the original game's). */
val PROGRESS_NAMES = listOf(
    // Academy
    "Transmuter", "Hoist", "Master Mason", "Tinkerer", "Waterworks", "Healer", "Deep Shafts", "Printing Press", "Pathfinders", "Forge",
    // Guilds
    "Harbor Charter", "Grand Factor", "Peddler", "Swift Hulls", "Corner the Field", "Corner the Wares",
    // Crown
    "Abbot", "Charter of Rights", "Turncoat", "Envoy", "Plot", "Wrecker", "Informer", "Marshal", "Feast",
)

/** C&K actions that are a tap on a board corner, and the corner each one means. */
val CK_VERTEX_KINDS: Set<ActionKind> = setOf(
    CkActionType.BUILD_KNIGHT, CkActionType.ACTIVATE_KNIGHT, CkActionType.PROMOTE_KNIGHT, CkActionType.MOVE_KNIGHT,
    CkActionType.DISPLACE_KNIGHT, CkActionType.CHASE_ROBBER, CkActionType.RELOCATE_KNIGHT, CkActionType.BUILD_CITY_WALL,
    CkActionType.CHOOSE_VERTEX,
)

/** The corner a C&K board action points at (a move's destination), or -1. */
fun ckVertex(a: Action): Int = when (a.type) {
    CkActionType.MOVE_KNIGHT, CkActionType.DISPLACE_KNIGHT -> a.b
    in CK_VERTEX_KINDS -> a.payload.toInt()
    else -> -1
}

private fun card(i: Int) = ALL_CARD_NAMES.getOrElse(i) { "card $i" }
private fun progress(i: Int) = PROGRESS_NAMES.getOrElse(i) { "card $i" }
private fun deck(t: Int) = DECK_NAMES.getOrElse(t) { "deck $t" }

/** A short button / confirm label for a C&K action, in our names; null for non-C&K actions. */
fun ckLabel(s: GameState, a: Action, names: List<String>): String? = when (a.type) {
    CkActionType.BUILD_KNIGHT -> "Raise a knight here"
    CkActionType.ACTIVATE_KNIGHT -> "Rouse this knight"
    CkActionType.PROMOTE_KNIGHT -> "Promote this knight"
    CkActionType.MOVE_KNIGHT -> "Move the knight here"
    CkActionType.DISPLACE_KNIGHT -> "Charge and displace the knight here"
    CkActionType.CHASE_ROBBER -> "Chase the bandit off"
    CkActionType.RELOCATE_KNIGHT -> "Set your knight down here"
    CkActionType.BUILD_CITY_WALL -> "Build Ramparts here"
    CkActionType.CHOOSE_VERTEX -> "Choose this corner"
    CkActionType.IMPROVE_CITY -> "Improve ${deck(a.a)}" + if (a.b > 0) " (claim the Capital)" else ""
    CkActionType.PLAY_PROGRESS -> "Play ${progress(a.a)}"
    CkActionType.DISCARD_PROGRESS -> "Discard ${progress(a.a)}"
    CkActionType.TAKE_PROGRESS -> "Take ${progress(a.a)}"
    CkActionType.CHOOSE_CARD -> "Choose ${card(a.a)}"
    CkActionType.CHOOSE_PROGRESS_DECK -> "Draw from ${deck(a.a)}"
    CkActionType.COMMERCIAL_HARBOR_OFFER -> "Offer ${card(a.a)} to ${names.getOrElse(a.b) { "player ${a.b}" }}"
    CkActionType.CK_DONE -> "Done"
    CkActionType.OFFER_TRADE_CK -> "Offer this trade"
    else -> null
}

/** Detail for one choice of a progress card with several ways to play it (its arguments). */
fun progressOption(a: Action): String {
    val c = a.a
    return when (c) {
        Progress.RESOURCE_MONOPOLY, Progress.TRADE_MONOPOLY, Progress.MERCHANT_FLEET -> card(a.b)
        else -> if (a.c != 0) "${a.b} / ${a.c}" else "${a.b}"
    }
}

/** Button groups for the non-board C&K moves: one entry per kind (and per card), each with its actions. */
fun ckButtonGroups(s: GameState, legal: List<Action>, names: List<String>): List<Pair<String, List<Action>>> {
    val groups = legal.filter { it.type is CkActionType && it.type !in CK_VERTEX_KINDS && it.type != CkActionType.OFFER_TRADE_CK }
        .groupBy { if (it.type == CkActionType.PLAY_PROGRESS) "Play ${progress(it.a)}" else ckLabel(s, it, names) ?: it.toString() }
        .map { (label, actions) -> label to actions }
    return groups // bank and player trades go through the trade chest
}

/** Label for one bank trade choice (card indices may be commodities). */
fun bankLabel(a: Action) = "Give ${card(a.maritimeGive)} for ${card(a.maritimeGet)}"

/** The prompt line for a C&K turn, by what the legal moves ask for. */
fun ckPrompt(s: GameState, legal: List<Action>): String? {
    val kinds = legal.map { it.type }.toSet()
    return when {
        CkActionType.CHOOSE_PROGRESS_DECK in kinds -> "Raiders beaten: draw a progress card from a deck"
        CkActionType.DISCARD_PROGRESS in kinds -> "Too many progress cards: discard one"
        CkActionType.CHOOSE_CARD in kinds -> "Choose a card"
        CkActionType.RELOCATE_KNIGHT in kinds -> "Tap a glowing corner to set your knight down"
        CkActionType.CHOOSE_VERTEX in kinds -> "Tap a glowing corner"
        else -> null
    }
}

/** Track of the improvement an IMPROVE_CITY action raises (for colouring its button). */
fun improveTrack(a: Action) = if (a.type == CkActionType.IMPROVE_CITY) a.a else -1

@Suppress("unused")
private val TRACK_ORDER_CHECK = listOf(Track.TRADE, Track.POLITICS, Track.SCIENCE) // DECK_NAMES follows this order
