package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-11-04-09 A6 (the owner: "Don't count it (Recommended)": "The copy is a second copy of tiles the budget
 * already counts, so it's already capped by it. The number stays as it is."): an area's drop-off map copy does not
 * change what it costs against the tile budget. 1795 is the 15 km area's estimate `OfflineTileBudgetTest` pins.
 */
class OfflineTileBudgetDropOffMapTest {

    private val ridge = Region(lat = 45.3736, lng = -121.6959, radiusKm = 15)

    private fun area(id: Long, copy: RegionDropOffMap) = OfflineRegionSummary(
        id = id,
        name = "Area $id",
        region = ridge,
        minZoom = OfflineMapRepository.MIN_ZOOM,
        maxZoom = OfflineMapRepository.MAX_ZOOM,
        tileCount = 3590,
        sizeBytes = 1L,
        createdAtEpochMillis = 0L,
        dropOffMap = copy,
    )

    @Test
    fun `an area's copy, whole or partial, leaves its cost against the tile budget at its estimate`() {
        assertEquals(1795, area(1, RegionDropOffMap(tiles = 598, emptyTiles = 2, sizeBytes = 2_000_000L, whole = true)).budgetTileCount())
        assertEquals(1795, area(2, RegionDropOffMap(tiles = 6, emptyTiles = 0, sizeBytes = 60_000L, whole = false)).budgetTileCount())
    }

    @Test
    fun `the used side of the budget is unchanged by the areas' copies`() {
        val areas = listOf(
            area(1, RegionDropOffMap(tiles = 598, emptyTiles = 2, sizeBytes = 2_000_000L, whole = true)),
            area(2, RegionDropOffMap.NONE),
        )
        assertEquals(1795L + 1795L, areas.budgetTilesUsed())
    }
}
