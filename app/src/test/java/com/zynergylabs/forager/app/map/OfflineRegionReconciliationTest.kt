package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.data.local.OfflineRegionDao
import com.zynergylabs.forager.app.data.local.OfflineRegionEntity
import com.zynergylabs.forager.app.domain.model.Region
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [reconcileOfflineRegions] and [regionListOrFailure], the decisions [MapLibreOfflineMapRepository.listRegions]
 * makes about `OfflineManager`'s read (dispatch 2026-09-28-106, P1 to P3). Driven with a fake DAO
 * and plain [LiveRegion]s because `OfflineManager` cannot be built off a device. What no test here
 * reaches: the mapping from a real `OfflineRegion` to a [LiveRegion], and `OfflineManager` itself.
 */
class OfflineRegionReconciliationTest {

    private class FakeDao(rows: List<OfflineRegionEntity>) : OfflineRegionDao {
        val rows = rows.associateBy { it.id }.toMutableMap()
        override suspend fun getAll() = rows.values.toList()
        override suspend fun getById(id: Long) = rows[id]
        override suspend fun getForDay(dayStartInclusive: Long, dayEndExclusive: Long) = emptyList<OfflineRegionEntity>()
        override suspend fun upsert(region: OfflineRegionEntity) { rows[region.id] = region }
        override suspend fun deleteById(id: Long) { rows.remove(id) }
    }

    private class Run(val shown: List<Long>, val dao: FakeDao, val tilesDeleted: List<Long>, val warnings: List<String>)

    private fun run(
        rows: List<OfflineRegionEntity>,
        live: List<LiveRegion>,
        inFlight: Set<Long> = emptySet(),
    ): Run {
        val dao = FakeDao(rows)
        val tilesDeleted = mutableListOf<Long>()
        val warnings = mutableListOf<String>()
        val shown = runBlocking {
            reconcileOfflineRegions(live, dao, inFlight, { tilesDeleted += it }, { warnings += it })
        }
        return Run(shown.map { it.id }, dao, tilesDeleted, warnings)
    }

    @Test
    fun `an empty read deletes no Room row`() {
        val result = run(rows = listOf(row(1), row(2)), live = emptyList())

        assertEquals("both rows survive an empty read", setOf(1L, 2L), result.dao.rows.keys)
        assertEquals(emptyList<Long>(), result.shown)
    }

    @Test
    fun `a partial read keeps the Room row it lacks and shows only what it has`() {
        val result = run(rows = listOf(row(1), row(2)), live = listOf(complete(1)))

        assertEquals("row 2 survives a read that lacks it", setOf(1L, 2L), result.dao.rows.keys)
        assertEquals(listOf(1L), result.shown)
    }

    @Test
    fun `every Room row missing from the read is logged with its id, each time it is seen`() {
        val first = run(rows = listOf(row(7), row(9)), live = emptyList())
        val second = run(rows = listOf(row(7), row(9)), live = emptyList())

        for (result in listOf(first, second)) {
            assertEquals(1, result.warnings.count { "7" in it && "no MapLibre region" in it })
            assertEquals(1, result.warnings.count { "9" in it && "no MapLibre region" in it })
        }
    }

    @Test
    fun `a complete region is never deleted, whatever else the read holds`() {
        val result = run(
            rows = listOf(row(1), row(2)),
            live = listOf(complete(1), incomplete(2, metadata = neverFinishedMetadata())),
        )

        assertEquals(listOf(1L), result.shown)
        assertTrue(1L !in result.tilesDeleted)
        assertTrue(1L in result.dao.rows.keys)
    }

    @Test
    fun `an incomplete region that provably never finished is deleted, tiles and row, and the delete is logged with its id`() {
        val result = run(rows = emptyList(), live = listOf(incomplete(5, metadata = neverFinishedMetadata())))

        assertEquals(listOf(5L), result.tilesDeleted)
        assertEquals(emptyList<Long>(), result.shown)
        assertEquals(1, result.warnings.count { "5" in it && "never finished" in it })
    }

    @Test
    fun `an incomplete region that has a Room row is not deleted`() {
        val result = run(rows = listOf(row(3)), live = listOf(incomplete(3, metadata = neverFinishedMetadata())))

        assertEquals(emptyList<Long>(), result.tilesDeleted)
        assertEquals(setOf(3L), result.dao.rows.keys)
        assertEquals(emptyList<Long>(), result.shown)
        assertEquals(1, result.warnings.count { "3" in it && "kept" in it })
    }

    @Test
    fun `an incomplete region whose metadata records a completion time is not deleted`() {
        val result = run(rows = emptyList(), live = listOf(incomplete(4, metadata = finishedMetadata())))

        assertEquals(emptyList<Long>(), result.tilesDeleted)
        assertEquals(1, result.warnings.count { "4" in it && "kept" in it })
    }

    @Test
    fun `an incomplete region with unreadable or missing metadata is not deleted`() {
        val result = run(
            rows = emptyList(),
            live = listOf(incomplete(6, metadata = "junk".toByteArray()), incomplete(8, metadata = null)),
        )

        assertEquals(emptyList<Long>(), result.tilesDeleted)
        assertEquals(1, result.warnings.count { "6" in it && "kept" in it })
        assertEquals(1, result.warnings.count { "8" in it && "kept" in it })
    }

    @Test
    fun `a download still running in this process is not deleted`() {
        val result = run(
            rows = emptyList(),
            live = listOf(incomplete(10, metadata = neverFinishedMetadata())),
            inFlight = setOf(10L),
        )

        assertEquals(emptyList<Long>(), result.tilesDeleted)
        assertEquals(emptyList<Long>(), result.shown)
    }

    @Test
    fun `a complete region with no Room row is rebuilt from its metadata`() {
        val result = run(rows = emptyList(), live = listOf(complete(11, metadata = finishedMetadata())))

        assertEquals(listOf(11L), result.shown)
        assertEquals("Ridge", result.dao.rows.getValue(11L).name)
    }

    @Test
    fun `a null list from MapLibre is a failed read, not an empty one`() {
        assertThrows(IOException::class.java) { regionListOrFailure<Long>(null) }
        assertEquals(emptyList<Long>(), regionListOrFailure(emptyList<Long>()))
        assertEquals(listOf(1L), regionListOrFailure(listOf(1L)))
    }

    private fun row(id: Long) = OfflineRegionEntity(id, "Row $id", 1.0, 2.0, 5, 6.0, 12.0, 1_000L)

    private fun complete(id: Long, metadata: ByteArray? = finishedMetadata()) = LiveRegion(id, true, metadata, 10, 100)

    private fun incomplete(id: Long, metadata: ByteArray?) = LiveRegion(id, false, metadata, 1, 10)

    private fun neverFinishedMetadata() = RegionMetadata("Ridge", Region(1.0, 2.0, 5), 6.0, 12.0, downloadedAtEpochMillis = 0L).toBytes()

    private fun finishedMetadata() = RegionMetadata("Ridge", Region(1.0, 2.0, 5), 6.0, 12.0, downloadedAtEpochMillis = 1_000L).toBytes()

    // ---- restored regions (dispatch 2026-09-28-137, item 1) ----

    @Test
    fun `a Room row with no MapLibre region is offered as not downloaded, with its stored centre, radius and zoom`() {
        val rows = listOf(row(1), row(2).copy(name = "Cedar Creek", lat = 45.5, lng = -122.6, radiusKm = 8, minZoom = 10.0, maxZoom = 15.0))

        val listed = notDownloadedRegions(rows, liveIds = setOf(1L))

        assertEquals(listOf(2L), listed.map { it.id })
        val region = listed.single()
        assertEquals(false, region.isDownloaded)
        assertEquals("Cedar Creek", region.name)
        assertEquals(com.zynergylabs.forager.app.domain.model.Region(lat = 45.5, lng = -122.6, radiusKm = 8), region.region)
        assertEquals(10.0, region.minZoom, 0.0)
        assertEquals(15.0, region.maxZoom, 0.0)
        assertEquals(0, region.tileCount)
        assertEquals(0L, region.sizeBytes)
    }

    @Test
    fun `a Room row MapLibre does have is not offered as not downloaded`() {
        assertEquals(emptyList<Long>(), notDownloadedRegions(listOf(row(1), row(2)), liveIds = setOf(1L, 2L)).map { it.id })
    }

    @Test
    fun `an empty read offers every row, and listing them deletes nothing`() {
        val dao = FakeDao(listOf(row(1), row(2)))

        val listed = notDownloadedRegions(runBlocking { dao.getAll() }, liveIds = emptySet())

        assertEquals(setOf(1L, 2L), listed.map { it.id }.toSet())
        assertEquals("both rows are still in the store", setOf(1L, 2L), dao.rows.keys)
    }
}
