package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RampartsHeightTest {
    @Test
    fun `the walls grow with the owner's best improvement, low at 0-1, middle at 2-3, high at 4-5`() {
        assertEquals(listOf(0, 0, 1, 1, 2, 2), (0..5).map { BuildingSprites.rampartsHeight(it) })
        assertEquals(0, BuildingSprites.rampartsHeight(-1))
        assertEquals(2, BuildingSprites.rampartsHeight(9))
    }

    @Test
    fun `every height has art in both themes, 8 tilt steps, and a perch lift that rises with it`() {
        for (pirate in listOf(false, true)) {
            val sets = BuildingArt.ramparts(pirate)
            assertEquals(3, sets.size)
            for (p in sets) { assertEquals(8, p.body.size); assertEquals(8, p.trim.size) }
        }
        assertEquals(3, RAMPARTS_PERCH_LIFT.size)
        assert(RAMPARTS_PERCH_LIFT.toList().zipWithNext().all { (a, b) -> a < b })
    }
}
