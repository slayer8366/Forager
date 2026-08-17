package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import java.time.LocalDate
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
 * [RoomPlannedTripRepository] against a real, in-memory Room database — not a fake — because the
 * thing worth verifying here is the mapping Room itself is responsible for: entity-to-domain
 * conversion, [LocalDate] round-tripping through Room's `TEXT` column, and replace-on-conflict
 * upsert semantics. A hand-written fake would only echo back whatever this test assumed, proving
 * nothing about the real persistence path (CLAUDE.md: assert on actual output, not a proxy for it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomPlannedTripRepositoryTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomPlannedTripRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        repository = RoomPlannedTripRepository(database.plannedTripDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a saved trip is returned by getAll with the same fields`() = runTest {
        val trip = PlannedTrip(id = "trip-1", name = "Trip 1", location = LatLng(45.4, -122.7), date = LocalDate.of(2026, 9, 1))

        repository.save(trip).getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(trip), all)
    }

    @Test
    fun `getAll on an empty database returns an empty list, not a failure`() = runTest {
        val all = repository.getAll().getOrThrow()

        assertTrue(all.isEmpty())
    }

    @Test
    fun `saving a trip with an existing id replaces it rather than duplicating it`() = runTest {
        val original = PlannedTrip(id = "trip-1", name = "Original", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 9, 1))
        val updated = PlannedTrip(id = "trip-1", name = "Updated", location = LatLng(46.0, -123.0), date = LocalDate.of(2026, 9, 10))

        repository.save(original).getOrThrow()
        repository.save(updated).getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(updated), all)
    }

    @Test
    fun `deleting a trip removes it and leaves the others`() = runTest {
        val keep = PlannedTrip(id = "keep", name = "Keep", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 9, 1))
        val remove = PlannedTrip(id = "remove", name = "Remove", location = LatLng(46.0, -123.0), date = LocalDate.of(2026, 9, 2))
        repository.save(keep).getOrThrow()
        repository.save(remove).getOrThrow()

        repository.delete("remove").getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(keep), all)
    }

    @Test
    fun `deleting an id that isn't stored is a no-op, not a failure`() = runTest {
        val result = repository.delete("never-saved")

        assertTrue(result.isSuccess)
    }
}
