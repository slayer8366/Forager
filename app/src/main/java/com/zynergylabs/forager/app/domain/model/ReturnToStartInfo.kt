package com.zynergylabs.forager.app.domain.model

import com.zynergylabs.forager.app.domain.ShownHeight

/**
 * What the return-to-vehicle/return-to-start screen (Phase 1c) shows: bearing, straight-line
 * distance, and elevation difference back to a track's start point. Deliberately **not** an ETA —
 * a time estimate over a straight line across terrain this app has no data for is exactly the
 * fabricated-plausible-value failure CLAUDE.md forbids, per the plan's own explicit cut of this
 * feature. [bearingDegrees] is true-north; [com.zynergylabs.forager.app.domain.CompassProvider.heading] is
 * magnetic-north-relative, and reconciling the two is
 * [com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase]'s job (HUD-foundations dispatch, Item 2) —
 * this used to say "a UI-layer concern", and nothing in the UI ever did it.
 *
 * [elevationDifferenceMeters] is `start altitude - current altitude`: positive means the start
 * point is higher than where you are now. `null` whenever either point lacks a reported altitude,
 * same "unsupported, not guessed" rule every other altitude field in this app follows. As computed it is a difference of
 * two raw heights; the screen's copy is [withLiveHeight]'s.
 *
 * [startAltitudeMeters] is the start point's stored (raw) height, carried so the screen can put the start on the live
 * height's footing (dispatch 2026-10-28-13); `null` when the start has none.
 */
data class ReturnToStartInfo(
    val bearingDegrees: Double,
    val distanceMeters: Double,
    val elevationDifferenceMeters: Double?,
    val startAltitudeMeters: Double? = null,
)

/**
 * The Return control's height difference from the live height the strip shows (dispatch 2026-10-28-13; the owner:
 * "Yes, same as the strip (Recommended)"): the start's stored height put above sea level by the live conversion's own
 * offset ([ShownHeight.Known.seaLevelOf]), minus the live height.
 *
 * **With no sea-level height** (RECORD -2024, the owner: "Yes it should"), as the strip falls back to the fix's GPS height
 * (RECORD -2022): before the first conversion arrives ([shown] `null`) and on a phone that cannot convert
 * ([ShownHeight.Unavailable], below Android 14), the difference is the raw start minus the raw height here, as
 * [com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase] computed it. Never one end raw and the other sea level.
 *
 * **The rule this rests on: [startAltitudeMeters] is a raw GPS height.** The stored height does not record its kind
 * (`TrackPointEntity.altitude`, `WaypointEntity.altitude`), and a GPX import writes its file's `<ele>`, a sea-level height,
 * into those same columns. Every route to the start is a live fix's raw altitude today: the origin waypoint made from a
 * live fix, the first recorded point (kept by the recording service as `fix.toTrackPoint()`), and a taken-up recording's
 * marker or first recorded point. An imported track never becomes the running recording (its id comes only from
 * `startTrack(null)`), a GPX import never sets `originWaypointId`, and a restore is refused while recording.
 * `TrackRecordingSwipeAwayTest`'s "the start height" tests drive each route and fail if a non-raw height reaches the start;
 * a new route to the start must keep that, or record the height's kind.
 *
 * `null` when either end has no height, as before, so it reads "elevation diff. unavailable".
 */
fun ReturnToStartInfo.withLiveHeight(shown: ShownHeight?): ReturnToStartInfo {
    val difference = when (shown) {
        is ShownHeight.Known -> startAltitudeMeters?.let { shown.seaLevelOf(it) - shown.meters }
        ShownHeight.Unavailable, null -> rawDifference()
    }
    return copy(elevationDifferenceMeters = difference)
}

/** Raw start minus raw here: [ReturnToStartInfo.elevationDifferenceMeters] as computed, before any live height is applied. */
private fun ReturnToStartInfo.rawDifference(): Double? = elevationDifferenceMeters
