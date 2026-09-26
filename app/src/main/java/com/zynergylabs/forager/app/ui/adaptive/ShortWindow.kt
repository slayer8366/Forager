package com.zynergylabs.forager.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Material 3's compact-height boundary: a window shorter than this is *short*.
 *
 * `docs/plans/landscape-phone-design.md`, P1 and Resolution R1 (the owner's "Classify by window"):
 * any window under 480dp tall gets the sideways-phone layout — the compact tree — whatever its
 * width and whatever the device. On a phone that means landscape (the S22 Ultra's landscape
 * window is `w823dp h384dp`); a full-screen tablet is never that short.
 */
private const val SHORT_WINDOW_MAX_HEIGHT_DP = 480

/**
 * Whether the current window is short — see [SHORT_WINDOW_MAX_HEIGHT_DP].
 *
 * A separate function beside [currentWindowWidthClass] rather than a fourth [WindowWidthClass]
 * case or a height read threaded into that function (CLAUDE.md, Building: new capability is a new
 * function): width classification is unchanged, and the one caller that needs height asks this.
 * Reads [LocalConfiguration] for the same reason [currentWindowWidthClass] does (its doc comment).
 */
@Composable
fun isShortWindow(): Boolean = LocalConfiguration.current.screenHeightDp < SHORT_WINDOW_MAX_HEIGHT_DP
