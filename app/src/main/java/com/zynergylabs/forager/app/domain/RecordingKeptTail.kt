package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The newest points a recording has kept, on [trackId], newest last. [NONE] when no recording holds any. */
data class KeptTail(val trackId: String?, val points: List<TrackPoint>) {
    companion object {
        val NONE = KeptTail(null, emptyList())
    }
}

/**
 * The recording's newest kept points, written or not, for the recording line (dispatch 2026-10-26-05, RECORD -1651, the
 * owner: "(b) Reach the dot (Recommended)").
 *
 * ## Why
 *
 * The recording screen drew its line from the stored track alone (`TrackRecordingPoller`, polled every 15 s), and the
 * service writes kept points in batches, every 20 points or 30 s (`TrackRecordingService.FLUSH_BATCH_SIZE`,
 * `FLUSH_INTERVAL_MILLIS`). So the line ended at the last written point, up to a write and a poll behind the walker
 * (Hazel's #404 diagnosis, `prompts/preserved/2026-10-25-10-s22-diagnosis-report.md`, branch
 * way-home-fresh-start-s22-report). The service now hands each kept point here too, as it hands it to the way home
 * (`WayHomeKeptPoints`, dispatch 2026-10-25-10), and the screen draws [recordingLineOf] the stored track and these.
 *
 * ## Why a holder of its own, and not the way home's held points
 *
 * The way home's points sit behind `WayHomeWatch`'s lock, which the screen's main thread must not take; they have no
 * flow to say a point was kept; and they are pruned against the way home's own store reads, which can be newer than the
 * screen's last poll, so the line could lose points its stored copy does not have yet. This holder has no lock: [tail]
 * is a `StateFlow` of an immutable list, written with `update`, and read by the screen as it is. It prunes nothing; the
 * reader takes only the points newer than its own stored copy.
 *
 * ## What it holds
 *
 * The points kept on one recording, newest last, at most [MAX]. A point on another recording replaces them all; [end]
 * forgets them. [MAX] is the way home's figure (`WayHomeKeptPoints.MAX`, 200), not a new one: 200 kept points are at
 * least 16 minutes at High accuracy's 5 s (`TrackRecordingMode.HIGH_ACCURACY`), far beyond the write and the poll.
 * What is written, and when, does not change: the service's batch is untouched.
 */
class RecordingKeptTail {
    private val _tail = MutableStateFlow(KeptTail.NONE)
    val tail: StateFlow<KeptTail> = _tail.asStateFlow()

    /** A point the recording on [trackId] kept. */
    fun add(trackId: String, point: TrackPoint) = _tail.update { current ->
        val held = if (current.trackId == trackId) current.points else emptyList()
        KeptTail(trackId, (held + point).takeLast(MAX))
    }

    /** The recording on [trackId] (or, with `null`, any: the service's `onDestroy`) has ended. */
    fun end(trackId: String?) = _tail.update { current ->
        if (trackId != null && trackId != current.trackId) current else KeptTail.NONE
    }

    companion object {
        /** As `WayHomeKeptPoints.MAX` (dispatch 2026-10-25-10). See the class doc. */
        const val MAX = 200
    }
}

/**
 * The recording line on [trackId]: [stored], the track as the store reads it, then the points in [tail] newer than its
 * last, network fixes left out as the store's read leaves them out ([excludeNetworkProviderFixes]), so the line does not
 * show a point the next read would take away. A point both written and held is drawn once. A [tail] for another
 * recording, or no recording, adds nothing.
 */
fun recordingLineOf(stored: List<TrackPoint>, tail: KeptTail, trackId: String?): List<TrackPoint> {
    if (trackId == null || tail.trackId != trackId) return stored
    val lastStored = stored.lastOrNull()?.timestampEpochMillis ?: Long.MIN_VALUE
    val newer = excludeNetworkProviderFixes(tail.points.filter { it.timestampEpochMillis > lastStored })
    return if (newer.isEmpty()) stored else stored + newer
}
