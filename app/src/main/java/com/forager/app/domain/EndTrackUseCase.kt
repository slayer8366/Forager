package com.forager.app.domain

/** Marks a track finished at the current time. [currentTime] is injected so a test can fix it. */
class EndTrackUseCase(
    private val repository: TrackRepository,
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
) {
    suspend operator fun invoke(trackId: String): Result<Unit> =
        repository.end(trackId, currentTime.nowEpochMillis())
}
