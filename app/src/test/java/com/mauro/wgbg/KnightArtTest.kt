package com.mauro.wgbg

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnightArtTest {
    @Test
    fun `both themes have three levels of eight tilt steps with matching body and trim`() {
        for (pirate in listOf(false, true)) {
            val levels = KnightArt.levels(pirate)
            assertEquals(3, levels.size)
            for (p in levels) {
                assertEquals(8, p.body.size)
                assertEquals(8, p.trim.size)
                assertEquals(16, (p.body + p.trim).toSet().size, "no frame used twice")
            }
        }
    }

    /** Applies the 4x5 matrix [m] (values 0..1, offsets in column 4) to an RGBA colour. */
    private fun apply(m: FloatArray, c: FloatArray) = FloatArray(4) { r -> (0 until 4).sumOf { k -> (m[r * 5 + k] * c[k]).toDouble() }.toFloat() + m[r * 5 + 4] }

    @Test
    fun `an inactive knight's body is its tint dimmed toward grey, alpha untouched`() {
        for (color in listOf(Color(0xFFD03030), Color(0xFF3060D0), Color(0xFFE08A20), Color(0xFFF0F0F0))) {
            val p = floatArrayOf(color.red, color.green, color.blue)
            for (body in listOf(floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(0.8f, 0.6f, 0.4f, 0.5f))) {
                val tinted = FloatArray(3) { body[it] * p[it] }
                val luma = 0.299f * tinted[0] + 0.587f * tinted[1] + 0.114f * tinted[2]
                val out = apply(knightInactiveMatrix(color), body)
                for (ch in 0 until 3) assertEquals(KNIGHT_IDLE_VALUE * (KNIGHT_IDLE_SAT * tinted[ch] + (1 - KNIGHT_IDLE_SAT) * luma), out[ch], 1e-5f)
                assertEquals(body[3], out[3], 1e-6f)
            }
        }
    }

    @Test
    fun `idle knights of different players stay different colours`() {
        val white = floatArrayOf(1f, 1f, 1f, 1f)
        val red = apply(knightInactiveMatrix(Color(0xFFD03030)), white)
        val blue = apply(knightInactiveMatrix(Color(0xFF3060D0)), white)
        assertTrue(red[0] > red[2] + 0.1f && blue[2] > blue[0] + 0.1f, "red ${red.toList()} blue ${blue.toList()}")
    }

    @Test
    fun `corners draw back to front, so the nearer piece is on top`() {
        val ys = floatArrayOf(5f, 1f, 3f, 1f, 9f)
        assertEquals(listOf(1, 3, 2, 0, 4), cornerDrawOrder(ys.size) { ys[it] })
        assertEquals(emptyList<Int>(), cornerDrawOrder(0) { 0f })
    }
}
