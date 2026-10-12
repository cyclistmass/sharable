package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.DevCard
import wgbg.engine.Progress

class GuideArtTest {
    @Test
    fun `knight card art picks the theme's own deck, levels 1 to 3`() {
        assertEquals(listOf(R.drawable.card_knight_normal_1, R.drawable.card_knight_normal_2, R.drawable.card_knight_normal_3),
            (1..3).map { GuideArt.knightCard(it, pirate = false) })
        assertEquals(listOf(R.drawable.card_knight_pirate_1, R.drawable.card_knight_pirate_2, R.drawable.card_knight_pirate_3),
            (1..3).map { GuideArt.knightCard(it, pirate = true) })
        // The two themes never share a drawable (so a theme's art never leaks into the other's cards).
        for (lv in 1..3) assertNotEquals(GuideArt.knightCard(lv, pirate = false), GuideArt.knightCard(lv, pirate = true))
    }

    @Test
    fun `every Fortune card gets a valid background, Sentinel in the theme's own knight art`() {
        for (pirate in listOf(false, true)) {
            for (c in FORTUNE.indices) {
                val art = GuideArt.fortuneArt(c, pirate)
                assertNotEquals(0, art, "Fortune card $c (${FORTUNE[c]}) has no art, pirate=$pirate")
            }
            assertEquals(GuideArt.knightCard(1, pirate), GuideArt.fortuneArt(DevCard.KNIGHT, pirate))
        }
        // Only the Sentinel (Knight) is the full knight card picture; the rest get the drawn cream card face.
        assertTrue(GuideArt.fortuneIsKnightCard(DevCard.KNIGHT))
        for (c in FORTUNE.indices) if (c != DevCard.KNIGHT) assertTrue(!GuideArt.fortuneIsKnightCard(c), "Fortune card $c should not be the plain knight card")
    }

    @Test
    fun `the pirate theme never falls back to the normal knight cards for Sentinel`() {
        val normal = GuideArt.fortuneArt(DevCard.KNIGHT, pirate = false)
        val pirate = GuideArt.fortuneArt(DevCard.KNIGHT, pirate = true)
        assertNotEquals(normal, pirate)
        assertEquals(R.drawable.card_knight_pirate_1, pirate)
        assertEquals(R.drawable.card_knight_normal_1, normal)
    }

    @Test
    fun `every progress card has art for its deck's commodity`() {
        for (c in 0 until Progress.COUNT) {
            val art = progressArt(c)
            assertNotEquals(0, art, "progress card $c has no art")
            assertTrue(art in ALL_CARD_ART, "progress card $c's art ($art) isn't one of the commodity pictures")
        }
    }
}
