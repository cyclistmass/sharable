package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CongratsTest {
    private fun p(seat: Int, vp: Int, winner: Boolean = false) =
        PlayerInfo(seat, 80, false, vp, 0, 0, false, false, winner, emptyList())

    @Test fun `the winner comes first, then by points, ties keep seat order`() {
        // A winner can show fewer points than another seat only in odd endings; it still leads.
        val order = standings(listOf(p(0, 7), p(1, 9), p(2, 10, winner = true), p(3, 7))).map { it.seat }
        assertEquals(listOf(2, 1, 0, 3), order)
        assertEquals(listOf(1, 0, 2), standings(listOf(p(0, 5), p(1, 8), p(2, 5))).map { it.seat }, "a draw: by points")
    }

    @Test fun `the title speaks to the player who won, names another winner, and says draw`() {
        val names = listOf("You", "Ada", "Bo")
        assertEquals("Congratulations, you win!", congratsTitle(0, 0, names))
        assertEquals("Ada wins!", congratsTitle(1, 0, names))
        assertEquals("Bo wins!", congratsTitle(2, -1, names), "watching bots")
        assertEquals("A draw!", congratsTitle(-1, 0, names))
    }
}
