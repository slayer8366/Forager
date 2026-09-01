package com.forager.app.domain

import com.forager.app.domain.model.DerivedTrip
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GetTripReportOfflineRegionsUseCase] over a fake [OfflineMapRepository] — [isCoordinateWithinRegionTiles]
 * itself already has direct coverage in [OfflineTileMembershipTest] (inside/outside/antimeridian/
 * nesting, on the raw function); this instead covers the use case's own added logic: reading each
 * region's own [OfflineRegionSummary.maxZoom] rather than a shared constant (owner decision #2 —
 * "accuracy is relied on where memory fades"), and gathering candidate coordinates from every one of
 * [DerivedTrip.finds]/[DerivedTrip.tracks]/[DerivedTrip.waypoints], not just one of them.
 *
 * Per the Stage 2b follow-up dispatch's own point 4: pure logic gets unit tests, not Compose UI
 * tests — [CartographyScreen]/[CartographyEntryEditScreen] (the only callers of this use case) stay
 * covered at the `ViewModel` level ([CartographyViewModelTest]) rather than gaining a Compose test of
 * their own here.
 */
class GetTripReportOfflineRegionsUseCaseTest {

    private val day = LocalDate.of(2026, 8, 1)

    @Test
    fun `a region whose tiles cover one of the trip's coordinates is included`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        // Roughly 3km east of center, well inside a 10km-radius region's footprint — the same
        // "comfortably inside" point OfflineTileMembershipTest uses for the raw function.
        val trip = tripWithFinds(LatLng(45.5, -122.56))
        val region = regionSummary(id = 1L, region = portland, maxZoom = 15.0)

        val result = GetTripReportOfflineRegionsUseCase(FakeOfflineMapRepository(listOf(region)))(trip).getOrThrow()

        assertEquals(listOf(region), result)
    }

    @Test
    fun `a region whose tiles cover none of the trip's coordinates is excluded`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        // Seattle, ~230km north — nowhere near a 10km-radius region centred on Portland.
        val trip = tripWithFinds(LatLng(47.6, -122.3))
        val region = regionSummary(id = 1L, region = portland, maxZoom = 15.0)

        val result = GetTripReportOfflineRegionsUseCase(FakeOfflineMapRepository(listOf(region)))(trip).getOrThrow()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a coordinate just outside a tiny region's footprint is correctly excluded, not trivially true`() = runTest {
        val tinyRegion = Region(lat = 0.0, lng = 0.0, radiusKm = 1)
        // ~1.1 degrees of longitude at the equator is well over 100km — far outside a 1km-radius
        // region, but close enough in absolute terms that a coarse bug (e.g. always including
        // anything on the same continent) wouldn't happen to pass this by accident.
        val trip = tripWithFinds(LatLng(0.0, 1.1))
        val region = regionSummary(id = 1L, region = tinyRegion, maxZoom = 15.0)

        val result = GetTripReportOfflineRegionsUseCase(FakeOfflineMapRepository(listOf(region)))(trip).getOrThrow()

        assertTrue(result.isEmpty())
    }

    /**
     * Two otherwise-identical regions, differing only in [OfflineRegionSummary.maxZoom] — proves the
     * use case reads each region's *own* maxZoom (owner decision #2), not one shared constant: if it
     * used a single zoom for every region, both would come back the same way, one way or the other.
     * A coarser zoom's tiles cover far more ground than a finer one's (the same nesting property
     * [OfflineTileMembershipTest] proves on the raw function) — zoom 2's tiles are large enough to
     * cover almost the entire globe in one tile, zoom 15's are metres-to-low-kilometres across, so a
     * point ~50km from both regions' shared center is unambiguously covered by the coarse region and
     * unambiguously not covered by the fine one, regardless of exact tile-boundary arithmetic.
     */
    @Test
    fun `each region is tested at its own maxZoom, and a coarser maxZoom is at least as inclusive`() = runTest {
        val sharedCenter = Region(lat = 0.0, lng = 0.0, radiusKm = 1)
        // Roughly 50km from the shared center — far outside the 1km-radius region itself, but that's
        // irrelevant here: it's zoom, not radius, doing the work in this test.
        val trip = tripWithFinds(LatLng(0.0, 0.45))
        val fineRegion = regionSummary(id = 1L, region = sharedCenter, maxZoom = 15.0)
        val coarseRegion = regionSummary(id = 2L, region = sharedCenter, maxZoom = 2.0)

        val result = GetTripReportOfflineRegionsUseCase(FakeOfflineMapRepository(listOf(fineRegion, coarseRegion)))(trip).getOrThrow()

        assertEquals(listOf(coarseRegion), result)
    }

    @Test
    fun `candidate coordinates are gathered from finds, tracks, and waypoints alike`() = runTest {
        val region = regionSummary(id = 1L, region = Region(lat = 10.0, lng = 10.0, radiusKm = 5), maxZoom = 15.0)
        val fakeRepository = FakeOfflineMapRepository(listOf(region))

        val fromFind = GetTripReportOfflineRegionsUseCase(fakeRepository)(tripWithFinds(LatLng(10.0, 10.0))).getOrThrow()
        val fromTrack = GetTripReportOfflineRegionsUseCase(fakeRepository)(tripWithTrackPoint(LatLng(10.0, 10.0))).getOrThrow()
        val fromWaypoint = GetTripReportOfflineRegionsUseCase(fakeRepository)(tripWithWaypoint(LatLng(10.0, 10.0))).getOrThrow()

        assertEquals("a find's own location must be checked", listOf(region), fromFind)
        assertEquals("every recorded track point must be checked", listOf(region), fromTrack)
        assertEquals("a waypoint's own location must be checked", listOf(region), fromWaypoint)
    }

    private fun tripWithFinds(vararg locations: LatLng): DerivedTrip = DerivedTrip(
        date = day,
        finds = locations.mapIndexed { index, location ->
            MushroomLogEntry.draft(id = "find-$index", location = location, date = day)
        },
        tracks = emptyList(),
        waypoints = emptyList(),
        offlineRegions = emptyList(),
    )

    private fun tripWithTrackPoint(location: LatLng): DerivedTrip = DerivedTrip(
        date = day,
        finds = emptyList(),
        tracks = listOf(
            Track(
                id = "track-1",
                name = "Trip track",
                startedAtEpochMillis = 0L,
                endedAtEpochMillis = 1L,
                points = listOf(TrackPoint(lat = location.lat, lng = location.lng, altitude = null, accuracyMeters = null, timestampEpochMillis = 0L)),
            ),
        ),
        waypoints = emptyList(),
        offlineRegions = emptyList(),
    )

    private fun tripWithWaypoint(location: LatLng): DerivedTrip = DerivedTrip(
        date = day,
        finds = emptyList(),
        tracks = emptyList(),
        waypoints = listOf(
            Waypoint(id = "waypoint-1", lat = location.lat, lng = location.lng, altitude = null, name = "Marker", note = "", createdAtEpochMillis = 0L),
        ),
        offlineRegions = emptyList(),
    )

    private fun regionSummary(id: Long, region: Region, maxZoom: Double): OfflineRegionSummary = OfflineRegionSummary(
        id = id,
        name = "Region $id",
        region = region,
        minZoom = OfflineMapRepository.MIN_ZOOM,
        maxZoom = maxZoom,
        tileCount = 0,
        sizeBytes = 0L,
        createdAtEpochMillis = 0L,
    )

    private class FakeOfflineMapRepository(private val regions: List<OfflineRegionSummary>) : OfflineMapRepository {
        override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> =
            error("not used by this test")

        override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used by this test")

        override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions)
    }
}
