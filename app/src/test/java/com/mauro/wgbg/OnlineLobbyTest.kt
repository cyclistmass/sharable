package com.mauro.wgbg

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import wgbg.engine.*
import wgbg.net.*

class OnlineLobbyTest {
    @Test fun `Firestore wire preserves every config field and long seeds`() {
        for (expansion in Expansion.entries) for (seed in listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L)) {
            val config = GameConfig(expansion, 3, 16, BoardLayout.BEGINNER, false, 9, 321, true, true)
            val r = Room("ROVER-42", "host", config, seed, listOf(Seat("host", "Host"), Seat("guest", "Guest", ready = true), Seat(bot = "STRONG", open = false), Seat()), Status.playing, 123, 4)
            assertEquals(r, room(r.id, r.wire()))
        }
    }
    @Test fun `session maps identity bots settings and readiness through onChange`() {
        val store = MemoryStore(); val host = InMemoryTransport("host", store); val guest = InMemoryTransport("guest", store)
        val setup = GameSetup(name = "Host").withSeat(1, SeatKind.FRIEND)
        val h = LobbySession.host(host, setup, 7, "ROVER-42").join()
        val g = LobbySession.join(guest, setup.copy(name = "Guest"), h.id, 1).join()
        var latest: Snapshot? = null; val sub = guest.observe(g.id, { latest = it }) { throw it }
        val model = g.model(latest!!.room); assertEquals(1, model.me); assertFalse(model.isHost)
        g.onChange(model.toggleReady(), latest!!.room).join(); assertTrue(g.model(latest!!.room).seats[1].ready)
        assertTrue(h.model(latest!!.room).isHost); assertEquals(BotLevel.GREEDY, model.seats[2].bot)
        guest.leave(g.id).join(); assertFalse(g.model(latest!!.room).isHost)
        sub.close()
    }
    @Test fun `stub host semantics still follow copied seat identity`() {
        val sample = Lobby.sample(); assertTrue(sample.isHost); assertFalse(sample.copy(me = 1).isHost)
        assertTrue(sample.copy(me = 1).toggleReady().seats[1].ready.not())
    }

    @Test
    fun `online seats map rooms to engine players, me, names and host bots`() {
        val room = wgbg.net.Room("ROVER-42", "h", wgbg.engine.GameConfig(numPlayers = 4), 7, listOf(
            wgbg.net.Seat("h", "Mauro", ready = true), wgbg.net.Seat(null, "", null, open = true),
            wgbg.net.Seat(null, "", "EASY", open = false), wgbg.net.Seat("g", "Erin", ready = true)))
        val guest = OnlineSeats(room, "g")
        org.junit.jupiter.api.Assertions.assertEquals(listOf(0, 2, 3), guest.players)   // the empty open seat doesn't play
        org.junit.jupiter.api.Assertions.assertEquals(2, guest.me)
        org.junit.jupiter.api.Assertions.assertEquals(3, guest.config.numPlayers)
        org.junit.jupiter.api.Assertions.assertEquals(listOf("Mauro", "Blue (Easy)", "You"), guest.names)
        org.junit.jupiter.api.Assertions.assertEquals(BotLevel.EASY, guest.bot(1))
        org.junit.jupiter.api.Assertions.assertNull(guest.bot(0)); org.junit.jupiter.api.Assertions.assertNull(guest.bot(2))
        org.junit.jupiter.api.Assertions.assertEquals(3, guest.seatOf(2))
        org.junit.jupiter.api.Assertions.assertFalse(guest.isHost)
        val host = OnlineSeats(room, "h")
        org.junit.jupiter.api.Assertions.assertEquals(0, host.me); org.junit.jupiter.api.Assertions.assertTrue(host.isHost)
        org.junit.jupiter.api.Assertions.assertEquals(-1, OnlineSeats(room, "stranger").me)
    }

    @Test
    fun `joining by code takes the first open seat, and says so when the room is full or missing`() {
        val store = MemoryStore()
        val setup = GameSetup(name = "Mauro").withSeat(1, SeatKind.FRIEND).withSeat(2, SeatKind.FRIEND)
        val host = LobbySession.host(InMemoryTransport("h", store), setup, 5, code = "ROVER-42").join()
        val erin = joinFirstOpenSeat(InMemoryTransport("e", store), GameSetup(name = "Erin"), "ROVER-42").join()
        val sam = joinFirstOpenSeat(InMemoryTransport("s", store), GameSetup(name = "Sam"), "ROVER-42").join()
        val room = sam.client.view().room!!
        assertEquals(listOf("h", "e", "s"), room.seats.take(3).map { it.uid })
        val full = runCatching { joinFirstOpenSeat(InMemoryTransport("x", store), GameSetup(name = "Max"), "ROVER-42").join() }
        assertEquals("That room is full or has started", onlineProblem(full.exceptionOrNull()!!))
        val missing = runCatching { joinFirstOpenSeat(InMemoryTransport("y", store), GameSetup(name = "Ann"), "OTTER-11").join() }
        assertTrue(missing.isFailure)
        listOf(host, erin, sam).forEach { it.close() }
    }

    @Test
    fun `online needs a real name, not the default You`() {
        assertFalse(onlineName("You")); assertFalse(onlineName(" you ")); assertFalse(onlineName("  "))
        assertTrue(onlineName("Erin"))
    }

    @Test
    fun `online problems read as short sentences`() {
        assertEquals("No room with that code", onlineProblem(java.util.concurrent.CompletionException(IllegalStateException("Unknown room"))))
        assertEquals("The server said no (permission denied)", onlineProblem(RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")))
        assertEquals("Can't reach the server. Check the connection.", onlineProblem(java.io.IOException("x")))
    }
}
