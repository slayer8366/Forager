package com.forager.app.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CommitDraftEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteGalleryPhotoUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetDraftEntriesUseCase
import com.forager.app.domain.GetGalleryPhotosUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.PullPhotoIntoEntryUseCase
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
import com.forager.app.domain.StartEditingLogEntryUseCase
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the mushroom log's list/create/edit state. Kept as its own `ViewModel` rather than folded
 * into [com.forager.app.ui.availability.AvailabilityViewModel]: the log is reachable from the
 * drawer as its own destination (see `docs/plans/mushroom-log.md`'s Navigation section) and is
 * functionally independent of the availability search — the two never need to observe each other's
 * state, only [com.forager.app.MainActivity] wires them together (the map's "Log a find" option
 * calls into this ViewModel with a location the availability screen reported).
 *
 * ## Standalone drafts (Workstream L4b, owner decision 2026-08-22; corrected 2026-08-25, L4b-R)
 *
 * A draft is a **separate row**, not the committed entry itself wearing a flag — the first L4b pass
 * shipped the single-row shape and it was rejected on its consequences, not just its merits: it let
 * an interrupted edit overwrite a committed entry in place, and made the committed entry vanish from
 * the log for the entire time it was being edited. See [MushroomLogEntry.draftOfEntryId]'s own doc
 * comment for the storage shape this drives.
 *
 * **The lifecycle of an edit session:**
 * - [onStartNewEntry] creates a brand-new draft immediately (`draftOfEntryId = null`) and opens it
 *   for editing directly — there is no separate "start editing" step for something that doesn't
 *   exist yet.
 * - [onOpenEntry] opens an existing row (committed or already-draft) for *viewing* — a report, not
 *   an edit form. No new row is created here.
 * - [onStartEditingEntry] is the actual "begin editing" action, called when the user taps into the
 *   edit form for a row [onOpenEntry] showed. If that row is already a draft, this is a no-op (it's
 *   already editable). If it's a genuinely committed entry, this creates a **new** draft row seeded
 *   as a copy of it, `draftOfEntryId` pointing back at the committed entry's id, and switches
 *   [MushroomLogUiState.editingEntry] to that new row. From this point on, the committed row is
 *   completely untouched — still visible in [MushroomLogUiState.entries] with its last-saved values
 *   — until Save.
 * - [onEntryEdited] autosaves on every field change, same cadence as before this model existed, but
 *   always onto whatever draft row is currently open — never the committed parent.
 *
 * **The three exits**, once a draft is open:
 * - [onSaveEntry] commits: for a brand-new entry's draft, flips it to committed in place (same id);
 *   for a re-edit's draft, copies its fields onto the parent, repoints its photo references onto the
 *   parent (merging with what the parent already had), and removes the draft row — transactionally
 *   (see [CommitDraftEntryUseCase]/`MushroomLogDao.commitDraft`). The form stays open, now showing
 *   the committed result.
 * - [onCancelEditing] is the only exit that discards anything: it deletes the draft row and its own
 *   photo references outright. For a brand-new entry this deletes the whole entry, leaving nothing
 *   in the log or the Drafts section. For a re-edit, the parent — untouched this whole time — is
 *   completely unaffected.
 * - [onLeaveEditingIncidentally] (tab switch, backgrounding, the back arrow, or any other way of
 *   leaving besides tapping Save or Cancel) does **not** commit and does **not** discard — the draft
 *   is already durably persisted (every [onEntryEdited] call wrote it), so this just closes the form.
 *   The draft surfaces in the Drafts filter ([MushroomLogUiState.draftEntries]) until the user
 *   returns to resolve it. (Correction from the first L4b pass, which auto-committed here — the
 *   owner's 2026-08-25 decision is explicit: an incidental exit must never put an unsaved entry in
 *   the log.)
 *
 * **The known hazard, resolved deliberately:** [loadEntries] used to re-derive [MushroomLogUiState.editingEntry]
 * wholesale from a fresh repository read (G3) — safe only because nothing uncommitted existed to
 * lose at the time. Under this model that would silently discard in-progress typing on every
 * refresh, including [onDeleteGalleryPhoto]'s own success-path refresh. [loadEntries] now merges in
 * only [MushroomLogEntry.photos] — the one field G3's refresh existed to keep current — onto
 * whatever [MushroomLogUiState.editingEntry] already holds, never replacing the rest of it. This
 * still holds under the standalone-draft model: the merge searches both [MushroomLogUiState.entries]
 * and [MushroomLogUiState.draftEntries] for the open row's id, since it may now be either.
 *
 * ## Serialized editing-entry mutations (Workstream L4c, owner decision 2026-08-25)
 *
 * The photo-attach/`loadEntries()` race (L4b-R2's own verification pulse, §1 of this class's own
 * commit history) was one instance of a class: every handler below that reads
 * [MushroomLogUiState.editingEntry], awaits real I/O, then writes it back is racing every other
 * such handler, with no ordering guarantee between them — whichever write lands last wins,
 * regardless of which the user asked for first. [editingEntryMutex] closes the whole class, not
 * just the one pairing that was found: every write to [MushroomLogUiState.editingEntry] (and the
 * [loadEntries] read that feeds its own merge into it) acquires this one `Mutex` around its
 * disk-I/O-and-write critical section, so those critical sections run one at a time, in the order
 * the user's actions arrived (Kotlin's `Mutex` grants a contended lock to waiters in FIFO order).
 *
 * **What's deliberately outside the lock:** [onEntryEdited]'s own *immediate* local reflection of a
 * keystroke (`_uiState.update` before its `launch`) — locking that too would add input lag to the
 * hottest path in this class for no correctness gain, since nothing else ever re-touches
 * [MushroomLogUiState.editingEntry] from that same write's own success callback (only
 * `saveErrorMessage`). [loadGalleryPhotos]/[onSaveErrorDismissed] never touch
 * [MushroomLogUiState.editingEntry] at all and stay outside for the same "don't lock unrelated
 * work" reason. [onDeleteGalleryPhoto] itself never acquires the lock either — it calls
 * [loadEntries] afterward, which acquires and releases its own lock independently; that is a
 * function call, not a nested acquisition, so it cannot deadlock.
 *
 * **No handler acquires [editingEntryMutex] while already holding it.** Checked directly, not
 * assumed: the one place a handler calls another editing-entry mutator
 * ([onDeleteGalleryPhoto] → [loadEntries]) is exactly the case above, where the caller never holds
 * the lock itself first.
 */
class MushroomLogViewModel(
    private val getEntries: GetMushroomLogEntriesUseCase,
    private val getDraftEntries: GetDraftEntriesUseCase,
    private val createEntry: CreateMushroomLogEntryUseCase,
    private val startEditingEntry: StartEditingLogEntryUseCase,
    private val saveEntry: SaveMushroomLogEntryUseCase,
    private val commitDraftEntry: CommitDraftEntryUseCase,
    private val deleteEntry: DeleteMushroomLogEntryUseCase,
    private val addPhoto: AddPhotoToLogEntryUseCase,
    private val removePhoto: RemovePhotoFromLogEntryUseCase,
    private val getGalleryPhotos: GetGalleryPhotosUseCase,
    private val pullPhotoIntoEntry: PullPhotoIntoEntryUseCase,
    private val deleteGalleryPhoto: DeleteGalleryPhotoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MushroomLogUiState())
    val uiState: StateFlow<MushroomLogUiState> = _uiState.asStateFlow()

    /** See this class's own doc comment, "Serialized editing-entry mutations," for what this guards and why. */
    private val editingEntryMutex = Mutex()

    init {
        loadEntries()
        loadGalleryPhotos()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEntries = true, loadErrorMessage = null) }
            // Workstream L4c: the read and the editingEntry merge it feeds are one critical section
            // — see this class's own "Serialized editing-entry mutations" doc comment. Acquired here
            // (not just around the final _uiState.update) so a write that's already mid-flight when
            // this refresh starts is guaranteed to have landed on disk before this read runs, not
            // just before the merge applies.
            editingEntryMutex.withLock {
                getEntries().fold(
                    onSuccess = { entries ->
                        val drafts = getDraftEntries().getOrElse { error ->
                            Log.w(TAG, "Couldn't load drafts.", error)
                            _uiState.value.draftEntries
                        }
                        _uiState.update { state ->
                            // Merges in only the freshest `photos` for the open entry (found in either
                            // list — a report view lives in entries, an open draft in draftEntries, and
                            // which one it is can change mid-session) — never replaces the rest of
                            // editingEntry, so in-progress typing survives a refresh. See this class's
                            // own doc comment on the loadEntries hazard for the full reasoning.
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
     * already known, or with none at all — as its own standalone draft, then opens it for editing
     * directly (no separate [onStartEditingEntry] step; nothing committed exists yet to view a
     * report of). Deliberately never added to [MushroomLogUiState.entries]: that list is
     * committed-only (owner decision #6), so a brand-new entry stays invisible there until Save.
     */
    fun onStartNewEntry(location: LatLng?, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            editingEntryMutex.withLock {
                createEntry(location, date).fold(
                    onSuccess = { entry -> _uiState.update { it.copy(editingEntry = entry, saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't start a new log entry.", error)
                        _uiState.update { it.copy(saveErrorMessage = "Couldn't start a new entry.") }
                    },
                )
            }
        }
    }

    /**
     * Opens an already-loaded row for *viewing* — found in either [MushroomLogUiState.entries] (a
     * committed entry, shown as a report) or [MushroomLogUiState.draftEntries] (a draft reopened
     * from the Drafts filter, resumed directly into editing — see [JournalTab]'s own mode logic).
     * Creates nothing; see [onStartEditingEntry] for the action that actually begins an edit session
     * on a committed entry.
     */
    fun onOpenEntry(id: String) {
        viewModelScope.launch {
            editingEntryMutex.withLock {
                val state = _uiState.value
                val entry = state.entries.firstOrNull { it.id == id } ?: state.draftEntries.firstOrNull { it.id == id }
                _uiState.update { it.copy(editingEntry = entry) }
            }
        }
    }

    /** Returns to the list without resolving anything — correct only for closing a *report* (nothing editable was ever touched). An open edit session resolves via [onSaveEntry]/[onCancelEditing]/[onLeaveEditingIncidentally] instead. */
    fun onCloseEntry() {
        viewModelScope.launch {
            editingEntryMutex.withLock {
                _uiState.update { it.copy(editingEntry = null) }
            }
        }
    }

    /**
     * Begins editing [MushroomLogUiState.editingEntry] — see this class's own doc comment for why
     * this is a distinct step from [onOpenEntry]. A no-op if it's already a draft (a brand-new entry,
     * or one reopened from the Drafts filter); otherwise creates a new draft row pointing at it and
     * switches [MushroomLogUiState.editingEntry] to that row. A no-op if nothing is open.
     */
    fun onStartEditingEntry() {
        val current = _uiState.value.editingEntry ?: return
        if (current.isDraft) return
        viewModelScope.launch {
            editingEntryMutex.withLock {
                startEditingEntry(current).fold(
                    onSuccess = { draft -> _uiState.update { it.copy(editingEntry = draft, saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't start editing entry '${current.id}'.", error)
                        _uiState.update { it.copy(saveErrorMessage = "Couldn't start editing that entry.") }
                    },
                )
            }
        }
    }

    /** Autosaves [updated] — always onto whatever draft row is currently open (see this class's own doc comment); never touches [MushroomLogUiState.entries] or [MushroomLogUiState.draftEntries], since a committed parent (if any) stays untouched until Save. */
    fun onEntryEdited(updated: MushroomLogEntry) {
        // Deliberately outside editingEntryMutex — see this class's own "Serialized editing-entry
        // mutations" doc comment for why: this is the hottest path in this class, nothing else ever
        // re-touches editingEntry from this same write's own success callback, and locking an
        // instant local reflection would add input lag for no correctness gain.
        _uiState.update { it.copy(editingEntry = updated) }
        viewModelScope.launch {
            editingEntryMutex.withLock {
                saveEntry(updated).fold(
                    onSuccess = { _uiState.update { it.copy(saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't save entry '${updated.id}'.", error)
                        _uiState.update { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                    },
                )
            }
        }
    }

    /**
     * Save — commits the currently-open draft (see [CommitDraftEntryUseCase]) and keeps the form
     * open, now showing the committed result under its committed id. A no-op if nothing is open.
     */
    fun onSaveEntry() {
        val draft = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            editingEntryMutex.withLock {
                commitDraftEntry(draft).fold(
                    onSuccess = { committed ->
                        _uiState.update { state ->
                            state.copy(
                                entries = if (state.entries.any { it.id == committed.id }) {
                                    state.entries.map { if (it.id == committed.id) committed else it }
                                } else {
                                    state.entries + committed
                                },
                                draftEntries = state.draftEntries.filterNot { it.id == draft.id },
                                editingEntry = committed,
                                saveErrorMessage = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't save entry '${draft.id}'.", error)
                        _uiState.update { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                    },
                )
            }
        }
    }

    /**
     * Cancel — the only exit that discards anything. Deletes the currently-open draft's own row and
     * its own photo references (see [DeleteMushroomLogEntryUseCase]) — for a brand-new entry this is
     * the whole entry, gone from the log and the Drafts filter both; for a re-edit, the committed
     * parent was never touched and is completely unaffected. A no-op if nothing is open.
     */
    fun onCancelEditing() {
        val draft = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            editingEntryMutex.withLock {
                deleteEntry(draft.id).fold(
                    onSuccess = {
                        _uiState.update { state ->
                            state.copy(
                                draftEntries = state.draftEntries.filterNot { it.id == draft.id },
                                editingEntry = null,
                                saveErrorMessage = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't discard draft '${draft.id}'.", error)
                        _uiState.update { it.copy(saveErrorMessage = "Couldn't discard that draft.") }
                    },
                )
            }
        }
    }

    /**
     * Leaving without answering — tab switch, backgrounding, the back arrow, or any other exit
     * besides tapping Save or Cancel. Never commits, never discards (owner decision, 2026-08-25,
     * correcting the first L4b pass's auto-save-on-exit behavior): the draft is already durably
     * persisted by [onEntryEdited]'s own per-keystroke writes, so this only closes the form and — if
     * the open row is a draft — makes sure it's reflected in [MushroomLogUiState.draftEntries],
     * without waiting for a fresh [loadEntries]. Never inspects whether the draft was actually
     * touched (that auto-delete existed in the first pass, was never authorized, and is gone — see
     * the L4b-R dispatch). A no-op if nothing is open. The *call* never blocks — [editingEntryMutex]
     * (Workstream L4c) is uncontended the overwhelming majority of the time, in which case the
     * state update inside still applies before this function returns to its caller; only while some
     * other editing-entry mutation is genuinely in flight does this queue behind it, by design (see
     * this class's own "Serialized editing-entry mutations" doc comment).
     */
    fun onLeaveEditingIncidentally() {
        val current = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            editingEntryMutex.withLock {
                _uiState.update { state ->
                    state.copy(
                        draftEntries = when {
                            !current.isDraft -> state.draftEntries
                            state.draftEntries.any { it.id == current.id } -> state.draftEntries.map { if (it.id == current.id) current else it }
                            else -> state.draftEntries + current
                        },
                        editingEntry = null,
                    )
                }
            }
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
            editingEntryMutex.withLock {
                deleteEntry(id).fold(
                    onSuccess = {
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
    }

    fun onAddPhoto(source: PhotoSource) {
        val entry = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPhoto = true) }
            editingEntryMutex.withLock {
                addPhoto(entry, source).fold(
                    onSuccess = { updated ->
                        _uiState.update { it.copy(editingEntry = updated, isSavingPhoto = false, saveErrorMessage = null) }
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
    }

    fun onRemovePhoto(photo: LogPhoto) {
        val entry = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            editingEntryMutex.withLock {
                removePhoto(entry, photo).fold(
                    onSuccess = { updated -> _uiState.update { it.copy(editingEntry = updated, saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't remove a photo from entry '${entry.id}'.", error)
                        _uiState.update { it.copy(saveErrorMessage = "Couldn't remove that photo.") }
                    },
                )
            }
        }
    }

    /** Workstream G3: references an existing gallery [photo] from the currently-editing entry — a reference only, never a new file (see [PullPhotoIntoEntryUseCase]'s own doc comment). */
    fun onPullPhoto(photo: LogPhoto) {
        val entry = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            editingEntryMutex.withLock {
                pullPhotoIntoEntry(entry, photo).fold(
                    onSuccess = { updated -> _uiState.update { it.copy(editingEntry = updated, saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't pull photo '${photo.id}' into entry '${entry.id}'.", error)
                        _uiState.update { it.copy(saveErrorMessage = "Couldn't add that photo.") }
                    },
                )
            }
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
                    // Deliberately not acquiring editingEntryMutex here first — loadEntries()
                    // acquires and releases its own below. Calling into it is not a nested
                    // acquisition, since this function never holds the lock itself.
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
     * of the "cleared on dismiss or next successful save, whichever comes first" rule; the ten write
     * sites above (start/start-editing/edit/save/cancel/delete/add photo/remove photo/pull photo/
     * delete gallery photo) cover the "next successful save" half themselves.
     */
    fun onSaveErrorDismissed() {
        _uiState.update { it.copy(saveErrorMessage = null) }
    }

    private companion object {
        const val TAG = "MushroomLog"
    }
}
