package com.mauro.wgbg

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PieceColourTest {
    private fun apply(m: FloatArray, c: FloatArray) = FloatArray(3) { r -> (0 until 3).sumOf { k -> (m[r * 5 + k] * c[k]).toDouble() }.toFloat() }
    private fun sat(c: FloatArray) = c.max() - c.min()

    @Test
    fun `saturation 1 changes nothing, more spreads the channels, less pulls them together, grey stays grey`() {
        val warm = floatArrayOf(0.7f, 0.5f, 0.3f)
        val same = apply(saturationMatrix(1f), warm)
        for (i in 0 until 3) assertEquals(warm[i], same[i], 1e-5f)
        assertTrue(sat(apply(saturationMatrix(1.2f), warm)) > sat(warm))
        assertTrue(sat(apply(saturationMatrix(0.9f), warm)) < sat(warm))
        val grey = apply(saturationMatrix(1.2f), floatArrayOf(0.5f, 0.5f, 0.5f))
        for (v in grey) assertEquals(0.5f, v, 1e-4f)
    }

    @Test
    fun `pieces are brighter and more vivid than a plain tint of the same wood`() {
        val wood = floatArrayOf(0.8f, 0.6f, 0.4f)
        for (color in PLAYER_COLORS) {
            val plain = FloatArray(3) { wood[it] * floatArrayOf(color.red, color.green, color.blue)[it] }
            val piece = apply(pieceMatrix(color), wood)
            assertTrue(piece.sum() > plain.sum(), "brighter for $color")
            if (sat(plain) > 0.05f) assertTrue(sat(piece) > sat(plain), "more vivid for $color")
        }
    }
}
