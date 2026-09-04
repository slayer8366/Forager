package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.OfflineRegionDecision
import com.forager.app.domain.model.Region
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [GetCartographyEntryOfflineRegionUseCase] over a fake [OfflineMapRepository] — the same shape
 * [GetTripReportOfflineRegionsUseCaseTest] already establishes for the sibling question that use
 * case answers. [isCoordinateWithinRegionTiles] itself already has direct coverage
 * ([OfflineTileMembershipTest]); this instead covers this use case's own added logic: scoping to
 * the entry's own *kept* [CartographyEntry.offlineRegionDecisions] only (never falling back to
 * every downloaded region, unlike [GetTripReportOfflineRegionsUseCase] — see this use case's own
 * doc comment for why that's a deliberate, not incidental, difference), and tolerating a kept
 * region no longer downloaded, or an entry with nothing resolvable at all, without ever throwing.
 */
class GetCartographyEntryOfflineRegionUseCaseTest {

    private val baseEntry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)

    @Test
    fun `an entry whose kept data falls inside a kept, downloaded region reports that region`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        val region = regionSummary(id = 1L, region = portland)
        val entry = baseEntry.copy(offlineRegionDecisions = listOf(keptDecision(offlineRegionId = 1L, region = portland)))
        // Roughly 3km east of centre, well inside a 10km-radius region's footprint.
        val points = listOf(LatLng(45.5, -122.56))

        val result = GetCartographyEntryOfflineRegionUseCase(FakeOfflineMapRepository(listOf(region)))(entry, points)

        assertEquals(region, result)
    }

    @Test
    fun `an entry whose kept data falls outside a kept, downloaded region's tiles reports none`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        val region = regionSummary(id = 1L, region = portland)
        val entry = baseEntry.copy(offlineRegionDecisions = listOf(keptDecision(offlineRegionId = 1L, region = portland)))
        // Seattle, ~230km north — nowhere near a 10km-radius region centred on Portland.
        val points = listOf(LatLng(47.6, -122.3))

        val result = GetCartographyEntryOfflineRegionUseCase(FakeOfflineMapRepository(listOf(region)))(entry, points)

        assertNull(result)
    }

    @Test
    fun `a kept region deleted from the device is treated as unavailable, without error`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        // Never in the repository's own listRegions() result -- the dangling-reference case.
        val entry = baseEntry.copy(offlineRegionDecisions = listOf(keptDecision(offlineRegionId = 1L, region = portland)))
        val points = listOf(LatLng(45.5, -122.56))

        val result = GetCartographyEntryOfflineRegionUseCase(FakeOfflineMapRepository(emptyList()))(entry, points)

        assertNull(result)
    }

    @Test
    fun `an entry with no resolvable coordinates at all reports none and does not throw`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        val region = regionSummary(id = 1L, region = portland)
        val entry = baseEntry.copy(offlineRegionDecisions = listOf(keptDecision(offlineRegionId = 1L, region = portland)))

        val result = GetCartographyEntryOfflineRegionUseCase(FakeOfflineMapRepository(listOf(region)))(entry, emptyList())

        assertNull(result)
    }

    @Test
    fun `an entry with no kept offline region decisions reports none, even if a covering region is downloaded`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        val region = regionSummary(id = 1L, region = portland)
        // No offlineRegionDecisions at all -- nothing kept, so nothing to check against.
        val points = listOf(LatLng(45.5, -122.56))

        val result = GetCartographyEntryOfflineRegionUseCase(FakeOfflineMapRepository(listOf(region)))(baseEntry, points)

        assertNull(result)
    }

    @Test
    fun `a withheld offline region decision is never checked, even if its region is downloaded and covers the data`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        val region = regionSummary(id = 1L, region = portland)
        val entry = baseEntry.copy(
            offlineRegionDecisions = listOf(
                OfflineRegionDecision(offlineRegionId = 1L, name = "Ridge Region", lat = portland.lat, lng = portland.lng, radiusKm = portland.radiusKm, kept = false),
            ),
        )
        val points = listOf(LatLng(45.5, -122.56))

        val result = GetCartographyEntryOfflineRegionUseCase(FakeOfflineMapRepository(listOf(region)))(entry, points)

        assertNull(result)
    }

    /**
     * The scope-defining test: a region the entry never kept, but which genuinely covers the
     * entry's data and is downloaded on the device, must still report none — proves this use case
     * never falls back to searching every downloaded region the way [GetTripReportOfflineRegionsUseCase]
     * does for its own, different question.
     */
    @Test
    fun `a covering, downloaded region the entry never kept is not reported`() = runTest {
        val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
        val region = regionSummary(id = 99L, region = portland)
        // baseEntry keeps nothing at all -- region 99 covers the data but was never kept by this entry.
        val points = listOf(LatLng(45.5, -122.56))

        val result = GetCartographyEntryOfflineRegionUseCase(FakeOfflineMapRepository(listOf(region)))(baseEntry, points)

        assertNull(result)
    }

    private fun keptDecision(offlineRegionId: Long, region: Region): OfflineRegionDecision = OfflineRegionDecision(
        offlineRegionId = offlineRegionId,
        name = "Ridge Region",
        lat = region.lat,
        lng = region.lng,
        radiusKm = region.radiusKm,
        kept = true,
    )

    private fun regionSummary(id: Long, region: Region): OfflineRegionSummary = OfflineRegionSummary(
        id = id,
        name = "Region $id",
        region = region,
        minZoom = OfflineMapRepository.MIN_ZOOM,
        maxZoom = OfflineMapRepository.MAX_ZOOM,
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
