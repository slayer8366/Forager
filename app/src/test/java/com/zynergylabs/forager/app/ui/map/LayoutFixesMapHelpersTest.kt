package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.location.modes.CameraMode

/**
 * Part 1 layout fixes, the two pure decisions `SightingsMap` makes for items 4 and 5 (planner message
 * `2026-09-28-98`): what a new map restores from the camera the user left, and where MapLibre's
 * attribution button keeps from the edges. Applying either to a real `MapView` is a device item.
 */
class LayoutFixesMapHelpersTest {

    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
    private fun snapshot(following: Boolean) = MapCameraSnapshot(
        target = LatLng(45.3512, -122.6011),
        zoom = 15.5,
        bearing = 30.0,
        tilt = 10.0,
        following = following,
        appliedTarget = region to LatLng(45.33, -122.64),
    )

    // ── Item 4: cameraRestoreFor ──

    @Test
    fun `T4u a new map restores the camera the user left, following, with tracking and no zoom-in`() {
        val saved = snapshot(following = true)
        assertEquals(
            MapCameraRestore(saved.target, 15.5, 30.0, 10.0, saved.appliedTarget, CameraMode.TRACKING),
            cameraRestoreFor(saved, previousCameraMode = null),
        )
    }

    @Test
    fun `T4u a new map restores the camera the user left, panned away, with tracking off`() {
        val saved = snapshot(following = false)
        assertEquals(
            MapCameraRestore(saved.target, 15.5, 30.0, 10.0, saved.appliedTarget, CameraMode.NONE),
            cameraRestoreFor(saved, previousCameraMode = null),
        )
    }

    @Test
    fun `T4u with nothing saved there is no restore, and the map opens as a fresh map does`() {
        assertNull(cameraRestoreFor(null, previousCameraMode = null))
    }

    @Test
    fun `T4u a style swap on a live map keeps its own camera and mode, and restores nothing`() {
        assertNull(cameraRestoreFor(snapshot(following = false), previousCameraMode = CameraMode.TRACKING))
    }

    // ── Item 5: attributionMarginsPx ──

    private val defaults = intArrayOf(10, 11, 12, 13)

    @Test
    fun `T5u left to right, the end inset goes on the right margin and the bottom inset on the bottom`() {
        assertArrayEquals(intArrayOf(10, 11, 12 + 225, 13 + 360), attributionMarginsPx(defaults, bottomInsetPx = 360, endInsetPx = 225, isRtl = false))
    }

    @Test
    fun `T5u right to left, the end inset goes on the left margin`() {
        assertArrayEquals(intArrayOf(10 + 225, 11, 12, 13 + 360), attributionMarginsPx(defaults, bottomInsetPx = 360, endInsetPx = 225, isRtl = true))
    }

    @Test
    fun `T5u with no insets the defaults stand`() {
        assertArrayEquals(defaults, attributionMarginsPx(defaults, bottomInsetPx = 0, endInsetPx = 0, isRtl = false))
    }
}
