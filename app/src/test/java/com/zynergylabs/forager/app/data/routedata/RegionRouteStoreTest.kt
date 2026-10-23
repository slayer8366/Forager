package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [RegionRouteStore]: what a listing reads from a region's route folder, deleting a region's routes, and the clean-up
 * of folders no region owns (dispatch 2026-10-23-03, item 2).
 */
class RegionRouteStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val logs = mutableListOf<String>()
    private val root by lazy { File(temp.root, "offline_routes") }
    private val store by lazy { RegionRouteStore(root, { message, _ -> logs += message }) }

    private fun storeRoutes(regionId: Long, files: Map<String, ByteArray>) {
        val partial = store.beginFetch(regionId)
        files.forEach { (name, bytes) -> File(partial, name).writeBytes(bytes) }
        store.commit(
            regionId,
            partial,
            RegionRouteRecord.Stored("run-1", "t", files.map { (name, bytes) -> RecordedFile(name, bytes.size.toLong(), FakeRouteServer.sha256(bytes)) }),
        )
        store.endFetch(regionId)
    }

    @Test
    fun `a region with no folder reads as not fetched`() {
        assertEquals(RegionRouteData.NotFetched, store.status(1))
    }

    @Test
    fun `deleting a region's routes removes its folder, and it then reads as not fetched`() {
        storeRoutes(1, mapOf("W125_N45.rd5" to ByteArray(10)))

        store.delete(1)

        assertEquals(RegionRouteData.NotFetched, store.status(1))
        assertEquals(emptyList<String>(), root.list()!!.toList())
    }

    @Test
    fun `a stored file that is gone reads as damaged, never as stored`() {
        storeRoutes(1, mapOf("W125_N45.rd5" to ByteArray(10), "W120_N45.rd5" to ByteArray(20)))
        File(root, "1/W120_N45.rd5").delete()

        assertEquals(RegionRouteData.Damaged("W120_N45.rd5 is gone"), store.status(1))
        assertEquals(1, logs.count { "region 1 is damaged" in it })
    }

    @Test
    fun `a stored file of another size reads as damaged`() {
        storeRoutes(1, mapOf("W125_N45.rd5" to ByteArray(10)))
        File(root, "1/W125_N45.rd5").writeBytes(ByteArray(7))

        assertEquals(RegionRouteData.Damaged("W125_N45.rd5 is 7 bytes, recorded 10"), store.status(1))
    }

    @Test
    fun `an unreadable record reads as damaged`() {
        storeRoutes(1, mapOf("W125_N45.rd5" to ByteArray(10)))
        File(root, "1/$RECORD_FILE_NAME").writeText("state=sideways\n")

        assertEquals(RegionRouteData.Damaged("its record is unreadable (unknown state 'sideways')"), store.status(1))
    }

    @Test
    fun `a missing record reads back with its reason`() {
        val partial = store.beginFetch(3)
        store.commit(3, partial, RegionRouteRecord.Missing(RouteDataMissingReason.RATE_LIMITED))
        store.endFetch(3)

        assertEquals(RegionRouteData.Missing(RouteDataMissingReason.RATE_LIMITED), store.status(3))
    }

    @Test
    fun `the clean-up removes folders no kept region owns, unfinished fetches and unfinished deletes, and keeps the rest`() {
        storeRoutes(1, mapOf("W125_N45.rd5" to ByteArray(10)))
        storeRoutes(2, mapOf("W125_N45.rd5" to ByteArray(10)))
        File(root, "7.partial").mkdirs() // a killed process's fetch
        File(root, "8.deleting-123").mkdirs() // a delete that did not finish
        val running = store.beginFetch(9) // a fetch running now, whose region has no row yet
        File(root, "notes").mkdirs() // a name this store never writes

        store.cleanOrphans(keepIds = setOf(1L))

        assertEquals(setOf("1", "9.partial", "notes"), root.list()!!.toSet())
        assertTrue(running.isDirectory)
        assertEquals(1, logs.count { "Removed route folder 2: no region 2" in it })
        assertEquals(1, logs.count { "Removed route folder 7.partial: a fetch that never finished" in it })
        assertEquals(1, logs.count { "Removed route folder 8.deleting-123: an unfinished delete" in it })
        assertEquals(1, logs.count { "'notes'" in it && "left alone" in it })
    }

    @Test
    fun `negative region ids, which restored backups use, are folders like any other`() {
        storeRoutes(-3, mapOf("W125_N45.rd5" to ByteArray(10)))

        store.cleanOrphans(keepIds = setOf(-3L))

        assertEquals(setOf("-3"), root.list()!!.toSet())
        assertTrue(store.status(-3) is RegionRouteData.Stored)
    }
}
