package com.forager.app.domain.model

/**
 * A bare geographic position, with no notion of search radius or observation attached.
 *
 * Distinct from [Region] on purpose: [Region] is a *search area* the user chose, while this is
 * just a point — a sighting's location, or a computed cluster centre. Keeping them separate
 * stops "the centre of a foraging area" from being mistaken for "somewhere the app searched".
 */
data class LatLng(
    val lat: Double,
    val lng: Double,
)
