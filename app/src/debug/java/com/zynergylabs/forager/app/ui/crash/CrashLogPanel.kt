package com.zynergylabs.forager.app.ui.crash

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.motion.PageSlide
import com.zynergylabs.forager.app.ui.motion.PageSlideStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
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
import com.zynergylabs.forager.app.crash.CrashFileStore
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zynergylabs.forager.app.ui.format.displayDateTime
import com.zynergylabs.forager.app.ui.format.is24HourClock

/**
 * The Settings tab's crash-log diagnostic surface — a read-only list of what
 * `com.zynergylabs.forager.app.crash.CrashUncaughtExceptionHandler` has captured, reached the same way
 * `OfflineMaps` is from `SettingsContent` (see that composable's own `CrashLogsEntryRow` call
 * site). Kept in its own package rather than folded into `AvailabilityScreen.kt`, mirroring
 * `com.zynergylabs.forager.app.ui.log.LogPanel`'s own doc comment on why: this needs none of
 * `AvailabilityScreen`'s private state, only the file list handed in.
 *
 * Deliberately plain — a diagnostic surface, not a designed feature: list, tap to view the raw
 * trace, or share it. Nothing else.
 */
@Composable
internal fun CrashLogPanel(files: List<File>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var viewing by remember { mutableStateOf<File?>(null) }
    // Motion Part 3, Amendment 2 (RECORD -682; the owner: "Push slide from the left (Recommended)"; scout T3): a crash's detail
    // slides in from the drawer's left edge while the list slides out to the right, and back reverses it. The detail is drawn
    // from the file it was opened on, so it still draws while it leaves. No fill of its own: the drawer's is the one layer.
    PageSlide(
        targetState = viewing,
        depthOf = { if (it == null) 0 else 1 },
        modifier = modifier.fillMaxWidth(),
        contentKey = { it?.path },
        pageColor = Color.Transparent,
        style = PageSlideStyle.PUSH_FROM_LEFT,
    ) { file ->
        if (file != null) {
            CrashLogDetail(file = file, onBack = { viewing = null }, modifier = Modifier.fillMaxSize())
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CrashLogHeader(onBack = onBack)
                CrashLogList(files = files, onOpen = { viewing = it }, modifier = Modifier.weight(1f))
            }
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
            .clickableWithShapedPress(role = Role.Button, onClick = onClick)
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
            .clickableWithShapedPress(role = Role.Button, onClick = onBack)
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
            .clickableWithShapedPress(role = Role.Button, onClick = onOpen)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatCrashTimestamp(file, is24HourClock()), style = MaterialTheme.typography.bodyLarge)
        BouncingIconButton(onClick = { shareCrashLog(context, file) }) {
            Icon(Icons.Filled.Share, contentDescription = "Share crash report")
        }
    }
}

@Composable
private fun CrashLogDetail(file: File, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // The read is disk I/O — kept off the composing thread, same reasoning as any other
    // suspend read triggered from a LaunchedEffect elsewhere in this app. It goes through the one
    // guarded read below (dispatch 2026-09-28-658, J1), so a file that cannot be read shows a plain
    // line instead of throwing out of the effect.
    var content by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        content = withContext(Dispatchers.IO) { readCrashLog(file) } ?: CRASH_LOG_UNREADABLE_TEXT
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
            .clickableWithShapedPress(role = Role.Button, onClick = onBack)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to crash log list")
        Text("Crash Report", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * What the detail view shows when [readCrashLog] could not read the file. The owner's wording (RECORD -660, Amendment 1 to -658).
 */
internal const val CRASH_LOG_UNREADABLE_TEXT = "Couldn't read this crash report. Go back and open it again."

/**
 * The crash log's text, or `null` when it could not be read: a file removed or pruned between the
 * list and the tap, or one the app is not allowed to read. The failure is logged with its cause,
 * and the caller shows [CRASH_LOG_UNREADABLE_TEXT] (dispatch 2026-09-28-658, J1). The one read of a
 * crash file in this panel.
 */
internal fun readCrashLog(file: File): String? =
    try {
        file.readText()
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't read the crash report ${file.name}.", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to read the crash report ${file.name}.", e)
        null
    }

private const val TAG = "CrashLogPanel"

/**
 * A "when" for [file], derived from the epoch millis its filename encodes — see [CrashFileStore.epochMillisOf]. "Oct 7, 2026,
 * 2:14 PM", or "Oct 7, 2026, 14:14" on a phone set to 24-hour time (data part D, RECORD -702).
 */
private fun formatCrashTimestamp(file: File, is24HourClock: Boolean): String {
    val epochMillis = CrashFileStore.epochMillisOf(file) ?: return file.name
    return displayDateTime(epochMillis, is24HourClock)
}

/**
 * Hands [file] to another app via the FileProvider authority already declared for
 * [com.zynergylabs.forager.app.photo.CameraCaptureFiles]' captures — see `res/xml/file_paths.xml`'s
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
