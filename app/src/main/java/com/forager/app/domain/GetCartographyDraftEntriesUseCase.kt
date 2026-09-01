package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/** Loads every unfinished Cartography entry, most recently updated first — the Drafts submenu's own feed. The complement of [GetCartographyEntriesUseCase]. */
class GetCartographyDraftEntriesUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend operator fun invoke(): Result<List<CartographyEntry>> = repository.getAllDrafts().map { entries ->
        entries.sortedByDescending { it.updatedAtEpochMillis }
    }
}
