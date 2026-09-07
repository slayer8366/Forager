package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/**
 * Per-edit autosave write for an open Cartography entry (writing text, tags, or the kept-item
 * selection) — the same per-keystroke-write shape [MushroomLogRepository.save] serves for a find's
 * draft, just without a separate commit-time repoint step: a Cartography entry has no shadow-draft
 * mechanism (see [CartographyEntry]'s own doc comment), so this is the *only* write for an entry that
 * is still a draft, and — since editing a committed entry happens in place — for one that isn't.
 *
 * **Stamps [CartographyEntry.updatedAtEpochMillis] with [now] on every save** (plate pulse, owner
 * ruling on item 2). Before this, the field was written only by [CreateCartographyEntryUseCase] and
 * [CommitCartographyEntryUseCase], so a committed entry edited in place kept its commit time
 * forever — a modification time that did not record modifications. It is stamped here, at the one
 * seam every save passes through, rather than at each caller, so no caller can forget; the same
 * reason the network-fix rule lives at `RoomTrackRepository`'s read seam. The stamped entry is
 * what this returns, and it is the copy a caller should keep — the one it passed in is already out
 * of date by one field. (`CartographyViewModel.persist` deliberately does *not* write the stamped
 * copy back over `editingEntry` for a draft's autosave: that write completes asynchronously, and a
 * keystroke typed in between would be clobbered by the older text. The draft's in-memory stamp is
 * stale until the next load, which is the honest cost; the row on disk is right.)
 *
 * This field is no longer the Entries feed's sort key — see [GetCartographyEntriesUseCase] — which
 * is what made stamping it on every save safe: an edit no longer moves a day around in the feed.
 */
class SaveCartographyEntryUseCase(
    private val repository: CartographyEntryRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(entry: CartographyEntry): Result<CartographyEntry> {
        val stamped = entry.copy(updatedAtEpochMillis = now())
        return repository.save(stamped).map { stamped }
    }
}
