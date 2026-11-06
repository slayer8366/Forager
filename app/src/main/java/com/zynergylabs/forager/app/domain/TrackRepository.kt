package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord

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

    /**
     * Every stored point for the track with id [id], in stored order, at full precision (the exact
     * millisecond timestamp, [TrackPoint.accuracyMeters], both speed columns) with each point's
     * network-provider-fix verdict attached — see [TrackPointRecord]. GPX full-record export
     * dispatch: unlike [getById]/[getAll]/[getForDay], this does **not** apply
     * [com.zynergylabs.forager.app.domain.excludeNetworkProviderFixes] — no display consumer may call this;
     * they keep going through the filtered reads above (ruling: in-app display stays filtered, the
     * export carries the full data set). Empty if no such track exists or it has no stored points.
     */
    suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>>

    /**
     * Every track overlapping one local day — Journal Stage 2a's derived-trip read. Half-open range
     * `[dayStartInclusiveEpochMillis, dayEndExclusiveEpochMillis)` — see
     * [com.zynergylabs.forager.app.domain.LocalDayRange]'s own doc comment for why, and
     * `com.zynergylabs.forager.app.data.local.TrackDao.getTracksForDay`'s own doc comment for the
     * midnight-crossing overlap test this is built on.
     */
    suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>>

    /** Creates a new track with no points yet and no end time. */
    suspend fun create(track: Track): Result<Unit>

    /**
     * Writes [track] and all its [Track.points] at once, all or nothing: plan T16's GPX import, the one
     * caller. Not [create] then [appendPoints], whose gap could leave a row with only some of its points.
     * The default answers "unsupported", explicitly, as [endIfOpen]'s does.
     */
    suspend fun createWithPoints(track: Track): Result<Unit> =
        Result.failure(UnsupportedOperationException("createWithPoints is not supported by ${this::class.simpleName}"))

    /** Appends [points] to the track with id [trackId]. Never replaces previously-appended points. */
    suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit>

    /** Marks the track with id [trackId] as finished at [endedAtEpochMillis]. */
    suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit>

    /**
     * Marks the track with id [trackId] as finished at [endedAtEpochMillis] **only if it has no end
     * time yet**, and says whether it wrote: `true` if the row was open and is now ended, `false`
     * if it was already ended or does not exist. Unlike [end], it can never replace an end time
     * that is already there (dispatch 2026-09-28-400, Amendment 3, ruling C).
     *
     * The default answers "unsupported", explicitly, so an implementation that has not been given
     * this cannot be mistaken for one that ended nothing.
     */
    suspend fun endIfOpen(trackId: String, endedAtEpochMillis: Long): Result<Boolean> =
        Result.failure(UnsupportedOperationException("endIfOpen is not supported by ${this::class.simpleName}"))

    /**
     * Points [com.zynergylabs.forager.app.domain.model.Track.originWaypointId] at [waypointId] — the write path
     * for that column (navigation HUD stage one), run once by `TrackRecordingViewModel` after it
     * creates the origin waypoint from the first accuracy-gated fix. A no-op, not a failure, if no
     * such track exists.
     */
    suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit>

    /** Removes the track with this id and all its points. A no-op, not a failure, if none exists. */
    suspend fun delete(id: String): Result<Unit>
}
