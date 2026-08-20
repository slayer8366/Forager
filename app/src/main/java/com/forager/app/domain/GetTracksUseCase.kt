package com.forager.app.domain

import com.forager.app.domain.model.Track

/** Every recorded track. A one-line wrapper — see [DeleteMushroomLogEntryUseCase]'s doc comment for why one exists at all. */
class GetTracksUseCase(
    private val repository: TrackRepository,
) {
    suspend operator fun invoke(): Result<List<Track>> = repository.getAll()
}
