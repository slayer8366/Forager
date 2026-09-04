package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/**
 * Finishes [draft] — "a draft is an unfinished entry," so finishing one is simply flipping
 * [CartographyEntry.isDraft] to `false` and saving, same row, same id. No repoint step: unlike a
 * find's re-edit ([CommitDraftEntryUseCase]), a Cartography entry has no separate shadow-draft row to
 * merge back — see [CartographyEntry]'s own doc comment for why that mechanism doesn't apply here.
 */
class CommitCartographyEntryUseCase(
    private val repository: CartographyEntryRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(draft: CartographyEntry): Result<CartographyEntry> {
        val committed = draft.copy(isDraft = false, updatedAtEpochMillis = now())
        return repository.save(committed).map { committed }
    }
}
