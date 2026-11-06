package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapControlsUi.kt (dispatch 2026-10-19-12, UI split Phase 1, PR 8).

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Stop
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatElevationChange
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import kotlin.math.roundToInt

/**
 * The Return control's icon (dispatch 2026-09-28-497, plan task T7): an X in a circle while
 * navigating, so it reads as the way out (the owner: "The navigation icon should turn into an X
 * circle to let the user know it's used to exit navigation"); its own directions icon otherwise.
 * [isReturning] is whether any navigation is on: since dispatch 2026-09-28-502 the pill passes its
 * `isNavigating` here, a waypoint navigation included. The parameter keeps the name T7 gave it, which
 * T7's own test calls it by.
 */
internal fun returnControlIcon(isReturning: Boolean): ImageVector = if (isReturning) Icons.Filled.Cancel else Icons.Filled.Directions

/** The Return control's label, matching its icon: the HUD exit's own words while navigating, today's label otherwise. [isReturning] as in [returnControlIcon]. */
internal fun returnControlDescription(isReturning: Boolean, isRecording: Boolean, info: ReturnToStartInfo?, distanceUnit: DistanceUnit): String =
    if (isReturning) {
        "Stop navigating"
    } else {
        returnToStartStripText(isRecording, info, distanceUnit).ifBlank { "Return to vehicle — start recording first" }
    }

/**
 * The return-to-vehicle line's text — blank while not recording (nothing to return to), a status
 * message once recording starts but before the first breadcrumb lands (bearing/distance need a
 * start point), then bearing, distance, and elevation difference once [info] is real. Now
 * [ControlPill]'s own `contentDescription` for its return-to-vehicle row (this used to also feed
 * the compass strip's own visible readout via `ReturnToVehicleStripControl`, removed in this
 * dispatch's Part A — see [CompassElevationStripContent]'s own doc comment).
 * [ReturnToStartInfo]'s own doc comment covers why there's no ETA here.
 */
internal fun returnToStartStripText(isRecording: Boolean, info: ReturnToStartInfo?, distanceUnit: DistanceUnit): String {
    if (!isRecording) return ""
    if (info == null) return "Recording — waiting for a fix to compute the way back"
    val elevationText = info.elevationDifferenceMeters?.let {
        formatElevationChange(it, UnitSystem.forDistanceUnit(distanceUnit))
    } ?: "elevation diff. unavailable"
    val bearing = info.bearingDegrees.roundToInt()
    return "Return: $bearing° ${cardinalDirection(info.bearingDegrees.toFloat())} · ${formatDistanceMeters(info.distanceMeters, distanceUnit)} · $elevationText"
}
