package com.mauro.wgbg

import kotlin.math.exp
import kotlin.math.hypot

/**
 * How clouds feel under the finger: light, soft and buoyant. Every tunable lives here.
 * Speeds are lattice units (hex sides) per second. None of this depends on the Motion setting.
 */
object CloudFeel {
    /** Velocity decays as e^(-DAMPING·t): a flick halves its speed about every 0.8 s. */
    const val DAMPING = 0.9f
    /** No cloud moves faster than this, however hard it is flicked. */
    const val MAX_SPEED = 3.5f
    /** Below this speed a cloud is at rest. */
    const val REST = 0.03f
    /** Push from a tap, away from the finger. */
    const val TAP_IMPULSE = 0.35f
    /** Share of a nudge's finger speed handed to the cloud. */
    const val NUDGE_GAIN = 0.6f
    /** Share of a flick's release speed the cloud keeps. */
    const val FLICK_RETAIN = 0.8f
    /** Share of the release speed kept when a slow drag lets go. */
    const val DRAG_RELEASE = 0.3f
    /** How stiffly a dragged cloud follows the finger (1/s); higher is snappier. */
    const val FOLLOW = 18f
    /** Taps 1..POKES_TO_POOF-1 push the cloud; this one poofs it. */
    const val POKES_TO_POOF = 5

    /** A tap is shorter than this; a press held longer past slop is a drag. */
    const val TAP_MS = 300L
    /** Release speed (screen px/s) from which a short gesture counts as a flick. */
    const val FLICK_PX_PER_S = 1200f

    enum class Gesture { TAP, NUDGE, DRAG, FLICK }

    /**
     * Sorts a finished touch on a cloud. [travelPx] is the total finger path, [durationMs] the
     * press length, [releaseSpeedPx] the finger speed at release and [slopPx] the system touch slop.
     */
    fun classify(travelPx: Float, durationMs: Long, releaseSpeedPx: Float, slopPx: Float): Gesture = when {
        travelPx < slopPx -> if (durationMs < TAP_MS) Gesture.TAP else Gesture.DRAG // a long press that never moved: just let go
        releaseSpeedPx >= FLICK_PX_PER_S -> Gesture.FLICK
        durationMs > TAP_MS || travelPx > 4 * slopPx -> Gesture.DRAG
        else -> Gesture.NUDGE
    }

    /** Velocity pushing a cloud at ([cx], [cy]) away from a touch at ([tx], [ty]); a dead-center tap goes along [fallbackAngle]. */
    fun impulseAway(cx: Float, cy: Float, tx: Float, ty: Float, fallbackAngle: Float): Pair<Float, Float> {
        val dx = cx - tx
        val dy = cy - ty
        val d = hypot(dx, dy)
        return if (d > 1e-3f) dx / d * TAP_IMPULSE to dy / d * TAP_IMPULSE
        else kotlin.math.cos(fallbackAngle) * TAP_IMPULSE to kotlin.math.sin(fallbackAngle) * TAP_IMPULSE
    }

    /** Fraction of the way a dragged cloud closes on its target in [dt] seconds (frame-rate independent). */
    fun followFraction(dt: Float) = 1f - exp(-FOLLOW * dt)

    /** Velocity multiplier for [dt] seconds of damping. */
    fun damp(dt: Float) = exp(-DAMPING * dt)

    /** ([vx], [vy]) limited to [MAX_SPEED]. */
    fun cap(vx: Float, vy: Float): Pair<Float, Float> {
        val s = hypot(vx, vy)
        if (!s.isFinite()) return 0f to 0f
        return if (s > MAX_SPEED) vx * MAX_SPEED / s to vy * MAX_SPEED / s else vx to vy
    }
}
