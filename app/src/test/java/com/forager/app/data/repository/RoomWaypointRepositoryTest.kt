package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.domain.model.Waypoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomWaypointRepositoryTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomWaypointRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        repository = RoomWaypointRepository(database.waypointDao())
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun `a saved waypoint comes back with every field intact`() = runTest {
        val waypoint = Waypoint(id = "w1", lat = 45.5, lng = -122.6, altitude = 312.0, name = "Parking", note = "Room for 3 cars", createdAtEpochMillis = 1_000L)

        repository.save(waypoint).getOrThrow()

        assertEquals(listOf(waypoint), repository.getAll().getOrThrow())
    }

    @Test
    fun `saving a waypoint with the same id replaces it rather than duplicating it`() = runTest {
        val original = Waypoint(id = "w1", lat = 45.5, lng = -122.6, altitude = null, name = "Old name", note = "", createdAtEpochMillis = 1_000L)
        val renamed = original.copy(name = "New name")

        repository.save(original).getOrThrow()
        repository.save(renamed).getOrThrow()

        val all = repository.getAll().getOrThrow()
        assertEquals(1, all.size)
        assertEquals("New name", all.first().name)
    }

    @Test
    fun `deleting a waypoint removes it, and a delete of a missing id is not a failure`() = runTest {
        val waypoint = Waypoint(id = "w1", lat = 45.5, lng = -122.6, altitude = null, name = "Spot", note = "", createdAtEpochMillis = 1_000L)
        repository.save(waypoint).getOrThrow()

        repository.delete("w1").getOrThrow()
        assertTrue(repository.getAll().getOrThrow().isEmpty())

        val result = repository.delete("does-not-exist")
        assertTrue(result.isSuccess)
    }

    /** HUD-foundations dispatch, Item 3: the `trackId` link round-trips, and its read path returns only the linked waypoints, oldest first. */
    @Test
    fun `waypoints dropped while a track recorded come back for that track, oldest first, and no others`() = runTest {
        val second = Waypoint(id = "w2", lat = 45.53, lng = -122.69, altitude = null, name = "Second", note = "", createdAtEpochMillis = 2_000L, trackId = "t1")
        val first = Waypoint(id = "w1", lat = 45.52, lng = -122.68, altitude = 50.0, name = "Trailhead", note = "", createdAtEpochMillis = 1_000L, trackId = "t1")
        val otherTrack = Waypoint(id = "w3", lat = 45.6, lng = -122.7, altitude = null, name = "Other", note = "", createdAtEpochMillis = 1_500L, trackId = "t2")
        val unlinked = Waypoint(id = "w4", lat = 45.7, lng = -122.8, altitude = null, name = "Loose", note = "", createdAtEpochMillis = 1_200L, trackId = null)
        listOf(second, first, otherTrack, unlinked).forEach { repository.save(it).getOrThrow() }

        assertEquals(listOf(first, second), repository.getForTrack("t1").getOrThrow())
        assertEquals(listOf(otherTrack), repository.getForTrack("t2").getOrThrow())
        assertEquals(emptyList<Waypoint>(), repository.getForTrack("no-such-track").getOrThrow())
        assertEquals(unlinked, repository.getById("w4").getOrThrow())
    }

    /** HUD-foundations dispatch, Item 3 (owner decision): detaching nulls the link and keeps the waypoint. */
    @Test
    fun `detaching from a track nulls the link on its waypoints only and keeps every row`() = runTest {
        val linked = Waypoint(id = "w1", lat = 45.52, lng = -122.68, altitude = 50.0, name = "Trailhead", note = "", createdAtEpochMillis = 1_000L, trackId = "t1")
        val otherTrack = Waypoint(id = "w3", lat = 45.6, lng = -122.7, altitude = null, name = "Other", note = "", createdAtEpochMillis = 1_500L, trackId = "t2")
        repository.save(linked).getOrThrow()
        repository.save(otherTrack).getOrThrow()

        repository.detachFromTrack("t1").getOrThrow()

        assertEquals(linked.copy(trackId = null), repository.getById("w1").getOrThrow())
        assertEquals(otherTrack, repository.getById("w3").getOrThrow())
        assertEquals(emptyList<Waypoint>(), repository.getForTrack("t1").getOrThrow())
        assertTrue(repository.detachFromTrack("never-linked").isSuccess)
    }
}
