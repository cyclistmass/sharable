package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.PI

class Sea3dTest {
    @Test
    fun `a course picks the render whose bow points the same way on screen`() {
        val half = (PI / 2).toFloat()
        // Sea y points down the screen; renders count counter-clockwise from east on screen.
        assertEquals(0, SeaSprites3d.headingIndex(0f, 1f))           // east
        assertEquals(4, SeaSprites3d.headingIndex(-half, 1f))        // up the screen
        assertEquals(8, SeaSprites3d.headingIndex(PI.toFloat(), 1f)) // west
        assertEquals(12, SeaSprites3d.headingIndex(half, 1f))        // down the screen
        assertEquals(2, SeaSprites3d.headingIndex(-half / 2, 1f))    // north-east
        // Tilted, a north-east course looks flatter on screen, so it turns toward east.
        assertEquals(1, SeaSprites3d.headingIndex(-half / 2, 0.4f))
        for (k in -40..40) assertEquals(true, SeaSprites3d.headingIndex(k * 0.3f, 0.7f) in 0 until SeaArt.HEADINGS)
    }

    @Test
    fun `tilt steps match the renders and every table is complete`() {
        assertEquals(0, SeaSprites3d.tiltStep(0f))
        assertEquals(7, SeaSprites3d.tiltStep(0.96f))
        assertEquals(7, SeaSprites3d.tiltStep(2f))
        assertEquals(4, SeaSprites3d.tiltStep(0.96f * 4 / 7))
        for (t in listOf(SeaArt.sloop, SeaArt.sloopTrim, SeaArt.sloopWake, SeaArt.raiders, SeaArt.raidersWake)) {
            assertEquals(SeaArt.HEADINGS, t.size)
            t.forEach { assertEquals(SeaArt.STEPS, it.size) }
        }
    }
}
