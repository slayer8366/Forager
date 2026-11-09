package com.zynergylabs.forager.app.ui.map

import android.animation.ValueAnimator
import android.util.Log
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import kotlin.math.hypot
import kotlin.math.max

/*
 * The elastic nudge while navigating (dispatch 2026-09-28-463; the owner chose "Give, then spring
 * back"). The step path the owner confirmed:
 * 1. The walker nudges the map: it moves with the finger, but stiffly, about half as far.
 * 2. Lifted before the drag threshold: it springs back to following, with a slight overshoot and
 *    settle, in about a quarter of a second.
 * 3. Dragged past the threshold: it lets go, stops following, and "Return to Route" appears, as now.
 * 4. A pinch still zooms and keeps following.
 *
 * How, read from the pinned MapLibre 13.5.0 bytecode:
 * - Under the threshold MapLibre's own pan never runs: with tracking-gesture management on (-457), the
 *   location camera raises the move detector's threshold and interrupts the gesture, so neither the map
 *   nor the app's move listener sees a nudge. The finger is read from the map view's touch listener.
 * - An app camera move cannot show the give: MapLibreMap's camera calls notify the location component's
 *   developer-animation listener, which sets the camera mode to NONE and ends following.
 * - The padding while tracking can: the location camera applies it through Transform directly, with no
 *   such notice, so following stays on. Padding is screen space, so the give holds under the tilt and
 *   a compass-facing bearing, and tracking keeps moving the walker underneath it.
 * - MapLibre animates padding linearly only, so the spring's overshoot is the app's own animator, which
 *   sets the padding each frame.
 */

/** How far the map is shown displaced from where following puts it, in screen pixels: right and down are positive. */
data class NudgeOffset(val dxPx: Float, val dyPx: Float) {
    companion object {
        val ZERO = NudgeOffset(0f, 0f)
    }
}

/**
 * One nudge's give: [NAVIGATION_NUDGE_GIVE_RATIO] of one finger's travel from where it came down, never
 * more than that share of [thresholdPx], the finger travel at which MapLibre lets go. Pure, so it can be
 * tested; [NudgeElasticDriver] feeds it.
 */
class NudgeGive(private val thresholdPx: Float, private val ratio: Float = NAVIGATION_NUDGE_GIVE_RATIO) {
    private var downX = 0f
    private var downY = 0f
    private var inGesture = false
    private var base = NudgeOffset.ZERO

    /** The give now shown. */
    var offset = NudgeOffset.ZERO
        private set

    /** One finger down. [current] is a give still on screen (a spring cut short), which this one starts from. */
    fun down(x: Float, y: Float, current: NudgeOffset) {
        downX = x
        downY = y
        inGesture = true
        base = current
        offset = current
    }

    /** The finger moved. The give to show, or `null` for none: no finger down, more than one finger, or [canGive] false (not following, or a mode transition running). */
    fun move(x: Float, y: Float, pointerCount: Int, canGive: Boolean): NudgeOffset? {
        if (!inGesture || pointerCount != 1 || !canGive) return null
        val dx = x - downX
        val dy = y - downY
        val travel = hypot(dx, dy)
        val scale = if (travel > thresholdPx) thresholdPx / travel else 1f
        offset = NudgeOffset(base.dxPx + dx * scale * ratio, base.dyPx + dy * scale * ratio)
        return offset
    }

    /**
     * The gesture is over for the give: the finger lifted, or a second finger came down. The give to
     * spring back from, or `null` when there is none to spring: nothing was given, or following has
     * ended (a drag past the threshold), whose padding stays until the view is applied again.
     */
    fun release(stillFollowing: Boolean): NudgeOffset? {
        val had = inGesture
        val from = offset
        inGesture = false
        base = NudgeOffset.ZERO
        offset = NudgeOffset.ZERO
        return if (had && stillFollowing && from != NudgeOffset.ZERO) from else null
    }
}

/**
 * The padding that shows [offset] on top of the navigation view's own (top [topPaddingPx], the rest 0).
 * The walker sits at the centre of the padded area, so widening one side by twice the give moves them,
 * and the map with them, by the give. Only ever widened, so no side goes negative.
 */
fun nudgePadding(topPaddingPx: Double, offset: NudgeOffset): DoubleArray = doubleArrayOf(
    2.0 * max(offset.dxPx, 0f),
    topPaddingPx + 2.0 * max(offset.dyPx, 0f),
    2.0 * max(-offset.dxPx, 0f),
    2.0 * max(-offset.dyPx, 0f),
)

/**
 * The give left at [fraction] (0 to 1) of the spring: from [from], through rest, a little past it, and
 * settling exactly at rest. Android's `OvershootInterpolator` curve, written out so it is the same off
 * the phone: progress = (x-1)²((t+1)(x-1)+t)+1.
 */
fun nudgeSpringOffset(from: NudgeOffset, fraction: Float, tension: Double = NAVIGATION_NUDGE_OVERSHOOT_TENSION): NudgeOffset {
    if (fraction >= 1f) return NudgeOffset.ZERO
    val x = fraction.coerceAtLeast(0f).toDouble() - 1.0
    val progress = x * x * ((tension + 1.0) * x + tension) + 1.0
    val left = (1.0 - progress).toFloat()
    return NudgeOffset(from.dxPx * left, from.dyPx * left)
}

/** The map's side of the elastic nudge, so the driver can be tested without a MapView. */
interface NudgeCamera {
    /** Whether the navigation view is following and can take padding now (no mode transition running). */
    fun canGive(): Boolean

    /** Shows [padding] (left, top, right, bottom, in pixels) at once, keeping following. */
    fun showPadding(padding: DoubleArray)
}

/**
 * Turns the map view's touches into the give and the spring. Called from the touch listener the map
 * view already has, which returns `false`, so MapLibre's own handling of every touch is unchanged.
 * Main thread only.
 */
class NudgeElasticDriver(
    private val camera: NudgeCamera,
    thresholdPx: Float,
    private val viewTopPaddingPx: () -> Double,
) {
    private val give = NudgeGive(thresholdPx)
    private var shown = NudgeOffset.ZERO
    private var spring: ValueAnimator? = null

    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopSpring()
                if (camera.canGive()) give.down(event.x, event.y, shown) else shown = NudgeOffset.ZERO
            }
            MotionEvent.ACTION_MOVE -> {
                val offset = give.move(event.x, event.y, event.pointerCount, camera.canGive()) ?: return
                show(offset)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
        }
    }

    /** The map is going away. */
    fun cancel() = stopSpring()

    /** Whether a spring is running (dispatch -470: logged when navigation is left). */
    val springing: Boolean get() = spring?.isRunning == true

    private fun release() {
        val from = give.release(stillFollowing = camera.canGive())
        if (from == null) {
            shown = NudgeOffset.ZERO
            return
        }
        Log.i(NAVIGATION_VIEW_LOG_TAG, "nudge: springing back from ${"%.0f".format(from.dxPx)}, ${"%.0f".format(from.dyPx)} px")
        spring = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NAVIGATION_NUDGE_SPRING_MILLIS
            interpolator = LinearInterpolator() // the overshoot is nudgeSpringOffset's
            addUpdateListener { animator ->
                if (!camera.canGive()) {
                    Log.i(NAVIGATION_VIEW_LOG_TAG, "nudge: following ended mid-spring; the spring stops")
                    shown = NudgeOffset.ZERO
                    animator.cancel()
                    return@addUpdateListener
                }
                show(nudgeSpringOffset(from, animator.animatedFraction))
            }
            start()
        }
    }

    private fun show(offset: NudgeOffset) {
        shown = offset
        camera.showPadding(nudgePadding(viewTopPaddingPx(), offset))
    }

    private fun stopSpring() {
        spring?.cancel()
        spring = null
    }
}
