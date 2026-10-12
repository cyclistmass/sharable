package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 3D board: buildings and the Bandit stand up toward the camera like the flat board's pieces (Mauro, 2026-10-12). */
class LiveLiftTest {
    private val piece = LivePlacement("default_town", Offset(1.5f, -0.7f), 0.03f, scale = 0.8f, heading = 0.6f, owner = 1)

    @Test
    fun `no lift is the plain placement`() {
        val plain = liveModelMatrix(piece)
        assertArrayEquals(plain, liveModelMatrix(piece, 0f, 1.2f), 1e-6f)
        assertEquals(1.5f, plain[12]); assertEquals(0.03f, plain[13]); assertEquals(-0.7f, plain[14])
    }

    @Test
    fun `a lifted piece leans toward the camera, keeps its base, size and shape, at every twist`() {
        for (rotation in listOf(0f, 0.7f, 2f, -2.5f, 3.1f)) for (lift in listOf(0.1f, 0.5f, 0.9f)) {
            val m = liveModelMatrix(piece, lift, rotation)
            // Its up axis (column 1) points toward the eye's ground direction (sin r, cos r), lift radians off vertical.
            val up = floatArrayOf(m[4], m[5], m[6]).map { it / piece.scale }
            assertEquals(cos(lift), up[1], 1e-5f)
            assertEquals(sin(lift) * sin(rotation), up[0], 1e-5f)
            assertEquals(sin(lift) * cos(rotation), up[2], 1e-5f)
            // Still a rotation times the scale: columns the same length and square to each other.
            val cols = (0..2).map { c -> floatArrayOf(m[c * 4], m[c * 4 + 1], m[c * 4 + 2]) }
            for (c in cols) assertEquals(piece.scale, sqrt(c.sumOf { (it * it).toDouble() }).toFloat(), 1e-5f)
            for (a in 0..2) for (b in a + 1..2) assertEquals(0f, (0..2).sumOf { (cols[a][it] * cols[b][it]).toDouble() }.toFloat(), 1e-5f)
            // The base stays where it was.
            assertEquals(1.5f, m[12]); assertEquals(0.03f, m[13]); assertEquals(-0.7f, m[14])
        }
    }

    @Test
    fun `after a turn every standing piece faces the camera, then leans toward it`() {
        // Mauro: "buildings should tilt towards viewer after rotate". Their front (+Z, like the digits) must point at the
        // eye's ground direction (sin r, cos r) at every Turn-bar step and twist, not stay at the board's 0.
        val pieces = listOf("bandit", "default_homestead", "pirate_town", "default_ramparts", "default_capital_crown", "pirate_knight_1")
        for (model in pieces) for (rotation in listOf(0f, 1.5707964f, 3.1415927f, 4.712389f, 0.7f, -2.5f)) for (lift in listOf(0f, 0.4f, 0.9f)) {
            val p = LivePlacement(model, Offset(0.4f, 1.1f), scale = 0.8f)
            val m = liveStandingMatrix(p, lift, rotation)
            val front = floatArrayOf(m[8], m[9], m[10]).map { it / p.scale }
            assertEquals(cos(lift) * sin(rotation), front[0], 1e-5f, "$model at $rotation")
            assertEquals(cos(lift) * cos(rotation), front[2], 1e-5f, "$model at $rotation")
            val up = floatArrayOf(m[4], m[5], m[6]).map { it / p.scale }
            assertEquals(sin(lift) * sin(rotation), up[0], 1e-5f, "$model leans toward the eye")
            assertEquals(sin(lift) * cos(rotation), up[2], 1e-5f, "$model leans toward the eye")
            assertEquals(0.4f, m[12]); assertEquals(1.1f, m[14])
        }
    }

    @Test
    fun `pieces are seen from 10 degrees above the table until the board tilts past that`() {
        // Mauro picked it, 2026-10-12: side-on with fronts showing on a flat or low board.
        for (tilt in listOf(0f, Camera.START_TILT, 0.5f, Camera.MAX_TILT)) {
            val seenFrom = Math.PI.toFloat() / 2f - (tilt + liveLift(tilt)) // elevation of the eye above the piece's table
            assertEquals(LIVE_PIECE_VIEW, seenFrom, 1e-5f, "tilt $tilt")
        }
        assertEquals(0f, liveLift(1.5f), 1e-6f)  // tilted past 10° above the table: true 3D, no extra lean
        assertEquals(0f, liveLift(Math.PI.toFloat() / 2f - LIVE_PIECE_VIEW), 1e-6f)
    }

    @Test
    fun `with the switch off, pieces keep the board's heading`() {
        val p = LivePlacement("default_town", Offset(0f, 0f), scale = 1f)
        val m = liveStandingMatrix(p, 0f, 1.2f, facing = false)
        assertArrayEquals(liveModelMatrix(p, 0f, 1.2f), m, 1e-6f)
        assertEquals(0f, m[8], 1e-6f); assertEquals(1f, m[10], 1e-6f)  // front still +Z, not turned to the eye
    }

    @Test
    fun `buildings and the Bandit stand, while tiles, roads, plates and the sea lie flat`() {
        for (m in listOf("bandit", "default_homestead", "pirate_town", "default_ramparts", "pirate_capital_crown", "pirate_knight_1", "samurai_knight_3")) assertTrue(liveStanding(m), m)
        for (m in listOf("sea", "tile_grain_lod0", "default_road_v", "number_plate", "digit_8")) assertFalse(liveStanding(m), m)
    }
}
