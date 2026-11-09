package com.zynergylabs.forager.app.ui.map

import android.location.Location
import android.os.Looper
import android.util.SparseArray
import java.lang.reflect.Proxy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.CameraMapFixture
import org.maplibre.android.maps.MapLibreMap
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The S22 on build 4990 (PR #507's merge), RECORD -2183 (J8) and -2192 (#511): landscape, navigating and following, a font change
 * recreates the activity; following stays on, but the walker's dot sits at the plain centre of the map, with none of the
 * navigation view's padding (y 771 against 936 with the top padding alone and 861.5 with J8's lift above the resting route card),
 * until the next card change moves the padding again.
 *
 * Why, read from MapLibre 13.5.0's bytecode: the recreated map's first style load restores following (MapStyleReload.kt, the camera
 * restore's TRACKING, set by activateLiveLocationIfPermitted). From not following with a known location, that mode change runs the
 * location component's own transition, an animated move of 750 ms during which `paddingWhileTracking` and `tiltWhileTracking` are
 * refused ("the camera mode is transitioning"). The navigation view applies itself straight after the style load, inside that
 * transition: its mode change calls its listener at once (a following-to-following change runs no transition, and a change that
 * cancels the running one posts that cancel, so the transition still reads as running), and the view's padding and tilt are
 * refused, at once and for good. The later D29/J8 shift runs only when the side insets change, so nothing applied them again.
 *
 * Run here on a real MapLibreMap and the SDK's own LocationComponent and LocationCameraController over a recording native map
 * (CameraMapFixture); what this reads is the padding (and the tilt) the location component's animator is given, the camera's own
 * input. Whether the S22 then draws the dot above the card is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationViewAfterRestoreTransitionTest {

    private val restored = MapLibreLatLng(45.3736, -121.6959)
    private val walker = MapLibreLatLng(45.3740, -121.6959)

    /** The view's padding with J8's bottom: the top quarter of a 1338 px map, and the lift above the resting route card. */
    private val topPx = 334.5
    private val j8 = NavigationSideInsets(0.0, 0.0, bottomPx = 153.0)

    @Test
    fun `facing up after the restore's transition, the view's padding with J8's bottom reaches the camera`() =
        theViewLandsAfterTheRestoreTransition(NavigationFacing.FACING_UP, endTransition = {})

    @Test
    fun `calibrating after the restore's transition, the view's padding with J8's bottom reaches the camera`() =
        theViewLandsAfterTheRestoreTransition(NavigationFacing.CALIBRATING, endTransition = { CameraMapFixture.finishAnimatedMove(it) })

    private fun theViewLandsAfterTheRestoreTransition(facing: NavigationFacing, endTransition: (MapLibreMap) -> Unit) {
        val start = CameraPosition.Builder().target(restored).zoom(16.0).bearing(0.0).tilt(0.0).padding(0.0, 0.0, 0.0, 0.0).build()
        val (map, recording) = CameraMapFixture.map(start = start, fit = start, cameraMode = CameraMode.NONE)
        recording.metersPerPixel = 1.0
        val component = map.locationComponent
        readyToTrack(component, recording)
        val modeChange = NavigationModeChange()

        restoreFollowing(component, modeChange)
        assertEquals("precondition: the restore set following", CameraMode.TRACKING, recording.cameraMode)
        assertTrue("precondition: the location component's own transition is running (an animated move to the walker)", transitioning(recording))
        // The animated move landed at once on its end camera with no padding of its own; the native map keeps the padding a move does
        // not set, so the camera keeps the restore's (here none).
        recording.camera = CameraPosition.Builder(recording.camera!!).padding(0.0, 0.0, 0.0, 0.0).build()

        modeChange.applyView(map, facing, topPx, sideInsetsPx = { j8 })
        endTransition(map)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue("precondition: the restore's transition has ended", !transitioning(recording))
        val padding = askedTarget(component, "ANIMATOR_PADDING") as DoubleArray?
        assertNotNull("the navigation view's padding never reached the camera after the restore's transition ($facing)", padding)
        assertArrayEquals("the view's padding, J8's bottom with it ($facing)", doubleArrayOf(0.0, topPx, 0.0, j8.bottomPx), padding, 0.001)
        val tilt = askedTarget(component, "ANIMATOR_TILT") as Float?
        assertNotNull("the navigation view's tilt never reached the camera after the restore's transition ($facing)", tilt)
        assertEquals("the view's tilt ($facing)", NAVIGATION_VIEW_TILT_DEGREES.toFloat(), tilt!!, 0.001f)
    }

    /** The first style load's restore of following, as activateLiveLocationIfPermitted sets it (LiveLocationPuck.kt). */
    @Suppress("UNUSED_PARAMETER")
    private fun restoreFollowing(component: LocationComponent, modeChange: NavigationModeChange) {
        component.cameraMode = CameraMode.TRACKING
    }

    /** The parts of an activated, started component a tracking transition and the padding read, set as activation leaves them. */
    private fun readyToTrack(component: LocationComponent, recording: CameraMapFixture.Recording) {
        set(component, "isLayerReady", true)
        set(component, "lastLocation", Location("test").apply { latitude = walker.latitude; longitude = walker.longitude })
        set(recording.cameraController, "isEnabled", true)
        val coordinator = get(component, "locationAnimatorCoordinator")!!
        @Suppress("UNCHECKED_CAST")
        val listeners = get(coordinator, "listeners") as SparseArray<Any>
        // The coordinator makes a camera animator only for a kind with a listener (createNewPaddingAnimator, 13.5.0), as the map's
        // own camera adapters register them; a listener that does nothing stands in for those.
        listeners.put(animatorId("ANIMATOR_PADDING"), noOpValueListener())
        listeners.put(animatorId("ANIMATOR_TILT"), noOpValueListener())
    }

    private fun transitioning(recording: CameraMapFixture.Recording): Boolean = get(recording.cameraController, "isTransitioning") as Boolean

    private fun noOpValueListener(): Any {
        val type = Class.forName("org.maplibre.android.location.MapLibreAnimator\$AnimationsValueChangeListener")
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "no-op value listener"
                else -> null
            }
        }
    }

    /** The target of the animator the coordinator was last given for [kind]: what the camera was asked for, or null if never asked. */
    private fun askedTarget(component: LocationComponent, kind: String): Any? {
        val coordinator = get(component, "locationAnimatorCoordinator")!!
        val animator = (get(coordinator, "animatorArray") as SparseArray<*>).get(animatorId(kind)) ?: return null
        return Class.forName("org.maplibre.android.location.MapLibreAnimator").getDeclaredMethod("getTarget").apply { isAccessible = true }.invoke(animator)
    }

    private fun animatorId(name: String): Int =
        Class.forName("org.maplibre.android.location.MapLibreAnimator").getDeclaredField(name).apply { isAccessible = true }.getInt(null)

    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            runCatching { return type.getDeclaredField(name).apply { isAccessible = true } }
            type = type.superclass
        }
        error("no field $name on ${owner.javaClass}")
    }

    private fun get(owner: Any, name: String): Any? = field(owner, name).get(owner)
    private fun set(owner: Any, name: String, value: Any?) = field(owner, name).set(owner, value)
}
