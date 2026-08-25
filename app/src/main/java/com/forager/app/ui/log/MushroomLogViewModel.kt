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
 * ## Autosave, not an explicit save button
 *
 * [onEntryEdited] persists on every call rather than waiting for a "Save" action. A field
 * app has no guarantee of a graceful exit — low battery, no signal, the OS reclaiming the process —
 * and the deferred-observation workflow this entry supports (see [MushroomLogEntry]'s doc comment)
 * already means the forager leaves and comes back; losing whatever was filled in between visits
 * because a save button wasn't tapped would defeat the point of a *field* record. Each edit is a
 * small, cheap local SQLite write, so there's no real cost to writing on every change.
 */
class MushroomLogViewModel(
    private val getEntries: GetMushroomLogEntriesUseCase,
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

    init {
        loadEntries()
        loadGalleryPhotos()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEntries = true, loadErrorMessage = null) }
            getEntries().fold(
                onSuccess = { entries ->
                    _uiState.update { state ->
                        // Also re-derives editingEntry from the fresh read, not just entries — a
                        // no-op at init (editingEntry is always null there), but load-bearing for
                        // onDeleteGalleryPhoto below: an entry left open in the background across a
                        // tab switch must not keep showing a photo the gallery just deleted.
                        state.copy(
                            entries = entries,
                            editingEntry = state.editingEntry?.let { editing -> entries.firstOrNull { it.id == editing.id } },
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

    /** Starts and immediately persists a new, entirely-unrecorded entry — at [location] if one is already known, or with none at all — then opens it for editing. */
    fun onStartNewEntry(location: LatLng?, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            createEntry(location, date).fold(
                onSuccess = { entry ->
                    _uiState.update { it.copy(entries = it.entries + entry, editingEntry = entry, saveErrorMessage = null) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't start a new log entry.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't start a new entry.") }
                },
            )
        }
    }

    /** Opens an already-loaded entry for viewing/editing — see [MushroomLogUiState.editingEntry]. */
    fun onOpenEntry(id: String) {
        _uiState.update { state -> state.copy(editingEntry = state.entries.firstOrNull { it.id == id }) }
    }

    /** Returns to the list. */
    fun onCloseEntry() {
        _uiState.update { it.copy(editingEntry = null) }
    }

    /** Persists [updated] and reflects it in both [MushroomLogUiState.editingEntry] and the list — see this class's doc comment on autosave. */
    fun onEntryEdited(updated: MushroomLogEntry) {
        _uiState.update { it.replacing(updated) }
        viewModelScope.launch {
            saveEntry(updated).fold(
                onSuccess = { _uiState.update { it.copy(saveErrorMessage = null) } },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save entry '${updated.id}'.", error)
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
                    _uiState.update { state ->
                        state.copy(
                            entries = state.entries.filterNot { it.id == id },
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
     * on its own) must not keep showing a reference to a photo that no longer exists.
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
     * of the "cleared on dismiss or next successful save, whichever comes first" rule; the five
     * write sites above cover the "next successful save" half themselves.
     */
    fun onSaveErrorDismissed() {
        _uiState.update { it.copy(saveErrorMessage = null) }
    }

    /** [updated] replacing its counterpart in both [MushroomLogUiState.entries] and, if it's the open one, [MushroomLogUiState.editingEntry]. */
    private fun MushroomLogUiState.replacing(updated: MushroomLogEntry): MushroomLogUiState = copy(
        entries = entries.map { if (it.id == updated.id) updated else it },
        editingEntry = editingEntry?.let { if (it.id == updated.id) updated else it },
    )

    private companion object {
        const val TAG = "MushroomLog"
    }
}
