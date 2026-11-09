package com.zynergylabs.forager.app.domain

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository
import com.zynergylabs.forager.app.data.repository.RoomKeptTrackPathRepository
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Part 2 follow-ups F1 item 5, point 5 of continuation `2026-09-28-191`: a Journal entry that kept a track
 * follows the rule for an entry that kept a since-deleted waypoint.
 *
 * **The rule, found in code (read at `addf7d7a`):** a delete removes the record and nothing else. Neither
 * `RoomWaypointRepository.delete` (`dao.deleteById`) nor `RoomTrackRepository.delete`
 * (`dao.deleteTrackAndPoints`) touches `cartography_entry_*_refs`, and those tables have no `@ForeignKey` "by
 * explicit standing rule" (`CartographyEntryEntity.kt`, the ref entities' doc comments): the ref row survives
 * the delete with its snapshot, and the entry shows the snapshot. So a kept track stays in the entry as its
 * snapshot (name, distance, duration, point count), and its map line is not drawn, since the points went with the
 * track (`GetCartographyEntryMapDataUseCaseTest`, "a kept track deleted from Records draws nothing and does not
 * error"). The two ref tables differ in what the snapshot holds (a waypoint's carries its coordinates, so it can
 * still be drawn; a track's carries no path), but the rule, the ref row surviving untouched, is the same.
 *
 * Real Room, real repositories, the real [DeleteTrackUseCase] and [DeleteWaypointUseCase], and a reload of the
 * entry from the database after the delete: the ref rows' presence is read from the rows, not assumed.
 *
 * **Superseded in part by F3 (dispatch 2026-09-28-195, owner 2026-09-29, "Option B" and "All recommended").**
 * The paragraph above says a deleted track's map line "is not drawn, since the points went with the track".
 * That is no longer the rule: just before a track is deleted its path is copied into a row for every ref row
 * naming it (`cartography_entry_track_paths`), and the entry draws that saved line. The ref row and its
 * snapshot still survive the delete untouched. The dispatch's item 7: "F1's tests that pin 'a deleted track
 * draws no line' (TrackDeleteEntryRefsTest, and GetCartographyEntryMapDataUseCaseTest:114) now expect the
 * saved line."
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackDeleteEntryRefsTest {

    private lateinit var database: ForagerDatabase
    private lateinit var trackRepository: RoomTrackRepository
    private lateinit var waypointRepository: RoomWaypointRepository
    private lateinit var entryRepository: RoomCartographyEntryRepository
    private lateinit var mapData: GetCartographyEntryMapDataUseCase

    private val track = Track(
        id = "track-1",
        name = "Ridge Loop",
        startedAtEpochMillis = 1_000L,
        endedAtEpochMillis = 2_000L,
        points = listOf(TrackPoint(lat = 45.20, lng = -122.50, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L)),
    )
    private val waypoint = Waypoint(id = "wp-1", lat = 45.21, lng = -122.51, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = 1_500L)
    private val trackDecision = TrackDecision(trackId = "track-1", name = "Ridge Loop", distanceMeters = 1200.0, durationMillis = 600_000L, pointCount = 42, kept = true)
    private val waypointDecision = WaypointDecision(waypointId = "wp-1", name = "Creek pin", lat = 45.21, lng = -122.51, kept = true)
    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false, trackDecisions = listOf(trackDecision), waypointDecisions = listOf(waypointDecision))

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
        trackRepository = RoomTrackRepository(database.trackDao())
        waypointRepository = RoomWaypointRepository(database.waypointDao())
        entryRepository = RoomCartographyEntryRepository(database.cartographyEntryDao())
        mapData = GetCartographyEntryMapDataUseCase(trackRepository, RoomMushroomLogRepository(database.mushroomLogDao()), RoomKeptTrackPathRepository(database.cartographyEntryDao()))
        trackRepository.create(track).getOrThrow()
        trackRepository.appendPoints(track.id, track.points).getOrThrow()
        waypointRepository.save(waypoint).getOrThrow()
        entryRepository.save(entry).getOrThrow()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `before the delete the entry has the kept track drawn and both counts at one`() = runTest {
        val loaded = entryRepository.getById("entry-1").getOrThrow()!!

        assertEquals(listOf(trackDecision), loaded.trackDecisions)
        assertEquals(listOf(RecordPolyline("track-1", listOf(LatLng(45.20, -122.50)))), mapData(loaded, galleryPhotos = emptyList()).trackPolylines)
        assertEquals(1, entryRepository.countEntriesReferencingTrack("track-1").getOrThrow())
        assertEquals(1, entryRepository.countEntriesReferencingWaypoint("wp-1").getOrThrow())
    }

    // Edited by F3 (was: "... and draws no line", asserting an empty list). Dispatch 2026-09-28-195 item 7, quoted:
    // "F1's tests that pin 'a deleted track draws no line' (TrackDeleteEntryRefsTest, and
    // GetCartographyEntryMapDataUseCaseTest:114) now expect the saved line." The first two assertions are F1's,
    // unchanged; the last is the one the ruling inverts.
    @Test
    fun `after the track is deleted the entry still keeps it as its snapshot, and draws its saved line`() = runTest {
        deleteTrack("track-1")

        val loaded = entryRepository.getById("entry-1").getOrThrow()!!

        assertEquals("the ref row and its snapshot survive the delete, unchanged", listOf(trackDecision), loaded.trackDecisions)
        assertEquals("the track itself is gone", null, trackRepository.getById("track-1").getOrThrow())
        assertEquals(
            "its line is the path saved at delete time",
            listOf(RecordPolyline("track-1", listOf(LatLng(45.20, -122.50)))),
            mapData(loaded, galleryPhotos = emptyList()).trackPolylines,
        )
    }

    @Test
    fun `before the delete nothing is saved, the path is copied only when the track is deleted`() = runTest {
        assertEquals(emptyList<SavedPath>(), savedPaths())
        assertEquals("the track is drawn live, from its own points", listOf(RecordPolyline("track-1", listOf(LatLng(45.20, -122.50)))), mapData(entryRepository.getById("entry-1").getOrThrow()!!, emptyList()).trackPolylines)
    }

    @Test
    fun `every entry that has the track gets a saved path, kept or withheld, committed or draft, and an entry with another track gets none`() = runTest {
        val withheld = entry.copy(id = "entry-withheld", trackDecisions = listOf(trackDecision.copy(kept = false)))
        val draft = entry.copy(id = "entry-draft", isDraft = true)
        val other = entry.copy(id = "entry-other", trackDecisions = listOf(trackDecision.copy(trackId = "track-2")))
        listOf(withheld, draft, other).forEach { entryRepository.save(it).getOrThrow() }

        deleteTrack("track-1")

        val path = listOf(LatLng(45.20, -122.50))
        assertEquals(
            listOf(
                SavedPath("entry-1", "track-1", path),
                SavedPath("entry-draft", "track-1", path),
                SavedPath("entry-withheld", "track-1", path),
            ),
            savedPaths(),
        )
    }

    @Test
    fun `a withheld entry has the saved path but does not draw it`() = runTest {
        val withheld = entry.copy(id = "entry-withheld", trackDecisions = listOf(trackDecision.copy(kept = false)))
        entryRepository.save(withheld).getOrThrow()

        deleteTrack("track-1")

        assertEquals("the row exists", listOf("entry-1", "entry-withheld"), savedPaths().map { it.entryId })
        assertEquals("but a withheld decision is never drawn", emptyList<RecordPolyline>(), mapData(entryRepository.getById("entry-withheld").getOrThrow()!!, emptyList()).trackPolylines)
    }

    @Test
    fun `the track's own points are still deleted, as the delete-data page says`() = runTest {
        deleteTrack("track-1")

        assertEquals(0L, count("SELECT COUNT(*) FROM track_points WHERE trackId = 'track-1'"))
        assertEquals(0L, count("SELECT COUNT(*) FROM tracks WHERE id = 'track-1'"))
    }

    @Test
    fun `the saved path is the read-seam-filtered path in timestamp order, not the stored rows`() = runTest {
        // Appended out of order, with one network-provider fix (sub-second millis, NetworkProviderFix.kt) between them.
        val walk = Track(id = "track-2", name = "Out and back", startedAtEpochMillis = 1_000L, endedAtEpochMillis = 4_000L, points = emptyList())
        trackRepository.create(walk).getOrThrow()
        trackRepository.appendPoints(
            "track-2",
            listOf(
                TrackPoint(lat = 45.3, lng = -122.3, altitude = null, accuracyMeters = null, timestampEpochMillis = 3_000L),
                TrackPoint(lat = 45.9, lng = -122.9, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_500L),
                TrackPoint(lat = 45.1, lng = -122.1, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L),
            ),
        ).getOrThrow()
        entryRepository.save(entry.copy(id = "entry-2", trackDecisions = listOf(trackDecision.copy(trackId = "track-2")))).getOrThrow()

        deleteTrack("track-2")

        assertEquals(
            listOf(SavedPath("entry-2", "track-2", listOf(LatLng(45.1, -122.1), LatLng(45.3, -122.3)))),
            savedPaths(),
        )
    }

    @Test
    fun `deleting the track again leaves the saved paths as they were`() = runTest {
        deleteTrack("track-1")
        val afterFirst = savedPaths()

        deleteTrack("track-1")

        assertEquals(afterFirst, savedPaths())
        assertEquals(1, afterFirst.size)
    }

    @Test
    fun `saving the entry again after the delete leaves its saved path in place and drawn`() = runTest {
        deleteTrack("track-1")
        val loaded = entryRepository.getById("entry-1").getOrThrow()!!

        entryRepository.save(loaded.copy(text = "Edited after the track was deleted")).getOrThrow()

        assertEquals(listOf(SavedPath("entry-1", "track-1", listOf(LatLng(45.20, -122.50)))), savedPaths())
        assertEquals(listOf(RecordPolyline("track-1", listOf(LatLng(45.20, -122.50)))), mapData(entryRepository.getById("entry-1").getOrThrow()!!, emptyList()).trackPolylines)
    }

    @Test
    fun `deleting an entry deletes its saved paths and no other entry's`() = runTest {
        entryRepository.save(entry.copy(id = "entry-2")).getOrThrow()
        deleteTrack("track-1")
        assertEquals(listOf("entry-1", "entry-2"), savedPaths().map { it.entryId })

        entryRepository.delete("entry-1").getOrThrow()

        assertEquals(listOf("entry-2"), savedPaths().map { it.entryId })
    }

    @Test
    fun `the same holds for a waypoint, which is the rule the track follows`() = runTest {
        DeleteWaypointUseCase(waypointRepository)("wp-1").getOrThrow()

        val loaded = entryRepository.getById("entry-1").getOrThrow()!!

        assertEquals("the waypoint's ref row and snapshot survive its delete, unchanged", listOf(waypointDecision), loaded.waypointDecisions)
        assertEquals(null, waypointRepository.getById("wp-1").getOrThrow())
    }

    @Test
    fun `deleting the track leaves the entry's waypoint decision alone too`() = runTest {
        deleteTrack("track-1")

        val loaded = entryRepository.getById("entry-1").getOrThrow()!!

        assertEquals(listOf(waypointDecision), loaded.waypointDecisions)
    }

    private suspend fun deleteTrack(id: String) {
        DeleteTrackUseCase(trackRepository, waypointRepository, RoomKeptTrackPathRepository(database.cartographyEntryDao()))(id).getOrThrow()
    }

    private data class SavedPath(val entryId: String, val trackId: String, val path: List<LatLng>)

    /** The rows of `cartography_entry_track_paths`, read straight from SQL and decoded, in key order: the table's own content, not the repository's reading of it. */
    private fun savedPaths(): List<SavedPath> = database.openHelper.readableDatabase
        .query("SELECT entryId, trackId, path FROM cartography_entry_track_paths ORDER BY entryId, trackId").use { c ->
            buildList { while (c.moveToNext()) add(SavedPath(c.getString(0), c.getString(1), TrackPathCodec.decode(c.getBlob(2)))) }
        }

    private fun count(sql: String): Long = database.openHelper.readableDatabase.query(sql).use { c -> c.moveToFirst(); c.getLong(0) }
}
