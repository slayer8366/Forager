package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.5 PR 1 (dispatch 2026-10-19-39, RECORD -1477): the search dropdown's dismiss scrim and panel, moved from
// CompactMainScaffold (AvailabilityCompactScaffold.kt lines 1677-1877 as of b60d7fba) into this function, which the frame calls at
// the same place: inside its BoxWithConstraints, after the tab content, so both still draw over it and are tried first for a
// touch. The block is moved with its indentation, as the frame's own Stage G extraction did. The only edits to it:
//  - showSearchDropdown and pickingSearchLocationOnMap are read and written through [ScaffoldSearchDropdownState], a getter and
//    a setter on the frame's own State, at the same places;
//  - mapCompassStripHeight, a State the frame holds, is read through a getter, mapCompassStripHeight(), at the same place;
//  - everything else it used is a parameter of the same name and type: the frame's getter parameters (isMapFullscreen,
//    compactTab) as the getters themselves, called where they were called; the frame's other parameters as they are; and the
//    frame's locals (mapSearchBarHeight, bottomNavHeight, mapControlsPadding, landscapeSearchWidth, compassStripClearance,
//    railBeside, tabLandscapePillPlace) as values. Those locals are worked out again on every composition from inputs read in
//    the frame's own scopes (landscapeSearchWidth in the BoxWithConstraints that calls this, the rest above it), so any change
//    to one recomposes a scope that calls this again with the new value: a value here cannot go stale, which is the case Stage
//    G's getter rule exists for (planner, dispatch 2026-10-19-39's go).
// One comment in the block no longer describes its place: the one on the fully qualified AnimatedVisibility names an implicit
// ColumnScope from the frame's Column, which this function does not have. The call is left fully qualified and the comment as
// it was, since a move carries its comments unchanged (survey section 7.8).
// The block reads real insets (WindowInsets.ime, isImeVisible) at the same places, still in this composition; Robolectric reports
// them as zero, so what they do is checked on the S22 (CLAUDE.md, "Robolectric reports zero window insets").

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches

@Composable
internal fun BoxWithConstraintsScope.ScaffoldSearchDropdownLayer(
    dropdown: ScaffoldSearchDropdownState,
    isMapFullscreen: () -> Boolean,
    compactTab: () -> CompactTab,
    mapSearchBarHeight: Dp,
    bottomNavHeight: Dp,
    mapControlsPadding: PaddingValues,
    landscapeSearchWidth: Dp?,
    mapCompassStripHeight: () -> Dp,
    compassStripClearance: Dp,
    punchHoleEdge: ScreenEdge,
    railBeside: Boolean,
    tabLandscapePillPlace: Modifier,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onCompactTabChange: (CompactTab) -> Unit,
    onSelectedTabChange: (ResultsTab) -> Unit,
) {
                        if (!isMapFullscreen()) {
                            // Dismiss-elsewhere scrim for SearchEntryBar's own "tap to focus, dismiss
                            // elsewhere" model (map/navigation redesign dispatch D): while the dropdown
                            // is open, SearchDropdown's own bounds only cover its own (bounded, scrolled)
                            // content height, not the full remaining area below SearchEntryBar — a tap
                            // on visible tab content past that edge would otherwise reach the tab
                            // underneath (panning the map, tapping a sighting dot) with no way to close
                            // the panel except the back button. This is a real, intentional interception
                            // — the opposite of Understory rule 1's "nothing here swallows a touch meant
                            // for the map," which is about the *collapsed* state, not an actively open
                            // modal panel — present only while showSearchDropdown is true, composed
                            // before SearchDropdown so that panel's own controls still win the tap they
                            // sit on (composition-order-is-hit-test-order, the same rule this file's
                            // other overlapping surfaces already rely on).
                            if (dropdown.showSearchDropdown) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Map tab only: excludes SearchEntryBar's own band from the
                                        // scrim's bounds entirely, top edge down. That bar now composes
                                        // inside CompactMapTab's own searchBarSlot (that call site's own
                                        // doc comment — needed for its 80% fill to actually blend with
                                        // the map, an interop-nesting requirement, not a cosmetic one),
                                        // which puts it earlier in this Box's composition order than
                                        // this scrim — composition-order-is-hit-test-order would
                                        // otherwise mean the scrim wins every tap on the bar while the
                                        // dropdown is open, breaking "type in the bar while the dropdown
                                        // is still showing." Excluding the region outright, rather than
                                        // reordering composition, keeps the scrim's own intercept of the
                                        // map underneath it intact (composed before CompactMapTab would
                                        // let the map's own pan gesture win those same taps instead).
                                        //
                                        // Same reasoning, bottom edge up, for the bottom nav — fullscreen-
                                        // fixes dispatch, Item 1 (third design). Before that dispatch,
                                        // the nav always lived in Scaffold's own bottomBar slot, a
                                        // separate composition subtree this scrim's fillMaxSize() never
                                        // reached, so the nav stayed tappable regardless of this dropdown
                                        // on every tab, Map included. Now that the Map tab's own nav
                                        // instance composes inside CompactMapTab's own Box (a descendant
                                        // of this same weight(1f) Box the scrim also fills), leaving this
                                        // unexcluded would silently swallow every tap on it while the
                                        // dropdown is open — confirmed, reproducible: this is exactly
                                        // what AvailabilityScreenMapIconStackTest's own bottom-nav tests
                                        // caught (map-slot still present after "tapping" List/Seasonal/
                                        // Tools, because the tap never reached the nav's own onClick at
                                        // all — though this scrim turned out not to be the actual
                                        // culprit there; see the SearchDropdown AnimatedVisibility's own
                                        // heightIn doc comment below for what was). bottomNavHeight
                                        // (that hoisted var's own doc comment explains why it's a live
                                        // measurement, not a fixed constant) applies only on the Map
                                        // tab, where the nav lives in this Box; every other tab keeps it
                                        // in bottomBar again, so this is a no-op there.
                                        .padding(
                                            top = if (compactTab() == CompactTab.MAP) mapSearchBarHeight else 0.dp,
                                            bottom = if (compactTab() == CompactTab.MAP) bottomNavHeight else 0.dp,
                                        )
                                        // Landscape B2 (S2): off the overlaid rail, so a tap on the
                                        // rail while the dropdown is open still reaches the rail.
                                        // Zero in portrait; Map tab only (elsewhere the rail is
                                        // beside this Box, not in it).
                                        .padding(if (compactTab() == CompactTab.MAP) mapControlsPadding else PaddingValues(0.dp))
                                        .testTag(SEARCH_DROPDOWN_SCRIM_TAG)
                                        .pointerInput(Unit) {
                                            detectTapGestures { dropdown.showSearchDropdown = false }
                                        },
                                )
                            }
                            // Fully qualified: an implicit ColumnScope receiver is still in scope from
                            // the outer Column this Box sits inside, which makes the bare name resolve
                            // to ColumnScope's own AnimatedVisibility overload instead of this top-level
                            // one — Kotlin then refuses it ("cannot be called with an implicit
                            // receiver") since a BoxScope, not a ColumnScope, is this call's real one.
                            // fullscreen-fixes dispatch, Item 1 (third design): fed into heightIn below.
                            // RECORD -750, item 1 (the owner: "The drop down search panel has a gap between the top of the panel and
                            // the bottom of the bar. It should sit flush against the bar to look continuous"): in a landscape window
                            // the strip is beside the bar, not under it, so the panel starts at the bar's own bottom, its divider. The
                            // strip's clearance below was added there too, and since the landscape strip reports no portrait height
                            // (mapCompassStripHeight is 0 there) it fell back to one text line, the gap. Portrait is unchanged.
                            val searchDropdownTopOffset = when {
                                compactTab() != CompactTab.MAP -> 0.dp
                                landscapeSearchWidth != null -> mapSearchBarHeight
                                else -> mapSearchBarHeight + mapCompassStripHeight().coerceAtLeast(compassStripClearance)
                            }
                            // Part 1 layout fixes, item 3 (Part 1's device check, check 8): on the Maps tab
                            // this Box runs to the window's bottom, since the tab's contentWindowInsets
                            // reserve only the top and sides, so the keyboard does not shrink it. The panel's
                            // cap therefore keeps it above the keyboard's own inset, or above the nav band,
                            // whichever reaches higher. The other tabs' Scaffold padding (safeDrawing, which
                            // includes the keyboard) shrinks this Box already. Robolectric reports no
                            // keyboard, so this is device-only by construction.
                            val searchDropdownImeBottom = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
                            // RECORD -750, item 2 (the owner: "If the keyboard is floating keep it to the one side. The bar still should
                            // scroll up to reveal the search button at the bottom"): in landscape the panel's cap now follows the
                            // keyboard as portrait's does (below), and the panel is told the keyboard is up, read from the real IME
                            // insets rather than only from its own viewport shrinking: docked (a bottom inset) or floating (visible,
                            // but a floating keyboard reports no inset). Either way the panel scrolls to its end, so Search and Set on
                            // map sit above a docked keyboard's top or at the panel's own bottom. Its width and side never change.
                            // Device-only: Robolectric has no keyboard, and whether a floating keyboard reports itself visible is the
                            // OEM's (see the -750 report).
                            @OptIn(ExperimentalLayoutApi::class)
                            val searchDropdownKeyboardUp = landscapeSearchWidth != null && (searchDropdownImeBottom > 0.dp || WindowInsets.isImeVisible)
                            // RECORD -691: no minimum touch target while it leaves, so a touch near a leaving piece under 48 dp is not handed to it (motion/LeavingTakesNoTouches.kt, NoTouchTargetExpansion).
                            NoTouchTargetExpansion(active = !dropdown.showSearchDropdown) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = dropdown.showSearchDropdown,
                                enter = expandVertically(animationSpec = MotionTokens.panelMotionSpec()) + fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
                                exit = shrinkVertically(animationSpec = MotionTokens.panelMotionSpec()) + fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
                                // Starts below the compass strip rather than painting over it — Map tab
                                // only, since the strip only exists inside CompactMapTab; every other
                                // tab keeps the panel flush against the top like before. No extra gap
                                // beyond the strip's own measured height: the owner's own direct call
                                // ("bar, strip, drawer... each meeting the next without a break") — an
                                // earlier version added Spacing.sm here on top of compassStripClearance,
                                // which read as a seam between the strip and this panel rather than one
                                // continuous piece of chrome. searchBarHeight added on top of that, Map
                                // tab only: SearchEntryBar now overlays the map above the strip on this
                                // tab (see this scaffold's own searchBarHeight doc comment), so the
                                // drawer needs to start below both, not just the strip.
                                //
                                // heightIn(max=...) — fullscreen-fixes dispatch, Item 1 (third design):
                                // this panel's own SearchDropdown has a verticalScroll expecting a
                                // bounded parent, but nothing here previously bounded it — a latent
                                // overflow, harmless before this dispatch because the weight(1f) Box
                                // this sits in never held anything sensitive in the overflow region.
                                // Now that the Map tab's own bottom nav lives inside that same Box (see
                                // CompactMapTab's own doc comment), an unbounded panel here — expanded
                                // via "Enter coordinates manually" (a fold dispatch 2026-09-28-697 removed), exactly what
                                // AvailabilityScreenMapIconStackTest's own searchAReferenceRegion()
                                // helper does — measured tall enough to physically reach into the nav's
                                // own screen band and, being composed after it, won every tap there:
                                // confirmed via that test's own bounds queries (SearchDropdown's own
                                // reported bounds genuinely overlapped the nav's), not assumed. Capped
                                // to what's actually left below this panel's own top offset, minus the
                                // nav's own band on the Map tab, so it scrolls instead of overflowing.
                                modifier = (if (landscapeSearchWidth != null) {
                                    // Landscape B2 (S2): the bar's width and side.
                                    Modifier
                                        .align(if (punchHoleEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                        .padding(mapControlsPadding)
                                        .padding(top = searchDropdownTopOffset)
                                        .width(landscapeSearchWidth)
                                        // RECORD -750, item 2: above a docked keyboard, as portrait's cap is (no nav band in landscape).
                                        .heightIn(max = (maxHeight - searchDropdownTopOffset - searchDropdownImeBottom).coerceAtLeast(0.dp))
                                } else if (railBeside) {
                                    // Dispatch 2026-10-17-09: beside the rail the pill is the Maps pill's size, at the left
                                    // (tabLandscapePillPlace), so its panel takes the pill's width and place, as on Maps (Landscape B2,
                                    // S2), instead of the readable-width column the pill used to sit in. No nav band in landscape.
                                    Modifier
                                        .align(if (punchHoleEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                        .padding(top = searchDropdownTopOffset)
                                        .then(tabLandscapePillPlace)
                                        .heightIn(max = (maxHeight - searchDropdownTopOffset).coerceAtLeast(0.dp))
                                } else {
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = searchDropdownTopOffset)
                                        .heightIn(
                                            max = maxHeight - searchDropdownTopOffset -
                                                (if (compactTab() == CompactTab.MAP) maxOf(bottomNavHeight, searchDropdownImeBottom) else 0.dp),
                                        )
                                })
                                    // Scout item Q5 (dispatch 2026-09-28-652, item 7; the owner, RECORD -651: "Fix it in this
                                    // sweep"): from the moment the panel starts closing it takes no touch. The dismiss scrim above
                                    // is already gone by then (it is composed only while showSearchDropdown), so a tap anywhere,
                                    // the shrinking panel included, reaches the map, and none of the panel's buttons can be pressed.
                                    // The panel still draws its shrink and fade. Last in the chain, so its bounds are the panel's.
                                    .leavingTakesNoTouches(leaving = !dropdown.showSearchDropdown),
                            ) {
                                SearchDropdown(
                                    uiState = uiState,
                                    distanceUnit = distanceUnit,
                                    onRecentSearchSelected = { summary ->
                                        dropdown.showSearchDropdown = false
                                        onRecentSearchSelected(summary)
                                    },
                                    currentTime = currentTime,
                                    onManualLatChanged = onManualLatChanged,
                                    onManualLngChanged = onManualLngChanged,
                                    onSearchManualCoordinates = {
                                        dropdown.showSearchDropdown = false
                                        onSearchManualCoordinates()
                                    },
                                    onRadiusChanged = onRadiusChanged,
                                    onMonthSelected = onMonthSelected,
                                    onUseCurrentLocation = {
                                        dropdown.showSearchDropdown = false
                                        onUseCurrentLocation()
                                    },
                                    onSetOnMap = {
                                        dropdown.showSearchDropdown = false
                                        onCompactTabChange(CompactTab.MAP)
                                        onSelectedTabChange(ResultsTab.MAP)
                                        dropdown.pickingSearchLocationOnMap = true
                                    },
                                    // Over the Maps tab's map only; on the other tabs its Month menu stays solid.
                                    overMap = compactTab() == CompactTab.MAP,
                                    keyboardUp = searchDropdownKeyboardUp,
                                )
                            }
                            }
                        }
}
