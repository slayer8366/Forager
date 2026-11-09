package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapChromeSheetNavigationBar
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * The details sheet for one Records row (journal redesign J5c): a Material 3 [ModalBottomSheet] with
 * the record's full information and its actions. Back and a scrim tap dismiss it (the sheet's own
 * behaviour, through [onDismiss]).
 *
 * **Everything shown is already in memory where the row is** (the dispatch's "no new query, column
 * or migration"): the lists [RecordsTab] was handed, the waypoints' reference counts it was handed,
 * and figures derived from them in the same way their rows or cards already derive them. A target
 * whose record is no longer in those lists (a delete landed, a list reloaded) closes the sheet.
 *
 * **Opened fully expanded** (`skipPartiallyExpanded`), so every field shows without a drag in
 * portrait; in a short landscape window the sheet takes the height it has and its content scrolls.
 *
 * **Waypoint: Navigate, then Directions** (dispatch 2026-09-28-502, plan task T9). The owner's M1 ruling
 * asked for Navigate (the app's own HUD) beside Directions; J5c shipped Directions only, because nothing
 * then navigated to a chosen waypoint. [onNavigateToWaypoint] is that entry point now: the sheet closes
 * and the Maps tab navigates to the waypoint, and Back opens this sheet again (Amendment 1). A host that
 * passes none (an entry map's) offers Directions only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordDetailsSheet(
    target: RecordDetailsTarget,
    waypoints: List<Waypoint>,
    tracks: List<Track>,
    offlineRegions: List<OfflineRegionSummary>,
    /** `RecordsTab`'s `waypointEntryReferenceCounts`. A waypoint with no entry here has no count to show, and the line is left out. */
    waypointEntryReferenceCounts: Map<String, Int>,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    staleThresholdDays: Int,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    /** The track's Delete: a pending delete with Undo, as the list's swipe asks for (Part 2 follow-ups F1 item 5, owner "Option A"). `null` shows none; a track still recording never shows one. */
    onDeleteTrack: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    /**
     * Whether a map is drawn on screen beneath the sheet (map chrome at 80%, dispatch 2026-09-28-56 as
     * amended by -58; planner message -77): the container is then at `MAP_CHROME_OVER_MAP_ALPHA`, and
     * otherwise solid, unchanged. Each call site says which it is.
     */
    overMap: Boolean = false,
    /** Dispatch 2026-09-28-502: a waypoint's "Navigate", given its id; `null` offers none (an entry map's sheet). */
    onNavigateToWaypoint: ((String) -> Unit)? = null,
    /**
     * Dispatch -616 as amended by -618: shows another record's details in this sheet's place: a waypoint
     * from a walk's "Waypoints on this track" list, and the walk again on Back from it. `null` leaves the
     * list's rows without a tap, and Back closes the sheet.
     */
    onOpenDetails: ((RecordDetailsTarget) -> Unit)? = null,
) {
    // One sheet per target, so a target opened in this one's place gets a sheet of its own, shown fresh:
    // the sheet Back has just hidden is not reused (its state is hidden, and it would stay so).
    key(target) {
        RecordDetailsSheetFor(
            target, waypoints, tracks, offlineRegions, waypointEntryReferenceCounts, distanceUnit, nowEpochMillis,
            staleThresholdDays, getFullRecord, onDeleteTrack, onDismiss, overMap, onNavigateToWaypoint, onOpenDetails,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailsSheetFor(
    target: RecordDetailsTarget,
    waypoints: List<Waypoint>,
    tracks: List<Track>,
    offlineRegions: List<OfflineRegionSummary>,
    waypointEntryReferenceCounts: Map<String, Int>,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    staleThresholdDays: Int,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    onDeleteTrack: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    overMap: Boolean,
    onNavigateToWaypoint: ((String) -> Unit)?,
    onOpenDetails: ((RecordDetailsTarget) -> Unit)?,
) {
    val waypoint = (target as? RecordDetailsTarget.WaypointDetails)?.let { t -> waypoints.firstOrNull { it.id == t.id } }
    val track = (target as? RecordDetailsTarget.TrackDetails)?.let { t -> tracks.firstOrNull { it.id == t.id } }
    val region = (target as? RecordDetailsTarget.OfflineRegionDetails)?.let { t -> offlineRegions.firstOrNull { it.id == t.id } }
    if (waypoint == null && track == null && region == null) {
        // Not an error: the record left the list the row came from. Nothing to show, so no sheet.
        LaunchedEffect(target) { onDismiss() }
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Material3's own default container role for a sheet (`BottomSheetDefaults.ContainerColor`), at the
    // map chrome's alpha over a map, as the Layers sheet is (`MapLayersSheet`). The content colour is
    // pinned to the role's own for the reason given there: `contentColorFor` matches a colour-scheme
    // role exactly. The scrim stays Material3's default.
    // C1: the navigation bar's colour (was surfaceContainerLow); alpha as it was, 0.8 over a map and solid elsewhere.
    val containerColor = mapChromeFill(navigationBarContainerColor(), overMap)
    val contentColor = contentColorFor(navigationBarContainerColor())
    // Back, a scrim touch or a drag down: back to where this target was opened from, if anywhere.
    val parent = target.returnsTo()
    val dismiss: () -> Unit = if (parent != null && onOpenDetails != null) ({ onOpenDetails(parent) }) else onDismiss
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(RECORD_DETAILS_SHEET_TAG).mapChromeContainerColor(containerColor),
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        // Over a map only: the band under the sheet follows the sheet's own container (item 3, -104).
        if (overMap) MapChromeSheetNavigationBar()
        Column(
            modifier = Modifier
                .mapChromeContentColor(LocalContentColor.current)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RecordDetailsBody(
                waypoint = waypoint,
                track = track,
                region = region,
                waypoints = waypoints,
                tracks = tracks,
                waypointEntryReferenceCounts = waypointEntryReferenceCounts,
                distanceUnit = distanceUnit,
                nowEpochMillis = nowEpochMillis,
                staleThresholdDays = staleThresholdDays,
                getFullRecord = getFullRecord,
                onDeleteTrack = onDeleteTrack,
                onNavigateToWaypoint = onNavigateToWaypoint,
                onOpenDetails = onOpenDetails,
                overMap = overMap,
            )
        }
    }
}

/** What the record's details show in [RecordDetailsSheet]. Exactly one of [waypoint], [track], [region] is non-null. */
@Composable
private fun RecordDetailsBody(
    waypoint: Waypoint?,
    track: Track?,
    region: OfflineRegionSummary?,
    waypoints: List<Waypoint>,
    tracks: List<Track>,
    waypointEntryReferenceCounts: Map<String, Int>,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    staleThresholdDays: Int,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    onDeleteTrack: ((String) -> Unit)?,
    onNavigateToWaypoint: ((String) -> Unit)?,
    onOpenDetails: ((RecordDetailsTarget) -> Unit)?,
    overMap: Boolean,
) {
    when {
        waypoint != null -> WaypointDetails(waypoint, tracks, waypointEntryReferenceCounts, onNavigateToWaypoint)
        track != null -> TrackDetails(
            track,
            waypoints,
            distanceUnit,
            getFullRecord,
            onDeleteTrack,
            onOpenWaypoint = onOpenDetails?.let { open -> { id: String -> open(RecordDetailsTarget.WaypointDetails(id, fromTrackId = track.id)) } },
            overMap = overMap,
        )
        region != null -> OfflineRegionDetails(region, distanceUnit, nowEpochMillis, staleThresholdDays)
    }
}
