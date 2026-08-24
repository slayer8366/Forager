package com.forager.app.domain

import com.forager.app.domain.model.TrackPoint

/**
 * Appends already-sampled points to a track. A one-line wrapper, kept as its own class per the
 * one-class-one-job pattern used throughout `domain/` (see [DeletePlannedTripUseCase]) — the
 * sampling decision itself belongs to [LocationSampler], upstream of this call, not here.
 */
class RecordTrackPointsUseCase(
    private val repository: TrackRepository,
) {
    suspend operator fun invoke(trackId: String, points: List<TrackPoint>): Result<Unit> {
        if (points.isEmpty()) return Result.success(Unit)
        return repository.appendPoints(trackId, points)
    }
}
