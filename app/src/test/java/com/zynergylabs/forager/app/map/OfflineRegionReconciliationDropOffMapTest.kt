package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.data.local.OfflineRegionDao
import com.zynergylabs.forager.app.data.local.OfflineRegionEntity
import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drop-off map copy's half of [reconcileOfflineRegions] (dispatch 2026-11-04-09): an area and its copy are one unit,
 * as an area and its routes are. Its shown size counts the copy (A5, the owner: "Include it (Recommended)"), it carries
 * the copy's state, an area deleted here loses its copy, and copies no area owns are cleared with the same set kept as
 * the routes'.
 */
class OfflineRegionReconciliationDropOffMapTest {

    private class FakeDao(rows: List<OfflineRegionEntity>) : OfflineRegionDao {
        val rows = rows.associateBy { it.id }.toMutableMap()
        override suspend fun getAll() = rows.values.toList()
        override suspend fun getById(id: Long) = rows[id]
        override suspend fun getForDay(dayStartInclusive: Long, dayEndExclusive: Long) = emptyList<OfflineRegionEntity>()
        override suspend fun upsert(region: OfflineRegionEntity) { rows[region.id] = region }
        override suspend fun deleteById(id: Long) { rows.remove(id) }
    }

    private val routesOf1 = RegionRouteData.Stored("usca-20261021T111429Z", "2026-10-21T00:00:00Z", 2_500L, File("/routes/1"))
    private val copyOf1 = RegionDropOffMap(tiles = 598, emptyTiles = 2, sizeBytes = 40_000L, whole = true)

    @Test
    fun `an area's shown size is its tiles, its routes and its drop-off map copy, and it carries the copy's state`() {
        val copies = FakeRegionDropOffMaps(mutableMapOf(1L to copyOf1))
        val shown = reconcile(
            rows = listOf(row(1), row(2)),
            live = listOf(complete(1, bytes = 100), complete(2, bytes = 300)),
            routes = FakeRegionRouteFolders(mutableMapOf(1L to routesOf1)),
            copies = copies,
        )

        assertEquals(mapOf(1L to 100L + 2_500L + 40_000L, 2L to 300L), shown.associate { it.id to it.sizeBytes })
        assertEquals(mapOf(1L to copyOf1, 2L to RegionDropOffMap.NONE), shown.associate { it.id to it.dropOffMap })
    }

    @Test
    fun `an area deleted as never finished loses its copy too`() {
        val copies = FakeRegionDropOffMaps(mutableMapOf(5L to copyOf1))
        reconcile(rows = emptyList(), live = listOf(LiveRegion(5, false, neverFinished(), 1, 10)), copies = copies)

        assertEquals(listOf(5L), copies.deleted)
    }

    @Test
    fun `a copy's delete that fails is logged, and the listing still answers`() {
        val warnings = mutableListOf<String>()
        val copies = FakeRegionDropOffMaps(deleteFails = true)
        val shown = reconcile(
            rows = listOf(row(1)),
            live = listOf(complete(1, bytes = 100), LiveRegion(5, false, neverFinished(), 1, 10)),
            copies = copies,
            warnings = warnings,
        )

        assertEquals(listOf(1L), shown.map { it.id })
        assertEquals(warnings.toString(), 1, warnings.count { "Couldn't delete region 5's drop-off map copy" in it })
    }

    @Test
    fun `orphan copies are cleared keeping every Room row, every area in the read and every running download`() {
        val copies = FakeRegionDropOffMaps()
        val routes = FakeRegionRouteFolders()
        reconcile(
            rows = listOf(row(1), row(7)),
            live = listOf(complete(1, bytes = 100), complete(9, bytes = 100), LiveRegion(11, false, neverFinished(), 1, 10), LiveRegion(5, false, neverFinished(), 1, 10)),
            inFlight = setOf(11L),
            routes = routes,
            copies = copies,
        )

        assertEquals(listOf(setOf(1L, 7L, 9L, 11L)), copies.keptByCleanup)
        assertEquals("the same set the routes keep", routes.keptByCleanup, copies.keptByCleanup)
    }

    private fun reconcile(
        rows: List<OfflineRegionEntity>,
        live: List<LiveRegion>,
        copies: FakeRegionDropOffMaps,
        routes: FakeRegionRouteFolders = FakeRegionRouteFolders(),
        inFlight: Set<Long> = emptySet(),
        warnings: MutableList<String> = mutableListOf(),
    ) = runBlocking {
        reconcileOfflineRegions(live, FakeDao(rows), inFlight, {}, { warnings += it }, routes, copies)
    }

    private fun row(id: Long) = OfflineRegionEntity(id, "Row $id", 1.0, 2.0, 5, 6.0, 12.0, 1_000L)

    private fun complete(id: Long, bytes: Long) =
        LiveRegion(id, true, RegionMetadata("Ridge", Region(1.0, 2.0, 5), 6.0, 12.0, downloadedAtEpochMillis = 1_000L).toBytes(), 10, bytes)

    private fun neverFinished() = RegionMetadata("Ridge", Region(1.0, 2.0, 5), 6.0, 12.0, downloadedAtEpochMillis = 0L).toBytes()
}
