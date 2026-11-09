package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, PR 2 (dispatch 2026-10-19-40): what AvailabilityScreen hands the Maps tab's map, moved out of the body
// as plain functions. Not composable, so composition is unchanged; the body calls each below the holder, and every state
// they read is read at that call, during the same composition, through the holder's getters.

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapForecastFeed
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.OPEN_IN_JOURNAL_LABEL
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import com.zynergylabs.forager.app.ui.map.layers.JOURNAL_ENTRIES_SWITCH_LAYER_ID
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState

/** The Layers sheet's inputs for the Maps tab. Reads the forecast cells, the zoomed-out flag and the legend's flag through [state]. */
internal fun availabilityMapLayersControls(
    state: AvailabilityScreenState,
    uiState: AvailabilityUiState,
    availableColourFieldGroups: Map<String, String>,
    mapRecordsDrawn: MapRecords,
    journalHighlights: JournalEntryHighlights,
    onSetCartographyEntryShownOnMap: (entryId: String, shown: Boolean) -> Unit,
    onMapLayerVisibilityChanged: (String, Boolean) -> Unit,
    onMapLayerOpacityChanged: (String, Float) -> Unit,
    onColourFieldMoved: (String, ColourFieldMove) -> Unit,
): MapLayersControls = MapLayersControls(
    stored = uiState.mapLayers,
    availableColourFields = availableColourFieldGroups.keys,
    cellsShown = state.forecastCellsShown().filterKeys { it in availableColourFieldGroups },
    forecastZoomedOut = state.forecastZoomedOut(),
    records = mapRecordsDrawn,
    journalHighlights = journalHighlights,
    // J8-3: the chip's list. Hiding writes shownOnMap for each entry, one write each; the Layers
    // sheet's "Journal entries" switch is a separate, display-only choice and is never touched here.
    onHideJournalEntry = { id -> onSetCartographyEntryShownOnMap(id, false) },
    onHideAllJournalEntries = { journalHighlights.shownEntries.forEach { onSetCartographyEntryShownOnMap(it.entryId, false) } },
    legendExpanded = state.mapLegendExpanded(),
    onLegendExpandedChange = { state.setMapLegendExpanded(it) },
    onVisibilityChanged = onMapLayerVisibilityChanged,
    onOpacityChanged = onMapLayerOpacityChanged,
    onColourFieldMoved = onColourFieldMoved,
)

/** How the Maps tab's map is drawn. Reads the basemap picked on this screen through [state]. */
internal fun availabilityMapRenderMode(
    state: AvailabilityScreenState,
    uiState: AvailabilityUiState,
    basemap: Basemap,
    isNightMode: Boolean,
    drawnMapLayers: MapLayersState,
    forecastFeed: MapForecastFeed?,
): MapRenderMode = MapRenderMode(
    basemap = basemap,
    night = isNightMode,
    // The cold-launch gate: no style until the Night Maps preference and the stored basemap are
    // both in (colour build C1; dispatch 2026-09-28-708, "Restoring at launch must not flash the
    // wrong basemap first"). The field is named for the first; see MapRenderMode.nightModeLoaded.
    nightModeLoaded = uiState.nightModeMapsLoaded && (uiState.mapMode != null || state.chosenMapMode() != null),
    layers = drawnMapLayers,
    forecast = forecastFeed,
    // Dispatch 2026-10-19-37: the Maps tab's map springs back from a small nudge while following.
    nudgeSpringBack = true,
)

/**
 * M1 (B3, B4): what the Maps tab's glyph bubbles look records up in, and the J5c sheet's inputs, all
 * already in hand in the body. A find's "Open in Journal" opens the find in its report over whatever
 * the Journal was showing (PendingJournalDestination.VIEW_FIND), so the saved Records chip and top
 * tab are never changed (planner's ruling on F3, continuation 2026-09-28-30). The Journal tab comes up.
 * Checked against the Maps search-bar gate (AvailabilityCompactScaffold's isEditingJournalEntry):
 * the find opens as the Journal tab comes up, where that gate hides the header as it does for any
 * open find; the Maps tab's own bar is gated on the Journal showing, so it is unaffected.
 *
 * [logUiState] is the value of the composition that builds these, as the body's own lambdas captured
 * it; the pending Journal requests and the tab are written through [state].
 */
internal fun availabilityMapBubbleSources(
    state: AvailabilityScreenState,
    uiState: AvailabilityUiState,
    logUiState: MushroomLogUiState,
    waypoints: List<Waypoint>,
    waypointEntryReferenceCounts: Map<String, Int>,
    tracks: List<Track>,
    currentTime: CurrentTimeProvider,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    drawnMapLayers: MapLayersState,
    journalHighlights: JournalEntryHighlights,
    leaveLogEntryEditingOfferingDiscard: () -> Unit,
    onOpenLogEntry: (String) -> Unit,
    onNavigateToWaypoint: ((String) -> Unit)?,
    startWaypointNavigation: (WaypointNavigationOrigin) -> Unit,
): MapRecordSources = MapRecordSources(
    finds = logUiState.entries,
    galleryPhotos = logUiState.galleryPhotos,
    photoEntryReferenceCounts = logUiState.cartographyEntryPhotoReferenceCounts,
    waypoints = waypoints,
    waypointEntryReferenceCounts = waypointEntryReferenceCounts,
    tracks = tracks,
    plannedTrips = uiState.plannedTrips,
    offlineRegions = uiState.visibleOfflineRegions,
    distanceUnit = uiState.distanceUnit,
    staleThresholdDays = uiState.offlineStaleThresholdDays,
    nowEpochMillis = currentTime::nowEpochMillis,
    getFullRecord = getFullRecord,
    onOpenFind = { findId ->
        // Intent 2026-09-28-44, F3 (the owner, continuation 2026-09-28-45: "Leave the kept one
        // first (Recommended)"): a find kept open on the Journal is left before this one opens
        // over it, through the one wrapper, so a changed one lands in Drafts at once with "Saved
        // to Drafts", an unchanged re-edit's copy is deleted, and a viewed one just closes.
        if (logUiState.editingEntry != null) leaveLogEntryEditingOfferingDiscard()
        state.setPendingJournalFindId(findId)
        state.setPendingJournalDestination(PendingJournalDestination.VIEW_FIND)
        onOpenLogEntry(findId)
        state.setCompactTab(CompactTab.JOURNAL)
    },
    openFindLabel = OPEN_IN_JOURNAL_LABEL,
    // J8-4: a highlighted record's keeping entries, while the Layers sheet's "Journal entries"
    // switch shows the highlights; with it off no record is highlighted, so no bubble names one.
    journalEntriesKeeping = if (drawnMapLayers.stateOf(JOURNAL_ENTRIES_SWITCH_LAYER_ID).visible) journalHighlights.keptIn else emptyMap(),
    // J8-4, the owner's Q1 ruling ("Open in Journal, prompt first (Recommended)"): switches to the
    // Journal with the
    // entry in its report on Entries, the one top-tab change opening it requires; the saved Records
    // chip is untouched. The Journal side (CartographyScreen's openEntryRequest) asks the existing
    // "Save your changes?" first when another entry is open in its editor with unsaved changes, and
    // just closes an unchanged one. A find kept open on the Journal is left first through the one
    // wrapper, as M1's find route does (F3), since it would otherwise show over the entry.
    onOpenEntry = { entryId ->
        if (logUiState.editingEntry != null) leaveLogEntryEditingOfferingDiscard()
        // Not forgotten any more (dispatch 2026-09-28-387, Part B): the bubble the line was tapped in has just remembered the way back.
        state.setPendingJournalEntryId(entryId)
        state.setPendingJournalDestination(PendingJournalDestination.VIEW_ENTRY)
        state.setCompactTab(CompactTab.JOURNAL)
    },
    // Dispatch -502: the Maps tab's bubbles and the sheet opened from them offer Navigate; an entry map's do not.
    onNavigateToWaypoint = onNavigateToWaypoint?.let { startWaypointNavigation },
)
