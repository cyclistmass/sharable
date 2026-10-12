package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.ai.GreedyBot
import wgbg.engine.Action
import wgbg.engine.ActionBuffer
import wgbg.engine.Rules

/** Bug reports and ideas (2026-10-12): the note, the position and a game that replays exactly, within Firestore's size. */
class FeedbackTest {
    private val chivalry = GameSetup().withRuleset(Ruleset.CHIVALRY)

    private fun playAndTrack(setup: GameSetup, seed: Long, moves: Int): Triple<wgbg.engine.GameState, List<Action>, TurnTracker> {
        val s = Rules.newGame(setup.config(), seed)
        val bots = List(setup.players.size) { GreedyBot(seed * 31 + it) }
        val tracker = TurnTracker(PLAYER_NAMES)
        val buf = ActionBuffer(); val made = ArrayList<Action>()
        while (!s.isOver && made.size < moves) {
            Rules.legalActions(s, buf)
            val who = s.actingPlayer
            val a = bots[who].choose(s, buf)
            tracker.apply(s, who, a) { Rules.apply(s, a) }; made += a
        }
        return Triple(s, made, tracker)
    }

    private fun report(note: String, game: String?, history: List<String>, position: String?, max: Int = Int.MAX_VALUE) =
        Feedback.formatFitted(max, Feedback.Kind.BUG, note, "0.2.8", 28, "Pixel", "2400x1080", game, history, position, 0L)

    @Test
    fun `a report's game replays to the exact position it names, in both rulesets`() {
        for (setup in listOf(GameSetup(), chivalry)) {
            val (live, moves, tracker) = playAndTrack(setup, 13L, 250)
            val r = report("couldn't build a town", LocalSave(setup, 13L, moves).encode(),
                Feedback.historyLines(tracker.history, PLAYER_NAMES), Feedback.position(live, PLAYER_NAMES))
            assertTrue(r.startsWith("WGBG bug report\n"))
            assertTrue(r.contains("note:\n  couldn't build a town\n"))
            assertTrue(r.contains("hash ${live.stateHash()}"))
            // The game block is the local-save format: cut it out and replay it.
            val game = r.substringAfter("same format as the local save):\n").substringBefore("\nhistory (oldest first):")
            val back = LocalSave.decode(game)
            assertNotNull(back)
            assertEquals(live.stateHash(), back!!.replay()!!.stateHash())
        }
    }

    @Test
    fun `history reads oldest first, a line per turn and its events`() {
        val (_, _, tracker) = playAndTrack(GameSetup(), 3L, 200)
        val lines = Feedback.historyLines(tracker.history, PLAYER_NAMES)
        val turns = lines.filter { it.startsWith("turn ") }.map { it.substringAfter("turn ").substringBefore(' ').toInt() }
        assertTrue(turns.size > 3)
        assertEquals(turns.sorted(), turns)
    }

    @Test
    fun `an upload fits Firestore's limit by dropping the oldest history, never the note or the game`() {
        val (live, moves, _) = playAndTrack(chivalry, 5L, 400)
        val game = LocalSave(chivalry, 5L, moves).encode()
        val history = List(3000) { "turn $it · Blue rolled 8" }
        val r = report("the dice froze", game, history, Feedback.position(live, PLAYER_NAMES), Feedback.UPLOAD_MAX)
        assertTrue(r.length <= Feedback.UPLOAD_MAX, "${r.length}")
        assertTrue(r.contains("the dice froze"))
        assertTrue(r.contains(game))
        assertTrue(r.contains("turn 2999 · Blue rolled 8"), "the newest history stays")
        assertFalse(r.contains("turn 0 · Blue rolled 8\n"), "the oldest history goes")
        // Without a limit nothing is dropped (Share sends it all).
        assertTrue(report("x", game, history, null).contains("turn 0 · Blue rolled 8\n"))
    }

    @Test
    fun `from the start screen a report is just the note, and an empty note says so`() {
        val r = report("", null, emptyList(), null)
        assertTrue(r.contains("note:\n  (none)"))
        assertFalse(r.contains("game ("))
        assertFalse(r.contains("history"))
        assertFalse(r.contains("position"))
    }

    @Test
    fun `dictation adds to what was typed`() {
        assertEquals("hello", Feedback.addSpoken("", " hello "))
        assertEquals("the bank is empty and it still let me trade", Feedback.addSpoken("the bank is empty ", "and it still let me trade"))
        assertEquals("typed", Feedback.addSpoken("typed", "  "))
    }
}
