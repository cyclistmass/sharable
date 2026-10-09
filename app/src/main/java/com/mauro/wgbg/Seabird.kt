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
 * Its path curves gently around the island; flap rate, phase, size and speed differ per bird.
 */
class Seabird(seed: Int, private val ringR: Float) {
    private val rnd = java.util.Random(seed * 7919L + 11)
    private val id = seed
    /** Angle around the island, turn direction, speed (rad/s) and how far its circle wobbles. */
    private var a = rnd.nextFloat() * 2 * PI.toFloat()
    private val dir = if (rnd.nextBoolean()) 1f else -1f
    private val speed = 0.035f + rnd.nextFloat() * 0.03f
    private val wobble = 0.35f + rnd.nextFloat() * 0.5f
    private val wobbleRate = 0.15f + rnd.nextFloat() * 0.15f
    val scale = 0.85f + rnd.nextFloat() * 0.35f
    private val flapHz = 2.6f + rnd.nextFloat() * 1.2f
    private val burstCycle = 7f + rnd.nextFloat() * 5f           // seconds between flapping bursts
    private val burstLen = 0.24f                                 // share of the cycle spent flapping
    private val phase = rnd.nextFloat() * 10f
    private var t = 0f

    var x = 0f; private set
    var y = 0f; private set
    /** Direction of flight on screen (radians) and bank (−1..1) while turning. */
    var heading = 0f; private set
    var bank = 0f; private set

    fun update(dt: Float) {
        t += dt
        a += dir * speed * dt
        val r = ringR + wobble * sin(t * wobbleRate + phase)
        val nx = cos(a) * r; val ny = sin(a) * r
        if (x != 0f || y != 0f) heading = kotlin.math.atan2(ny - y, nx - x)
        bank = (dir * 0.5f + 0.5f * sin(t * wobbleRate + phase)).coerceIn(-1f, 1f) * 0.6f
        x = nx; y = ny
    }

    /** True while the bird is in a flapping burst (the rest of the time it glides). */
    val flapping get() = ((t + phase) % burstCycle) / burstCycle < burstLen

    /** Frame of the 8-frame flap strip (10 fps) while flapping, or -1 while gliding. */
    fun flapFrame(): Int = if (flapping) (((t + phase) * 10f).toInt() % 8 + 8) % 8 else -1

    /** Wing hinge angle (radians, + = up) for this moment: a full stroke while flapping, a slight flex while gliding. */
    fun hinge(): Float = if (flapping) 0.55f * sin(2 * PI.toFloat() * flapHz * (t + phase)) else 0.06f * sin(0.9f * t + phase)

    companion object {
        private val BODY = Color(0xFFF4F6F8)
        private val WING = Color(0xFFE6EBF0)
        private val SHADE = Color(0xFFB9C3CD)
        private val TIP = Color(0xFF8C97A3)
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
