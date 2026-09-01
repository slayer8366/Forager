package com.forager.app.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.CommitCartographyEntryUseCase
import com.forager.app.domain.ComputeTrackStatisticsUseCase
import com.forager.app.domain.CreateCartographyEntryUseCase
import com.forager.app.domain.DeleteCartographyEntryUseCase
import com.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.forager.app.domain.GetCartographyEntriesUseCase
import com.forager.app.domain.GetDerivedTripUseCase
import com.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.SaveCartographyEntryUseCase
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.KeptFindRef
import com.forager.app.domain.model.KeptOfflineRegionRef
import com.forager.app.domain.model.KeptPhotoRef
import com.forager.app.domain.model.KeptTrackRef
import com.forager.app.domain.model.KeptWaypointRef
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.Waypoint
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns [CartographyEntry]'s list/create/edit state — Journal Stage 2b. See [CartographyUiState]'s
 * own doc comment for the shape this manages; see [CartographyEntry]'s own doc comment for why this
 * is a wholly separate `ViewModel` from [MushroomLogViewModel] rather than folded into it.
 *
 * ## Curation is creation-time only
 *
 * [onStartEntry] persists a brand-new draft with **every** candidate from its day's compiled trip
 * report already kept — "withholding is a first-class operation, not a filter," so the deliberate act
 * is *removing* something, not opting into it. [onToggleKept]'s four call sites
 * ([onToggleKeptFind]/[onToggleKeptTrack]/[onToggleKeptWaypoint]/[onToggleKeptOfflineRegion]) then
 * remove-or-restore against that starting set, autosaving on every toggle the same per-action-write
 * shape [MushroomLogViewModel.onEntryEdited] uses for a find.
 *
 * [onOpenEntry] (reopening an entry later, from the Entries or Drafts list) deliberately does **not**
 * reload its day's trip report — [CartographyUiState.candidatesForEditingEntry] stays `null`, so the
 * edit screen shows only the entry's own already-decided kept items (remove-only, via the same
 * [onToggleKeptFind]-family functions, which fall back to plain removal whenever no candidate data is
 * loaded to re-add from). This is a disclosed scope simplification, not a storage constraint: this
 * schema only persists what is kept, never a separate "explicitly withheld" set, so re-loading a
 * fresh trip report on every reopen would have no way to tell "never decided, default to kept" apart
 * from "already withheld once" — re-defaulting every previously-withheld candidate back to kept on
 * each reopen. Keeping curation to entry creation avoids that regression entirely, at the cost of no
 * "add something new from this day later" flow — see this dispatch's own required disclosure for the
 * full reasoning.
 */
class CartographyViewModel(
    private val getEntries: GetCartographyEntriesUseCase,
    private val getDraftEntries: GetCartographyDraftEntriesUseCase,
    private val createEntry: CreateCartographyEntryUseCase,
    private val saveEntry: SaveCartographyEntryUseCase,
    private val commitEntry: CommitCartographyEntryUseCase,
    private val deleteEntry: DeleteCartographyEntryUseCase,
    private val getDerivedTrip: GetDerivedTripUseCase,
    private val getTripReportOfflineRegions: GetTripReportOfflineRegionsUseCase,
    private val computeTrackStatistics: ComputeTrackStatisticsUseCase,
    /** Injected so a test can fix when a photo attachment is stamped — same reasoning as every other `now`/`currentTime` provider in this codebase. */
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartographyUiState())
    val uiState: StateFlow<CartographyUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEntries = true, loadErrorMessage = null) }
            getEntries().fold(
                onSuccess = { entries ->
                    val drafts = getDraftEntries().getOrElse { error ->
                        Log.w(TAG, "Couldn't load Cartography drafts.", error)
                        _uiState.value.draftEntries
                    }
                    _uiState.update { it.copy(entries = entries, draftEntries = drafts, isLoadingEntries = false) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't load Cartography entries.", error)
                    _uiState.update { it.copy(isLoadingEntries = false, loadErrorMessage = "Entries unavailable.") }
                },
            )
        }
    }

    /**
     * Starts a brand-new entry for [date], persists it immediately as a draft with every one of that
     * day's candidates already kept, and opens it for curation. See this class's own doc comment for
     * why every candidate starts kept.
     */
    fun onStartEntry(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCandidates = true, candidatesErrorMessage = null) }
            val trip = getDerivedTrip(date).getOrElse { error ->
                Log.w(TAG, "Couldn't compile the trip report for $date.", error)
                _uiState.update { it.copy(isLoadingCandidates = false, candidatesErrorMessage = "Couldn't compile that day's report.") }
                return@launch
            }
            val offlineRegions = getTripReportOfflineRegions(trip).getOrElse { error ->
                Log.w(TAG, "Couldn't determine offline-region coverage for $date.", error)
                emptyList()
            }

            createEntry(date).fold(
                onSuccess = { draft ->
                    val kept = draft.copy(
                        keptFinds = trip.finds.map { it.toKeptRef() },
                        keptTracks = trip.tracks.map { it.toKeptRef(computeTrackStatistics) },
                        keptWaypoints = trip.waypoints.map { it.toKeptRef() },
                        keptOfflineRegions = offlineRegions.map { it.toKeptRef() },
                    )
                    saveEntry(kept)
                    _uiState.update {
                        it.copy(
                            editingEntry = kept,
                            candidatesForEditingEntry = trip,
                            candidateOfflineRegionsForEditingEntry = offlineRegions,
                            isLoadingCandidates = false,
                            saveErrorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't start a new Cartography entry.", error)
                    _uiState.update { it.copy(isLoadingCandidates = false, candidatesErrorMessage = "Couldn't start a new entry.") }
                },
            )
        }
    }

    /** Reopens an already-created entry (committed or draft) for viewing/editing — see this class's own doc comment for why no trip report is reloaded here. */
    fun onOpenEntry(id: String) {
        val state = _uiState.value
        val entry = state.entries.firstOrNull { it.id == id } ?: state.draftEntries.firstOrNull { it.id == id }
        _uiState.update {
            it.copy(editingEntry = entry, candidatesForEditingEntry = null, candidateOfflineRegionsForEditingEntry = emptyList())
        }
    }

    fun onCloseEntry() {
        _uiState.update { it.copy(editingEntry = null, candidatesForEditingEntry = null, candidateOfflineRegionsForEditingEntry = emptyList()) }
    }

    fun onTextChanged(text: String) {
        val entry = _uiState.value.editingEntry ?: return
        persist(entry.copy(text = text))
    }

    fun onTagsChanged(tags: List<String>) {
        val entry = _uiState.value.editingEntry ?: return
        persist(entry.copy(tags = tags))
    }

    fun onToggleKeptFind(findId: String) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        if (entry.keptFinds.any { it.findId == findId }) {
            persist(entry.copy(keptFinds = entry.keptFinds.filterNot { it.findId == findId }))
            return
        }
        val candidate = state.candidatesForEditingEntry?.finds?.firstOrNull { it.id == findId } ?: return
        persist(entry.copy(keptFinds = entry.keptFinds + candidate.toKeptRef()))
    }

    fun onToggleKeptTrack(trackId: String) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        if (entry.keptTracks.any { it.trackId == trackId }) {
            persist(entry.copy(keptTracks = entry.keptTracks.filterNot { it.trackId == trackId }))
            return
        }
        val candidate = state.candidatesForEditingEntry?.tracks?.firstOrNull { it.id == trackId } ?: return
        persist(entry.copy(keptTracks = entry.keptTracks + candidate.toKeptRef(computeTrackStatistics)))
    }

    fun onToggleKeptWaypoint(waypointId: String) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        if (entry.keptWaypoints.any { it.waypointId == waypointId }) {
            persist(entry.copy(keptWaypoints = entry.keptWaypoints.filterNot { it.waypointId == waypointId }))
            return
        }
        val candidate = state.candidatesForEditingEntry?.waypoints?.firstOrNull { it.id == waypointId } ?: return
        persist(entry.copy(keptWaypoints = entry.keptWaypoints + candidate.toKeptRef()))
    }

    /** Attaches or detaches [photoId] (from the standalone photo library) — see [CartographyEntry.keptPhotos]'s own doc comment. No candidate lookup needed either direction: unlike the other three toggles, a photo id alone is enough to attach it, stamped with [now]. */
    fun onToggleKeptPhoto(photoId: String) {
        val entry = _uiState.value.editingEntry ?: return
        persist(
            if (entry.keptPhotos.any { it.photoId == photoId }) {
                entry.copy(keptPhotos = entry.keptPhotos.filterNot { it.photoId == photoId })
            } else {
                entry.copy(keptPhotos = entry.keptPhotos + KeptPhotoRef(photoId = photoId, attachedAtEpochMillis = now()))
            },
        )
    }

    fun onToggleKeptOfflineRegion(offlineRegionId: Long) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        if (entry.keptOfflineRegions.any { it.offlineRegionId == offlineRegionId }) {
            persist(entry.copy(keptOfflineRegions = entry.keptOfflineRegions.filterNot { it.offlineRegionId == offlineRegionId }))
            return
        }
        val candidate = state.candidateOfflineRegionsForEditingEntry.firstOrNull { it.id == offlineRegionId } ?: return
        persist(entry.copy(keptOfflineRegions = entry.keptOfflineRegions + candidate.toKeptRef()))
    }

    /** Finishes the currently-open draft — see [CommitCartographyEntryUseCase]'s own doc comment. A no-op if nothing is open or it's already committed. */
    fun onFinishEntry() {
        val entry = _uiState.value.editingEntry?.takeIf { it.isDraft } ?: return
        viewModelScope.launch {
            commitEntry(entry).fold(
                onSuccess = { committed ->
                    _uiState.update { it.copy(editingEntry = committed, saveErrorMessage = null) }
                    loadEntries()
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't finish entry '${entry.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't finish that entry.") }
                },
            )
        }
    }

    fun onDeleteEntry(id: String) {
        viewModelScope.launch {
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

    fun onSaveErrorDismissed() {
        _uiState.update { it.copy(saveErrorMessage = null) }
    }

    /** Reflects [entry] immediately (so typing/toggling never feels laggy) and autosaves it — the same immediate-local-write-then-persist shape [MushroomLogViewModel.onEntryEdited] uses. */
    private fun persist(entry: CartographyEntry) {
        _uiState.update { it.copy(editingEntry = entry) }
        viewModelScope.launch {
            saveEntry(entry).fold(
                onSuccess = { _uiState.update { it.copy(saveErrorMessage = null) } },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save entry '${entry.id}'.", error)
                    _uiState.update { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                },
            )
        }
    }

    private companion object {
        const val TAG = "Cartography"
    }
}

private fun MushroomLogEntry.toKeptRef() = KeptFindRef(
    findId = id,
    foundOn = foundOn,
    ownIdentification = ownIdentification,
    hasPhotos = photos.isNotEmpty(),
)

private fun Track.toKeptRef(computeTrackStatistics: ComputeTrackStatisticsUseCase): KeptTrackRef {
    val stats = computeTrackStatistics(points)
    return KeptTrackRef(
        trackId = id,
        name = name,
        distanceMeters = stats.distanceMeters,
        durationMillis = stats.durationMillis,
        pointCount = stats.totalPoints,
    )
}

private fun Waypoint.toKeptRef() = KeptWaypointRef(waypointId = id, name = name, lat = lat, lng = lng)

private fun OfflineRegionSummary.toKeptRef() = KeptOfflineRegionRef(
    offlineRegionId = id,
    name = name,
    lat = region.lat,
    lng = region.lng,
    radiusKm = region.radiusKm,
)
