package com.zynergylabs.forager.app.importgpx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zynergylabs.forager.app.domain.GpxFileSource
import com.zynergylabs.forager.app.domain.GpxImportOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plan T16's in-app side: the file Records > Tracks > "Import GPX" picked, saved through [importGpx], and
 * the outcome of an Open with or Share import that [GpxImportActivity] already saved, both turned into
 * one [GpxImportNotice] for the screen (Records on the new track, its note or message). Its own
 * ViewModel, not a method on TrackRecordingViewModel: an import has nothing to do with recording, and
 * keeping it apart is what keeps it from touching recording state.
 */
class GpxImportViewModel(
    private val importGpx: suspend (GpxFileSource) -> GpxImportOutcome,
) : ViewModel() {

    private val _notice = MutableStateFlow<GpxImportNotice?>(null)

    /** The latest import still to be shown, or `null`. Cleared by [onNoticeShown]. */
    val notice: StateFlow<GpxImportNotice?> = _notice.asStateFlow()

    private var nextSeq = 1L

    /** Saves the picked file. Not cut short by leaving the screen: the use case keeps all of the file or none of it. */
    fun importFile(source: GpxFileSource): Job = viewModelScope.launch {
        val outcome = withContext(NonCancellable) { importGpx(source) }
        post(outcome)
    }

    /** An import [GpxImportActivity] already saved, read from the Intent that opened Forager. */
    fun onImportedElsewhere(outcome: GpxImportOutcome) = post(outcome)

    /** The screen has shown notice [seq]; a later one is left alone. */
    fun onNoticeShown(seq: Long) {
        _notice.update { current -> if (current?.seq == seq) null else current }
    }

    private fun post(outcome: GpxImportOutcome) {
        _notice.value = GpxImportNotice(seq = nextSeq++, outcome = outcome)
    }
}
