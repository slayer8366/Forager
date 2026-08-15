package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/observations/species_counts.
 * https://api.inaturalist.org/v1/docs/#!/Observations/get_observations_species_counts
 */
@Serializable
data class SpeciesCountsResponseDto(
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("page") val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("results") val results: List<SpeciesCountDto> = emptyList(),
)

@Serializable
data class SpeciesCountDto(
    @SerialName("count") val count: Int,
    @SerialName("taxon") val taxon: TaxonDto,
)

@Serializable
data class TaxonDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("preferred_common_name") val preferredCommonName: String? = null,
    @SerialName("rank") val rank: String? = null,
    @SerialName("wikipedia_url") val wikipediaUrl: String? = null,
    @SerialName("observations_count") val observationsCount: Int? = null,
    @SerialName("default_photo") val defaultPhoto: PhotoDto? = null,
)

@Serializable
data class PhotoDto(
    @SerialName("square_url") val squareUrl: String? = null,
    @SerialName("medium_url") val mediumUrl: String? = null,
)
