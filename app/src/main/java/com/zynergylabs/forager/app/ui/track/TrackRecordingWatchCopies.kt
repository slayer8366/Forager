package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.BackByChoice
import com.zynergylabs.forager.app.domain.BackByControl
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.SundownShown
import com.zynergylabs.forager.app.domain.backByAtFor
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.TAG
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * What [TrackRecordingViewModel] copies from the watches into the screen's state, and the arrival hold's timer (nav step 2,
 * PR 2, dispatch 2026-10-21-01; the pattern of UI split Phase 2.7, RECORD -1439), moved out of the ViewModel unchanged: Back
 * by's set and clear, the back-by and sundown copies, and starting and ending the arrival hold. The ViewModel decides when:
 * [copyFromWatch][TrackRecordingViewModel] still decides whether a just-ended return starts the hold and whether the route
 * tick runs, and every caller of [endArrivalHold] is still a ViewModel function. Every value read from the screen comes
 * through [uiState], every write goes through [updateState], and the hold launches in [scope], the ViewModel's own
 * `viewModelScope`, read each time rather than kept.
 */
internal class TrackRecordingWatchCopies(
    private val uiState: () -> TrackRecordingUiState,
    private val updateState: ((TrackRecordingUiState) -> TrackRecordingUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val sundownShown: StateFlow<SundownShown?>,
    private val backBy: BackByControl,
    private val errorLog: ErrorLog,
    private val currentTime: CurrentTimeProvider,
    private val zone: ZoneId,
) {

    /** The arrival hold's timer (RECORD -845), running only while [TrackRecordingUiState.isArrivalHeld]. See [holdArrival]. */
    private var arrivalHoldJob: Job? = null

    /**
     * The quick menu's "+1 h", "+2 h", "+3 h" or a picked time (dispatch 2026-09-28-645): sets this
     * recording's back-by time, worked out from this class's clock and zone ([backByAtFor]). Does
     * nothing with no recording: Back by exists only while recording (the owner, "During a recording
     * only"), and the menu offers no choice then.
     */
    fun setBackBy(choice: BackByChoice) {
        val trackId = uiState().activeTrack?.trackId ?: run {
            // Dispatch 2026-09-28-796: no longer silent. The menu offers no choice without a recording,
            // so reaching here means the screen and the recording disagree; it is logged and recorded.
            errorLog.w(TAG, "Back by was not set: no recording on this screen.", IllegalStateException("back-by with no active track"))
            backBy.setWithNoRecording()
            return
        }
        val at = backByAtFor(choice, currentTime.nowEpochMillis(), zone)
        if (!backBy.set(trackId, at)) {
            errorLog.w(TAG, "Back by was not set for track '$trackId': another recording is being watched.", IllegalStateException("back-by refused"))
        }
    }

    /** The quick menu's "Clear". */
    fun clearBackBy() {
        val trackId = uiState().activeTrack?.trackId ?: return
        backBy.clear(trackId)
    }

    /** Copies the watch's back-by time into the screen's state while it is for this screen's recording, as [copySundownLine] does. */
    fun copyBackBy() {
        updateState { state ->
            val shown = backBy.shown.value
            state.copy(backBy = shown?.takeIf { state.activeTrack != null && it.trackId == state.activeTrack.trackId })
        }
    }

    /**
     * Copies the watch's sundown line into the screen's state while it is for this screen's
     * recording, the rule [copyFromWatch] follows; `null` otherwise. Called when the watch publishes
     * and from [copyFromWatch], which runs whenever this screen's recording changes.
     */
    fun copySundownLine() {
        updateState { state ->
            val shown = sundownShown.value
            state.copy(sundownLine = shown?.line?.takeIf { state.activeTrack != null && shown.trackId == state.activeTrack.trackId })
        }
    }

    /** Starts the arrival hold, ending by itself after [ARRIVAL_HOLD_MILLIS]. */
    fun holdArrival() {
        arrivalHoldJob?.cancel()
        updateState { it.copy(isArrivalHeld = true) }
        arrivalHoldJob = scope().launch {
            delay(ARRIVAL_HOLD_MILLIS)
            updateState { it.copy(isArrivalHeld = false) }
            arrivalHoldJob = null
        }
    }

    /** Ends the arrival hold now, if one is running: the walker closed it, tapped Return again, or stopped the recording. */
    fun endArrivalHold() {
        arrivalHoldJob?.cancel()
        arrivalHoldJob = null
        if (uiState().isArrivalHeld) updateState { it.copy(isArrivalHeld = false) }
    }
}
