package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI

/**
 * A critter on the game's sea (docs/design/critter-spec.md): the [CritterRunner] ticks every 0.1 s and the board shows
 * it between ticks, smoothly. Positions are in the game's lattice (y down); the runner's board frame has y up.
 */
internal class SeaCritter(val module: CritterModule, private val seed: Long,
                          /** Runs the start-up search (a few thousand coast checks): off the UI thread in the app, inline in tests. */
                          private val launch: (() -> Unit) -> Unit = { it() }) {
    val spec: CritterSpec get() = module.spec
    /** Starts once the open water in view is known and its start is found; null until then, or if there is no room. */
    var runner: CritterRunner? = null; private set
    private var tried = false
    @Volatile private var found: CritterRunner? = null
    private var lastStage = ""
    private var clock = 0.0
    private var px = 0.0; private var py = 0.0; private var ph = 0.0

    /** Advances [dt] seconds in [view] (at most a few seconds' worth at once, so a long pause doesn't stall a frame). */
    fun update(dt: Float, view: Rect) {
        if (!tried) {
            tried = true
            launch {
                // A small screen leaves little open water: widen the margin until the critter has room.
                val r = listOf(CritterBoard.VIEW_MARGIN, 1.5, 2.0).firstNotNullOfOrNull { m -> runCatching { CritterRunner(spec, seed, CritterBoard.forView(view, m)) }.getOrNull() }
                critterLog("${spec.id} " + (r?.let { "starts at %.1f,%.1f".format(it.x, -it.y) } ?: "has no room") +
                    " in view %.1f..%.1f x %.1f..%.1f".format(view.left, view.right, view.top, view.bottom))
                found = r
            }
        }
        if (runner == null) found?.let { runner = it; px = it.x; py = it.y; ph = it.heading }
        val r = runner ?: return
        clock += dt.coerceAtMost(3f)
        while (clock >= CritterRunner.DT) {
            px = r.x; py = r.y; ph = r.heading
            r.step(); clock -= CritterRunner.DT
            if (r.stage.id != lastStage) { lastStage = r.stage.id; critterLog("${spec.id} %s at %.1f,%.1f".format(lastStage, r.x, -r.y)) }
        }
    }

    private val f get() = (clock / CritterRunner.DT).coerceIn(0.0, 1.0)

    /** Where it is now, in the game's lattice. */
    val at: Offset get() = runner?.let { Offset((px + (it.x - px) * f).toFloat(), -(py + (it.y - py) * f).toFloat()) } ?: Offset.Zero

    /** Its course on the sea (y down) in radians, for [CritterModule.headingIndex]. */
    val course: Float get() = runner?.let { (-(ph + CritterRunner.wrap(it.heading - ph) * f) * PI / 180).toFloat() } ?: 0f

    fun pose(): CritterPose? = runner?.pose()

    companion object {
        /**
         * The game's sea critters: each spec rolls its [CritterSpec.chance] once per game on its own random stream,
         * seeded from the board, so every phone in an online game agrees whether there is a whale (where it swims
         * follows each screen's open water). Only the standard island.
         */
        fun forGame(modules: List<CritterModule>, boardSeed: Long, landHexes: Int, launch: (() -> Unit) -> Unit = { it() }): List<SeaCritter> =
            if (landHexes != 19) emptyList() else modules.mapIndexedNotNull { i, m ->
                val s = m.spec
                val roll = CritterRandom(boardSeed * 31 + i * 7919 + 17)
                if (s.count[1] < 1 || roll.next() >= s.chance) null else SeaCritter(m, (boardSeed * 131 + i) and 0xffffffffL, launch)
            }
    }
}

/** A number from the board's tiles and numbers: the same on every phone in a game, different from game to game. */
internal fun boardSeed(s: wgbg.engine.GameState): Long =
    (0 until s.board.hexCount).fold(17L) { acc, h -> acc * 31 + s.board.hexResource[h] * 13 + s.numberAt(h) } and 0x7fffffffL

/** Agent hook (WGBG-CRITTER in logcat); quiet in unit tests, where android.util.Log isn't there. */
internal fun critterLog(msg: String) { runCatching { android.util.Log.i("WGBG-CRITTER", msg) } }
