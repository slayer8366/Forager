package com.forager.app.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteGalleryPhotoUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetGalleryPhotosUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.GetOrphanedDraftEntriesUseCase
import com.forager.app.domain.PullPhotoIntoEntryUseCase
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the mushroom log's list/create/edit state. Kept as its own `ViewModel` rather than folded
 * into [com.forager.app.ui.availability.AvailabilityViewModel]: the log is reachable from the
 * drawer as its own destination (see `docs/plans/mushroom-log.md`'s Navigation section) and is
 * functionally independent of the availability search — the two never need to observe each other's
 * state, only [com.forager.app.MainActivity] wires them together (the map's "Log a find" option
 * calls into this ViewModel with a location the availability screen reported).
 *
 * ## Persisted drafts, not autosave-always-commits (Workstream L4b, owner decision 2026-08-22)
 *
 * Autosave-on-every-keystroke does not go away as *infrastructure* — [onEntryEdited] still writes
 * on every call, the same cheap-local-SQLite-write reasoning that motivated it in the first place
 * (a field app has no guarantee of a graceful exit). What changes is *what* that write commits to:
 * every write while an entry is open for editing lands with [MushroomLogEntry.isDraft] `true`, and
 * the entry is only visible via [GetMushroomLogEntriesUseCase] — "the log" — once one of the three
 * exits below resolves it. Storage shape is a single discriminator column, not a second table or a
 * change-list (owner decision: a flag fails loudly — a query that forgets to filter shows a stray
 * entry — where the rejected alternatives fail silently; see [com.forager.app.data.local.MushroomLogEntryEntity.isDraft]'s
 * own doc comment for the full reasoning).
 *
 * **The three exits** — [onSaveEntry], [onCancelEditing], [onLeaveEditingIncidentally] — are the
 * only ways an open edit session resolves:
 * - **Save** commits whatever is currently in the form (`isDraft = false`) and keeps the form open,
 *   showing the committed result.
 * - **Cancel** is the only exit that discards anything. For an entry that was never committed
 *   before this session ([editingEntryIsFreshlyCreated]), it deletes the row outright — there is no
 *   "last saved state" to revert to. For an entry reopened from an already-committed row, it
 *   restores [editingEntrySnapshot] — the entry exactly as it looked the moment it was opened,
 *   held only in this ViewModel's memory, never a second persisted table — and reverses any photo
 *   attach/detach made during the session by diffing the snapshot's photo set against the current
 *   one (attach/detach already write live cross-reference rows; this undoes exactly the rows this
 *   session changed, never touching the gallery photo itself — see [revertPhotosToSnapshot]).
 * - **Leaving without answering** (tab switch, backgrounding, the back arrow — never the explicit
 *   Cancel button) auto-saves: commits current content, or — if the entry was never committed and
 *   is still byte-for-byte the pristine draft it started as — deletes it, so tapping "+" and
 *   leaving immediately leaves nothing visible (owner decision #6).
 *
 * **The known hazard, resolved deliberately:** [loadEntries] used to re-derive [MushroomLogUiState.editingEntry]
 * wholesale from a fresh repository read (G3) — safe only because nothing uncommitted existed to
 * lose at the time. Under this model that would silently discard in-progress typing on every
 * refresh, including [onDeleteGalleryPhoto]'s own success-path refresh. [loadEntries] now merges in
 * only [MushroomLogEntry.photos] — the one field G3's refresh existed to keep current — onto
 * whatever [MushroomLogUiState.editingEntry] already holds, never replacing the rest of it.
 */
class MushroomLogViewModel(
    private val getEntries: GetMushroomLogEntriesUseCase,
    private val getOrphanedDraftEntries: GetOrphanedDraftEntriesUseCase,
    private val createEntry: CreateMushroomLogEntryUseCase,
    private val saveEntry: SaveMushroomLogEntryUseCase,
    private val deleteEntry: DeleteMushroomLogEntryUseCase,
    private val addPhoto: AddPhotoToLogEntryUseCase,
    private val removePhoto: RemovePhotoFromLogEntryUseCase,
    private val getGalleryPhotos: GetGalleryPhotosUseCase,
    private val pullPhotoIntoEntry: PullPhotoIntoEntryUseCase,
    private val deleteGalleryPhoto: DeleteGalleryPhotoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MushroomLogUiState())
    val uiState: StateFlow<MushroomLogUiState> = _uiState.asStateFlow()

    /**
     * [MushroomLogUiState.editingEntry] exactly as it looked the moment it was opened for editing
     * (via [onOpenEntry]) or created (via [onStartNewEntry]) — this session's only "last saved
     * state," held in memory only. `null` whenever nothing is open. Never persisted: a crash loses
     * it the same way it loses any other in-memory ViewModel state, which is why a crash-recovered
     * draft (see [GetOrphanedDraftEntriesUseCase]) re-opens with *itself* as the new baseline rather
     * than promising a revert further back than where the user resumed from — that older state was
     * never recoverable under the "no second table" storage shape, and reinstating a draft as-is is
     * still strictly non-destructive (nothing is deleted without an explicit [onDeleteEntry]).
     */
    private var editingEntrySnapshot: MushroomLogEntry? = null

    /**
     * `true` exactly when the currently-open entry was created by *this* session's [onStartNewEntry]
     * and has never been committed — the one case [onCancelEditing] deletes outright instead of
     * restoring [editingEntrySnapshot]. Reopening any existing row, committed or an orphaned draft,
     * always sets this `false`: only direct deletion ([onDeleteEntry]) may remove a row this
     * ViewModel didn't itself just create moments ago.
     */
    private var editingEntryIsFreshlyCreated = false

    init {
        loadEntries()
        loadGalleryPhotos()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEntries = true, loadErrorMessage = null) }
            getEntries().fold(
                onSuccess = { entries ->
                    val drafts = getOrphanedDraftEntries().getOrElse { error ->
                        Log.w(TAG, "Couldn't load orphaned drafts.", error)
                        _uiState.value.draftEntries
                    }
                    _uiState.update { state ->
                        // Merges in only the freshest `photos` for the open entry (found in either
                        // list, regardless of its own draft state) — never replaces the rest of
                        // editingEntry, so in-progress typing survives a refresh. See this class's
                        // own doc comment on the loadEntries hazard for why a wholesale replacement
                        // (G3's original shape) is no longer safe once drafts exist.
                        val editing = state.editingEntry
                        val freshPhotos = editing?.let { (entries + drafts).firstOrNull { fresh -> fresh.id == editing.id }?.photos }
                        state.copy(
                            entries = entries,
                            draftEntries = drafts,
                            editingEntry = if (freshPhotos != null) editing.copy(photos = freshPhotos) else editing,
                            isLoadingEntries = false,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't load the mushroom log.", error)
                    _uiState.update {
                        // Not belief-changing — the entries are on disk; only the read failed. See
                        // docs/error-presentation-spec.md's per-field table: neutral "unavailable"
                        // wording that doesn't imply anything was lost, not "Couldn't load...".
                        it.copy(isLoadingEntries = false, loadErrorMessage = "Log entries unavailable.")
                    }
                },
            )
        }
    }

    /** Loads [MushroomLogUiState.galleryPhotos] for [PhotoGalleryScreen] — Workstream G2, independent of [loadEntries] (see [MushroomLogUiState]'s own doc comment on why the two get separate loading/error fields). */
    fun loadGalleryPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGalleryPhotos = true, galleryLoadErrorMessage = null) }
            getGalleryPhotos().fold(
                onSuccess = { photos ->
                    _uiState.update { it.copy(galleryPhotos = photos, isLoadingGalleryPhotos = false) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't load the photo gallery.", error)
                    _uiState.update {
                        // Same "not belief-changing" reasoning as loadEntries' own failure path —
                        // the photos are on disk; only the read failed.
                        it.copy(isLoadingGalleryPhotos = false, galleryLoadErrorMessage = "Photo gallery unavailable.")
                    }
                },
            )
        }
    }

    /**
     * Starts and immediately persists a new, entirely-unrecorded entry — at [location] if one is
     * already known, or with none at all — as an uncommitted draft, then opens it for editing.
     * Deliberately never added to [MushroomLogUiState.entries]: that list is committed-only
     * (owner decision #6), so a brand-new entry stays invisible there until one of the three exits
     * commits it.
     */
    fun onStartNewEntry(location: LatLng?, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            createEntry(location, date).fold(
                onSuccess = { entry ->
                    editingEntrySnapshot = entry
                    editingEntryIsFreshlyCreated = true
                    _uiState.update { it.copy(editingEntry = entry, saveErrorMessage = null) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't start a new log entry.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't start a new entry.") }
                },
            )
        }
    }

    /**
     * Opens an already-loaded entry for viewing/editing — found in either [MushroomLogUiState.entries]
     * (the normal case) or [MushroomLogUiState.draftEntries] (reinstating a crash-recovered draft).
     * Either way this is reopening a row that already existed before this call, never one this
     * session just created, so [editingEntryIsFreshlyCreated] is always `false` here — see that
     * field's own doc comment for why that's the correct baseline for a resumed crash-recovered
     * draft as well as a normal re-edit.
     */
    fun onOpenEntry(id: String) {
        val state = _uiState.value
        val entry = state.entries.firstOrNull { it.id == id } ?: state.draftEntries.firstOrNull { it.id == id }
        editingEntrySnapshot = entry
        editingEntryIsFreshlyCreated = false
        _uiState.update { it.copy(editingEntry = entry) }
    }

    /** Returns to the list without resolving whatever is open — see [onSaveEntry]/[onCancelEditing]/[onLeaveEditingIncidentally] for the ways an edit session actually resolves; this alone is only correct for closing a *report* (nothing edited) view. */
    fun onCloseEntry() {
        editingEntrySnapshot = null
        editingEntryIsFreshlyCreated = false
        _uiState.update { it.copy(editingEntry = null) }
    }

    /** Persists [updated] as a draft (`isDraft = true`) and reflects it in [MushroomLogUiState.editingEntry] — never in [MushroomLogUiState.entries] while a draft, per [replacing]. See this class's doc comment on the persisted-draft model. */
    fun onEntryEdited(updated: MushroomLogEntry) {
        val draft = updated.copy(isDraft = true)
        _uiState.update { it.replacing(draft) }
        viewModelScope.launch {
            saveEntry(draft).fold(
                onSuccess = { _uiState.update { it.copy(saveErrorMessage = null) } },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save entry '${updated.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                },
            )
        }
    }

    /** Save — commits the currently-open entry (`isDraft = false`) and keeps it open, now showing the committed result. A no-op if nothing is open. */
    fun onSaveEntry() {
        val current = _uiState.value.editingEntry ?: return
        val committed = current.copy(isDraft = false)
        viewModelScope.launch {
            saveEntry(committed).fold(
                onSuccess = {
                    editingEntrySnapshot = committed
                    editingEntryIsFreshlyCreated = false
                    _uiState.update { it.replacing(committed).copy(saveErrorMessage = null) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save entry '${current.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                },
            )
        }
    }

    /**
     * Cancel — the only exit that throws anything away. A freshly-created, never-committed entry
     * is deleted outright (there is no prior state to revert to); an entry reopened from an
     * already-committed row is restored to [editingEntrySnapshot], including reversing any photo
     * attach/detach made this session (see [revertPhotosToSnapshot]) — the gallery photo itself is
     * never touched either way, only the reference. A no-op if nothing is open.
     */
    fun onCancelEditing() {
        val current = _uiState.value.editingEntry ?: return
        if (editingEntryIsFreshlyCreated) {
            onDeleteEntry(current.id)
            return
        }
        val snapshot = editingEntrySnapshot ?: return
        viewModelScope.launch {
            val restoredPhotos = revertPhotosToSnapshot(current, snapshot)
            val restored = snapshot.copy(photos = restoredPhotos)
            saveEntry(restored).fold(
                onSuccess = {
                    editingEntrySnapshot = null
                    editingEntryIsFreshlyCreated = false
                    _uiState.update { it.replacing(restored).copy(editingEntry = null, saveErrorMessage = null) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't undo changes to entry '${current.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't undo your changes.") }
                },
            )
        }
    }

    /**
     * Leaving without answering — tab switch, backgrounding, or the back arrow, never the explicit
     * Cancel button. Auto-saves: commits whatever is currently in the form, unless the entry was
     * never committed and is still byte-for-byte the pristine draft it started as, in which case it
     * is deleted instead — so tapping "+" and leaving immediately leaves nothing visible (owner
     * decision #6). A no-op if nothing is open.
     */
    fun onLeaveEditingIncidentally() {
        val current = _uiState.value.editingEntry ?: return
        if (editingEntryIsFreshlyCreated && current == editingEntrySnapshot) {
            onDeleteEntry(current.id)
            return
        }
        val committed = current.copy(isDraft = false)
        viewModelScope.launch {
            saveEntry(committed).fold(
                onSuccess = {
                    editingEntrySnapshot = null
                    editingEntryIsFreshlyCreated = false
                    _uiState.update { it.replacing(committed).copy(editingEntry = null, saveErrorMessage = null) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save entry '${current.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                },
            )
        }
    }

    /**
     * Removes the entry's own row and its photo *references* only — see
     * [DeleteMushroomLogEntryUseCase]'s own doc comment for why this reversed L1's original
     * file-deleting behavior under gallery ownership. No photo lookup needed any more: nothing left
     * downstream of [deleteEntry] touches the entry's photos.
     */
    fun onDeleteEntry(id: String) {
        viewModelScope.launch {
            deleteEntry(id).fold(
                onSuccess = {
                    if (_uiState.value.editingEntry?.id == id) {
                        editingEntrySnapshot = null
                        editingEntryIsFreshlyCreated = false
                    }
                    _uiState.update { state ->
                        state.copy(
                            entries = state.entries.filterNot { it.id == id },
                            draftEntries = state.draftEntries.filterNot { it.id == id },
                            editingEntry = state.editingEntry?.takeUnless { it.id == id },
                            saveErrorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't delete entry '$id'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't delete that entry.") }
                },
            )
        }
    }

    fun onAddPhoto(source: PhotoSource) {
        val entry = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPhoto = true) }
            addPhoto(entry, source).fold(
                onSuccess = { updated ->
                    _uiState.update { it.replacing(updated).copy(isSavingPhoto = false, saveErrorMessage = null) }
                    // A freshly added photo is a new gallery row PhotoGalleryScreen's already-loaded
                    // state doesn't know about yet — without this, it wouldn't appear there until
                    // the ViewModel is recreated. Detach has no equivalent need: it never removes a
                    // gallery row, only a reference nothing in this screen currently displays.
                    loadGalleryPhotos()
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't attach a photo to entry '${entry.id}'.", error)
                    _uiState.update {
                        it.copy(isSavingPhoto = false, saveErrorMessage = "Couldn't attach that photo.")
                    }
                },
            )
        }
    }

    fun onRemovePhoto(photo: LogPhoto) {
        val entry = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            removePhoto(entry, photo).fold(
                onSuccess = { updated -> _uiState.update { it.replacing(updated).copy(saveErrorMessage = null) } },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't remove a photo from entry '${entry.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't remove that photo.") }
                },
            )
        }
    }

    /** Workstream G3: references an existing gallery [photo] from the currently-editing entry — a reference only, never a new file (see [PullPhotoIntoEntryUseCase]'s own doc comment). */
    fun onPullPhoto(photo: LogPhoto) {
        val entry = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            pullPhotoIntoEntry(entry, photo).fold(
                onSuccess = { updated -> _uiState.update { it.replacing(updated).copy(saveErrorMessage = null) } },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't pull photo '${photo.id}' into entry '${entry.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't add that photo.") }
                },
            )
        }
    }

    /**
     * Workstream G3: deletes [photo] from the gallery — the user has already confirmed, including
     * seeing how many entries reference it (see [com.forager.app.ui.log.PhotoGalleryScreen]'s own
     * confirmation flow). Refreshes both the gallery and the entry list on success: an entry left
     * open in the background (e.g. across a tab switch — nothing closes [MushroomLogUiState.editingEntry]
     * on its own) must not keep showing a reference to a photo that no longer exists. See this
     * class's own doc comment on the [loadEntries] hazard for why that refresh no longer risks
     * discarding in-progress typing.
     */
    fun onDeleteGalleryPhoto(photo: GalleryPhoto) {
        viewModelScope.launch {
            deleteGalleryPhoto(photo.photo) { error ->
                Log.w(TAG, "Couldn't delete the file for photo '${photo.photo.id}'.", error)
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(saveErrorMessage = null) }
                    loadGalleryPhotos()
                    loadEntries()
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't delete photo '${photo.photo.id}' from the gallery.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't delete that photo.") }
                },
            )
        }
    }

    /**
     * Clears [MushroomLogUiState.saveErrorMessage] once its one-shot Toast has been shown — see
     * that field's own render site (`LogPanel`/`JournalTab`'s `LaunchedEffect`) for the other half
     * of the "cleared on dismiss or next successful save, whichever comes first" rule; the nine
     * write sites above (start/edit/save/cancel/delete/add photo/remove photo/pull photo/delete
     * gallery photo) cover the "next successful save" half themselves.
     */
    fun onSaveErrorDismissed() {
        _uiState.update { it.copy(saveErrorMessage = null) }
    }

    /**
     * Reverses [current]'s photo attachments back to [snapshot]'s set — detaching anything attached
     * during the session, reattaching anything detached — and returns the fully-reverted photo
     * list. Never touches a gallery photo's own row: [RemovePhotoFromLogEntryUseCase]/
     * [PullPhotoIntoEntryUseCase] both only ever attach/detach a reference, matching the "What NOT
     * to do" rule this exists to satisfy (Cancel must never let a photo vanish from the album).
     * A failure on any one photo is logged and otherwise ignored — best-effort revert, since the
     * alternative (leaving the whole cancel unresolved) would strand the user's edit session
     * entirely over one photo's transient failure.
     */
    private suspend fun revertPhotosToSnapshot(current: MushroomLogEntry, snapshot: MushroomLogEntry): List<LogPhoto> {
        var working = current
        val toDetach = current.photos.filterNot { photo -> snapshot.photos.any { it.id == photo.id } }
        val toReattach = snapshot.photos.filterNot { photo -> current.photos.any { it.id == photo.id } }
        toDetach.forEach { photo ->
            removePhoto(working, photo).fold(
                onSuccess = { working = it },
                onFailure = { error -> Log.w(TAG, "Couldn't revert photo '${photo.id}' on entry '${working.id}'.", error) },
            )
        }
        toReattach.forEach { photo ->
            pullPhotoIntoEntry(working, photo).fold(
                onSuccess = { working = it },
                onFailure = { error -> Log.w(TAG, "Couldn't revert photo '${photo.id}' on entry '${working.id}'.", error) },
            )
        }
        return working.photos
    }

    /**
     * [updated] replacing its counterpart in [MushroomLogUiState.editingEntry], and in
     * [MushroomLogUiState.entries] according to [MushroomLogEntry.isDraft]: removed from the list
     * if now a draft, updated in place if already present and now committed, or appended if this is
     * its first-ever commit (a brand-new entry was never added to [entries] at creation — see
     * [onStartNewEntry]'s own doc comment).
     */
    private fun MushroomLogUiState.replacing(updated: MushroomLogEntry): MushroomLogUiState = copy(
        entries = when {
            updated.isDraft -> entries.filterNot { it.id == updated.id }
            entries.any { it.id == updated.id } -> entries.map { if (it.id == updated.id) updated else it }
            else -> entries + updated
        },
        editingEntry = editingEntry?.let { if (it.id == updated.id) updated else it },
    )

    private companion object {
        const val TAG = "MushroomLog"
    }
}
