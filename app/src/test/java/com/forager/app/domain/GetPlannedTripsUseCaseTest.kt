package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePlannedTripRepository(
    private val getAllResult: Result<List<PlannedTrip>>,
) : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = getAllResult
    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        throw NotImplementedError("not exercised by GetPlannedTripsUseCaseTest")
    override suspend fun delete(id: String): Result<Unit> =
        throw NotImplementedError("not exercised by GetPlannedTripsUseCaseTest")
}

private fun trip(id: String, date: LocalDate) = PlannedTrip(id = id, name = id, location = LatLng(45.0, -122.0), date = date)

class GetPlannedTripsUseCaseTest {

    private val referenceDay = LocalDate.of(2026, 8, 17)

    @Test
    fun `a trip dated today is moved to the front`() = runTest {
        val repository = FakePlannedTripRepository(
            Result.success(
                listOf(
                    trip("soonest", referenceDay.plusDays(1)),
                    trip("today", referenceDay),
                    trip("later", referenceDay.plusDays(5)),
                ),
            ),
        )
        val useCase = GetPlannedTripsUseCase(repository, today = { referenceDay })

        val result = useCase().getOrThrow()

        assertEquals(listOf("today", "soonest", "later"), result.map { it.id })
    }

    @Test
    fun `trips other than today's stay ordered soonest-first`() = runTest {
        val repository = FakePlannedTripRepository(
            Result.success(
                listOf(
                    trip("far", referenceDay.plusDays(10)),
                    trip("near", referenceDay.plusDays(2)),
                ),
            ),
        )
        val useCase = GetPlannedTripsUseCase(repository, today = { referenceDay })

        val result = useCase().getOrThrow()

        assertEquals(listOf("near", "far"), result.map { it.id })
    }

    @Test
    fun `multiple trips dated today all stay at the front`() = runTest {
        val repository = FakePlannedTripRepository(
            Result.success(
                listOf(
                    trip("future", referenceDay.plusDays(1)),
                    trip("today-a", referenceDay),
                    trip("today-b", referenceDay),
                ),
            ),
        )
        val useCase = GetPlannedTripsUseCase(repository, today = { referenceDay })

        val result = useCase().getOrThrow()

        assertEquals(setOf("today-a", "today-b"), result.take(2).map { it.id }.toSet())
        assertEquals("future", result.last().id)
    }

    @Test
    fun `repository failure propagates as failure`() = runTest {
        val failure = IllegalStateException("disk error")
        val repository = FakePlannedTripRepository(Result.failure(failure))
        val useCase = GetPlannedTripsUseCase(repository, today = { referenceDay })

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
