package com.zynergylabs.forager.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import com.zynergylabs.forager.app.location.hasLocationPermission
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.CompassEngine
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * MapLibre's own puck-movement animation runs on a fixed internal base duration
 * ([org.maplibre.android.location.LocationComponentOptions.trackingAnimationDurationMultiplier]
 * scales it, rather than taking an absolute millisecond value) — verified against the pinned
 * `13.5.0` artifact with `javap -v` on the package-private `LocationComponentConstants` class,
 * since it isn't part of the public API surface: `TRANSITION_ANIMATION_DURATION_MS = 750L`. Not
 * guaranteed stable across SDK versions; re-verify the same way after any MapLibre bump.
 */
internal const val LOCATION_COMPONENT_BASE_ANIMATION_DURATION_MS = 750f

/** Dispatch 2026-09-28-510: the dot's look decisions, `adb logcat -s ForagerDot`. Diagnostic; nothing reads it. */
internal const val POSITION_DOT_LOG_TAG = "ForagerDot"

/**
 * docs/motion-spec.md §2 "User location": animate only on meaningful GPS change, avoid jitter.
 * [LocationComponentOptions.trackingAnimationDurationMultiplier] is the one knob MapLibre
 * exposes for how long the puck takes to glide to a new fix -- a multiplier on
 * [LOCATION_COMPONENT_BASE_ANIMATION_DURATION_MS], not an absolute millisecond value -- so this
 * scales [MotionTokens.LOCATION_INDICATOR_MOVE_DURATION_MS] against that base rather than
 * leaving the SDK default in place. A plain function, not inlined into
 * [activateLiveLocationIfPermitted], so the computed value is unit-testable without the native
 * MapLibre objects that function needs.
 */
internal fun locationIndicatorTrackingAnimationMultiplier(): Float =
    MotionTokens.LOCATION_INDICATOR_MOVE_DURATION_MS / LOCATION_COMPONENT_BASE_ANIMATION_DURATION_MS

/**
 * The options [activateLiveLocationIfPermitted] hands MapLibre for the puck. Its own function so the
 * options are unit-testable: `LocationComponentOptions` is a plain value class, unlike the
 * native-backed `Style` the activation itself needs.
 */
internal fun liveLocationComponentOptions(context: Context, navigating: Boolean = false, look: PositionLook = PositionLook.PRECISE): LocationComponentOptions =
    LocationComponentOptions.builder(context)
        // Dispatch 2026-09-28-457, Parts B and C: while navigating, MapLibre's own tracking-gesture
        // management (off by default, 13.5.0) raises the move thresholds while following: a one-finger
        // drag must pass NAVIGATION_NUDGE_THRESHOLD_DP and a two-finger movement
        // NAVIGATION_MULTI_FINGER_MOVE_THRESHOLD_DP before tracking ends. Seen at the desk without it:
        // a pinch's second finger, then about 0.1 to 0.2 s later "tracking changed to 8" by the library,
        // and a one-finger nudge the same within 40 ms. Off outside navigation, so the map there is unchanged.
        .trackingGesturesManagement(navigating)
        .apply {
            if (navigating) {
                val density = context.resources.displayMetrics.density
                trackingInitialMoveThreshold(NAVIGATION_NUDGE_THRESHOLD_DP * density)
                trackingMultiFingerMoveThreshold(NAVIGATION_MULTI_FINGER_MOVE_THRESHOLD_DP * density)
            }
        }
        // Duration ratio, not an absolute value: see locationIndicatorTrackingAnimationMultiplier()
        // above — its base (750ms) came from javap-inspecting the pinned MapLibre artifact, not
        // public API or documentation, so it can silently go stale on a MapLibre version bump.
        .trackingAnimationDurationMultiplier(locationIndicatorTrackingAnimationMultiplier())
        // Below the fan's lowest layer, so an open fan draws over the puck (dispatch 2026-09-28-290).
        // The owner, on seeing the puck over an open fan: "When the icon expands over user location,
        // the user location overlaps it. Can the fan icons move on top of the user location instead
        // of beneath?" With no position given MapLibre adds the puck on top of the style, over the
        // fan. Only the puck's topmost layer is placed by this; the rest of its layers (accuracy
        // circle, background, shadow, and the bearing or foreground) are each added directly below
        // the one before, so the whole puck sits just under the fan, above every registry layer
        // (13.5.0, read with javap on LocationComponentPositionManager and
        // SymbolLocationLayerRenderer). The layer must already be in the style: see
        // addFanOutLayers, which initializeOverlayLayers runs before either activation call.
        .layerBelow(FanOutIds.LEGS_CASING_LAYER)
        // Dispatch 2026-09-28-510: how the dot is drawn for the position the app has judged (MapPosition.kt).
        // PRECISE adds nothing, so a GPS dot is MapLibre's own exactly as before. The other two point the dot
        // at the app's own images, by name (registered on every style load, addPositionLookImages), and never
        // change MapLibre's own: two causes, both seen at the S22 desk step, kept the GPS dot pale after GPS
        // took over. MapLibre tints with setTint and no mutate(), so a tint applied to its own vector dot stays
        // on every later copy; and MapLibre does not redraw an image replaced by one of the same size under the
        // same name (PuckImageTintTest). A different name is a different image, so switching looks switches
        // what is drawn. The heading arrow is left MapLibre's, since the compass is live whatever the position.
        // Both turn off MapLibre's 30 s stale greying: the app decides when a position is old, and a network
        // reading arriving every 20 s or so would otherwise flicker between soft and grey.
        .apply {
            when (look) {
                PositionLook.PRECISE -> Unit
                PositionLook.APPROXIMATE -> {
                    foregroundName(PUCK_APPROXIMATE_IMAGE)
                    accuracyAlpha(APPROXIMATE_ACCURACY_ALPHA)
                    enableStaleState(false)
                }
                PositionLook.LAST_KNOWN -> {
                    foregroundName(PUCK_LAST_KNOWN_IMAGE)
                    accuracyColor(LAST_KNOWN_COLOR)
                    enableStaleState(false)
                }
            }
        }
        .build()

/**
 * The options to apply to an already-initialised LocationComponent just before [liveLocationComponentOptions],
 * or `null` when nothing is needed first (dispatch 2026-09-28-318, fail 3).
 *
 * After a basemap swap the SDK has already put the puck's layers back, before this app's layers exist (see
 * `LiveLocationPuckSwapTest` for the chain, read from 13.5.0 with javap), so the puck sits under the markers and the fan.
 * Re-activating hands the SDK the options it already holds, and it moves nothing for those. Applying the same options with
 * no position first makes the next application a change, so the SDK takes the puck's layers out and adds them again
 * below [FanOutIds.LEGS_CASING_LAYER], which exists by then. Both happen in one call on the main thread, so no frame is
 * drawn between them. Rejected: moving the puck's layers by their own ids (those names are the SDK's, not public API),
 * and adding this app's layers through `Style.Builder` so they exist before the SDK re-places the puck (it restructures
 * working code). A component not yet initialised builds its layers where [options] say, so it needs nothing first.
 */
internal fun puckReplacementOptions(alreadyInitialised: Boolean, options: LocationComponentOptions): LocationComponentOptions? =
    if (alreadyInitialised) options.toBuilder().layerAbove(null).layerBelow(null).build() else null

/**
 * Turns on MapLibre's own "blue dot" location puck and has the camera follow it — "like regular
 * GPS," the project owner's own framing, rather than the compass strip's pre-existing one-shot
 * locate-me fetch (which still exists unchanged, feeding that strip's own text readout, not the
 * map's camera). A no-op, not a crash or a silent guess, when [Manifest.permission.ACCESS_FINE_LOCATION]/
 * [Manifest.permission.ACCESS_COARSE_LOCATION] aren't granted — same "explicit unsupported state,
 * never fabricated" rule the one-shot path ([com.zynergylabs.forager.app.location.AndroidLocationProvider]) behind the shared [hasLocationPermission]
 * already follows; the map simply won't show a puck until permission exists
 * and something re-triggers this (a fresh style load, or the locate-me icon — see
 * [resumeTrackingRequestId][MapOverlayContent.resumeTrackingRequestId]'s own doc comment).
 *
 * [restoreCameraMode] is what this composable's own basemap-swap effect passes to avoid a real
 * hardware-reported bug: `setStyle` (any basemap change, or a night-mode toggle, which shares this
 * same path on every basemap) discards the LocationComponent outright, so this function has to run again on every
 * such swap just to keep the puck visible — but always re-forcing [CameraMode.TRACKING] here, as
 * this used to do, snapped the camera back onto the user's location on every basemap switch even
 * after they had deliberately panned away, which the GPS/locate-me icon is the control for, not
 * this one. `null` (the default, and what the locate-me icon's own re-activation call passes)
 * means "genuinely first activation" and still defaults to [CameraMode.TRACKING] plus the
 * zoom-in below; a non-null value restores exactly that mode instead, whatever it was.
 *
 * The zoom-in only fires on that genuine first activation ([restoreCameraMode] `== null`) — the
 * project owner's own ask ("when starting the maps, on any map mode, have it zoom in
 * automatically and center on user location") — not on every basemap-swap re-activation, where it
 * would just be the same unwanted recenter one step removed (this time snapping zoom rather than
 * position). Zoom only, not target: [CameraMode.TRACKING] already owns panning to the live fix,
 * so this doesn't fight it by also specifying a target.
 *
 * Breaking out of [CameraMode.TRACKING] again is built into MapLibre's
 * [LocationComponent][org.maplibre.android.location.LocationComponent] itself, not code this app
 * wrote: the SDK's own gesture detection drops to [CameraMode.NONE] the moment the user pans,
 * drags, or zooms, which is also what the data+camera refresh effect above checks to decide
 * whether it's safe to move the camera itself without fighting an active puck.
 */
@SuppressLint("MissingPermission") // hasLocationPermission(context) is the real (runtime) check.
internal fun activateLiveLocationIfPermitted(
    map: MapLibreMap,
    style: Style,
    context: Context,
    // @CameraMode.Mode is an IntDef (see org.maplibre.android.location.modes.CameraMode's own
    // javap output -- a final class of `int` constants, not a real Kotlin type), so this and
    // LocationComponent's own cameraMode property are both plain Int, not CameraMode.
    restoreCameraMode: Int? = null,
    // Ruling A (continuation 2026-09-28-432): the app's true-heading engine, when the map has one.
    compassEngine: CompassEngine? = null,
    // Dispatch 2026-09-28-510: the app's position for the dot, when the map has one, and how to draw it.
    // Without one, MapLibre's own engine stays, as before.
    locationEngine: AppLocationEngine? = null,
    look: PositionLook = PositionLook.PRECISE,
    // Dispatch 2026-10-19-37: the map springs back from a small nudge (MapRenderMode.nudgeSpringBack), so it takes
    // the plain nudge's options (PlainMapNudge.kt). `false`: the ordinary options, exactly as before.
    nudgeSpringBack: Boolean = false,
) {
    if (!hasLocationPermission(context)) return
    val locationComponent = map.locationComponent
    val options = ordinaryLocationOptions(context, look, nudgeSpringBack)
    puckReplacementOptions(locationComponent.isLocationComponentActivated, options)?.let { locationComponent.applyStyle(it) }
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .apply { if (locationEngine != null) locationEngine(locationEngine) else useDefaultLocationEngine(true) }
            .build(),
    )
    locationComponent.isLocationComponentEnabled = true
    compassEngine?.let { locationComponent.compassEngine = it }
    // COMPASS, not NORMAL: the puck itself points the device's own heading, the same live sensor
    // the compass strip's heading text already reads — matching, not duplicating, that readout.
    locationComponent.renderMode = RenderMode.COMPASS
    locationComponent.cameraMode = restoreCameraMode ?: CameraMode.TRACKING
    if (restoreCameraMode == null) {
        map.easeCamera(CameraUpdateFactory.zoomTo(FIRST_ACTIVATION_ZOOM))
    }
}

/**
 * The zoom level a first-ever GPS activation eases to, once — a close, orienting view rather than
 * whatever the region-search zoom heuristic ([zoomForRadiusKm], topping out at 13.0) happened to
 * leave the camera at. 16.0 is the conventional "street level" a locate-me control zooms to
 * elsewhere (mainstream maps apps, among others) — not derived from anything specific to this app.
 */
private const val FIRST_ACTIVATION_ZOOM = 16.0
