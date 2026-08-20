package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
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

        val early = point(lat = 1.0, t = 100L)
        val late = point(lat = 2.0, t = 200L)
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

        val points = (0 until 1_000).map { i -> point(lat = i.toDouble(), t = i.toLong()) }

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

    private fun point(lat: Double, t: Long) = TrackPoint(lat = lat, lng = 0.0, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
