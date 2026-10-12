package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.motion.ListRowMotion
import com.zynergylabs.forager.app.ui.motion.rememberListRows
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.networkFixExclusionNote
import com.zynergylabs.forager.app.export.TrackGpxExporter
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.TrackThumbnail
import com.zynergylabs.forager.app.ui.log.TwoStageSwipeRow
import com.zynergylabs.forager.app.ui.log.rememberSwipeRevealGroup
import com.zynergylabs.forager.app.ui.log.swipeRevealTouchWatcher
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import com.zynergylabs.forager.app.ui.log.opensRecordDetails
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zynergylabs.forager.app.ui.format.displayDateTime
import com.zynergylabs.forager.app.ui.format.is24HourClock

/**
 * The Journal Records tab's "get a track out of the app" surface — mirrors
 * [com.zynergylabs.forager.app.ui.crash.CrashLogPanel]'s list-then-share shape exactly, the closest existing
 * precedent in this app for "list files this app owns, tap one to hand it to another app."
 *
 * Field-test dispatch item 1: `GpxCodec` was fully implemented and tested but called from nowhere.
 * There is no dedicated track list/detail screen anywhere yet, and the dispatch is explicit not to
 * design one for this — the crash-log pattern is the most convenient real surface that already
 * does "list this app's own records, tap to share one."
 *
 * `internal`, no header: Journal restructure Stage 1 moved this into `RecordsTab` (`ui/log/`) as a
 * flat sub-tab, not a drill-in submenu — see that composable's own doc comment. A header with its
 * own back arrow made sense inside Settings' drill-in shape; a `SecondaryTabRow` sub-tab is left by
 * tapping another tab, not by a back affordance embedded in the content.
 */
@Composable
internal fun TrackExportList(
    tracks: List<Track>,
    /** All saved waypoints — filtered per track (by [Waypoint.trackId]) at the row that needs it. Defaults empty so a caller not exercising export (most existing screen tests) is unaffected. */
    waypoints: List<Waypoint> = emptyList(),
    /**
     * GPX full-record export dispatch: the unfiltered read for the file's `<extensions>` block —
     * see [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.getFullRecord]. Defaults to reporting
     * an empty record, same reasoning as [waypoints]; `MainActivity` wires the real one.
     */
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    modifier: Modifier = Modifier,
    /**
     * Journal redesign J5c: a tap on a row opens that track's details sheet (`RecordDetailsSheet`),
     * given the track's id. `null`, the default, leaves the rows without a tap, as before.
     */
    onOpenTrackDetails: ((String) -> Unit)? = null,
    /**
     * Part 2 follow-ups F1 item 5 (owner "Option A"): a swipe on a **finished** track's row (a short swipe
     * reveals Delete, a full swipe deletes, or the row's "Delete" accessibility action) asks for a pending
     * delete with Undo, as a waypoint's row does. A track that is still recording has no swipe, and no Delete
     * anywhere. `null`, the default, leaves every row as it was.
     */
    onDeleteTrack: ((String) -> Unit)? = null,
    /** Set when a committed delete failed and the track is back: shown above the list, as the waypoints list shows its own. */
    errorMessage: String? = null,
) {
    // One open row at a time, and a touch elsewhere on the list closes it (J4b L6).
    val swipeGroup = rememberSwipeRevealGroup()
    // Motion Part 3, item 2 (RECORD -651, Lists: "Slide and close up"; motion/ListMotion.kt): a deleted track's row fades and
    // shrinks while the rows below close up, and Undo brings it back the way it went. The last one leaves before the empty text.
    val rows = rememberListRows(tracks, key = { it.id })
    if (rows.isEmpty()) {
        Text(
            "No recorded tracks yet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .swipeRevealTouchWatcher(swipeGroup)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        rows.forEach { listRow ->
            key(listRow.key) { ListRowMotion(listRow) { track ->
                val row: @Composable () -> Unit = {
                    TrackExportRow(
                        track = track,
                        waypoints = waypoints,
                        getFullRecord = getFullRecord,
                        onClick = onOpenTrackDetails?.let { open -> { open(track.id) } },
                    )
                }
                if (onDeleteTrack != null && track.canBeDeleted) {
                    TwoStageSwipeRow(
                        testTag = swipeToDeleteTag(RecordType.TRACKS, track.id),
                        rowKey = track.id,
                        group = swipeGroup,
                        onDelete = { onDeleteTrack(track.id) },
                        onEdit = null,
                    ) { row() }
                } else {
                    row()
                }
            } }
        }
    }
}

@Composable
internal fun TrackExportRow(
    track: Track,
    waypoints: List<Waypoint>,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    /**
     * Journal redesign J5c: what a tap on the row opens (the track's details sheet), or `null` for no
     * row tap. The All logbook passes `null` and puts the tap on its badged row instead, so the badge
     * takes it too. The Share button keeps its own tap either way.
     */
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val is24Hour = is24HourClock()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(trackExportRowTag(track.id))
            .then(if (onClick != null) Modifier.opensRecordDetails(trackTitle(track, is24Hour), onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Journal redesign J3, C3 (owner ruling "Rows and Entries cards"): the track's own points,
        // already in memory on every row, drawn as a small thumbnail in front of its text. Fewer than
        // two points draws nothing (TrackThumbnail's rule).
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackThumbnail(
                trackId = track.id,
                points = track.points,
                modifier = Modifier.size(TRACK_ROW_THUMBNAIL_SIZE).testTag("track-thumbnail-${track.id}"),
            )
            Column {
                // Plan T16: the track's name where it has one (an imported track always does), its start
                // time otherwise, as the details sheet's title already reads (trackTitle). A recorded walk has
                // no name, so its row reads as before.
                Text(trackTitle(track, is24Hour), style = MaterialTheme.typography.bodyLarge)
                Text(trackSubtitle(track, is24Hour), style = MaterialTheme.typography.bodySmall)
            }
        }
        // testTag, not contentDescription alone, is what a test (and this dispatch's own testing
        // note) needs to find this by: a contentDescription proves TalkBack can reach it, not that
        // a sighted tester can find it visually — see this dispatch's item 2 for the bug that shape
        // of assertion hid for an entire release.
        BouncingIconButton(
            onClick = { scope.launch { shareTrackGpx(context, track, waypoints, getFullRecord) } },
            modifier = Modifier.testTag("share-track-${track.id}"),
        ) {
            Icon(Icons.Filled.Share, contentDescription = "Share track recorded ${displayDateTime(track.startedAtEpochMillis, is24Hour)}")
        }
    }
}

/**
 * "N points", plus — only when the read seam excluded most or all of the track as network-provider
 * fixes (timestamp-filter dispatch, Item 3) — what it left out, so a short or empty track is never a
 * silent one. The ordinary track, including one the rule quietly cleaned, reads exactly as before.
 */
internal fun trackSubtitle(track: Track, is24HourClock: Boolean): String {
    val pointCount = track.points.size
    val pointsText = if (pointCount == 1) "1 point" else "$pointCount points"
    val note = networkFixExclusionNote(track)
    val body = when {
        note != null && pointCount == 0 -> note
        note != null -> "$pointsText · $note"
        else -> pointsText
    }
    // Plan T16: an imported track says so first ("Imported" label, the owner's answer in -636), then the
    // date it carries, since its title is its name rather than its time.
    if (track.importedAtEpochMillis != null) return "$IMPORTED_LABEL · ${displayDateTime(track.startedAtEpochMillis, is24HourClock)} · $body"
    return if (track.endedAtEpochMillis == null) "$body · recording" else body
}

/** Plan T16: the label an imported track wears in Records' rows and its details. */
internal const val IMPORTED_LABEL = "Imported"

/** Plan T16, "Import, show "No times"" (-636): what stands where a duration or a time would be for a track whose file had none. */
internal const val NO_TIMES_IN_FILE = "No times in file"


/** Whether Delete may be offered for this track: never while it is still recording (its end time is null). Part 2 follow-ups F1 item 5. */
internal val Track.canBeDeleted: Boolean get() = endedAtEpochMillis != null

/**
 * A track's name, or its start time when it has none: what the row shows as its title (J5c's sheet title too). The start time
 * reads "Oct 7, 2026, 2:14 PM", or "Oct 7, 2026, 14:14" on a phone set to 24-hour time (data part D, RECORD -702: the row and
 * the sheet agreed only on a 12-hour phone until then, data part B's report item 8).
 */
internal fun trackTitle(track: Track, is24HourClock: Boolean): String =
    track.name ?: displayDateTime(track.startedAtEpochMillis, is24HourClock)

/** The Tracks chip's row for [trackId] (J5c: the details tap is tested at several points across it). */
internal fun trackExportRowTag(trackId: String): String = "track-row-$trackId"

private val TRACK_ROW_THUMBNAIL_SIZE = 40.dp


/**
 * Writes [track] to a GPX file (disk I/O off the composing thread) and hands it to the share
 * sheet. [waypoints] must already be filtered to this track. [getFullRecord]'s failure is logged,
 * not swallowed (CLAUDE.md: a failure is reported, never silent) — but doesn't block the share
 * itself: the filtered `<trkseg>` the app already displayed is still worth getting out, even
 * without the raw `<extensions>` record this dispatch adds.
 */
/**
 * The row's Share action, also the J5c details sheet's: [allWaypoints] filtered to [track] (by
 * [Waypoint.trackId]), then [exportAndShareTrack]. One function so the two buttons cannot drift.
 */
internal suspend fun shareTrackGpx(
    context: Context,
    track: Track,
    allWaypoints: List<Waypoint>,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
) = exportAndShareTrack(context, track, allWaypoints.filter { it.trackId == track.id }, getFullRecord)

private suspend fun exportAndShareTrack(
    context: Context,
    track: Track,
    waypoints: List<Waypoint>,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
) {
    val fullRecordRead = getFullRecord(track.id)
        .onFailure { error -> Log.w("TrackExportPanel", "Couldn't load track ${track.id}'s full record; exporting without it.", error) }
    val fullRecord = fullRecordRead.getOrDefault(emptyList())
    val file = withContext(Dispatchers.IO) {
        TrackGpxExporter.forContext(context).write(track, fullRecord = fullRecord, waypoints = waypoints)
    }
    // Dispatch 2026-09-28-658 (R9): an export without the full record is partial, and the person
    // sharing it is told so before the share sheet opens, not left to believe it is complete.
    // On the main thread explicitly: under the compose test harness this coroutine resumed from the
    // IO hop on a thread with no Looper, and a Toast needs one (seen in TrackExportPartialTest).
    if (fullRecordRead.isFailure) {
        withContext(Dispatchers.Main) { Toast.makeText(context, PARTIAL_GPX_EXPORT_MESSAGE, Toast.LENGTH_LONG).show() }
    }
    context.startActivity(Intent.createChooser(shareGpxIntent(context, file), "Share track"))
}

/**
 * Shown before the share sheet when the track's full GPS record could not be read, so the file holds
 * the track as the app shows it but not the raw record (dispatch 2026-09-28-658, R9). The owner's wording
 * (RECORD -660, Amendment 1 to -658).
 */
internal const val PARTIAL_GPX_EXPORT_MESSAGE =
    "Couldn't add the full GPS record. This file has the track only. Share again to retry."

/**
 * Builds the `ACTION_SEND` intent for [file] — split out from [exportAndShareTrack] so the intent's
 * own shape (mime type, URI, flags) is testable without actually driving a share sheet, the same
 * split `com.zynergylabs.forager.app.ui.availability.directionsIntent`/`launchDirections` uses. Uses the same
 * `${applicationId}.fileprovider` authority the crash-log share action does — see
 * `res/xml/file_paths.xml`'s `tracks` cache-path entry for why a different path, not a different
 * authority, is what's new here.
 */
internal fun shareGpxIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
