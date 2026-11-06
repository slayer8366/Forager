package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.data.local.OfflineRegionDao
import com.zynergylabs.forager.app.data.local.OfflineRegionEntity
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The route half of [reconcileOfflineRegions] (dispatch 2026-10-23-03, item 2): a region and its routes are one unit.
 * Its size counts its routes, it carries their state, a region deleted here loses them, and route folders no region
 * owns are cleared with the right set kept.
 */
class OfflineRegionReconciliationRoutesTest {

    private class FakeDao(rows: List<OfflineRegionEntity>) : OfflineRegionDao {
        val rows = rows.associateBy { it.id }.toMutableMap()
        override suspend fun getAll() = rows.values.toList()
        override suspend fun getById(id: Long) = rows[id]
        override suspend fun getForDay(dayStartInclusive: Long, dayEndExclusive: Long) = emptyList<OfflineRegionEntity>()
        override suspend fun upsert(region: OfflineRegionEntity) { rows[region.id] = region }
        override suspend fun deleteById(id: Long) { rows.remove(id) }
    }

    private val stored = RegionRouteData.Stored("usca-20261021T111429Z", "2026-10-21T00:00:00Z", 2_500L, File("/routes/1"))

    @Test
    fun `a region's shown size is its tiles plus its stored routes, and it carries their state`() {
        val routes = FakeRegionRouteFolders(mutableMapOf(1L to stored, 2L to RegionRouteData.NoneHere))
        val shown = reconcile(rows = listOf(row(1), row(2)), live = listOf(complete(1, bytes = 100), complete(2, bytes = 300)), routes = routes)

        assertEquals(mapOf(1L to 2_600L, 2L to 300L), shown.associate { it.id to it.sizeBytes })
        assertEquals(mapOf(1L to stored, 2L to RegionRouteData.NoneHere), shown.associate { it.id to it.routeData })
    }

    @Test
    fun `a region with missing routes lists as missing with the reason, and its size is its tiles`() {
        val missing = RegionRouteData.Missing(RouteDataMissingReason.NETWORK)
        val shown = reconcile(rows = listOf(row(1)), live = listOf(complete(1, bytes = 100)), routes = FakeRegionRouteFolders(mutableMapOf(1L to missing)))

        assertEquals(missing, shown.single().routeData)
        assertEquals(100L, shown.single().sizeBytes)
    }

    @Test
    fun `a region downloaded before routes existed lists as not fetched`() {
        val shown = reconcile(rows = listOf(row(1)), live = listOf(complete(1, bytes = 100)), routes = FakeRegionRouteFolders())

        assertEquals(RegionRouteData.NotFetched, shown.single().routeData)
    }

    @Test
    fun `a region deleted as never finished loses its routes too`() {
        val routes = FakeRegionRouteFolders(mutableMapOf(5L to stored))
        reconcile(rows = emptyList(), live = listOf(LiveRegion(5, false, neverFinished(), 1, 10)), routes = routes)

        assertEquals(listOf(5L), routes.deleted)
    }

    @Test
    fun `a route delete that fails after a never-finished region is deleted is logged, and the listing still answers`() {
        val warnings = mutableListOf<String>()
        val routes = FakeRegionRouteFolders(deleteFails = true)
        val shown = reconcile(rows = listOf(row(1)), live = listOf(complete(1, bytes = 100), LiveRegion(5, false, neverFinished(), 1, 10)), routes = routes, warnings = warnings)

        assertEquals(listOf(1L), shown.map { it.id })
        assertEquals(1, warnings.count { "Couldn't delete region 5's routes" in it })
    }

    @Test
    fun `orphan route folders are cleared keeping every Room row, every region in the read and every running download`() {
        val routes = FakeRegionRouteFolders()
        reconcile(
            // Row 7 has no MapLibre region in this read: kept, so its routes are kept too. 9 is in the read with no
            // row and is rebuilt from metadata. 11 is downloading now.
            rows = listOf(row(1), row(7)),
            live = listOf(complete(1, bytes = 100), complete(9, bytes = 100), LiveRegion(11, false, neverFinished(), 1, 10)),
            inFlight = setOf(11L),
            routes = routes,
        )

        assertEquals(listOf(setOf(1L, 7L, 9L, 11L)), routes.keptByCleanup)
    }

    @Test
    fun `a region deleted as never finished is not kept by the clean-up, so a route delete that failed is retried there`() {
        val routes = FakeRegionRouteFolders(deleteFails = true)
        reconcile(rows = listOf(row(1)), live = listOf(complete(1, bytes = 100), LiveRegion(5, false, neverFinished(), 1, 10)), routes = routes)

        assertEquals(listOf(setOf(1L)), routes.keptByCleanup)
    }

    private fun reconcile(
        rows: List<OfflineRegionEntity>,
        live: List<LiveRegion>,
        routes: FakeRegionRouteFolders,
        inFlight: Set<Long> = emptySet(),
        warnings: MutableList<String> = mutableListOf(),
    ) = runBlocking {
        reconcileOfflineRegions(live, FakeDao(rows), inFlight, {}, { warnings += it }, routes)
    }

    private fun row(id: Long) = OfflineRegionEntity(id, "Row $id", 1.0, 2.0, 5, 6.0, 12.0, 1_000L)

    private fun complete(id: Long, bytes: Long) =
        LiveRegion(id, true, RegionMetadata("Ridge", Region(1.0, 2.0, 5), 6.0, 12.0, downloadedAtEpochMillis = 1_000L).toBytes(), 10, bytes)

    private fun neverFinished() = RegionMetadata("Ridge", Region(1.0, 2.0, 5), 6.0, 12.0, downloadedAtEpochMillis = 0L).toBytes()
}
