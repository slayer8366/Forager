package com.forager.app.domain.model

import java.time.LocalDate

/**
 * A single real iNaturalist observation with a mappable, non-obscured position.
 *
 * [lat]/[lng] are iNaturalist's own publicly-shown coordinates for a real (not randomized) point
 * — observations iNaturalist marks `obscured` are excluded entirely before a [Sighting] is ever
 * built for them, not plotted with a caveat. See
 * [com.forager.app.domain.MushroomRepository.getSightings]'s doc comment.
 */
data class Sighting(
    val observationId: Long,
    val taxonId: Long,
    val scientificName: String,
    val commonName: String?,
    val lat: Double,
    val lng: Double,
    val observedOn: LocalDate?,
    val photoUrl: String?,
    /**
     * Positional accuracy in metres of [lat]/[lng], iNaturalist's `public_positional_accuracy` —
     * ordinary GPS error, which can be hundreds of metres even for a non-obscured observation.
     * Null when iNaturalist reports no accuracy figure at all; the UI must say so explicitly
     * rather than omit the note, since a missing accuracy is not the same as a good one.
     */
    val positionalAccuracyMeters: Int? = null,
)
