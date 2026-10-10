package com.mauro.wgbg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ln
import kotlin.math.pow

/**
 * On-screen view controls (Mauro, 2026-10-10): two upright brass bars on the board's right edge, Zoom and
 * Tilt, beside the pinch / two-finger gestures. Positions are 0 (bottom) .. 1 (top).
 */
object ViewBars {
    /** Zoom grows evenly per bar step (1x at the bottom, [Camera.MAX_ZOOM] at the top). */
    fun zoomToBar(zoom: Float): Float =
        (ln(zoom.coerceIn(Camera.MIN_ZOOM, Camera.MAX_ZOOM) / Camera.MIN_ZOOM) / ln(Camera.MAX_ZOOM / Camera.MIN_ZOOM)).coerceIn(0f, 1f)

    fun barToZoom(t: Float): Float = Camera.MIN_ZOOM * (Camera.MAX_ZOOM / Camera.MIN_ZOOM).pow(t.coerceIn(0f, 1f))

    fun tiltToBar(tilt: Float): Float = (tilt / Camera.MAX_TILT).coerceIn(0f, 1f)

    fun barToTilt(t: Float): Float = t.coerceIn(0f, 1f) * Camera.MAX_TILT

    /** Bar position for a touch at [y] px in a bar [height] px tall (top = 1). */
    fun barAt(y: Float, height: Float): Float = if (height <= 0f || !y.isFinite()) 0f else (1f - y / height).coerceIn(0f, 1f)
}

/** The Zoom and Tilt bars on a small wood plate with a brass edge. */
@Composable
fun ViewBarsPanel(camera: Camera, onZoom: (Float) -> Unit, onTilt: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.brassEdgedWood(3.dp).padding(horizontal = 6.dp, vertical = 8.dp).testTag("view-bars"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrassBar("Zoom", ViewBars.zoomToBar(camera.zoom), "bar-zoom") { onZoom(ViewBars.barToZoom(it)) }
        BrassBar("Tilt", ViewBars.tiltToBar(camera.tilt), "bar-tilt") { onTilt(ViewBars.barToTilt(it)) }
    }
}

/** An upright brass slider, 0 at the bottom to 1 at the top (view bars, Settings' Density). */
@Composable
internal fun BrassBar(label: String, value: Float, tag: String, height: androidx.compose.ui.unit.Dp = 150.dp, onChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.width(30.dp).height(height).testTag(tag)
                .pointerInput(Unit) { detectTapGestures { onChange(ViewBars.barAt(it.y, size.height.toFloat())) } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        onChange(ViewBars.barAt(change.position.y, size.height.toFloat()))
                    }
                },
        ) {
            val w = 8.dp.toPx()
            val x = size.width / 2f
            val knob = 11.dp.toPx()
            val top = knob; val bottom = size.height - knob
            val y = bottom - (bottom - top) * value
            // Dark groove, brass fill from the bottom up to the knob, then the brass knob.
            drawRoundRect(Color(0xFF2A190D), Offset(x - w / 2, top), Size(w, bottom - top), CornerRadius(w / 2))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE3C477), Color(0xFFA77F33))), Offset(x - w / 2, y), Size(w, bottom - y), CornerRadius(w / 2))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF3DC94), Color(0xFFB08436), Color(0xFF6E4E1C)), Offset(x - knob * 0.3f, y - knob * 0.3f), knob * 1.3f), knob, Offset(x, y))
            drawCircle(Color(0xFF4A3020), knob, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        Text(label, fontSize = 11.sp, color = Color(0xFFF1D58A))
    }
}
