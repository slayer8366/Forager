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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Records' **All** view — journal redesign J1, S4 (plan J4; the owner's answers in
 * `prompts/preserved/2026-09-27-17.md`): every record in one list, grouped by day, days newest first,
 * that day's finds first and then its timed records newest first ([buildRecordsLogbook] owns the
 * order).
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
 * **Tapping a find** calls [onOpenFind], which `RecordsTab` turns into "select the Finds chip and open
 * that find's report there", so Back goes report, then the Finds gallery, then All. Waypoint, track
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
    /** Part 2 follow-ups F1 item 5: a finished track row's swipe (pending delete with Undo); `null` leaves track rows without one. A recording track never has one. */
    onDeleteTrack: ((String) -> Unit)? = null,
    onDownloadAgain: (Long) -> Unit = {},
    onOpenFind: (String) -> Unit,
    modifier: Modifier = Modifier,
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
) {
    val days = buildRecordsLogbook(
        finds = finds.orEmpty(),
        tracks = tracks,
        waypoints = waypoints,
        offlineRegions = availabilityUiState.visibleOfflineRegions,
        zone = ZoneId.systemDefault(),
    )
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
        if (days.isEmpty()) {
            Text("No records yet.", style = MaterialTheme.typography.bodyMedium)
        }
        days.forEach { day ->
            LogbookDayHeader(day)
            day.finds.chunked(FIND_COLUMNS).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { find ->
                        Box(modifier = Modifier.weight(1f).testTag(logbookRowTag(RecordType.FINDS, find.id))) {
                            FindTileWithOptions(entry = find, onClick = { onOpenFind(find.id) }, onEdit = onEditFind, onDelete = onDeleteFind)
                            RecordTypeBadge(
                                type = RecordType.FINDS,
                                recordId = find.id,
                                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs),
                            )
                        }
                    }
                    repeat(FIND_COLUMNS - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            day.timed.forEach { record ->
                when (record) {
                    is TimedRecord.TrackRecord -> key(RecordType.TRACKS, record.track.id) {
                        val row: @Composable () -> Unit = {
                            BadgedRow(
                                type = RecordType.TRACKS,
                                recordId = record.track.id,
                                detailsName = trackTitle(record.track),
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
                    is TimedRecord.WaypointRecord -> key(RecordType.WAYPOINTS, record.waypoint.id) {
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
                                WaypointRow(waypoint = record.waypoint)
                            }
                        }
                    }
                    is TimedRecord.OfflineRegionRecord -> key(RecordType.OFFLINE_MAPS, record.region.id) {
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
            }
        }
    }

}

@Composable
private fun LogbookDayHeader(day: LogbookDay) {
    val count = day.recordCount
    Text(
        "${DAY_HEADER_FORMAT.format(day.date)} · $count ${if (count == 1) "record" else "records"}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = Spacing.sm)
            .testTag(logbookDayTag(day.date)),
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

internal fun logbookDayTag(date: LocalDate): String = "records-logbook-day-$date"
internal fun logbookRowTag(type: RecordType, recordId: String): String = "records-logbook-row-${type.tagName()}-$recordId"
internal fun recordTypeBadgeTag(type: RecordType, recordId: String): String = "records-badge-${type.tagName()}-$recordId"

/** The Finds gallery's own column count on compact (`FindsGalleryScreen`'s `columns` default). */
private const val FIND_COLUMNS = 2
private val BADGE_SIZE = 28.dp
private val BADGE_ICON_SIZE = 16.dp
private val DAY_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
