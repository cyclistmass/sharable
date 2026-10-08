package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiceTest {
    /** Pearson chi-square of observed counts against expected probabilities. */
    private fun chiSquare(counts: IntArray, probs: DoubleArray): Double {
        val n = counts.sum().toDouble()
        return counts.indices.sumOf { (counts[it] - n * probs[it]).let { d -> d * d } / (n * probs[it]) }
    }

    private val sumProbs = DoubleArray(11) { (6 - kotlin.math.abs(5 - it)) / 36.0 } // sums 2..12

    @Test
    fun `true random dice are fair - faces and sums match the odds`() {
        val dice = TrueRandomDice()
        val faces = IntArray(6)
        val sums = IntArray(11)
        val events = IntArray(6)
        repeat(360_000) {
            val r = dice.next(withEvent = true)
            faces[r.d1 - 1]++; faces[r.d2 - 1]++
            sums[r.sum - 2]++
            events[r.event]++
        }
        // Critical values at p = 0.0001: 25.7 for 5 degrees of freedom, 35.6 for 10.
        val flat = DoubleArray(6) { 1 / 6.0 }
        assertTrue(chiSquare(faces, flat) < 25.7, "faces ${faces.toList()}")
        assertTrue(chiSquare(events, flat) < 25.7, "event die ${events.toList()}")
        assertTrue(chiSquare(sums, sumProbs) < 35.6, "sums ${sums.toList()}")
    }

    @Test
    fun `true random dice show no memory - a roll does not predict the next`() {
        val dice = TrueRandomDice()
        // Count transitions from each sum to the next; a fair source makes the next sum
        // independent of the previous one, so "after a 7" looks like any other roll.
        val after7 = IntArray(11)
        var prev = dice.next().sum
        repeat(400_000) {
            val s = dice.next().sum
            if (prev == 7) after7[s - 2]++
            prev = s
        }
        assertTrue(chiSquare(after7, sumProbs) < 35.6, "sums after a 7 ${after7.toList()}")
    }

    @Test
    fun `the deck deals every combination once before reshuffling near the end`() {
        val deck = DiceDeck(decks = 1, reshuffleAt = 5)
        repeat(200) {
            val seen = HashSet<Pair<Int, Int>>()
            repeat(31) {
                val r = deck.next()
                assertTrue(seen.add(r.d1 to r.d2), "a combination repeated within one deck")
            }
        }
    }

    @Test
    fun `a full deck has exactly the textbook sum counts`() {
        val deck = DiceDeck(decks = 1, reshuffleAt = 0)
        val sums = IntArray(11)
        repeat(36) { sums[deck.next().sum - 2]++ }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1), sums.toList())
    }

    @Test
    fun `a double stack holds each combination exactly twice`() {
        val deck = DiceDeck(decks = 2, reshuffleAt = 0)
        val counts = HashMap<Pair<Int, Int>, Int>()
        repeat(72) { deck.next().let { counts.merge(it.d1 to it.d2, 1, Int::plus) } }
        assertEquals(36, counts.size)
        assertTrue(counts.values.all { it == 2 })
    }

    @Test
    fun `rolls with explicit dice drive the engine and stay replayable`() {
        val s = T.playState()
        assertTrue(Rules.isLegal(s, Action.roll(3, 4)))
        assertFalse(Rules.isLegal(s, Action.of(ActionType.ROLL, 7L or (1L shl 8))), "die face 7")
        val before = s.rng.seed
        Rules.applyChecked(s, Action.roll(5, 6))
        assertEquals(11, s.lastRoll)
        assertEquals(before, s.rng.seed, "external dice must not consume the engine RNG")
        // The same log replays to the same state.
        val a = Rules.newGame(GameConfig(), 3)
        val b = Rules.newGame(GameConfig(), 3)
        val log = mutableListOf<Action>()
        val buf = ActionBuffer()
        val dice = TrueRandomDice()
        var steps = 0
        while (!a.isOver && steps++ < 3000) {
            Rules.legalActions(a, buf)
            var act = buf[(steps * 7919) % buf.size]
            if (act.type == ActionType.ROLL) act = dice.next().toAction()
            Rules.apply(a, act); log += act
        }
        for (act in log) Rules.apply(b, act)
        assertEquals(a.stateHash(), b.stateHash())
    }
}
