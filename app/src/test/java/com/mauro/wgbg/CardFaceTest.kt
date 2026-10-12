package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Card names on the small faces keep whole words on a line (0.2.7 test: "Trailblazers" broke mid-word). */
class CardFaceTest {
    @Test
    fun `short names keep the normal size and only long words shrink, to a still readable size`() {
        for (n in listOf("Sentinel", "Windfall", "Landmark", "Corner the Market")) assertEquals(8f, cardNameSp(n, 56))
        val long = cardNameSp("Trailblazers", 56)
        assertTrue(long < 8f && long >= 5.5f, "Trailblazers at $long sp")
        assertEquals(8f, cardNameSp("Trailblazers", 120))
    }
}
