package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import wgbg.engine.Action
import wgbg.engine.ActionType
import wgbg.engine.GameConfig
import wgbg.engine.Rules

class BoardTapHintTest {
    @Test
    fun `tapping the bandit's own tile while moving it explains why nothing happened`() {
        val s = Rules.newGame(GameConfig(numPlayers = 3), 11)
        val b = s.board
        val robber = s.robberHex
        val other = (0 until b.hexCount).first { it != robber }
        val moving = listOf(Action.moveRobber(other, -1))
        assertNotNull(boardTapHint(b, moving, robber, hexLattice(b, robber)))
        assertNull(boardTapHint(b, moving, robber, hexLattice(b, other)))       // a real target: no hint
        assertNull(boardTapHint(b, Rules.legalActions(s), robber, hexLattice(b, robber)))   // not moving the bandit now
        assert(Rules.legalActions(s).none { it.type == ActionType.MOVE_ROBBER })
    }
}
