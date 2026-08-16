package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private class FakeSightingsRepository(
    private val speciesCountsResult: Result<List<com.forager.app.domain.model.SpeciesObservationCount>> = Result.success(emptyList()),
    private val sightingsResult: Result<List<Sighting>>,
) : MushroomRepository {
    var lastRegion: Region? = null
    var lastMonth: Int? = null

    override suspend fun getSpeciesCounts(region: Region, month: Int) = speciesCountsResult

    override suspend fun getSightings(region: Region, month: Int): Result<List<Sighting>> {
        lastRegion = region
        lastMonth = month
        return sightingsResult
    }
}

private fun sighting(id: Long, observedOn: LocalDate?) = Sighting(
    observationId = id,
    taxonId = id,
    scientificName = "Species $id",
    commonName = null,
    lat = 45.0,
    lng = -122.0,
    observedOn = observedOn,
    photoUrl = null,
)

class GetSightingsUseCaseTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    @Test
    fun `sorts sightings by observed date, most recent first`() = runTest {
        val repository = FakeSightingsRepository(
            sightingsResult = Result.success(
                listOf(
                    sighting(1, LocalDate.of(2025, 9, 1)),
                    sighting(2, LocalDate.of(2025, 9, 20)),
                    sighting(3, LocalDate.of(2025, 9, 10)),
                ),
            ),
        )
        val useCase = GetSightingsUseCase(repository)

        val result = useCase(region, month = 9).getOrThrow()

        assertEquals(listOf(2L, 3L, 1L), result.map { it.observationId })
    }

    @Test
    fun `sightings with no observed date sort last`() = runTest {
        val repository = FakeSightingsRepository(
            sightingsResult = Result.success(
                listOf(
                    sighting(1, null),
                    sighting(2, LocalDate.of(2025, 9, 20)),
                ),
            ),
        )
        val useCase = GetSightingsUseCase(repository)

        val result = useCase(region, month = 9).getOrThrow()

        assertEquals(listOf(2L, 1L), result.map { it.observationId })
    }

    @Test
    fun `repository failure propagates as failure`() = runTest {
        val failure = IllegalStateException("network down")
        val repository = FakeSightingsRepository(sightingsResult = Result.failure(failure))
        val useCase = GetSightingsUseCase(repository)

        val result = useCase(region, month = 9)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `passes the requested region and month through unchanged`() = runTest {
        val repository = FakeSightingsRepository(sightingsResult = Result.success(emptyList()))
        val useCase = GetSightingsUseCase(repository)

        useCase(region, month = 4)

        assertEquals(region, repository.lastRegion)
        assertEquals(4, repository.lastMonth)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a month outside 1-12`() = runTest {
        val repository = FakeSightingsRepository(sightingsResult = Result.success(emptyList()))
        val useCase = GetSightingsUseCase(repository)

        useCase(region, month = 0)
    }
}
