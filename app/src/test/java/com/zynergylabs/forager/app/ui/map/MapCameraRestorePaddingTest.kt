package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.CameraMapFixture
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECORD -2192 ("Exactly as I left it"; "Yes, stay panned"), the S22 on build 5054: panned away while navigating, then a font
 * change, and the recreated map showed the panned view moved by about (+770, -240) px in landscape. The navigation view's padding
 * stays on while panned away, the saved target is the centre of that padded view, and the new map restored the target at no
 * padding. What MapLibre then draws, and so the pixels, cannot be shown here (a real MapView cannot run under Robolectric, and the
 * stand-in map of the recreation tests has no padding to model), so this runs the production chain on the values instead: the
 * camera idle's snapshot of a real MapLibreMap over a recording native map (CameraMapFixture), the app's own saver across the
 * recreation, the first style load's restore, and the camera the new map's native side is given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapCameraRestorePaddingTest {

    /** The navigation view's padding in landscape: the D29 side inset on the right, the top quarter of the map's height. */
    private val navigationPadding = doubleArrayOf(0.0, 270.0, 1544.0, 0.0)

    private val left: CameraPosition = CameraPosition.Builder()
        .target(MapLibreLatLng(45.4123, -121.7531))
        .zoom(16.5)
        .bearing(30.0)
        .tilt(45.0)
        .padding(navigationPadding)
        .build()

    /** The camera the user [left] on one map, through the recreation, as given to the new map's native side. */
    private fun reopened(navigatingOnReopen: Boolean): CameraPosition {
        val (before, _) = CameraMapFixture.map(start = left, fit = left, cameraMode = CameraMode.NONE)
        val memory = MapCameraMemory().apply { saved = cameraSnapshotOf(before, appliedTarget = null) }
        val value = with(MapCameraMemoryAcrossRecreation) { SaverScope { true }.save(memory) }!!
        val restored = MapCameraMemoryAcrossRecreation.restore(value)!!
        val fresh = CameraPosition.Builder().target(MapLibreLatLng(0.0, 0.0)).zoom(1.0).build()
        val (after, recording) = CameraMapFixture.map(start = fresh, fit = fresh)
        applyCameraRestore(after, cameraRestoreFor(restored.saved, previousCameraMode = null, navigating = navigatingOnReopen)!!)
        return recording.camera!!
    }

    @Test
    fun `a view panned away from while navigating reopens at the padding it was left at`() {
        val camera = reopened(navigatingOnReopen = true)

        assertEquals("the target", left.target, camera.target)
        assertEquals("the zoom", left.zoom, camera.zoom, 0.0)
        assertArrayEquals("the padding the target is the centre of", navigationPadding, camera.padding, 0.0)
    }

    @Test
    fun `not navigating on the reopen the new map keeps its own padding`() {
        val camera = reopened(navigatingOnReopen = false)

        assertEquals("the target", left.target, camera.target)
        assertNull("the map's own padding, none given", camera.padding)
    }
}
