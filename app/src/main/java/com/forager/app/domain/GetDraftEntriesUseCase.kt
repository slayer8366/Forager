package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/**
 * Loads every [MushroomLogEntry.isDraft] row currently stored — Workstream L4b, **renamed and
 * broadened 2026-08-25 (L4b-R)**: no longer a crash-recovery special case, this is now the feed for
 * the Drafts filter/section itself (owner decision 2026-08-25: "unsaved work is held in a Drafts
 * section"). A row can be draft here for either reason a live edit session or a crash left one:
 * - **Live**: a brand-new entry not yet committed, or a re-edit's separate draft row (see
 *   [MushroomLogEntry.draftOfEntryId]) — both still open for editing right now, or persisted after
 *   an incidental exit per owner decision 2026-08-25 ("leaving without answering... persists the
 *   draft, it does not commit"). Every clean exit (Save or Cancel) removes its row from this set one
 *   way or another.
 * - **Orphaned**: a crash killed the process before any exit ran. Indistinguishable from a live,
 *   incidentally-exited draft by this column alone — both are simply "not yet resolved" — which is
 *   exactly why decision 2026-08-25 puts both in the same Drafts section rather than a separate
 *   crash-recovery UI: from the user's perspective they're the same thing, unsaved work waiting to
 *   be finished or discarded.
 *
 * The complement of [GetMushroomLogEntriesUseCase], which excludes exactly what this includes.
 */
class GetDraftEntriesUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(): Result<List<MushroomLogEntry>> = repository.getAll().map { entries ->
        entries.filter { it.isDraft }
    }
}
