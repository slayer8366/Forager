package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/**
 * Loads every *committed* log entry, most recently found first — the natural browsing order for a
 * field log. Filters out [MushroomLogEntry.isDraft] rows (Workstream L4b, owner decision #6:
 * "a draft never appears in the log") — [MushroomLogRepository.getAll] itself stays a raw,
 * unfiltered read (every other caller, and every repository-level test, wants every row regardless
 * of draft state), so this use case is the one deliberate place that policy is applied. See
 * [GetDraftEntriesUseCase] for the complementary read.
 *
 * Workstream L4b-R: this filter alone is enough to keep a committed entry visible with its
 * last-saved values for the entire time it's being re-edited (owner decision, 2026-08-25) — no
 * special-casing needed here, because a re-edit's draft is a *separate* row (see
 * [MushroomLogEntry.draftOfEntryId]) that never touches the committed one until Save. The committed
 * row's `isDraft` stays `false` throughout, so it's never excluded by this filter in the first
 * place.
 */
class GetMushroomLogEntriesUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(): Result<List<MushroomLogEntry>> = repository.getAll().map { entries ->
        entries.filterNot { it.isDraft }.sortedByDescending { it.foundOn }
    }
}
