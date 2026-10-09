package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The dice histogram's numbers: tallies, expectations, 7-streaks, chi-square and its p-value. */
class DiceStatsTest {
    @Test
    fun `counts and expectations line up with the 36 combinations`() {
        val all = (1..6).flatMap { a -> (1..6).map { b -> a + b } }
        val d = DiceStats(all)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1), d.counts.toList())
        for (i in 0 until 11) assertEquals(d.counts[i].toDouble(), d.expected[i], 1e-9)
        assertEquals(0.0, d.chiSquare, 1e-9)
        assertEquals(1.0, d.pValue, 1e-9)
    }

    @Test
    fun `the playtest's opening rolls show the streak`() {
        val d = DiceStats(listOf(6, 7, 7, 7, 5, 7))
        assertEquals(4, d.sevens)
        assertEquals(3, d.longestSevenRun)
        assertEquals(4, d.mostSevensIn(6))
        assertEquals(3, d.mostSevensIn(4)) // every 4-roll window holds three 7s
        assertEquals(3, d.mostSevensIn(3))
        assertEquals(4, d.mostSevensIn(100)) // window longer than the list
    }

    @Test
    fun `chance of k or more 7s in n rolls`() {
        assertEquals(1.0, DiceStats.chanceOfAtLeastSevens(0, 6), 1e-12)
        assertEquals(1 / 6.0, DiceStats.chanceOfAtLeastSevens(1, 1), 1e-12)
        // 5 or 6 sevens in 6 rolls: 6 * (1/6)^5 * (5/6) + (1/6)^6 = 31 / 46656.
        assertEquals(31.0 / 46656, DiceStats.chanceOfAtLeastSevens(5, 6), 1e-12)
    }

    @Test
    fun `p-values match chi-square tables for 10 degrees of freedom`() {
        // Upper-tail critical values: 18.307 at 0.05, 23.209 at 0.01, 29.588 at 0.001, 9.342 at 0.5.
        fun p(x: Double) = 1.0 - DiceStats.regularizedGammaP(5.0, x / 2)
        assertEquals(0.05, p(18.307), 1e-4)
        assertEquals(0.01, p(23.209), 1e-4)
        assertEquals(0.001, p(29.588), 1e-5)
        assertEquals(0.5, p(9.342), 1e-3)
    }

    @Test
    fun `true random dice look fair in a big sample, and a loaded die does not`() {
        val dice = TrueRandomDice()
        val fair = DiceStats(List(100_000) { dice.next().sum })
        assertTrue(fair.pValue > 0.0001, "fair dice flagged: chi2 ${fair.chiSquare}")
        // A die that rolls 7 a quarter of the time is caught at once.
        val rng = java.util.Random(1)
        val loaded = DiceStats(List(10_000) { if (rng.nextInt(4) == 0) 7 else dice.next().sum })
        assertTrue(loaded.pValue < 1e-6, "loaded dice not caught: p ${loaded.pValue}")
    }

    @Test
    fun `empty and invalid input`() {
        val d = DiceStats(emptyList())
        assertEquals(0, d.rolls)
        assertEquals(0, d.longestSevenRun)
        assertEquals(0, d.mostSevensIn(6))
        assertEquals(1.0, d.pValue)
        assertThrows<IllegalArgumentException> { DiceStats(listOf(1)) }
        assertThrows<IllegalArgumentException> { DiceStats(listOf(13)) }
    }
}
