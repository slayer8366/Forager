package com.zynergylabs.forager.app.domain.model

/**
 * A single named point the user marked — a trailhead, a parking spot, a spot worth returning to —
 * independent of any [Track]. Phase 1a builds the schema and storage only; dropping one from the
 * map (the same [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker] idiom this app uses for a planned
 * trip or a mushroom-log find) is Phase 1c work, once the map layer it needs is on the new renderer.
 *
 * [altitude] is `null` whenever the fix it was created from didn't report one, same rule as
 * [TrackPoint.altitude] and [com.zynergylabs.forager.app.domain.LocationResult.Success.altitude].
 *
 * [trackId] — HUD-foundations dispatch, Item 3 (owner decision): the [Track] this waypoint was
 * dropped **while recording**, or `null` for a waypoint dropped with no recording running. The
 * richer meaning, not "this track's origin" — a track's origin is the track's own explicit pointer
 * ([Track.originWaypointId]), so each column has exactly one meaning. A plain nullable link, no
 * `@ForeignKey` (this database declares none — see `WaypointEntity`); when the track is deleted the
 * link is nulled, not left dangling and not cascaded, so the waypoint — the trailhead, the one
 * location worth keeping after clearing an old track — survives as an ordinary waypoint (see
 * [com.zynergylabs.forager.app.domain.DeleteTrackUseCase]). Defaults to `null` so no existing constructor site
 * changes; read back through [com.zynergylabs.forager.app.domain.WaypointRepository.getForTrack].
 */
data class Waypoint(
    val id: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val name: String,
    val note: String,
    val createdAtEpochMillis: Long,
    val trackId: String? = null,
    /** See [WaypointDesignation] — `null` for an ordinary, user-dropped waypoint. Defaults to `null` so no existing constructor site changes. */
    val designation: WaypointDesignation? = null,
)
