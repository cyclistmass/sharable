package com.mauro.wgbg

import wgbg.engine.DiceDeck
import wgbg.engine.DiceMode
import wgbg.engine.DiceSource
import wgbg.engine.Expansion
import wgbg.engine.GameConfig

// ------------------------------------------------------------------ new-game settings (UI-free, unit tested)

/** Who sits in a seat: a friend on another phone, a bot, or nobody. */
enum class SeatKind(val label: String) { FRIEND("Friend"), BOT("Bot"), EMPTY("Empty") }

/** Bot strength, weakest first. STRONG is the trained bot (#6); until it lands it plays as Greedy. */
enum class BotLevel(val label: String) { EASY("Easy"), GREEDY("Greedy"), STRONG("Strong") }

data class SeatSetup(val kind: SeatKind, val bot: BotLevel = BotLevel.GREEDY)

/** Rulesets the screen offers, with our names (never the original game's). */
enum class Ruleset(val label: String, val expansion: Expansion, val defaultPoints: Int, val players: IntRange) {
    BASE("Base game", Expansion.BASE, 10, 2..4),
    CHIVALRY("Age of Chivalry", Expansion.CITIES_AND_KNIGHTS, 13, 3..4),
}

/**
 * Everything the game-start screen (#3) decides. Seat 0 is always you; seats 1-3 are
 * Friend/Bot/Empty. [decks] is 0 for true-random dice, or 1-4 stacked 36-card dice decks.
 */
data class GameSetup(
    val name: String = "You",
    /** content:// URI of the chosen photo, or null for the plain colored token. */
    val photo: String? = null,
    val seats: List<SeatSetup> = listOf(
        SeatSetup(SeatKind.FRIEND), SeatSetup(SeatKind.BOT), SeatSetup(SeatKind.BOT), SeatSetup(SeatKind.BOT),
    ),
    val decks: Int = 0,
    val ruleset: Ruleset = Ruleset.BASE,
    val points: Int = ruleset.defaultPoints,
) {
    init {
        require(seats.size == MAX_SEATS) { "always $MAX_SEATS seats (empty ones are EMPTY)" }
    }

    /** Seats that will play, in seat order (empty seats drop out). */
    val players: List<Int> get() = seats.indices.filter { it == 0 || seats[it].kind != SeatKind.EMPTY }

    val hasFriends get() = seats.drop(1).any { it.kind == SeatKind.FRIEND }

    /** What stops Start, as text for the screen; empty means ready. */
    val problems: List<String>
        get() = buildList {
            if (name.isBlank()) add("Enter your name")
            if (players.size !in ruleset.players) add("${ruleset.label} needs ${ruleset.players.first}-${ruleset.players.last} players")
            if (points !in POINTS_RANGE) add("Points must be ${POINTS_RANGE.first}-${POINTS_RANGE.last}")
            if (decks !in 0..MAX_DECKS) add("Dice: random or 1-$MAX_DECKS decks")
        }

    val canStart get() = problems.isEmpty()

    fun config(): GameConfig = GameConfig(expansion = ruleset.expansion, numPlayers = players.size, vpToWin = points)

    fun dice(): DiceSource = if (decks == 0) DiceDeck.of(DiceMode.RANDOM) else DiceDeck.of(DiceMode.DECK, decks)

    /** Seat i becomes [kind]; seat 0 (you) never changes. */
    fun withSeat(i: Int, kind: SeatKind) = if (i == 0) this else copy(seats = seats.toMutableList().also { it[i] = it[i].copy(kind = kind) })

    fun withBot(i: Int, level: BotLevel) = if (i == 0) this else copy(seats = seats.toMutableList().also { it[i] = SeatSetup(SeatKind.BOT, level) })

    /** Switching ruleset moves points to its default unless the player had changed them. */
    fun withRuleset(r: Ruleset) = copy(ruleset = r, points = if (points == ruleset.defaultPoints) r.defaultPoints else points)

    fun withPoints(p: Int) = copy(points = p.coerceIn(POINTS_RANGE))

    fun withDecks(d: Int) = copy(decks = d.coerceIn(0, MAX_DECKS))

    companion object {
        const val MAX_SEATS = 4
        const val MAX_DECKS = 4
        val POINTS_RANGE = 5..20

        /** The dice choices in screen order: random, then 36/72/108/144-card decks. */
        val DICE_CHOICES = (0..MAX_DECKS).toList()

        fun diceLabel(decks: Int) = if (decks == 0) "Random" else "${36 * decks}-card deck"
    }
}
