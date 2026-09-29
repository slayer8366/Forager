package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * Removes a track and its points by id — and first nulls [com.zynergylabs.forager.app.domain.model.Waypoint.trackId]
 * on every waypoint that was dropped while it recorded (HUD-foundations dispatch, Item 3, owner
 * decision). The waypoints survive as ordinary waypoints: the origin waypoint is the trailhead,
 * the one location someone wants to keep after clearing an old track, so deleting them with the
 * track would discard the useful part, and leaving the link in place would lie about a track
 * that no longer exists.
 *
 * **Before either, the track's path is copied** (F3, dispatch 2026-09-28-195; owner, 2026-09-29: "Option B"):
 * [KeptTrackPathRepository.copyForTrack] writes the track's points, as read through the repository (so already
 * filtered by the read seam, in timestamp order), into a saved-path row for every journal entry that has the track,
 * kept or withheld, draft or not. An entry that kept the track then still draws its line once the points are gone.
 * The track's own points are still deleted, as the delete-data page says.
 *
 * **Why the copy is idempotent-and-first, not in one transaction with the delete** (the dispatch: "in one Room
 * transaction if feasible. Otherwise the copy is idempotent and runs first"). The three steps live in three
 * repositories behind three interfaces, and the domain layer has no transaction seam (CLAUDE.md: keep domain logic
 * free of Android/Room bindings); giving it one would be a new abstraction threaded through every construction of
 * this class. The detach and the delete were already two steps with no shared transaction, so this follows the
 * same rule as the existing design: order the steps so a failure between any two leaves a state a retry repairs.
 * Copy first: if the copy fails, nothing else has run, the track is still there and the failure is reported; if a
 * later step fails, the saved rows exist for a track that still exists, which harms nothing (the entry draws the
 * live track and ignores the saved path) and a retry replaces them with the same values. The one window this does
 * not close: an entry that starts keeping the track after the copy and before the delete has a ref row with no
 * saved path. That needs a person to save an entry, in the moments a delete takes.
 *
 * **A track that is already gone copies nothing** and the delete still runs, so a second delete never overwrites
 * a saved path with an empty one.
 *
 * Detach then delete, deliberately: the two live in different repositories with no shared
 * transaction, so one can succeed and the other fail. If the detach succeeds and the delete
 * fails, the caller sees the failure and the track is still there — its waypoints merely read as
 * unlinked, which a retry leaves unchanged. The other order would leave links pointing at a
 * track that is gone, the exact dangling state this exists to rule out, with the failure reported
 * for a deletion that had actually happened. [com.zynergylabs.forager.app.domain.model.Track.originWaypointId]
 * needs no counterpart step: it lives on the row being deleted.
 */
class DeleteTrackUseCase(
    private val repository: TrackRepository,
    private val waypointRepository: WaypointRepository,
    private val keptTrackPaths: KeptTrackPathRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        repository.getById(id)
            .mapCatching { track ->
                if (track != null) keptTrackPaths.copyForTrack(id, track.points.map { LatLng(it.lat, it.lng) }).getOrThrow()
            }
            .mapCatching { waypointRepository.detachFromTrack(id).getOrThrow() }
            .mapCatching { repository.delete(id).getOrThrow() }
}
