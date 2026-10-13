package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A critter on the game's sea (docs/design/critter-spec.md): the [CritterRunner] ticks every 0.1 s and the board shows
 * it between ticks, smoothly. Positions are in the game's lattice (y down); the runner's board frame has y up.
 */
internal class SeaCritter(val spec: CritterSpec, private val seed: Long) {
    /** Starts on the first [update] that knows the open water in view; null until then, or if there is no room. */
    var runner: CritterRunner? = null; private set
    private var tried = false
    private var lastStage = ""
    private var clock = 0.0
    private var px = 0.0; private var py = 0.0; private var ph = 0.0

    /** Advances [dt] seconds in [view] (at most a few seconds' worth at once, so a long pause doesn't stall a frame). */
    fun update(dt: Float, view: Rect) {
        if (!tried) {
            tried = true
            // A small screen leaves little open water: widen the margin until the critter has room.
            runner = listOf(CritterBoard.VIEW_MARGIN, 1.5, 2.0).firstNotNullOfOrNull { m -> runCatching { CritterRunner(spec, seed, CritterBoard.forView(view, m)) }.getOrNull() }
            runner?.let { px = it.x; py = it.y; ph = it.heading }
            critterLog("${spec.id} " + (runner?.let { "starts at %.1f,%.1f".format(it.x, -it.y) } ?: "has no room") +
                " in view %.1f..%.1f x %.1f..%.1f".format(view.left, view.right, view.top, view.bottom))
        }
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

    /** Its course on the sea (y down) in radians, for [whaleHeadingIndex]. */
    val course: Float get() = runner?.let { (-(ph + CritterRunner.wrap(it.heading - ph) * f) * PI / 180).toFloat() } ?: 0f

    fun pose(): CritterPose? = runner?.pose()

    companion object {
        /**
         * The game's sea critters: each spec rolls its [CritterSpec.chance] once per game on its own random stream,
         * seeded from the board, so every phone in an online game agrees whether there is a whale (where it swims
         * follows each screen's open water). Only the standard island.
         */
        fun forGame(specs: List<CritterSpec>, boardSeed: Long, landHexes: Int): List<SeaCritter> =
            if (landHexes != 19) emptyList() else specs.mapIndexedNotNull { i, s ->
                val roll = CritterRandom(boardSeed * 31 + i * 7919 + 17)
                if (s.count[1] < 1 || roll.next() >= s.chance) null else SeaCritter(s, (boardSeed * 131 + i) and 0xffffffffL)
            }
    }
}

/** A number from the board's tiles and numbers: the same on every phone in a game, different from game to game. */
internal fun boardSeed(s: wgbg.engine.GameState): Long =
    (0 until s.board.hexCount).fold(17L) { acc, h -> acc * 31 + s.board.hexResource[h] * 13 + s.numberAt(h) } and 0x7fffffffL

/** A whale frame: [pose] in [SeaArt.WHALE_POSES] order and how opaque, or null when it is under water. */
internal class WhaleFrame(val pose: Int, val alpha: Float)

/**
 * The flat board's whale frame for a critter pose: swim at its depth, the 8-beat tail and the 3-puff blow from Crest's
 * render; surfacing rises d25 to d50 as it fades in, diving sinks d50 to d25 as it fades out (no render of their own yet).
 */
internal fun whaleFrame(p: CritterPose, clips: Map<String, CritterClip>): WhaleFrame? {
    val anim = p.anim ?: return null
    if (p.opacity <= 0.0) return null
    val clip = anim.substringBefore(':')
    val depth = anim.substringAfter(':', "d50")
    val share = clips[clip]?.let { (p.animTime / it.seconds).coerceIn(0.0, 0.999) } ?: 0.0
    val (name, alpha) = when (clip) {
        "beat" -> "${depth}_beat%02d".format(floor(share * 8).toInt()) to 1f
        "blow" -> "d50_blow${floor(share * 3).toInt() + 1}" to 1f
        "surface" -> (if (share < 0.5) "d25" else "d50") to (share / 0.4).toFloat().coerceIn(0f, 1f)
        "dive" -> (if (share < 0.5) "d50" else "d25") to ((1 - share) / 0.4).toFloat().coerceIn(0f, 1f)
        else -> depth to 1f
    }
    val i = SeaArt.WHALE_POSES.indexOf(name).takeIf { it >= 0 } ?: SeaArt.WHALE_POSES.indexOf("d50")
    return WhaleFrame(i, alpha * p.opacity.toFloat())
}

/** The whale's rendered heading (8 steps, counter-clockwise from east on screen) for a [course] seen with [squash]. */
internal fun whaleHeadingIndex(course: Float, squash: Float): Int {
    val screen = atan2(-sin(course) * squash, cos(course))
    val i = (screen / (2 * PI.toFloat() / SeaArt.WHALE_HEADINGS)).roundToInt()
    return ((i % SeaArt.WHALE_HEADINGS) + SeaArt.WHALE_HEADINGS) % SeaArt.WHALE_HEADINGS
}

/** The whale's frame spans this many hex units: twice the sloop's, as Crest rendered it. */
internal const val WHALE_FRAME = 2f * SeaSprites3d.SLOOP_FRAME

/** Draws the whale at [p] (its waterline), [side] px square. */
internal fun DrawScope.drawWhale(sprites: SeaSprites3d, p: Offset, side: Float, frame: WhaleFrame, heading: Int, tiltStep: Int) {
    if (side < 2f || !side.isFinite() || frame.alpha <= 0.01f) return
    val img = sprites.frame(SeaArt.whale[frame.pose], heading, tiltStep)
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset((p.x - side / 2).toInt(), (p.y - side / 2).toInt()),
        IntSize(side.toInt(), side.toInt()), alpha = frame.alpha, filterQuality = FilterQuality.Medium)
}

/** Agent hook (WGBG-CRITTER in logcat); quiet in unit tests, where android.util.Log isn't there. */
internal fun critterLog(msg: String) { runCatching { android.util.Log.i("WGBG-CRITTER", msg) } }
