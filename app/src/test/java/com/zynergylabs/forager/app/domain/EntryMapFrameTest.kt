package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The entry map's opening frame (owner, 2026-09-28, "Fit all kept records"; dispatch 2026-09-28-34
 * and planner message 2026-09-28-35): the bounds of the kept tracks' points, finds, located photos
 * and waypoints, with 48 dp of padding, zoomed no closer than 17; all of them at one place opens at
 * zoom 16; kept offline regions never count, since a regions-only entry has no map (plate pulse,
 * item 4); nothing to frame is `null`.
 */
class EntryMapFrameTest {

    private fun data(
        tracks: List<RecordPolyline> = emptyList(),
        finds: List<RecordPoint> = emptyList(),
        waypoints: List<RecordPoint> = emptyList(),
        photos: List<RecordPoint> = emptyList(),
        regions: List<RecordRegion> = emptyList(),
    ) = CartographyEntryMapData(tracks, finds, waypoints, photos, regions)

    private val farRegion = RecordRegion("region-1", Region(lat = 47.0, lng = -120.0, radiusKm = 20))

    @Test
    fun `tracks only fit the bounds of every track point, at 48 dp and a zoom cap of 17`() {
        val frame = entryMapFrame(
            data(
                tracks = listOf(
                    RecordPolyline("t1", listOf(LatLng(45.20, -122.50), LatLng(45.23, -122.46))),
                    RecordPolyline("t2", listOf(LatLng(45.18, -122.52), LatLng(45.21, -122.49))),
                ),
            ),
        )

        assertEquals(
            EntryMapFrame.Fit(GeoBoundingBox(north = 45.23, south = 45.18, east = -122.46, west = -122.52), paddingDp = 48, maxZoom = 17.0),
            frame,
        )
    }

    @Test
    fun `mixed kinds fit tracks, finds, waypoints and located photos together`() {
        val frame = entryMapFrame(
            data(
                tracks = listOf(RecordPolyline("t1", listOf(LatLng(45.20, -122.50), LatLng(45.21, -122.51)))),
                finds = listOf(RecordPoint("f1", LatLng(45.30, -122.55))),
                waypoints = listOf(RecordPoint("w1", LatLng(45.15, -122.40))),
                photos = listOf(RecordPoint("p1", LatLng(45.25, -122.60))),
            ),
        )

        assertEquals(EntryMapFrame.Fit(GeoBoundingBox(north = 45.30, south = 45.15, east = -122.40, west = -122.60)), frame)
    }

    @Test
    fun `a single point opens centred on it at zoom 16, and so do several at the same place`() {
        val at = LatLng(45.51, -122.61)

        assertEquals(EntryMapFrame.SinglePoint(at, zoom = 16.0), entryMapFrame(data(finds = listOf(RecordPoint("f1", at)))))
        assertEquals(
            EntryMapFrame.SinglePoint(at, zoom = 16.0),
            entryMapFrame(data(finds = listOf(RecordPoint("f1", at)), photos = listOf(RecordPoint("p1", at)), waypoints = listOf(RecordPoint("w1", at)))),
        )
    }

    /**
     * Planner message 2026-09-28-35: a regions-only entry has nothing to frame. Its positive half
     * shows the same regions with one waypoint do frame, so this cannot pass on a function that
     * frames nothing at all.
     */
    @Test
    fun `regions only frame nothing, and the same regions with a waypoint frame the waypoint`() {
        assertNull(entryMapFrame(data(regions = listOf(farRegion))))

        val waypoint = LatLng(45.4, -122.5)
        assertEquals(
            EntryMapFrame.SinglePoint(waypoint),
            entryMapFrame(data(waypoints = listOf(RecordPoint("w1", waypoint)), regions = listOf(farRegion))),
        )
    }

    @Test
    fun `regions plus a find frame the find alone, the region ignored`() {
        val find = LatLng(45.51, -122.61)

        assertEquals(EntryMapFrame.SinglePoint(find), entryMapFrame(data(finds = listOf(RecordPoint("f1", find)), regions = listOf(farRegion))))
        assertEquals(
            EntryMapFrame.Fit(GeoBoundingBox(north = 45.52, south = 45.51, east = -122.60, west = -122.61)),
            entryMapFrame(
                data(finds = listOf(RecordPoint("f1", find), RecordPoint("f2", LatLng(45.52, -122.60))), regions = listOf(farRegion)),
            ),
        )
    }

    /** Nothing to frame keeps today's behaviour, no map at all; the positive half is one located photo. */
    @Test
    fun `nothing kept frames nothing, and an empty track adds nothing, while one located photo frames it`() {
        assertNull(entryMapFrame(data()))
        assertNull(entryMapFrame(data(tracks = listOf(RecordPolyline("t1", emptyList())))))

        val photo = LatLng(45.0, -122.0)
        assertEquals(EntryMapFrame.SinglePoint(photo), entryMapFrame(data(photos = listOf(RecordPoint("p1", photo)))))
    }
}
