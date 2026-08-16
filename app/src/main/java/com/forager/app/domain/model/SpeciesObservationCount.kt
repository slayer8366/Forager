package com.forager.app.domain.model

/** How many verifiable iNaturalist observations of one taxon matched a region+month query. */
data class SpeciesObservationCount(
    val taxonId: Long,
    val scientificName: String,
    val commonName: String?,
    val rank: String?,
    val observationCount: Int,
    val photoUrl: String?,
    val wikipediaUrl: String?,
)
