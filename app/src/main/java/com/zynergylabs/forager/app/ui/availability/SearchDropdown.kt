package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySearchUi.kt (dispatch 2026-10-19-17, UI split Phase 1, PR 10a).

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.theme.mapChromeContentColor
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing


/**
 * Map/navigation redesign dispatch C: the compact window's entire search surface, floating over
 * whatever tab content is currently showing (usually the map) from where quick species search used
 * to sit — the quick panel is gone rather than kept alongside this one. Started as just item 1's "advanced search" (location/radius/month); a
 * follow-up owner call folded species search and Recent Searches in too, on the same "one place
 * instead of two" reasoning item 1 itself already argued for its own content — those had been
 * living in the Tools drawer, one tap further away and split across two surfaces from location
 * search's own new home.
 *
 * **Radius and month promoted to this surface's own top level, alongside basic search — Advanced
 * search is location only now.** Originally shaped with radius/month nested inside "Advanced
 * search" alongside location; the project owner asked directly for radius and month to move up to
 * "normal search" instead, on the reasoning both are reached for on nearly every search the same
 * way species/category already are, unlike location (set on map/use current location/manual
 * coordinates), which stays gated one tap deeper since most searches don't need to override it.
 *
 * **Composed after the tab content in the same [Box] at its call site, so it draws on top by
 * composition order alone** — the same "later-composed wins" rule already governing the Tools
 * drawer's own relationship to the tab content it overlays. Understory's four over-the-map rules
 * apply here as an entry condition, not a guideline (dispatch C's own text, since this surface sits
 * over the map when expanded):
 * 1. No [Surface] — a plain [Box] with [background], the same non-intercepting shape
 *    [CompassElevationStripContent] already uses, so nothing here swallows a touch meant for the
 *    map underneath once this closes.
 * 2. No scroll modifier *for content that lets the map still show through it* — the reason this
 *    panel used to call [SpeciesSearchControls] with `chipRowScrollable = false` before dispatch D
 *    moved that composable's own field out into [SearchEntryBar] (a category chip row's own default
 *    `horizontalScroll` is exactly the pointer-handler-with-nothing-to-scroll shape that rule bans
 *    for a thin decorative strip, though not a concern for this panel's own remaining content, none
 *    of which scrolls horizontally). This whole panel is a different case regardless: rule 1's
 *    [background] (at the map chrome's alpha, so the map shows through it) already blocks every touch within its bounds from reaching the map
 *    underneath, so once opened it behaves like the drawer's own [SearchControls] sheet, not like
 *    the compass strip — and on the smallest
 *    supported phone (`w360dp-h640dp-xhdpi`), the panel as it stood before dispatch
 *    2026-09-28-697 (both coordinate folds expanded) genuinely didn't fit in the space [compactMainScaffold] hands this
 *    surface (`Modifier.weight(1f)`'s remaining height below the summary bar and above the bottom
 *    nav), which is exactly the "starved children, nothing scrolled, simply unreachable" failure
 *    this file's own `Column`-without-`verticalScroll` doc comment already describes for the old
 *    stacked layout. So the inner [Column] below carries [Modifier.verticalScroll], the same
 *    "`weight(1f)` gives a bounded, not infinite, height to scroll within" pattern already used for
 *    [SearchControls], the offline map picker, and the settings panel in this same file — proven
 *    safe here by [AvailabilityScreenLayoutTest]'s own "every advanced-search dropdown control is
 *    reachable" test at that exact device config.
 * 3. Full width is the deliberate choice here, not left to default [fillMaxWidth] creep: a
 *    location/radius/month panel needs real width for its fields and slider to be usable, the same
 *    reasoning [CompassElevationStripContent]'s own doc comment already states for going full width.
 * 4. A long-press/real-touch test accompanies this, per [AvailabilityScreenMapIconStackTest]'s own
 *    "item 5" precedent from Dispatch A.
 *
 * Item 2 of dispatch C — location input, SIDE BY SIDE: "Set on map" reuses the exact same
 * pan-to-centre-pin-plus-confirm flow every other pin placement in this app already uses
 * ([CentrePinLocationPickerOverlay], surfaced over [CompactMapTab]'s own real map — see
 * `compactMainScaffold`'s `pickingSearchLocationOnMap` state for the plumbing), not a second
 * picker. Since dispatch 2026-09-28-697 it sits on the left of the bottom row, beside Search.
 *
 * Item 3 — manual coordinates: once collapsed by default under "Enter coordinates manually" inside
 * "Advanced search"; since dispatch 2026-09-28-697 both folds are gone and Latitude and Longitude
 * are this panel's first row, searched by the "Search coordinates" button under them (RECORD -700).
 * The bottom row's Search is "Use current location" renamed and moved (see the body's comment).
 *
 * Item 4 — the "redundant list" this dispatch asks to confirm and remove: none exists. The only
 * list this drawer's advanced-search content has ever shown is [ResultsSection]'s ranked species
 * list, and that has exactly one call site, inside [ListTab] — nothing resembling it lived in
 * [RegionControls]/[MonthSelector] (the content this dropdown's own radius/month controls are
 * drawn from) before this dispatch, confirmed by reading both, so there is nothing to remove.
 */
@Composable
internal fun SearchDropdown(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    /** The bottom row's Search: "Use current location", renamed and moved (RECORD -700). */
    onUseCurrentLocation: () -> Unit,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    currentTime: CurrentTimeProvider,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onSetOnMap: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether this panel opens over the Maps tab's map; its Month menu is then at the map chrome's alpha (owner, "1 A"). */
    overMap: Boolean = false,
    /**
     * RECORD -750, item 2: whether a keyboard is up over the panel, read by the scaffold from the real IME insets (docked: a
     * bottom inset; floating: visible with none). On each rise, the panel scrolls to its end, as on a viewport shrink, until
     * the user drags it. False keeps the viewport-shrink rule alone (portrait, and every test that does not set it).
     */
    keyboardUp: Boolean = false,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val currentKeyboardUp by rememberUpdatedState(keyboardUp)
    // Dispatch 2026-10-17-09 (RECORD -1238, the owner: "Yes, both (Recommended)"; the page's text read through this panel in the
    // owner's floating-keyboard screenshot): at the map chrome's alpha over the Maps tab's map, as before (MapIconStackButtonColor
    // is that role at 0.8), and solid off the map (owner: "In the places that aren't covering a map, they can stay solid").
    val panelFill = mapChromeFill((if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight).copy(alpha = 1f), overMap)
    CompositionLocalProvider(LocalContentColor provides mapChromeContentColor(isDarkTheme)) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                // Rule 1 above: Box + background, never Surface, over the map.
                .background(
                    color = panelFill,
                    shape = RectangleShape,
                )
                .testTag(SEARCH_DROPDOWN_TAG)
                .mapChromeContainerColor(panelFill),
        ) {
            val scrollState = rememberScrollState()
            // Map/navigation search-UI redo dispatch: "scrolling the drawer dismisses the
            // keyboard" — the path to reach Month (and everything below it) without the keyboard,
            // raised by the search bar's own field focus, eating the space this content needs to
            // scroll into view. clearFocus() rather than a manual IME-hide call: the field is what
            // holds focus and raised the keyboard in the first place, so releasing it is what
            // actually lowers the keyboard, the same cause-and-effect Android already wires up.
            val focusManager = LocalFocusManager.current
            LaunchedEffect(scrollState.isScrollInProgress) {
                if (scrollState.isScrollInProgress) focusManager.clearFocus()
            }
            // Dispatch 2026-09-28-697 (RECORD intent -697) reordered this panel, top to bottom:
            // coordinates with "Search coordinates" under them (RECORD -700), Month, Search radius,
            // Recent searches, then Set on map and Search side by side at the bottom. The owner, verbatim: "the search menu is a bit unorganized. The
            // search button is all the way at the top, tucked away, while manual search is at the
            // bottom. That's a bit backwards"; "The set on map and search button where the "Search
            // this location" button is now. Manual can go at the top, tucked away where the current
            // search is"; "remove the drop down functions for the advanced search"; and "Search on
            // the right, set on map on the left". So the "Advanced search" and "Enter coordinates
            // manually" folds are gone and Latitude and Longitude show at once, which is what the
            // earlier "Also open manual coordinates" (continuation 2026-09-28-40) asked for and what
            // the one-shot expand request used to arrange. The scroll-to-the-end on open, and the
            // keep-in-view that followed it (continuation 2026-09-28-41, Part 1 layout fixes item 3),
            // are gone with it: they existed to bring the coordinates into view from the bottom of
            // the panel, and the coordinates are now its first row, so the panel opens at its top.
            // Recent searches keeps its own fold, as the dispatch says.
            //
            // Dispatch 2026-09-28-722 (RECORD intent -722) brings the keep-in-view back for the bottom
            // row. The owner, verbatim: "Real quick, the search menu doesn't bounce back up when the
            // keyboard hits it. It used to do that. The search button is still hidden as a result."
            // Removing the keep-in-view above with the scroll-to-the-end also removed the only thing
            // that lifted the panel's end above the keyboard, and the end now holds Set on map and
            // Search. So: each time the viewport shrinks (the keyboard coming up shrinks this panel,
            // its cap follows the keyboard, see compactMainScaffold), the panel scrolls to its end, so
            // the bottom row sits just above the keyboard. Only on a shrink, never on the first size
            // or a growth, so the panel still opens at its top with the coordinates first (-697), and
            // ScrollState's own clamp handles a growth as it did before. A drag by the user stops it
            // until the next open (the panel leaves composition when it closes, so the flag resets),
            // the old rule (Part 1 layout fixes item 3): their scroll stands from then on. Keyed on the
            // viewport, not the content, so Recent searches opening does not pull the view to the end.
            var bottomRowKeptInView by remember { mutableStateOf(true) }
            if (bottomRowKeptInView) {
                LaunchedEffect(Unit) {
                    launch {
                        scrollState.interactionSource.interactions.first { it is DragInteraction.Start }
                        bottomRowKeptInView = false
                    }
                    // RECORD -750, item 2: the keyboard's own rise, read from its insets, not only the shrink it may cause. A
                    // floating keyboard shrinks nothing, and a panel opened with the keyboard already up never sees a shrink.
                    // On each rise (false to true) the panel goes to its end; the shrink rule below then follows a docked
                    // keyboard's inset as it grows, and a later growth of the viewport clamps to the new end. Only on a rise,
                    // never on a content change, so opening Recent searches still does not pull the view to the end.
                    launch {
                        var wasUp = false
                        snapshotFlow { currentKeyboardUp }.collect { up ->
                            if (up && !wasUp) scrollState.scrollTo(scrollState.maxValue)
                            wasUp = up
                        }
                    }
                    var previousViewport = scrollState.viewportSize
                    snapshotFlow { scrollState.viewportSize }.collect { viewport ->
                        if (viewport < previousViewport) scrollState.scrollTo(scrollState.maxValue)
                        previousViewport = viewport
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Rule 2 above: bounded by the weight(1f) Box this surface is composed into,
                    // not a no-op — see this composable's own doc comment for why a scroll is safe
                    // here despite Understory rule 2 banning it for content over the visible map.
                    .verticalScroll(scrollState)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = uiState.manualLatText,
                        onValueChange = onManualLatChanged,
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f).testTag(SEARCH_DROPDOWN_LATITUDE_TAG),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.manualLngText,
                        onValueChange = onManualLngChanged,
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f).testTag(SEARCH_DROPDOWN_LONGITUDE_TAG),
                        singleLine = true,
                    )
                }
                // RECORD -700, the owner on typed coordinates: "Small button under the fields". A text
                // button, so it reads as smaller than the bottom row's two; it runs the manual-coordinates
                // search "Search this location" ran, its validation and error text unchanged.
                TextButton(
                    onClick = onSearchManualCoordinates,
                    modifier = Modifier.testTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG),
                ) {
                    Text("Search coordinates")
                }
                MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected, overMap = overMap)
                Text(
                    "Search radius: ${formatDistanceKm(uiState.radiusKm, distanceUnit)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = uiState.radiusKm.toFloat(),
                    onValueChange = { onRadiusChanged(it.toInt()) },
                    valueRange = 1f..50f,
                    steps = 48,
                )

                HorizontalDivider()
                CollapsibleSection(title = "Recent searches") {
                    RecentSearchesSection(
                        recentSearches = uiState.recentSearches,
                        currentTime = currentTime,
                        distanceUnit = distanceUnit,
                        onRecentSearchSelected = onRecentSearchSelected,
                    )
                }

                HorizontalDivider()
                // The bottom row, near the thumb: Set on map on the left, Search on the right (owner,
                // "Search on the right, set on map on the left"). Actions, not selections:
                // OutlinedButton/Button, not FilterChip. Search is "Use current location" renamed and
                // moved, the same path unchanged (permission, one fix, the fields written, a search),
                // and reads no field. RECORD -700, the owner: "There are no fields to fill in. Search is
                // the same as "Use current location" just renamed and relocated."
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = onSetOnMap,
                        modifier = Modifier.weight(1f).testTag(SEARCH_DROPDOWN_SET_ON_MAP_TAG),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Set on map")
                    }
                    Button(
                        onClick = onUseCurrentLocation,
                        modifier = Modifier.weight(1f).testTag(SEARCH_DROPDOWN_SEARCH_TAG),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Search")
                    }
                }
            }
        }
    }
}

/** [SearchDropdown]'s Latitude field, for tests. */
internal const val SEARCH_DROPDOWN_LATITUDE_TAG = "search-dropdown-latitude"

/** [SearchDropdown]'s Longitude field, for tests. */
internal const val SEARCH_DROPDOWN_LONGITUDE_TAG = "search-dropdown-longitude"

/** [SearchDropdown]'s "Search coordinates" text button, under Latitude and Longitude, for tests. */
internal const val SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG = "search-dropdown-search-coordinates"

/** [SearchDropdown]'s Set on map button, the left of its bottom row, for tests. */
internal const val SEARCH_DROPDOWN_SET_ON_MAP_TAG = "search-dropdown-set-on-map"

/** [SearchDropdown]'s Search button, the right of its bottom row, for tests. */
internal const val SEARCH_DROPDOWN_SEARCH_TAG = "search-dropdown-search"

/** See [SearchDropdown]'s own doc comment. */
internal const val SEARCH_DROPDOWN_TAG = "search-dropdown"

/** See [SearchEntryBar]'s own dismiss-elsewhere scrim doc comment, at its call site. */
internal const val SEARCH_DROPDOWN_SCRIM_TAG = "search-dropdown-scrim"
