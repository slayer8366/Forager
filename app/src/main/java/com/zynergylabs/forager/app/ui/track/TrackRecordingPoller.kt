package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.NETWORK_FIXES_RECORDING_NOTICE
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.isMostlyNetworkFixes
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.POLL_INTERVAL_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The breadcrumb poll of [TrackRecordingViewModel] (UI split Phase 2.7, PR 4, dispatch 2026-10-19-30,
 * RECORD -1439), moved out of the ViewModel unchanged. It holds the poll's one job, the track the poll
 * last read, and the once-per-recording network-fixes flag. The ViewModel decides when the poll starts
 * and stops, from the same lines as before: [begin] from `startRecording`, `takeUpRunningRecording` and
 * `startReturn`; [cancel] from `clearRecordingState`; [cancelOnCleared] from `onCleared`. Each is
 * synchronous. The poll launches in [scope], the ViewModel's own `viewModelScope`, read each time rather
 * than kept, so it gets no scope, dispatcher or lifetime of its own. What it calls back into the
 * ViewModel ([settleTakenUpOrigin], [handStartPointToWatch], [nextNoticeId]) is called from the same place
 * in the poll body, in the same order, as before.
 *
 * ## Why breadcrumbs are polled, not reactive
 *
 * (Moved here from the ViewModel's own doc by the split before the way home, dispatch 2026-10-23-09 Amendment 1, part 0.)
 * [com.zynergylabs.forager.app.domain.TrackRepository] is plain suspend calls, matching every other Room-backed repository in this
 * app — there is no `Flow<Track>` to collect. [com.zynergylabs.forager.app.service.TrackRecordingService]
 * itself batches writes (every 20 points or 30 seconds, whichever first) rather than writing per
 * fix, so polling at roughly that same cadence ([POLL_INTERVAL_MILLIS]) shows breadcrumbs about as
 * fresh as the data actually is, without adding a reactive-query layer this database has never
 * needed anywhere else.
 */
internal class TrackRecordingPoller(
    private val uiState: () -> TrackRecordingUiState,
    private val updateState: ((TrackRecordingUiState) -> TrackRecordingUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val trackRepository: TrackRepository,
    private val settleTakenUpOrigin: suspend (ActiveTrack, Track) -> Unit,
    private val handStartPointToWatch: () -> Unit,
    /** The ViewModel's one notice counter, `++recordingNoticeIds`, shared with the trip-start warning and the background prompt. */
    private val nextNoticeId: () -> Int,
) {

    private var pollingJob: Job? = null

    /** The track the 15 s poll last read for the active recording: what the route tick routes along. */
    var lastPolledTrack: Track? = null
        private set

    private var networkFixesNoticeShown = false

    /**
     * A recording started on this screen (`startRecording`): no track read yet, and the network-fixes
     * notice may show again. The ViewModel's two writes, `lastPolledTrack = null` and
     * `networkFixesNoticeShown = false`, in one call.
     */
    fun resetForNewRecording() {
        lastPolledTrack = null
        networkFixesNoticeShown = false
    }

    /**
     * No track read yet, the notice flag left as it is: a recording taken up (`takeUpRunningRecording`,
     * which deliberately does not reset the flag) and a cleared one (`clearRecordingState`).
     */
    fun resetPolledTrack() {
        lastPolledTrack = null
    }

    fun begin(trackId: String) {
        pollingJob?.cancel()
        pollingJob = scope().launch {
            while (true) {
                trackRepository.getById(trackId).onSuccess { track ->
                    updateState { it.copy(breadcrumbPoints = track?.points.orEmpty()) }
                    val active = uiState().activeTrack
                    if (track != null && active != null && active.trackId == trackId) settleTakenUpOrigin(active, track)
                    if (track != null && uiState().activeTrack?.trackId == trackId) lastPolledTrack = track
                    handStartPointToWatch()
                    // Timestamp-filter dispatch, Item 3: once per recording, the moment the read
                    // seam is seen to be excluding most of this track — see isMostlyNetworkFixes for
                    // why the threshold also waits for ten stored points before it can fire.
                    if (track != null && !networkFixesNoticeShown && track.isMostlyNetworkFixes()) {
                        networkFixesNoticeShown = true
                        updateState { it.copy(networkFixesNotice = RecordingNotice(nextNoticeId(), NETWORK_FIXES_RECORDING_NOTICE)) }
                    }
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    /** Stops the poll: `clearRecordingState`'s own two lines, moved here unchanged. */
    fun cancel() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** `onCleared`'s own line: cancels the poll and, as before, leaves the field as it is. */
    fun cancelOnCleared() {
        pollingJob?.cancel()
    }
}
