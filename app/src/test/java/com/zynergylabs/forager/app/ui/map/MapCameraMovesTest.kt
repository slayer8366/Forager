package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.location.modes.CameraMode

/**
 * RECORD -1525: a search made on the Maps tab is framed by the map on screen, which the sightings spinner then replaces
 * before its camera settles. The map that comes back restores [MapCameraMemory.saved]; with the frame already marked applied,
 * it stayed on the camera from before the search (the S22, 2026-10-21, on every build back to #206's head). The frame now
 * writes its own camera into the memory ([searchFrameSnapshot], called where `MapDataRefresh` applies the frame), so the
 * restore lands on the searched place. MapLibre cannot run here: the camera drawn is the S22 step's.
 */
class MapCameraMovesTest {

    private val oregonCity = MapCameraSnapshot(
        target = LatLng(45.3263, -122.6339),
        zoom = 12.0,
        bearing = 0.0,
        tilt = 0.0,
        following = true,
        appliedTarget = null,
    )
    private val seattle = Region(lat = 47.66, lng = -122.415, radiusKm = 8)

    @Test
    fun `a search frame applied by a map the spinner replaces is the camera the next map restores, once`() {
        val memory = MapCameraMemory().apply { saved = oregonCity }
        val target = seattle to null

        // What MapDataRefresh does where it applies frame 1.
        memory.appliedSearchFrameId = 1
        memory.saved = searchFrameSnapshot(seattle, bearing = 0.0, tilt = 0.0, appliedTarget = target)

        // The map that comes back, a new MapView (no camera mode of its own yet).
        val restore = cameraRestoreFor(memory.saved, previousCameraMode = null)!!
        assertEquals("restored on the searched place", LatLng(47.66, -122.415), restore.target)
        assertEquals("at the search's zoom", zoomForRadiusKm(8), restore.zoom, 0.0)
        assertEquals("not following the GPS fix", CameraMode.NONE, restore.cameraMode)
        assertEquals("the search region taken as applied, so the region move does not run again", target, restore.appliedTarget)
        assertEquals(
            "and the frame is not applied a second time",
            SearchFrameMove.NONE,
            searchFrameMove(isGpsTracking = false, requestId = 1, lastAppliedId = memory.appliedSearchFrameId),
        )
    }
}
