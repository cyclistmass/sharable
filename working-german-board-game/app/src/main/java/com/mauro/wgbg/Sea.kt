package com.mauro.wgbg

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import wgbg.engine.Board
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The framed round sea around the island (issue #1), in board lattice units (hex side = 1,
 * origin at the board's center), so it pans, zooms and tilts with the board.
 *
 * The water is animated like a classic hand-painted RTS map: two slow, translucent layers of
 * tonal patches drift over the deep-blue gradient; small hand-painted wavelets (a light crescent
 * over a darker shadow stroke) loop through fade-in, curl and fade-out, each on its own phase,
 * and reappear nearby; a few double-size crests roll through the deeper water; and little
 * clusters of foam curl in and break along the coastline, which is found from the hex geometry.
 * Everything is a pure function of time and deterministic placement, so it loops seamlessly.
 * Clouds and gulls stay over the water; clouds can be poked (poof after a few pokes) or
 * dragged, and tapping the water splashes.
 */
class Sea(board: Board, seed: Int = 1) {
    private val rnd = Random(seed)
    private val topo = board.topology
    private fun lx(v: Int) = topo.vertexPos[v].x * SQRT3 / 2f
    private fun ly(v: Int) = topo.vertexPos[v].y / 2f
    private val land: List<Offset> = topo.hexes.indices.map { h ->
        val vs = topo.hexVertices[h]
        Offset(vs.map { lx(it) }.average().toFloat(), vs.map { ly(it) }.average().toFloat())
    }
    /** Farthest land corner from the center. */
    val landR = topo.vertexPos.maxOf { hypot(it.x * SQRT3 / 2f, it.y / 2f) }

    /** Distance from (x, y) to the island's coast (each land hex approximated by its inscribed circle). */
    internal fun coastDist(x: Float, y: Float): Float {
        var m = Float.MAX_VALUE
        for (h in land) { val d = hypot(x - h.x, y - h.y); if (d < m) m = d }
        return m - HEX_IN
    }

    // ------------------------------------------------------------------ wavelets

    /**
     * A small hand-painted wavelet. It loops every [period] seconds from [phase]; each loop it
     * reappears a little way from its home ([x], [y]) with a slightly different angle and bend.
     * Its crest lies along ([px], [py]); it bows and drifts toward ([nx], [ny]).
     */
    internal class Wavelet(val x: Float, val y: Float, val angle: Float, val len: Float, val period: Float, val phase: Float,
                           val color: Color, val alpha: Float, val big: Boolean, val id: Int)
    internal val wavelets: List<Wavelet>

    /** One breaking spot just off a coastline edge: a cluster of crescents curling in toward the land. */
    internal class Break(val x: Float, val y: Float, val nx: Float, val ny: Float, val period: Float, val phase: Float, val count: Int, val id: Int)
    internal val breaks: List<Break>

    init {
        // Coastline edges: an edge whose two corners share exactly one hex has water on its other side.
        class Coast(val ax: Float, val ay: Float, val bx: Float, val by: Float, val nx: Float, val ny: Float)
        val coast = topo.edgeVertices.mapNotNull { (v1, v2) ->
            val shared = topo.vertexHexes[v1].filter { it in topo.vertexHexes[v2] }
            if (shared.size != 1) return@mapNotNull null
            val c = land[shared[0]]
            val mx = (lx(v1) + lx(v2)) / 2; val my = (ly(v1) + ly(v2)) / 2
            val d = hypot(mx - c.x, my - c.y)
            Coast(lx(v1), ly(v1), lx(v2), ly(v2), (mx - c.x) / d, (my - c.y) / d)
        }
        // Harbor mouths: the water just off each port's edge gets a little extra activity.
        val harbors = board.ports.map { p ->
            val mx = (lx(p.v1) + lx(p.v2)) / 2; val my = (ly(p.v1) + ly(p.v2)) / 2
            val d = hypot(mx, my)
            Offset(mx + mx / d * 0.45f, my + my / d * 0.45f)
        }

        fun color(): Color = rnd.nextFloat().let { if (it < 0.55f) LIGHT_BLUE else if (it < 0.9f) CYAN else PALE }
        val list = ArrayList<Wavelet>()
        // Room for the farthest a wavelet can hop and drift, so it never touches the coast or the rim.
        val roam = HOP * SQRT3 / 1.2f + DRIFT
        /** Adds a wavelet at (x, y) if it fits on the water there; returns whether it did. */
        fun add(x: Float, y: Float, big: Boolean): Boolean {
            val cd = coastDist(x, y)
            val len = (0.075f + rnd.nextFloat() * 0.085f + (if (rnd.nextFloat() < 0.08f) 0.05f else 0f)) * if (big) 2f else 1f
            if (cd < roam + len * 0.4f || hypot(x, y) + roam + len * 0.75f > SEA_R) return false
            // Near the coast crests lie parallel to it; out at sea they take varied angles around a gentle swell.
            val nearest = land.minBy { hypot(x - it.x, y - it.y) }
            val shoreAngle = atan2(y - nearest.y, x - nearest.x) + PI.toFloat() / 2
            val seaAngle = SWELL + (rnd.nextFloat() - 0.5f) * 2.4f
            val w = exp(-cd / 0.5f)
            val angle = if (rnd.nextFloat() < w) shoreAngle + (rnd.nextFloat() - 0.5f) * 0.5f else seaAngle
            list += Wavelet(x, y, angle, len, period = 3.5f + rnd.nextFloat() * 3.5f, phase = rnd.nextFloat(),
                color = if (big) (if (rnd.nextFloat() < 0.6f) PALE else LIGHT_BLUE) else color(),
                alpha = if (big) 0.78f else 0.55f + 0.25f * w, big = big, id = list.size)
            return true
        }
        fun waterPoint(): Pair<Float, Float> {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val d = sqrt(rnd.nextFloat()) * SEA_R
            return cos(a) * d to sin(a) * d
        }
        // Small wavelets: denser near the shore, calmer in open water.
        var small = 0
        while (small < SMALL_WAVELETS) {
            val (x, y) = waterPoint()
            if (rnd.nextFloat() < 0.3f + 0.7f * exp(-coastDist(x, y) / 0.6f) && add(x, y, big = false)) small++
        }
        for (h in harbors) repeat(3) { add(h.x + (rnd.nextFloat() - 0.5f) * 0.5f, h.y + (rnd.nextFloat() - 0.5f) * 0.5f, big = false) }
        // Double-size crests out in the deeper water.
        var big = 0
        while (big < BIG_WAVELETS) { val (x, y) = waterPoint(); if (coastDist(x, y) > 1f && add(x, y, big = true)) big++ }
        wavelets = list

        // Breaking spots: about two thirds of the coastline edges, away from the corners.
        breaks = coast.filter { rnd.nextFloat() < 0.7f }.mapIndexed { i, c ->
            val t = 0.3f + rnd.nextFloat() * 0.4f
            Break(c.ax + (c.bx - c.ax) * t, c.ay + (c.by - c.ay) * t, c.nx, c.ny,
                period = 5f + rnd.nextFloat() * 4f, phase = rnd.nextFloat(), count = 2 + rnd.nextInt(2), id = i)
        }
    }

    /** Where wavelet [w] is in its loop at [t] (0..1) and which loop it is on. */
    private fun loop(w: Wavelet, t: Float): Float { val u = t / w.period + w.phase; return u - floor(u) }
    private fun loopIndex(w: Wavelet, t: Float) = floor(t / w.period + w.phase).toInt()
    /** Opacity of [w] at time [t]: zero at the loop's ends, so it can reappear elsewhere unseen. */
    internal fun waveletAlpha(w: Wavelet, t: Float): Float { val s = sin(PI.toFloat() * loop(w, t)); return w.alpha * s * sqrt(s) }
    /** Centre of [w] at time [t]: home, plus a per-loop hop, plus a slow drift across its crest. */
    internal fun waveletX(w: Wavelet, t: Float) = w.x + hop(w.id, loopIndex(w, t), 0) * HOP + cos(w.angle + PI.toFloat() / 2) * DRIFT * loop(w, t)
    internal fun waveletY(w: Wavelet, t: Float) = w.y + hop(w.id, loopIndex(w, t), 1) * HOP + sin(w.angle + PI.toFloat() / 2) * DRIFT * loop(w, t)

    private class Puff(val x: Float, val y: Float, val r: Float, val life: Float, var age: Float = 0f, val dx: Float = 0f, val dy: Float = 0f)
    class Cloud(var x: Float, var y: Float, val drift: Float, val shape: Int, var poof: Float = -1f) {
        /** Pokes taken so far; the cloud poofs on the [POKES_TO_POOF]th. */
        var hits = 0
        /** Seconds left of the wobble from the last poke. */
        var wobble = 0f
        /** Velocity from a fling, lattice units per second; it dies away on its own. */
        var vx = 0f
        var vy = 0f
    }
    private class Gull(var a: Float, val r: Float, val speed: Float, var phase: Float)
    private class Ring(val x: Float, val y: Float, var age: Float = 0f, val delay: Float = 0f)

    private val drops = ArrayList<Puff>()
    private val rings = ArrayList<Ring>()
    val clouds = ArrayList<Cloud>()
    private val gulls = List(3) { i -> Gull(rnd.nextFloat() * 2 * PI.toFloat(), landR + 0.4f + 0.18f * i, (0.04f + 0.017f * i) * if (i % 2 == 1) -1 else 1, rnd.nextFloat() * 6) }
    private val glints = List(70) { val (x, y) = seaPoint(0.2f); floatArrayOf(x, y, rnd.nextFloat() * 6f, 0.6f + rnd.nextFloat()) }
    internal var time = 0f
        private set

    // Tonal patches: two small tileable noise textures, built once (pixels here, the bitmap lazily on first draw).
    internal val patchPixels = arrayOf(patchTexture(PATCH_A_LOW, PATCH_A_HIGH, seed * 31 + 1), patchTexture(PATCH_B_LOW, PATCH_B_HIGH, seed * 31 + 2))
    private var patchBrushes: List<Pair<BitmapShader, ShaderBrush>>? = null
    private val patchMatrix = Matrix()
    private val crescent = Path()

    init { repeat(4) { clouds += newCloud() } }

    private fun seaPoint(margin: Float): Pair<Float, Float> {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val d = landR + margin + rnd.nextFloat() * (SEA_R - landR - 2 * margin)
        return cos(a) * d to sin(a) * d
    }
    private fun newCloud(): Cloud { val (x, y) = seaPoint(0.35f); return Cloud(x, y, 0.012f + rnd.nextFloat() * 0.02f, rnd.nextInt(3)) }

    /** Advances the animation by [dt] seconds. */
    fun update(dt: Float) {
        time += dt
        for (p in drops) p.age += dt
        drops.removeAll { it.age > it.life }
        for (r in rings) r.age += dt
        rings.removeAll { it.age - it.delay > 1.3f }
        for (g in gulls) { g.a += g.speed * dt; g.phase += dt * 3 }
        for (c in clouds) {
            if (c.poof >= 0) { c.poof += dt; continue }
            c.wobble = (c.wobble - dt).coerceAtLeast(0f)
            val a = atan2(c.y, c.x) + c.drift * dt
            val d = hypot(c.x, c.y)
            c.x = cos(a) * d; c.y = sin(a) * d
            // Momentum from a fling: carries on and slows smoothly to rest, bouncing softly off the bounds.
            if (c.vx != 0f || c.vy != 0f) {
                c.x += c.vx * dt; c.y += c.vy * dt
                val damp = exp(-FLING_DAMPING * dt)
                c.vx *= damp; c.vy *= damp
                if (hypot(c.vx, c.vy) < FLING_REST) { c.vx = 0f; c.vy = 0f }
                bounce(c)
            }
        }
        // A poofed cloud is replaced a few seconds later somewhere else on the sea.
        val gone = clouds.count { it.poof > 3.5f }
        clouds.removeAll { it.poof > 3.5f }
        repeat(gone) { clouds += newCloud() }
    }

    /** True if [p] (lattice) is on open water. */
    fun isWater(p: Offset) = hypot(p.x, p.y).let { it in landR..SEA_R }

    fun cloudAt(p: Offset): Cloud? = clouds.lastOrNull { it.poof < 0 && hypot(p.x - it.x, p.y - it.y) < 0.38f }

    /**
     * Pokes a cloud. It shrugs off the first few pokes (it wobbles, shrinks a little and sheds a
     * few wisps) and poofs on the [POKES_TO_POOF]th.
     */
    fun poof(c: Cloud) {
        if (c.poof >= 0) return
        c.hits++
        if (c.hits < POKES_TO_POOF) {
            c.wobble = WOBBLE
            repeat(3) {
                val a = -PI.toFloat() / 2 + (rnd.nextFloat() - 0.5f) * 2.4f
                drops += Puff(c.x, c.y, 0.045f, 0.45f, dx = cos(a) * 0.3f, dy = sin(a) * 0.3f)
            }
            return
        }
        c.poof = 0f
        repeat(9) { i ->
            val a = i / 9f * 2 * PI.toFloat()
            drops += Puff(c.x, c.y, 0.07f, 0.55f, dx = cos(a) * 0.45f, dy = sin(a) * 0.45f)
        }
    }

    /**
     * Lets go of a dragged cloud with the finger's release velocity ([vx], [vy] in lattice units per
     * second). It keeps moving and slows to rest in about 1.5-2 s; very hard flicks are capped.
     */
    fun fling(c: Cloud, vx: Float, vy: Float) {
        if (c.poof >= 0 || !vx.isFinite() || !vy.isFinite()) return
        val sp = hypot(vx, vy)
        val k = if (sp > FLING_MAX) FLING_MAX / sp else 1f
        c.vx = vx * k; c.vy = vy * k
    }

    /** Keeps [c] between the island and the rim; hitting either reflects its radial speed, softened. */
    private fun bounce(c: Cloud) {
        val d = hypot(c.x, c.y).coerceAtLeast(1e-3f)
        val lo = landR + 0.25f; val hi = SEA_R - 0.25f
        if (d in lo..hi) return
        val nx = c.x / d; val ny = c.y / d
        val vr = c.vx * nx + c.vy * ny
        if ((d > hi && vr > 0) || (d < lo && vr < 0)) {
            c.vx -= (1 + BOUNCE) * vr * nx; c.vy -= (1 + BOUNCE) * vr * ny
        }
        val r = d.coerceIn(lo, hi)
        c.x = nx * r; c.y = ny * r
    }

    /** Drags a cloud, keeping it over the water. Grabbing a moving cloud stops it. */
    fun move(c: Cloud, to: Offset) {
        c.vx = 0f; c.vy = 0f
        val d = hypot(to.x, to.y)
        // At the dead center there's no direction to push out along: keep the cloud's current bearing.
        val (ux, uy) = if (d > 1e-3f) to.x / d to to.y / d else hypot(c.x, c.y).let { c.x / it to c.y / it }
        val r = d.coerceIn(landR + 0.25f, SEA_R - 0.25f)
        c.x = ux * r; c.y = uy * r
    }

    fun splash(p: Offset) {
        repeat(3) { rings += Ring(p.x, p.y, delay = it * 0.14f) }
        repeat(7) {
            val a = -PI.toFloat() / 2 + (rnd.nextFloat() - 0.5f) * 2.2f
            val v = 0.3f + rnd.nextFloat() * 0.3f
            drops += Puff(p.x, p.y, 0.04f + rnd.nextFloat() * 0.04f, 0.75f, dx = cos(a) * v, dy = sin(a) * v)
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Water, waves, surf and splashes: drawn before the tiles. [at] maps lattice to screen; [s] is px per side. */
    fun drawWater(ds: DrawScope, at: (Float, Float) -> Offset, s: Float, squash: Float, woodBrush: Brush) = with(ds) {
        val c = at(0f, 0f)
        val oval = { r: Float -> Rect(c.x - r * s, c.y - r * s * squash, c.x + r * s, c.y + r * s * squash) }
        // Frame shadow and wooden rim.
        oval(SEA_R + RIM + 0.1f).let { drawOval(Color.Black.copy(alpha = 0.45f), it.topLeft + Offset(0f, 0.18f * s), it.size) }
        oval(SEA_R + RIM / 2).let { drawOval(woodBrush, it.topLeft, it.size, style = Stroke(RIM * s)) }
        oval(SEA_R + RIM / 2).let { drawOval(Color.Black.copy(alpha = 0.32f), it.topLeft, it.size, style = Stroke(RIM * s)) }
        oval(SEA_R + RIM).let { drawOval(Color(0x88E2B37C), it.topLeft, it.size, style = Stroke(0.025f * s)) }
        oval(SEA_R).let { drawOval(Color(0xFF3B2412), it.topLeft, it.size, style = Stroke(0.035f * s)) }

        val clip = Path().apply { addOval(oval(SEA_R)) }
        clipPath(clip) {
            drawOval(Brush.radialGradient(
                0.0f to DEEP_SHORE, 0.72f to DEEP_SHORE, 0.86f to DEEP_MID, 1f to DEEP_RIM,
                center = c, radius = SEA_R * s,
            ), oval(SEA_R).topLeft, oval(SEA_R).size)
            drawPatches(this, c, s, squash, oval(SEA_R))
            // Shallows hugging the coast: stacked translucent hex halos read as a soft gradient.
            for ((grow, alpha) in listOf(1.22f to 0.18f, 1.14f to 0.22f, 1.07f to 0.28f)) {
                for (h in land) drawPath(hexPath(at, h, grow), SHALLOW.copy(alpha = alpha))
            }
            // Sun glints twinkling.
            for (g in glints) {
                val a = (sin(time * g[3] + g[2]) * 0.5f + 0.5f)
                if (a > 0.55f) drawCircle(Color.White.copy(alpha = (a - 0.55f) * 1.4f), 0.018f * s, at(g[0], g[1]))
            }
            for (w in wavelets) drawWavelet(this, at, w)
            for (b in breaks) drawBreak(this, at, s, b)
            for (r in rings) {
                val t = ((r.age - r.delay) / 1.2f).coerceIn(0f, 1f)
                if (r.age < r.delay) continue
                val rr = (0.06f + t * 0.6f) * s
                val cc = at(r.x, r.y)
                drawOval(Color.White.copy(alpha = 0.9f * (1 - t)), cc - Offset(rr, rr * 0.62f * squash), Size(2 * rr, 2 * rr * 0.62f * squash), style = Stroke(0.03f * s))
            }
        }
        // Surf line on the coast (tiles cover its inner half).
        for (h in land) drawPath(hexPath(at, h, 1.06f), Color.White.copy(alpha = 0.8f), style = Stroke(0.05f * s))
    }

    /** Two translucent layers of soft tonal patches drifting slowly in different directions. */
    private fun drawPatches(ds: DrawScope, c: Offset, s: Float, squash: Float, area: Rect) {
        val brushes = patchBrushes ?: patchPixels.map { px ->
            val bmp = Bitmap.createBitmap(px, PATCH_TEX, PATCH_TEX, Bitmap.Config.ARGB_8888)
            val sh = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            sh to ShaderBrush(sh)
        }.also { patchBrushes = it }
        for ((i, pair) in brushes.withIndex()) {
            val (shader, brush) = pair
            // Layer A: broad blotches; layer B: longer flowing bands, turned and drifting the other way.
            val tile = if (i == 0) PATCH_TILE_A else PATCH_TILE_B
            val k = tile * s / PATCH_TEX
            val drift = time * if (i == 0) 0.018f else -0.011f
            patchMatrix.reset()
            patchMatrix.setScale(k * if (i == 0) 1f else 1.9f, k * squash)
            patchMatrix.postRotate(if (i == 0) 0f else 24f)
            patchMatrix.postTranslate(c.x + drift * s * 1.3f, c.y + drift * s * squash * 0.6f + i * 37f)
            shader.setLocalMatrix(patchMatrix)
            ds.drawRect(brush, area.topLeft, area.size, alpha = if (i == 0) 0.55f + 0.1f * sin(time * 0.21f) else 0.45f + 0.1f * sin(time * 0.17f + 2f))
        }
    }

    /** A tapered crescent from [x0],[y0] to [x1],[y1], bowing [bow] toward ([nx],[ny]), [thick] at its middle. */
    private fun crescentPath(at: (Float, Float) -> Offset, x0: Float, y0: Float, x1: Float, y1: Float, nx: Float, ny: Float, bow: Float, thick: Float): Path {
        val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2
        val a = at(x0, y0); val b = at(x1, y1)
        val k1 = at(mx + nx * 2 * bow, my + ny * 2 * bow)
        val k2 = at(mx + nx * (2 * bow - 2 * thick), my + ny * (2 * bow - 2 * thick))
        return crescent.apply { reset(); moveTo(a.x, a.y); quadraticTo(k1.x, k1.y, b.x, b.y); quadraticTo(k2.x, k2.y, a.x, a.y); close() }
    }

    private fun drawWavelet(ds: DrawScope, at: (Float, Float) -> Offset, w: Wavelet) {
        val a = waveletAlpha(w, time)
        if (a < 0.01f) return
        val u = loop(w, time); val n = loopIndex(w, time)
        // Each loop it takes a slightly different angle and length; within a loop it lengthens and bends.
        val ang = w.angle + hop(w.id, n, 2) * 0.35f
        val px = cos(ang); val py = sin(ang)
        val nx = -py; val ny = px
        val swell = sin(PI.toFloat() * u)
        val half = w.len / 2 * (0.6f + 0.4f * swell) * (1 + 0.25f * hop(w.id, n, 3))
        val bow = w.len * (0.24f + 0.1f * sin(2 * PI.toFloat() * u + w.id))
        val thick = maxOf(0.016f, w.len * if (w.big) 0.12f else 0.15f)
        val cx = waveletX(w, time); val cy = waveletY(w, time)
        // A slight hook: one end trails, so it reads as a brush stroke rather than a symmetric arc.
        val hook = w.len * 0.15f * hop(w.id, n, 4)
        val x0 = cx - px * half - nx * hook; val y0 = cy - py * half - ny * hook
        val x1 = cx + px * half; val y1 = cy + py * half
        // Darker neighbouring stroke just behind for depth, then the light crest.
        val sh = thick * 1.4f
        ds.drawPath(crescentPath(at, x0 - nx * sh, y0 - ny * sh + 0.012f, x1 - nx * sh, y1 - ny * sh + 0.012f, nx, ny, bow, thick * 1.1f), SHADOW.copy(alpha = a * 0.75f))
        ds.drawPath(crescentPath(at, x0, y0, x1, y1, nx, ny, bow, thick), w.color.copy(alpha = a))
    }

    /** A cluster of little crescents curling in toward the coast, then breaking into flecks that fade. */
    private fun drawBreak(ds: DrawScope, at: (Float, Float) -> Offset, s: Float, b: Break) {
        val u = (time / b.period + b.phase).let { it - floor(it) }
        if (u > 0.62f) return
        val n = floor(time / b.period + b.phase).toInt()
        val tx = -b.ny; val ty = b.nx                    // along the coast
        val approach = (u / 0.38f).coerceAtMost(1f)       // rolling in
        val breakP = ((u - 0.3f) / 0.32f).coerceIn(0f, 1f) // turning to flecks
        val off = 0.32f - 0.16f * smooth(approach)
        for (i in 0 until b.count) {
            val along = (i - (b.count - 1) / 2f) * 0.13f + hop(b.id, n * 7 + i, 0) * 0.04f
            val len = (0.14f + 0.06f * hop(b.id, n * 7 + i, 1).let { abs(it) }) * (0.6f + 0.4f * smooth(approach))
            val stagger = if (i % 2 == 1) 0.03f else 0f
            val cx = b.x + tx * along + b.nx * (off + stagger)
            val cy = b.y + ty * along + b.ny * (off + stagger)
            // The crest bows toward the land (it's curling over onto the shore) and whitens as it steepens.
            val a = smooth(u / 0.12f) * (1 - smooth(breakP * 1.4f))
            if (a > 0.01f) {
                val col = lerpColor(FOAM_CYAN, FOAM_WHITE, smooth(approach))
                ds.drawPath(crescentPath(at, cx - tx * len / 2, cy - ty * len / 2, cx + tx * len / 2, cy + ty * len / 2,
                    -b.nx, -b.ny, len * (0.14f + 0.12f * approach), len * 0.28f), col.copy(alpha = 0.9f * a))
            }
            // Flecks: short bright dashes left where the crest broke, drifting in and fading.
            if (breakP > 0f) {
                val fa = sin(PI.toFloat() * breakP) * 0.8f
                for (j in 0 until 3) {
                    val q = hop(b.id, n * 31 + i * 3 + j, 2)
                    val fx = cx + tx * len * 0.5f * q - b.nx * 0.05f * breakP
                    val fy = cy + ty * len * 0.5f * q - b.ny * 0.05f * breakP
                    val fl = 0.012f + 0.012f * abs(hop(b.id, n * 31 + i * 3 + j, 3))
                    ds.drawLine(FOAM_WHITE.copy(alpha = fa * (0.6f + 0.4f * abs(q))), at(fx - tx * fl, fy - ty * fl), at(fx + tx * fl, fy + ty * fl),
                        0.024f * s, StrokeCap.Round)
                }
            }
        }
    }

    /** Clouds, gulls and splash droplets: drawn after the board. */
    fun drawSky(ds: DrawScope, at: (Float, Float) -> Offset, s: Float) = with(ds) {
        for (p in drops) {
            val t = p.age / p.life
            // Droplets arc up and fall back (cloud puffs just spread out).
            val lift = if (p.dy < 0) -sin(PI.toFloat() * t) * 0.15f else 0f
            drawCircle(Color(0xFFE8F7FF).copy(alpha = 1 - t), p.r * s, at(p.x + p.dx * t, p.y + p.dy * t * 0.4f + lift))
        }
        for (g in gulls) {
            val p = at(cos(g.a) * g.r, sin(g.a) * g.r)
            val flap = (0.55f + 0.45f * sin(g.phase)) * 0.06f * s
            val w = 0.11f * s
            val path = Path().apply {
                moveTo(p.x - w, p.y); quadraticTo(p.x - w / 2, p.y - flap, p.x, p.y); quadraticTo(p.x + w / 2, p.y - flap, p.x + w, p.y)
            }
            drawPath(path, Color.White, style = Stroke(0.028f * s, cap = StrokeCap.Round))
        }
        for (c in clouds) {
            val fade = if (c.poof >= 0) (1 - c.poof / 0.25f).coerceAtLeast(0f) else 1f
            if (fade <= 0f) continue
            val p = at(c.x, c.y)
            // Each poke leaves it a bit smaller; the latest one makes it jiggle.
            val jig = c.wobble / WOBBLE
            val u = 0.16f * s * (1 - 0.08f * c.hits) * (1 + 0.12f * jig * sin(c.wobble * 40f))
            drawOval(Color(0xFF002040).copy(alpha = 0.22f * fade), p + Offset(-1.1f * u, 1.0f * u), Size(2.8f * u, 0.6f * u))
            for ((dx, dy, r) in UNDER) drawCircle(Color(0xFFB9CBE0).copy(alpha = fade), r * u, p + Offset(dx * u, dy * u))
            for ((dx, dy, r) in PUFFS[c.shape]) drawCircle(
                Brush.radialGradient(listOf(Color.White, Color(0xFFF4F8FC), Color(0xFFC9D8EA)), center = p + Offset((dx - 0.3f * r) * u, (dy - 0.4f * r) * u), radius = 1.5f * r * u),
                r * u, p + Offset(dx * u, dy * u), alpha = fade)
            for ((dx, dy, r) in TOPS) drawCircle(Color.White.copy(alpha = fade), r * u, p + Offset(dx * u, dy * u))
        }
    }

    private fun hexPath(at: (Float, Float) -> Offset, c: Offset, grow: Float) = Path().apply {
        for (i in 0 until 6) {
            val a = (-90 + 60 * i) * PI / 180
            val q = at(c.x + grow * cos(a).toFloat(), c.y + grow * sin(a).toFloat())
            if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
        }
        close()
    }

    companion object {
        private val SQRT3 = sqrt(3f)
        /** Inradius of a land hex (side 1). */
        private val HEX_IN = SQRT3 / 2
        /** Radius of the framed sea, in hex sides. */
        const val SEA_R = 5.4f
        /** Width of the wooden rim. */
        const val RIM = 0.26f
        /** Pokes it takes to poof a cloud. */
        const val POKES_TO_POOF = 4
        private const val WOBBLE = 0.45f
        /** Fling physics: speed cap (units/s), damping rate (1/s: a brisk throw rests in ~1.7 s), rest speed, bounce. */
        const val FLING_MAX = 6f
        const val FLING_DAMPING = 2.4f
        private const val FLING_REST = 0.04f
        const val BOUNCE = 0.4f

        const val SMALL_WAVELETS = 150
        const val BIG_WAVELETS = 14
        /** How far a wavelet may hop from home between loops, and how far it drifts within one. */
        const val HOP = 0.09f
        const val DRIFT = 0.035f
        /** Mean direction of the open-sea swell's crests. */
        private const val SWELL = 0.5f

        private val DEEP_RIM = Color(0xFF04203A)
        private val DEEP_MID = Color(0xFF0B3F6B)
        private val DEEP_SHORE = Color(0xFF1D5E8E)
        private val SHALLOW = Color(0xFF3F8FB8)
        // Wavelet and foam paints, picked from the board's existing blues.
        private val LIGHT_BLUE = Color(0xFF86BCE2)
        private val CYAN = Color(0xFF8FD6E4)
        private val PALE = Color(0xFFE6F4FB)
        private val SHADOW = Color(0xFF082947)
        private val FOAM_CYAN = Color(0xFFB6E9F2)
        private val FOAM_WHITE = Color(0xFFF4FCFF)
        // Tonal patch layers: navy-to-cobalt blotches, and medium-blue-to-muted-cyan bands.
        private const val PATCH_A_LOW = 0xFF0A2B50.toInt()
        private const val PATCH_A_HIGH = 0xFF2563A8.toInt()
        private const val PATCH_B_LOW = 0xFF2B6699.toInt()
        private const val PATCH_B_HIGH = 0xFF4E9AB5.toInt()
        /** Patch texture size in pixels, and how many hex sides one tile spans. */
        const val PATCH_TEX = 64
        private const val PATCH_TILE_A = 4.3f
        private const val PATCH_TILE_B = 3.1f

        private fun smooth(t: Float) = t.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

        /** Deterministic pseudo-random in -1..1 for (id, loop, channel): no state, no allocation. */
        internal fun hop(id: Int, n: Int, ch: Int): Float {
            var h = id * 374761393 + n * 668265263 + ch * 1442695041
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 32767.5f - 1f
        }

        /**
         * A seamless soft noise texture (value noise on an 8×8 wrapped grid, two octaves) mapped to
         * translucent patches: dark where the noise is low, bright where it's high, clear in between.
         */
        internal fun patchTexture(low: Int, high: Int, seed: Int): IntArray {
            val r = Random(seed)
            fun grid(n: Int) = FloatArray(n * n) { r.nextFloat() }
            val g1 = grid(6); val g2 = grid(12)
            fun sample(g: FloatArray, n: Int, x: Float, y: Float): Float {
                val fx = x * n; val fy = y * n
                val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
                val tx = smooth(fx - ix); val ty = smooth(fy - iy)
                fun v(i: Int, j: Int) = g[((j % n + n) % n) * n + ((i % n + n) % n)]
                val a = v(ix, iy) + (v(ix + 1, iy) - v(ix, iy)) * tx
                val b = v(ix, iy + 1) + (v(ix + 1, iy + 1) - v(ix, iy + 1)) * tx
                return a + (b - a) * ty
            }
            return IntArray(PATCH_TEX * PATCH_TEX) { i ->
                val x = (i % PATCH_TEX) / PATCH_TEX.toFloat(); val y = (i / PATCH_TEX) / PATCH_TEX.toFloat()
                val v = 0.7f * sample(g1, 6, x, y) + 0.3f * sample(g2, 12, x, y)
                val d = (v - 0.5f) * 2f                      // -1..1
                val col = if (d < 0) low else high
                val a = (smooth(abs(d) * 1.6f) * 110).toInt()  // up to ~43% opacity at the extremes
                (a shl 24) or (col and 0xFFFFFF)
            }
        }

        private val UNDER = listOf(Triple(-1.1f, 0.4f, 0.8f), Triple(1.1f, 0.4f, 0.8f), Triple(0f, 0.55f, 1.4f))
        private val PUFFS = listOf(
            listOf(Triple(-1.1f, 0.1f, 0.65f), Triple(-0.4f, -0.4f, 0.8f), Triple(0.5f, -0.6f, 0.9f), Triple(1.2f, -0.1f, 0.65f), Triple(0f, 0.2f, 0.9f)),
            listOf(Triple(-0.9f, 0f, 0.7f), Triple(0f, -0.5f, 0.95f), Triple(0.9f, -0.1f, 0.75f), Triple(0.1f, 0.25f, 0.85f)),
            listOf(Triple(-1.2f, 0.15f, 0.55f), Triple(-0.5f, -0.3f, 0.75f), Triple(0.3f, -0.55f, 0.8f), Triple(1f, -0.2f, 0.7f), Triple(1.5f, 0.2f, 0.5f), Triple(0.2f, 0.2f, 0.9f)),
        )
        private val TOPS = listOf(Triple(-0.6f, -0.7f, 0.32f), Triple(0.3f, -1.0f, 0.36f), Triple(-1.2f, -0.2f, 0.24f))
    }
}
