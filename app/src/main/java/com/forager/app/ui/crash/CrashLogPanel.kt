package com.forager.app.ui.crash

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.forager.app.crash.CrashFileStore
import com.forager.app.ui.theme.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Settings tab's crash-log diagnostic surface — a read-only list of what
 * `com.forager.app.crash.CrashUncaughtExceptionHandler` has captured, reached the same way
 * `OfflineMaps` is from `SettingsContent` (see that composable's own `CrashLogsEntryRow` call
 * site). Kept in its own package rather than folded into `AvailabilityScreen.kt`, mirroring
 * `com.forager.app.ui.log.LogPanel`'s own doc comment on why: this needs none of
 * `AvailabilityScreen`'s private state, only the file list handed in.
 *
 * Deliberately plain — a diagnostic surface, not a designed feature: list, tap to view the raw
 * trace, or share it. Nothing else.
 */
@Composable
internal fun CrashLogPanel(files: List<File>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var viewing by remember { mutableStateOf<File?>(null) }
    val file = viewing
    if (file != null) {
        CrashLogDetail(file = file, onBack = { viewing = null }, modifier = modifier)
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            CrashLogHeader(onBack = onBack)
            CrashLogList(files = files, onOpen = { viewing = it }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The Settings panel's row into this panel — mirrors `AvailabilityScreen`'s own
 * `OfflineMapsEntryRow` shape exactly (a plain row, not a sticky one: it lives inside Settings'
 * own scrolling content, same reasoning as that composable's own doc comment).
 */
@Composable
internal fun CrashLogsEntryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Crash Logs", style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Mirrors `AvailabilityScreen`'s `SettingsHeader` shape exactly — see that composable's own call site for why. */
@Composable
private fun CrashLogHeader(onBack: () -> Unit) {
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
        Text("Crash Logs", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CrashLogList(files: List<File>, onOpen: (File) -> Unit, modifier: Modifier = Modifier) {
    if (files.isEmpty()) {
        Text(
            "No crash reports yet.",
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
        files.forEach { file -> CrashLogRow(file = file, onOpen = { onOpen(file) }) }
    }
}

@Composable
private fun CrashLogRow(file: File, onOpen: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatCrashTimestamp(file), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { shareCrashLog(context, file) }) {
            Icon(Icons.Filled.Share, contentDescription = "Share crash report")
        }
    }
}

@Composable
private fun CrashLogDetail(file: File, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // file.readText() is disk I/O — kept off the composing thread, same reasoning as any other
    // suspend read triggered from a LaunchedEffect elsewhere in this app.
    var content by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        content = withContext(Dispatchers.IO) { file.readText() }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        CrashLogDetailHeader(onBack = onBack)
        Text(
            content ?: "Loading…",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        )
    }
}

/** Same drill-in shape a submenu header uses elsewhere in this app: back returns to the list, one level up. */
@Composable
private fun CrashLogDetailHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to crash log list")
        Text("Crash Report", style = MaterialTheme.typography.titleMedium)
    }
}

/** A locale-formatted "when" for [file], derived from the epoch millis its filename encodes — see [CrashFileStore.epochMillisOf]. */
private fun formatCrashTimestamp(file: File): String {
    val epochMillis = CrashFileStore.epochMillisOf(file) ?: return file.name
    return DISPLAY_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")

/**
 * Hands [file] to another app via the FileProvider authority already declared for
 * [com.forager.app.photo.CameraCaptureFiles]' captures — see `res/xml/file_paths.xml`'s
 * `crashes` entry.
 */
private fun shareCrashLog(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share crash report"))
}
