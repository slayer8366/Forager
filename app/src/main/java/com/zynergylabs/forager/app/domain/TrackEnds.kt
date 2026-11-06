package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The recording service's report that it has stored a track's end (dispatch 2026-10-21-02, RECORD -1521):
 * the id of the last track whose `endedAtEpochMillis` the service wrote. Held by `AppContainer`, as
 * [RecordingHalts] is, so it outlives the Activity; the service writes it, the recording ViewModel reads it.
 *
 * **Why a report and not a reload on Stop.** The screen's Stop only clears the screen's state. The service
 * ends the row later, asynchronously, after it has written the buffered points
 * (`TrackRecordingService.stopRecording`). A reload started from the screen's Stop could run before that
 * write and find the track still open, or not yet worth showing. This is reported only after the write has
 * succeeded, so a reload it starts can never be early. A failed write reports nothing: the service logs it.
 *
 * **What it is for.** The ViewModel reloads its track list when this changes, so a finished track is in
 * Records at once (it was missing until the Tracks chip was tapped or the app restarted). The ViewModel
 * decides to reload (RECORD -1450); this only says the write is done.
 *
 * A [StateFlow] keeps only the latest id, so a ViewModel created later reads the last one and reloads once.
 * That is harmless: a reload reads what is stored. A second report of the same id does not emit again, which
 * is also harmless: the reload the first one started already read that track's end.
 */
class TrackEnds {
    private val _latest = MutableStateFlow<String?>(null)
    val latest: StateFlow<String?> = _latest.asStateFlow()

    /** The service stored the end of [trackId]. Call only after the write has succeeded. */
    fun report(trackId: String) {
        _latest.value = trackId
    }
}
