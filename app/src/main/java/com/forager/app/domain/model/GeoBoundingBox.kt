package com.forager.app.domain.model

/**
 * A rectangle in lat/lng degrees, this project's own type rather than osmdroid's `BoundingBox` —
 * the same seam [Region]/[LatLng] already draw around vendor geometry types. Produced by
 * [com.forager.app.domain.GeoDistance.boundingBox]; the one place it becomes an osmdroid
 * `BoundingBox` is `OsmdroidOfflineMapRepository`.
 *
 * [east] can be numerically less than [west] when the box crosses the antimeridian (a search
 * centred near longitude ±180°) — callers that hand this to a library expecting `west < east`
 * must account for that rather than assuming a simple ordering.
 */
data class GeoBoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)
