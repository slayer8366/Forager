package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.5 PR 3 (dispatch 2026-10-19-39, RECORD -1477): the search pill on List, Seasonal and the Journal (the Maps
// tab's own is in ScaffoldMapTabCall's searchBarSlot), with the Box that measures its slot for JournalSearchPillRoom. Moved from
// CompactMainScaffold (AvailabilityCompactScaffold.kt lines 1113-1185 at b60d7fba) into this function, which the frame calls at
// the same place: first in its content Column, above the tab content. The block is moved with its indentation, as the frame's
// own Stage G extraction did, and with its two remembers and its LaunchedEffect, which were already inside it. The only edit:
// showSearchDropdown is written through [ScaffoldSearchDropdownState], a setter on the frame's own State, at the same place.
// Everything else it used is a parameter of the same name and type: the frame's getter parameters (isMapFullscreen,
// compactTab) as the getters themselves, called where they were called; the frame's other parameters as they are; and the
// frame's locals (journalSearchHeld, journalHidesSearchHeader, journalSearchPillRoom, tabLandscapePillPlace, readableContentCap,
// railBeside, lastMapsStripHeight, tabPillStripLineHeight) as values. Those locals are worked out again on every composition
// from inputs read in the frame's own scopes, so any change to one recomposes a scope that calls this again with the new value:
// a value here cannot go stale, which is the case Stage G's getter rule exists for (planner, dispatch 2026-10-19-39's go).
// journalSearchPillRoom is the frame's remembered object itself, not a copy. The pill's place (tabLandscapePillPlace) holds the
// navigation-bar inset on the side away from the rail, worked out in the frame; Robolectric reports it as zero, so where the pill
// sits in landscape is checked on the S22 (CLAUDE.md, "Robolectric reports zero window insets").
// The fully qualified AnimatedVisibility call is left as it was.

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens

@Composable
internal fun ScaffoldHeaderSearch(
    dropdown: ScaffoldSearchDropdownState,
    isMapFullscreen: () -> Boolean,
    compactTab: () -> CompactTab,
    journalSearchHeld: Boolean,
    journalHidesSearchHeader: Boolean,
    journalSearchPillRoom: JournalSearchPillRoom,
    tabLandscapePillPlace: Modifier,
    readableContentCap: Modifier,
    railBeside: Boolean,
    lastMapsStripHeight: Dp,
    tabPillStripLineHeight: Dp,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    appClearFocusInProgress: BooleanArray,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onClearSearch: () -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
) {
                    Box(modifier = Modifier.measuresJournalSearchPill(journalSearchPillRoom)) {
                    if (!isMapFullscreen() && compactTab() != CompactTab.MAP && !(journalSearchHeld && compactTab() == CompactTab.JOURNAL)) {
                        // Dispatch 2026-10-17-09, RECORD -1244 (the owner: "And have the search bar slide up and down when
                        // revealing/hiding"): J5's reveal on the Journal (journalHidesSearchHeader) slides the pill down into place
                        // from above, and back up out of view, while the content below follows it (the expand and shrink), on
                        // MotionTokens.panelMotionSpec, the spec the Maps tab's own search bar slides on into and out of fullscreen.
                        // Arriving on a tab shows its pill as it stands, with no slide: only a reveal or a hide on the Journal plays
                        // one. Every other way the pill comes or goes is as before, at once. Under reduced motion the reveal is at
                        // once too (docs/motion-spec.md §4).
                        // Dispatch 2026-10-18-03, item 3 (Forager RECORD -1269, "Fix item 4 too"; Elder's top-up audit, item 4):
                        // this was a key(compactTab()) around the AnimatedVisibility, which re-created the pill on every tab
                        // switch, so its field lost focus and its cursor. Now the pill is composed once: a switch of tab is told
                        // apart from a reveal or hide on the same tab (searchHeaderSlideTab, the tab the last visibility change
                        // settled on), and the switch's change of visibility takes no transition.
                        val searchHeaderReduceMotion = LocalReduceMotion.current
                        val searchHeaderVisibility = remember { MutableTransitionState(!journalHidesSearchHeader) }
                        searchHeaderVisibility.targetState = !journalHidesSearchHeader
                        var searchHeaderSlideTab by remember { mutableStateOf(compactTab()) }
                        val searchHeaderArriving = searchHeaderSlideTab != compactTab()
                        LaunchedEffect(compactTab(), searchHeaderVisibility.isIdle) {
                            if (searchHeaderVisibility.isIdle) searchHeaderSlideTab = compactTab()
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = searchHeaderVisibility,
                            enter = if (searchHeaderReduceMotion || searchHeaderArriving) {
                                EnterTransition.None
                            } else {
                                slideInVertically(animationSpec = MotionTokens.panelMotionSpec()) { fullHeight -> -fullHeight } +
                                    expandVertically(animationSpec = MotionTokens.panelMotionSpec(), expandFrom = Alignment.Top)
                            },
                            exit = if (searchHeaderReduceMotion || searchHeaderArriving) {
                                ExitTransition.None
                            } else {
                                slideOutVertically(animationSpec = MotionTokens.panelMotionSpec()) { fullHeight -> -fullHeight } +
                                    shrinkVertically(animationSpec = MotionTokens.panelMotionSpec(), shrinkTowards = Alignment.Top)
                            },
                        ) {
                        Column {
                        // SearchEntryBar takes no modifier, so its place goes on this wrapper (tabLandscapePillPlace).
                        Box(modifier = tabLandscapePillPlace) {
                        SearchEntryBar(
                            uiState = uiState,
                            distanceUnit = distanceUnit,
                            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                            onTaxonSearchResultSelected = { result ->
                                // RECORD -725/-726, the owner: "just keep the keyboard open and fill it in, no search yet"; "The
                                // keyboard and the search bar". Only the suggestion list closes (the ViewModel empties it); the
                                // dropdown, the field's focus and the keyboard stay. It closed the dropdown here before.
                                onTaxonSearchResultSelected(result)
                            },
                            onClearSearch = onClearSearch,
                            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                            onFieldFocused = { if (!appClearFocusInProgress[0]) dropdown.showSearchDropdown = true },
                            // Dispatch 2026-10-17-09 (RECORD -1224, -1225; the owner: "The updated UI still needs to be applied to
                            // the Journal, the Seasonal, and the List tab"): the Maps tab's pill, its corners and its outline all
                            // round, with no divider, solid here since no map is behind it (overMap false; the owner: "In the places
                            // that aren't covering a map, they can stay solid"). Its height is the Maps pill's, so it does not change
                            // size across the tab switch. In landscape it is the Maps pill's size, at the left, clear of the
                            // navigation bar (tabLandscapePillPlace), at the Maps strip's height (RECORD -729); with no strip
                            // beside it here it rounds all four corners and is outlined all round.
                            // Dispatch 2026-10-18-03, item 2: the strip's height as Maps last measured it, else its one line
                            // worked out here (the strip's evening lines are not modelled; listed in known-bugs).
                            landscapeHeight = if (railBeside) lastMapsStripHeight.takeIf { it > 0.dp } ?: tabPillStripLineHeight else null,
                            pillCorners = MapCardCorners.ALL,
                            pillOutlineSides = MapChromeOutlineSides(left = true, top = true, right = true, bottom = true),
                        )
                        }
                        // Under the pill, inside the readable-width cap, as it was before the pill moved.
                        Box(modifier = readableContentCap) { SearchNotice(uiState) }
                        }
                        }
                    }
                    }
}
