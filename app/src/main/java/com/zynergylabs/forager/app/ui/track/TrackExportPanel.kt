package com.zynergylabs.forager.app.ui.track

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
    if (tracks.isEmpty()) {
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        tracks.forEach { track -> TrackExportRow(track = track, waypoints = waypoints, getFullRecord = getFullRecord) }
    }
}

@Composable
private fun TrackExportRow(
    track: Track,
    waypoints: List<Waypoint>,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(formatTrackTimestamp(track), style = MaterialTheme.typography.bodyLarge)
            Text(trackSubtitle(track), style = MaterialTheme.typography.bodySmall)
        }
        // testTag, not contentDescription alone, is what a test (and this dispatch's own testing
        // note) needs to find this by: a contentDescription proves TalkBack can reach it, not that
        // a sighted tester can find it visually — see this dispatch's item 2 for the bug that shape
        // of assertion hid for an entire release.
        IconButton(
            onClick = {
                val trackWaypoints = waypoints.filter { it.trackId == track.id }
                scope.launch { exportAndShareTrack(context, track, trackWaypoints, getFullRecord) }
            },
            modifier = Modifier.testTag("share-track-${track.id}"),
        ) {
            Icon(Icons.Filled.Share, contentDescription = "Share track recorded ${formatTrackTimestamp(track)}")
        }
    }
}

/**
 * "N points", plus — only when the read seam excluded most or all of the track as network-provider
 * fixes (timestamp-filter dispatch, Item 3) — what it left out, so a short or empty track is never a
 * silent one. The ordinary track, including one the rule quietly cleaned, reads exactly as before.
 */
internal fun trackSubtitle(track: Track): String {
    val pointCount = track.points.size
    val pointsText = if (pointCount == 1) "1 point" else "$pointCount points"
    val note = networkFixExclusionNote(track)
    val body = when {
        note != null && pointCount == 0 -> note
        note != null -> "$pointsText · $note"
        else -> pointsText
    }
    return if (track.endedAtEpochMillis == null) "$body · recording" else body
}

private fun formatTrackTimestamp(track: Track): String =
    DISPLAY_FORMAT.format(Instant.ofEpochMilli(track.startedAtEpochMillis).atZone(ZoneId.systemDefault()))

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")

/**
 * Writes [track] to a GPX file (disk I/O off the composing thread) and hands it to the share
 * sheet. [waypoints] must already be filtered to this track. [getFullRecord]'s failure is logged,
 * not swallowed (CLAUDE.md: a failure is reported, never silent) — but doesn't block the share
 * itself: the filtered `<trkseg>` the app already displayed is still worth getting out, even
 * without the raw `<extensions>` record this dispatch adds.
 */
private suspend fun exportAndShareTrack(
    context: Context,
    track: Track,
    waypoints: List<Waypoint>,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
) {
    val fullRecord = getFullRecord(track.id)
        .onFailure { error -> Log.w("TrackExportPanel", "Couldn't load track ${track.id}'s full record; exporting without it.", error) }
        .getOrDefault(emptyList())
    val file = withContext(Dispatchers.IO) {
        TrackGpxExporter.forContext(context).write(track, fullRecord = fullRecord, waypoints = waypoints)
    }
    context.startActivity(Intent.createChooser(shareGpxIntent(context, file), "Share track"))
}

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
