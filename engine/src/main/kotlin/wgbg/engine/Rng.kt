package wgbg.engine

/**
 * SplitMix64 PRNG. Implemented here (instead of java.util.Random / kotlin.random)
 * so sequences are identical on every JVM and Android version: replays and
 * network games depend on it.
 */
class Rng(var seed: Long) {
    fun nextLong(): Long {
        seed += GAMMA
        var z = seed
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform int in [0, bound), unbiased (rejection sampling). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        val threshold = RANGE - RANGE % bound
        while (true) {
            val r = nextLong() ushr 33 // 31 random bits
            if (r < threshold) return (r % bound).toInt()
        }
    }

    /** Fisher-Yates shuffle. */
    fun shuffle(a: IntArray) {
        for (i in a.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
    }

    fun copy() = Rng(seed)

    private companion object {
        val GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        val MIX1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX2 = 0x94D049BB133111EBuL.toLong()
        const val RANGE = 1L shl 31
    }
}
