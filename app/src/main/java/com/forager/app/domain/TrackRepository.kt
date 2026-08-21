package com.forager.app.domain

import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint

/**
 * Owned abstraction over track persistence — the same pattern as [MushroomLogRepository]. Domain
 * and UI code depend on this interface, never on Room directly. The Room-backed implementation
 * lives in `data/repository/`.
 *
 * Points are appended in batches ([appendPoints]) rather than the whole [Track] being re-saved on
 * every fix, since a multi-hour recording writes a point every few seconds — see
 * `RoomTrackRepository`'s doc comment for the batching this maps onto at the Room layer.
 */
interface TrackRepository {
    /** Every track currently stored, in no particular order — ordering is a use-case concern. */
    suspend fun getAll(): Result<List<Track>>

    suspend fun getById(id: String): Result<Track?>

    /** Creates a new track with no points yet and no end time. */
    suspend fun create(track: Track): Result<Unit>

    /** Appends [points] to the track with id [trackId]. Never replaces previously-appended points. */
    suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit>

    /** Marks the track with id [trackId] as finished at [endedAtEpochMillis]. */
    suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit>

    /** Removes the track with this id and all its points. A no-op, not a failure, if none exists. */
    suspend fun delete(id: String): Result<Unit>
}
