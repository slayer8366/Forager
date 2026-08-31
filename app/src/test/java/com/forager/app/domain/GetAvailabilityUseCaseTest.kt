package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
private const val MONTH = 8

private val COUNTS = listOf(
    SpeciesObservationCount(
        taxonId = 48473L,
        scientificName = "Ganoderma applanatum",
        commonName = "artist's bracket",
        rank = "species",
        observationCount = 14,
        photoUrl = null,
        wikipediaUrl = null,
    ),
    SpeciesObservationCount(
        taxonId = 120242L,
        scientificName = "Cyathus striatus",
        commonName = "fluted bird's nest fungus",
        rank = "species",
        observationCount = 7,
        photoUrl = null,
        wikipediaUrl = null,
    ),
)

/**
 * A repository whose species-counts answer the test sets: either the counts above or one specific
 * exception instance, so a test can assert the failure that comes out is *that* object rather than
 * merely some failure.
 */
private class ScriptedMushroomRepository(
    var speciesCounts: Result<List<SpeciesObservationCount>> = Result.success(COUNTS),
) : MushroomRepository {
    var speciesCountsCallCount: Int = 0
        private set

    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter): Result<List<SpeciesObservationCount>> {
        speciesCountsCallCount++
        return speciesCounts
    }

    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage> =
        Result.failure(UnsupportedOperationException("sightings are not part of the cached search"))
}

/**
 * [GetAvailabilityUseCase]'s three outcomes, each asserted on what actually comes back rather than
 * on "it worked" / "it failed": which subtype of [AvailabilitySearchResult], carrying which
 * forecast and which timestamp, and — on the miss path — the identical [Throwable] the live search
 * produced.
 */
class GetAvailabilityUseCaseTest {

    private val remote = ScriptedMushroomRepository()
    private val clock = MutableClock(now = 1_000L)
    private val cache = InMemorySearchCacheRepository(clock)
    private val useCase = GetAvailabilityUseCase(PredictAvailabilityUseCase(remote), cache)

    @Test
    fun `a live search returns Live and writes the forecast through to the cache`() = runTest {
        val result = useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow()

        val live = result as AvailabilitySearchResult.Live
        assertEquals(listOf("Ganoderma applanatum", "Cyathus striatus"), live.forecast.entries.map { it.species.scientificName })
        assertEquals(1.0f, live.forecast.entries.first().relativeLikelihood, 0.0001f)
        assertEquals(1, cache.saveCount)

        // The written entry is the same forecast, retrievable under this exact search.
        val cached = cache.getCached(REGION, MONTH, TaxonFilter.FUNGI)
        assertNotNull(cached)
        assertEquals(live.forecast, cached!!.forecast)
        assertEquals(1_000L, cached.cachedAtEpochMillis)
    }

    @Test
    fun `a failed live search with a cached result returns Cached with the time it was cached`() = runTest {
        // Populate the cache from a live search made at t=1000, then fail the network.
        val storedForecast = (useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow() as AvailabilitySearchResult.Live).forecast
        clock.now = 5_000L
        remote.speciesCounts = Result.failure(IOException("Unable to resolve host api.inaturalist.org"))

        val result = useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow()

        val cached = result as AvailabilitySearchResult.Cached
        assertEquals(storedForecast, cached.forecast)
        // The stamp is when the result was fetched (t=1000 on this clock), not when it was read
        // back at t=5000 — the banner reports the result's age, not the moment it was replayed.
        assertEquals(1_000L, cached.cachedAtEpochMillis)
    }

    @Test
    fun `a failed live search with nothing cached returns the original failure unchanged`() = runTest {
        val networkFailure = IOException("Unable to resolve host api.inaturalist.org")
        remote.speciesCounts = Result.failure(networkFailure)

        val result = useCase(REGION, MONTH, TaxonFilter.FUNGI)

        assertTrue(result.isFailure)
        // The same exception object, not a cache-miss error invented in its place: what the user
        // needs told is why the search failed.
        assertSame(networkFailure, result.exceptionOrNull())
    }

    /**
     * The cache is keyed on the exact search. A cached August/Fungi result must not answer a
     * September one — see [SearchCacheRepository]'s doc comment on why nothing here is fuzzy.
     */
    @Test
    fun `a cached result for another month does not answer this month's failed search`() = runTest {
        useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow()
        val networkFailure = IOException("Unable to resolve host api.inaturalist.org")
        remote.speciesCounts = Result.failure(networkFailure)

        val otherMonth = useCase(REGION, MONTH + 1, TaxonFilter.FUNGI)
        val otherFilter = useCase(REGION, MONTH, TaxonFilter.PLANTS)
        val otherRegion = useCase(REGION.copy(radiusKm = REGION.radiusKm + 1), MONTH, TaxonFilter.FUNGI)

        assertSame(networkFailure, otherMonth.exceptionOrNull())
        assertSame(networkFailure, otherFilter.exceptionOrNull())
        assertSame(networkFailure, otherRegion.exceptionOrNull())
    }

    /**
     * The live search is always attempted first, including when a perfectly good cached copy
     * exists: this is a fallback, not a read-through cache that would let the ranked list go stale
     * while the network was fine.
     */
    @Test
    fun `a cached result does not stop the next search asking the network`() = runTest {
        useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow()
        assertEquals(1, remote.speciesCountsCallCount)

        val second = useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow()

        assertEquals(2, remote.speciesCountsCallCount)
        assertTrue(second is AvailabilitySearchResult.Live)
    }

    /**
     * An empty ranking is a real answer ("no verifiable observations here this month"), and the UI
     * renders it as one — so it is cached and replayed like any other, not treated as nothing.
     */
    @Test
    fun `an empty ranking is cached and replayed as an empty Cached result`() = runTest {
        remote.speciesCounts = Result.success(emptyList())
        useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow()
        remote.speciesCounts = Result.failure(IOException("offline"))

        val result = useCase(REGION, MONTH, TaxonFilter.FUNGI).getOrThrow()

        val cached = result as AvailabilitySearchResult.Cached
        assertTrue(cached.forecast.entries.isEmpty())
        assertEquals(REGION, cached.forecast.region)
        assertEquals(TaxonFilter.FUNGI, cached.forecast.filter)
    }


    /** Nothing is written when the live search fails — a failure must not overwrite a good copy. */
    @Test
    fun `a failed live search writes nothing to the cache`() = runTest {
        remote.speciesCounts = Result.failure(IOException("offline"))

        useCase(REGION, MONTH, TaxonFilter.FUNGI)

        assertEquals(0, cache.saveCount)
        assertNull(cache.getCached(REGION, MONTH, TaxonFilter.FUNGI))
    }
}
