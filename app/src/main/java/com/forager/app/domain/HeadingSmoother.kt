package com.forager.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Smooths a stream of compass headings — navigation HUD stage one, the *one* filter both the
 * compass strip and the HUD's compasses read, so the two can never disagree (the HUD's target
 * needle is heading-relative, so smoothing the heading smooths the needle; the target bearing is
 * never smoothed separately).
 *
 * An exponential moving average on the heading's **unit vector**, not on the degree value: 359°
 * and 1° average to 0°, not 180°. Each sample moves the running vector [alpha] of the way toward
 * the new one; the heading is the running vector's angle. With [DEFAULT_ALPHA] at the sensor's
 * UI-rate cadence (about 16 Hz) a step settles 90% of the way in about seven samples, under half
 * a second — live enough for a readout that is read continuously — while a ±3° jitter comes out
 * around ±1°. The first sample seeds the vector outright, so there is no lag from zero on start.
 *
 * Deliberately stateful and single-instance per consumer: the state *is* the filter. Not a Flow
 * operator, so the arithmetic is testable with pinned literals and no coroutine machinery.
 */
class HeadingSmoother(private val alpha: Float = DEFAULT_ALPHA) {
    private var x = 0.0
    private var y = 0.0
    private var seeded = false

    /** The smoothed heading in `[0, 360)` after folding in [headingDegrees], itself in degrees clockwise from north. */
    fun next(headingDegrees: Float): Float {
        val radians = Math.toRadians(headingDegrees.toDouble())
        val sampleX = cos(radians)
        val sampleY = sin(radians)
        if (!seeded) {
            x = sampleX
            y = sampleY
            seeded = true
        } else {
            x += alpha * (sampleX - x)
            y += alpha * (sampleY - y)
        }
        val degrees = Math.toDegrees(atan2(y, x)).toFloat()
        return ((degrees % 360f) + 360f) % 360f
    }

    /** Forgets the running vector, so the next sample seeds afresh — used when the heading source goes away and comes back. */
    fun reset() {
        seeded = false
        x = 0.0
        y = 0.0
    }

    companion object {
        /** Owner decision (HUD stage one): 0.3 per sample. See the class doc comment for what it buys. */
        const val DEFAULT_ALPHA = 0.3f
    }
}
