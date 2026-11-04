package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SavePlottedRouteUseCase] (dispatch 2026-10-30-08, step 3). */
class SavePlottedRouteUseCaseTest {

    private class Recording(private val result: Result<Unit> = Result.success(Unit)) : PlannedTripRepository {
        val saved = mutableListOf<PlannedTrip>()
        override suspend fun getAll(): Result<List<PlannedTrip>> = throw NotImplementedError("not used")
        override suspend fun save(trip: PlannedTrip): Result<Unit> = result.also { saved += trip }
        override suspend fun delete(id: String): Result<Unit> = throw NotImplementedError("not used")
    }

    private val today = LocalDate.of(2026, 10, 30)
    private val creek = RouteStop("Creek", LatLng(45.2, -122.5))
    private val falls = RouteStop("Falls", LatLng(45.24, -122.55))
    private val route = PlottedRoute(
        listOf(creek, falls),
        listOf(RouteLeg.Straight(listOf(creek.position, falls.position), 900.0, StraightReason.NO_REGION_COVERS)),
    )

    private fun useCase(repository: PlannedTripRepository) = SavePlottedRouteUseCase(repository, today = { today }, idGenerator = { "id-1" })

    @Test
    fun `saves the route as a trip pinned at its first stop, undated when no date is given`() = runTest {
        val repository = Recording()

        val trip = useCase(repository)(route, "Creek to Falls", null).getOrThrow()

        val expected = PlannedTrip(id = "id-1", name = "Creek to Falls", location = creek.position, date = null, route = route)
        assertEquals(expected, trip)
        assertEquals(listOf(expected), repository.saved)
    }

    @Test
    fun `saves a date of today or later`() = runTest {
        val repository = Recording()

        assertEquals(today, useCase(repository)(route, "Creek to Falls", today).getOrThrow().date)
    }

    @Test
    fun `refuses a blank name and a past date as failures, saving nothing`() = runTest {
        val repository = Recording()

        val blank = useCase(repository)(route, "  ", null)
        val past = useCase(repository)(route, "Creek to Falls", today.minusDays(1))

        assertTrue(blank.exceptionOrNull() is IllegalArgumentException)
        assertTrue(past.exceptionOrNull() is IllegalArgumentException)
        assertEquals(emptyList<PlannedTrip>(), repository.saved)
    }

    @Test
    fun `a failed save is a failure`() = runTest {
        val error = IllegalStateException("disk full")

        val result = useCase(Recording(Result.failure(error)))(route, "Creek to Falls", null)

        assertEquals(error, result.exceptionOrNull())
        assertNull(result.getOrNull())
    }
}
