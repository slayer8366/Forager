package com.zynergylabs.forager.app.domain.model

/**
 * A recorded path: a foreground-recorded sequence of [TrackPoint]s from [startedAtEpochMillis]
 * until [endedAtEpochMillis], or still in progress if that's `null`.
 *
 * No automatic pruning or retention limit, on purpose — unlike
 * [com.zynergylabs.forager.app.domain.SearchCacheRepository]'s five-entry LRU (disposable, re-fetchable
 * answers), a recorded track is irreplaceable field data in exactly the way a
 * [MushroomLogEntry] is: losing one to an eviction policy would be entirely this app's fault. It
 * is deleted only by explicit user action, the same precedent
 * [com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase] already set for log entries.
 *
 * [points] is the full recorded sequence, loaded together rather than paginated — a multi-hour
 * track is at most a few thousand points at any sane sampling interval (see
 * [com.zynergylabs.forager.app.domain.model.TrackRecordingMode]), well within what a single Room query and an
 * in-memory list handle without difficulty.
 *
 * [originWaypointId] — HUD-foundations dispatch, Item 3 (owner decision): an explicit pointer to
 * the [Waypoint] that marks where this track started, once the navigation HUD creates one on
 * record-start. Explicit rather than "the earliest waypoint carrying this track's id", which would
 * be a convention resting on an undecided question (which fix seeds the origin); with a pointer,
 * [Waypoint.trackId] and this column each have exactly one meaning. `null` for every track
 * recorded before the HUD exists, and for any track whose origin was never created. Written with
 * the track row ([com.zynergylabs.forager.app.domain.TrackRepository.create]); read back through
 * [com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase]. Defaults to `null` so no existing
 * constructor site changes.
 *
 * [excludedPointCount] — timestamp-filter dispatch (owner decision): how many stored points the
 * read seam left out of [points] as network-provider fixes
 * ([com.zynergylabs.forager.app.domain.isNetworkProviderFix]). **Derived at the seam, never a column**: it is
 * computed in the same mapping that applies the rule, so it cannot drift from the rule that
 * produced it, and it is what lets a surface say "N more not shown" or "no usable points" rather
 * than render a blank line in silence. `0` for a track built anywhere other than the repository
 * read (a fake, a fresh `create`), which is also the honest value there.
 *
 * [importedAtEpochMillis] — plan T16, GPX import (dispatch 2026-09-28-634, the owner's answers in -636):
 * when this track was imported from a GPX file, or `null` for a walk this app recorded. What the
 * "Imported" label in Records reads, and what keeps an imported track out of the Journal's derived
 * trips ("Records and map only"). A column as of `MIGRATION_17_18`.
 *
 * [importedWithoutTimes] — the same dispatch, "Import, show "No times"": the file gave this track no
 * times (or not for every point), so it is dated the import moment and its point times are only an
 * ordering. The surfaces show "No times in file" where a duration or a time would be. Always `false`
 * for a recorded walk. A column as of `MIGRATION_17_18`.
 */
data class Track(
    val id: String,
    val name: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val points: List<TrackPoint>,
    val originWaypointId: String? = null,
    val excludedPointCount: Int = 0,
    val importedAtEpochMillis: Long? = null,
    val importedWithoutTimes: Boolean = false,
)
