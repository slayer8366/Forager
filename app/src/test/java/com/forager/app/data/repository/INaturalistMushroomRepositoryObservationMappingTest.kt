package com.forager.app.data.repository

import com.forager.app.data.remote.INaturalistApi
import com.forager.app.data.remote.dto.ObservationDto
import com.forager.app.data.remote.dto.ObservationsResponseDto
import com.forager.app.data.remote.dto.SpeciesCountsResponseDto
import com.forager.app.data.remote.dto.TaxonDto
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers `INaturalistMushroomRepository.toDomain(ObservationDto)`'s exclusion of obscured
 * observations and its carry-through of positional accuracy — exercised via the public
 * [INaturalistMushroomRepository.getSightings] rather than the private `toDomain` directly.
 *
 * Confirmed live against `api.inaturalist.org/v1/observations` before this was written: an
 * obscured observation still carries a parseable `location` (randomized to a coarse cell, tens of
 * kilometres across), `obscured=true`, and a `public_positional_accuracy` in the tens-of-thousands
 * of metres — while a non-obscured observation has `positional_accuracy ==
 * public_positional_accuracy` exactly, confirmed on real sampled data.
 */
private class FixedResponseApi(private val response: ObservationsResponseDto) : INaturalistApi {
    override suspend fun getSpeciesCounts(
        lat: Double,
        lng: Double,
        radiusKm: Int,
        month: Int,
        iconicTaxa: String?,
        taxonId: Long?,
        withoutTaxonId: Long?,
        verifiable: Boolean,
        perPage: Int,
    ): SpeciesCountsResponseDto = SpeciesCountsResponseDto()

    override suspend fun getObservations(
        lat: Double,
        lng: Double,
        radiusKm: Int,
        month: Int,
        iconicTaxa: String?,
        taxonId: Long?,
        withoutTaxonId: Long?,
        verifiable: Boolean,
        perPage: Int,
    ): ObservationsResponseDto = response
}

class INaturalistMushroomRepositoryObservationMappingTest {

    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
    private val taxon = TaxonDto(id = 49158L, name = "Hericium erinaceus", preferredCommonName = "lion's mane")

    private fun observation(
        id: Long,
        location: String? = "45.4,-122.6",
        obscured: Boolean = false,
        publicPositionalAccuracy: Int? = 10,
    ) = ObservationDto(
        id = id,
        taxon = taxon,
        location = location,
        obscured = obscured,
        publicPositionalAccuracy = publicPositionalAccuracy,
    )

    @Test
    fun `a non-obscured observation with a location is kept, with its accuracy carried through`() = runTest {
        val response = ObservationsResponseDto(
            totalResults = 1,
            results = listOf(observation(id = 1, obscured = false, publicPositionalAccuracy = 142)),
        )

        val page = INaturalistMushroomRepository(FixedResponseApi(response))
            .getSightings(region, month = 8, filter = TaxonFilter.FUNGI).getOrThrow()

        assertEquals(1, page.sightings.size)
        assertEquals(142, page.sightings[0].positionalAccuracyMeters)
    }

    @Test
    fun `an obscured observation is excluded even though it has a parseable location`() = runTest {
        val response = ObservationsResponseDto(
            totalResults = 1,
            // A real obscured response still carries a valid "lat,lng" -- confirmed live -- so this
            // fixture deliberately gives it one, to prove exclusion is driven by `obscured`, not by
            // an absent location.
            results = listOf(observation(id = 1, location = "45.4,-122.6", obscured = true, publicPositionalAccuracy = 28818)),
        )

        val page = INaturalistMushroomRepository(FixedResponseApi(response))
            .getSightings(region, month = 8, filter = TaxonFilter.FUNGI).getOrThrow()

        assertTrue(page.sightings.isEmpty())
        // The true count is unaffected -- the caller can still see one observation existed here,
        // it just isn't one this app will plot. See SightingsPage's own doc comment.
        assertEquals(1, page.totalResults)
    }

    @Test
    fun `an observation with no location at all is excluded, obscured flag notwithstanding`() = runTest {
        val response = ObservationsResponseDto(
            totalResults = 1,
            results = listOf(observation(id = 1, location = null, obscured = false)),
        )

        val page = INaturalistMushroomRepository(FixedResponseApi(response))
            .getSightings(region, month = 8, filter = TaxonFilter.FUNGI).getOrThrow()

        assertTrue(page.sightings.isEmpty())
    }

    @Test
    fun `a missing obscured field defaults to excluded, not silently included`() {
        // ObservationDto's own default -- no repository call needed, this is a deserialization-time
        // fact about the DTO itself. A response shape iNaturalist has never actually sent (every
        // sampled observation carries `obscured` explicitly) should still fail safe.
        val dto = ObservationDto(id = 1, taxon = taxon, location = "45.4,-122.6")
        assertTrue(dto.obscured)
    }

    @Test
    fun `absent positional accuracy is null, not zero or a fabricated value`() = runTest {
        val response = ObservationsResponseDto(
            totalResults = 1,
            results = listOf(observation(id = 1, obscured = false, publicPositionalAccuracy = null)),
        )

        val page = INaturalistMushroomRepository(FixedResponseApi(response))
            .getSightings(region, month = 8, filter = TaxonFilter.FUNGI).getOrThrow()

        assertEquals(1, page.sightings.size)
        assertNull(page.sightings[0].positionalAccuracyMeters)
    }

    @Test
    fun `a mixed batch keeps only the non-obscured, located observations`() = runTest {
        val response = ObservationsResponseDto(
            totalResults = 3,
            results = listOf(
                observation(id = 1, obscured = false),
                observation(id = 2, obscured = true),
                observation(id = 3, location = null),
            ),
        )

        val page = INaturalistMushroomRepository(FixedResponseApi(response))
            .getSightings(region, month = 8, filter = TaxonFilter.FUNGI).getOrThrow()

        assertEquals(listOf(1L), page.sightings.map { it.observationId })
    }
}
