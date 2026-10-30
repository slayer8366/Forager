package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
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
    private val logged = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        repository = RoomPlannedTripRepository(database.plannedTripRouteDao(), ErrorLog { _, message, error -> logged += "$message :: ${error.message}" })
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

    // ---- schema 23: undated trips and routes (dispatch 2026-10-30-02, step 1) -------------------

    @Test
    fun `an undated trip reads back with no date`() = runTest {
        val trip = PlannedTrip(id = "undated", name = "Someday", location = LatLng(45.4, -122.7), date = null)

        repository.save(trip).getOrThrow()

        assertEquals(listOf(trip), repository.getAll().getOrThrow())
    }

    @Test
    fun `a trip's route reads back with its stops, its legs' lines, lengths, trail parts and reasons`() = runTest {
        repository.save(routeTrip).getOrThrow()

        assertEquals(listOf(routeTrip), repository.getAll().getOrThrow())
        assertEquals(6580.75, repository.getAll().getOrThrow().single().route!!.totalMeters, 1e-9)
        assertEquals("nothing was refused", emptyList<String>(), logged)
    }

    @Test
    fun `saving a trip again without its route removes the route rows`() = runTest {
        repository.save(routeTrip).getOrThrow()

        repository.save(routeTrip.copy(route = null)).getOrThrow()

        assertEquals(listOf(routeTrip.copy(route = null)), repository.getAll().getOrThrow())
        assertEquals(0, rowCount("planned_trip_route_stops") + rowCount("planned_trip_route_legs"))
    }

    @Test
    fun `deleting a route trip removes its stops and legs, and leaves another trip's route alone`() = runTest {
        val other = routeTrip.copy(id = "other")
        repository.save(routeTrip).getOrThrow()
        repository.save(other).getOrThrow()

        repository.delete(routeTrip.id).getOrThrow()

        assertEquals(listOf(other), repository.getAll().getOrThrow())
        assertEquals(3, rowCount("planned_trip_route_stops"))
        assertEquals(2, rowCount("planned_trip_route_legs"))
    }

    private fun rowCount(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private val a = LatLng(45.2, -122.5)
    private val b = LatLng(45.22, -122.52)
    private val c = LatLng(45.25, -122.49)

    private val routeTrip = PlannedTrip(
        id = "route-1",
        name = "Ridge run",
        location = a,
        date = LocalDate.of(2026, 11, 14),
        route = PlottedRoute(
            stops = listOf(RouteStop("Creek pin", a), RouteStop("Big oak", b), RouteStop("Spring", c)),
            legs = listOf(
                RouteLeg.Trail(listOf(a, LatLng(45.201, -122.501), LatLng(45.219, -122.519), b), meters = 2480.5, trailFrom = 1, trailTo = 2),
                RouteLeg.Straight(listOf(b, c), meters = 4100.25, reason = StraightReason.NO_REGION_COVERS),
            ),
        ),
    )
}
