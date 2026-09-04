package com.forager.app.data.remote

import com.forager.app.data.remote.dto.ObservationsResponseDto
import com.forager.app.data.remote.dto.SpeciesCountsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Raw Retrofit surface for the iNaturalist REST API (https://api.inaturalist.org/v1/).
 *
 * This interface is the only place in the app that speaks Retrofit/iNaturalist's wire
 * format directly. Domain and UI code depend on [com.forager.app.domain.MushroomRepository]
 * instead, per the isolated-integration-layer rule in CLAUDE.md.
 *
 * [iconicTaxa] and [taxonId] are mutually exclusive per call: pass one and leave the other
 * null (a null @Query parameter is simply omitted by Retrofit). Exactly one is non-null for
 * every call this app makes; see [com.forager.app.domain.model.TaxonFilter].
 *
 * [withoutTaxonId] subtracts a taxon (and its descendants) from whichever of those two
 * selected the results. Verified against the live API before being relied on, because
 * iNaturalist answers 200 and silently ignores a parameter it doesn't recognise — a
 * misspelling here would look exactly like a working filter that changes nothing.
 */
interface INaturalistApi {

    /**
     * Ranks taxa by how many verifiable observations exist for the given filters.
     * Used as the availability signal: a species observed often in this place during
     * this month, across past years, is more likely to be found there again this month.
     */
    @GET("observations/species_counts")
    suspend fun getSpeciesCounts(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusKm: Int,
        @Query("month") month: Int,
        @Query("iconic_taxa") iconicTaxa: String? = null,
        @Query("taxon_id") taxonId: Long? = null,
        @Query("without_taxon_id") withoutTaxonId: Long? = null,
        @Query("verifiable") verifiable: Boolean = true,
        @Query("per_page") perPage: Int = 30,
    ): SpeciesCountsResponseDto

    /** Individual observation records (position, taxon, date, photo) for map pins. */
    @GET("observations")
    suspend fun getObservations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusKm: Int,
        @Query("month") month: Int,
        @Query("iconic_taxa") iconicTaxa: String? = null,
        @Query("taxon_id") taxonId: Long? = null,
        @Query("without_taxon_id") withoutTaxonId: Long? = null,
        @Query("verifiable") verifiable: Boolean = true,
        @Query("per_page") perPage: Int = 200,
    ): ObservationsResponseDto
}
