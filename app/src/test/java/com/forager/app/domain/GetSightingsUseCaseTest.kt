package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private class FakeSightingsRepository(
    private val sightingsResult: Result<List<Sighting>>,
) : MushroomRepository {
    var lastRegion: Region? = null
    var lastMonth: Int? = null
    var lastFilter: TaxonFilter? = null

    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) = Result.success(emptyList<com.forager.app.domain.model.SpeciesObservationCount>())

    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<List<Sighting>> {
        lastRegion = region
        lastMonth = month
        lastFilter = filter
        return sightingsResult
    }

    override suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>> {
        throw NotImplementedError("not exercised by GetSightingsUseCaseTest")
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

        val result = useCase(region, month = 9, filter = TaxonFilter.FUNGI).getOrThrow()

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

        val result = useCase(region, month = 9, filter = TaxonFilter.FUNGI).getOrThrow()

        assertEquals(listOf(2L, 1L), result.map { it.observationId })
    }

    @Test
    fun `repository failure propagates as failure`() = runTest {
        val failure = IllegalStateException("network down")
        val repository = FakeSightingsRepository(sightingsResult = Result.failure(failure))
        val useCase = GetSightingsUseCase(repository)

        val result = useCase(region, month = 9, filter = TaxonFilter.FUNGI)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `passes the requested region, month, and filter through unchanged`() = runTest {
        val repository = FakeSightingsRepository(sightingsResult = Result.success(emptyList()))
        val useCase = GetSightingsUseCase(repository)

        useCase(region, month = 4, filter = TaxonFilter.LICHENS)

        assertEquals(region, repository.lastRegion)
        assertEquals(4, repository.lastMonth)
        assertEquals(TaxonFilter.LICHENS, repository.lastFilter)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a month outside 1-12`() = runTest {
        val repository = FakeSightingsRepository(sightingsResult = Result.success(emptyList()))
        val useCase = GetSightingsUseCase(repository)

        useCase(region, month = 0, filter = TaxonFilter.FUNGI)
    }
}
