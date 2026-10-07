package wgbg.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LongestRoadTest {
    // Three hexes far apart on the board, so players' roads never touch.
    private val top = Hex(0, -2)
    private val right = Hex(2, 0)
    private val bottomLeft = Hex(-2, 2)

    private fun ring(s: GameState, h: Hex) = IntArray(6) { T.corner(s, h.q, h.r, it) }

    /** The edge leaving corner [c] of [h] away from the hex. */
    private fun outward(s: GameState, h: Hex, c: Int): Int {
        val r = ring(s, h)
        return s.board.topology.vertexEdges[r[c]].first { s.board.otherEnd(it, r[c]) !in r }
    }

    /** Road along [n] consecutive sides of hex [h]'s perimeter starting at corner 0. */
    private fun around(s: GameState, p: Int, h: Hex, n: Int) {
        val c = ring(s, h)
        T.path(s, p, *IntArray(n + 1) { c[it % 6] })
    }

    @Test
    fun `needs at least 5 roads`() {
        val s = T.playState()
        around(s, 0, top, 4)
        assertEquals(4, s.roadLength[0])
        assertEquals(-1, s.longestRoadOwner)
        val c = ring(s, top)
        T.path(s, 0, c[4], c[5])
        assertEquals(0, s.longestRoadOwner)
        assertEquals(2, s.victoryPoints(0))
    }

    @Test
    fun `loops and branches are measured as the longest trail`() {
        val s = T.playState()
        around(s, 0, top, 6)
        assertEquals(6, s.roadLength[0])

        val t = T.playState()
        val center = Hex(0, 0)
        around(t, 0, center, 5)
        // Spur off corner 0, away from the hex, extends the trail to 6.
        T.road(t, 0, outward(t, center, 0))
        assertEquals(6, t.roadLength[0])
        // A spur off the middle (corner 2) does not.
        T.road(t, 0, outward(t, center, 2))
        assertEquals(6, t.roadLength[0])
        // Closing the loop: spur + 6-loop = 7.
        T.path(t, 0, ring(t, center)[5], ring(t, center)[0])
        assertEquals(7, t.roadLength[0])
    }

    @Test
    fun `holder keeps the card on a tie, loses it to a strictly longer road`() {
        val s = T.playState()
        around(s, 0, top, 5)
        around(s, 1, right, 5)
        assertEquals(0, s.longestRoadOwner)
        val c = ring(s, right)
        T.path(s, 1, c[5], c[0])
        assertEquals(1, s.longestRoadOwner)
        assertEquals(0, s.victoryPoints(0))
        assertEquals(2, s.victoryPoints(1))
    }

    @Test
    fun `own settlement does not break a road`() {
        val s = T.playState()
        around(s, 0, top, 5)
        T.settle(s, 0, ring(s, top)[2])
        assertEquals(5, Rules.longestRoad(s, 0))
    }

    /** Player [p] builds a settlement on corner [c] of [h] through the real rules. */
    private fun cutAt(s: GameState, p: Int, h: Hex, c: Int) {
        s.currentPlayer = p
        s.actingPlayer = p
        s.hasRolled = true
        T.road(s, p, outward(s, h, c))
        T.giveAll(s, p, Cost.SETTLEMENT)
        Rules.applyChecked(s, Action.buildSettlement(ring(s, h)[c]))
    }

    @Test
    fun `broken by an opponent settlement - lost to nobody`() {
        val s = T.playState()
        around(s, 0, top, 5)
        assertEquals(0, s.longestRoadOwner)
        cutAt(s, 1, top, 2)
        assertEquals(3, s.roadLength[0])
        assertEquals(-1, s.longestRoadOwner)
        assertEquals(0, s.victoryPoints(0))
        T.checkInvariants(s)
    }

    @Test
    fun `broken road passes the card to a unique longest road`() {
        val s = T.playState()
        around(s, 0, top, 5)
        around(s, 2, right, 5)
        assertEquals(0, s.longestRoadOwner)
        cutAt(s, 1, top, 3)
        assertEquals(3, s.roadLength[0])
        assertEquals(2, s.longestRoadOwner)
        T.checkInvariants(s)
    }

    @Test
    fun `broken road with a tie among others sets the card aside`() {
        val s = T.playState()
        around(s, 0, top, 6)
        around(s, 2, right, 5)
        around(s, 3, bottomLeft, 5)
        assertEquals(0, s.longestRoadOwner)
        // A loop cut at one corner is still a 6-trail ending at the settlement.
        cutAt(s, 1, top, 2)
        assertEquals(6, s.roadLength[0])
        assertEquals(0, s.longestRoadOwner)
        cutAt(s, 1, top, 4)
        assertEquals(4, s.roadLength[0])
        assertEquals(-1, s.longestRoadOwner)
        T.checkInvariants(s)
    }
}
