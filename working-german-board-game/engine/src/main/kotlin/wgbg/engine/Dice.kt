package wgbg.engine

import java.security.SecureRandom

/** One throw: two production dice, plus the Age of Chivalry event die face (0..5) when it's used. */
data class DiceRoll(val d1: Int, val d2: Int, val event: Int = -1) {
    val sum get() = d1 + d2

    /** The ROLL action that records exactly these dice in the game log. */
    fun toAction() = Action.roll(d1, d2, event)
}

/** Where real games get their dice. AI lookahead keeps using the engine's fast seeded RNG instead. */
interface DiceSource {
    fun next(withEvent: Boolean = false): DiceRoll
}

/** RANDOM: true-random dice. DECK: pre-rolled stack of 1-4 decks of 36 (see [DiceDeck]). */
enum class DiceMode { RANDOM, DECK }

/**
 * Truly random dice from the operating system's cryptographic generator: on Android and Linux
 * the kernel CSPRNG, continuously reseeded from hardware noise (interrupt timing, CPU jitter,
 * and hardware RNGs where present). Nothing about past rolls predicts the next one, and
 * [SecureRandom.nextInt] uses rejection sampling, so each face has exactly 1/6 odds.
 */
class TrueRandomDice(private val rng: SecureRandom = SecureRandom()) : DiceSource {
    override fun next(withEvent: Boolean) =
        DiceRoll(rng.nextInt(6) + 1, rng.nextInt(6) + 1, if (withEvent) rng.nextInt(6) else -1)
}

/**
 * Pre-rolled dice deck: [decks] copies of the 36 cards (one per combination of the two dice),
 * shuffled together with the same secure generator. Over a full stack every sum comes up
 * exactly as often as the odds say (per deck: one 2, two 3s, ... six 7s ... one 12), so no long
 * droughts. A deeper stack (72, 108, 144 cards) still evens out but allows streaks within it.
 * The stack is reshuffled when only [reshuffleAt] cards remain, so the last few cards can't be
 * counted. The event die (Age of Chivalry) is not part of the deck; it stays truly random.
 */
class DiceDeck(
    val decks: Int = 1,
    private val reshuffleAt: Int = 5,
    private val rng: SecureRandom = SecureRandom(),
) : DiceSource {
    private val size = 36 * decks
    private val cards = IntArray(size) { it % 36 } // card c = dice (c / 6 + 1, c % 6 + 1)
    private var next = size

    /** Cards left before the next reshuffle (for the UI). */
    val remaining get() = (size - next - reshuffleAt).coerceAtLeast(0)

    init { require(decks in 1..8 && reshuffleAt in 0 until size) }

    override fun next(withEvent: Boolean): DiceRoll {
        if (size - next <= reshuffleAt) shuffle()
        val c = cards[next++]
        return DiceRoll(c / 6 + 1, c % 6 + 1, if (withEvent) rng.nextInt(6) else -1)
    }

    private fun shuffle() {
        for (i in size - 1 downTo 1) { // Fisher-Yates
            val j = rng.nextInt(i + 1)
            val t = cards[i]; cards[i] = cards[j]; cards[j] = t
        }
        next = 0
    }

    companion object {
        fun of(mode: DiceMode, decks: Int = 1): DiceSource = when (mode) {
            DiceMode.RANDOM -> TrueRandomDice()
            DiceMode.DECK -> DiceDeck(decks)
        }
    }
}
