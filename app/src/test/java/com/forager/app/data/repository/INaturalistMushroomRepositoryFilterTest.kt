package com.forager.app.data.repository

import com.forager.app.data.remote.INaturalistApi
import com.forager.app.data.remote.dto.ObservationsResponseDto
import com.forager.app.data.remote.dto.SpeciesCountsResponseDto
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers how a [TaxonFilter] reaches the wire as iNaturalist query parameters — in
 * particular that Fungi carries the Lecanoromycetes exclusion and that nothing else does.
 *
 * The parameter itself was verified against the live API before this was written:
 * `without_taxon_id=54743` cut the reference search from 152 species to 113 on
 * species_counts and 462 observations to 358, dropping *Xanthoria parietina*,
 * *Phlyctis argena* and *Parmelia sulcata*. A deliberately misspelled parameter name
 * returned the unfiltered 462, which is why these tests assert on the value actually
 * handed to the API rather than trusting the request to be meaningful.
 */
private class RecordingApi : INaturalistApi {
    var lastIconicTaxa: String? = null
    var lastTaxonId: Long? = null
    var lastWithoutTaxonId: Long? = null
    var callCount = 0

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
    ): SpeciesCountsResponseDto {
        record(iconicTaxa, taxonId, withoutTaxonId)
        return SpeciesCountsResponseDto()
    }

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
    ): ObservationsResponseDto {
        record(iconicTaxa, taxonId, withoutTaxonId)
        return ObservationsResponseDto()
    }

    private fun record(iconicTaxa: String?, taxonId: Long?, withoutTaxonId: Long?) {
        lastIconicTaxa = iconicTaxa
        lastTaxonId = taxonId
        lastWithoutTaxonId = withoutTaxonId
        callCount++
    }
}

class INaturalistMushroomRepositoryFilterTest {

    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

    @Test
    fun `Fungi species counts exclude Lecanoromycetes`() = runTest {
        val api = RecordingApi()

        INaturalistMushroomRepository(api).getSpeciesCounts(region, month = 8, filter = TaxonFilter.FUNGI)

        assertEquals(1, api.callCount)
        assertEquals("Fungi", api.lastIconicTaxa)
        assertNull(api.lastTaxonId)
        assertEquals(54743L, api.lastWithoutTaxonId)
    }

    @Test
    fun `Fungi sightings exclude Lecanoromycetes`() = runTest {
        val api = RecordingApi()

        INaturalistMushroomRepository(api).getSightings(region, month = 8, filter = TaxonFilter.FUNGI)

        assertEquals(1, api.callCount)
        assertEquals("Fungi", api.lastIconicTaxa)
        assertNull(api.lastTaxonId)
        assertEquals(54743L, api.lastWithoutTaxonId)
    }

    @Test
    fun `Plants exclude nothing on either endpoint`() = runTest {
        val counts = RecordingApi()
        val sightings = RecordingApi()

        INaturalistMushroomRepository(counts).getSpeciesCounts(region, month = 8, filter = TaxonFilter.PLANTS)
        INaturalistMushroomRepository(sightings).getSightings(region, month = 8, filter = TaxonFilter.PLANTS)

        assertEquals("Plantae", counts.lastIconicTaxa)
        assertNull(counts.lastWithoutTaxonId)
        assertEquals("Plantae", sightings.lastIconicTaxa)
        assertNull(sightings.lastWithoutTaxonId)
    }

    /** The Lichens chip is the opt-in: it must still select Lecanoromycetes, not subtract it. */
    @Test
    fun `Lichens select Lecanoromycetes and exclude nothing`() = runTest {
        val counts = RecordingApi()
        val sightings = RecordingApi()

        INaturalistMushroomRepository(counts).getSpeciesCounts(region, month = 8, filter = TaxonFilter.LICHENS)
        INaturalistMushroomRepository(sightings).getSightings(region, month = 8, filter = TaxonFilter.LICHENS)

        assertEquals(54743L, counts.lastTaxonId)
        assertNull(counts.lastIconicTaxa)
        assertNull(counts.lastWithoutTaxonId)
        assertEquals(54743L, sightings.lastTaxonId)
        assertNull(sightings.lastIconicTaxa)
        assertNull(sightings.lastWithoutTaxonId)
    }

    /**
     * Searching for a lichen by name must return it. An exclusion that overrode an
     * explicit species choice would silently answer a different question than the one asked.
     */
    @Test
    fun `a searched-for specific taxon excludes nothing`() = runTest {
        val api = RecordingApi()
        val xanthoriaParietina = TaxonFilter.SpecificTaxon(taxonId = 55576L, label = "Common Sunburst Lichen")

        INaturalistMushroomRepository(api).getSpeciesCounts(region, month = 8, filter = xanthoriaParietina)

        assertEquals(55576L, api.lastTaxonId)
        assertNull(api.lastIconicTaxa)
        assertNull(api.lastWithoutTaxonId)
    }

    /** The exclusion and the Lichens chip read the same constant, so they cannot drift apart. */
    @Test
    fun `Fungi exclusion and the Lichens chip name the same taxon`() {
        assertEquals(TaxonFilter.LICHENS.taxonId, TaxonFilter.FUNGI.excludedTaxonId)
    }
}
