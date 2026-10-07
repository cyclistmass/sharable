package wgbg.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardTest {
    private fun checkStandard(b: Board) {
        assertEquals(19, b.hexCount)
        assertEquals(listOf(4, 3, 4, 4, 3), (0 until CARD_TYPES).map { r -> b.hexResource.count { it == r } })
        assertEquals(1, b.hexResource.count { it == Resource.NONE })
        assertEquals(0, b.hexNumber[b.desertHex])
        assertEquals(
            BoardGen.NUMBERS.toList(),
            b.hexNumber.filter { it != 0 }.sorted(),
        )
        assertEquals(9, b.ports.size)
        assertEquals(4, b.ports.count { it.type == Port.GENERIC })
        assertEquals((0 until 5).toList(), b.ports.filter { it.type != Port.GENERIC }.map { it.type }.sorted())
        val portVertices = b.ports.flatMap { listOf(it.v1, it.v2) }
        assertEquals(18, portVertices.toSet().size)
        for (p in b.ports) {
            assertTrue(b.edgeBetween(p.v1, p.v2) >= 0, "port vertices must be adjacent")
            assertTrue(b.topology.vertexHexes[p.v1].size < 3 && b.topology.vertexHexes[p.v2].size < 3, "port on coast")
        }
    }

    @Test
    fun `random boards are standard and seed-dependent`() {
        val a = BoardGen.random(Rng(1))
        val a2 = BoardGen.random(Rng(1))
        val c = BoardGen.random(Rng(2))
        checkStandard(a)
        checkStandard(c)
        assertArrayEquals(a.hexResource, a2.hexResource)
        assertArrayEquals(a.hexNumber, a2.hexNumber)
        assertEquals(a.ports.map { it.type }, a2.ports.map { it.type })
        assertFalse(a.hexResource.contentEquals(c.hexResource) && a.hexNumber.contentEquals(c.hexNumber))
    }

    @Test
    fun `avoid adjacent red numbers flag`() {
        var violationsWithout = 0
        for (seed in 0L until 200) {
            val b = BoardGen.random(Rng(seed), avoidAdjacentRed = true)
            checkStandard(b)
            assertFalse(BoardGen.hasAdjacentRed(b.topology, b.hexNumber))
            if (BoardGen.hasAdjacentRed(b.topology, BoardGen.random(Rng(seed), false).hexNumber)) violationsWithout++
        }
        assertTrue(violationsWithout > 0, "unconstrained generation should sometimes put 6/8 together")
    }

    @Test
    fun `beginner layout`() {
        val b = BoardGen.beginner()
        checkStandard(b)
        assertEquals(b.hexId(Hex(0, 0)), b.desertHex)
        assertEquals(Resource.ORE, b.hexResource[b.hexId(Hex(0, -2))])
        assertEquals(10, b.hexNumber[b.hexId(Hex(0, -2))])
        assertEquals(Resource.SHEEP, b.hexResource[b.hexId(Hex(0, 2))])
        assertEquals(11, b.hexNumber[b.hexId(Hex(0, 2))])
        assertFalse(BoardGen.hasAdjacentRed(b.topology, b.hexNumber))
    }

    @Test
    fun `robber starts on the desert`() {
        val s = Rules.newGame(GameConfig(), 7)
        assertEquals(s.board.desertHex, s.robberHex)
        assertEquals(Resource.NONE, s.board.hexResource[s.robberHex])
    }

    @Test
    fun `rng is stable and unbiased enough`() {
        // Pinned values: changing the generator would break every saved game.
        val r = Rng(12345)
        val first = LongArray(3) { r.nextLong() }
        val again = Rng(12345)
        assertArrayEquals(first, LongArray(3) { again.nextLong() })
        assertNotEquals(first[0], first[1])
        val counts = IntArray(6)
        val d = Rng(9)
        repeat(60000) { counts[d.nextInt(6)]++ }
        for (c in counts) assertTrue(c in 9500..10500, "die face count $c")
        // SplitMix64 reference value for seed 0 (first output).
        assertEquals(-2152535657050944081L, Rng(0).nextLong())
    }
}
