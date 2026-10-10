package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiceArtTest {
    @Test
    fun `every pirate event die style has six distinct faces and a tumble`() {
        assertEquals(4, DiceArt.EVENT_STYLES.size)
        val all = HashSet<Int>()
        for (style in DiceArt.EVENT_STYLES.indices) {
            val faces = (0..5).map { DiceArt.eventFace(true, it, style) }
            assertEquals(6, faces.toSet().size, "style $style faces")
            assertTrue(all.addAll(faces), "style $style reuses another style's faces")
            val tumble = DiceArt.eventTumble(true, style)
            assertTrue(tumble.size >= 2, "style $style has ${tumble.size} tumble frames")
            assertEquals(tumble.size, tumble.toSet().size)
        }
    }

    @Test
    fun `style 0 is the original pirate die and the Antique die ignores the style`() {
        for (e in 0..5) {
            assertEquals(DiceArt.FACES_PE[DiceArt.eventFile(e) - 1], DiceArt.eventFace(true, e))
            assertEquals(DiceArt.eventFace(true, e), DiceArt.eventFace(true, e, 0))
            for (style in 0..3) assertEquals(DiceArt.FACES_AE[DiceArt.eventFile(e) - 1], DiceArt.eventFace(false, e, style))
        }
        assertEquals(DiceArt.TUMBLE_PE, DiceArt.eventTumble(true))
        assertEquals(DiceArt.TUMBLE_AE, DiceArt.eventTumble(false, 2))
    }

    @Test
    fun `out-of-range styles fall back safely`() {
        assertEquals(DiceArt.eventFace(true, 3, 0), DiceArt.eventFace(true, 3, -5))
        assertEquals(DiceArt.eventFace(true, 3, 3), DiceArt.eventFace(true, 3, 99))
        assertEquals(DiceArt.eventTumble(true, 3), DiceArt.eventTumble(true, 7))
    }
}
