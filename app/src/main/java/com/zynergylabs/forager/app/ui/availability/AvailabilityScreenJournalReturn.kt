package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, PR 3 (dispatch 2026-10-19-40): the ways into and back out of the Journal, moved out of AvailabilityScreen's
// body as plain functions. Not composable, so composition is unchanged; the body calls each at the line where it built the
// lambda before, and every remember and effect those lambdas sat beside stays in the body.

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination
import com.zynergylabs.forager.app.ui.log.leaveKeepsDraft
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * RECORD -868 and -871: the Tools drawer's "Offline maps" row (see the body's comment on toolsReturn). Every state it reads
 * and writes goes through [state], at the moment the row is tapped, as the body's own lambda read them.
 */
internal fun buildOpenOfflineMapsFromTools(
    state: AvailabilityScreenState,
    onMapFullscreenChanged: (Boolean) -> Unit,
): () -> Unit = {
    if (state.toolsReturn() == null) {
        state.setToolsReturn(
            ToolsOfflineMapsReturn(
                compactTab = state.compactTab(),
                selectedTab = state.selectedTab(),
                journalTopTab = state.journalScreenState.topTab,
                recordsFilter = state.journalScreenState.recordsFilter,
                mapFullscreen = state.isMapFullscreen(),
            ),
        )
        state.setToolsReturnArrived(false)
    }
    state.setIsDrawerOpen(false)
    if (state.isMapFullscreen()) {
        state.setIsMapFullscreen(false)
        onMapFullscreenChanged(false)
    }
    state.setPendingJournalDestination(PendingJournalDestination.VIEW_OFFLINE_MAPS)
    state.setCompactTab(CompactTab.JOURNAL)
}

/**
 * Intent 2026-09-28-44, F1: the one wrapped "leaving without answering" callback (see the body's comment above its call).
 * [logUiState] is the value of the composition that builds it, as the body's own lambda captured it; [latestLogUiState]
 * reads the latest one when the snackbar's Discard is tapped, as `rememberUpdatedState` gave it before.
 */
internal fun buildLeaveLogEntryEditingOfferingDiscard(
    logUiState: MushroomLogUiState,
    latestLogUiState: () -> MushroomLogUiState,
    onLeaveLogEntryEditingIncidentally: () -> Unit,
    onDiscardLogDraft: (String) -> Unit,
    logDraftSnackbarScope: CoroutineScope,
    logDraftSnackbarHostState: SnackbarHostState,
): () -> Unit = {
    val left = logUiState.editingEntry
    val keptDraftId = left?.takeIf { leaveKeepsDraft(it, logUiState.entries) }?.id
    onLeaveLogEntryEditingIncidentally()
    if (keptDraftId != null) {
        logDraftSnackbarScope.launch {
            val result = logDraftSnackbarHostState.showSnackbar(
                message = "Saved to Drafts",
                actionLabel = "Discard",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (latestLogUiState().draftEntries.any { it.id == keptDraftId }) {
                    onDiscardLogDraft(keptDraftId)
                } else {
                    Log.w("AvailabilityScreen", "Discard on \"Saved to Drafts\" ignored: '$keptDraftId' is no longer a draft.")
                }
            }
        }
    }
}

/** Item 8 and dispatch 2026-09-28-387, Part B: leaving a find or an entry opened from the Maps tab returns to the Maps tab. */
internal class MapReturnCallbacks(
    val onFindReportClosed: (String?) -> Unit,
    val onEntryClosed: (String, Boolean) -> Unit,
    val onFindDeleted: (String) -> Unit,
)

/** The tab is written through [state]; [mapReturnMemory] is the one the body remembers, passed as itself. */
internal fun mapReturnCallbacks(state: AvailabilityScreenState, mapReturnMemory: MapReturnMemory): MapReturnCallbacks {
    val onFindReportClosed: (String?) -> Unit = { closing ->
        if (closing != null && mapReturnMemory.onFindClosed(closing)) {
            state.setCompactTab(CompactTab.MAP)
            state.setSelectedTab(ResultsTab.MAP)
        }
    }
    // Dispatch 2026-09-28-387, Part B: an entry opened from a bubble's "kept in" line returns to the Maps tab, with that bubble and the fan, when it is left from its
    // report; the Maps tab it comes back to is built with them (MapReturnMemory.takeEntryBubble, takeFanKeys).
    val onEntryClosed: (String, Boolean) -> Unit = { entryId, fromReport ->
        if (mapReturnMemory.onEntryClosed(entryId, fromReport)) {
            state.setCompactTab(CompactTab.MAP)
            state.setSelectedTab(ResultsTab.MAP)
        }
    }
    val onFindDeleted: (String) -> Unit = { deleted ->
        if (mapReturnMemory.onFindDeleted(deleted)) {
            state.setCompactTab(CompactTab.MAP)
            state.setSelectedTab(ResultsTab.MAP)
        }
    }
    return MapReturnCallbacks(onFindReportClosed = onFindReportClosed, onEntryClosed = onEntryClosed, onFindDeleted = onFindDeleted)
}
