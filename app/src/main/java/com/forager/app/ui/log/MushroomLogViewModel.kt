package com.forager.app.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
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
 * state, only [com.forager.app.MainActivity] wires them together (the map's long-press "log a find"
 * option calls into this ViewModel with a location the availability screen reported).
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(MushroomLogUiState())
    val uiState: StateFlow<MushroomLogUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEntries = true, loadErrorMessage = null) }
            getEntries().fold(
                onSuccess = { entries ->
                    _uiState.update { it.copy(entries = entries, isLoadingEntries = false) }
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

    /** Starts and immediately persists a new, entirely-unrecorded entry at [location], then opens it for editing. */
    fun onStartNewEntry(location: LatLng, date: LocalDate = LocalDate.now()) {
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
     * The photo list comes from [MushroomLogUiState.entries], already loaded — no new query, per
     * [DeleteMushroomLogEntryUseCase]'s own doc comment on why cleanup doesn't need one. If [id]
     * isn't found there (shouldn't happen: every caller sources it from this same state), the
     * entry still gets deleted by id — the row deletion the user asked for never depends on photo
     * lookup succeeding — just with nothing to clean up, since there's nothing here to look up.
     */
    fun onDeleteEntry(id: String) {
        val photos = _uiState.value.entries.firstOrNull { it.id == id }?.photos.orEmpty()
        viewModelScope.launch {
            deleteEntry(
                id,
                photos,
                onPhotoDeleteFailed = { photo, error ->
                    Log.w(TAG, "Couldn't delete photo file '${photo.relativePath}' for entry '$id'.", error)
                },
            ).fold(
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
