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
import com.forager.app.domain.model.DerivedTrip
import com.forager.app.domain.model.FindDecision
import com.forager.app.domain.model.OfflineRegionDecision
import com.forager.app.domain.model.PhotoAttachment
import com.forager.app.domain.model.TrackDecision
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.WaypointDecision
import com.forager.app.domain.model.Waypoint
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns [CartographyEntry]'s list/create/edit state — Journal Stage 2b, extended by its own
 * follow-up dispatch (point 2). See [CartographyUiState]'s own doc comment for the shape this
 * manages; see [CartographyEntry]'s own doc comment for why this is a wholly separate `ViewModel`
 * from [MushroomLogViewModel] rather than folded into it.
 *
 * ## Three states, on every open — not creation-time only
 *
 * [onStartEntry] and [onOpenEntry] both load a fresh trip report for the entry's day, published as
 * [CartographyUiState.candidatesForEditingEntry]/[CartographyUiState.candidateOfflineRegionsForEditingEntry].
 * [CartographyEntryEditScreen] is what merges those live candidates against the entry's own persisted
 * decisions: a candidate already decided (kept or withheld) renders with that decision, unaffected by
 * whatever the live trip report currently says; a candidate with no decision on record renders as
 * **not yet decided** — "available to add,"
 * per the follow-up dispatch, never as included. [onStartEntry] alone auto-decides every one of its
 * day's *initial* candidates as kept (unchanged from the original 2b behavior — "withholding is a
 * first-class operation," so a brand-new entry starts full and the deliberate act is removing
 * something); nothing discovered on a *later* open is ever auto-decided either way.
 *
 * [onSetFindDecision]/[onSetTrackDecision]/[onSetWaypointDecision]/[onSetOfflineRegionDecision] take
 * an explicit target `kept` rather than toggling — an undecided candidate needs two independent
 * actions available (Keep, Withhold), not one that only knows how to flip an existing decision.
 * Setting either way persists a decision row from that point on; an entry never reverts a decision to
 * "undecided" on its own.
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
     * day's *initial* candidates already kept, and opens it for curation. See this class's own doc
     * comment for why only this one moment auto-decides candidates.
     */
    fun onStartEntry(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val trip = loadTripReport(date) ?: return@launch

            createEntry(date).fold(
                onSuccess = { draft ->
                    val decided = draft.copy(
                        findDecisions = trip.derivedTrip.finds.map { it.toDecision(kept = true) },
                        trackDecisions = trip.derivedTrip.tracks.map { it.toDecision(kept = true, computeTrackStatistics) },
                        waypointDecisions = trip.derivedTrip.waypoints.map { it.toDecision(kept = true) },
                        offlineRegionDecisions = trip.offlineRegions.map { it.toDecision(kept = true) },
                    )
                    saveEntry(decided)
                    _uiState.update {
                        it.copy(
                            editingEntry = decided,
                            candidatesForEditingEntry = trip.derivedTrip,
                            candidateOfflineRegionsForEditingEntry = trip.offlineRegions,
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

    /**
     * Reopens an already-created entry (committed or draft) and reloads a fresh trip report for its
     * day — see this class's own doc comment. The entry's own persisted decisions are untouched by
     * this reload; only [CartographyUiState.candidatesForEditingEntry]/[CartographyUiState.candidateOfflineRegionsForEditingEntry]
     * (what the edit screen offers as *undecided*) come from it.
     */
    fun onOpenEntry(id: String) {
        val state = _uiState.value
        val entry = state.entries.firstOrNull { it.id == id } ?: state.draftEntries.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(editingEntry = entry) }
        viewModelScope.launch {
            val trip = loadTripReport(entry.date) ?: return@launch
            _uiState.update {
                it.copy(
                    candidatesForEditingEntry = trip.derivedTrip,
                    candidateOfflineRegionsForEditingEntry = trip.offlineRegions,
                    isLoadingCandidates = false,
                )
            }
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

    /** Sets (or changes) the decision on find [findId] to [kept] — see this class's own doc comment on why this takes an explicit target rather than toggling. */
    fun onSetFindDecision(findId: String, kept: Boolean) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        val decision = entry.findDecisions.firstOrNull { it.findId == findId }?.copy(kept = kept)
            ?: state.candidatesForEditingEntry?.finds?.firstOrNull { it.id == findId }?.toDecision(kept = kept)
            ?: return
        persist(entry.copy(findDecisions = entry.findDecisions.filterNot { it.findId == findId } + decision))
    }

    fun onSetTrackDecision(trackId: String, kept: Boolean) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        val decision = entry.trackDecisions.firstOrNull { it.trackId == trackId }?.copy(kept = kept)
            ?: state.candidatesForEditingEntry?.tracks?.firstOrNull { it.id == trackId }?.toDecision(kept = kept, computeTrackStatistics)
            ?: return
        persist(entry.copy(trackDecisions = entry.trackDecisions.filterNot { it.trackId == trackId } + decision))
    }

    fun onSetWaypointDecision(waypointId: String, kept: Boolean) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        val decision = entry.waypointDecisions.firstOrNull { it.waypointId == waypointId }?.copy(kept = kept)
            ?: state.candidatesForEditingEntry?.waypoints?.firstOrNull { it.id == waypointId }?.toDecision(kept = kept)
            ?: return
        persist(entry.copy(waypointDecisions = entry.waypointDecisions.filterNot { it.waypointId == waypointId } + decision))
    }

    fun onSetOfflineRegionDecision(offlineRegionId: Long, kept: Boolean) {
        val state = _uiState.value
        val entry = state.editingEntry ?: return
        val decision = entry.offlineRegionDecisions.firstOrNull { it.offlineRegionId == offlineRegionId }?.copy(kept = kept)
            ?: state.candidateOfflineRegionsForEditingEntry.firstOrNull { it.id == offlineRegionId }?.toDecision(kept = kept)
            ?: return
        persist(entry.copy(offlineRegionDecisions = entry.offlineRegionDecisions.filterNot { it.offlineRegionId == offlineRegionId } + decision))
    }

    /**
     * Attaches or detaches [photoId] (from the standalone photo library) — see
     * [CartographyEntry.photos]'s own doc comment for why this is a plain toggle, not a three-state
     * decision the way the trip-report candidates above are: a photo has no candidate pool to be
     * undecided about. No candidate lookup needed either direction: a photo id alone is enough to
     * attach it, stamped with [now].
     */
    fun onToggleKeptPhoto(photoId: String) {
        val entry = _uiState.value.editingEntry ?: return
        persist(
            if (entry.photos.any { it.photoId == photoId }) {
                entry.copy(photos = entry.photos.filterNot { it.photoId == photoId })
            } else {
                entry.copy(photos = entry.photos + PhotoAttachment(photoId = photoId, attachedAtEpochMillis = now()))
            },
        )
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

    /** Reflects [entry] immediately (so typing/deciding never feels laggy) and autosaves it — the same immediate-local-write-then-persist shape [MushroomLogViewModel.onEntryEdited] uses. */
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

    /** Compiles [date]'s trip report plus its offline-region coverage, updating [CartographyUiState]'s own loading/error fields around the two calls. `null` on failure — the caller returns early either way. */
    private suspend fun loadTripReport(date: LocalDate): TripReportCandidates? {
        _uiState.update { it.copy(isLoadingCandidates = true, candidatesErrorMessage = null) }
        val trip = getDerivedTrip(date).getOrElse { error ->
            Log.w(TAG, "Couldn't compile the trip report for $date.", error)
            _uiState.update { it.copy(isLoadingCandidates = false, candidatesErrorMessage = "Couldn't compile that day's report.") }
            return null
        }
        val offlineRegions = getTripReportOfflineRegions(trip).getOrElse { error ->
            Log.w(TAG, "Couldn't determine offline-region coverage for $date.", error)
            emptyList()
        }
        return TripReportCandidates(trip, offlineRegions)
    }

    private data class TripReportCandidates(val derivedTrip: DerivedTrip, val offlineRegions: List<OfflineRegionSummary>)

    private companion object {
        const val TAG = "Cartography"
    }
}

private fun MushroomLogEntry.toDecision(kept: Boolean) = FindDecision(
    findId = id,
    foundOn = foundOn,
    ownIdentification = ownIdentification,
    hasPhotos = photos.isNotEmpty(),
    kept = kept,
)

private fun Track.toDecision(kept: Boolean, computeTrackStatistics: ComputeTrackStatisticsUseCase): TrackDecision {
    val stats = computeTrackStatistics(points)
    return TrackDecision(
        trackId = id,
        name = name,
        distanceMeters = stats.distanceMeters,
        durationMillis = stats.durationMillis,
        pointCount = stats.totalPoints,
        kept = kept,
    )
}

private fun Waypoint.toDecision(kept: Boolean) = WaypointDecision(waypointId = id, name = name, lat = lat, lng = lng, kept = kept)

private fun OfflineRegionSummary.toDecision(kept: Boolean) = OfflineRegionDecision(
    offlineRegionId = id,
    name = name,
    lat = region.lat,
    lng = region.lng,
    radiusKm = region.radiusKm,
    kept = kept,
)
