package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import wgbg.engine.Board
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The framed round sea around the island (issue #1), in board lattice units (hex side = 1,
 * origin at the board's center), so it pans, zooms and tilts with the board.
 *
 * Waves are few and randomly aligned and only ever travel straight across their own crest;
 * near the coast they roll in and break into foam. Clouds and gulls stay over the water;
 * clouds can be poked (poof) or dragged, and tapping the water splashes.
 */
class Sea(board: Board, seed: Int = 1) {
    private val rnd = Random(seed)
    private val land: List<Offset> = board.topology.hexes.indices.map { h ->
        val vs = board.topology.hexVertices[h]
        Offset(vs.map { board.topology.vertexPos[it].x * SQRT3 / 2f }.average().toFloat(),
            vs.map { board.topology.vertexPos[it].y / 2f }.average().toFloat())
    }
    /** Farthest land corner from the center. */
    val landR = board.topology.vertexPos.maxOf { hypot(it.x * SQRT3 / 2f, it.y / 2f) }

    private class Wave(var x: Float, var y: Float, val dir: Float, val shore: Boolean, val speed: Float,
                       val len: Float, var bend: Float, var wob: Float, val life: Float, var age: Float = 0f, var dead: Boolean = false)
    private class Puff(val x: Float, val y: Float, val r: Float, val life: Float, var age: Float = 0f, val dx: Float = 0f, val dy: Float = 0f)
    class Cloud(var x: Float, var y: Float, val drift: Float, val shape: Int, var poof: Float = -1f)
    private class Gull(var a: Float, val r: Float, val speed: Float, var phase: Float)
    private class Ring(val x: Float, val y: Float, var age: Float = 0f, val delay: Float = 0f)

    private val waves = ArrayList<Wave>()
    private val foam = ArrayList<Puff>()
    private val drops = ArrayList<Puff>()
    private val rings = ArrayList<Ring>()
    val clouds = ArrayList<Cloud>()
    private val gulls = List(3) { i -> Gull(rnd.nextFloat() * 2 * PI.toFloat(), landR + 0.4f + 0.18f * i, (0.12f + 0.05f * i) * if (i % 2 == 1) -1 else 1, rnd.nextFloat() * 6) }
    private val glints = List(70) { val (x, y) = seaPoint(0.2f); floatArrayOf(x, y, rnd.nextFloat() * 6f, 0.6f + rnd.nextFloat()) }
    private var time = 0f

    init { repeat(4) { clouds += newCloud() } }

    private fun seaPoint(margin: Float): Pair<Float, Float> {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val d = landR + margin + rnd.nextFloat() * (SEA_R - landR - 2 * margin)
        return cos(a) * d to sin(a) * d
    }
    private fun newCloud(): Cloud { val (x, y) = seaPoint(0.35f); return Cloud(x, y, 0.012f + rnd.nextFloat() * 0.02f, rnd.nextInt(3)) }

    private fun newWave(shore: Boolean): Wave {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val d = if (shore) SEA_R - 0.25f - rnd.nextFloat() * 0.3f else landR + 0.35f + rnd.nextFloat() * (SEA_R - landR - 0.6f)
        val x = cos(a) * d
        val y = sin(a) * d
        val dir = if (shore) atan2(-y, -x) + (rnd.nextFloat() - 0.5f) * 0.5f else rnd.nextFloat() * 2 * PI.toFloat()
        return Wave(x, y, dir, shore,
            speed = if (shore) 0.1f + rnd.nextFloat() * 0.07f else 0.035f + rnd.nextFloat() * 0.05f,
            len = if (shore) 0.35f + rnd.nextFloat() * 0.3f else 0.15f + rnd.nextFloat() * 0.25f,
            bend = 0.3f, wob = rnd.nextFloat() * 6, life = if (shore) 99f else 4f + rnd.nextFloat() * 4f)
    }

    /** Advances the animation by [dt] seconds. */
    fun update(dt: Float) {
        time += dt
        while (waves.count { !it.shore } < 7) waves += newWave(false)
        while (waves.count { it.shore } < 6) waves += newWave(true)
        for (w in waves) {
            w.age += dt
            w.wob += dt * 0.9f
            w.bend = 0.3f + 0.15f * sin(w.age * 1.3f + w.wob)
            w.x += cos(w.dir) * w.speed * dt
            w.y += sin(w.dir) * w.speed * dt
            if (w.shore && hypot(w.x, w.y) < landR + 0.08f) {
                w.dead = true
                repeat(5) { foam += Puff(w.x + (rnd.nextFloat() - 0.5f) * 0.3f, w.y + (rnd.nextFloat() - 0.5f) * 0.3f, 0.03f + rnd.nextFloat() * 0.05f, 0.9f + rnd.nextFloat() * 0.5f) }
            } else if (!w.shore && w.age > w.life) w.dead = true
        }
        waves.removeAll { it.dead }
        for (p in foam) p.age += dt
        foam.removeAll { it.age > it.life }
        for (p in drops) p.age += dt
        drops.removeAll { it.age > it.life }
        for (r in rings) r.age += dt
        rings.removeAll { it.age - it.delay > 1.3f }
        for (g in gulls) { g.a += g.speed * dt; g.phase += dt * 7 }
        for (c in clouds) {
            if (c.poof >= 0) { c.poof += dt; continue }
            val a = atan2(c.y, c.x) + c.drift * dt
            val d = hypot(c.x, c.y)
            c.x = cos(a) * d; c.y = sin(a) * d
        }
        // A poofed cloud is replaced a few seconds later somewhere else on the sea.
        val gone = clouds.count { it.poof > 3.5f }
        clouds.removeAll { it.poof > 3.5f }
        repeat(gone) { clouds += newCloud() }
    }

    /** True if [p] (lattice) is on open water. */
    fun isWater(p: Offset) = hypot(p.x, p.y).let { it in landR..SEA_R }

    fun cloudAt(p: Offset): Cloud? = clouds.lastOrNull { it.poof < 0 && hypot(p.x - it.x, p.y - it.y) < 0.38f }

    fun poof(c: Cloud) {
        if (c.poof >= 0) return
        c.poof = 0f
        repeat(9) { i ->
            val a = i / 9f * 2 * PI.toFloat()
            drops += Puff(c.x, c.y, 0.07f, 0.55f, dx = cos(a) * 0.45f, dy = sin(a) * 0.45f)
        }
    }

    /** Drags a cloud, keeping it over the water. */
    fun move(c: Cloud, to: Offset) {
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
            // Shallows hugging the coast: stacked translucent hex halos read as a soft gradient.
            for ((grow, alpha) in listOf(1.22f to 0.18f, 1.14f to 0.22f, 1.07f to 0.28f)) {
                for (h in land) drawPath(hexPath(at, h, grow), SHALLOW.copy(alpha = alpha))
            }
            // Sun glints twinkling.
            for (g in glints) {
                val a = (sin(time * g[3] + g[2]) * 0.5f + 0.5f)
                if (a > 0.55f) drawCircle(Color.White.copy(alpha = (a - 0.55f) * 1.4f), 0.018f * s, at(g[0], g[1]))
            }
            // Waves: crest perpendicular to travel, bowing forward and uneven.
            for (w in waves) {
                val op = if (w.shore) minOf(1f, w.age / 1.5f) * 0.85f else sin(PI.toFloat() * w.age / w.life) * 0.7f
                if (op <= 0f) continue
                val ux = cos(w.dir); val uy = sin(w.dir); val px = -uy; val py = ux
                val half = w.len / 2; val lead = w.len * w.bend * 0.5f
                val path = Path().apply {
                    val a = at(w.x - px * half, w.y - py * half)
                    val k = at(w.x + ux * lead + px * sin(w.wob) * half * 0.25f, w.y + uy * lead + py * sin(w.wob) * half * 0.25f)
                    val b = at(w.x + px * half, w.y + py * half)
                    moveTo(a.x, a.y); quadraticTo(k.x, k.y, b.x, b.y)
                }
                drawPath(path, Color.White.copy(alpha = op), style = Stroke((if (w.shore) 0.04f else 0.027f) * s, cap = StrokeCap.Round))
            }
            for (p in foam) {
                val t = p.age / p.life
                drawCircle(Color.White.copy(alpha = 0.85f * (1 - t)), p.r * s * (1 + 0.8f * t), at(p.x, p.y))
            }
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
            val u = 0.16f * s
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
        /** Radius of the framed sea, in hex sides. */
        const val SEA_R = 5.4f
        /** Width of the wooden rim. */
        const val RIM = 0.26f
        private val DEEP_RIM = Color(0xFF04203A)
        private val DEEP_MID = Color(0xFF0B3F6B)
        private val DEEP_SHORE = Color(0xFF1D5E8E)
        private val SHALLOW = Color(0xFF3F8FB8)
        private val UNDER = listOf(Triple(-1.1f, 0.4f, 0.8f), Triple(1.1f, 0.4f, 0.8f), Triple(0f, 0.55f, 1.4f))
        private val PUFFS = listOf(
            listOf(Triple(-1.1f, 0.1f, 0.65f), Triple(-0.4f, -0.4f, 0.8f), Triple(0.5f, -0.6f, 0.9f), Triple(1.2f, -0.1f, 0.65f), Triple(0f, 0.2f, 0.9f)),
            listOf(Triple(-0.9f, 0f, 0.7f), Triple(0f, -0.5f, 0.95f), Triple(0.9f, -0.1f, 0.75f), Triple(0.1f, 0.25f, 0.85f)),
            listOf(Triple(-1.2f, 0.15f, 0.55f), Triple(-0.5f, -0.3f, 0.75f), Triple(0.3f, -0.55f, 0.8f), Triple(1f, -0.2f, 0.7f), Triple(1.5f, 0.2f, 0.5f), Triple(0.2f, 0.2f, 0.9f)),
        )
        private val TOPS = listOf(Triple(-0.6f, -0.7f, 0.32f), Triple(0.3f, -1.0f, 0.36f), Triple(-1.2f, -0.2f, 0.24f))
    }
}
