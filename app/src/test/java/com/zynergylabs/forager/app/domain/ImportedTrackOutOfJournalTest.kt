package com.zynergylabs.forager.app.domain

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plan T16, the owner's "Records and map only" (RECORD -636): an imported GPX track, and the waypoints that
 * came in with it, stay out of the Journal's derived trips; a recorded walk on the same day, and a waypoint
 * of its own, are there as before. Through the real [GetDerivedTripUseCase] over Room, with the track
 * written by the real [ImportGpxUseCase] from a file dated that day. Records and the map read every track
 * ([GetTracksUseCase], [GetMapRecordsUseCase]), so the imported one is still in [TrackRepository.getAll].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImportedTrackOutOfJournalTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private lateinit var database: ForagerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `an imported track and its waypoints are left out of that day's derived trip, a recorded walk is not`() = runTest {
        val tracks = RoomTrackRepository(database.trackDao())
        val waypoints = RoomWaypointRepository(database.waypointDao())
        // The MULTI_TRACK file's first track is on 2026-09-23 (10:00 to 11:00 UTC) with two waypoints that day.
        val outcome = ImportGpxUseCase(tracks, waypoints, currentTime = { 1_790_000_000_000L }, errorLog = { _, _, _ -> })(GpxImportFixtures.source(GpxImportFixtures.MULTI_TRACK))
        val importedIds = (outcome as GpxImportOutcome.Imported).trackIds
        val day = LocalDate.of(2026, 9, 23)
        val noon = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        tracks.create(Track("recorded", null, noon, noon + 3_600_000L, emptyList())).getOrThrow()
        waypoints.save(Waypoint("own-wp", 45.0, -122.0, null, "Own", "", noon + 60_000L, trackId = "recorded")).getOrThrow()
        waypoints.save(Waypoint("loose-wp", 45.0, -122.0, null, "Loose", "", noon + 120_000L)).getOrThrow()

        val trip = GetDerivedTripUseCase(
            RoomMushroomLogRepository(database.mushroomLogDao()),
            tracks,
            waypoints,
            RoomOfflineRegionDayIndex(database.offlineRegionDao()),
        )(day, zone).getOrThrow()

        assertEquals("only the recorded walk", listOf("recorded"), trip.tracks.map { it.id })
        assertEquals("the walk's own waypoint and a loose one, none of the file's", setOf("Own", "Loose"), trip.waypoints.map { it.name }.toSet())
        assertEquals("Records and the map still have every imported track", importedIds.toSet() + "recorded", tracks.getAll().getOrThrow().map { it.id }.toSet())
    }
}
