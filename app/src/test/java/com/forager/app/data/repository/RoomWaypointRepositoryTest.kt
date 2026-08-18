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
}
