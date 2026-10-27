package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-10-26-21 Amendment 1, T1 (the owner: "Yes, the estimate (Recommended)", RECORD -1703): an area counts
 * against the tile budget by its own estimate, recomputed from its stored centre and radius, never by MapLibre's count,
 * which on Trail Map v3 also counts the official-trails band's tiles (about one per basemap tile).
 *
 * The expected figures are literals already pinned elsewhere, not recomputed here from the code under test: 1774 for a
 * 15 km area at 45.326, -122.634 (the "~1774 tiles" the tile-estimate dispatch recorded, `OfflineMapRepository`'s
 * SERVED_MAX_ZOOM comment), and 5120 for a 24 km area at 49.60, -122.607 (`AvailabilityViewModelOfflineMapsTest`, from the
 * two-data-corrections pre-build report).
 */
class OfflineTileBudgetTest {

    private fun area(id: Long, region: Region, mapLibreCount: Int, downloaded: Boolean = true) = OfflineRegionSummary(
        id = id,
        name = "Area $id",
        region = region,
        minZoom = OfflineMapRepository.MIN_ZOOM,
        maxZoom = OfflineMapRepository.MAX_ZOOM,
        tileCount = mapLibreCount,
        sizeBytes = if (downloaded) 1L else 0L,
        createdAtEpochMillis = 0L,
        isDownloaded = downloaded,
    )

    private val ridge = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
    private val northEdge = Region(lat = 49.60, lng = -122.607, radiusKm = 24)

    @Test
    fun `a downloaded area counts by its estimate, not by MapLibre's count of both sources' tiles`() {
        // 3548 is what MapLibre would report for a v3 download of this area: the basemap's 1774 and one band tile each.
        assertEquals(1774, area(1, ridge, mapLibreCount = 3548).budgetTileCount())
        // A v2 area whose count already matched its estimate reads the same as before.
        assertEquals(1774, area(2, ridge, mapLibreCount = 1774).budgetTileCount())
    }

    @Test
    fun `an area restored from a backup and not downloaded here costs nothing`() {
        assertEquals(0, area(3, northEdge, mapLibreCount = 0, downloaded = false).budgetTileCount())
    }

    @Test
    fun `the used side is the sum of each area's estimate`() {
        val areas = listOf(
            area(1, ridge, mapLibreCount = 3548),
            area(2, northEdge, mapLibreCount = 10240),
            area(3, northEdge, mapLibreCount = 0, downloaded = false),
        )
        assertEquals(1774L + 5120L, areas.budgetTilesUsed())
    }
}
