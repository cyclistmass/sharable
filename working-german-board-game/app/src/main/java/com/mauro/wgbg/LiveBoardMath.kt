package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import wgbg.engine.Board
import wgbg.engine.GameState
import wgbg.engine.Piece
import kotlin.math.*

/** glTF is Y-up: the game's downward lattice y is glTF z, in hex-side units. */
internal fun liveVertex(board: Board, v: Int): Offset = board.topology.vertexPos[v].let {
    Offset(it.x * sqrt(3f) / 2f, it.y / 2f)
}

internal fun liveHex(board: Board, h: Int): Offset = board.topology.hexVertices[h]
    .map { liveVertex(board, it) }.reduce { a, b -> a + b } / 6f

/** Orthographic camera shared by Filament, target drawing and tap inversion, including free orbit. */
internal data class LiveProjection(val center: Offset, val scale: Float, val tilt: Float, val rotation: Float) {
    init {
        require(scale.isFinite() && scale > 0f)
        require(tilt.isFinite() && tilt in 0f..Camera.MAX_TILT)
        require(rotation.isFinite())
    }

    fun project(p: Offset, height: Float = 0f): Offset {
        val c = cos(rotation); val s = sin(rotation)
        return center + Offset((c * p.x - s * p.y) * scale,
            ((s * p.x + c * p.y) * cos(tilt) - height * sin(tilt)) * scale)
    }

    fun unproject(p: Offset): Offset {
        val q = (p - center) / scale
        val z = q.y / cos(tilt)
        val c = cos(rotation); val s = sin(rotation)
        return Offset(c * q.x + s * z, -s * q.x + c * z)
    }
}

internal fun woodTint(materialName: String): Boolean = materialName == "WoodBody"

/** Runtime plate moved into the lower third, inside the low foreground of the realtime exports. */
internal val LIVE_PLATE = Offset(0.36f, 0.45f)

internal data class LivePlacement(val model: String, val position: Offset, val height: Float = 0f,
    val scale: Float = 1f, val heading: Float = 0f, val owner: Int = -1)

/**
 * Column-major glTF Y-axis transform; positive heading cancels the camera's screen orbit. A [lift] (radians) then tips
 * the model about its base toward a camera orbited by [rotation], so it is seen from that much steeper than the board.
 */
internal fun liveModelMatrix(p: LivePlacement, lift: Float = 0f, rotation: Float = 0f): FloatArray {
    val c = cos(p.heading) * p.scale; val s = sin(p.heading) * p.scale
    val h = floatArrayOf(c, 0f, -s, 0f, p.scale, 0f, s, 0f, c) // columns
    if (lift == 0f) return floatArrayOf(h[0], h[1], h[2], 0f, h[3], h[4], h[5], 0f, h[6], h[7], h[8], 0f,
        p.position.x, p.height, p.position.y, 1f)
    // Rodrigues about the camera's ground-level right axis (cos r, 0, -sin r): up turns toward the eye (sin r, 0, cos r).
    val ax = cos(rotation); val az = -sin(rotation)
    val ct = cos(lift); val st = sin(lift); val k = 1f - ct
    val r = floatArrayOf( // columns
        ct + ax * ax * k, az * st, ax * az * k,
        -az * st, ct, ax * st,
        ax * az * k, -ax * st, ct + az * az * k)
    val m = FloatArray(9)
    for (col in 0..2) for (row in 0..2) m[col * 3 + row] = (0..2).sumOf { (r[it * 3 + row] * h[col * 3 + it]).toDouble() }.toFloat()
    return floatArrayOf(m[0], m[1], m[2], 0f, m[3], m[4], m[5], 0f, m[6], m[7], m[8], 0f, p.position.x, p.height, p.position.y, 1f)
}

/** Pieces that stand up (buildings, the Bandit): tipped toward the camera like the flat board's sprites. */
internal fun liveStanding(model: String): Boolean =
    model == "bandit" || model.endsWith("_homestead") || model.endsWith("_town") || model.endsWith("_ramparts") || "_capital_" in model

/**
 * How far standing pieces tip toward the camera at board [tilt]: the flat board shows them [pieceLiftSteps] of its 8
 * tilt steps steeper than the land (Mauro: "3d view does not have tilt on buildings"), never past the steepest view.
 */
internal fun liveLift(tilt: Float, steps: Float = pieceLiftSteps): Float =
    (minOf(Camera.MAX_TILT, tilt + steps * Camera.MAX_TILT / 7f) - tilt).coerceAtLeast(0f)

/** Reads only real game state. No fixture pieces are added to the playing board. */
internal fun livePlacements(s: GameState, pirate: Boolean, lod: Int): List<LivePlacement> = buildList {
    require(lod in 0..1)
    val b = s.board
    val theme = if (pirate) "pirate" else "default"
    val terrain = listOf("timber", "clay", "fleece", "grain", "stone", "desert")
    add(LivePlacement("sea", Offset.Zero, -0.24f))
    for (h in 0 until b.hexCount) {
        val at = liveHex(b, h)
        add(LivePlacement("tile_${terrain.getOrElse(b.hexResource[h]) { "desert" }}_lod$lod", at))
        if (b.hexNumber[h] > 0) {
            add(LivePlacement("number_plate", at + LIVE_PLATE, 0.012f))
            if (h != s.robberHex) add(LivePlacement("digit_${b.hexNumber[h]}", at + LIVE_PLATE, 0.039f))
        }
    }
    if (s.robberHex >= 0) add(LivePlacement("bandit", liveHex(b, s.robberHex) + LIVE_PLATE, 0.012f))
    for (e in 0 until b.edgeCount) {
        val owner = s.edgeOwner[e]
        if (owner < 0) continue
        val a = liveVertex(b, b.edgeA[e]); val z = liveVertex(b, b.edgeB[e])
        val dir = (z - a) / (z - a).getDistance()
        for (side in listOf(-1f, 1f)) add(LivePlacement("${theme}_road_v", (a + z) / 2f + dir * side * ROAD_SPREAD,
            scale = ROAD_SCALE, heading = atan2(dir.x, dir.y), owner = owner))
    }
    for (v in 0 until b.vertexCount) {
        val owner = s.vertexOwner[v]
        if (owner < 0) continue
        val at = liveVertex(b, v)
        val ck = s.ck
        val walled = ck?.wall?.get(v) == 1
        if (walled) add(LivePlacement("${theme}_ramparts", at, scale = BUILDING_SCALE, owner = owner))
        val metro = ck?.metroVertex?.indexOf(v) ?: -1
        val kind = when {
            metro >= 0 -> "capital_${listOf("guilds", "academy", "crown")[metro]}"
            s.vertexBuilding[v] == Piece.CITY -> "town"
            else -> "homestead"
        }
        add(LivePlacement("${theme}_$kind", at, if (walled) 0.10f * BUILDING_SCALE else 0f,
            BUILDING_SCALE * if (walled) 0.9f else 1f, owner = owner))
    }
}

/** Side-by-side imported models for maintainer review; never mixed into a game state. */
internal fun liveInventoryPlacements(): List<LivePlacement> = buildList {
    add(LivePlacement("sea", Offset.Zero, -.24f, 2f))
    val terrains = listOf("timber", "clay", "fleece", "grain", "stone", "desert")
    for (lod in 0..1) for ((i, terrain) in terrains.withIndex()) {
        val at = Offset(-6.5f + i * 2.6f, -4f + lod * 2.7f)
        add(LivePlacement("tile_${terrain}_lod$lod", at))
        add(LivePlacement("number_plate", at + LIVE_PLATE, .012f))
        add(LivePlacement("digit_${listOf(6, 8, 5, 9, 10, 11)[i]}", at + LIVE_PLATE, .039f))
    }
    val kinds = listOf("homestead", "town", "ramparts", "capital_guilds", "capital_academy", "capital_crown", "road_v", "road_p30", "road_m30")
    for ((row, theme) in listOf("default", "pirate").withIndex()) for ((i, kind) in kinds.withIndex())
        add(LivePlacement("${theme}_$kind", Offset(-7.2f + 1.8f * i, 1.3f + 2f * row), scale = 1.8f, owner = i % 4))
    add(LivePlacement("bandit", Offset(-4f, 5.4f)))
    add(LivePlacement("number_plate", Offset(0f, 5.4f), .012f))
    add(LivePlacement("digit_6", Offset(0f, 5.4f), .039f))
}
