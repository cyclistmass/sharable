package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wgbg.engine.Action
import wgbg.engine.DevCard
import wgbg.engine.GameConfig
import wgbg.engine.Expansion
import wgbg.engine.Progress
import wgbg.engine.Rules

class CardHintsTest {
    private val board = Rules.newGame(GameConfig(expansion = Expansion.CITIES_AND_KNIGHTS, numPlayers = 3), 1).board

    @Test
    fun `every card has a hint`() {
        assertEquals(Progress.COUNT, PROGRESS_HINTS.size)
        assertTrue(PROGRESS_HINTS.all { it.isNotBlank() })
        assertEquals(DevCard.COUNT, FORTUNE_HINTS.size)
    }

    @Test
    fun `board cards glow on their spot and a tap there picks them`() {
        val envoy = listOf(Action.playProgress(Progress.DIPLOMAT, 5), Action.playProgress(Progress.DIPLOMAT, 9))
        assertTrue(isBoardCard(envoy))
        assertEquals(setOf(5, 9), Targets.ofCards(envoy).edges)
        assertEquals(listOf(envoy[1]), movesAt(board, envoy, edgeLattice(board, 9)))
        val abbot = listOf(Action.playProgress(Progress.BISHOP, 3))
        assertEquals(setOf(3), Targets.ofCards(abbot).hexes)
        assertEquals(abbot, movesAt(board, abbot, hexLattice(board, 3)))
        assertFalse(isBoardCard(listOf(Action.playProgress(Progress.SPY, 1))))
    }

    @Test
    fun `the Tinkerer picks two tiles - first tap narrows, second tap chooses`() {
        val swaps = listOf(Action.playProgress(Progress.INVENTOR, 2, 7), Action.playProgress(Progress.INVENTOR, 2, 11), Action.playProgress(Progress.INVENTOR, 4, 7))
        assertEquals(setOf(2, 4), Targets.ofCards(swaps).hexes)
        val first = movesAt(board, swaps, hexLattice(board, 2))
        assertEquals(swaps.take(2), first)
        assertEquals(setOf(7, 11), Targets.ofCards(first, second = true).hexes)
        assertEquals(listOf(swaps[1]), movesAt(board, first, hexLattice(board, 11), second = true))
    }

    @Test
    fun `player choices show names, not numbers`() {
        val s = Rules.newGame(GameConfig(expansion = Expansion.CITIES_AND_KNIGHTS, numPlayers = 3), 1)
        assertEquals("Blue", progressOption(s, Action.playProgress(Progress.SPY, 1), listOf("You", "Blue", "Orange")))
    }
}
