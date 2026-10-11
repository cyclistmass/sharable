package com.mauro.wgbg

import wgbg.engine.Piece
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Each player's unplayed pieces (#52, Mauro 2026-10-10), laid on the table just off the board in front of
 * their seat, at board scale and facing their owner: homesteads and towns in neat rows, roads in three
 * rows of five. Seats go round the board by angle, so 2-6 players all work (6 is planned for v3); the
 * viewer's own seat is always at the bottom.
 *
 * All positions are lattice units (hex side = 1, y down) like the board; drawing projects them.
 */
object Supply {
    enum class Kind { HOMESTEAD, TOWN, ROAD }

    /** One piece in a supply: where it sits and (for roads) its half-length vector along the row. */
    data class Spot(val kind: Kind, val x: Float, val y: Float, val dx: Float = 0f, val dy: Float = 0f)

    /** Gap from the island's edge to the supply's centre line. */
    const val REACH = 1.8f
    const val ROAD_ROWS = 3
    private const val HOME_GAP = 0.85f
    private const val TOWN_GAP = 1.15f
    private const val ROAD_GAP = 0.55f
    private const val ROAD_HALF = 0.3f

    /** Radius of the island (outermost corner), as the sea measures it. */
    fun landR(vertexX: FloatArray, vertexY: FloatArray): Float {
        var r = 0f
        for (i in vertexX.indices) r = maxOf(r, hypot(vertexX[i] * sqrt(3f) / 2f, vertexY[i] / 2f))
        return r
    }

    /** Direction (radians, screen y down) from the board's centre to [seat]'s supply; [viewer] sits at the bottom. */
    fun seatAngle(seat: Int, viewer: Int, players: Int): Float {
        val n = players.coerceAtLeast(1)
        val k = ((seat - viewer) % n + n) % n
        return (PI / 2 + k * 2 * PI / n).toFloat()
    }

    /** Where [seat]'s supply is centred before any drag (lattice units). */
    fun home(seat: Int, viewer: Int, players: Int, landR: Float): Pair<Float, Float> {
        val a = seatAngle(seat, viewer, players)
        val r = landR + REACH
        return cos(a) * r to sin(a) * r
    }

    /**
     * The pieces of one supply: [homesteads], [towns] and [roads] left, for a seat at angle [angle], centred at
     * ([cx], [cy]). Rows run left to right as the owner sees them; roads nearest the board, then towns, then
     * homesteads nearest the owner.
     */
    fun layout(homesteads: Int, towns: Int, roads: Int, angle: Float, cx: Float, cy: Float): List<Spot> {
        val ox = cos(angle); val oy = sin(angle)        // outward, toward the owner
        val tx = sin(angle); val ty = -cos(angle)       // along the row, the owner's left to right
        val out = ArrayList<Spot>(maxOf(0, homesteads) + maxOf(0, towns) + maxOf(0, roads))
        fun at(u: Float, v: Float) = (cx + tx * u + ox * v) to (cy + ty * u + oy * v)
        fun row(count: Int, gap: Float, v: Float, kind: Kind) {
            for (i in 0 until count) {
                val (x, y) = at((i - (count - 1) / 2f) * gap, v)
                out += if (kind == Kind.ROAD) Spot(kind, x, y, tx * ROAD_HALF, ty * ROAD_HALF) else Spot(kind, x, y)
            }
        }
        val perRow = (Piece.LIMITS[Piece.ROAD] + ROAD_ROWS - 1) / ROAD_ROWS
        var left = roads.coerceAtLeast(0)
        for (r in 0 until ROAD_ROWS) {
            val n = minOf(perRow, left)
            row(n, ROAD_GAP, -0.75f + r * 0.25f, Kind.ROAD)
            left -= n
        }
        row(towns.coerceAtLeast(0), TOWN_GAP, 0.35f, Kind.TOWN)
        row(homesteads.coerceAtLeast(0), HOME_GAP, 1.2f, Kind.HOMESTEAD)
        return out
    }

    /** Rough reach of a full supply from its centre, for panning (lattice units). */
    const val RADIUS = 1.5f

    /**
     * True when lattice point ([x], [y]) is on the supply centred at ([cx], [cy]) for a seat at [angle]: inside its
     * rows plus a small margin, but never reaching in toward the board's coast and ports.
     */
    fun hit(x: Float, y: Float, cx: Float, cy: Float, angle: Float): Boolean {
        val dx = x - cx; val dy = y - cy
        val u = dx * sin(angle) - dy * cos(angle)   // along the rows
        val v = dx * cos(angle) + dy * sin(angle)   // toward the owner
        return kotlin.math.abs(u) <= 2.4f && v in -0.95f..1.6f
    }

}
