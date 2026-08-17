package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class SaveRecordingPlannedTripRepository(
    private val saveResult: Result<Unit> = Result.success(Unit),
) : PlannedTripRepository {
    var savedTrip: PlannedTrip? = null

    override suspend fun getAll(): Result<List<PlannedTrip>> =
        throw NotImplementedError("not exercised by SavePlannedTripUseCaseTest")

    override suspend fun save(trip: PlannedTrip): Result<Unit> {
        savedTrip = trip
        return saveResult
    }

    override suspend fun delete(id: String): Result<Unit> =
        throw NotImplementedError("not exercised by SavePlannedTripUseCaseTest")
}

class SavePlannedTripUseCaseTest {

    private val today = LocalDate.of(2026, 8, 17)
    private val location = LatLng(45.4, -122.7)

    @Test
    fun `saves a trip with the given location, date and name, assigning it an id`() = runTest {
        val repository = SaveRecordingPlannedTripRepository()
        val useCase = SavePlannedTripUseCase(repository, today = { today }, idGenerator = { "generated-id" })

        val result = useCase(location, today, "Trip 1").getOrThrow()

        assertEquals(PlannedTrip(id = "generated-id", name = "Trip 1", location = location, date = today), result)
        assertEquals(PlannedTrip(id = "generated-id", name = "Trip 1", location = location, date = today), repository.savedTrip)
    }

    @Test
    fun `accepts a future date`() = runTest {
        val repository = SaveRecordingPlannedTripRepository()
        val useCase = SavePlannedTripUseCase(repository, today = { today }, idGenerator = { "id" })

        val result = useCase(location, today.plusDays(10), "Trip 1")

        assertTrue(result.isSuccess)
        assertEquals(today.plusDays(10), result.getOrThrow().date)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a date before today`() = runTest {
        val repository = SaveRecordingPlannedTripRepository()
        val useCase = SavePlannedTripUseCase(repository, today = { today }, idGenerator = { "id" })

        useCase(location, today.minusDays(1), "Trip 1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a blank name`() = runTest {
        val repository = SaveRecordingPlannedTripRepository()
        val useCase = SavePlannedTripUseCase(repository, today = { today }, idGenerator = { "id" })

        useCase(location, today, "   ")
    }

    @Test
    fun `repository failure propagates as failure`() = runTest {
        val failure = IllegalStateException("disk full")
        val repository = SaveRecordingPlannedTripRepository(saveResult = Result.failure(failure))
        val useCase = SavePlannedTripUseCase(repository, today = { today }, idGenerator = { "id" })

        val result = useCase(location, today, "Trip 1")

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `each call gets a distinct id from the default generator`() = runTest {
        val repository = SaveRecordingPlannedTripRepository()
        val useCase = SavePlannedTripUseCase(repository, today = { today })

        val first = useCase(location, today, "Trip 1").getOrThrow()
        val second = useCase(location, today, "Trip 2").getOrThrow()

        assertTrue(first.id != second.id)
    }
}
