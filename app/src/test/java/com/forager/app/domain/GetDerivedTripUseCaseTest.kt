package com.forager.app.domain

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.data.local.OfflineRegionEntity
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.data.repository.RoomWaypointRepository
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [GetDerivedTripUseCase] against a real, in-memory Room database, through the real
 * [RoomMushroomLogRepository]/[RoomTrackRepository]/[RoomWaypointRepository]/[RoomOfflineRegionDayIndex]
 * — Journal Stage 2a dispatch: "test the day-boundary logic hard... it is the part most likely to be
 * subtly wrong and the part no UI test would catch." A real SQLite database is what actually proves
 * the indexed `WHERE` clauses in `MushroomLogDao`/`TrackDao`/`WaypointDao`/`OfflineRegionDao` behave
 * correctly at the boundary, not a hand-rolled in-memory fake that could just re-implement the same
 * mistake the real query might have.
 *
 * A fixed [ZoneId] (`America/Los_Angeles`) is passed explicitly throughout, never
 * [ZoneId.systemDefault], so these assertions hold regardless of what zone this suite happens to run
 * in — see [LocalDayRangeTest] for the boundary math itself, isolated from Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GetDerivedTripUseCaseTest {

    private val zone = ZoneId.of("America/Los_Angeles")

    private lateinit var database: ForagerDatabase
    private lateinit var useCase: GetDerivedTripUseCase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        useCase = GetDerivedTripUseCase(
            mushroomLogRepository = RoomMushroomLogRepository(database.mushroomLogDao()),
            trackRepository = RoomTrackRepository(database.trackDao()),
            waypointRepository = RoomWaypointRepository(database.waypointDao()),
            offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a find at 11-59pm local belongs to that evening's trip, not the next day's`() = runTest {
        val evening = MushroomLogEntry.draft(id = "evening-find", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 5))
            .copy(isDraft = false)
        RoomMushroomLogRepository(database.mushroomLogDao()).save(evening).getOrThrow()

        val theDayItself = useCase(LocalDate.of(2026, 8, 5), zone).getOrThrow()
        val theNextDay = useCase(LocalDate.of(2026, 8, 6), zone).getOrThrow()

        assertEquals(listOf("evening-find"), theDayItself.finds.map { it.id })
        assertTrue("the next day's trip must not also claim this find", theNextDay.finds.isEmpty())
    }

    @Test
    fun `a track from 10pm to 1am appears in both days' derived trips`() = runTest {
        val startedAt = LocalDate.of(2026, 8, 5).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val endedAt = LocalDate.of(2026, 8, 6).atTime(1, 0).atZone(zone).toInstant().toEpochMilli()
        val track = Track(id = "midnight-track", name = null, startedAtEpochMillis = startedAt, endedAtEpochMillis = endedAt, points = emptyList())
        val trackRepository = RoomTrackRepository(database.trackDao())
        trackRepository.create(track).getOrThrow()
        trackRepository.appendPoints(
            trackId = track.id,
            points = listOf(TrackPoint(lat = 45.5, lng = -122.6, altitude = null, accuracyMeters = null, timestampEpochMillis = startedAt)),
        ).getOrThrow()
        trackRepository.end(track.id, endedAt).getOrThrow()

        val firstDay = useCase(LocalDate.of(2026, 8, 5), zone).getOrThrow()
        val secondDay = useCase(LocalDate.of(2026, 8, 6), zone).getOrThrow()
        val unrelatedDay = useCase(LocalDate.of(2026, 8, 7), zone).getOrThrow()

        assertEquals(listOf("midnight-track"), firstDay.tracks.map { it.id })
        assertEquals(listOf("midnight-track"), secondDay.tracks.map { it.id })
        assertTrue("a day the track never touched must not claim it", unrelatedDay.tracks.isEmpty())
        // Proves this is a real join back to track_points, not just the bare TrackEntity row.
        assertEquals(1, firstDay.tracks.single().points.size)
    }

    /** 2026-03-08 is a US spring-forward day in `America/Los_Angeles` — see [LocalDayRangeTest] for the boundary math this exercises end to end. */
    @Test
    fun `a waypoint dropped just after local midnight on a DST transition day is bucketed into that transition day`() = runTest {
        val justAfterMidnight = LocalDate.of(2026, 3, 8).atTime(0, 5).atZone(zone).toInstant().toEpochMilli()
        val waypoint = Waypoint(
            id = "dst-waypoint",
            lat = 45.5,
            lng = -122.6,
            altitude = null,
            name = "Trailhead",
            note = "",
            createdAtEpochMillis = justAfterMidnight,
        )
        RoomWaypointRepository(database.waypointDao()).save(waypoint).getOrThrow()

        val transitionDay = useCase(LocalDate.of(2026, 3, 8), zone).getOrThrow()
        val dayBefore = useCase(LocalDate.of(2026, 3, 7), zone).getOrThrow()

        assertEquals(listOf("dst-waypoint"), transitionDay.waypoints.map { it.id })
        assertTrue(dayBefore.waypoints.isEmpty())
    }

    @Test
    fun `an offline region downloaded that day is included, without live tile-status fields it cannot honestly have`() = runTest {
        val downloadedAt = LocalDate.of(2026, 8, 5).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        database.offlineRegionDao().upsert(
            OfflineRegionEntity(
                id = 42L,
                name = "Chanterelle Ridge",
                lat = 45.5,
                lng = -122.6,
                radiusKm = 10,
                minZoom = 10.0,
                maxZoom = 15.0,
                createdAtEpochMillis = downloadedAt,
                isEntryCapture = false,
            ),
        )

        val theDay = useCase(LocalDate.of(2026, 8, 5), zone).getOrThrow()

        assertEquals(listOf(42L), theDay.offlineRegions.map { it.id })
        assertEquals("Chanterelle Ridge", theDay.offlineRegions.single().name)
    }

    @Test
    fun `a day with no data at all succeeds with every list empty, not a failure`() = runTest {
        val trip = useCase(LocalDate.of(2026, 1, 1), zone).getOrThrow()

        assertTrue(trip.finds.isEmpty())
        assertTrue(trip.tracks.isEmpty())
        assertTrue(trip.waypoints.isEmpty())
        assertTrue(trip.offlineRegions.isEmpty())
    }

    @Test
    fun `finds are not filtered by draft state - this dispatch does not decide entries-vs-drafts curation`() = runTest {
        val draft = MushroomLogEntry.draft(id = "still-a-draft", location = LatLng(45.5, -122.6), date = LocalDate.of(2026, 8, 5))
        RoomMushroomLogRepository(database.mushroomLogDao()).save(draft).getOrThrow()

        val trip = useCase(LocalDate.of(2026, 8, 5), zone).getOrThrow()

        assertEquals(listOf("still-a-draft"), trip.finds.map { it.id })
    }
}
