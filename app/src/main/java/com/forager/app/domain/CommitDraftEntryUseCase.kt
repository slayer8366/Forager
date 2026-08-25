package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/**
 * Workstream L4b-R: Save. Commits [draft] — a brand-new entry's own draft row (`draftOfEntryId ==
 * null`) or a re-edit's separate draft row (`draftOfEntryId` pointing at the committed entry it
 * drafts) — onto the log. Builds the committed content under the correct id (the parent's, when
 * there is one; the draft's own, when there isn't — see [MushroomLogEntry.draftOfEntryId]'s own doc
 * comment) and delegates the actual transaction — upsert, photo-reference repoint, draft-row
 * removal — to [MushroomLogRepository.commitDraft]. See that method's own doc comment, and
 * `MushroomLogDao.commitDraft`'s, for why this must be one transaction.
 */
class CommitDraftEntryUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(draft: MushroomLogEntry): Result<MushroomLogEntry> {
        val committed = draft.copy(id = draft.draftOfEntryId ?: draft.id, isDraft = false, draftOfEntryId = null)
        return repository.commitDraft(draftId = draft.id, committed = committed).map { committed }
    }
}
