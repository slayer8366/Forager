package com.forager.app.ui.map

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [shouldMoveCameraToTarget] is the data+camera refresh effect's own decision of whether to move
 * the map camera — extracted as a plain function, the same reason
 * [locationIndicatorTrackingAnimationMultiplier] is one (see [LocationIndicatorMotionTest]'s doc
 * comment), so the actual defect is unit-testable rather than requiring the native, device-backed
 * `MapLibreMap`/`CameraPosition` the real effect also touches.
 *
 * The hardware-reported bug this guards: switching basemap or toggling night mode re-ran that
 * effect (it shares a `loadedStyle` key with the basemap-swap effect) and, once GPS tracking had
 * already been broken by a manual pan, recentered the camera on the search region regardless —
 * reading as "changing map style brought the map back to my location," which only the GPS/
 * locate-me icon is meant to do.
 */
class CameraTargetRecenterGuardTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 15)
    private val target = region to null

    @Test
    fun `moves the camera on first load, when nothing has been applied yet`() {
        assertTrue(shouldMoveCameraToTarget(isGpsTracking = false, target = target, lastAppliedCameraTarget = null))
    }

    @Test
    fun `does not move the camera again for the same target, the basemap-switch bug`() {
        assertFalse(shouldMoveCameraToTarget(isGpsTracking = false, target = target, lastAppliedCameraTarget = target))
    }

    @Test
    fun `moves the camera when the region actually changed`() {
        val newRegion = region.copy(lat = 46.0)
        assertTrue(shouldMoveCameraToTarget(isGpsTracking = false, target = newRegion to null, lastAppliedCameraTarget = target))
    }

    @Test
    fun `moves the camera when only focusOverride changed, region held constant`() {
        val focused = region to LatLng(45.6, -122.5)
        assertTrue(shouldMoveCameraToTarget(isGpsTracking = false, target = focused, lastAppliedCameraTarget = target))
    }

    @Test
    fun `never moves the camera while GPS tracking owns it, even for a target that would otherwise qualify`() {
        val newRegion = region.copy(lat = 46.0)
        assertFalse(shouldMoveCameraToTarget(isGpsTracking = true, target = newRegion to null, lastAppliedCameraTarget = target))
    }
}
