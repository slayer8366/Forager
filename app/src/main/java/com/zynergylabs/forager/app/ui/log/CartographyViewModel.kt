package com.zynergylabs.forager.app.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val getEntry: GetCartographyEntryUseCase,
    private val commitEntry: CommitCartographyEntryUseCase,
    private val deleteEntry: DeleteCartographyEntryUseCase,
    private val getDerivedTrip: GetDerivedTripUseCase,
    private val getTripReportOfflineRegions: GetTripReportOfflineRegionsUseCase,
    private val computeTrackStatistics: ComputeTrackStatisticsUseCase,
    /** J8: writes an entry's [CartographyEntry.shownOnMap] — see [onSetShownOnMap]. */
    private val setShownOnMap: SetCartographyEntryShownOnMapUseCase,
    /** Injected so a test can fix when a photo attachment is stamped — same reasoning as every other `now`/`currentTime` provider in this codebase. */
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Where an entry delete still pending when this ViewModel is cleared is committed (journal
     * redesign J4b L2): `viewModelScope` is cancelled by then. See [PendingDeleteCommitScope].
     */
    private val pendingDeleteCommitScope: CoroutineScope = PendingDeleteCommitScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartographyUiState())
    val uiState: StateFlow<CartographyUiState> = _uiState.asStateFlow()

    /**
     * The one entry whose delete is pending (journal redesign J4b L2), moved to its own class by UI split
     * Phase 2.8 PR 1. It reads and writes this screen's state through the getter and setter here and
     * launches in this ViewModel's own scope. Declared above `init`, and must stay there: every
     * collaborator is constructed before `init` runs.
     */
    private val pendingDeletes = CartographyPendingDeletes(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        pendingDeleteCommitScope = pendingDeleteCommitScope,
        deleteEntry = deleteEntry,
    )

    /**
     * The open entry's edits and their autosave, moved to their own class by UI split Phase 2.8 PR 2. It
     * reads and writes this screen's state through the getter and setter here and launches in this
     * ViewModel's own scope. Declared above `init`, and must stay there, as [pendingDeletes] is.
     */
    private val edits = CartographyEntryEdits(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        saveEntry = saveEntry,
        computeTrackStatistics = computeTrackStatistics,
        now = now,
    )

    /**
     * Show on map and Hide from map (J8), moved to their own class by UI split Phase 2.8 PR 2. It reads
     * and writes this screen's state through the getter and setter here and launches in this ViewModel's
     * own scope. Declared above `init`, and must stay there, as [pendingDeletes] is.
     */
    private val shownOnMap = CartographyShownOnMap(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        setShownOnMap = setShownOnMap,
    )

    /**
     * Starting and reopening an entry, with the day's trip report, moved to their own class by UI split
     * Phase 2.8 PR 3. It reads and writes this screen's state through the getter and setter here and
     * launches in this ViewModel's own scope. Declared above `init`, and must stay there, as
     * [pendingDeletes] is.
     */
    private val opening = CartographyEntryOpening(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        createEntry = createEntry,
        saveEntry = saveEntry,
        getDerivedTrip = getDerivedTrip,
        getTripReportOfflineRegions = getTripReportOfflineRegions,
        computeTrackStatistics = computeTrackStatistics,
    )

    /**
     * Closing, finishing, saving, demoting, discarding and deleting the open entry, moved to their own
     * class by UI split Phase 2.8 PR 3. It reads and writes this screen's state through the getter and
     * setter here, launches in this ViewModel's own scope, and refreshes the lists through
     * [loadEntries]. Declared above `init`, and must stay there, as [pendingDeletes] is.
     */
    private val exits = CartographyEntryExits(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        commitEntry = commitEntry,
        saveEntry = saveEntry,
        getEntry = getEntry,
        deleteEntry = deleteEntry,
        reloadEntries = { loadEntries() },
    )

    init {
        loadEntries()
    }

    fun loadEntries(): Job {
        return viewModelScope.launch {
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
     * After a restore (dispatch 2026-09-28-182, item 5): reads the entries and drafts again, then closes the open entry, with
     * the candidates and the unsaved flag that belong to it, if its record is in neither list now. [loadEntries] never touches
     * the open entry, so a Replace that deleted it would leave it on screen. A read that failed closes nothing.
     */
    fun reloadAfterRestore(): Job {
        return viewModelScope.launch {
            loadEntries().join()
            _uiState.update { state ->
                val open = state.editingEntry
                when {
                    open == null || state.loadErrorMessage != null -> state
                    (state.entries + state.draftEntries).any { it.id == open.id } -> state
                    else -> {
                        Log.i(TAG, "A restore removed the open entry '${open.id}'; it is closed.")
                        state.copy(
                            editingEntry = null,
                            candidatesForEditingEntry = null,
                            candidateOfflineRegionsForEditingEntry = emptyList(),
                            hasUnsavedChanges = false,
                        )
                    }
                }
            }
        }
    }

    /** Starts a new entry for [date] with the day's candidates kept: see [CartographyEntryOpening.onStartEntry]. */
    fun onStartEntry(date: LocalDate = LocalDate.now()) = opening.onStartEntry(date)

    /** Reopens an entry with a fresh trip report: see [CartographyEntryOpening.onOpenEntry]. */
    fun onOpenEntry(id: String) = opening.onOpenEntry(id)

    /** Closes the open entry: see [CartographyEntryExits.onCloseEntry]. */
    fun onCloseEntry() = exits.onCloseEntry()

    /** The open entry's text changed: see [CartographyEntryEdits.onTextChanged]. */
    fun onTextChanged(text: String) = edits.onTextChanged(text)

    /** The open entry's tags changed: see [CartographyEntryEdits.onTagsChanged]. */
    fun onTagsChanged(tags: List<String>) = edits.onTagsChanged(tags)

    /** Keep or withhold a find: see [CartographyEntryEdits.onSetFindDecision]. */
    fun onSetFindDecision(findId: String, kept: Boolean) = edits.onSetFindDecision(findId, kept)

    /** Keep or withhold a track: see [CartographyEntryEdits.onSetTrackDecision]. */
    fun onSetTrackDecision(trackId: String, kept: Boolean) = edits.onSetTrackDecision(trackId, kept)

    /** Keep or withhold a waypoint: see [CartographyEntryEdits.onSetWaypointDecision]. */
    fun onSetWaypointDecision(waypointId: String, kept: Boolean) = edits.onSetWaypointDecision(waypointId, kept)

    /** Keep or withhold an offline region: see [CartographyEntryEdits.onSetOfflineRegionDecision]. */
    fun onSetOfflineRegionDecision(offlineRegionId: Long, kept: Boolean) = edits.onSetOfflineRegionDecision(offlineRegionId, kept)

    /** A group switch in "In this entry": see [CartographyEntryEdits.onSetEntryGroupIncluded]. */
    fun onSetEntryGroupIncluded(group: EntryGroup, included: Boolean) = edits.onSetEntryGroupIncluded(group, included)

    /** Attaches or detaches a photo: see [CartographyEntryEdits.onToggleKeptPhoto]. */
    fun onToggleKeptPhoto(photoId: String) = edits.onToggleKeptPhoto(photoId)

    /** Finishes the open draft: see [CartographyEntryExits.onFinishEntry]. */
    fun onFinishEntry() = exits.onFinishEntry()

    /** Saves a committed entry's edits in place: see [CartographyEntryExits.onSaveEntry]. */
    fun onSaveEntry() = exits.onSaveEntry()

    /** Demotes the open committed entry to a draft and closes it: see [CartographyEntryExits.onSaveEntryAsDraft]. */
    fun onSaveEntryAsDraft() = exits.onSaveEntryAsDraft()

    /** Discards the open committed entry's edits from the store: see [CartographyEntryExits.onDiscardEntryChanges]. */
    fun onDiscardEntryChanges() = exits.onDiscardEntryChanges()

    /** Deletes an entry at once, with no Undo: see [CartographyEntryExits.onDeleteEntry]. */
    fun onDeleteEntry(id: String) = exits.onDeleteEntry(id)

    /** An entry card's Delete: the entry becomes pending. See [CartographyPendingDeletes.requestDeleteEntry]. */
    fun requestDeleteEntry(id: String) = pendingDeletes.requestDeleteEntry(id)

    /** The snackbar's Undo. See [CartographyPendingDeletes.undoDeleteEntry]. */
    fun undoDeleteEntry(id: String) = pendingDeletes.undoDeleteEntry(id)

    /** The snackbar ended without Undo. See [CartographyPendingDeletes.commitDeleteEntry]. */
    fun commitDeleteEntry(id: String) = pendingDeletes.commitDeleteEntry(id)

    override fun onCleared() {
        // J4b L2: an entry still pending is committed here, since no snackbar is left to end.
        // viewModelScope is already cancelled, so the delete runs on pendingDeleteCommitScope.
        pendingDeletes.commitOnCleared()
    }

    /** Shows or hides a saved entry on the Maps tab: see [CartographyShownOnMap.onSetShownOnMap]. */
    fun onSetShownOnMap(id: String, shown: Boolean) = shownOnMap.onSetShownOnMap(id, shown)

    /** Clears the show-on-map failure message: see [CartographyShownOnMap.onShownOnMapErrorDismissed]. */
    fun onShownOnMapErrorDismissed() = shownOnMap.onShownOnMapErrorDismissed()

    /**
     * Clears [CartographyUiState.saveErrorMessage] once its Toast has shown. The Toast is hosted by the
     * Journal, `JournalTab` and `LogPanel`, as the find editor's is (intent 2026-09-28-68, the owner's
     * "Option B" in continuation -76), so a message set while the Journal is not on screen waits here
     * until it next opens. Every success path also clears it.
     */
    fun onSaveErrorDismissed() {
        _uiState.update { it.copy(saveErrorMessage = null) }
    }

    internal companion object {
        const val TAG = "Cartography"
    }
}
