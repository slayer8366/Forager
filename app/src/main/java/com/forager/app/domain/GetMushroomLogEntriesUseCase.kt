package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/**
 * Loads every *committed* log entry, most recently found first — the natural browsing order for a
 * field log. Filters out [MushroomLogEntry.isDraft] rows (Workstream L4b, owner decision #6:
 * "nothing appears in the log until the user has put something there") — [MushroomLogRepository.getAll]
 * itself stays a raw, unfiltered read (every other caller, and every repository-level test, wants
 * every row regardless of draft state), so this use case is the one deliberate place that policy is
 * applied. See [GetOrphanedDraftEntriesUseCase] for the complementary read.
 */
class GetMushroomLogEntriesUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(): Result<List<MushroomLogEntry>> = repository.getAll().map { entries ->
        entries.filterNot { it.isDraft }.sortedByDescending { it.foundOn }
    }
}
