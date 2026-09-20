package com.zynergylabs.forager.app.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zynergylabs.forager.app.diagnostics.DiagnosticsLog
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Settings tab's **debug-only** diagnostics surface, beside Crash Logs and shaped like it: a
 * drill-in panel, a header whose whole row is the back control, plain rows, a share icon per file
 * that hands the file to another app through the app's own `FileProvider`. Built for the PR #102
 * device check on a phone with no ADB and no logcat, where three checklist steps had no instrument:
 * StrictMode violations (step 1), the startup sweep's count (step 4) and the EXIF content of a
 * persisted photo (step 5, which needs the file off the device). It lists `filesDir/photos` and
 * `filesDir/captures` with name, size and last-modified time and a count per directory, shows and
 * shares the [DiagnosticsLog], and shares any listed file.
 *
 * ## How it is debug-only, and why that gate
 *
 * This file lives in `src/debug`, and `src/release` carries a twin with the same two signatures
 * ([DiagnosticsEntryRow], [DiagnosticsPanel]) that compose nothing. `AvailabilityScreen` calls both
 * unconditionally; in a release build the entry row draws nothing, so the panel is unreachable,
 * and its code is not merely unreachable but absent from the APK. A `BuildConfig.DEBUG` branch
 * would not give that: the release build type has `isMinifyEnabled = false`, so nothing strips
 * dead code, and a branch would ship this panel, its `photos/` FileProvider path and the
 * `diagnostics/` one in every release, reachable to anyone who patched the flag. The source-set
 * split is the gate that makes "a release build does not contain the panel" a fact about the
 * artifact rather than about a boolean. The same split gives the debug build its own
 * `res/xml/file_paths.xml`, which is where `photos/` becomes shareable — the release provider
 * config is untouched and still never exposes it, as its own comment promises.
 *
 * ## Read-only, and off the thread it is measuring
 *
 * Nothing here deletes, clears, renames or writes to the directories it lists; the only write
 * anywhere is the log's own, elsewhere. The listing and the log read happen on `Dispatchers.IO`,
 * and so does `FileProvider.getUriForFile` (it canonicalises the path, a disk read). That is not
 * fastidiousness: the StrictMode policy this same dispatch installs is on the main thread, so a
 * panel that listed a directory on main would write its own violations into the log it exists to
 * show, and step 1's reading would be about the instrument. `CrashLogPanel` does call `list()` on
 * main, which predates the policy and will now appear in the log when Crash Logs is opened; that
 * is reported, not changed here.
 */
@Composable
internal fun DiagnosticsPanel(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DiagnosticsPanel(
        photosDir = File(context.filesDir, PHOTOS_DIRECTORY),
        capturesDir = File(context.filesDir, CAPTURES_DIRECTORY),
        log = DiagnosticsLog.forContext(context),
        onBack = onBack,
        modifier = modifier,
    )
}

/** The Settings panel's row into this panel. Draws its own divider above so the release twin, which draws nothing, leaves no orphaned rule behind. */
@Composable
internal fun DiagnosticsEntryRow(onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(DIAGNOSTICS_ENTRY_LABEL, style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** The testable form: directories and log handed in, same reason `CrashLogPanel` takes its file list. */
@Composable
internal fun DiagnosticsPanel(
    photosDir: File,
    capturesDir: File,
    log: DiagnosticsLog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewingLog by remember { mutableStateOf(false) }
    if (viewingLog) {
        DiagnosticsLogDetail(log = log, onBack = { viewingLog = false }, modifier = modifier)
        return
    }

    var listing by remember(photosDir, capturesDir, log) { mutableStateOf<DiagnosticsListing?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(photosDir, capturesDir, log) {
        listing = withContext(Dispatchers.IO) { readListing(photosDir, capturesDir, log) }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val share: (File, String) -> Unit = { file, mimeType ->
        shareFile(context, scope, file, mimeType, onError = { shareError = it })
    }

    Column(modifier = modifier.fillMaxWidth()) {
        DiagnosticsHeader(title = DIAGNOSTICS_TITLE, backDescription = "Back to Settings", onBack = onBack)
        val current = listing
        if (current == null) {
            Text(READING_LABEL, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.lg))
            return
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            shareError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(DIAGNOSTICS_SHARE_ERROR_TAG))
            }
            LogRow(sizeBytes = current.logBytes, onView = { viewingLog = true }, onShare = { share(log.file, "text/plain") })
            HorizontalDivider()
            DirectorySection(title = "$PHOTOS_DIRECTORY/", files = current.photos, onShare = { share(it, "image/jpeg") })
            HorizontalDivider()
            DirectorySection(title = "$CAPTURES_DIRECTORY/", files = current.captures, onShare = { share(it, "image/jpeg") })
        }
    }
}

@Composable
private fun LogRow(sizeBytes: Long, onView: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onView)
            .testTag(DIAGNOSTICS_LOG_ROW_TAG),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(LOG_ROW_LABEL, style = MaterialTheme.typography.bodyLarge)
            Text(formatBytes(sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onShare, modifier = Modifier.testTag(DIAGNOSTICS_LOG_SHARE_TAG)) {
            Icon(Icons.Filled.Share, contentDescription = "Share diagnostics log")
        }
    }
}

@Composable
private fun DirectorySection(title: String, files: List<ListedFile>, onShare: (File) -> Unit) {
    Text(directoryHeading(title, files.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
    if (files.isEmpty()) {
        Text(NO_FILES_LABEL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    files.forEach { listed -> FileRow(listed = listed, onShare = { onShare(listed.file) }) }
}

@Composable
private fun FileRow(listed: ListedFile, onShare: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(listed.file.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Text(
                "${formatBytes(listed.sizeBytes)} · ${formatModified(listed.modifiedEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.testTag(diagnosticsShareTag(listed.file))) {
            Icon(Icons.Filled.Share, contentDescription = "Share ${listed.file.name}")
        }
    }
}

/** Mirrors `CrashLogDetail`: the text is read on IO in a `LaunchedEffect`, never during composition. */
@Composable
private fun DiagnosticsLogDetail(log: DiagnosticsLog, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var content by remember(log) { mutableStateOf<String?>(null) }
    LaunchedEffect(log) {
        content = withContext(Dispatchers.IO) { log.read() }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        DiagnosticsHeader(title = LOG_ROW_LABEL, backDescription = "Back to Diagnostics", onBack = onBack)
        Text(
            when (val text = content) {
                null -> READING_LABEL
                "" -> EMPTY_LOG_LABEL
                else -> text
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg)
                .testTag(DIAGNOSTICS_LOG_TEXT_TAG),
        )
    }
}

/** Same shape as `CrashLogHeader`: the whole row is the back control. */
@Composable
private fun DiagnosticsHeader(title: String, backDescription: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription)
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** One directory entry as listed: the file plus the two numbers read once, on IO, so composition reads nothing from disk. */
internal data class ListedFile(val file: File, val sizeBytes: Long, val modifiedEpochMillis: Long)

internal data class DiagnosticsListing(val photos: List<ListedFile>, val captures: List<ListedFile>, val logBytes: Long)

/** Regular files only, newest first. A missing directory lists as empty: before the first photo, `photos/` does not exist yet. */
internal fun readListing(photosDir: File, capturesDir: File, log: DiagnosticsLog): DiagnosticsListing =
    DiagnosticsListing(photos = listFiles(photosDir), captures = listFiles(capturesDir), logBytes = log.sizeBytes())

private fun listFiles(directory: File): List<ListedFile> =
    directory.listFiles().orEmpty()
        .filter { it.isFile }
        .map { ListedFile(file = it, sizeBytes = it.length(), modifiedEpochMillis = it.lastModified()) }
        .sortedByDescending { it.modifiedEpochMillis }

/**
 * The URI is minted on IO (see the class doc), then the chooser starts on main. A provider that
 * cannot serve the file — a path outside `file_paths.xml`'s roots — throws `IllegalArgumentException`,
 * which is shown on the panel and logged rather than dropped.
 */
private fun shareFile(context: Context, scope: CoroutineScope, file: File, mimeType: String, onError: (String) -> Unit) {
    scope.launch {
        val uri = withContext(Dispatchers.IO) {
            runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
        }
        uri.fold(
            onSuccess = { contentUri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
            },
            onFailure = { error ->
                Log.w(TAG, "Couldn't share '${file.name}'.", error)
                onError("Couldn't share ${file.name}: ${error.message ?: error.javaClass.simpleName}")
            },
        )
    }
}

internal fun directoryHeading(title: String, count: Int): String =
    "$title · " + when (count) {
        1 -> "1 file"
        else -> "$count files"
    }

/** "512 B", "4.2 KB", "3.1 MB": one decimal above bytes, enough to tell a scrubbed 3.1 MB photo from a 6 MB one at a glance. */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** With seconds: step 4 compares a listing against a relaunch a few seconds earlier. */
internal fun formatModified(epochMillis: Long): String =
    MODIFIED_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val MODIFIED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm:ss a")

/**
 * The two directory names, asserted rather than assumed: `DiagnosticsPanelTest` persists a photo
 * through the real `FilePhotoStore` and issues a capture through the real `CameraCaptureFiles`,
 * then checks both appear here, so these strings cannot drift from those classes' private
 * constants without a test saying so.
 */
internal const val PHOTOS_DIRECTORY = "photos"
internal const val CAPTURES_DIRECTORY = "captures"

internal const val DIAGNOSTICS_ENTRY_LABEL = "Diagnostics (debug build)"
internal const val DIAGNOSTICS_TITLE = "Diagnostics"
internal const val LOG_ROW_LABEL = "Diagnostics log"
internal const val NO_FILES_LABEL = "No files."
internal const val READING_LABEL = "Reading…"
internal const val EMPTY_LOG_LABEL = "The log is empty."
internal const val DIAGNOSTICS_LOG_ROW_TAG = "diagnostics-log-row"
internal const val DIAGNOSTICS_LOG_SHARE_TAG = "diagnostics-log-share"
internal const val DIAGNOSTICS_LOG_TEXT_TAG = "diagnostics-log-text"
internal const val DIAGNOSTICS_SHARE_ERROR_TAG = "diagnostics-share-error"
internal fun diagnosticsShareTag(file: File): String = "diagnostics-share:${file.name}"

private const val TAG = "DiagnosticsPanel"
