package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/**
 * Per-edit autosave write for an open Cartography entry (writing text, tags, or the kept-item
 * selection) — the same per-keystroke-write shape [MushroomLogRepository.save] serves for a find's
 * draft, just without a separate commit-time repoint step: a Cartography entry has no shadow-draft
 * mechanism (see [CartographyEntry]'s own doc comment), so this is the *only* write for an entry that
 * is still a draft, and — since editing a committed entry happens in place — for one that isn't.
 */
class SaveCartographyEntryUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend operator fun invoke(entry: CartographyEntry): Result<CartographyEntry> =
        repository.save(entry).map { entry }
}
