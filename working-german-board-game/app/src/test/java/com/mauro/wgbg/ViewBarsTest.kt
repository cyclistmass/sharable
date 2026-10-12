package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewBarsTest {
    @Test
    fun `zoom bar spans 1x at the bottom to max at the top and round-trips`() {
        assertEquals(Camera.MIN_ZOOM, ViewBars.barToZoom(0f), 1e-5f)
        assertEquals(Camera.MAX_ZOOM, ViewBars.barToZoom(1f), 1e-4f)
        assertEquals(2f, ViewBars.barToZoom(0.5f), 1e-4f) // even steps: halfway is 2x of 1..4x
        for (i in 0..20) { val t = i / 20f; assertEquals(t, ViewBars.zoomToBar(ViewBars.barToZoom(t)), 1e-4f) }
    }

    @Test
    fun `tilt bar maps 0 to flat and 1 to the steepest tilt`() {
        assertEquals(0f, ViewBars.barToTilt(0f), 0f)
        assertEquals(Camera.MAX_TILT, ViewBars.barToTilt(1f), 1e-6f)
        for (i in 0..20) { val t = i / 20f; assertEquals(t, ViewBars.tiltToBar(ViewBars.barToTilt(t)), 1e-5f) }
    }

    @Test
    fun `touches clamp to the bar and garbage input is safe`() {
        assertEquals(1f, ViewBars.barAt(-50f, 300f), 0f)
        assertEquals(0f, ViewBars.barAt(400f, 300f), 0f)
        assertEquals(0.5f, ViewBars.barAt(150f, 300f), 1e-6f)
        assertEquals(0f, ViewBars.barAt(Float.NaN, 300f), 0f)
        assertEquals(0f, ViewBars.barAt(10f, 0f), 0f)
        assertEquals(1f, ViewBars.zoomToBar(99f), 0f)
        assertEquals(0f, ViewBars.zoomToBar(0.1f), 0f)
        assertEquals(1f, ViewBars.tiltToBar(5f), 0f)
    }

    @Test
    fun `the Turn bar starts in the middle, turns half a turn each way, and reads back any twist`() {
        val pi = Math.PI.toFloat()
        assertEquals(0.5f, ViewBars.turnToBar(0f), 1e-6f)
        assertEquals(0f, ViewBars.barToTurn(0.5f), 1e-6f)
        assertEquals(pi, ViewBars.barToTurn(1f), 1e-5f)
        assertEquals(-pi, ViewBars.barToTurn(0f), 1e-5f)
        for (t in listOf(0.05f, 0.3f, 0.5f, 0.77f, 0.95f)) assertEquals(t, ViewBars.turnToBar(ViewBars.barToTurn(t)), 1e-5f)
        // A finger twist can leave any angle; the knob shows it wrapped, never off the bar.
        assertEquals(ViewBars.turnToBar(0.4f), ViewBars.turnToBar(0.4f + 2 * pi), 1e-5f)
        assertEquals(ViewBars.turnToBar(-0.4f), ViewBars.turnToBar(2 * pi - 0.4f), 1e-5f)
        assertEquals(0.5f, ViewBars.turnToBar(Float.NaN), 1e-6f)
        for (r in listOf(-20f, -3.2f, 3.2f, 20f)) assertEquals(true, ViewBars.turnToBar(r) in 0f..1f)
    }
}
