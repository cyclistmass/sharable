package wgbg.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Tallies of two-dice totals against the odds, for the dice histogram (CLI `dice`, and later the
 * stats subpanel, #16). Pearson chi-square with its p-value says how surprising the spread is;
 * the longest run of 7s answers "five 7s in six rolls" complaints directly.
 */
class DiceStats(private val sums: List<Int>) {
    init {
        require(sums.all { it in 2..12 }) { "two-dice totals are 2..12" }
    }

    val rolls = sums.size

    /** Count per total, index 0 = a 2 ... index 10 = a 12. */
    val counts = IntArray(11).also { c -> sums.forEach { c[it - 2]++ } }

    /** Expected count per total for this many rolls. */
    val expected = DoubleArray(11) { rolls * ways(it + 2) / 36.0 }

    val sevens get() = counts[5]

    /** Longest run of consecutive 7s. */
    val longestSevenRun = sums.fold(0 to 0) { (best, run), s -> if (s == 7) maxOf(best, run + 1) to run + 1 else best to 0 }.first

    /** Most 7s in any [window] consecutive rolls (the whole list if shorter). */
    fun mostSevensIn(window: Int): Int {
        if (sums.isEmpty()) return 0
        val w = window.coerceAtMost(sums.size)
        var n = sums.take(w).count { it == 7 }
        var best = n
        for (i in w until sums.size) {
            if (sums[i] == 7) n++
            if (sums[i - w] == 7) n--
            best = maxOf(best, n)
        }
        return best
    }

    /** Pearson chi-square over the 11 totals (10 degrees of freedom). */
    val chiSquare: Double get() = if (rolls == 0) 0.0 else counts.indices.sumOf { (counts[it] - expected[it]).let { d -> d * d } / expected[it] }

    /**
     * Chance a fair pair of dice spreads at least this unevenly. Large samples only: with fewer than
     * about 5 expected rolls per total (180 rolls) the chi-square approximation is rough.
     */
    val pValue: Double get() = if (rolls == 0) 1.0 else 1.0 - regularizedGammaP(5.0, chiSquare / 2)

    companion object {
        /** Ways to roll [sum] with two dice (1 for 2 and 12, 6 for 7). */
        fun ways(sum: Int) = if (sum !in 2..12) 0 else 6 - abs(7 - sum)

        /** Chance of exactly [k] or more 7s among [n] fair rolls. */
        fun chanceOfAtLeastSevens(k: Int, n: Int): Double {
            var p = 0.0
            for (i in k..n) p += choose(n, i) * Math.pow(1 / 6.0, i.toDouble()) * Math.pow(5 / 6.0, (n - i).toDouble())
            return p
        }

        private fun choose(n: Int, k: Int): Double {
            var r = 1.0
            for (i in 1..k) r = r * (n - k + i) / i
            return r
        }

        /** P(a, x), the regularized lower incomplete gamma (series for x < a + 1, continued fraction above). */
        internal fun regularizedGammaP(a: Double, x: Double): Double {
            if (x <= 0) return 0.0
            val lnGammaA = lnGamma(a)
            if (x < a + 1) {
                var sum = 1.0 / a
                var term = sum
                var n = 1
                while (n < 500) {
                    term *= x / (a + n)
                    sum += term
                    if (abs(term) < abs(sum) * 1e-15) break
                    n++
                }
                return sum * exp(-x + a * ln(x) - lnGammaA)
            }
            // Lentz's continued fraction for Q(a, x).
            var b = x + 1 - a
            var c = 1.0 / 1e-300
            var d = 1.0 / b
            var h = d
            for (i in 1..500) {
                val an = -i * (i - a)
                b += 2.0
                d = an * d + b
                if (abs(d) < 1e-300) d = 1e-300
                c = b + an / c
                if (abs(c) < 1e-300) c = 1e-300
                d = 1.0 / d
                val del = d * c
                h *= del
                if (abs(del - 1.0) < 1e-15) break
            }
            return 1.0 - exp(-x + a * ln(x) - lnGammaA) * h
        }

        /** Lanczos approximation of ln Γ(x). */
        private fun lnGamma(x: Double): Double {
            val g = doubleArrayOf(76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5)
            var y = x
            val tmp = x + 5.5 - (x + 0.5) * ln(x + 5.5)
            var ser = 1.000000000190015
            for (c in g) { y += 1; ser += c / y }
            return -tmp + ln(2.5066282746310005 * ser / x)
        }
    }
}
