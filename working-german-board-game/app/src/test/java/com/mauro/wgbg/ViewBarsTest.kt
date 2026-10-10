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
}
