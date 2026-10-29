package com.zynergylabs.forager.app.ui.availability

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.ui.crash.CrashLogsEntryRow
import com.zynergylabs.forager.app.ui.diagnostics.DiagnosticsEntryRow

/**
 * Debug builds: the end of Settings' list, a divider under Backup and then the two debug-only rows,
 * Crash Logs and Diagnostics. Exactly what `SettingsContent` drew inline before dispatch 2026-10-11;
 * moved here so the divider exists only where a row follows it (RECORD -830: release has neither row,
 * and its twin in `src/release` draws nothing, divider included). Emitted straight into the caller's
 * column, so spacing and order are unchanged.
 */
@Composable
internal fun SettingsDebugRows(onOpenCrashLogs: () -> Unit, onOpenDiagnostics: () -> Unit) {
    HorizontalDivider()
    CrashLogsEntryRow(onClick = onOpenCrashLogs)
    DiagnosticsEntryRow(onClick = onOpenDiagnostics)
}
