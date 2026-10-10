package com.mauro.wgbg

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import wgbg.engine.Track
import kotlin.math.PI
import kotlin.math.atan2

/**
 * The rendered buildings (#41) loaded for one theme: per piece 8 tilt steps of a wood body (tinted to the
 * player's colour when drawn) and an untinted trim. Age of Chivalry pieces load only when [chivalry].
 */
class BuildingSprites(res: Resources, pirate: Boolean, chivalry: Boolean) {
    class Piece(val body: List<ImageBitmap>, val trim: List<ImageBitmap>)

    private fun load(res: Resources, p: BuildingArt.Piece) =
        Piece(p.body.map { ImageBitmap.imageResource(res, it) }, p.trim.map { ImageBitmap.imageResource(res, it) })

    val homestead = load(res, BuildingArt.homestead(pirate))
    val town = load(res, BuildingArt.town(pirate))
    /** Roads by board edge direction: upright, rising to the right (+30) and falling to the right (-30). */
    val roadV = load(res, BuildingArt.roadV(pirate))
    val roadP30 = load(res, BuildingArt.roadP30(pirate))
    val roadM30 = load(res, BuildingArt.roadM30(pirate))
    val ramparts = if (chivalry) load(res, BuildingArt.ramparts(pirate)) else null
    private val capitals = if (chivalry) BuildingArt.capitals(pirate).map { load(res, it) } else emptyList()

    /** The Capital for a metropolis on [track]: Guilds for trade, Crown for politics, Academy for science. */
    fun capital(track: Int): Piece? = capitals.getOrNull(capitalIndex(track))

    /** The road sprite for an edge running from lattice ([dx], [dy]) (y down). */
    fun roadFor(dx: Float, dy: Float): Piece = when (roadKind(dx, dy)) {
        0 -> roadV
        1 -> roadP30
        else -> roadM30
    }

    companion object {
        /** Hex units across one 256 px frame (193 px per hex unit). */
        const val FRAME_UNITS = 256f / 193f
        /** The road policy from the art report: two 0.66-long sprites centred this far either side of an edge's middle. */
        const val ROAD_OFFSET = 0.17f

        /** Index into BuildingArt.capitals (Guilds, Academy, Crown) for a metropolis [track], or -1. */
        fun capitalIndex(track: Int): Int = when (track) { Track.TRADE -> 0; Track.SCIENCE -> 1; Track.POLITICS -> 2; else -> -1 }

        /** 0 = upright edge, 1 = rising to the right (screen 150 degrees), 2 = falling to the right (30 degrees). */
        fun roadKind(dx: Float, dy: Float): Int {
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() % 180f
            if (deg < 0f) deg += 180f
            return when {
                deg in 60f..120f -> 0
                deg > 120f -> 1
                else -> 2
            }
        }
    }
}

/**
 * Draws [piece] with its contact point at [at], [unitPx] screen px per hex unit, the board tilted [tilt] radians
 * (neighbouring tilt steps cross-fade, like the bandit), its body tinted [color], turned [degrees] about [at].
 */
internal fun DrawScope.drawPieceSprite(piece: BuildingSprites.Piece, at: Offset, unitPx: Float, tilt: Float, color: Color, degrees: Float = 0f) {
    val side = BuildingSprites.FRAME_UNITS * unitPx
    if (side < 2f || !side.isFinite()) return
    val (a, b, f) = banditSteps(tilt)
    val dst = IntOffset((at.x - side / 2).toInt(), (at.y - side / 2).toInt())
    val size = IntSize(side.toInt(), side.toInt())
    val tint = ColorFilter.tint(color, BlendMode.Modulate)
    fun layer(i: Int, alpha: Float) {
        val body = piece.body[i]; val trim = piece.trim[i]
        drawImage(body, IntOffset.Zero, IntSize(body.width, body.height), dst, size, alpha = alpha, colorFilter = tint, filterQuality = FilterQuality.Medium)
        drawImage(trim, IntOffset.Zero, IntSize(trim.width, trim.height), dst, size, alpha = alpha, filterQuality = FilterQuality.Medium)
    }
    if (degrees == 0f) {
        layer(a, 1f); if (f > 0.02f) layer(b, f)
    } else rotate(degrees, at) { layer(a, 1f); if (f > 0.02f) layer(b, f) }
}

/** Degrees to turn a supply's pieces so they face their owner at [seatAngle] (radians; the viewer's seat is 90 degrees). */
internal fun faceOwnerDegrees(seatAngle: Float): Float = Math.toDegrees((seatAngle - PI.toFloat() / 2f).toDouble()).toFloat()
