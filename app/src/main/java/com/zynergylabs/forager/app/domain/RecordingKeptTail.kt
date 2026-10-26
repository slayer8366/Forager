package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The newest points a recording has kept, on [trackId], newest last. [NONE] when no recording holds any. */
data class KeptTail(val trackId: String?, val points: List<TrackPoint>) {
    companion object {
        val NONE = KeptTail(null, emptyList())
    }
}

/** STUB (fail-first): holds nothing yet. */
class RecordingKeptTail {
    private val _tail = MutableStateFlow(KeptTail.NONE)
    val tail: StateFlow<KeptTail> = _tail.asStateFlow()

    fun add(trackId: String, point: TrackPoint) = Unit

    fun end(trackId: String?) = Unit

    companion object {
        const val MAX = 200
    }
}

/** STUB (fail-first): the stored points only. */
fun recordingLineOf(stored: List<TrackPoint>, tail: KeptTail, trackId: String?): List<TrackPoint> = stored
