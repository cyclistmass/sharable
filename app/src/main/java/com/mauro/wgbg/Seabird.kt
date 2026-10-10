package com.mauro.wgbg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small pale seabird seen from above, with articulated wings: each wing hinges at its shoulder,
 * flapping in short bursts (about a quarter of the time) and otherwise gliding with small flexes.
 * Its path curves gently around the island, now and then looping around a passing boat; flap rate, phase, size and speed differ per bird.
 */
class Seabird(seed: Int, private val ringR: Float) {
    private val rnd = java.util.Random(seed * 7919L + 11)
    private val id = seed
    /** Turn direction, flight speed (lattice units/s) and how far its home ring wobbles. */
    private val dir = if (rnd.nextBoolean()) 1f else -1f
    private val speed = 0.16f + rnd.nextFloat() * 0.12f
    private val wobble = 0.35f + rnd.nextFloat() * 0.5f
    private val wobbleRate = 0.15f + rnd.nextFloat() * 0.15f
    val scale = 0.85f + rnd.nextFloat() * 0.35f
    private val flapHz = 2.6f + rnd.nextFloat() * 1.2f
    private val burstCycle = 7f + rnd.nextFloat() * 5f           // seconds between flapping bursts
    private val burstLen = 0.24f                                 // share of the cycle spent flapping
    private val phase = rnd.nextFloat() * 10f
    private var t = 0f
    /** Seconds until the bird next looks for a boat to circle, and how long the current circling lasts. */
    private var nextCircle = 8f + rnd.nextFloat() * 30f
    private var circleLeft = 0f
    /** Index of the boat being circled, or -1 while cruising its home ring. */
    var circling = -1; private set
    /** Seconds until the bird next cuts across the island, and how long the current crossing lasts. */
    private var nextCross = 20f + rnd.nextFloat() * 60f
    private var crossLeft = 0f
    /** Where the current crossing heads: the far side of the island, fixed when the crossing starts. */
    private var crossX = 0f
    private var crossY = 0f
    /** True while the bird is cutting across the island (birds fly over land too). */
    val crossing get() = crossLeft > 0f

    var x = 0f; private set
    var y = 0f; private set
    /** Direction of flight on screen (radians) and bank (−1..1) while turning. */
    var heading = 0f; private set
    var bank = 0f; private set

    init {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        x = cos(a) * ringR; y = sin(a) * ringR
        heading = a + dir * PI.toFloat() / 2
    }

    /**
     * Flies one step: the bird steers toward a point a little ahead on its home ring (or, now and then,
     * around a nearby boat from [boats], given as x, y pairs), turning at most [MAX_TURN] rad/s so its
     * path is always a slow curve, never a corner.
     */
    fun update(dt: Float, boats: FloatArray = NO_BOATS, nBoats: Int = boats.size / 2) {
        t += dt
        if (circling >= nBoats) circling = -1
        if (circling >= 0) {
            circleLeft -= dt
            if (circleLeft <= 0f) { circling = -1; nextCircle = 20f + rnd.nextFloat() * 40f }
        } else {
            nextCircle -= dt
            if (nextCircle <= 0f) {
                var best = -1; var bestD = CIRCLE_REACH
                for (i in 0 until nBoats) {
                    val d = kotlin.math.hypot(boats[2 * i] - x, boats[2 * i + 1] - y)
                    if (d < bestD) { bestD = d; best = i }
                }
                if (best >= 0) { circling = best; circleLeft = 10f + rnd.nextFloat() * 12f }
                else nextCircle = 6f + rnd.nextFloat() * 10f
            }
        }
        // Now and then (when not with a boat) cut across the island, then back out to the home ring.
        if (circling < 0) {
            if (crossLeft > 0f) crossLeft -= dt
            else {
                nextCross -= dt
                if (nextCross <= 0f) {
                    // Long enough to reach the far side at this bird's speed (with time to turn), not a fixed span.
                    crossLeft = 2.4f * ringR / speed; nextCross = 40f + rnd.nextFloat() * 80f
                    val far = kotlin.math.atan2(y, x) + PI.toFloat()
                    crossX = cos(far) * ringR; crossY = sin(far) * ringR
                }
            }
        }
        val gx: Float; val gy: Float
        if (crossLeft > 0f && circling < 0) {
            // Head across the middle of the island to the far side of the ring; done on arrival.
            gx = crossX; gy = crossY
            if (kotlin.math.hypot(gx - x, gy - y) < 0.5f) crossLeft = 0f
        } else if (circling >= 0) {
            val bx = boats[2 * circling]; val by = boats[2 * circling + 1]
            val around = kotlin.math.atan2(y - by, x - bx) + dir * LEAD
            gx = bx + cos(around) * CIRCLE_R; gy = by + sin(around) * CIRCLE_R
        } else {
            val around = kotlin.math.atan2(y, x) + dir * LEAD
            val r = ringR + wobble * sin(t * wobbleRate + phase)
            gx = cos(around) * r; gy = sin(around) * r
        }
        var turn = kotlin.math.atan2(gy - y, gx - x) - heading
        while (turn > PI) turn -= 2 * PI.toFloat()
        while (turn < -PI) turn += 2 * PI.toFloat()
        val step = turn.coerceIn(-MAX_TURN * dt, MAX_TURN * dt)
        heading += step
        if (heading > PI) heading -= 2 * PI.toFloat() else if (heading < -PI) heading += 2 * PI.toFloat()
        // Bank into the turn, eased so it doesn't snap.
        val want = if (dt > 0f) (step / dt / MAX_TURN).coerceIn(-1f, 1f) * 0.6f else bank
        bank += (want - bank) * (1f - kotlin.math.exp(-3f * dt))
        x += cos(heading) * speed * dt; y += sin(heading) * speed * dt
    }

    /** True while the bird is in a flapping burst (the rest of the time it glides). */
    val flapping get() = ((t + phase) % burstCycle) / burstCycle < burstLen

    /** Frame of a [frames]-frame flap strip while flapping (one stroke cycle per 0.8 s whatever the count), or -1 while gliding. */
    fun flapFrame(frames: Int = 8): Int = if (flapping) (((t + phase) * frames * 1.25f).toInt() % frames + frames) % frames else -1

    /** Wing hinge angle (radians, + = up) for this moment: a full stroke while flapping, a slight flex while gliding. */
    fun hinge(): Float = if (flapping) 0.55f * sin(2 * PI.toFloat() * flapHz * (t + phase)) else 0.06f * sin(0.9f * t + phase)

    companion object {
        private val BODY = Color(0xFFF4F6F8)
        private val WING = Color(0xFFE6EBF0)
        private val SHADE = Color(0xFFB9C3CD)
        private val TIP = Color(0xFF8C97A3)
        /** Fastest turn (rad/s): keeps every path a gentle curve. */
        const val MAX_TURN = 0.7f
        /** How far ahead (radians around the ring or boat) the bird aims. */
        private const val LEAD = 0.6f
        /** Radius of the loop around a boat, and how near a boat must be for a bird to go and circle it. */
        const val CIRCLE_R = 0.55f
        const val CIRCLE_REACH = 2.5f
        private val NO_BOATS = FloatArray(0)
    }

    /**
     * Draws the bird at screen [p] with [u] px per body-length. Wings are tapered curved paths
     * hinged at the shoulders; a hinge folds the wing toward the body (foreshortening from above).
     * [path] is reused between calls so drawing allocates nothing.
     */
    fun draw(ds: DrawScope, p: Offset, u: Float, path: Path) = with(ds) {
        val s = u * scale
        val fx = cos(heading); val fy = sin(heading)           // forward
        val sx = -fy; val sy = fx                               // to the bird's right
        fun pt(f: Float, r: Float) = Offset(p.x + fx * f * s + sx * r * s, p.y + fy * f * s + sy * r * s)
        val h = hinge()
        // Seen from above, a raised or lowered wing looks shorter; banking lifts one wing a little.
        for (wing in 0..1) {
            val side = if (wing == 0) -1f else 1f
            val span = 1.25f * cos(h + side * bank * 0.3f).coerceIn(0.35f, 1f)
            val sweep = 0.18f + 0.12f * kotlin.math.abs(h)    // tips sweep back more mid-stroke
            path.reset()
            val shoulderF = pt(0.12f, 0.08f * side); val shoulderB = pt(-0.12f, 0.08f * side)
            val tip = pt(-sweep - 0.1f, span * side)
            path.moveTo(shoulderF.x, shoulderF.y)
            val lead = pt(0.15f, span * 0.55f * side)
            path.quadraticTo(lead.x, lead.y, tip.x, tip.y)    // curved leading edge
            val trail = pt(-0.25f, span * 0.45f * side)
            path.quadraticTo(trail.x, trail.y, shoulderB.x, shoulderB.y)
            path.close()
            drawPath(path, if (side > 0) WING else SHADE)
            drawCircle(TIP, 0.045f * s, tip)                  // dark wingtip
        }
        // Body: a slim pale teardrop with a tail.
        path.reset()
        val head = pt(0.42f, 0f); val tail = pt(-0.45f, 0f)
        path.moveTo(head.x, head.y)
        pt(0f, 0.11f).let { path.quadraticTo(it.x, it.y, tail.x, tail.y) }
        pt(0f, -0.11f).let { path.quadraticTo(it.x, it.y, head.x, head.y) }
        path.close()
        drawPath(path, BODY)
    }
}
