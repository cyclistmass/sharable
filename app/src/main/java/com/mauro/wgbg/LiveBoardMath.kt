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
