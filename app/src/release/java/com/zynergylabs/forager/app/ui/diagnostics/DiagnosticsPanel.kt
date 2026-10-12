package com.zynergylabs.forager.app.ui.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The release build's twin of the debug-only diagnostics panel: the same two signatures
 * `AvailabilityScreen` calls, composing nothing. With no entry row drawn, the panel branch in
 * Settings is unreachable, and because this is a build-type source set rather than a
 * `BuildConfig.DEBUG` branch, the debug panel's code is not in the release APK at all — see the
 * debug version's own doc comment for why that distinction matters with minification off.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun DiagnosticsEntryRow(onClick: () -> Unit) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun DiagnosticsPanel(onBack: () -> Unit, modifier: Modifier = Modifier) = Unit
