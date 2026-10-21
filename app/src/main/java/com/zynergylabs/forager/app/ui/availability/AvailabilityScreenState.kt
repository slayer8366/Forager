package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, PR 1 (dispatch 2026-10-19-40): the main screen's holder.

import com.zynergylabs.forager.app.ui.log.JournalScreenState
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown

/**
 * What the code moved out of [AvailabilityScreen]'s body reads and writes of the state that body
 * remembers. Every state is still remembered in the body, where it always was, with the same key and
 * the same lifetime; this holder only reaches it. Each field is a getter or a setter onto that state,
 * never a copy of its value (Stage G, `docs/audits/2026-09-26-split-stage-g-completion-report.md`).
 * [journalScreenState] is a holder already, so it is passed as itself.
 *
 * Not remembered: the body builds it on every composition, so it adds no slot. Values the body works
 * out afresh on each composition (whether a navigation is active, and the like) are not here; they are
 * passed as values to whatever reads them, exactly as the lambdas they were read in captured them.
 */
internal class AvailabilityScreenState(
    val compactTab: () -> CompactTab,
    val setCompactTab: (CompactTab) -> Unit,
    val selectedTab: () -> ResultsTab,
    val setSelectedTab: (ResultsTab) -> Unit,
    val journalScreenState: JournalScreenState,
    val waypointNavigationOrigin: () -> WaypointNavigationOrigin?,
    val setWaypointNavigationOrigin: (WaypointNavigationOrigin?) -> Unit,
    val setMapWaypointReopen: (WaypointNavigationOrigin?) -> Unit,
    val setRecordsWaypointReopen: (String?) -> Unit,
    val navigationFollowBroken: () -> Boolean,
    val setNavigationFollowBroken: (Boolean) -> Unit,
    val navigationViewRequestId: () -> Int,
    val setNavigationViewRequestId: (Int) -> Unit,
    // Phase 2.6, PR 2: what the map input builders read and write (AvailabilityScreenMapInputs.kt).
    val forecastCellsShown: () -> Map<String, ForecastCellsShown>,
    val forecastZoomedOut: () -> Boolean,
    val mapLegendExpanded: () -> Boolean,
    val setMapLegendExpanded: (Boolean) -> Unit,
    val chosenMapMode: () -> MapMode?,
    val setPendingJournalDestination: (PendingJournalDestination?) -> Unit,
    val setPendingJournalFindId: (String?) -> Unit,
    val setPendingJournalEntryId: (String?) -> Unit,
    // Phase 2.6, PR 3: what the journal-return code reads and writes (AvailabilityScreenJournalReturn.kt).
    val isMapFullscreen: () -> Boolean,
    val setIsMapFullscreen: (Boolean) -> Unit,
    val setIsDrawerOpen: (Boolean) -> Unit,
    // Phase 2.6, E2: the Back handlers read it (AvailabilityScreenBackHandlers.kt).
    val isDrawerOpen: () -> Boolean,
    val toolsReturn: () -> ToolsOfflineMapsReturn?,
    val setToolsReturn: (ToolsOfflineMapsReturn?) -> Unit,
    val setToolsReturnArrived: (Boolean) -> Unit,
    // Phase 2.6, PR 4: what the drawer host reads (AvailabilityScreenDrawerHost.kt).
    val openSettingsOnlyRequest: () -> Int,
    // Phase 2.6, D: what the notices' triggers write (AvailabilityScreenNotices.kt).
    val setOpenSettingsOnlyRequest: (Int) -> Unit,
)
