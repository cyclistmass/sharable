package com.mauro.wgbg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashReportTest {
    @Test
    fun `a report names the error, build, device, screen and the last moves, then the stack`() {
        val error = IllegalStateException("boom")
        val r = CrashReport.format(error, "0.2.1", 3, "Pixel 8, Android 16 (API 36)", "2400x1080 px, density 2.625",
            listOf("turn 1 BUILD_INITIAL_ROAD p0 BUILD_ROAD:12"), 0L)
        for (part in listOf("version: 0.2.1 (3)", "device: Pixel 8", "screen: 2400x1080", "error: java.lang.IllegalStateException: boom",
            "BUILD_ROAD:12", "stack:", "CrashReportTest")) assertTrue(part in r, "missing '$part'")
        assertTrue(r.indexOf("last moves") < r.indexOf("stack:"), "moves before the stack")
    }

    @Test
    fun `the QR text is capped and says so`() {
        assertEquals("short", CrashReport.forQr("short"))
        val long = "x".repeat(5000)
        val q = CrashReport.forQr(long)
        assertTrue(q.length <= 1500 && q.endsWith("[truncated]"))
        assertTrue(qrMatrix(q).width > 0, "fits in a QR code")
    }

    @Test
    fun `only the last 40 moves are kept`() {
        repeat(100) { CrashReport.note("move $it") }
        val lines = CrashReport.recentLines()
        assertEquals(40, lines.size)
        assertEquals("move 60", lines.first()); assertEquals("move 99", lines.last())
    }
}
