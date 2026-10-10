package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RejoinTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `a room left minutes ago is rejoined on launch`() {
        assertTrue(rejoinRecent(now - 60_000L, now))
        assertTrue(rejoinRecent(now - ROOM_REJOIN_MS, now))
    }

    @Test
    fun `an old, unknown or future room is forgotten, not rejoined`() {
        assertFalse(rejoinRecent(now - ROOM_REJOIN_MS - 1, now), "older than the limit")
        assertFalse(rejoinRecent(now - 24 * 3600_000L, now), "yesterday's room")
        assertFalse(rejoinRecent(0L, now), "saved by an older version without a time")
        assertFalse(rejoinRecent(now + 60_000L, now), "a clock that went backwards")
    }
}
