package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/** Loads every log entry, most recently found first — the natural browsing order for a field log. */
class GetMushroomLogEntriesUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(): Result<List<MushroomLogEntry>> = repository.getAll().map { entries ->
        entries.sortedByDescending { it.foundOn }
    }
}
