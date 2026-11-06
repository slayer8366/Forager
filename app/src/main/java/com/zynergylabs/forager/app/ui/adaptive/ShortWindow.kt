package com.zynergylabs.forager.app.ui.adaptive

import androidx.compose.runtime.Composable
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Material 3's compact-height boundary: a window shorter than this is *short*.
 *
 * `docs/plans/landscape-phone-design.md`, P1 and Resolution R1 (the owner's "Classify by window"):
 * any window under 480dp tall is *short*. On a phone that means landscape (the S22 Ultra's
 * landscape window is `w823dp h384dp`); a full-screen tablet is never that short.
 *
 * Since dispatch 2026-09-28-245 the compact tree is drawn at every window size, so *short* no longer
 * chooses a tree. It remains a height budget where one is needed, and the phone's landscape layouts
 * are chosen by [isLandscapeWindow] (dispatch -246), not by this.
 */
private const val SHORT_WINDOW_MAX_HEIGHT_DP = 480

/**
 * Whether the current window is short — see [SHORT_WINDOW_MAX_HEIGHT_DP].
 *
 * A separate function beside [currentWindowWidthClass] rather than a fourth [WindowWidthClass]
 * case or a height read threaded into that function (CLAUDE.md, Building: new capability is a new
 * function). Reads the window's own height, `LocalWindowInfo.current.containerDpSize`, for the same
 * reason [currentWindowWidthClass] does (its doc comment).
 */
@Composable
fun isShortWindow(): Boolean = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_MAX_HEIGHT_DP.dp

/**
 * Whether the current window is in landscape, whatever its height: the gate for the phone's landscape
 * layouts (the navigation rail, the L, the side-by-side Journal and offline-map pickers). Dispatch
 * 2026-09-28-246, the owner: "We did the rotation work on the phone so it should carry across to the
 * tablet". A phone in landscape is short and landscape, and a phone in portrait is neither, so every
 * phone outcome is as before; a tablet in landscape is landscape and not short, and is what this adds.
 * A short window in portrait (split screen) is not landscape and keeps its portrait arrangement.
 */
@Composable
fun isLandscapeWindow(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Short, or landscape: the gate for a rule that was "short" and is meant to follow the landscape layouts too. */
@Composable
fun isShortOrLandscapeWindow(): Boolean = isShortWindow() || isLandscapeWindow()

/**
 * Whether the window is a large screen, a tablet or an unfolded foldable: its smallest width is at least
 * [com.zynergylabs.forager.app.ui.log.LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP] (600 dp, the same threshold the camera's natural-frame
 * hold uses). Dispatch 2026-10-18-10 (Forager RECORD -1337, -1338, -1340): the gate for the tablet Journal's wider column and square
 * maps. Every phone, in either orientation, is under it (the S22 Ultra's smallest width is 384 dp), so a phone's layout never reads
 * true here. A separate function, not a case threaded into [isLandscapeWindow] (CLAUDE.md, Building).
 */
@Composable
fun isLargeScreenWindow(): Boolean =
    LocalConfiguration.current.smallestScreenWidthDp >= com.zynergylabs.forager.app.ui.log.LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP
