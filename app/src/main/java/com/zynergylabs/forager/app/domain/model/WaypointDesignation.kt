package com.zynergylabs.forager.app.domain.model

/**
 * What an auto-created waypoint *is* to the track it belongs to — navigation HUD stage one.
 *
 * A field, never a naming convention: the owner's display naming ("Start · Sep 5, 9:41 AM") is only
 * the waypoint's *default* [Waypoint.name], and the user may rename it freely. Navigation reads
 * this field (and [Track.originWaypointId]) — if it read the name, renaming "Start" to "Truck"
 * would silently destroy the way-back target. `null` on [Waypoint.designation] means an ordinary,
 * user-dropped waypoint.
 *
 * Stored by name in `waypoints.designation` (see `WaypointEntity`); read by the map's waypoint
 * filter (`mapVisibleWaypoints`: the end waypoint never draws unless it is the target, and neither
 * auto waypoint draws while not navigating) and by whatever resolves a track's end waypoint, which
 * has no pointer of its own on [Track] and is found by `trackId` plus this.
 */
enum class WaypointDesignation {
    /** Where the track started — pointed at by [Track.originWaypointId], the HUD's stage-one target. */
    ORIGIN,

    /** Where the track stopped. Never a navigation target in stage one, so never drawn on the map. */
    END,
}
