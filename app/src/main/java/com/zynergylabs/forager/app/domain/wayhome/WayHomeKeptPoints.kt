package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.excludeNetworkProviderFixes
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint

/**
 * The recording's newest kept points, held for the way home so it starts from the walker and not from the last point
 * written (dispatch 2026-10-25-10, RECORD -1636, the owner: "Yes, after Hazel's queue (Recommended)").
 *
 * ## Why this, and not a flush before the read
 *
 * The service writes kept points in batches, every 20 points or 30 s (`TrackRecordingService.FLUSH_BATCH_SIZE`,
 * `FLUSH_INTERVAL_MILLIS`), and the way home reads the track from the store (`WayHomeWatch.work`), so the line started
 * up to one batch behind the walker (Sorrel's bends report, `prompts/preserved/2026-10-25-07-report.md`: 18.7 m in the
 * S22 case, up to about 42 m at 1.4 m/s). Two ways to close that were weighed. A flush at Return and at each re-route
 * would change when the stored track is written, add a write the battery and the store did not have, and needs a path
 * from the watch back into the service. Handing the watch the kept points, as `ReturnWatch.onKeptPoint` already is
 * handed them, changes nothing the user sees but the line's start: the write timing, the stored track, the battery and
 * the recording notification stay as they were. The second was chosen.
 *
 * ## What it holds
 *
 * The points kept on one recording, newest last, at most [MAX] of them. A point on another recording replaces them all
 * (one recording at a time; a new recording's first point forgets the last one's). [addedTo] appends only the points
 * newer than the stored track's last point, by time, so a point both written and held is used once, and then forgets
 * the held points the store already has. [MAX] is ten batches: the held points are pruned at each read, and written
 * every 20, so more than 20 are held only while writes fail (logged by the service), and then the newest [MAX] still
 * give the line its start.
 *
 * ## Network fixes
 *
 * The service hands over every point its sampler keeps, and the sampler keeps a network fix whose reported accuracy
 * clears the mode's ceiling (`LocationSampler.shouldAccept`). The stored track [addedTo] is handed has already been
 * through the store's read path, which leaves network fixes out ([excludeNetworkProviderFixes], `RoomTrackRepository`).
 * [addedTo] applies the same rule to the held points, so the way home never starts from a network-only position, as no
 * other display of the track does (dispatch 2026-10-26-07, RECORD -1654). The held list itself is not filtered: the
 * pruning by time above works the same either way, and the rule stays at the one place the points are read.
 *
 * Not thread-safe: [WayHomeWatch] calls it under its lock.
 */
internal class WayHomeKeptPoints {
    private var trackId: String? = null
    private val points = ArrayDeque<TrackPoint>()

    fun add(trackId: String, point: TrackPoint) {
        if (trackId != this.trackId) {
            this.trackId = trackId
            points.clear()
        }
        points.addLast(point)
        while (points.size > MAX) points.removeFirst()
    }

    /** [track] with the points held for it that are newer than its last point, in order, network fixes left out. */
    fun addedTo(track: Track): Track {
        if (track.id != trackId || points.isEmpty()) return track
        val lastStored = track.points.lastOrNull()?.timestampEpochMillis ?: Long.MIN_VALUE
        while (points.isNotEmpty() && points.first().timestampEpochMillis <= lastStored) points.removeFirst()
        val newer = excludeNetworkProviderFixes(points)
        if (newer.isEmpty()) return track
        return track.copy(points = track.points + newer)
    }

    /** The recording on [trackId] (or, with `null`, any) has ended. */
    fun forget(trackId: String?) {
        if (trackId != null && trackId != this.trackId) return
        this.trackId = null
        points.clear()
    }

    companion object {
        const val MAX = 200
    }
}
