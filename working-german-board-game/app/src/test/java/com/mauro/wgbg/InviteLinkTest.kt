package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InviteLinkTest {
    @Test
    fun `invite links point at a real page and carry the room code`() {
        assertTrue(JOIN_BASE.startsWith("https://cyclistmass.github.io/sharable/join/"))
        assertEquals("ROVER-42", joinCodeFrom(JOIN_BASE + "ROVER-42"))
    }

    @Test
    fun `the game's own link and loose forms give the code, garbage gives nothing`() {
        assertEquals("ROVER-42", joinCodeFrom("wgbg://join/ROVER-42"))
        assertEquals("ROVER-42", joinCodeFrom("wgbg://join/rover42"))
        assertNull(joinCodeFrom(null))
        assertNull(joinCodeFrom("wgbg://join/"))
        assertNull(joinCodeFrom("https://example.com/whatever"))
    }
}
