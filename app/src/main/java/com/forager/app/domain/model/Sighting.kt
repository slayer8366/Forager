package com.forager.app.domain.model

import java.time.LocalDate

/** A single real iNaturalist observation with a mappable position. */
data class Sighting(
    val observationId: Long,
    val taxonId: Long,
    val scientificName: String,
    val commonName: String?,
    val lat: Double,
    val lng: Double,
    val observedOn: LocalDate?,
    val photoUrl: String?,
)
