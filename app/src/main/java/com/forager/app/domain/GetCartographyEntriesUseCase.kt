package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/** Loads every committed Cartography entry, most recently updated first — the Entries submenu's own feed. */
class GetCartographyEntriesUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend operator fun invoke(): Result<List<CartographyEntry>> = repository.getAll().map { entries ->
        entries.sortedByDescending { it.updatedAtEpochMillis }
    }
}
