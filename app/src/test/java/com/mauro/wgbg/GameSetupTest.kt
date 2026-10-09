package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wgbg.engine.DiceDeck
import wgbg.engine.Expansion
import wgbg.engine.Rules
import wgbg.engine.TrueRandomDice
import kotlin.random.Random

/** The game-start settings (#3) and the lobby (#19): mapping to the engine, validation, and seat rules. */
class GameSetupTest {
    private val base = GameSetup(name = "Mauro")

    // ---------------------------------------------------------------- game setup

    @Test
    fun `defaults are four players, random dice, base game to 10`() {
        assertEquals(listOf(0, 1, 2, 3), base.players)
        assertTrue(base.canStart)
        val c = base.config()
        assertEquals(4, c.numPlayers)
        assertEquals(Expansion.BASE, c.expansion)
        assertEquals(10, c.vpToWin)
        assertTrue(base.dice() is TrueRandomDice)
    }

    @Test
    fun `empty seats drop out and the config counts only players`() {
        val s = base.withSeat(2, SeatKind.EMPTY)
        assertEquals(listOf(0, 1, 3), s.players)
        assertEquals(3, s.config().numPlayers)
    }

    @Test
    fun `seat 0 is always you`() {
        assertSame(base, base.withSeat(0, SeatKind.EMPTY))
        assertSame(base, base.withBot(0, BotLevel.EASY))
    }

    @Test
    fun `player counts outside the ruleset block start`() {
        val solo = base.withSeat(1, SeatKind.EMPTY).withSeat(2, SeatKind.EMPTY).withSeat(3, SeatKind.EMPTY)
        assertFalse(solo.canStart)
        assertTrue(solo.problems.single().contains("2-4"))
        val two = solo.withSeat(1, SeatKind.BOT)
        assertTrue(two.canStart)
        // Age of Chivalry needs at least 3.
        val chivalryTwo = two.withRuleset(Ruleset.CHIVALRY)
        assertFalse(chivalryTwo.canStart)
        assertTrue(chivalryTwo.problems.single().contains("3-4"))
    }

    @Test
    fun `a blank name blocks start`() {
        assertEquals(listOf("Enter your name"), base.copy(name = "  ").problems)
    }

    @Test
    fun `switching ruleset moves untouched points to its default but keeps custom points`() {
        assertEquals(13, base.withRuleset(Ruleset.CHIVALRY).points)
        assertEquals(10, base.withRuleset(Ruleset.CHIVALRY).withRuleset(Ruleset.BASE).points)
        assertEquals(12, base.withPoints(12).withRuleset(Ruleset.CHIVALRY).points)
        assertEquals(Expansion.CITIES_AND_KNIGHTS, base.withRuleset(Ruleset.CHIVALRY).config().expansion)
    }

    @Test
    fun `points and decks are clamped, and bad values constructed directly are reported`() {
        assertEquals(GameSetup.POINTS_RANGE.last, base.withPoints(99).points)
        assertEquals(GameSetup.POINTS_RANGE.first, base.withPoints(-3).points)
        assertEquals(GameSetup.MAX_DECKS, base.withDecks(9).decks)
        assertEquals(0, base.withDecks(-1).decks)
        assertFalse(base.copy(points = 50).canStart)
        assertFalse(base.copy(decks = 7).canStart)
    }

    @Test
    fun `deck dice hold 36 cards per deck`() {
        for (d in 1..GameSetup.MAX_DECKS) {
            val dice = base.withDecks(d).dice()
            assertTrue(dice is DiceDeck)
            assertEquals("${36 * d}-card deck", GameSetup.diceLabel(d))
        }
        assertEquals("Random", GameSetup.diceLabel(0))
    }

    @Test
    fun `every startable setup builds a playable engine game`() {
        // Every seat combination x ruleset x points: canStart must agree with the engine accepting it.
        val kinds = SeatKind.entries
        for (a in kinds) for (b in kinds) for (c in kinds) for (r in Ruleset.entries) {
            val s = base.withSeat(1, a).withSeat(2, b).withSeat(3, c).withRuleset(r)
            val engineOk = runCatching { Rules.newGame(s.config(), 1L) }.isSuccess
            assertEquals(engineOk, s.canStart, "seats $a $b $c, ${r.label}")
        }
    }

    @Test
    fun `the setup needs exactly four seats`() {
        assertThrows<IllegalArgumentException> { GameSetup(seats = listOf(SeatSetup(SeatKind.FRIEND))) }
    }

    // ---------------------------------------------------------------- lobby

    private val lobby = Lobby.host(base.withSeat(1, SeatKind.FRIEND).withSeat(2, SeatKind.FRIEND).withBot(3, BotLevel.EASY), code = "K7QX4")

    @Test
    fun `hosting seats you, places the chosen bots and leaves friend seats open`() {
        assertEquals("Mauro", lobby.seats[0].name)
        assertTrue(lobby.seats[1].open && lobby.seats[2].open)
        assertEquals(BotLevel.EASY, lobby.seats[3].bot)
        assertEquals("https://wgbg.example/join/K7QX4", lobby.link)
        assertEquals(2, lobby.filled)
    }

    @Test
    fun `room codes avoid look-alike characters and validate`() {
        val r = Random(7)
        repeat(500) {
            val code = Lobby.newCode(r)
            assertTrue(Lobby.isValidCode(code), code)
            assertTrue(code.none { it in "0O1IL" }, code)
        }
        assertFalse(Lobby.isValidCode("K7QX"))
        assertFalse(Lobby.isValidCode("K7QX0"))
        assertFalse(Lobby.isValidCode("k7qx4"))
    }

    @Test
    fun `friends join open seats in order until the room is full`() {
        val one = lobby.join("Justin")!!
        assertEquals("Justin", one.seats[1].name)
        val two = one.join("Erin")!!
        assertEquals("Erin", two.seats[2].name)
        assertNull(two.join("Late"))
    }

    @Test
    fun `start waits until every seated person is ready`() {
        val full = lobby.join("Justin")!!.join("Erin")!!
        assertFalse(full.canStart) // nobody ready yet (bot is)
        val ready = full.copy(seats = full.seats.map { if (it.isBot) it else it.copy(ready = true) })
        assertTrue(ready.canStart)
        // A guest can't start the game.
        assertFalse(ready.copy(me = 1).canStart)
    }

    @Test
    fun `open seats drop out at start, and the player count must still fit the ruleset`() {
        val host = lobby.toggleReady()
        assertTrue(host.canStart) // you + easy bot = 2 players, base game
        assertEquals(listOf(0, 3), host.gameSetup().players)
        val chivalry = host.copy(setup = host.setup.withRuleset(Ruleset.CHIVALRY))
        assertFalse(chivalry.canStart)
    }

    @Test
    fun `only the host places and removes bots, never on a person or seat 0`() {
        val withJustin = lobby.join("Justin")!!
        assertSame(withJustin, withJustin.placeBot(1, BotLevel.STRONG)) // Justin's seat
        assertSame(withJustin, withJustin.placeBot(0, BotLevel.STRONG))
        val bot2 = withJustin.placeBot(2, BotLevel.GREEDY)
        assertEquals(BotLevel.GREEDY, bot2.seats[2].bot)
        assertEquals(BotLevel.STRONG, bot2.placeBot(3, BotLevel.STRONG).seats[3].bot) // relevel
        assertTrue(bot2.removeBot(2).seats[2].open)
        assertSame(bot2, bot2.removeBot(1)) // can't remove a person
        val guest = bot2.copy(me = 1)
        assertSame(guest, guest.placeBot(2, BotLevel.EASY))
        assertSame(guest, guest.removeBot(2))
    }

    @Test
    fun `lobby seats become the game's friend, bot and empty seats`() {
        val l = lobby.join("Justin")!!
        val g = l.gameSetup()
        assertEquals(listOf(SeatKind.FRIEND, SeatKind.FRIEND, SeatKind.EMPTY, SeatKind.BOT), g.seats.map { it.kind })
        assertEquals(BotLevel.EASY, g.seats[3].bot)
    }

    @Test
    fun `ready toggles only for a seated player`() {
        assertTrue(lobby.toggleReady().seats[0].ready)
        assertFalse(lobby.toggleReady().toggleReady().seats[0].ready)
        val ghost = lobby.copy(me = 1) // seat 1 is open
        assertSame(ghost, ghost.toggleReady())
    }

    @Test
    fun `the stub lobby is a sensible picture of a room`() {
        val s = Lobby.sample()
        assertEquals(listOf("Mauro", "Justin", "Erin"), s.seats.take(3).map { it.name })
        assertTrue(s.seats[1].ready)
        assertTrue(s.seats[3].isBot)
        assertFalse(s.canStart)
    }
}
