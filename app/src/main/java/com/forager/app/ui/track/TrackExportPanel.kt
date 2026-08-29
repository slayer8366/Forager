package com.forager.app.ui.track

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.forager.app.domain.model.Track
import com.forager.app.export.TrackGpxExporter
import com.forager.app.ui.theme.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Settings tab's "get a track out of the app" surface — mirrors
 * [com.forager.app.ui.crash.CrashLogPanel]'s list-then-share shape exactly, the closest existing
 * precedent in this app for "list files this app owns, tap one to hand it to another app."
 *
 * Field-test dispatch item 1: `GpxCodec` was fully implemented and tested but called from nowhere.
 * There is no dedicated track list/detail screen anywhere yet, and the dispatch is explicit not to
 * design one for this — Settings' existing crash-log pattern is the most convenient real surface
 * that already does "list this app's own records, tap to share one."
 */
@Composable
internal fun TrackExportPanel(tracks: List<Track>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        TrackExportHeader(onBack = onBack)
        TrackExportList(tracks = tracks, modifier = Modifier.weight(1f))
    }
}

/** Settings' own row into this panel — mirrors `CrashLogsEntryRow`'s shape exactly. */
@Composable
internal fun TrackExportEntryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Recorded Tracks", style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Mirrors `CrashLogHeader`'s shape exactly — see that composable's own call site for why. */
@Composable
private fun TrackExportHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
        Text("Recorded Tracks", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TrackExportList(tracks: List<Track>, modifier: Modifier = Modifier) {
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
        tracks.forEach { track -> TrackExportRow(track = track) }
    }
}

@Composable
private fun TrackExportRow(track: Track) {
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
            onClick = { scope.launch { exportAndShareTrack(context, track) } },
            modifier = Modifier.testTag("share-track-${track.id}"),
        ) {
            Icon(Icons.Filled.Share, contentDescription = "Share track recorded ${formatTrackTimestamp(track)}")
        }
    }
}

private fun trackSubtitle(track: Track): String {
    val pointCount = track.points.size
    val pointsText = if (pointCount == 1) "1 point" else "$pointCount points"
    return if (track.endedAtEpochMillis == null) "$pointsText · recording" else pointsText
}

private fun formatTrackTimestamp(track: Track): String =
    DISPLAY_FORMAT.format(Instant.ofEpochMilli(track.startedAtEpochMillis).atZone(ZoneId.systemDefault()))

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")

/** Writes [track] to a GPX file (disk I/O off the composing thread) and hands it to the share sheet. */
private suspend fun exportAndShareTrack(context: Context, track: Track) {
    val file = withContext(Dispatchers.IO) { TrackGpxExporter.forContext(context).write(track) }
    context.startActivity(Intent.createChooser(shareGpxIntent(context, file), "Share track"))
}

/**
 * Builds the `ACTION_SEND` intent for [file] — split out from [exportAndShareTrack] so the intent's
 * own shape (mime type, URI, flags) is testable without actually driving a share sheet, the same
 * split `com.forager.app.ui.availability.directionsIntent`/`launchDirections` uses. Uses the same
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
