package com.zynergylabs.forager.app.ui.map

import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationFix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.CompassEngine
import org.maplibre.android.location.CompassListener
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.OnLocationCameraTransitionListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap

/*
 * The map's navigation view (dispatch 2026-09-28-430, plan task T22; rulings in continuation
 * 2026-09-28-432). The owner's step path, confirmed with "Go on T22":
 *
 *   Tap Return > the map tilts about 45°, turns so the way you face is up, and places you a little
 *   below the centre; it follows you.
 *   Drag the map > it stops following; "Return to Route" appears; a tap brings the view back.
 *   The compass gets stuck > "Compass calibrating…"; about 15 s of retrying; if it recovers the view
 *   carries on, if not the map turns north-up and says so; facing-up comes back by itself.
 *   Stop navigating > flat and north-up, as it was.
 *
 * The figures and the wording are the planner's, provisional, each named once here.
 */

/** How far the map tilts while navigating. The owner: "enough to focus on the path ahead, not so gentle that it's a cosmetic tilt." */
const val NAVIGATION_VIEW_TILT_DEGREES = 45.0

/**
 * The share of the map's height added as top padding while navigating, so the followed point (the
 * walker) sits below the centre and more of the map ahead is shown. With a top padding of P the
 * map centres in the remaining height, so the walker sits P / 2 below the middle: a quarter of the
 * height puts them an eighth of the way down from the centre.
 */
const val NAVIGATION_VIEW_TOP_PADDING_FRACTION = 0.25

/** How long a stuck compass is retried before the map turns north-up. */
const val NAVIGATION_COMPASS_RETRY_MILLIS = 15_000L

/** How often the stuck-compass clock is read, so the retry window ends on time with no new reading. */
private const val NAVIGATION_FACING_TICK_MILLIS = 250L

/** The HUD's heading label while a stuck compass is retried (ruling E: one place, one word). */
const val COMPASS_CALIBRATING_TEXT = "Compass calibrating…"

/** The HUD's heading label once the map has turned north-up because the compass cannot be used. */
const val COMPASS_NORTH_UP_TEXT = "Compass unavailable · north up"

/** Which way the map faces while navigating. */
enum class NavigationFacing {
    /** The way the walker faces is up. Also the state with no fix yet: nothing has failed. */
    FACING_UP,

    /** The compass is stuck; the map holds its bearing while it is retried. */
    CALIBRATING,

    /** The compass could not be used: north is up, and the map still follows. */
    NORTH_UP,
}

/**
 * Tells "stuck" apart from what [TrueHeadingReading] already says, with no new threshold
 * (continuation -432): stuck is [TrueHeadingReading.Unreliable], which is
 * [com.zynergylabs.forager.app.domain.CompassTrustJudge]'s existing band. Retried for
 * [retryMillis], then north-up; back to facing-up the moment the reading is
 * [TrueHeadingReading.Available] again. [TrueHeadingReading.NoSensor] is terminal: north-up at
 * once, with no retry window (ruling B). [TrueHeadingReading.NeedsFix] is not a compass fault.
 *
 * Not caught: a sensor that silently stops reporting. Nothing in the app measures that, and no new
 * threshold is made for it.
 */
class NavigationFacingJudge(private val retryMillis: Long = NAVIGATION_COMPASS_RETRY_MILLIS) {
    private var unreliableSinceMillis: Long? = null

    fun next(reading: TrueHeadingReading, nowMillis: Long): NavigationFacing = when (reading) {
        is TrueHeadingReading.Available, TrueHeadingReading.NeedsFix -> {
            unreliableSinceMillis = null
            NavigationFacing.FACING_UP
        }
        TrueHeadingReading.NoSensor -> NavigationFacing.NORTH_UP
        TrueHeadingReading.Unreliable -> {
            val since = unreliableSinceMillis ?: nowMillis.also { unreliableSinceMillis = it }
            if (nowMillis - since >= retryMillis) NavigationFacing.NORTH_UP else NavigationFacing.CALIBRATING
        }
    }
}

/**
 * [NavigationFacingJudge] over the one true heading, while [isNavigating]; [NavigationFacing.FACING_UP]
 * otherwise. Judged on every change of the heading, and on a short tick so the 15 s window ends on
 * time even if no new reading arrives. Read the result in a leaf: it changes rarely.
 */
@Composable
fun rememberNavigationFacing(heading: State<TrueHeadingReading>, isNavigating: Boolean, currentTime: CurrentTimeProvider): State<NavigationFacing> =
    produceState(initialValue = NavigationFacing.FACING_UP, isNavigating, heading, currentTime) {
        if (!isNavigating) {
            value = NavigationFacing.FACING_UP
            return@produceState
        }
        val judge = NavigationFacingJudge()
        // At once on every change of the heading, so the label never shows the old word first; and
        // on a short tick, so the retry window ends on time with no new reading. One dispatcher, so
        // the two never run the judge at the same moment.
        launch { snapshotFlow { heading.value }.collect { value = judge.next(it, currentTime.nowEpochMillis()) } }
        while (true) {
            delay(NAVIGATION_FACING_TICK_MILLIS)
            value = judge.next(heading.value, currentTime.nowEpochMillis())
        }
    }

/**
 * What the map is asked for while navigating (null otherwise). [following] is false once the user
 * has moved away from the view (a drag, or "Reset orientation"); the map then leaves the camera
 * where they put it until [restoreRequestId] changes ("Return to Route", or locate). [onLeftView]
 * is how the map reports such a move. Built so plan task T8 can turn it on by the same predicate,
 * `AvailabilityScreen`'s `isNavigating`.
 */
data class NavigationViewRequest(
    val facing: NavigationFacing,
    val following: Boolean,
    val restoreRequestId: Int,
    val onLeftView: () -> Unit,
    /** Dispatch 2026-09-28-440: true from the start of a navigation until the map has applied [NAVIGATION_VIEW_ZOOM] once. */
    val zoomOnStart: Boolean = false,
    /** The map reports the start zoom applied, so it is not applied again in this navigation. */
    val onStartZoomApplied: () -> Unit = {},
)

/**
 * The zoom the navigation view starts at, once per navigation (dispatch 2026-09-28-440; the owner:
 * "About two blocks ahead"). Provisional. The working, for the S22's map (about 797 dp tall) at the
 * owner's latitude (45.4° N), with MapLibre's default 36.87° field of view (a focal length of
 * 0.5 × 797 / tan 18.43° = 1195 dp), the 45° tilt and the quarter-height top padding (the walker at
 * 62.5% of the height): ground ahead = 0.7071 × (tan(45° + α) − 1) × 1195 × metres per dp. At 18,
 * about 158 m ahead to the HUD's lower edge and about 250 m to the map's top; at 17, about twice
 * that. Arithmetic, not measured on the phone.
 */
const val NAVIGATION_VIEW_ZOOM = 18.0

/**
 * The camera's maximum zoom: each basemap's own operating limit ([Basemap.maxZoom]), raised to
 * [NAVIGATION_VIEW_ZOOM] while navigating (Amendment 1; the owner: "Zoom in past the cap"). Only
 * the camera's limit moves. The tile sources' own `maxzoom` in the style ([styleJsonFor], which takes
 * no navigation input, held by `BasemapStyleTest`) does not, so no tile beyond a basemap's limit is
 * ever requested: MapLibre enlarges the deepest tiles instead, softer on Topo.
 */
fun navigationMaxZoom(basemap: Basemap, navigating: Boolean): Double =
    if (navigating) maxOf(basemap.maxZoom.toDouble(), NAVIGATION_VIEW_ZOOM) else basemap.maxZoom.toDouble()

/**
 * MapLibre's [CompassEngine] fed the app's own true heading (ruling A). MapLibre's built-in engine
 * (`LocationComponentCompassEngine`) works from the rotation vector alone with no declination,
 * so it reads magnetic north: inferred from its bytecode in the pinned 13.5.0 artifact (no
 * `GeomagneticField` call; `getRotationMatrixFromVector`, then `getOrientation`), not measured on
 * a phone. On a true-north map that turned the puck about 15° off in the Pacific Northwest, and
 * would have turned a compass-following map the same. This hands MapLibre the heading the compass
 * strip and the HUD read, smoothed and trust-judged; while that is not available it hands nothing,
 * and the last heading stands.
 */
class TrueHeadingCompassEngine : CompassEngine {
    private val listeners = mutableListOf<CompassListener>()
    private var lastHeading = 0f
    private var lastStatus = SensorManager.SENSOR_STATUS_UNRELIABLE

    override fun addCompassListener(listener: CompassListener) {
        listeners += listener
    }

    override fun removeCompassListener(listener: CompassListener) {
        listeners -= listener
    }

    override fun getLastHeading(): Float = lastHeading

    override fun getLastAccuracySensorStatus(): Int = lastStatus

    /** One reading of the app's true heading. Only [TrueHeadingReading.Available] moves the heading. */
    fun update(reading: TrueHeadingReading) {
        val status = if (reading is TrueHeadingReading.Available) SensorManager.SENSOR_STATUS_ACCURACY_HIGH else SensorManager.SENSOR_STATUS_UNRELIABLE
        if (status != lastStatus) {
            lastStatus = status
            listeners.toList().forEach { it.onCompassAccuracyChange(status) }
        }
        if (reading is TrueHeadingReading.Available) {
            lastHeading = reading.degrees
            listeners.toList().forEach { it.onCompassChanged(reading.degrees) }
        }
    }
}

/** The top padding, in pixels, that places the walker below the centre of a map [mapHeightPx] tall. */
fun navigationViewTopPaddingPx(mapHeightPx: Int): Double = mapHeightPx * NAVIGATION_VIEW_TOP_PADDING_FRACTION

/**
 * What a map needs to make the app's true heading for itself (ruling A, continuation
 * 2026-09-28-432, and the planner's ruling (i) on the Journal's centre-pin picker): the compass,
 * declination, and the live fix declination is read at. Provided once by `AvailabilityScreen` to
 * every map under it, so a map that is not handed a heading (the pickers) still points its puck to
 * true north, and the Journal's own composables pass nothing new. A map makes its heading only
 * while it is composed, so the compass is not read on a tab with no map.
 */
data class MapCompass(
    val compassProvider: CompassProvider,
    val computeTrueHeading: ComputeTrueHeadingUseCase,
    val liveFix: State<LocationFix.Update?>,
)

/** See [MapCompass]. `null` outside `AvailabilityScreen`: such a map keeps MapLibre's own compass. */
val LocalMapCompass = staticCompositionLocalOf<MapCompass?> { null }

/**
 * Nudge protection while navigating (dispatch 2026-09-28-457, Part C; the owner: "Small nudge snaps
 * back", "about a finger's width"): a one-finger drag shorter than this leaves the camera following;
 * only a longer, deliberate drag leaves the view. MapLibre's own default is 25 dp.
 *
 * 72 dp since dispatch 2026-09-28-477, Amendment 1; it was 48 dp, a finger's width. Seen on the S22:
 * quick flicks the owner meant as nudges travelled 56 to 91 dp and crossed 48 dp before the finger
 * lifted, so they left following. The owner chose "Raise the line for all drags", at "1.5 finger
 * widths, 72 dp", and confirmed the step path ("Confirmed"). The nudge's give cap follows it, half of
 * this, 36 dp. Provisional.
 */
const val NAVIGATION_NUDGE_THRESHOLD_DP = 72f

/**
 * How far two fingers must move together before following ends while navigating (Part B): above any
 * pinch's drift, so a pinch zooms and keeps following. MapLibre's own default for this option, 400 dp,
 * stated here so it is a choice, not an accident.
 */
const val NAVIGATION_MULTI_FINGER_MOVE_THRESHOLD_DP = 400f

/**
 * The elastic nudge (dispatch 2026-09-28-463; the owner: "Give, then spring back"): under
 * [NAVIGATION_NUDGE_THRESHOLD_DP] the map moves with one finger but stiffly, this share of the finger's
 * travel, so at most half the threshold before a real drag takes over. Provisional.
 */
const val NAVIGATION_NUDGE_GIVE_RATIO = 0.5f

/** How long the map takes to spring back to following once the nudging finger lifts. The owner: "about a quarter of a second". Provisional. */
const val NAVIGATION_NUDGE_SPRING_MILLIS = 250L

/**
 * How far the spring carries past rest before it settles: Android's `OvershootInterpolator` tension,
 * its own default. 2.0 peaks 13% past rest, about 3 dp after a full 24 dp give; 1.5 would be 8%.
 * The owner: "a slight overshoot and settle". Provisional.
 */
const val NAVIGATION_NUDGE_OVERSHOOT_TENSION = 2.0

/**
 * The start zoom's timing, apart from MapLibre so it can be tested (dispatch 2026-09-28-457, Part A).
 *
 * Seen on the owner's walk and at the desk (the `ForagerNavView` and `Mbgl-LocationComponent` lines in
 * the report): the navigation effect re-runs within milliseconds of Return, while the first mode
 * transition is still running. A second request for the same mode made MapLibre call the transition
 * listener at once, so the zoom (with the tilt and padding) was issued mid-transition and refused, and
 * the refusal cleared the pending flag; the first transition then finished without it.
 *
 * So: a request for the mode a transition of ours is already heading to waits for that transition
 * instead of being re-sent; the wanted start zoom is held, and handed out only by the listener of the
 * transition actually in flight, when it really finishes. A listener superseded by a newer request
 * hands out nothing, and the zoom stays held for the newer one.
 */
class StartZoomGate {
    private var generation = 0
    private var inFlight: Int? = null
    private var wanted: Double? = null

    /** Whether a mode transition of ours is running, during which MapLibre refuses padding (dispatch -463: no nudge give then). */
    val transitioning: Boolean get() = inFlight != null

    /** A view is asked for. Returns the generation to give its transition listener, or `null` to wait for the transition already heading to the same mode. */
    fun request(sameModeAsInFlight: Boolean, startZoom: Double?): Int? {
        if (startZoom != null) wanted = startZoom
        if (inFlight != null && sameModeAsInFlight) return null
        generation++
        inFlight = generation
        return generation
    }

    /** The transition for [generation] has finished. Returns the start zoom to apply now, if this is the transition in flight and one is held. */
    fun finished(generation: Int): Double? {
        if (inFlight != generation) return null
        inFlight = null
        return wanted.also { wanted = null }
    }

    /** The transition for [generation] was cancelled: nothing is applied, and the zoom stays held. */
    fun cancelled(generation: Int) {
        if (inFlight == generation) inFlight = null
    }

    /** Navigation has stopped. */
    fun reset() {
        inFlight = null
        wanted = null
    }
}

/** The log tag the navigation view's camera lines go under (read on the device check). */
const val NAVIGATION_VIEW_LOG_TAG = "ForagerNavView"

/**
 * The time the navigation view's own camera changes take, so the tilt and the turn ease in rather than jump. Internal since
 * motion Part 2, Amendment 1 (RECORD -672): the strip and the navigation display slide for exactly this long
 * (MotionTokens.navigationViewChromeSpec), read from here so the two cannot drift apart.
 */
internal const val NAVIGATION_VIEW_TRANSITION_MILLIS = 750L

/**
 * The navigation view's camera changes on MapLibre's [LocationComponent], and the one listener
 * that tells the user's moves away from the view apart from the app's own. Every change here is
 * the SDK's tracking API (a camera mode, with its own bearing and tilt, and the padding while
 * tracking), never a camera move, because a camera move from the app ends MapLibre's tracking.
 *
 * The camera modes: [NavigationFacing.FACING_UP] is [CameraMode.TRACKING_COMPASS] (position, and
 * bearing from the compass engine); [NavigationFacing.CALIBRATING] is [CameraMode.TRACKING]
 * (position; the bearing holds); [NavigationFacing.NORTH_UP] is [CameraMode.TRACKING_GPS_NORTH]
 * (position, north up).
 *
 * A user's move away is any change of camera mode the app did not make, while following: a drag
 * ends tracking (NONE), and a rotate gesture ends the compass's hold on the bearing. A pinch to
 * zoom keeps MapLibre tracking and is not a move away. What MapLibre does on a phone is device-only.
 */
class NavigationModeChange {
    private var appChanging = false
    private var listeningTo: LocationComponent? = null

    /** The camera mode the view asked for while following; `null` once the user has moved away, and outside navigation. */
    var expected: Int? = null
        private set

    /** Whether the navigation view has been applied and navigation has not stopped since. */
    var active = false
        private set

    private val startZoomGate = StartZoomGate()
    private var onStartZoomAppliedHeld: () -> Unit = {}

    /** Whether a mode transition of ours is running: the padding, and so a nudge's give, is refused until it ends. */
    val transitioning: Boolean get() = startZoomGate.transitioning

    // Dispatch -470, Part A: what of ours is still animating when navigation is left, and what moves the
    // camera during the leave's ease, for reading on the phone. Logging only.
    // A token per padding animation, not a count: MapLibre can call both a callback's onCancel and its
    // onFinish for one animation (seen on the S22: a count went to -4), so only the latest animation's
    // own first callback clears it.
    private var paddingAnimationToken = 0
    private var paddingAnimationInFlight: Int? = null
    private var startZoomInFlight = false
    private var leaveEaseStartedAt: Long? = null

    /** A camera move has started (any reason): logged while the leave's ease runs, with what of ours is still animating. */
    fun noteCameraMoveStarted(reason: Int) {
        // A window, not the ease's own life: MapLibre posts the move-started notice to the main looper,
        // so the notice of a move that cancels the ease arrives after the ease's cancel callback.
        val sinceLeave = android.os.SystemClock.uptimeMillis() - (leaveEaseStartedAt ?: return)
        if (sinceLeave > 1_500L) return
        Log.i(NAVIGATION_VIEW_LOG_TAG, "camera move during the leave's ease, $sinceLeave ms in: reason=$reason, ${inFlight()}")
    }

    /** The camera has moved: within 1 s of a leave, its tilt, zoom, padding and mode are logged, so what pulls it away from the leave's ease shows by what changes. */
    fun noteCameraMove(map: MapLibreMap) {
        val sinceLeave = android.os.SystemClock.uptimeMillis() - (leaveEaseStartedAt ?: return)
        if (sinceLeave > 1_000L) return
        logCamera(map, "camera during the leave's ease, $sinceLeave ms in")
    }

    private fun inFlight() = "padding animation in flight=${paddingAnimationInFlight != null}, start zoom in flight=$startZoomInFlight, transition in flight=${startZoomGate.transitioning}"

    /** Whether the location component holds the navigating options (nudge protection on). Reset by every activation, which applies the ordinary ones. */
    var gestureProtection = false

    /** Runs [block], an app-made change of camera mode, so the listener does not read it as the user's. */
    fun byTheApp(block: () -> Unit) {
        appChanging = true
        try {
            block()
        } finally {
            appChanging = false
        }
    }

    /** Listens to [map]'s location component once it is activated; [view] is read when a change arrives. */
    fun listenTo(map: MapLibreMap, view: () -> NavigationViewRequest?) {
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || listeningTo === component) return
        listeningTo = component
        component.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() = Unit

            override fun onCameraTrackingChanged(currentMode: Int) {
                // Dispatch -457: every tracking-mode change, for reading on the phone (a pinch that ends following, in particular).
                Log.i(NAVIGATION_VIEW_LOG_TAG, "tracking changed to $currentMode: expected=$expected, byTheApp=$appChanging, navigating=${view() != null}")
                if (appChanging) return
                val wanted = expected ?: return
                if (currentMode == wanted) return
                val request = view() ?: return
                if (!request.following) return
                expected = null
                request.onLeftView()
            }
        })
    }

    /**
     * The view for [facing]: its camera mode, then the tilt and the top padding that puts the walker
     * below the centre.
     *
     * The tilt and padding go in the mode change's own transition listener, not straight after it.
     * Read from the pinned 13.5.0 bytecode, and seen on the S22 desk step:
     * - `paddingWhileTracking` and `tiltWhileTracking` are ignored ("the camera mode is
     *   transitioning") while a mode transition runs. Called straight after the mode change, the
     *   padding was always dropped, and the walker stayed at the centre.
     * - A mode change runs a transition, and applies the bearing and tilt handed to it, only from
     *   not following into following; between two following modes it does neither.
     * The listener is called at the transition's end, or at once when there is none, so the tilt and
     * padding land either way.
     */
    fun applyView(map: MapLibreMap, facing: NavigationFacing, topPaddingPx: Double, startZoom: Double? = null, onStartZoomApplied: () -> Unit = {}) {
        val mode = when (facing) {
            NavigationFacing.FACING_UP -> CameraMode.TRACKING_COMPASS
            NavigationFacing.CALIBRATING -> CameraMode.TRACKING
            NavigationFacing.NORTH_UP -> CameraMode.TRACKING_GPS_NORTH
        }
        Log.i(NAVIGATION_VIEW_LOG_TAG, "apply view: $facing, start zoom ${startZoom ?: "none"}, mode now ${map.locationComponent.cameraMode}, asking $mode")
        if (startZoom != null) onStartZoomAppliedHeld = onStartZoomApplied
        val generation = startZoomGate.request(sameModeAsInFlight = expected == mode, startZoom = startZoom)
        if (generation == null) {
            Log.i(NAVIGATION_VIEW_LOG_TAG, "apply view: a transition to $mode is in flight; waiting for it")
            return
        }
        expected = mode
        active = true
        val component = map.locationComponent
        val then = object : OnLocationCameraTransitionListener {
            override fun onLocationCameraTransitionFinished(cameraMode: Int) {
                val zoom = startZoomGate.finished(generation)
                if (expected != mode) return
                component.tiltWhileTracking(NAVIGATION_VIEW_TILT_DEGREES, NAVIGATION_VIEW_TRANSITION_MILLIS)
                // Dispatch -440: the start zoom, once per navigation, here for the same reason as the
                // tilt and padding (zoomWhileTracking is refused while a mode transition runs). Reported
                // applied when its animation ends, so the request does not change mid-animation.
                if (zoom != null) {
                    val applied = onStartZoomAppliedHeld
                    Log.i(NAVIGATION_VIEW_LOG_TAG, "start zoom requested: $zoom")
                    startZoomInFlight = true
                    component.zoomWhileTracking(
                        zoom,
                        NAVIGATION_VIEW_TRANSITION_MILLIS,
                        object : MapLibreMap.CancelableCallback {
                            override fun onFinish() {
                                startZoomInFlight = false
                                logCamera(map, "start zoom finished")
                                applied()
                            }

                            // Issued only once nothing is transitioning, so a cancel here is a gesture
                            // taking over: the walker's own zoom, as the owner chose for a pinch.
                            override fun onCancel() {
                                startZoomInFlight = false
                                logCamera(map, "start zoom cancelled")
                                applied()
                            }
                        },
                    )
                }
                val paddingToken = ++paddingAnimationToken
                paddingAnimationInFlight = paddingToken
                component.paddingWhileTracking(
                    doubleArrayOf(0.0, topPaddingPx, 0.0, 0.0),
                    NAVIGATION_VIEW_TRANSITION_MILLIS,
                    object : MapLibreMap.CancelableCallback {
                        override fun onFinish() {
                            if (paddingAnimationInFlight == paddingToken) paddingAnimationInFlight = null
                            logCamera(map, "view applied, $facing")
                        }

                        override fun onCancel() {
                            if (paddingAnimationInFlight == paddingToken) paddingAnimationInFlight = null
                            logCamera(map, "view applied, $facing, the padding cancelled")
                        }
                    },
                )
            }

            override fun onLocationCameraTransitionCanceled(cameraMode: Int) = startZoomGate.cancelled(generation)
        }
        byTheApp { component.setCameraMode(mode, NAVIGATION_VIEW_TRANSITION_MILLIS, null, null, NAVIGATION_VIEW_TILT_DEGREES, then) }
    }

    /** "Reset orientation" while navigating (ruling D): north-up, still following. The caller reports the move away. */
    fun northUpKeepingFollow(map: MapLibreMap) {
        expected = null
        byTheApp { map.locationComponent.setCameraMode(CameraMode.TRACKING_GPS_NORTH, NAVIGATION_VIEW_TRANSITION_MILLIS, null, null, null, null) }
    }

    /**
     * Navigation has stopped: flat and north-up, as before it started, still following if the map
     * was. A following-to-following mode change does not touch the bearing or the tilt (see
     * [applyView]), and plain tracking has no bearing of its own to ease, so following is paused,
     * the camera eased flat and north-up with no padding, and following resumed when that ends
     * (from not following into following, so its own transition goes to the walker). Seen on the
     * S22 desk step: the first form, a following-to-following change, left the map tilted and turned.
     *
     * [maxZoom] has no default: its old default, `Double.MAX_VALUE`, is out of MapLibre's range for
     * `setMaxZoomPreference` (lint Range: at most 25.5), and the one caller always passed a real cap
     * (lint dispatch 2026-10-17-03).
     */
    fun leaveNavigation(map: MapLibreMap, maxZoom: Double = Double.MAX_VALUE) {
        expected = null
        active = false
        startZoomGate.reset()
        val component = map.locationComponent
        val wasFollowing = component.cameraMode != CameraMode.NONE
        // Dispatch -470: the view's padding animation (applyView) is stopped first. Leaving tracking
        // cancels MapLibre's zoom and tilt animations but not the padding one (13.5.0 bytecode:
        // LocationComponent$8.onCameraTrackingChanged cancels zoom and tilt only), and its listener stays
        // attached in every mode, so it went on setting the padding through Transform.moveCamera after the
        // mode was NONE and cancelled the ease below. Seen on the S22: the ease's first frame set the
        // padding to 0, the next put it back to the animation's 282, and the ease was cancelled, leaving
        // the map tilted; every leave with that animation in flight did so, and none without.
        val inFlightAtLeave = inFlight() // read before the cancel below clears it
        component.cancelPaddingWhileTrackingAnimation()
        if (wasFollowing) byTheApp { component.cameraMode = CameraMode.NONE }
        // Dispatch -440, Amendment 1: a camera above the basemap's own cap comes back within it as part
        // of this ease, and the cap is restored when the ease ends; setting it first would jump.
        val flat = CameraPosition.Builder(map.cameraPosition).tilt(0.0).bearing(0.0).padding(0.0, 0.0, 0.0, 0.0)
            .zoom(minOf(map.cameraPosition.zoom, maxZoom)).build()
        val done = { what: String ->
            if (!active) map.setMaxZoomPreference(maxZoom)
            if (wasFollowing && !active) byTheApp { component.cameraMode = CameraMode.TRACKING }
            logCamera(map, what)
        }
        Log.i(NAVIGATION_VIEW_LOG_TAG, "leaving navigation: was following=$wasFollowing, at the leave $inFlightAtLeave")
        leaveEaseStartedAt = android.os.SystemClock.uptimeMillis()
        map.easeCamera(CameraUpdateFactory.newCameraPosition(flat), NAVIGATION_VIEW_TRANSITION_MILLIS.toInt(), object : MapLibreMap.CancelableCallback {
            override fun onFinish() = done("navigation left")

            override fun onCancel() = done("navigation left, the ease cancelled")
        })
    }

    /** The camera the navigation view left, for the device check: no position, only how the map is turned and tilted. */
    private fun logCamera(map: MapLibreMap, what: String) {
        val camera = map.cameraPosition
        Log.i(NAVIGATION_VIEW_LOG_TAG, "$what: mode=${map.locationComponent.cameraMode}, zoom=${"%.2f".format(camera.zoom)}, bearing=${"%.1f".format(camera.bearing)}, tilt=${"%.1f".format(camera.tilt)}, padding=${camera.padding?.joinToString { "%.0f".format(it) }}")
    }
}
