package com.forager.app.domain.model

/**
 * A recorded path: a foreground-recorded sequence of [TrackPoint]s from [startedAtEpochMillis]
 * until [endedAtEpochMillis], or still in progress if that's `null`.
 *
 * No automatic pruning or retention limit, on purpose — unlike
 * [com.forager.app.domain.SearchCacheRepository]'s five-entry LRU (disposable, re-fetchable
 * answers), a recorded track is irreplaceable field data in exactly the way a
 * [MushroomLogEntry] is: losing one to an eviction policy would be entirely this app's fault. It
 * is deleted only by explicit user action, the same precedent
 * [com.forager.app.domain.DeleteMushroomLogEntryUseCase] already set for log entries.
 *
 * [points] is the full recorded sequence, loaded together rather than paginated — a multi-hour
 * track is at most a few thousand points at any sane sampling interval (see
 * [com.forager.app.domain.model.TrackRecordingMode]), well within what a single Room query and an
 * in-memory list handle without difficulty.
 *
 * [originWaypointId] — HUD-foundations dispatch, Item 3 (owner decision): an explicit pointer to
 * the [Waypoint] that marks where this track started, once the navigation HUD creates one on
 * record-start. Explicit rather than "the earliest waypoint carrying this track's id", which would
 * be a convention resting on an undecided question (which fix seeds the origin); with a pointer,
 * [Waypoint.trackId] and this column each have exactly one meaning. `null` for every track
 * recorded before the HUD exists, and for any track whose origin was never created. Written with
 * the track row ([com.forager.app.domain.TrackRepository.create]); read back through
 * [com.forager.app.domain.GetTrackOriginWaypointUseCase]. Defaults to `null` so no existing
 * constructor site changes.
 */
data class Track(
    val id: String,
    val name: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val points: List<TrackPoint>,
    val originWaypointId: String? = null,
)
