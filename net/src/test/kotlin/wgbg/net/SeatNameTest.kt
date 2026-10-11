package wgbg.net

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import wgbg.engine.*

class SeatNameTest {
    @Test fun `a fresh name is kept, trimmed`() {
        assertEquals("Erin", uniqueSeatName("  Erin ", listOf("Mauro")))
        assertEquals("Erin", uniqueSeatName("Erin", emptyList()))
    }
    @Test fun `a taken name gets the next free number, ignoring case and spaces`() {
        assertEquals("Erin 2", uniqueSeatName("Erin", listOf("Mauro", "erin")))
        assertEquals("Erin 3", uniqueSeatName("Erin", listOf("Erin", " ERIN 2 ")))
        assertEquals("Erin 2", uniqueSeatName("Erin", listOf("Erin", "Erin 3")))
        assertEquals("Erin 2 2", uniqueSeatName("Erin 2", listOf("Erin 2")))
    }
    @Test fun `three guests with the host's name all end up different`() {
        val store = MemoryStore()
        val host = InMemoryTransport("host", store)
        host.create(Room("ROOM", "host", GameConfig(numPlayers = 4), 1,
            listOf(Seat("host", "Mauro", ready = true, open = false), Seat(), Seat(), Seat()))).join()
        for (i in 1..3) InMemoryTransport("g$i", store).join("ROOM", i, "mauro").join()
        val names = host.listOpen().join().single().seats.map { it.name }
        assertEquals(listOf("Mauro", "mauro 2", "mauro 3", "mauro 4"), names)
    }
    @Test fun `a seat freed by leaving frees its name`() {
        val store = MemoryStore()
        val host = InMemoryTransport("host", store); val g = InMemoryTransport("g", store)
        host.create(Room("ROOM", "host", GameConfig(numPlayers = 2), 1,
            listOf(Seat("host", "Host", ready = true, open = false), Seat(), Seat(open = false), Seat(open = false)))).join()
        g.join("ROOM", 1, "Sam").join(); g.leave("ROOM").join(); InMemoryTransport("h", store).join("ROOM", 1, "Sam").join()
        assertEquals("Sam", host.listOpen().join().single().seats[1].name)
    }
}
