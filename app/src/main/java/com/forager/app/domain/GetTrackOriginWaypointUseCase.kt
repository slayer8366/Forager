package com.forager.app.domain

import com.forager.app.domain.model.Waypoint

/**
 * The [Waypoint] a track's [com.forager.app.domain.model.Track.originWaypointId] points at — the
 * read path for that column (HUD-foundations dispatch, Item 3), and what the navigation HUD will
 * call to find its target. Two repository reads joined in code — the track row, then the waypoint
 * it names — rather than a SQL join, matching this database's no-`@ForeignKey` rule and keeping
 * each Room repository on its own DAO.
 *
 * `null`, not a failure, when the track has no origin recorded (every track from before the HUD
 * existed), when the track itself no longer exists, or when the pointed-at waypoint has since been
 * deleted from Records — an explicit "no origin" in every case, never a substitute such as the
 * track's first point. A repository read failure stays a failure.
 */
class GetTrackOriginWaypointUseCase(
    private val trackRepository: TrackRepository,
    private val waypointRepository: WaypointRepository,
) {
    suspend operator fun invoke(trackId: String): Result<Waypoint?> =
        trackRepository.getById(trackId).mapCatching { track ->
            val originId = track?.originWaypointId ?: return@mapCatching null
            waypointRepository.getById(originId).getOrThrow()
        }
}
