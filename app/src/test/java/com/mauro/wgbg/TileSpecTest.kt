package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** The number plate rules in docs/design/art-spec.md, so art and code stay compatible. */
class TileSpecTest {
    /** Visible hex: pointy-top, side 1, inset to 0.93; its flat sides are sqrt(3)/2 * 0.93 from the centre. */
    private val visibleApothem = sqrt(3f) / 2f * 0.93f

    @Test
    fun `plate sits in the lower right quarter of the tile`() {
        assertTrue(NUMBER_TOKEN_X > 0f && NUMBER_TOKEN_Y > 0f, "plate centre ($NUMBER_TOKEN_X, $NUMBER_TOKEN_Y) is not lower right")
    }

    @Test
    fun `plate and its 0_31 clearing stay inside the visible hex`() {
        for (r in listOf(NUMBER_TOKEN_R, 0.31f)) {
            // Distance from the centre to the far side of the circle, along each side's normal (pointy-top: 0, 60, ... degrees).
            for (k in 0 until 6) {
                val a = k * PI.toFloat() / 3f
                val reach = NUMBER_TOKEN_X * cos(a) + NUMBER_TOKEN_Y * sin(a) + r
                assertTrue(reach <= visibleApothem, "radius $r crosses side $k: $reach > $visibleApothem")
            }
        }
        assertTrue(hypot(NUMBER_TOKEN_X, NUMBER_TOKEN_Y) < 0.6f)
    }

    @Test
    fun `plate radius matches the spec`() {
        assertTrue(kotlin.math.abs(NUMBER_TOKEN_R - 0.27f) < 1e-6f)
    }
}
