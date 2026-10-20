package com.zynergylabs.forager.app.ui.track

// Moved from TrackRecordingViewModel.kt unchanged (UI split Phase 1, PR 10g, dispatch 2026-10-19-24).

/** The owner's words (RECORD -660, item 4): the service was refused the foreground, so nothing is recording. */
internal const val RECORDING_COULD_NOT_START_MESSAGE = "Recording couldn't start. Open Forager and tap Record again."

/**
 * The permission case (RECORD -660, item 4): the service has no location permission, at the start or
 * lost during the walk. The planner's wording, pointing to the permission; the owner: "Use it as written" (RECORD -661).
 */
internal const val RECORDING_NEEDS_LOCATION_PERMISSION_MESSAGE =
    "Recording stopped because Forager can't use your location. Allow location for Forager in your phone's Settings, then tap Record again."

/**
 * How long the navigation display holds "Arrived" after a return ends on arrival, before it closes by
 * itself (RECORD -845). The owner chose "Arrived for a few seconds, then closes (Recommended)" over
 * "Arrived stays until closed" and "closes at once with a short notice"; the ruling records it as
 * "about 10 seconds": long enough to glance down at a phone and read it, short enough that the plain
 * map is back before the walker is driving. A held reading, not a live one, so a drive-off inside the
 * hold does not turn it back into a distance.
 */
const val ARRIVAL_HOLD_MILLIS = 10_000L
