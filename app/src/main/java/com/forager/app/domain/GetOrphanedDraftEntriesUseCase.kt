package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/**
 * Loads every [MushroomLogEntry.isDraft] row currently stored — Workstream L4b crash recovery.
 * Under normal operation an `isDraft` row is transient: it exists only while
 * [MushroomLogViewModel] holds that entry open for editing, and every clean exit (Save, Cancel, or
 * an incidental exit) resolves it one way or another before the ViewModel would ever be recreated.
 * The only way one can still be `isDraft` at a fresh [MushroomLogViewModel.init] — the one moment
 * this use case is called — is a crash that killed the process before any exit ran, which is why
 * every row this returns is treated as orphaned rather than "someone else is editing this right
 * now": nothing else in this single-process app could be.
 *
 * The complement of [GetMushroomLogEntriesUseCase], which excludes exactly what this includes.
 */
class GetOrphanedDraftEntriesUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(): Result<List<MushroomLogEntry>> = repository.getAll().map { entries ->
        entries.filter { it.isDraft }
    }
}
