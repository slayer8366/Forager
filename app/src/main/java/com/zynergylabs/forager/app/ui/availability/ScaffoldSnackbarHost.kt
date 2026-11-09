package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.5 PR 4 (dispatch 2026-10-19-39, RECORD -1477): the content of the frame's Scaffold snackbarHost slot, moved
// from CompactMainScaffold (AvailabilityCompactScaffold.kt lines 819-886 at b60d7fba) into this function, which the slot's lambda
// now calls and nothing else. Moved with its indentation, as the frame's own Stage G extraction did, and with no edit: it reads
// no State the frame holds. What it used is a parameter of the same name and type: the frame's getter parameter compactTab as
// the getter itself, called where it was called; logDraftSnackbarHostState as it is; and the frame's locals (showRail,
// mapControlsPadding, snackbarMapBottom) as values. Those locals are worked out again on every composition from inputs read in
// the frame's own scope, so any change to one recomposes the frame, which calls this again with the new value: a value here
// cannot go stale, which is the case Stage G's getter rule exists for (planner, dispatch 2026-10-19-39's go).
// mapControlsPadding holds the cut-out and navigation-bar insets and snackbarMapBottom the navigation bar's bottom inset;
// Robolectric reports them as zero, so where the snackbar sits is checked on the S22 (CLAUDE.md, "Robolectric reports zero
// window insets").

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.motion.PressHighlight
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor

@Composable
internal fun ScaffoldSnackbarHost(
    logDraftSnackbarHostState: SnackbarHostState,
    compactTab: () -> CompactTab,
    showRail: Boolean,
    mapControlsPadding: PaddingValues,
    snackbarMapBottom: Dp,
) {
                // Material3's own snackbar, with its default colours passed explicitly. Its content
                // colour is its own role (`SnackbarDefaults.contentColor`), not derived from the fill.
                SnackbarHost(logDraftSnackbarHostState) { data ->
                    // Map chrome at 80% over the Maps tab, following the tab (planner message -77, Q4).
                    // C1: the navigation bar's colour (was inverseSurface). Its default content colour, inverseOnSurface, and its
                    // action, inversePrimary, fail WCAG AA on that colour (ChromeContrastTest), so the text takes the navigation
                    // bar's own content colour, onSurfaceVariant, and the action `primary`.
                    val snackbarColor = mapChromeFill(navigationBarContainerColor(), compactTab() == CompactTab.MAP)
                    val snackbarContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    // Motion Part 1 (dispatch 2026-09-28-652, item 3, scout S7; the owner, RECORD -651: "Yes, round them all";
                    // Amendment 1, RECORD -657: "Trim to its visible edge"). Material's Snackbar(snackbarData) draws its surface
                    // 12 dp inside the modifier it is given (SNACKBAR_SURFACE_MARGIN), so a tap on that modifier also took touches
                    // in a 12 dp band around the drawn snackbar, over the map. The tap, the test tag and the chrome-colour
                    // semantics now sit on a box laid exactly over the drawn surface instead, so a touch beside the snackbar
                    // reaches the map, and the press highlight is that surface's rounded shape. A deliberate touch-area change,
                    // the owner's; pinned by SnackbarTrimmedTouchTest.
                    val tapInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            // Dispatch 2026-09-28-104, item 6: on the Maps tab the Scaffold has no bottom
                            // bar to sit above and its contentWindowInsets drop the bottom side (below), so
                            // the host lay under the system navigation buttons. Off the Maps tab the
                            // bottomBar is real and Scaffold already places the host above it, so nothing is
                            // added there. Device-only: Robolectric reports the system inset as zero.
                            // Continuation -12 (planner's ruling): on the Maps tab the snackbar sits above the
                            // floating bottom navigation, not only above the system bar. In portrait, outside
                            // fullscreen, that is the nav's own measured height (which already includes the
                            // system-bar inset it takes, so nothing is added twice). In fullscreen the nav is
                            // off screen and the snackbar keeps the system-bar inset alone. In a short
                            // landscape window the rail runs the whole height of the port edge, so the
                            // snackbar keeps the system-bar inset at the bottom and takes the map controls'
                            // own side padding (the cut-out and the rail's measured width), so the centred
                            // snackbar clears the rail.
                            // Motion Part 2, item 7 (scout S6): the bottom offset below glides (snackbarMapBottom), so these
                            // cases and the Return to Route lift are one figure now. The rail case keeps its side padding.
                            .then(if (compactTab() == CompactTab.MAP && showRail) Modifier.padding(mapControlsPadding) else Modifier)
                            .padding(bottom = snackbarMapBottom),
                    ) {
                        Snackbar(
                            snackbarData = data,
                            containerColor = snackbarColor,
                            contentColor = snackbarContentColor,
                            actionContentColor = MaterialTheme.colorScheme.primary,
                            dismissActionContentColor = snackbarContentColor,
                        )
                        // The drawn surface's own box. No pointer input unless the notice is tappable, so an ordinary notice's
                        // action and dismiss buttons, under it, keep their touches.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(SNACKBAR_SURFACE_MARGIN)
                                .testTag(COMPACT_SNACKBAR_TAG)
                                // Dispatch 2026-09-28-626: a prompt whose whole surface is its tap (TappableNoticeVisuals).
                                .then(
                                    if (data.visuals is TappableNoticeVisuals) {
                                        Modifier.clickable(interactionSource = tapInteraction, indication = null, role = Role.Button) { data.performAction() }
                                    } else {
                                        Modifier
                                    },
                                )
                                .mapChromeContainerColor(snackbarColor)
                                .mapChromeContentColor(snackbarContentColor),
                        )
                        if (data.visuals is TappableNoticeVisuals) {
                            PressHighlight(interactionSource = tapInteraction, shape = SnackbarDefaults.shape, modifier = Modifier.padding(SNACKBAR_SURFACE_MARGIN))
                        }
                    }
                }
}
