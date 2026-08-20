package com.forager.app.domain

import com.forager.app.domain.model.Track
import java.util.UUID

/**
 * Starts a new, empty track and persists it immediately — the same "exists in storage from the
 * moment it's started" reasoning as [CreateMushroomLogEntryUseCase], so a crash or a killed
 * foreground service mid-recording still leaves the track (and whatever points were appended
 * before the crash) on disk rather than losing the whole recording.
 *
 * [currentTime] and [idGenerator] are injected for the same reason as [CreateMushroomLogEntryUseCase]:
 * a test can fix both instead of racing the clock or asserting against a random id.
 */
class StartTrackUseCase(
    private val repository: TrackRepository,
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(name: String? = null): Result<Track> {
        val track = Track(
            id = idGenerator(),
            name = name,
            startedAtEpochMillis = currentTime.nowEpochMillis(),
            endedAtEpochMillis = null,
            points = emptyList(),
        )
        return repository.create(track).map { track }
    }
}
