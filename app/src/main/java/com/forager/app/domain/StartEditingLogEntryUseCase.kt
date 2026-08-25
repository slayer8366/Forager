package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry
import java.util.UUID

/**
 * Workstream L4b-R: begins editing [committed] — the moment the user taps "Edit entry" on an
 * already-committed entry's report, not the moment [MushroomLogEntry.isDraft] edits actually start.
 * If [committed] is already a draft (a brand-new entry not yet saved, or a draft reopened from the
 * Drafts section), this is a no-op — there is already a row to write edits to. Otherwise, creates a
 * **separate** draft row seeded as an exact copy of [committed], with a fresh [MushroomLogEntry.id]
 * and [MushroomLogEntry.draftOfEntryId] pointing back at [committed]'s own id, and persists it
 * immediately.
 *
 * This is the mechanism that lets the committed row stay completely untouched — and therefore
 * visible in the log with its last-saved values — for the entire time the draft is being edited
 * (owner decision, 2026-08-25): every subsequent [SaveMushroomLogEntryUseCase] call during this
 * session writes to the new draft's id, never [committed]'s.
 *
 * [idGenerator] is injected for the same reason as [CreateMushroomLogEntryUseCase]'s: a test can fix
 * it instead of asserting against a random id.
 */
class StartEditingLogEntryUseCase(
    private val repository: MushroomLogRepository,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(committed: MushroomLogEntry): Result<MushroomLogEntry> {
        if (committed.isDraft) return Result.success(committed)
        val draft = committed.copy(id = idGenerator(), isDraft = true, draftOfEntryId = committed.id)
        return repository.save(draft).map { draft }
    }
}
