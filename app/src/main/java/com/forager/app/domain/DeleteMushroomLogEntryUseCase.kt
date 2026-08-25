package com.forager.app.domain

/**
 * Removes a log entry by [id] — its own row and its photo *references*, never the referenced
 * photos' files or gallery rows (owner decision, 2026-08-22: gallery ownership — see
 * [com.forager.app.domain.model.LogPhoto]'s own doc comment).
 *
 * **This is a reversal, not a bug fix.** Before `MIGRATION_7_8`, this use case also deleted the
 * entry's photo files via [PhotoStore] — correct at the time, because a photo row's only reason to
 * exist was one entry's ownership of it, so an orphaned file really was a leak (see this file's own
 * git history / the L1 workstream that added that behavior). Under gallery ownership a photo
 * persists whether or not any entry still references it, so deleting an entry deleting its files
 * too would now be the bug: *"people need to trust the app to behave the way other apps do — a
 * photo they took shouldn't vanish because they deleted a journal entry that happened to mention
 * it"* (owner's own reasoning, preserved here since it's what makes this the correct behavior, not
 * an arbitrary reversal). [PhotoStore] is no longer a dependency of this use case at all — nothing
 * left here calls it.
 */
class DeleteMushroomLogEntryUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}
