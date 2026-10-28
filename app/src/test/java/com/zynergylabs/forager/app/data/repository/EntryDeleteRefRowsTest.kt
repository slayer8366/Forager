package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-28-08, two checks on the rows under the live counts, read from the tables themselves.
 *
 * **The look report's guard (b).** An entry's delete takes its waypoint ref rows with it
 * (`CartographyEntryDao.deleteEntryAndRefs`). It asserts on the ref table, not on a count: every count joins
 * `cartography_entries`, so a ref row that outlived its entry would still count 0, and a check on the count could not
 * fail (docs/audits/2026-10-28-records-follow-ups-look.md, "Assert on the ref table, not the count"). This passed before
 * the fix and passes after it. It guards that one line, and proves nothing about the stale count.
 *
 * **What the live counts count.** [RoomEntryReferenceCountSource] counts committed entries only, and kept references
 * only where the table has `kept`, the same as the one-shot queries beside it. A draft's references are not counted
 * (the draft-lifecycle dispatch's decision 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntryDeleteRefRowsTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomCartographyEntryRepository
    private lateinit var source: RoomEntryReferenceCountSource

    private fun entry(id: String, isDraft: Boolean = false) =
        CartographyEntry.draft(id = id, date = LocalDate.of(2026, 9, 20), updatedAtEpochMillis = 1_000L).copy(isDraft = isDraft)
    private fun waypointKept(id: String, kept: Boolean = true) = WaypointDecision(waypointId = id, name = id, lat = 45.0, lng = -122.0, kept = kept)
    private fun trackKept(id: String, kept: Boolean = true) = TrackDecision(trackId = id, name = id, distanceMeters = 0.0, durationMillis = 0L, pointCount = 0, kept = kept)
    private fun regionKept(id: Long, kept: Boolean = true) = OfflineRegionDecision(offlineRegionId = id, name = "r$id", lat = 45.0, lng = -122.0, radiusKm = 5, kept = kept)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCartographyEntryRepository(database.cartographyEntryDao())
        source = RoomEntryReferenceCountSource(database.cartographyEntryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleting an entry deletes its waypoint ref rows, read from the ref table`() = runBlocking {
        repository.save(entry("entry-1").copy(waypointDecisions = listOf(waypointKept("wp-1")))).getOrThrow()
        assertEquals("positive control: the entry's waypoint ref row is stored", listOf("wp-1"), database.cartographyEntryDao().getWaypointRefs("entry-1").map { it.waypointId })

        repository.delete("entry-1").getOrThrow()

        assertEquals("the deleted entry's waypoint ref row outlived it", emptyList<String>(), database.cartographyEntryDao().getWaypointRefs("entry-1").map { it.waypointId })
    }

    @Test
    fun `the live counts count committed entries and kept references only, one count per id`() = runBlocking {
        repository.save(entry("a").copy(waypointDecisions = listOf(waypointKept("wp-1")), trackDecisions = listOf(trackKept("t-1")), offlineRegionDecisions = listOf(regionKept(7L)), photos = listOf(PhotoAttachment("p-1", 1L)))).getOrThrow()
        repository.save(entry("b").copy(waypointDecisions = listOf(waypointKept("wp-1"), waypointKept("wp-2", kept = false)), trackDecisions = listOf(trackKept("t-1", kept = false)), photos = listOf(PhotoAttachment("p-1", 1L)))).getOrThrow()
        repository.save(entry("draft", isDraft = true).copy(waypointDecisions = listOf(waypointKept("wp-1"), waypointKept("wp-3")), trackDecisions = listOf(trackKept("t-2")), offlineRegionDecisions = listOf(regionKept(8L)), photos = listOf(PhotoAttachment("p-2", 1L)))).getOrThrow()

        assertEquals("waypoints: two committed entries keep wp-1; wp-2 is left out, wp-3 only in a draft", mapOf("wp-1" to 2), source.waypointCounts().first())
        assertEquals("tracks: one committed entry keeps t-1; t-2 only in a draft", mapOf("t-1" to 1), source.trackCounts().first())
        assertEquals("offline regions: one committed entry keeps region 7; region 8 only in a draft", mapOf(7L to 1), source.offlineRegionCounts().first())
        assertEquals("photos: two committed entries keep p-1; p-2 only in a draft", mapOf("p-1" to 2), source.photoCounts().first())
    }
}
