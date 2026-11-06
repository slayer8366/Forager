package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.isOwnMarkerOf
import com.zynergylabs.forager.app.domain.isShortRecording
import com.zynergylabs.forager.app.domain.model.DerivedTrip
import com.zynergylabs.forager.app.ui.log.CartographyViewModel.Companion.TAG
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opening an entry in [CartographyViewModel] (UI split Phase 2.8, PR 3, dispatch 2026-10-19-34, RECORD
 * -1460): starting a new one and reopening an existing one, each with a fresh trip report for its day,
 * and the trip-report load they share, moved out of the ViewModel unchanged. It holds no state of its
 * own. Every value it reads from the screen's state comes through [uiState], every write goes through
 * [updateState], and its coroutines launch in [scope], the ViewModel's own `viewModelScope`, read each
 * time rather than kept. It holds no job and cancels none.
 */
internal class CartographyEntryOpening(
    private val uiState: () -> CartographyUiState,
    private val updateState: ((CartographyUiState) -> CartographyUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val createEntry: CreateCartographyEntryUseCase,
    private val saveEntry: SaveCartographyEntryUseCase,
    private val getDerivedTrip: GetDerivedTripUseCase,
    private val getTripReportOfflineRegions: GetTripReportOfflineRegionsUseCase,
    private val computeTrackStatistics: ComputeTrackStatisticsUseCase,
) {

    /**
     * Starts a brand-new entry for [date], persists it immediately as a draft with every one of that
     * day's *initial* candidates already kept, and opens it for curation. See [CartographyViewModel]'s own doc
     * comment for why only this one moment auto-decides candidates.
     */
    fun onStartEntry(date: LocalDate = LocalDate.now()) {
        scope().launch {
            val trip = loadTripReport(date) ?: return@launch

            createEntry(date).fold(
                onSuccess = { draft ->
                    val leftOutRecordings = trip.derivedTrip.tracks.filter { it.isShortRecording() }.map { it.id }.toSet()
                    val decided = draft.copy(
                        findDecisions = trip.derivedTrip.finds.map { it.toDecision(kept = true) },
                        // RECORD -867 ("Leave out recordings under a minute"): a recording shorter than a minute
                        // starts left out, a decision the editor shows and the user can change; it stays in Records.
                        trackDecisions = trip.derivedTrip.tracks.map { it.toDecision(kept = !it.isShortRecording(), computeTrackStatistics) },
                        // RECORD -873: and that recording's own Start and End markers with it; a waypoint the user saved
                        // during it stays in.
                        waypointDecisions = trip.derivedTrip.waypoints.map { it.toDecision(kept = !it.isOwnMarkerOf(leftOutRecordings)) },
                        offlineRegionDecisions = trip.offlineRegions.map { it.toDecision(kept = true) },
                    )
                    // The stamped copy is the one to keep -- see SaveCartographyEntryUseCase's own
                    // doc comment. A failed save still opens the entry as decided: the next
                    // persist retries, and the user must not lose the day's candidates. Logged
                    // and not shown (intent 2026-09-28-68, the planner's ruling in -76).
                    val saved = saveEntry(decided).getOrElse { error ->
                        Log.w(TAG, "Couldn't save new entry '${draft.id}' with its day's candidates kept; it opens with them kept, unsaved.", error)
                        decided
                    }
                    updateState {
                        it.copy(
                            editingEntry = saved,
                            candidatesForEditingEntry = trip.derivedTrip,
                            candidateOfflineRegionsForEditingEntry = trip.offlineRegions,
                            isLoadingCandidates = false,
                            saveErrorMessage = null,
                            hasUnsavedChanges = false,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't start a new Cartography entry.", error)
                    updateState { it.copy(isLoadingCandidates = false, candidatesErrorMessage = "Couldn't start a new entry.") }
                },
            )
        }
    }

    /**
     * Reopens an already-created entry (committed or draft) and reloads a fresh trip report for its
     * day — see [CartographyViewModel]'s own doc comment. The entry's own persisted decisions are untouched by
     * this reload; only [CartographyUiState.candidatesForEditingEntry]/[CartographyUiState.candidateOfflineRegionsForEditingEntry]
     * (what the edit screen offers as *undecided*) come from it.
     */
    /**
     * Opens an entry for editing or its report. **The entry is published only after the day's trip
     * report has loaded** (timestamp-filter dispatch, Item 2, owner decision): each track decision's
     * cached distance/duration/point count is recomputed from the track as the read seam now returns
     * it, written back if it changed, and the screen's first frame already shows the corrected
     * figures — there is no window in which a stale snapshot and a fresh figure disagree on screen.
     * The snapshot exists so an entry never silently changes on reopen (see
     * `CartographyEntryTrackRefEntity`'s own doc comment, where this exception is recorded): a cached
     * distance is not authored content, and the cached number was wrong. A decision whose track has
     * since been deleted has nothing to recompute from and keeps its figure. If the trip report cannot
     * be loaded, the entry opens uncorrected with the existing error message rather than not at all.
     */
    fun onOpenEntry(id: String) {
        val state = uiState()
        val entry = state.entries.firstOrNull { it.id == id } ?: state.draftEntries.firstOrNull { it.id == id } ?: return
        scope().launch {
            val trip = loadTripReport(entry.date)
            if (trip == null) {
                updateState { it.copy(editingEntry = entry, hasUnsavedChanges = false) }
                return@launch
            }
            val recomputed = entry.withRecomputedTrackSnapshots(trip.derivedTrip.tracks, computeTrackStatistics)
            // The stamped copy when the write-back landed, the recomputed one when it did not --
            // see SaveCartographyEntryUseCase's own doc comment for why the returned entry is the
            // one to keep.
            val corrected = if (recomputed != entry) {
                saveEntry(recomputed).getOrElse { error ->
                    // Shown corrected regardless: the figure on screen must be the one the track
                    // now yields, and the next open recomputes again if this write did not land.
                    Log.w(TAG, "Couldn't write back recomputed track figures for entry '${entry.id}'.", error)
                    recomputed
                }
            } else {
                entry
            }
            if (corrected != entry) {
                updateState { current ->
                    current.copy(
                        entries = current.entries.map { if (it.id == corrected.id) corrected else it },
                        draftEntries = current.draftEntries.map { if (it.id == corrected.id) corrected else it },
                    )
                }
            }
            updateState {
                it.copy(
                    editingEntry = corrected,
                    hasUnsavedChanges = false,
                    candidatesForEditingEntry = trip.derivedTrip,
                    candidateOfflineRegionsForEditingEntry = trip.offlineRegions,
                    isLoadingCandidates = false,
                )
            }
        }
    }

    /** Compiles [date]'s trip report plus its offline-region coverage, updating [CartographyUiState]'s own loading/error fields around the two calls. `null` on failure — the caller returns early either way. */
    private suspend fun loadTripReport(date: LocalDate): TripReportCandidates? {
        updateState { it.copy(isLoadingCandidates = true, candidatesErrorMessage = null) }
        val trip = getDerivedTrip(date).getOrElse { error ->
            Log.w(TAG, "Couldn't compile the trip report for $date.", error)
            updateState { it.copy(isLoadingCandidates = false, candidatesErrorMessage = "Couldn't compile that day's report.") }
            return null
        }
        val offlineRegions = getTripReportOfflineRegions(trip).getOrElse { error ->
            Log.w(TAG, "Couldn't determine offline-region coverage for $date.", error)
            emptyList()
        }
        return TripReportCandidates(trip, offlineRegions)
    }

    private data class TripReportCandidates(val derivedTrip: DerivedTrip, val offlineRegions: List<OfflineRegionSummary>)
}
