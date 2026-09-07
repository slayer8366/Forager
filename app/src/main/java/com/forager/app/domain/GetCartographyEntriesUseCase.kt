package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/**
 * Loads every committed Cartography entry, **newest day first**, with
 * [CartographyEntry.updatedAtEpochMillis] descending only as the tie-break between entries on the
 * same day — the Entries submenu's own feed.
 *
 * **This deliberately reverses the previous "most recently updated first"** (plate pulse, owner
 * ruling on item 2, `docs/audits/2026-09-07-cartography-plate-renderer-pulse.md`). That order was
 * the stated intent when this was written, and it was never wrong by accident: it inherited the
 * modification time as a sort key because the field was the only timestamp on the entity. Once
 * [SaveCartographyEntryUseCase] stamps that field on every save, sorting on it would move a day
 * around the feed because a typo was fixed — and one field cannot honestly be both a modification
 * time and a sort key. An entry *is* a day ([CartographyEntry.date]), so the day is the order.
 * Sorted on the existing `date` column rather than a new created-at column: no migration, and it
 * matches what an entry is.
 *
 * The tie-break exists because nothing prevents two entries on one date — `onStartEntry` never
 * checks for an existing one and the DAO has no query by date (the pulse's addendum records this
 * as a queued finding, not this dispatch's). Two same-day entries are rare, and the recently
 * touched one first is a sensible secondary. [GetCartographyDraftEntriesUseCase] is unchanged —
 * "most recently touched first" is arguably right for unfinished work, and the ruling was about
 * this feed.
 */
class GetCartographyEntriesUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend operator fun invoke(): Result<List<CartographyEntry>> = repository.getAll().map { entries ->
        entries.sortedWith(compareByDescending<CartographyEntry> { it.date }.thenByDescending { it.updatedAtEpochMillis })
    }
}
