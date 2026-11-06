package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveClassifier
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.hypot

/*
 * The plain map springs back from a small nudge (dispatch 2026-10-19-37, RECORD -1469). The step path
 * the owner confirmed, with the map following you (locate on):
 * 1. Nudge the map: it moves with your finger, stiffly, about half as far.
 * 2. Lift early (under about 1 cm): it springs back to following, with a slight bounce, in about a
 *    quarter second.
 * 3. Drag further or flick: it lets go and stops following, and the locate button shows "not
 *    following", as now.
 * 4. Pinch: it zooms and keeps following.
 * "The same as navigating" (NudgeElastic.kt's header has the how, read from MapLibre 13.5.0), except
 * that the plain map has no "Return to Route": locate brings following back, as before. A flick under
 * the threshold springs back too, through the same fling guard as navigating (the planner's
 * confirmation of that reading, dispatch -37 go).
 *
 * A path of its own, beside navigation's, not a condition threaded through it: navigation's driver,
 * fling guard and options are not touched, and this path's condition requires that the map is not
 * navigating, so the two never act on the same touch. Only the Maps tab turns it on
 * ([MapRenderMode.nudgeSpringBack]); the centre-pin pickers keep MapLibre's own drag (owner's ruling,
 * "Maps tab only"), and the Cartography entry map has no location component at all.
 *
 * What navigation has and this needs in addition: the give is camera padding, which MapLibre keeps
 * as part of the camera. Navigation clears it when the view is applied again; the plain map has
 * nothing that does, so a drag that leaves following with a give on screen would leave the next
 * locate, search frame and tab return up to half the threshold off. [PlainNudgeLeftover] clears it,
 * keeping the picture where it is.
 */

/**
 * Whether the plain map follows for the nudge: the map has the spring-back ([springBack]), is not
 * navigating (navigation has its own path), and the location component is activated and following.
 * [cameraMode] is `null` while the component is not activated. Following is any mode but NONE, as
 * the data refresh and the camera listener already read it; on the plain map that is TRACKING, set by
 * locate or restored on a tab return, since the compass-facing modes are navigation's alone.
 */
internal fun plainMapFollowing(springBack: Boolean, navigating: Boolean, cameraMode: Int?): Boolean =
    springBack && !navigating && cameraMode != null && cameraMode != CameraMode.NONE

/**
 * The plain map's location options with the nudge: the ordinary ones ([liveLocationComponentOptions],
 * unchanged), plus MapLibre's tracking-gesture management at navigation's thresholds, so a one-finger
 * drag under [NAVIGATION_NUDGE_THRESHOLD_DP] and a pinch's drift do not end following.
 */
internal fun plainMapNudgeOptions(context: Context, look: PositionLook): LocationComponentOptions {
    val density = context.resources.displayMetrics.density
    return liveLocationComponentOptions(context, look = look).toBuilder()
        .trackingGesturesManagement(true)
        .trackingInitialMoveThreshold(NAVIGATION_NUDGE_THRESHOLD_DP * density)
        .trackingMultiFingerMoveThreshold(NAVIGATION_MULTI_FINGER_MOVE_THRESHOLD_DP * density)
        .build()
}

/** The options a map holds outside navigation: [plainMapNudgeOptions] when it has the spring-back, the ordinary ones otherwise. */
internal fun ordinaryLocationOptions(context: Context, look: PositionLook, nudgeSpringBack: Boolean): LocationComponentOptions =
    if (nudgeSpringBack) plainMapNudgeOptions(context, look) else liveLocationComponentOptions(context, look = look)

/**
 * Whether a give is left on screen once following has ended, so the padding must be cleared. Told
 * every padding the plain nudge shows; at a release (ACTION_UP or ACTION_CANCEL) on the plain map,
 * with following over and the last padding shown not zero, it answers `true` once and forgets it.
 * While following, the spring is taking it back to zero, and it waits. Off the plain map (navigating,
 * or no spring-back) it forgets: navigation applies its own padding.
 */
class PlainNudgeLeftover {
    private var lastShown: DoubleArray? = null

    fun noteShown(padding: DoubleArray) {
        lastShown = padding.copyOf()
    }

    fun onRelease(action: Int, plainMap: Boolean, stillFollowing: Boolean): Boolean {
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) return false
        if (!plainMap) {
            lastShown = null
            return false
        }
        val padding = lastShown ?: return false
        if (stillFollowing) return false
        lastShown = null
        return padding.any { it != 0.0 }
    }

    /** The padding has been cleared another way ([PlainMapNudge.beforeLocate]): nothing is left to clear. */
    fun forget() {
        lastShown = null
    }
}

/** The map's side of the plain nudge, so [PlainMapNudge] can be tested without a MapView. */
interface PlainNudgeMap {
    /** The location component's camera mode, or `null` while it is not activated. */
    fun cameraMode(): Int?

    /** Shows [padding] (left, top, right, bottom, in pixels) at once, keeping following. */
    fun showPadding(padding: DoubleArray)

    /** Following has ended with a give on screen: the padding back to zero, the picture kept where it is. */
    fun clearPaddingKeepingPicture()

    /** MapLibre's fling. */
    var flingEnabled: Boolean

    /** Whether the camera holds any padding now (the plain map sets none but the nudge's). */
    fun paddingShown(): Boolean
}

/**
 * The plain map's nudge, fed every touch by the map view's touch listener after navigation's, and
 * never consuming one. Its own [NudgeElasticDriver] (no top padding: the plain map has none) and
 * [NudgeFlingGuard], each told "following" by [plainMapFollowing], and the [PlainNudgeLeftover].
 * Main thread only.
 */
internal class PlainMapNudge(
    private val map: PlainNudgeMap,
    private val springBack: () -> Boolean,
    private val navigating: () -> Boolean,
    thresholdPx: Float,
) {
    private val leftover = PlainNudgeLeftover()
    private val driver = NudgeElasticDriver(
        camera = object : NudgeCamera {
            override fun canGive() = following()

            override fun showPadding(padding: DoubleArray) {
                map.showPadding(padding)
                leftover.noteShown(padding)
            }
        },
        thresholdPx = thresholdPx,
        viewTopPaddingPx = { 0.0 },
    )
    private val flingGuard = NudgeFlingGuard(
        object : FlingSwitch {
            override var enabled: Boolean
                get() = map.flingEnabled
                set(value) {
                    map.flingEnabled = value
                }
        },
    )
    private var downX = 0f
    private var downY = 0f

    private fun following() = plainMapFollowing(springBack(), navigating(), map.cameraMode())

    fun onTouch(event: MotionEvent, density: Float) {
        driver.onTouch(event)
        val following = following()
        flingGuard.onTouch(event, stillFollowing = following)
        val plainMap = springBack() && !navigating()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
            // Navigation's release line, for the plain map: whether it still follows and how far the finger went. Logging only.
            MotionEvent.ACTION_UP -> if (plainMap) {
                Log.i(NAVIGATION_VIEW_LOG_TAG, "touch released: mode=${map.cameraMode()}, travel=${"%.0f".format(hypot(event.x - downX, event.y - downY) / density)} dp")
            }
        }
        if (leftover.onRelease(event.actionMasked, plainMap, stillFollowing = following)) map.clearPaddingKeepingPicture()
    }

    /**
     * Locate was tapped, just before it sets following again (dispatch 2026-10-19-37, the owner's ruling "Fix it in #337
     * first"). Following can end during the spring with no finger down (a search frame, the reset-orientation tap), and
     * the driver then stops the spring with part of the give still on screen as padding, which no release clears. Tracking
     * would centre the dot in the padded area, up to half the threshold off centre, so on the plain map any padding the
     * camera holds is cleared first, keeping the picture, and tracking then moves from there to the dot. The camera's own
     * padding is read, not the leftover's record, so every way the spring can be cut short is covered. Never while
     * navigating: navigation applies its own padding. Only once following has ended (Sorrel's review of 2589c0d9): while it
     * is still on (locate tapped mid-spring, or a finger holding a give), the clear's camera move would itself end
     * following (MapLibreMap notifies its developer-animation listeners, which set NONE) and locate's transition would
     * replace the spring; there the spring settles the padding at zero itself, and locate while following changes nothing,
     * as before.
     */
    fun beforeLocate() {
        if (!springBack() || navigating() || map.cameraMode() != CameraMode.NONE) return
        if (map.paddingShown()) {
            Log.i(NAVIGATION_VIEW_LOG_TAG, "nudge: locate with a give left on screen; clearing it first")
            map.clearPaddingKeepingPicture()
        }
        leftover.forget()
    }

    /** The map is going away. */
    fun cancel() = driver.cancel()
}

/**
 * Clears the camera padding a give left once following ended, keeping the picture where it is: the
 * target moves to the map point at the view's centre, which is where it sits with no padding. Marked
 * as the app's own move first ([CameraMoveClassifier.markAppMove]). Writes nothing to the data
 * refresh's applied target (dispatch 2026-10-19-36): following has ended, and the map shows what the
 * user dragged to. Run at the release, before MapLibre handles it, so a fling starts from here.
 * Whether the picture stays still is device-only: a real MapView cannot run under Robolectric.
 */
internal fun clearNudgePaddingKeepingPicture(map: MapLibreMap, mapView: View, classifier: CameraMoveClassifier) {
    val centre = map.projection.fromScreenLocation(PointF(mapView.width / 2f, mapView.height / 2f))
    classifier.markAppMove()
    map.cameraPosition = CameraPosition.Builder(map.cameraPosition).target(centre).padding(0.0, 0.0, 0.0, 0.0).build()
    Log.i(NAVIGATION_VIEW_LOG_TAG, "nudge: following ended with a give on screen; padding cleared, picture kept")
}
