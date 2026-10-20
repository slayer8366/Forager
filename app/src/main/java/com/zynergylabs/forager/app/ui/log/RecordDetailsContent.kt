package com.zynergylabs.forager.app.ui.log

// Moved unchanged from RecordDetailsSheet.kt (dispatch 2026-10-19-07, UI split Phase 1, PR 4).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.isOfflineRegionStale
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.domain.networkFixExclusionNote
import com.zynergylabs.forager.app.ui.availability.decimalDegreesLabel
import com.zynergylabs.forager.app.ui.availability.launchDirections
import com.zynergylabs.forager.app.ui.availability.offlineRegionSizeLabel
import com.zynergylabs.forager.app.ui.availability.offlineRegionZoomNote
import com.zynergylabs.forager.app.ui.availability.relativeTimeLabel
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.track.canBeDeleted
import com.zynergylabs.forager.app.ui.track.shareTrackGpx
import com.zynergylabs.forager.app.ui.track.trackTitle
import com.zynergylabs.forager.app.ui.track.IMPORTED_LABEL
import com.zynergylabs.forager.app.ui.track.NO_TIMES_IN_FILE
import kotlinx.coroutines.launch
import com.zynergylabs.forager.app.ui.format.displayDateTime
import com.zynergylabs.forager.app.ui.format.is24HourClock

@Composable
internal fun WaypointDetails(waypoint: Waypoint, tracks: List<Track>, referenceCounts: Map<String, Int>, onNavigate: ((String) -> Unit)?) {
    val context = LocalContext.current
    val is24Hour = is24HourClock()
    val location = LatLng(waypoint.lat, waypoint.lng)
    DetailsTitle(waypoint.name)
    when (val mgrs = MgrsConverter.convert(location)) {
        is MgrsCoordinate.Grid -> DetailField(FIELD_MGRS, "MGRS", mgrs.value)
        // No line rather than a wrong one, as the row does (MgrsCoordinate's doc comment).
        is MgrsCoordinate.Unsupported -> Unit
    }
    DetailField(FIELD_COORDINATES, "Coordinates", decimalDegreesLabel(waypoint.lat, waypoint.lng))
    DetailField(FIELD_CREATED, "Created", displayDateTime(waypoint.createdAtEpochMillis, is24Hour))
    waypoint.trackId?.let { trackId ->
        // The track list is loaded at the ViewModel's init; a link to a track not in it is said
        // plainly rather than guessed at.
        val parent = tracks.firstOrNull { it.id == trackId }
        DetailField(FIELD_TRACK, "Track", parent?.let { trackTitle(it, is24Hour) } ?: "Not loaded")
    }
    referenceCounts[waypoint.id]?.let { count -> DetailField(FIELD_USED_IN, "Used in", journalEntryCountLabel(count)) }
    DetailsActions {
        // Dispatch -502: the app's own navigation first, then the hand-off to a maps app.
        onNavigate?.let { navigate ->
            OutlinedButton(
                onClick = { navigate(waypoint.id) },
                modifier = Modifier.testTag(RECORD_DETAILS_NAVIGATE_TAG),
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Navigate", modifier = Modifier.padding(start = Spacing.sm))
            }
        }
        OutlinedButton(
            onClick = { launchDirections(context, waypoint.name, location) },
            modifier = Modifier.testTag(RECORD_DETAILS_DIRECTIONS_TAG),
        ) {
            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Directions", modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

@Composable
internal fun TrackDetails(
    track: Track,
    waypoints: List<Waypoint>,
    distanceUnit: DistanceUnit,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    onDeleteTrack: ((String) -> Unit)?,
    onOpenWaypoint: ((String) -> Unit)?,
    overMap: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The same derivation the entry editor's candidate rows use for a live track
    // (CartographyEntryEditScreen's TracksSection), from the points already in memory.
    val stats = ComputeTrackStatisticsUseCase()(track.points)
    // Dispatch 2026-09-28-677 (data part B): times follow the phone's 12- or 24-hour setting on this sheet; data part D
    // (RECORD -702) moved the formatter to ui/format and the Records rows onto the same setting, so the two titles agree.
    val is24Hour = is24HourClock()
    val stamp = { epochMillis: Long -> displayDateTime(epochMillis, is24Hour) }
    // Plan T16: an imported track wears "Imported" beside its title, as a stale region wears "Stale".
    DetailsTitle(trackTitle(track, is24Hour), label = IMPORTED_LABEL.takeIf { track.importedAtEpochMillis != null })
    // Dispatch -616 as amended by -618: the drawing carries start, end and dropped-waypoint dots, and the
    // list of those waypoints sits under it.
    WalkThumbnail(
        track = track,
        waypoints = waypoints,
        modifier = Modifier.size(TRACK_THUMBNAIL_SIZE).testTag(RECORD_DETAILS_THUMBNAIL_TAG),
    )
    // Kept directly under the drawing, where -618 put it; dispatch -677 adds below it, and moves nothing above it.
    WalkWaypointsSection(waypointsDroppedOn(track, waypoints), onOpenWaypoint, stamp)
    // Dispatch 2026-09-28-677 (data part B; the owner, RECORD -656: "Tiles + profile, raw tucked away (Recommended)"): the
    // figures as labelled tiles, then the height profile, both data part A's own (LabelledTiles, EntryHeightProfile), so the
    // track sheet and the entry report read alike. Over a map the tiles are outlined, not filled: the sheet's container
    // already carries the map chrome's alpha, and a fill on top would composite past it.
    LabelledTiles(
        tiles = trackSheetTiles(track, stats, distanceUnit),
        tagOf = ::trackTileTag,
        overMap = overMap,
    )
    EntryHeightProfile(profile = trackHeightProfile(track), distanceUnit = distanceUnit)
    // Plan T16, "Import, show "No times"" (-636): a file with no times has none to show; its stored times are
    // only an ordering, so each place a time or a duration would be says so instead.
    val noTimes = track.importedWithoutTimes
    DetailField(FIELD_STARTED, "Started", if (noTimes) NO_TIMES_IN_FILE else stamp(track.startedAtEpochMillis))
    DetailField(FIELD_ENDED, "Ended", if (noTimes) NO_TIMES_IN_FILE else track.endedAtEpochMillis?.let(stamp) ?: "Still recording")
    track.importedAtEpochMillis?.let { DetailField(FIELD_IMPORTED, IMPORTED_LABEL, stamp(it)) }
    networkFixExclusionNote(track)?.let { note ->
        Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(RECORD_DETAILS_NOTE_TAG))
    }
    // The raw figures, folded (the owner: "Points and other raw figures go in a "Details" fold").
    DetailsFold {
        DetailField(FIELD_POINTS, "Points", track.points.size.toString())
        DetailField(FIELD_HEIGHTS, HEIGHTS_FIELD_LABEL, heightsRecordedLabel(stats.pointsWithAltitude, stats.totalPoints))
    }
    DetailsActions {
        OutlinedButton(
            onClick = { scope.launch { shareTrackGpx(context, track, waypoints, getFullRecord) } },
            modifier = Modifier.testTag(RECORD_DETAILS_SHARE_TAG),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Share", modifier = Modifier.padding(start = Spacing.sm))
        }
        // Part 2 follow-ups F1 item 5 (owner "Option A"): the same pending delete and Undo as the row's swipe,
        // labelled "Delete" as the swipe's revealed button and its accessibility action are (DELETE_ACTION_LABEL,
        // SwipeToDelete.kt). Never for a track that is still recording. The sheet or pane closes by itself when the
        // track leaves the list it reads (the pending track is hidden at once).
        if (onDeleteTrack != null && track.canBeDeleted) {
            OutlinedButton(
                onClick = { onDeleteTrack(track.id) },
                modifier = Modifier.testTag(RECORD_DETAILS_DELETE_TAG),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(DELETE_ACTION_LABEL, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

/**
 * "Waypoints on this track" (dispatch -616 as amended by -618; the owner chose the heading): the
 * waypoints dropped on the walk, oldest first, each row its name and when it was dropped. A tap anywhere on
 * a row opens that waypoint's details in this sheet's place ([onOpen]), and Back returns to the walk. A
 * walk with none shows no section at all, not an empty heading. The rows have no fill of their own: over
 * a map the sheet's container already carries the map chrome's alpha.
 */
@Composable
private fun WalkWaypointsSection(dropped: List<Waypoint>, onOpen: ((String) -> Unit)?, stamp: (Long) -> String) {
    if (dropped.isEmpty()) return
    Text(
        WALK_WAYPOINTS_HEADING,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.sm).testTag(RECORD_DETAILS_WALK_WAYPOINTS_HEADING_TAG),
    )
    dropped.forEach { waypoint ->
        val tap = onOpen?.let { open -> Modifier.opensRecordDetails(waypoint.name) { open(waypoint.id) } }
            ?: Modifier.semantics(mergeDescendants = true) {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WALK_WAYPOINT_ROW_MIN_HEIGHT)
                .then(tap)
                .testTag(recordDetailsWalkWaypointTag(waypoint.id)),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(waypoint.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stamp(waypoint.createdAtEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun OfflineRegionDetails(region: OfflineRegionSummary, distanceUnit: DistanceUnit, nowEpochMillis: Long, staleThresholdDays: Int) {
    val is24Hour = is24HourClock()
    val stale = isOfflineRegionStale(region.createdAtEpochMillis, nowEpochMillis, staleThresholdDays)
    DetailsTitle(region.name, stale = stale)
    DetailField(FIELD_RADIUS, "Radius", formatDistanceKm(region.region.radiusKm, distanceUnit))
    DetailField(FIELD_CENTRE, "Centre", decimalDegreesLabel(region.region.lat, region.region.lng))
    DetailField(FIELD_TILES, "Tiles", region.tileCount.toString())
    DetailField(FIELD_SIZE, "Size", offlineRegionSizeLabel(region))
    DetailField(
        FIELD_DOWNLOADED,
        "Downloaded",
        "${displayDateTime(region.createdAtEpochMillis, is24Hour)} (${relativeTimeLabel(region.createdAtEpochMillis, nowEpochMillis)})",
    )
    Text(offlineRegionZoomNote(region), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(RECORD_DETAILS_ZOOM_TAG))
}
