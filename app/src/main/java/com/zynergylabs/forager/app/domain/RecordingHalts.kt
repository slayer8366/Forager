package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the recording service stopped a recording it was asked to run (RECORD -660, item 4). */
enum class RecordingHaltReason {
    /** The system refused to put the service in the foreground (Android 12+ start restrictions, Android 14 background location). */
    FOREGROUND_REFUSED,

    /** No location permission, at the start or lost during the recording. */
    NO_LOCATION_PERMISSION,
}

/** The service stopped recording [trackId] for [reason]. */
data class RecordingHalt(val trackId: String, val reason: RecordingHaltReason)

/**
 * The service's report that it stopped a recording the screen still thinks is running (dispatch
 * 2026-09-28-658, Amendment 1, RECORD -660, item 4; the owner: "Fix it, say why (Recommended)").
 * Held by `AppContainer`, so it outlives the Activity as the watches do; the service writes it, the
 * recording ViewModel reads it and shows not recording, with the reason. Keyed by track, so a later
 * recording is never mistaken for the stopped one.
 */
class RecordingHalts {
    private val _latest = MutableStateFlow<RecordingHalt?>(null)
    val latest: StateFlow<RecordingHalt?> = _latest.asStateFlow()

    fun report(halt: RecordingHalt) {
        _latest.value = halt
    }
}
