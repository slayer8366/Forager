package com.zynergylabs.forager.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * M3's window-width breakpoints (compact / medium / expanded+), collapsed to a 3-way enum.
 *
 * M3 defines five width breakpoints; this app only ever changes composition at the first two
 * boundaries (600dp, 840dp) so the remaining three collapse into [EXPANDED] here rather than
 * being modeled as dead cases nothing branches on yet.
 *
 * Only [COMPACT] is branched on today; [MEDIUM], [EXPANDED] and the 840 dp breakpoint are kept for the tablet work (dispatch 2026-09-28-658; the owner: "Remove what costs, keep plans").
 */
enum class WindowWidthClass {
    /** Phones in portrait. Layout is unchanged from before adaptive support existed. */
    COMPACT,

    /** Phones in landscape, small/unfolded foldables, small tablets in portrait. */
    MEDIUM,

    /** Tablets, foldables unfolded to a large surface, desktop windows. */
    EXPANDED,
}

private const val MEDIUM_BREAKPOINT_DP = 600
private const val EXPANDED_BREAKPOINT_DP = 840

/**
 * Reads the current window's width and buckets it into [WindowWidthClass].
 *
 * Reads the window's own width, `LocalWindowInfo.current.containerDpSize`, rather than depending on
 * `androidx.compose.material3.windowsizeclass` — that artifact's `calculateWindowSizeClass` needs
 * an `Activity`, which every existing headless Robolectric test of this screen does not launch
 * through (see `AvailabilityScreenLayoutTest`'s doc comment on how the host activity is declared).
 *
 * Until lint dispatch 2026-10-17-03 this read `LocalConfiguration.current.screenWidthDp`. Lint's
 * ConfigurationScreenWidthHeight check rejects that: the configuration's width is rounded, and
 * whether it leaves out the system bars depends on the target SDK, so it is not reliably the
 * window's size. `containerDpSize` is the window's bounds (Compose UI 1.12,
 * `AndroidWindowInfo.android.kt`, `calculateWindowSize`), computed when first read and updated in
 * the same configuration-change call that updates `LocalConfiguration`
 * (`ComposeViewContext.onConfigurationChanged`), so the two never disagree for a frame. Kept
 * rather than rejected: reading `WindowMetricsCalculator` here ourselves, which is what
 * `containerDpSize` already does.
 */
@Composable
fun currentWindowWidthClass(): WindowWidthClass {
    val width = LocalWindowInfo.current.containerDpSize.width
    return when {
        width < MEDIUM_BREAKPOINT_DP.dp -> WindowWidthClass.COMPACT
        width < EXPANDED_BREAKPOINT_DP.dp -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
}
