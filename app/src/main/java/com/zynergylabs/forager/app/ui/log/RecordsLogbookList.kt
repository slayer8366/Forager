package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.ui.motion.ListRowMotion
import com.zynergylabs.forager.app.ui.motion.ListRowShape
import com.zynergylabs.forager.app.ui.motion.rememberListRows
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.isOfflineRegionStale
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.availability.OfflineRegionRow
import com.zynergylabs.forager.app.ui.availability.WaypointRow
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.track.TrackExportRow
import com.zynergylabs.forager.app.ui.track.canBeDeleted
import com.zynergylabs.forager.app.ui.track.trackTitle
import com.zynergylabs.forager.app.ui.format.is24HourClock

/**
 * Records' **All** view — journal redesign J1, S4 (plan J4; the owner's answers in
 * `prompts/preserved/2026-09-27-17.md`): every record in one list. Since RECORD -868 it is grouped by kind, in chip
 * order (Finds, Tracks, Waypoints, Offline maps), each section under a header with its count and newest first inside;
 * a kind with nothing in it is left out. It was grouped by day until then. [buildRecordsLogbook] owns the order.
 *
 * **The rows are the chips' own rows** (owner's answer 2, "Same rows as their chips"): [FindTile],
 * [TrackExportRow], [WaypointRow] and [OfflineRegionRow], each exactly as its single-type chip shows
 * it, controls included, with a [RecordTypeBadge] in front. Finds sit two to a row, as the Finds
 * gallery's own two-column grid shows them. Deletes confirm through the same dialogs the chips use
 * ([WaypointDeleteDialog], [OfflineRegionDeleteDialog]); nothing about deleting changes in J1.
 * Journal redesign J4 replaced both dialogs: a waypoint's or region's whole badged row swipes, as in its own chip (a
 * [TwoStageSwipeRow] since J4b L6), and [onDeleteWaypoint]/[onDeleteOfflineRegion] ask for a
 * pending delete with Undo. The regions listed are [AvailabilityUiState.visibleOfflineRegions].
 *
 * **Tapping a find** calls [onOpenFind]: the find's report slides in over All, which stays the selected chip, and Back
 * goes report, All, Finds, then out of Records (RECORD -874, the owner: "Find > All > Finds > out"). Waypoint, track
 * and region rows had no row tap until journal redesign J5c: a tap on one now opens its details
 * sheet ([onOpenDetails], [RecordDetailsSheet]).
 *
 * [finds] `null` means the caller has no finds list to give (`LogPanel`, out of scope until J6): the
 * logbook then says so in a line of its own rather than presenting the timed records as everything.
 *
 * A scrolling `Column`, like the single-type lists it gathers (`WaypointsSection`, `TrackExportList`),
 * not a lazy list: those lists already compose every row, and this one is their sum.
 */
@Composable
internal fun RecordsLogbookList(
    finds: List<MushroomLogEntry>?,
    tracks: List<Track>,
    waypoints: List<Waypoint>,
    availabilityUiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    onDeleteWaypoint: (String) -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Part 2 follow-ups F1 item 5: a finished track row's swipe (pending delete with Undo); `null` leaves track rows without one. A recording track never has one. */
    onDeleteTrack: ((String) -> Unit)? = null,
    onDownloadAgain: (Long) -> Unit = {},
    onOpenFind: (String) -> Unit,
    /** J4b L1: a find tile's long-press Delete (pending, with Undo); `null` leaves the tiles tap-only. */
    onDeleteFind: ((String) -> Unit)? = null,
    /** J4b L1: a find tile's long-press Edit. */
    onEditFind: ((String) -> Unit)? = null,
    /**
     * Journal redesign J5c: a tap on a closed waypoint, track or region row (its whole badged row,
     * badge included) opens that record's details sheet. `null` leaves those rows without a tap. A
     * tap on a row whose two-stage swipe is open closes the row instead (J4b's overlay takes it).
     */
    onOpenDetails: ((RecordDetailsTarget) -> Unit)? = null,
    /** Dispatch 2026-09-28-502: a waypoint row's Navigate, given its id; `null` (the default) offers none. */
    onNavigateToWaypoint: ((String) -> Unit)? = null,
) {
    val sections = buildRecordsLogbook(
        finds = finds.orEmpty(),
        tracks = tracks,
        waypoints = waypoints,
        offlineRegions = availabilityUiState.visibleOfflineRegions,
    )
    // Motion Part 3, item 2 (RECORD -651, Lists: "Slide and close up"; motion/ListMotion.kt): a deleted track, waypoint or
    // offline map row fades and shrinks while the rows below close up, and Undo brings it back the way it went. A section
    // whose last record goes leaves the same way, header and all (it was a day until RECORD -868). Find tiles shrink and
    // grow the same way (Amendment 1, scout R5).
    val sectionRows = rememberListRows(sections, key = { it.type })
    val now = currentTime.nowEpochMillis()
    // J4b L6: one open row at a time across the logbook; a touch elsewhere or a scroll closes it.
    val swipeGroup = rememberSwipeRevealGroup()
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState, swipeGroup) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling -> if (scrolling) swipeGroup.closeAll() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .swipeRevealTouchWatcher(swipeGroup)
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (finds == null) {
            Text(
                "Finds are not listed here. See the Finds chip.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (sectionRows.isEmpty()) {
            Text("No records yet.", style = MaterialTheme.typography.bodyMedium)
        }
        sectionRows.forEach { sectionRow -> key(sectionRow.key) { ListRowMotion(sectionRow) { section ->
          // One column per section, with the list's own spacing, so a leaving section shrinks as one block.
          Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LogbookSectionHeader(section)
            // Amendment 1 (RECORD -681: "Every list's rows close up and grow in"; scout R5): a deleted find's tile fades and shrinks in
            // place, and Undo grows it back; once it has gone the pairs re-pair. That re-pairing is still a jump: these rows are a
            // plain column, with no placement glide (reported as a stop).
            val findRows = rememberListRows(section.finds, key = { it.id })
            findRows.chunked(FIND_COLUMNS).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { findRow ->
                        Box(modifier = Modifier.weight(1f)) {
                          ListRowMotion(findRow, shape = ListRowShape.TILE) { find ->
                            Box(modifier = Modifier.testTag(logbookRowTag(RecordType.FINDS, find.id))) {
                                FindTileWithOptions(entry = find, onClick = { onOpenFind(find.id) }, onEdit = onEditFind, onDelete = onDeleteFind)
                                RecordTypeBadge(
                                    type = RecordType.FINDS,
                                    recordId = find.id,
                                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs),
                                )
                            }
                          }
                        }
                    }
                    repeat(FIND_COLUMNS - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            val timedRows = rememberListRows(section.timed, key = { it.rowKey() })
            timedRows.forEach { timedRow -> key(timedRow.key) { ListRowMotion(timedRow) { record ->
                when (record) {
                    is TimedRecord.TrackRecord -> run {
                        val row: @Composable () -> Unit = {
                            BadgedRow(
                                type = RecordType.TRACKS,
                                recordId = record.track.id,
                                detailsName = trackTitle(record.track, is24HourClock()),
                                onClick = onOpenDetails?.let { open -> { open(RecordDetailsTarget.TrackDetails(record.track.id)) } },
                            ) {
                                TrackExportRow(track = record.track, waypoints = waypoints, getFullRecord = getFullRecord)
                            }
                        }
                        // Never a track that is still recording: it gets the plain row, no swipe.
                        if (onDeleteTrack != null && record.track.canBeDeleted) {
                            TwoStageSwipeRow(
                                testTag = swipeToDeleteTag(RecordType.TRACKS, record.track.id),
                                rowKey = RecordType.TRACKS to record.track.id,
                                group = swipeGroup,
                                onDelete = { onDeleteTrack(record.track.id) },
                                onEdit = null,
                            ) { row() }
                        } else {
                            row()
                        }
                    }
                    is TimedRecord.WaypointRecord -> run {
                        TwoStageSwipeRow(
                            testTag = swipeToDeleteTag(RecordType.WAYPOINTS, record.waypoint.id),
                            rowKey = RecordType.WAYPOINTS to record.waypoint.id,
                            group = swipeGroup,
                            onDelete = { onDeleteWaypoint(record.waypoint.id) },
                            onEdit = null,
                        ) {
                            BadgedRow(
                                type = RecordType.WAYPOINTS,
                                recordId = record.waypoint.id,
                                detailsName = record.waypoint.name,
                                onClick = onOpenDetails?.let { open -> { open(RecordDetailsTarget.WaypointDetails(record.waypoint.id)) } },
                            ) {
                                WaypointRow(waypoint = record.waypoint, onNavigate = onNavigateToWaypoint?.let { navigate -> { navigate(record.waypoint.id) } })
                            }
                        }
                    }
                    is TimedRecord.OfflineRegionRecord -> run {
                        TwoStageSwipeRow(
                            testTag = swipeToDeleteTag(RecordType.OFFLINE_MAPS, record.region.id.toString()),
                            rowKey = RecordType.OFFLINE_MAPS to record.region.id,
                            group = swipeGroup,
                            onDelete = { onDeleteOfflineRegion(record.region.id) },
                            onEdit = null,
                        ) {
                            BadgedRow(
                                type = RecordType.OFFLINE_MAPS,
                                recordId = record.region.id.toString(),
                                detailsName = record.region.name,
                                onClick = onOpenDetails?.let { open -> { open(RecordDetailsTarget.OfflineRegionDetails(record.region.id)) } },
                            ) {
                                OfflineRegionRow(
                                    region = record.region,
                                    isStale = isOfflineRegionStale(record.region.createdAtEpochMillis, now, availabilityUiState.offlineStaleThresholdDays),
                                    distanceUnit = distanceUnit,
                                    nowEpochMillis = now,
                                    onDownloadAgain = { onDownloadAgain(record.region.id) },
                                )
                            }
                        }
                    }
                }
            } } }
          }
        } } }
    }

}

/** A timed record's key in its day's list: its type and id, as the rows were keyed before motion Part 3. */
private fun TimedRecord.rowKey(): Any = when (this) {
    is TimedRecord.TrackRecord -> RecordType.TRACKS to track.id
    is TimedRecord.WaypointRecord -> RecordType.WAYPOINTS to waypoint.id
    is TimedRecord.OfflineRegionRecord -> RecordType.OFFLINE_MAPS to region.id
}

/**
 * A section's header, its kind and its count, "Finds · 9" (RECORD -868), with the kind named as its chip names it. The
 * count is the section's rows, which are the lists the chips count (regions and waypoints already leave out a pending
 * delete), so it matches the chip. Replaced the day header ("Sat, Oct 10, 2026 · 8 records") with -868. Motion Part 3,
 * item 3 (RECORD -651, "Numbers instant, words fade"): with no word that changes, the count changes at once.
 */
@Composable
private fun LogbookSectionHeader(section: LogbookSection) {
    Text(
        "${section.type.filter().chipLabel()} · ${section.recordCount}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm).testTag(logbookSectionTag(section.type)),
    )
}

/**
 * A timed record's own row with its type badge in front. With [onClick] (J5c), a tap anywhere on it,
 * badge included, opens the record's details sheet, announced as "Details for [detailsName]"; the
 * row's own buttons inside it keep their taps.
 */
@Composable
private fun BadgedRow(
    type: RecordType,
    recordId: String,
    detailsName: String,
    onClick: (() -> Unit)?,
    row: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(logbookRowTag(type, recordId))
            .then(if (onClick != null) Modifier.opensRecordDetails(detailsName, onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordTypeBadge(type = type, recordId = recordId)
        Box(modifier = Modifier.weight(1f)) { row() }
    }
}

/**
 * A record's type, as a small disc in [RecordTypeStyle]'s colours (plan J6): the type's container
 * behind its chip icon in the type's accent. Announced as the type name.
 */
@Composable
internal fun RecordTypeBadge(type: RecordType, recordId: String, modifier: Modifier = Modifier) {
    val colors = RecordTypeStyle.colors(type)
    Box(
        modifier = modifier
            .size(BADGE_SIZE)
            .clip(CircleShape)
            .background(colors.container)
            .semantics { contentDescription = type.displayName() }
            .testTag(recordTypeBadgeTag(type, recordId)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.filter().chipIcon(), contentDescription = null, tint = colors.accent, modifier = Modifier.size(BADGE_ICON_SIZE))
    }
}

private fun RecordType.filter(): RecordsSubTab = when (this) {
    RecordType.FINDS -> RecordsSubTab.FINDS
    RecordType.TRACKS -> RecordsSubTab.RECORDED_TRACKS
    RecordType.WAYPOINTS -> RecordsSubTab.WAYPOINTS
    RecordType.OFFLINE_MAPS -> RecordsSubTab.OFFLINE_MAPS
}

private fun RecordType.displayName(): String = when (this) {
    RecordType.FINDS -> "Find"
    RecordType.TRACKS -> "Track"
    RecordType.WAYPOINTS -> "Waypoint"
    RecordType.OFFLINE_MAPS -> "Offline map"
}

internal fun RecordType.tagName(): String = when (this) {
    RecordType.FINDS -> "finds"
    RecordType.TRACKS -> "tracks"
    RecordType.WAYPOINTS -> "waypoints"
    RecordType.OFFLINE_MAPS -> "offline-maps"
}

internal fun logbookSectionTag(type: RecordType): String = "records-logbook-section-${type.tagName()}"
internal fun logbookRowTag(type: RecordType, recordId: String): String = "records-logbook-row-${type.tagName()}-$recordId"
internal fun recordTypeBadgeTag(type: RecordType, recordId: String): String = "records-badge-${type.tagName()}-$recordId"

/** The Finds gallery's own column count on compact (`FindsGalleryScreen`'s `columns` default). */
private const val FIND_COLUMNS = 2
private val BADGE_SIZE = 28.dp
private val BADGE_ICON_SIZE = 16.dp
