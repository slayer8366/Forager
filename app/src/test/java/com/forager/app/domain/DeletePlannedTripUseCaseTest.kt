package com.forager.app.domain

import com.forager.app.domain.model.PlannedTrip
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class DeleteRecordingPlannedTripRepository(
    private val deleteResult: Result<Unit> = Result.success(Unit),
) : PlannedTripRepository {
    var deletedId: String? = null

    override suspend fun getAll(): Result<List<PlannedTrip>> =
        throw NotImplementedError("not exercised by DeletePlannedTripUseCaseTest")

    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        throw NotImplementedError("not exercised by DeletePlannedTripUseCaseTest")

    override suspend fun delete(id: String): Result<Unit> {
        deletedId = id
        return deleteResult
    }
}

class DeletePlannedTripUseCaseTest {

    @Test
    fun `deletes by the given id`() = runTest {
        val repository = DeleteRecordingPlannedTripRepository()
        val useCase = DeletePlannedTripUseCase(repository)

        val result = useCase("trip-1")

        assertTrue(result.isSuccess)
        assertEquals("trip-1", repository.deletedId)
    }

    @Test
    fun `repository failure propagates as failure`() = runTest {
        val failure = IllegalStateException("disk error")
        val repository = DeleteRecordingPlannedTripRepository(deleteResult = Result.failure(failure))
        val useCase = DeletePlannedTripUseCase(repository)

        val result = useCase("trip-1")

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
