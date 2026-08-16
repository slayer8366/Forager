package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/taxa/autocomplete.
 * https://api.inaturalist.org/v1/docs/#!/Taxa/get_taxa_autocomplete
 */
@Serializable
data class TaxaAutocompleteResponseDto(
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("results") val results: List<TaxonDto> = emptyList(),
)
