package com.mauro.wgbg

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import wgbg.engine.Board
import wgbg.engine.GameState
import kotlin.math.cos
import kotlin.math.roundToInt

/** The 3D board's knights at this share of the flat board's size (Mauro: "scale down a little", then 30% smaller again). */
internal const val LIVE_KNIGHT_SHARE = 0.49f

/** Existing knight sprites and trade labels remain screen overlays for this prototype. */
internal fun DrawScope.drawLiveOverlay(s: GameState, p: LiveProjection, knights: List<ImageBitmap>, knightModel: (Int) -> Boolean = { false }) {
    val ck = s.ck
    if (ck != null) for (v in 0 until s.board.vertexCount) {
        val owner = ck.knightOwner[v]
        if (owner < 0 || knightModel(v)) continue   // drawn as a 3D model instead
        val at = p.project(liveVertex(s.board, v))
        val size = p.scale * KNIGHT_SCALE * LIVE_KNIGHT_SHARE
        val image = knights.getOrNull((ck.knightLevel[v].coerceIn(1, 3) - 1) * 2 + if (ck.isActive(v)) 1 else 0)
        drawCircle(PLAYER_COLORS[owner], size * .22f, at)
        if (image != null) drawImage(image, dstOffset = IntOffset((at.x - size / 2).roundToInt(), (at.y - size * .92f).roundToInt()),
            dstSize = IntSize(size.roundToInt(), size.roundToInt()))
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = p.scale * .14f; color = 0xFFF1D58A.toInt()
    }
    for (port in s.board.ports) {
        val mid = (liveVertex(s.board, port.v1) + liveVertex(s.board, port.v2)) / 2f
        val at = p.project(mid + coastOutward(s.board, port.v1, port.v2) * PORT_BUOY_OUT)
        val r = p.scale * .29f
        drawCircle(Color(0xFF422A1B), r, at)
        drawCircle(Color(0xFFAD8745), r, at, style = Stroke(p.scale * .025f))
        val label = if (port.type < 0) "3:1" else "2:1"
        drawContext.canvas.nativeCanvas.drawText(label, at.x, at.y, paint)
        if (port.type >= 0) drawContext.canvas.nativeCanvas.drawText(
            listOf("Timber", "Clay", "Fleece", "Grain", "Stone")[port.type], at.x, at.y + p.scale * .15f, paint)
    }
}

internal fun DrawScope.drawLiveTargets(b: Board, targets: Targets, p: LiveProjection, color: Color, pieceOn: (Int) -> Boolean = { false }) {
    for (v in targets.vertices) drawCircle(color, p.scale * .13f, p.project(liveVertex(b, v)), style = Stroke(p.scale * .035f))
    for (e in targets.edges) {
        // Clear of the pieces at its ends, like the flat board (the glow never covers a homestead).
        val v0 = liveVertex(b, b.edgeA[e]); val v1 = liveVertex(b, b.edgeB[e])
        val (f0, f1) = edgeGlowSpan(pieceOn(b.edgeA[e]), pieceOn(b.edgeB[e]))
        drawLine(color, p.project(v0 + (v1 - v0) * f0), p.project(v0 + (v1 - v0) * f1), p.scale * .055f)
    }
    for (h in targets.hexes) {
        val at = p.project(liveHex(b, h)); val r = p.scale * .33f
        drawOval(color, at - Offset(r, r * cos(p.tilt)), androidx.compose.ui.geometry.Size(r * 2f, r * cos(p.tilt) * 2f),
            style = Stroke(p.scale * .04f))
    }
}
