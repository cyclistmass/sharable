package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.GameConfig
import wgbg.net.Room
import wgbg.net.Seat
import wgbg.net.Status

class RejoinTest {
    @Test
    fun `a saved room is rejoined on launch however old it is`() {
        assertTrue(autoRejoin("ROVER-42", paused = false, busyElsewhere = false))
    }

    @Test
    fun `no room, a room left for now, or another reason to open the app means no automatic rejoin`() {
        assertFalse(autoRejoin(null, paused = false, busyElsewhere = false), "nothing saved (quit, deleted or ended)")
        assertFalse(autoRejoin("ROVER-42", paused = true, busyElsewhere = false), "left for now: Resume button instead")
        assertFalse(autoRejoin("ROVER-42", paused = false, busyElsewhere = true), "opened by an invite link or a debug start")
    }

    @Test
    fun `a guest who quit a running game is played by the host's bot, people and empty seats are not`() {
        val room = Room("ROVER-42", "host", GameConfig(), 1L, listOf(
            Seat("host", "Host", null, false, true), Seat(null, "", "GREEDY", false, false),
            Seat("guest", "Guest", null, false, true), Seat(null, "", null, false, false)), Status.playing)
        assertTrue(quitToBot(room, 1), "quit seat")
        assertFalse(quitToBot(room, 0), "the host")
        assertFalse(quitToBot(room, 2), "a person still playing")
        assertFalse(quitToBot(room, 3), "an empty seat")
        assertFalse(quitToBot(null, 1), "no room yet")
        assertFalse(quitToBot(room, 9), "no such seat")
    }
}
