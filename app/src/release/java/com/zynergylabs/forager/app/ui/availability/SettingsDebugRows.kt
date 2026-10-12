package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable

/**
 * The release build's twin of the end of Settings' debug-only list (dispatch 2026-10-11, RECORD -830):
 * no Crash Logs row, no Diagnostics row, and so no divider above them. Settings ends at Backup.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SettingsDebugRows(onOpenCrashLogs: () -> Unit, onOpenDiagnostics: () -> Unit) = Unit
