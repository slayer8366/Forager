package com.zynergylabs.forager.app.ui.crash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

/**
 * The release build's twin of the debug-only Crash Logs row and panel (dispatch 2026-10-11; the owner,
 * RECORD -830: "The Crash logs row in Settings"): the same two signatures `AvailabilitySettingsUi`
 * calls, composing nothing. With no row drawn, Settings' crash-log page is unreachable, and because
 * this is a build-type source set rather than a `BuildConfig.DEBUG` branch, the panel's code is not
 * in the release APK, as with `DiagnosticsPanel`.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun CrashLogsEntryRow(onClick: () -> Unit) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun CrashLogPanel(files: List<File>, onBack: () -> Unit, modifier: Modifier = Modifier) = Unit
