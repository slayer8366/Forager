package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/observations.
 * https://api.inaturalist.org/v1/docs/#!/Observations/get_observations
 */
@Serializable
data class ObservationsResponseDto(
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("page") val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("results") val results: List<ObservationDto> = emptyList(),
)

@Serializable
data class ObservationDto(
    @SerialName("id") val id: Long,
    @SerialName("taxon") val taxon: TaxonDto,
    /** "lat,lng" if present. Null or absent when iNaturalist withholds the location (e.g. conservation-sensitive taxa). */
    @SerialName("location") val location: String? = null,
    @SerialName("observed_on") val observedOn: String? = null,
    @SerialName("photos") val photos: List<ObservationPhotoDto> = emptyList(),
)

@Serializable
data class ObservationPhotoDto(
    @SerialName("url") val url: String? = null,
)
