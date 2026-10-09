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
}
