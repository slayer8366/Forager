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
 * offset ([ShownHeight.Known.seaLevelOf]), minus the live height. `null` when either is missing, as before, so it reads
 * "elevation diff. unavailable"; never the raw difference standing in for it.
 */
fun ReturnToStartInfo.withLiveHeight(shown: ShownHeight?): ReturnToStartInfo {
    val start = startAltitudeMeters
    val difference = if (start != null && shown is ShownHeight.Known) shown.seaLevelOf(start) - shown.meters else null
    return copy(elevationDifferenceMeters = difference)
}
