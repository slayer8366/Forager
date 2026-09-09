package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackPointRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [RoomTrackRepository] against a real, in-memory Room database, for the same reason
 * [RoomSearchCacheRepositoryTest] is: what needs verifying is what Room and the entity mapping
 * actually own, not something a fake would just echo back.
 *
 * Includes a real measurement of batched-insert cost — not a pass/fail assertion against an
 * arbitrary number (a slow CI runner would make that flaky for no real reason), but a check that
 * batching genuinely dominates one-row-at-a-time inserts, which is the actual claim
 * [TrackRepository][com.forager.app.domain.TrackRepository]'s doc comment makes. See its printed
 * output for the concrete numbers on whatever machine ran it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomTrackRepositoryTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomTrackRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        repository = RoomTrackRepository(database.trackDao())
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    /** HUD-foundations dispatch, Item 3: the origin pointer is written with the row and read back as-is. */
    @Test
    fun `a track's origin waypoint pointer round-trips, and a track without one reads back null`() = runTest {
        val withOrigin = Track(id = "t1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList(), originWaypointId = "wp-origin")
        val withoutOrigin = Track(id = "t2", name = null, startedAtEpochMillis = 2_000L, endedAtEpochMillis = null, points = emptyList())

        repository.create(withOrigin).getOrThrow()
        repository.create(withoutOrigin).getOrThrow()

        assertEquals("wp-origin", repository.getById("t1").getOrThrow()?.originWaypointId)
        assertNull(repository.getById("t2").getOrThrow()?.originWaypointId)
    }

    /** Navigation HUD stage one: the origin pointer's write path, run after the origin waypoint is created. */
    @Test
    fun `setting the origin pointer updates the stored track, and a missing track is not a failure`() = runTest {
        repository.create(Track(id = "t1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()

        repository.setOriginWaypoint("t1", "wp-origin").getOrThrow()

        assertEquals("wp-origin", repository.getById("t1").getOrThrow()?.originWaypointId)
        assertTrue(repository.setOriginWaypoint("no-such-track", "wp").isSuccess)
    }

    @Test
    fun `a created track starts with no points and no end time`() = runTest {
        val track = Track(id = "t1", name = "Morning walk", startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList())

        repository.create(track).getOrThrow()
        val stored = repository.getById("t1").getOrThrow()

        assertEquals(track, stored)
    }

    @Test
    fun `appended points come back in timestamp order regardless of insertion order`() = runTest {
        repository.create(Track(id = "t1", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()

        // Whole-second stamps: since the timestamp-filter dispatch the read seam excludes any point
        // with a fractional second as a network-provider fix, so sample data must look like GPS data.
        val early = point(lat = 1.0, t = 100_000L)
        val late = point(lat = 2.0, t = 200_000L)
        repository.appendPoints("t1", listOf(late)).getOrThrow()
        repository.appendPoints("t1", listOf(early)).getOrThrow()

        val stored = repository.getById("t1").getOrThrow()!!
        assertEquals(listOf(early, late), stored.points)
    }

    @Test
    fun `ending a track sets its end time and leaves everything else untouched`() = runTest {
        val track = Track(id = "t1", name = "Loop", startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())
        repository.create(track).getOrThrow()

        repository.end("t1", endedAtEpochMillis = 5_000L).getOrThrow()

        val stored = repository.getById("t1").getOrThrow()!!
        assertEquals(5_000L, stored.endedAtEpochMillis)
        assertEquals("Loop", stored.name)
    }

    @Test
    fun `deleting a track removes its points too`() = runTest {
        repository.create(Track(id = "t1", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
        repository.appendPoints("t1", listOf(point(lat = 1.0, t = 0L))).getOrThrow()

        repository.delete("t1").getOrThrow()

        assertNull(repository.getById("t1").getOrThrow())
        assertTrue(repository.getAll().getOrThrow().isEmpty())
    }

    @Test
    fun `a getById miss returns a successful null, not a failure`() = runTest {
        val result = repository.getById("does-not-exist")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    /**
     * A multi-hour recording at [com.forager.app.domain.model.TrackRecordingMode.HIGH_ACCURACY]'s
     * 5-second interval is ~720 points/hour; this uses 1,000 as a round, representative session
     * length. Real numbers, not an assertion of "fast enough" against a made-up threshold — a slow
     * CI runner would make an absolute-time assertion flaky for no reason connected to whether
     * batching itself works.
     */
    @Test
    fun `batched insert of 1000 points is meaningfully cheaper than the same points one at a time`() = runTest {
        repository.create(Track(id = "batched", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
        repository.create(Track(id = "one-by-one", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()

        // Whole-second stamps, for the same reason as the ordering test above.
        val points = (0 until 1_000).map { i -> point(lat = i.toDouble(), t = i * 1_000L) }

        val batchedMillis = measureMillis {
            repository.appendPoints("batched", points).getOrThrow()
        }
        val oneAtATimeMillis = measureMillis {
            points.forEach { repository.appendPoints("one-by-one", listOf(it)).getOrThrow() }
        }

        println("RoomTrackRepositoryTest: 1000 points batched=${batchedMillis}ms, one-at-a-time=${oneAtATimeMillis}ms")
        assertEquals(1_000, repository.getById("batched").getOrThrow()!!.points.size)
        assertTrue(
            "batched insert ($batchedMillis ms) should be faster than 1000 individual inserts ($oneAtATimeMillis ms)",
            batchedMillis <= oneAtATimeMillis,
        )
    }

    /**
     * Return-estimate dispatch, Item 3: the Doppler speed columns round-trip through the entity
     * mapping in both directions, and a point that reported neither reads back `null` in both —
     * not `0.0`, which would read as "stopped" to the pace.
     */
    @Test
    fun `a point's speed and speed accuracy round-trip, and a point without them reads back null in both`() = runTest {
        repository.create(Track(id = "t", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
        val moving = TrackPoint(lat = 45.0, lng = -122.0, altitude = 300.0, accuracyMeters = 3.79f, timestampEpochMillis = 1_000L, speedMetersPerSecond = 0.87f, speedAccuracyMetersPerSecond = 0.694f)
        val unreported = TrackPoint(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L, speedMetersPerSecond = null, speedAccuracyMetersPerSecond = null)
        repository.appendPoints("t", listOf(moving, unreported)).getOrThrow()

        val stored = repository.getById("t").getOrThrow()!!.points

        assertEquals(listOf(moving, unreported), stored)
        assertEquals(listOf(0.87f, null), stored.map { it.speedMetersPerSecond })
        assertEquals(listOf(0.694f, null), stored.map { it.speedAccuracyMetersPerSecond })
    }

    private fun point(lat: Double, t: Long) = TrackPoint(lat = lat, lng = 0.0, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    /**
     * Timestamp-filter dispatch: the read seam drops sub-second (network-provider) points and reports
     * how many, while the rows themselves stay exactly as inserted. The boundary — millis exactly
     * zero — is asserted as kept.
     */
    @Test
    fun `reads exclude sub-second points, count them, and leave the stored rows untouched`() = runTest {
        repository.create(Track(id = "t", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
        val kept0 = TrackPoint(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 0L)
        val dropped = TrackPoint(lat = 45.030, lng = -122.0, altitude = 111.6, accuracyMeters = null, timestampEpochMillis = 2_001L)
        val kept5 = TrackPoint(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 5_000L)
        repository.appendPoints("t", listOf(kept0, dropped, kept5)).getOrThrow()

        val track = repository.getById("t").getOrThrow()!!
        assertEquals(listOf(kept0, kept5), track.points)
        assertEquals(1, track.excludedPointCount)

        val rows = database.trackDao().getPointsForTrack("t")
        assertEquals(listOf(0L, 2_001L, 5_000L), rows.map { it.timestampEpochMillis })
        assertEquals(listOf(45.000, 45.030, 45.001), rows.map { it.lat })
    }

    /**
     * GPX full-record export dispatch, Item 1: [getFullRecord] is the one read that does **not**
     * apply the network-provider-fix rule — every stored row comes back, each carrying the same
     * verdict [getById] uses to decide what to exclude, so the two can never disagree about which
     * points the rule would drop.
     */
    @Test
    fun `getFullRecord returns every stored point with its verdict, unlike getById's filtered read`() = runTest {
        repository.create(Track(id = "t", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
        val kept0 = TrackPoint(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 0L)
        val dropped = TrackPoint(lat = 45.030, lng = -122.0, altitude = 111.6, accuracyMeters = null, timestampEpochMillis = 2_001L)
        val kept5 = TrackPoint(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 5_000L)
        repository.appendPoints("t", listOf(kept0, dropped, kept5)).getOrThrow()

        val fullRecord = repository.getFullRecord("t").getOrThrow()

        // The rule identifier is the literal the GPX file carries, written out by hand: an expectation
        // read from the constant under test would pass for whatever value that constant happened to hold.
        assertEquals(
            listOf(
                TrackPointRecord(kept0, kept = true),
                TrackPointRecord(dropped, kept = false, excludedByRule = "timestampMillisNonZero"),
                TrackPointRecord(kept5, kept = true),
            ),
            fullRecord,
        )
        // getById's own filtered read must still agree with which of these the verdict marks kept.
        assertEquals(listOf(kept0, kept5), repository.getById("t").getOrThrow()!!.points)
    }

    @Test
    fun `getFullRecord on a track with no stored points, or no such track, is an empty list not a failure`() = runTest {
        repository.create(Track(id = "t", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()

        assertTrue(repository.getFullRecord("t").getOrThrow().isEmpty())
        assertTrue(repository.getFullRecord("no-such-track").getOrThrow().isEmpty())
    }

}
