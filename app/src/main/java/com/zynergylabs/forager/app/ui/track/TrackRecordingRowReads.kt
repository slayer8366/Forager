package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.TAG

/**
 * The two reads of a track's own row that [TrackRecordingViewModel] acts on (dispatch 2026-10-23-09 Amendment 1, part 0:
 * the split before the way home is added, RECORD -1490), moved out of the ViewModel unchanged, in the pattern of UI split
 * Phase 2.7 (RECORD -1439). Each reads the row, logs what cannot be read, and says what the row says. Neither decides
 * anything: what follows from the answer (taking a recording up, clearing a recording's state) stays in the ViewModel's
 * own `takeUpRunningRecording` and `resyncRecordingState` (RECORD -1450). Every value read from the screen comes through
 * [uiState].
 */
internal class TrackRecordingRowReads(
    private val uiState: () -> TrackRecordingUiState,
    private val trackRepository: TrackRepository,
    private val errorLog: ErrorLog,
) {

    /**
     * The row of [trackId], the recording the service is running, for take-up; `null` when there is none to take up.
     *
     * **A row that cannot be read takes up nothing** and is logged. "Could not read the row" is
     * not "no recording", and it is tried again at the next foreground. A missing row is logged too: the service is
     * recording into a track that does not exist.
     */
    suspend fun trackToTakeUp(trackId: String): Track? {
        val track = trackRepository.getById(trackId).getOrElse { error ->
            errorLog.w(TAG, "Couldn't read track '$trackId' to take up its recording; the screen shows no recording.", error)
            return null
        }
        if (track == null) {
            errorLog.w(
                TAG,
                "The recording service is recording track '$trackId', which has no row; the screen shows no recording.",
                IllegalStateException("no track '$trackId'"),
            )
            return null
        }
        return track
    }

    /**
     * Whether [active]'s own row says its recording is over, for the resync: `true` when the row is ended or gone, and
     * the screen still shows [active] after the read.
     *
     * **A read failure says no** and is logged. "Could not read the row" is not evidence the
     * recording ended, and silently treating it as such would stop a live recording's UI on a
     * transient database error. That is the only branch [errorLog] carries, because it is the only
     * one holding a `Throwable` — [ErrorLog] takes a non-null one by design, and fabricating an
     * exception to report a state through it would misuse the abstraction rather than honour the
     * "never swallow a failure" rule it exists for.
     *
     * **A missing row says yes, in the same branch as an ended one**, and that is a state rather than
     * an error: a track that no longer exists cannot still be recording, so there is no failure to
     * report. The two are folded together because the ViewModel's response to them is identical and
     * splitting them would imply a distinction the code does not make.
     */
    suspend fun rowSaysEnded(active: ActiveTrack): Boolean {
        var ended = false
        trackRepository.getById(active.trackId)
            .onSuccess { track ->
                // The read suspended. Go by what is true now, as takeUpRunningRecording does: a Stop and a new
                // Record during the read (a Stop from the shade, then Record before the old end was written)
                // must not have the old track's end clear the new recording (dispatch 2026-10-21-04, review).
                if (uiState().activeTrack?.trackId != active.trackId) return@onSuccess
                if (track == null || track.endedAtEpochMillis != null) ended = true
            }
            .onFailure { error ->
                errorLog.w(TAG, "Couldn't re-read track '${active.trackId}'; leaving recording state as it is.", error)
            }
        return ended
    }
}
