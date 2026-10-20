package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.EntryContents
import com.zynergylabs.forager.app.domain.EntryFindItem
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.EntryOfflineMapItem
import com.zynergylabs.forager.app.domain.EntryTrackItem
import com.zynergylabs.forager.app.domain.EntryWaypointItem
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.entryContentsOf
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.ui.log.CartographyViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The open entry's edits in [CartographyViewModel] (UI split Phase 2.8, PR 2, dispatch 2026-10-19-34,
 * RECORD -1460): text, tags, the four per-item decisions, the group switch, the photo toggle, and
 * [persist], which every one of them ends in, moved out of the ViewModel unchanged. It holds no state of
 * its own. Every value it reads from the screen's state comes through [uiState], every write goes
 * through [updateState], and the draft autosave launches in [scope], the ViewModel's own
 * `viewModelScope`, read each time rather than kept. It holds no job and cancels none.
 */
internal class CartographyEntryEdits(
    private val uiState: () -> CartographyUiState,
    private val updateState: ((CartographyUiState) -> CartographyUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val saveEntry: SaveCartographyEntryUseCase,
    private val computeTrackStatistics: ComputeTrackStatisticsUseCase,
    /** Injected so a test can fix when a photo attachment is stamped: the ViewModel's own `now`. */
    private val now: () -> Long,
) {

    fun onTextChanged(text: String) {
        val entry = uiState().editingEntry ?: return
        persist(entry.copy(text = text))
    }

    fun onTagsChanged(tags: List<String>) {
        val entry = uiState().editingEntry ?: return
        persist(entry.copy(tags = tags))
    }

    /** Sets (or changes) the decision on find [findId] to [kept] — see [CartographyViewModel]'s own doc comment on why this takes an explicit target rather than toggling. */
    fun onSetFindDecision(findId: String, kept: Boolean) {
        val state = uiState()
        val entry = state.editingEntry ?: return
        val decision = entry.findDecisions.firstOrNull { it.findId == findId }?.copy(kept = kept)
            ?: state.candidatesForEditingEntry?.finds?.firstOrNull { it.id == findId }?.toDecision(kept = kept)
            ?: return
        persist(entry.copy(findDecisions = entry.findDecisions.filterNot { it.findId == findId } + decision))
    }

    fun onSetTrackDecision(trackId: String, kept: Boolean) {
        val state = uiState()
        val entry = state.editingEntry ?: return
        val decision = entry.trackDecisions.firstOrNull { it.trackId == trackId }?.copy(kept = kept)
            ?: state.candidatesForEditingEntry?.tracks?.firstOrNull { it.id == trackId }?.toDecision(kept = kept, computeTrackStatistics)
            ?: return
        persist(entry.copy(trackDecisions = entry.trackDecisions.filterNot { it.trackId == trackId } + decision))
    }

    fun onSetWaypointDecision(waypointId: String, kept: Boolean) {
        val state = uiState()
        val entry = state.editingEntry ?: return
        val decision = entry.waypointDecisions.firstOrNull { it.waypointId == waypointId }?.copy(kept = kept)
            ?: state.candidatesForEditingEntry?.waypoints?.firstOrNull { it.id == waypointId }?.toDecision(kept = kept)
            ?: return
        persist(entry.copy(waypointDecisions = entry.waypointDecisions.filterNot { it.waypointId == waypointId } + decision))
    }

    fun onSetOfflineRegionDecision(offlineRegionId: Long, kept: Boolean) {
        val state = uiState()
        val entry = state.editingEntry ?: return
        val decision = entry.offlineRegionDecisions.firstOrNull { it.offlineRegionId == offlineRegionId }?.copy(kept = kept)
            ?: state.candidateOfflineRegionsForEditingEntry.firstOrNull { it.id == offlineRegionId }?.toDecision(kept = kept)
            ?: return
        persist(entry.copy(offlineRegionDecisions = entry.offlineRegionDecisions.filterNot { it.offlineRegionId == offlineRegionId } + decision))
    }

    /**
     * A group switch in the editor's "In this entry" panel (dispatch 2026-09-28-667, data part A; the
     * owner in RECORD -656: "Summary first, open to adjust (Recommended)"): includes, or leaves out, every
     * item [EntryContents.members] puts in [group], its new ones too, as one change. A draft saves once
     * for the whole group, not once per item. What an included or left-out item then does is unchanged:
     * this writes the same decisions, with the same `kept` values, that the per-item setters above write.
     *
     * An existing decision is changed where it stands rather than moved last as the per-item setters do,
     * so a switch already in the asked-for state changes nothing and does not mark a saved entry unsaved.
     * A group with nothing in it is logged and ignored: the panel shows no switch for one.
     */
    fun onSetEntryGroupIncluded(group: EntryGroup, included: Boolean) {
        val state = uiState()
        val entry = state.editingEntry ?: return
        val contents = entryContentsOf(entry, state.candidatesForEditingEntry, state.candidateOfflineRegionsForEditingEntry, computeTrackStatistics)
        val members = contents.members(group)
        if (members.isEmpty()) {
            Log.w(TAG, "A switch was set for the empty group $group of entry '${entry.id}'; nothing changed.")
            return
        }
        val candidates = state.candidatesForEditingEntry
        var updated = entry
        for (item in members) {
            val changed = when (item) {
                is EntryTrackItem -> updated.trackDecisions.setOrAdd(
                    { it.trackId == item.id },
                    { it.copy(kept = included) },
                    { candidates?.tracks?.firstOrNull { it.id == item.id }?.toDecision(kept = included, computeTrackStatistics) },
                )?.let { updated.copy(trackDecisions = it) }
                is EntryWaypointItem -> updated.waypointDecisions.setOrAdd(
                    { it.waypointId == item.id },
                    { it.copy(kept = included) },
                    { candidates?.waypoints?.firstOrNull { it.id == item.id }?.toDecision(kept = included) },
                )?.let { updated.copy(waypointDecisions = it) }
                is EntryFindItem -> updated.findDecisions.setOrAdd(
                    { it.findId == item.id },
                    { it.copy(kept = included) },
                    { candidates?.finds?.firstOrNull { it.id == item.id }?.toDecision(kept = included) },
                )?.let { updated.copy(findDecisions = it) }
                is EntryOfflineMapItem -> updated.offlineRegionDecisions.setOrAdd(
                    { it.offlineRegionId == item.regionId },
                    { it.copy(kept = included) },
                    { state.candidateOfflineRegionsForEditingEntry.firstOrNull { it.id == item.regionId }?.toDecision(kept = included) },
                )?.let { updated.copy(offlineRegionDecisions = it) }
            }
            if (changed == null) {
                Log.w(TAG, "Group $group of entry '${entry.id}' lists '${item.id}', which has no decision and is no longer a candidate; it was skipped.")
            } else {
                updated = changed
            }
        }
        if (updated != entry) persist(updated)
    }

    /**
     * Attaches or detaches [photoId] (from the standalone photo library) — see
     * [CartographyEntry.photos]'s own doc comment for why this is a plain toggle, not a three-state
     * decision the way the trip-report candidates above are: a photo has no candidate pool to be
     * undecided about. No candidate lookup needed either direction: a photo id alone is enough to
     * attach it, stamped with [now].
     */
    fun onToggleKeptPhoto(photoId: String) {
        val entry = uiState().editingEntry ?: return
        persist(
            if (entry.photos.any { it.photoId == photoId }) {
                entry.copy(photos = entry.photos.filterNot { it.photoId == photoId })
            } else {
                entry.copy(photos = entry.photos + PhotoAttachment(photoId = photoId, attachedAtEpochMillis = now()))
            },
        )
    }

    /**
     * Reflects [entry] immediately (so typing/deciding never feels laggy). **Drafts** then autosave
     * unconditionally, unchanged from before this dispatch — a draft is in-progress by definition,
     * and silent autosave onto its own row is deliberate, correct behaviour (device-check patch,
     * Item 1: "drafts autosave silently... must not change"). **Committed** entries do not: the
     * owner's new policy makes their changes explicit, so this only marks
     * [CartographyUiState.hasUnsavedChanges] and leaves the actual write to [CartographyViewModel.onSaveEntry] — the
     * screen's own Save action, the leave-prompt's Save option, or the backgrounding hook in
     * `AvailabilityScreen`, never a keystroke.
     */
    private fun persist(entry: CartographyEntry) {
        if (entry.isDraft) {
            updateState { it.copy(editingEntry = entry) }
            scope().launch {
                saveEntry(entry).fold(
                    onSuccess = { updateState { it.copy(saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't save entry '${entry.id}'.", error)
                        updateState { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                    },
                )
            }
        } else {
            updateState { it.copy(editingEntry = entry, hasUnsavedChanges = true) }
        }
    }
}
