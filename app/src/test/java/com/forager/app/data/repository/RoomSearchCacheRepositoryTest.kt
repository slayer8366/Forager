package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.domain.MutableClock
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [RoomSearchCacheRepository] against a real, in-memory Room database — not a fake — for the same
 * reason [RoomPlannedTripRepositoryTest] is: what is worth verifying here is the part Room and the
 * serialization actually own. A ranked list goes out through a JSON column and comes back as
 * domain objects; a [TaxonFilter] is decomposed into columns and rebuilt; the key encoding decides
 * what counts as the same search; and eviction is a transaction over rows. A hand-written fake
 * would echo back whatever this test assumed about all four (CLAUDE.md: assert on actual output,
 * not a proxy for it).
 *
 * The clock is a [MutableClock] rather than the system one, so "least recently used" is an order
 * this test states rather than one it races.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomSearchCacheRepositoryTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomSearchCacheRepository
    private val clock = MutableClock(now = 1_000L)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        repository = RoomSearchCacheRepository(database.cachedSearchDao(), clock)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun `a saved forecast comes back with its entries, filter and fetch time intact`() = runTest {
        val saved = forecast(month = 8, filter = TaxonFilter.FUNGI)

        repository.save(saved)
        val cached = repository.getCached(REGION, 8, TaxonFilter.FUNGI)

        assertNotNull(cached)
        assertEquals(saved, cached!!.forecast)
        assertEquals(1_000L, cached.cachedAtEpochMillis)
        // Spelled out rather than left to data-class equality above: the JSON column is the part
        // that could plausibly lose a field and still compare equal on the ones it kept.
        val top = cached.forecast.entries.first()
        assertEquals(48473L, top.species.taxonId)
        assertEquals("artist's bracket", top.species.commonName)
        assertEquals("species", top.species.rank)
        assertEquals(14, top.species.observationCount)
        assertEquals("https://example.invalid/photo.jpg", top.species.photoUrl)
        assertEquals("https://en.wikipedia.org/wiki/Ganoderma_applanatum", top.species.wikipediaUrl)
        assertEquals(1.0f, top.relativeLikelihood, 0.0001f)
        assertNull(cached.forecast.entries[1].species.commonName)
    }

    /** A [TaxonFilter.SpecificTaxon] must rebuild as one, not as a category with a matching label. */
    @Test
    fun `a specific-taxon search round-trips as a specific-taxon filter`() = runTest {
        val filter = TaxonFilter.SpecificTaxon(taxonId = 47347L, label = "Pleurotus ostreatus")

        repository.save(forecast(month = 8, filter = filter))
        val cached = repository.getCached(REGION, 8, filter)

        assertEquals(filter, cached!!.forecast.filter)
    }

    /** And an iconic category must keep the exclusion that makes Fungi mean "without lichens". */
    @Test
    fun `an iconic-category search round-trips with its excluded taxon`() = runTest {
        repository.save(forecast(month = 8, filter = TaxonFilter.FUNGI))

        val cached = repository.getCached(REGION, 8, TaxonFilter.FUNGI)

        assertEquals(TaxonFilter.FUNGI, cached!!.forecast.filter)
        assertEquals(TaxonFilter.FUNGI.excludedTaxonId, cached.forecast.filter.excludedTaxonId)
    }

    /**
     * Matching is exact equality on region + month + filter. Each of the four things a key encodes
     * is varied on its own here, so a key that silently dropped one of them would be caught.
     */
    @Test
    fun `a search differing in region, month or filter is a miss, not a near match`() = runTest {
        repository.save(forecast(month = 8, filter = TaxonFilter.FUNGI))

        assertNull(repository.getCached(REGION.copy(lat = REGION.lat + 0.001), 8, TaxonFilter.FUNGI))
        assertNull(repository.getCached(REGION.copy(lng = REGION.lng + 0.001), 8, TaxonFilter.FUNGI))
        assertNull(repository.getCached(REGION.copy(radiusKm = 16), 8, TaxonFilter.FUNGI))
        assertNull(repository.getCached(REGION, 9, TaxonFilter.FUNGI))
        assertNull(repository.getCached(REGION, 8, TaxonFilter.PLANTS))
        assertNull(repository.getCached(REGION, 8, TaxonFilter.LICHENS))
        // The one that must still hit, so the misses above are about the differences and not about
        // the store being empty.
        assertNotNull(repository.getCached(REGION, 8, TaxonFilter.FUNGI))
    }

    @Test
    fun `re-saving the same search replaces its row rather than adding a second`() = runTest {
        repository.save(forecast(month = 8, filter = TaxonFilter.FUNGI))
        clock.now = 2_000L
        repository.save(forecast(month = 8, filter = TaxonFilter.FUNGI, topCount = 99))

        val recent = repository.getRecent()
        assertEquals(1, recent.size)
        assertEquals(2_000L, recent.single().cachedAtEpochMillis)
        assertEquals(99, repository.getCached(REGION, 8, TaxonFilter.FUNGI)!!.forecast.entries.first().species.observationCount)
    }

    @Test
    fun `the sixth distinct search evicts the least recently used one`() = runTest {
        (1..5).forEach { month ->
            clock.now = month * 1_000L
            repository.save(forecast(month = month, filter = TaxonFilter.FUNGI))
        }

        clock.now = 6_000L
        repository.save(forecast(month = 6, filter = TaxonFilter.FUNGI))

        assertNull("The oldest search should have been evicted", repository.getCached(REGION, 1, TaxonFilter.FUNGI))
        (2..6).forEach { month ->
            assertNotNull("Month $month should still be cached", repository.getCached(REGION, month, TaxonFilter.FUNGI))
        }
    }

    /**
     * Reading a cached search counts as using it. Without that bump this is a
     * least-recently-*written* cache, and the search somebody keeps reopening offline is exactly
     * the one that would be thrown away.
     */
    @Test
    fun `reading a cached search moves it out of the eviction firing line`() = runTest {
        (1..5).forEach { month ->
            clock.now = month * 1_000L
            repository.save(forecast(month = month, filter = TaxonFilter.FUNGI))
        }

        // Month 1 is the oldest write, but it is read here — so month 2 becomes the least
        // recently used and is what the sixth search should evict.
        clock.now = 5_500L
        assertNotNull(repository.getCached(REGION, 1, TaxonFilter.FUNGI))

        clock.now = 6_000L
        repository.save(forecast(month = 6, filter = TaxonFilter.FUNGI))

        assertNotNull("The search that was just read should have survived", repository.getCached(REGION, 1, TaxonFilter.FUNGI))
        assertNull("The least recently used search should have been evicted", repository.getCached(REGION, 2, TaxonFilter.FUNGI))
    }

    /** Reading must not re-date the result: the age on screen is when it was fetched. */
    @Test
    fun `reading a cached search does not change the fetch time it reports`() = runTest {
        repository.save(forecast(month = 8, filter = TaxonFilter.FUNGI))

        clock.now = 90_000L
        val cached = repository.getCached(REGION, 8, TaxonFilter.FUNGI)

        assertEquals(1_000L, cached!!.cachedAtEpochMillis)
        assertEquals(1_000L, repository.getRecent().single().cachedAtEpochMillis)
    }

    @Test
    fun `getRecent lists searches most recently used first, with their region, month and filter`() = runTest {
        clock.now = 1_000L
        repository.save(forecast(month = 3, filter = TaxonFilter.FUNGI))
        clock.now = 2_000L
        repository.save(forecast(month = 4, filter = TaxonFilter.PLANTS))
        clock.now = 3_000L
        repository.save(forecast(month = 5, filter = TaxonFilter.LICHENS))

        val recent = repository.getRecent()

        assertEquals(listOf(5, 4, 3), recent.map { it.month })
        assertEquals(
            listOf(TaxonFilter.LICHENS, TaxonFilter.PLANTS, TaxonFilter.FUNGI),
            recent.map { it.filter },
        )
        assertEquals(listOf(3_000L, 2_000L, 1_000L), recent.map { it.cachedAtEpochMillis })
        assertTrue(recent.all { it.region == REGION })
    }

    @Test
    fun `getRecent reorders when a cached search is read`() = runTest {
        clock.now = 1_000L
        repository.save(forecast(month = 3, filter = TaxonFilter.FUNGI))
        clock.now = 2_000L
        repository.save(forecast(month = 4, filter = TaxonFilter.FUNGI))
        assertEquals(listOf(4, 3), repository.getRecent().map { it.month })

        clock.now = 3_000L
        repository.getCached(REGION, 3, TaxonFilter.FUNGI)

        assertEquals(listOf(3, 4), repository.getRecent().map { it.month })
    }

    @Test
    fun `getRecent on an empty cache is an empty list, not a failure`() = runTest {
        assertTrue(repository.getRecent().isEmpty())
    }

    /**
     * The degradation contract from [com.forager.app.domain.SearchCacheRepository]: a storage
     * failure must behave as "nothing is cached" rather than throwing, because an exception out of
     * here would reach the user as a failed search.
     *
     * Provoked with real broken rows — one whose JSON column is not what
     * [com.forager.app.data.local.CachedSearchPayload] wrote, and one storing neither filter
     * discriminant — inserted through the real DAO, rather than a fake repository returning
     * whatever this test wanted. Reading either throws inside the mapping; what these assert is
     * that the read still comes back as "nothing cached" (and, per the repository's doc comment,
     * is logged rather than dropped in silence).
     *
     * **Not covered here:** the same contract for [RoomSearchCacheRepository.save], and for a
     * database that cannot be opened at all. Closing the database was tried as the provocation and
     * abandoned: Room 2.8.4 cancels its own coroutine scope inside `close()`, and that cancellation
     * surfaces as a `JobCancellationException` failing the test method itself, before any assertion
     * runs — an artifact of Room's lifecycle under `runTest`, not behaviour of the code under test.
     */
    @Test
    fun `an unreadable cached row degrades to a miss instead of throwing`() = runTest {
        repository.save(forecast(month = 8, filter = TaxonFilter.FUNGI))
        val stored = database.cachedSearchDao().getAllOrderedByLastAccessed().single()
        database.cachedSearchDao().upsert(stored.copy(entriesJson = "{not the payload we wrote}"))

        assertNull(repository.getCached(REGION, 8, TaxonFilter.FUNGI))
    }

    /**
     * A row that stores neither filter discriminant cannot be turned back into a [TaxonFilter] —
     * see `RoomSearchCacheRepository.toFilter` — and [RoomSearchCacheRepository.getRecent] maps
     * every row, so this is the one broken row that takes the whole picker with it. It must still
     * come back as an empty list rather than throwing into the ViewModel's start-up load.
     */
    @Test
    fun `a row with no filter discriminant degrades to no recent searches instead of throwing`() = runTest {
        repository.save(forecast(month = 8, filter = TaxonFilter.FUNGI))
        val stored = database.cachedSearchDao().getAllOrderedByLastAccessed().single()
        database.cachedSearchDao().upsert(
            stored.copy(filterIconicTaxonName = null, filterTaxonId = null, filterExcludedTaxonId = null),
        )

        assertTrue(repository.getRecent().isEmpty())
        assertNull(repository.getCached(REGION, 8, TaxonFilter.FUNGI))
    }

    private companion object {
        val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

        fun forecast(month: Int, filter: TaxonFilter, topCount: Int = 14) = AvailabilityForecast(
            region = REGION,
            month = month,
            filter = filter,
            entries = listOf(
                AvailabilityEntry(
                    species = SpeciesObservationCount(
                        taxonId = 48473L,
                        scientificName = "Ganoderma applanatum",
                        commonName = "artist's bracket",
                        rank = "species",
                        observationCount = topCount,
                        photoUrl = "https://example.invalid/photo.jpg",
                        wikipediaUrl = "https://en.wikipedia.org/wiki/Ganoderma_applanatum",
                    ),
                    relativeLikelihood = 1.0f,
                ),
                // Nulls in every nullable field, so the JSON round trip is exercised on them too.
                AvailabilityEntry(
                    species = SpeciesObservationCount(
                        taxonId = 120242L,
                        scientificName = "Cyathus striatus",
                        commonName = null,
                        rank = null,
                        observationCount = 7,
                        photoUrl = null,
                        wikipediaUrl = null,
                    ),
                    relativeLikelihood = 0.5f,
                ),
            ),
        )
    }
}
