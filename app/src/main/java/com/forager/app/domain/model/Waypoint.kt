package com.forager.app.domain.model

/**
 * A single named point the user marked — a trailhead, a parking spot, a spot worth returning to —
 * independent of any [Track]. Phase 1a builds the schema and storage only; dropping one from the
 * map (the same [com.forager.app.ui.map.CentrePinLocationPicker] idiom this app uses for a planned
 * trip or a mushroom-log find) is Phase 1c work, once the map layer it needs is on the new renderer.
 *
 * [altitude] is `null` whenever the fix it was created from didn't report one, same rule as
 * [TrackPoint.altitude] and [com.forager.app.domain.LocationResult.Success.altitude].
 */
data class Waypoint(
    val id: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val name: String,
    val note: String,
    val createdAtEpochMillis: Long,
)
