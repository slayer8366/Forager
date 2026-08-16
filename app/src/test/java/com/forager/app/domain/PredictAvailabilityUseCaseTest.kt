package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SpeciesObservationCount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMushroomRepository(
    private val result: Result<List<SpeciesObservationCount>>,
) : MushroomRepository {
    var lastRegion: Region? = null
    var lastMonth: Int? = null

    override suspend fun getSpeciesCounts(region: Region, month: Int): Result<List<SpeciesObservationCount>> {
        lastRegion = region
        lastMonth = month
        return result
    }

    override suspend fun getSightings(region: Region, month: Int): Result<List<Sighting>> {
        throw NotImplementedError("not exercised by PredictAvailabilityUseCaseTest")
    }
}

private fun species(id: Long, name: String, count: Int) = SpeciesObservationCount(
    taxonId = id,
    scientificName = name,
    commonName = null,
    rank = "species",
    observationCount = count,
    photoUrl = null,
    wikipediaUrl = null,
)

class PredictAvailabilityUseCaseTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    @Test
    fun `ranks species by observation count descending`() = runTest {
        val repository = FakeMushroomRepository(
            Result.success(
                listOf(
                    species(1, "Amanita muscaria", count = 5),
                    species(2, "Boletus edulis", count = 40),
                    species(3, "Cantharellus cibarius", count = 20),
                ),
            ),
        )
        val useCase = PredictAvailabilityUseCase(repository)

        val forecast = useCase(region, month = 9).getOrThrow()

        assertEquals(
            listOf("Boletus edulis", "Cantharellus cibarius", "Amanita muscaria"),
            forecast.entries.map { it.species.scientificName },
        )
    }

    @Test
    fun `top species has relative likelihood of 1 and others scale proportionally`() = runTest {
        val repository = FakeMushroomRepository(
            Result.success(
                listOf(
                    species(1, "Boletus edulis", count = 40),
                    species(2, "Cantharellus cibarius", count = 10),
                ),
            ),
        )
        val useCase = PredictAvailabilityUseCase(repository)

        val forecast = useCase(region, month = 9).getOrThrow()

        assertEquals(1f, forecast.entries[0].relativeLikelihood, 0.0001f)
        assertEquals(0.25f, forecast.entries[1].relativeLikelihood, 0.0001f)
    }

    @Test
    fun `empty results produce an empty forecast without dividing by zero`() = runTest {
        val repository = FakeMushroomRepository(Result.success(emptyList()))
        val useCase = PredictAvailabilityUseCase(repository)

        val forecast = useCase(region, month = 9).getOrThrow()

        assertTrue(forecast.entries.isEmpty())
        assertEquals(0, forecast.totalObservationsConsidered)
    }

    @Test
    fun `repository failure propagates as failure, not a fabricated empty result`() = runTest {
        val failure = IllegalStateException("network down")
        val repository = FakeMushroomRepository(Result.failure(failure))
        val useCase = PredictAvailabilityUseCase(repository)

        val result = useCase(region, month = 9)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `passes the requested region and month through unchanged`() = runTest {
        val repository = FakeMushroomRepository(Result.success(emptyList()))
        val useCase = PredictAvailabilityUseCase(repository)

        useCase(region, month = 3)

        assertEquals(region, repository.lastRegion)
        assertEquals(3, repository.lastMonth)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a month outside 1-12`() = runTest {
        val repository = FakeMushroomRepository(Result.success(emptyList()))
        val useCase = PredictAvailabilityUseCase(repository)

        useCase(region, month = 13)
    }
}
