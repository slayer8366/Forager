package com.forager.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * M3's window-width breakpoints (compact / medium / expanded+), collapsed to a 3-way enum.
 *
 * M3 defines five width breakpoints; this app only ever changes composition at the first two
 * boundaries (600dp, 840dp) so the remaining three collapse into [EXPANDED] here rather than
 * being modeled as dead cases nothing branches on yet.
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
 * Reads [LocalConfiguration] directly rather than depending on
 * `androidx.compose.material3.windowsizeclass` — that artifact's `calculateWindowSizeClass` needs
 * an `Activity`, which every existing headless Robolectric test of this screen does not launch
 * through (see `AvailabilityScreenLayoutTest`'s doc comment on how the host activity is declared).
 * `LocalConfiguration.screenWidthDp` is available anywhere a composition runs and is exactly what
 * that API reads under the hood, so this gets the same breakpoint behavior without the dependency
 * or the Activity requirement.
 */
@Composable
fun currentWindowWidthClass(): WindowWidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < MEDIUM_BREAKPOINT_DP -> WindowWidthClass.COMPACT
        widthDp < EXPANDED_BREAKPOINT_DP -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
}
