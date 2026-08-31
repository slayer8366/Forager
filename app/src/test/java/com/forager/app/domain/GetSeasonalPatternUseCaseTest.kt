package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Composition test: the fetch (sightings, then historical weather) and the pure compute step, wired together. */
class GetSeasonalPatternUseCaseTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    private class FakeMushroomRepository(private val page: Result<SightingsPage>) : MushroomRepository {
        override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
            Result.success(emptyList<SpeciesObservationCount>())
        override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) = page
    }

    private class FakeHistoricalWeatherProvider(
        private val result: Result<List<DailyWeather>>,
    ) : HistoricalWeatherProvider {
        var lastRegion: Region? = null
        var lastFrom: LocalDate? = null
        var lastThrough: LocalDate? = null
        var callCount = 0

        override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> {
            callCount++
            lastRegion = region
            lastFrom = from
            lastThrough = through
            return result
        }
    }

    private fun sighting(id: Long, observedOn: LocalDate?) = Sighting(
        observationId = id,
        taxonId = 1L,
        scientificName = "Species",
        commonName = null,
        lat = region.lat,
        lng = region.lng,
        observedOn = observedOn,
        photoUrl = null,
    )

    @Test
    fun `fetches the historical weather padded back by FRUITING_LAG_DAYS_last from the earliest sighting through the latest`() = runTest {
        val sightings = listOf(
            sighting(1, LocalDate.of(2025, 8, 20)), // latest
            sighting(2, LocalDate.of(2025, 6, 1)), // earliest
            sighting(3, LocalDate.of(2025, 7, 10)),
        )
        val repository = FakeMushroomRepository(Result.success(SightingsPage(sightings, totalResults = 3)))
        val weatherProvider = FakeHistoricalWeatherProvider(Result.success(emptyList()))
        val useCase = GetSeasonalPatternUseCase(GetSightingsUseCase(repository), weatherProvider, ComputeFruitingLagDistributionUseCase())

        useCase(region, month = 8, TaxonFilter.FUNGI).getOrThrow()

        assertEquals(region, weatherProvider.lastRegion)
        assertEquals(LocalDate.of(2025, 6, 1).minusDays(FruitingPatternAssumptions.FRUITING_LAG_DAYS.last.toLong()), weatherProvider.lastFrom)
        assertEquals(LocalDate.of(2025, 8, 20), weatherProvider.lastThrough)
    }

    @Test
    fun `sightings with no observed date are excluded from the fetch-range calculation`() = runTest {
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 1)), sighting(2, null))
        val repository = FakeMushroomRepository(Result.success(SightingsPage(sightings, totalResults = 2)))
        val weatherProvider = FakeHistoricalWeatherProvider(Result.success(emptyList()))
        val useCase = GetSeasonalPatternUseCase(GetSightingsUseCase(repository), weatherProvider, ComputeFruitingLagDistributionUseCase())

        useCase(region, month = 8, TaxonFilter.FUNGI).getOrThrow()

        assertEquals(LocalDate.of(2025, 8, 1), weatherProvider.lastThrough)
    }

    @Test
    fun `no dated sightings at all skips the weather fetch entirely and still reports a distribution`() = runTest {
        val repository = FakeMushroomRepository(Result.success(SightingsPage(listOf(sighting(1, null)), totalResults = 1)))
        val weatherProvider = FakeHistoricalWeatherProvider(Result.success(emptyList()))
        val useCase = GetSeasonalPatternUseCase(GetSightingsUseCase(repository), weatherProvider, ComputeFruitingLagDistributionUseCase())

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI).getOrThrow()

        assertEquals(0, weatherProvider.callCount)
        assertEquals(1, distribution.observationsExcludedForMissingDate)
        assertEquals(1, distribution.totalResultsOnServer)
    }

    @Test
    fun `a sightings fetch failure propagates without calling the weather provider`() = runTest {
        val failure = IllegalStateException("network down")
        val repository = FakeMushroomRepository(Result.failure(failure))
        val weatherProvider = FakeHistoricalWeatherProvider(Result.success(emptyList()))
        val useCase = GetSeasonalPatternUseCase(GetSightingsUseCase(repository), weatherProvider, ComputeFruitingLagDistributionUseCase())

        val result = useCase(region, month = 8, TaxonFilter.FUNGI)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertEquals(0, weatherProvider.callCount)
    }

    @Test
    fun `a weather fetch failure is reported as a failure, not an empty distribution`() = runTest {
        val repository = FakeMushroomRepository(Result.success(SightingsPage(listOf(sighting(1, LocalDate.of(2025, 8, 1))), totalResults = 1)))
        val failure = IllegalStateException("archive api down")
        val weatherProvider = FakeHistoricalWeatherProvider(Result.failure(failure))
        val useCase = GetSeasonalPatternUseCase(GetSightingsUseCase(repository), weatherProvider, ComputeFruitingLagDistributionUseCase())

        val result = useCase(region, month = 8, TaxonFilter.FUNGI)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `the total_results from the sightings page reaches the distribution`() = runTest {
        val repository = FakeMushroomRepository(Result.success(SightingsPage(listOf(sighting(1, LocalDate.of(2025, 8, 1))), totalResults = 1847)))
        val weatherProvider = FakeHistoricalWeatherProvider(Result.success(emptyList()))
        val useCase = GetSeasonalPatternUseCase(GetSightingsUseCase(repository), weatherProvider, ComputeFruitingLagDistributionUseCase())

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI).getOrThrow()

        assertEquals(1847, distribution.totalResultsOnServer)
        assertNull(distribution.buckets.firstOrNull { it.count < 0 })
    }
}
