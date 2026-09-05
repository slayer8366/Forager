package com.forager.app.domain

/**
 * Removes a track and its points by id — and first nulls [com.forager.app.domain.model.Waypoint.trackId]
 * on every waypoint that was dropped while it recorded (HUD-foundations dispatch, Item 3, owner
 * decision). The waypoints survive as ordinary waypoints: the origin waypoint is the trailhead,
 * the one location someone wants to keep after clearing an old track, so deleting them with the
 * track would discard the useful part, and leaving the link in place would lie about a track
 * that no longer exists.
 *
 * Detach first, then delete, deliberately: the two live in different repositories with no shared
 * transaction, so one can succeed and the other fail. If the detach succeeds and the delete
 * fails, the caller sees the failure and the track is still there — its waypoints merely read as
 * unlinked, which a retry leaves unchanged. The other order would leave links pointing at a
 * track that is gone, the exact dangling state this exists to rule out, with the failure reported
 * for a deletion that had actually happened. [com.forager.app.domain.model.Track.originWaypointId]
 * needs no counterpart step: it lives on the row being deleted.
 */
class DeleteTrackUseCase(
    private val repository: TrackRepository,
    private val waypointRepository: WaypointRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        waypointRepository.detachFromTrack(id).mapCatching { repository.delete(id).getOrThrow() }
}
