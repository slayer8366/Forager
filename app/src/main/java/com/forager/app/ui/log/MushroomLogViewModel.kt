package com.forager.app.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.AddPhotoToGalleryUseCase
import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CommitDraftEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteGalleryPhotoUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetDraftEntriesUseCase
import com.forager.app.domain.GetGalleryPhotosUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.PullPhotoIntoEntryUseCase
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
import com.forager.app.domain.StartEditingLogEntryUseCase
import com.forager.app.domain.UpdatePhotoLocationUseCase
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCapturePhotoSource
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
 * the lock itself first. [onOpenEntryForEditing] below is deliberately its own critical section
 * with its own inline logic, not `withLock { onOpenEntry(id); onStartEditingEntry() }` calling the
 * two existing public methods — that shape would have this function's own lock acquisition wait on
 * itself, a guaranteed deadlock on first use, since Kotlin's `Mutex` is not reentrant.
 *
 * **One caller composition this design does not trust, and why:** [LogPanel]'s own entry list used
 * to call [onOpenEntry] immediately followed by [onStartEditingEntry] — the same shape this
 * paragraph's own opening race was found in. That composition's correctness rests on
 * [onOpenEntry]'s write having already landed by the time [onStartEditingEntry]'s guard reads
 * state — true today only because both enqueue via `Dispatchers.Main.immediate`, which runs an
 * *uncontended* `launch` synchronously. Under contention (another editing-entry operation genuinely
 * in flight) that guarantee doesn't hold, and [onStartEditingEntry] would silently no-op against
 * stale state — restoring correctness through the exact implicit-ordering assumption that hid the
 * original race, not by closing it. [onOpenEntryForEditing] replaces that composition for
 * [LogPanel]'s one call site with a single atomic operation; [onStartEditingEntry]'s own guard was
 * *also* moved inside its lock (rather than relying on [onOpenEntryForEditing] alone) so a future
 * caller that chains the two old methods independently gets a guard that is correct on its own
 * terms, not one that merely happens to work for today's callers.
 *
 * **Rejected: exempting [onOpenEntry]/[onCloseEntry] from [editingEntryMutex] entirely** (owner
 * decision, 2026-08-25) — the alternative fix for the composition problem above, since neither
 * function has any suspension of its own to race internally. Rejected because it narrows the
 * guarantee this workstream exists to establish rather than closing it: an un-serialized
 * [onCloseEntry] could still be resurrected by a slower, already-in-flight [onSaveEntry]'s late
 * write landing afterward — precisely the "nobody notices when it's applied properly; when it
 * isn't, confidence is lost" class of bug this workstream was scoped to close everywhere, not just
 * at the one pairing a pulse happened to find. Do not restore this exemption as a simplification.
 *
 * ## Camera-capture location: a fire-and-forget follow-up write (photo-geodata dispatch)
 *
 * [locationProvider] is only ever read from [patchCameraCaptureLocation], never awaited by
 * [onAddPhoto]/[onAddGalleryPhoto] themselves. This was a genuine, reported tension in the
 * dispatch's own two instructions — "read GPS at capture" and "never block/delay capture" — that
 * directly reusing [LocationProvider.getCurrentLocation] cannot satisfy simultaneously, since that
 * call already blocks every one of its existing callers up to its own 20-second internal timeout.
 * The owner's decision (the photo-geodata amendment): persist the photo immediately, exactly as
 * before this dispatch, then fire a *separate*, unawaited `viewModelScope.launch` that requests a
 * fix and — only if one resolves — patches it onto the already-persisted row via
 * [updatePhotoLocation]. The photo appears in the gallery/entry instantly either way; a coordinate,
 * if one comes back, arrives a moment later as a quiet update, never as something the user waits on
 * or that blocks the capture UI. Only a [com.forager.app.photo.CameraCapturePhotoSource]-originated
 * photo ever triggers this — see [LogPhoto]'s own doc comment for why a gallery import's location
 * comes from EXIF instead, read synchronously as part of [com.forager.app.photo.FilePhotoStore.persist]
 * and never through this path.
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
    /** Standalone-photos dispatch: acquisition with no owning find — [PhotoGalleryScreen]'s own Camera/Gallery buttons. See [onAddGalleryPhoto]. */
    private val addPhotoToGallery: AddPhotoToGalleryUseCase,
    private val removePhoto: RemovePhotoFromLogEntryUseCase,
    private val getGalleryPhotos: GetGalleryPhotosUseCase,
    private val pullPhotoIntoEntry: PullPhotoIntoEntryUseCase,
    private val deleteGalleryPhoto: DeleteGalleryPhotoUseCase,
    /** Photo-geodata dispatch: the fire-and-forget GPS fix for a freshly captured camera photo — see this class's own doc comment, "Camera-capture location." */
    private val locationProvider: LocationProvider,
    private val updatePhotoLocation: UpdatePhotoLocationUseCase,
    /** How many Cartography entries currently keep a photo attached — Journal Stage 2b's 4b deletion warning, extended to photos. Plain suspend function rather than the whole Cartography repository — see `TrackRecordingViewModel.getWaypointReferenceCount`'s own doc comment for why. */
    private val getPhotoEntryReferenceCount: suspend (String) -> Int = { 0 },
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
                    // One query per photo — see TrackRecordingViewModel.loadWaypoints' identical
                    // choice for why this scale doesn't need a batched read.
                    val counts = photos.associate { it.photo.id to getPhotoEntryReferenceCount(it.photo.id) }
                    _uiState.update { it.copy(cartographyEntryPhotoReferenceCounts = counts) }
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
        viewModelScope.launch {
            editingEntryMutex.withLock {
                // Workstream L4c, refinement to the serialization design: this guard used to read
                // _uiState.value.editingEntry *before* acquiring the lock. That reads correctly only
                // because every caller today happens to enqueue its own launch in an order that
                // guarantees any preceding write already landed — an implicit ordering guarantee
                // resting on Dispatchers.Main.immediate semantics and Mutex fairness, the same
                // species of assumption that let the photo/loadEntries race (this workstream's own
                // finding) go unnoticed. Reading inside the lock instead means this guard is correct
                // on its own terms, independent of what any caller does before calling it — including
                // a caller nobody has written yet. [onOpenEntryForEditing] below is the *other* half
                // of this fix: the one production call site that used to chain onOpenEntry() then
                // this function now does both atomically in one critical section instead of relying
                // on this guard alone.
                val current = _uiState.value.editingEntry ?: return@withLock
                if (current.isDraft) return@withLock
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

    /**
     * Opens [id] for viewing and, if it turns out to be a genuinely committed entry, immediately
     * begins editing it — atomically, in one [editingEntryMutex] critical section, not
     * [onOpenEntry] and [onStartEditingEntry] chained by the caller (see this class's own doc
     * comment, "Serialized editing-entry mutations," for why that chain is not an equivalent, safe
     * substitute: its correctness would depend on the first call's write already having landed by
     * the time the second's guard reads state, which is exactly the implicit-ordering hazard this
     * workstream exists to close, not something to keep relying on for a new caller). [LogPanel]'s
     * own entry list is the one caller: it has no separate report step, so opening a row always
     * means "edit it." A no-op-shaped success on an already-draft row (no [startEditingEntry] call
     * needed, matching [onStartEditingEntry]'s own no-op case) and a failed [startEditingEntry]
     * still leave the found entry showing as a report (matching what the old two-call chain left
     * behind on the same failure), with [MushroomLogUiState.saveErrorMessage] set.
     */
    fun onOpenEntryForEditing(id: String) {
        viewModelScope.launch {
            editingEntryMutex.withLock {
                val state = _uiState.value
                val entry = state.entries.firstOrNull { it.id == id } ?: state.draftEntries.firstOrNull { it.id == id }
                if (entry == null || entry.isDraft) {
                    _uiState.update { it.copy(editingEntry = entry) }
                    return@withLock
                }
                startEditingEntry(entry).fold(
                    onSuccess = { draft -> _uiState.update { it.copy(editingEntry = draft, saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't start editing entry '${entry.id}'.", error)
                        _uiState.update { it.copy(editingEntry = entry, saveErrorMessage = "Couldn't start editing that entry.") }
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
     * without waiting for a fresh [loadEntries]. A no-op if nothing is open. The *call* never blocks
     * — [editingEntryMutex] (Workstream L4c) is uncontended the overwhelming majority of the time, in
     * which case the state update inside still applies before this function returns to its caller;
     * only while some other editing-entry mutation is genuinely in flight does this queue behind it,
     * by design (see this class's own "Serialized editing-entry mutations" doc comment).
     *
     * **A re-edit's draft that was never actually touched is deleted outright, not surfaced
     * (pending-edit-and-fixes dispatch, Item 2).** [StartEditingLogEntryUseCase] copies [current]'s
     * own committed parent field-for-field before this function ever runs — `id`/`isDraft`/
     * `draftOfEntryId` are the only fields that differ by construction, so comparing the draft against
     * its still-untouched parent (found via [MushroomLogUiState.entries], per [MushroomLogViewModel]'s
     * own doc comment on why the parent stays untouched until Save) after normalizing exactly those
     * three fields is an exact, not approximate, "nothing changed" check — [MushroomLogEntry] carries
     * no timestamp or other field that could legitimately drift on its own. Scoped to a **re-edit**
     * only ([draftOfEntryId] non-null): a brand-new entry's own draft (nothing to compare against) is
     * a different question this dispatch does not ask, so it keeps today's behavior — surfaced in
     * [MushroomLogUiState.draftEntries] unconditionally, same as before this fix. That auto-delete
     * existed once in the first L4b pass, was never authorized there, and was removed by the L4b-R
     * dispatch — this is not a return to it: that one deleted *any* draft, unconditionally, on every
     * incidental exit; this only deletes a re-edit's draft when it is provably identical to what's
     * already stored.
     */
    fun onLeaveEditingIncidentally() {
        val current = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            editingEntryMutex.withLock {
                val state = _uiState.value
                val parent = current.draftOfEntryId?.let { parentId -> state.entries.firstOrNull { it.id == parentId } }
                val isUnchangedReEdit = parent != null &&
                    current.copy(id = parent.id, isDraft = false, draftOfEntryId = null) == parent
                if (isUnchangedReEdit) {
                    deleteEntry(current.id).fold(
                        onSuccess = {
                            _uiState.update { s ->
                                s.copy(draftEntries = s.draftEntries.filterNot { it.id == current.id }, editingEntry = null)
                            }
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Couldn't discard the unchanged draft '${current.id}'.", error)
                            _uiState.update { s ->
                                s.copy(
                                    draftEntries = if (s.draftEntries.any { it.id == current.id }) {
                                        s.draftEntries.map { if (it.id == current.id) current else it }
                                    } else {
                                        s.draftEntries + current
                                    },
                                    editingEntry = null,
                                )
                            }
                        },
                    )
                } else {
                    _uiState.update { s ->
                        s.copy(
                            draftEntries = when {
                                !current.isDraft -> s.draftEntries
                                s.draftEntries.any { it.id == current.id } -> s.draftEntries.map { if (it.id == current.id) current else it }
                                else -> s.draftEntries + current
                            },
                            editingEntry = null,
                        )
                    }
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
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPhoto = true) }
            editingEntryMutex.withLock {
                // Read fresh, inside the critical section — not before acquiring it. Two photo
                // operations issued back to back must apply in the order issued: if this captured
                // editingEntry at call time instead, a second operation queued behind this one would
                // compute its own result from the *same* pre-this-operation snapshot, and its later
                // apply would silently discard this one's photo when it lands. See this class's own
                // "Serialized editing-entry mutations" doc comment — the same principle
                // onStartEditingEntry's guard applies, extended to every photo mutator.
                val entry = _uiState.value.editingEntry ?: return@withLock
                addPhoto(entry, source).fold(
                    onSuccess = { updated ->
                        _uiState.update { it.copy(editingEntry = updated, isSavingPhoto = false, saveErrorMessage = null) }
                        // A freshly added photo is a new gallery row PhotoGalleryScreen's already-loaded
                        // state doesn't know about yet — without this, it wouldn't appear there until
                        // the ViewModel is recreated. Detach has no equivalent need: it never removes a
                        // gallery row, only a reference nothing in this screen currently displays.
                        loadGalleryPhotos()
                        // Photo-geodata dispatch: see this class's own "Camera-capture location" doc
                        // comment. The newly persisted photo is always the last element of updated's
                        // photos list — AddPhotoToLogEntryUseCase's own entry.copy(photos = entry.photos + photo).
                        if (source is CameraCapturePhotoSource) {
                            patchCameraCaptureLocation(updated.photos.last().id)
                        }
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

    /**
     * Standalone-photos dispatch: acquires [source] via [PhotoGalleryScreen]'s own Camera/Gallery
     * buttons — persisted and added to the gallery only, never attached to anything (see
     * [AddPhotoToGalleryUseCase]'s own doc comment for why this stops one step short of
     * [onAddPhoto]). No [editingEntryMutex] needed: unlike [onAddPhoto], this never reads or writes
     * [MushroomLogUiState.editingEntry] at all.
     *
     * [onPersisted] — entry-photo-acquisition dispatch, Item 2: fired with the new photo's id on
     * success, before this function's own `loadGalleryPhotos()` refresh but after the write has
     * landed. Defaults to a no-op, so every call site before this dispatch is unaffected. Exists so
     * `MainActivity` can compose "persist to the standalone gallery" (owned here) with "attach to
     * the Cartography entry the user is standing in" (`CartographyViewModel.onToggleKeptPhoto`,
     * owned there) at the one place both ViewModels are already visible, without
     * `CartographyViewModel` growing its own copy of [AddPhotoToGalleryUseCase] — which would not
     * only duplicate this function's own persist logic, but leave [MushroomLogUiState.galleryPhotos]
     * stale: [GetGalleryPhotosUseCase] is a one-shot suspend call, not a reactive `Flow`, so a write
     * from a use case this ViewModel never called would never be reflected here on its own. Reusing
     * this function whole — persist, refresh, and (for a camera capture) the GPS patch below — keeps
     * a photo acquired from inside an entry indistinguishable, everywhere else in the app, from one
     * acquired from the Album or a find.
     */
    fun onAddGalleryPhoto(source: PhotoSource, onPersisted: (String) -> Unit = {}) {
        viewModelScope.launch {
            addPhotoToGallery(source).fold(
                onSuccess = { photo ->
                    _uiState.update { it.copy(saveErrorMessage = null) }
                    onPersisted(photo.id)
                    loadGalleryPhotos()
                    // Photo-geodata dispatch: see this class's own "Camera-capture location" doc comment.
                    if (source is CameraCapturePhotoSource) {
                        patchCameraCaptureLocation(photo.id)
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't add a photo to the gallery.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't add that photo.") }
                },
            )
        }
    }

    /**
     * Fire-and-forget: requests a live GPS fix and, only if one resolves, patches it onto the
     * already-persisted gallery photo [photoId] — see this class's own "Camera-capture location"
     * doc comment for the full reasoning. Launched from [onAddPhoto]/[onAddGalleryPhoto] without
     * being awaited, so this never delays the capture flow those functions already completed by the
     * time this runs. [LocationResult.PermissionDenied]/[LocationResult.LocationUnavailable] are
     * both silently accepted, same as [LogPhoto.latitude]/[LogPhoto.longitude] being `null` is the
     * ordinary case, not an error — nothing here surfaces a user-facing failure for "no fix came
     * back." Refreshes both galleries/entries on a successful patch, mirroring
     * [onDeleteGalleryPhoto]'s own dual-refresh, so a photo already visible on screen picks up its
     * new coordinate without the user needing to leave and return.
     */
    private fun patchCameraCaptureLocation(photoId: String) {
        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation() as? LocationResult.Success ?: return@launch
            updatePhotoLocation(photoId, location.lat, location.lng).fold(
                onSuccess = {
                    loadGalleryPhotos()
                    loadEntries()
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't patch a location fix onto photo '$photoId'.", error)
                },
            )
        }
    }

    fun onRemovePhoto(photo: LogPhoto) {
        viewModelScope.launch {
            editingEntryMutex.withLock {
                // See onAddPhoto's own comment on reading fresh inside the lock — same reasoning.
                val entry = _uiState.value.editingEntry ?: return@withLock
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
        viewModelScope.launch {
            editingEntryMutex.withLock {
                // See onAddPhoto's own comment on reading fresh inside the lock — same reasoning.
                val entry = _uiState.value.editingEntry ?: return@withLock
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
