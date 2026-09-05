package com.forager.app.ui.availability

// Split-AvailabilityScreen Stage C: the search UI, moved verbatim out of AvailabilityScreen.kt —
// SearchEntryBar, SearchDropdown, SpeciesSearchControls, ActiveSearchSummary, SearchNotice,
// AvailabilitySearchTopBar, SearchControls, RegionControls, RecentSearchesSection, MonthSelector,
// CollapsibleSection, their private helpers (activeSearchSummary, RecentSearchRow,
// TaxonSuggestionContent) and the four internal test-tag constants. Same package as Stage A, for
// the same reason: the seven test files that reach the tags and the log package's own
// CollapsibleSection import all resolve unchanged. Pure move: no signature, name or body changed.
// Six composables went private -> internal because their callers stay in AvailabilityScreen.kt;
// three symbols that stayed there (TripPlannerSection, CompassStripBackgroundColorDark/Light) went
// internal because code here composes them.

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.forager.app.ui.map.MapIconBar
import com.forager.app.ui.map.MapIconStackButtonColorDark
import com.forager.app.ui.map.MapIconStackButtonColorLight
import com.forager.app.ui.theme.Bark
import com.forager.app.ui.theme.LocalForagerDarkTheme
import com.forager.app.ui.theme.Spacing
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale


/**
 * What the screen is currently showing, in one line — "Fungi · August · 8 km". Medium/expanded
 * only now: compact replaced this with [SearchEntryBar], a real entry field, rather than a
 * read-only summary that opens a second one — see that composable's own doc comment. This is what
 * remains for the medium/expanded `mainScaffold`'s own results pane, which already shows
 * [SpeciesSearchControls] directly in its app bar and has no second-summary problem to fix.
 *
 * With the controls behind a drawer the user can no longer read their own filter settings off
 * the screen, so this replaces that. It is deliberately outside the drawer and outside the tab
 * content: it has to be true of both tabs and visible at all times.
 *
 * Also a shortcut back into the species search that produced [AvailabilityUiState.taxonFilter]
 * when that came from a picked result: tapping it calls [onReopenTaxonSuggestions], which
 * restores and re-runs [AvailabilityUiState.lastTaxonSearchQuery] rather than making the user
 * retype it to change species. A no-op tap when nothing was ever searched (the callback itself
 * handles that), so this is unconditionally clickable rather than needing its own enabled state.
 */
@Composable
internal fun ActiveSearchSummary(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onReopenTaxonSuggestions: () -> Unit,
) {
    Surface(
        onClick = onReopenTaxonSuggestions,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Text(
                text = activeSearchSummary(uiState, distanceUnit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Compact's own entire top search bar, replacing the old [ActiveSearchSummary]-opens-
 * [SearchDropdown] pair — map/navigation redesign dispatch D, the project owner's own direct call:
 * "the search bar at the top should be the entry field for searches," with the old read-only
 * summary-that-opens-a-second-text-field removed as the redundant "second search bar" it had
 * become. [SpeciesSearchControls]' own species field is hosted here directly — the same field
 * [SearchDropdown] used to host a second copy of one tap deeper — so typing happens right where the
 * bar already reads as a search field, not behind an extra tap into a nested panel. No chevron: the
 * leading search icon [SpeciesSearchControls] itself doesn't draw, so this bar draws its own, is
 * what the owner call singled out as already making the field read as tappable without one.
 *
 * **Tap-to-focus, dismiss-elsewhere**, not the old tap-to-toggle: focusing the species field
 * ([onFieldFocused], wired at the call site to `showSearchDropdown = true`) opens [SearchDropdown]
 * below it, the same panel as before (recent searches, location, radius, month) — but there is no
 * second tap on this bar that closes it again, since a real, focused text field doesn't
 * conventionally close on a second tap the way a toggle button did. Closing happens by the back
 * button (existing `BackHandler`, unchanged), by picking a result/recent search, or by tapping
 * outside the panel — see that call site's own `SEARCH_DROPDOWN_SCRIM_TAG` doc comment for the
 * last one, a real gap the old toggle model never had to fill (tapping the bar again always worked
 * before; a focused field has no such self-closing gesture).
 *
 * **80% opacity, themed for night/day, with a themed divider along the bottom** — the project
 * owner's own direct ask, once this bar started sitting immediately above [CompassElevationStrip]
 * on the Map tab (previously two fully-opaque, unrelated-looking strips; now two translucent ones
 * that need a real seam between them to read as separate surfaces rather than one blurred one).
 * [MapIconStackButtonColorDark]/[MapIconStackButtonColorLight] reused as-is for the 80% fill — that
 * pair already sits at exactly 0.8 alpha, so no second multiplier is layered on top — and
 * [MAP_ICON_STACK_BORDER_COLOR_DARK]/[MAP_ICON_STACK_BORDER_COLOR_LIGHT] for the [HorizontalDivider],
 * the same hairline-border pair already drawn against that exact fill everywhere else in this map
 * chrome family ([MapIconBar], [ControlPill], [DistanceArm]), rather than a new one-off colour.
 * [LocalForagerDarkTheme], not the app's own system light/dark [MaterialTheme.colorScheme]: this
 * bar sits immediately above map chrome that already keys off that same night/day concept
 * (`isDarkTheme` throughout this file), and reads as one coherent system with it rather than two
 * surfaces following two different theme signals stacked on top of each other.
 */
@Composable
internal fun SearchEntryBar(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onUseCurrentLocation: () -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onFieldFocused: () -> Unit,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val contentColor = if (isDarkTheme) Color.White else Bark
    // Same "Mg" / labelMedium measurement compactMainScaffold's own compassStripClearance uses
    // for the compass strip's own real text-row height (CompassElevationStripContent wraps
    // content with no extra vertical padding of its own) — the owner's own direct ask is this
    // field's box exactly twice that, not a guess at a fixed dp value that would drift the
    // moment either row's typography or density changes.
    val fieldHeightTextMeasurer = rememberTextMeasurer()
    val fieldHeightLabelStyle = MaterialTheme.typography.labelMedium
    val fieldHeightDensity = LocalDensity.current
    val fieldHeight = remember(fieldHeightLabelStyle, fieldHeightDensity) {
        with(fieldHeightDensity) {
            fieldHeightTextMeasurer.measure("Mg", fieldHeightLabelStyle).size.height.toDp() * 2
        }
    }
    // Text stays at Material's own default style (LocalTextStyle.current, not overridden) — the
    // owner's own direct call, after an earlier attempt that shrank the font instead read as
    // "the way it was before" broken: this box is short because OutlinedTextField reserves its
    // own fixed vertical padding around the text line regardless of style, not because the text
    // itself needs to be smaller. contentPadding below is the actual fix.
    val fieldContentPadding = OutlinedTextFieldDefaults.contentPadding(top = 2.dp, bottom = 2.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedTextColor = contentColor,
        unfocusedTextColor = contentColor,
        focusedPlaceholderColor = contentColor,
        unfocusedPlaceholderColor = contentColor,
        cursorColor = contentColor,
    )
    // Surface, not a plain Column + background: on the Map tab this now composes as a real
    // overlay above the map (compactMainScaffold's own call site), so — unlike
    // CompassElevationStripContent, which deliberately stays a plain Box so blank areas let
    // map touches through — this bar needs to consume every tap across its own bounds the way
    // it always implicitly did back when it sat in normal document flow with nothing underneath
    // it to fall through to. A plain Box + background wouldn't: the padding around the icon, the
    // spacer, and the divider have no interactive child of their own to consume a tap there, so
    // it would reach the map beneath. Surface intercepts its full bounds by default — the same
    // established shape MapIconBar's own Surface already relies on for the same reason (see this
    // file's own CLAUDE.md-documented precedent) — so this reuses that rather than hand-rolling a
    // pointerInput consumer. tonalElevation/shadowElevation pinned to 0.dp: this bar's own color
    // is exact (contentColor and MapIconStackButtonColorDark/Light are both already the intended
    // finished tone), not something Material's elevation-tint system should be allowed to touch.
    Surface(
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().testTag(SEARCH_ENTRY_BAR_TAG),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(start = Spacing.lg).size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    SpeciesSearchControls(
                        uiState = uiState,
                        onUseCurrentLocation = onUseCurrentLocation,
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                        queryFieldModifier = Modifier.testTag(ACTIVE_SEARCH_SUMMARY_TAG).height(fieldHeight),
                        onQueryFieldFocusChanged = { focused -> if (focused) onFieldFocused() },
                        restingPlaceholder = activeSearchSummary(uiState, distanceUnit),
                        showLocationTrailingIcon = false,
                        fieldColors = fieldColors,
                        contentPadding = fieldContentPadding,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            HorizontalDivider(color = if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT)
        }
    }
}

/**
 * Map/navigation redesign dispatch C: the compact window's entire search surface, floating over
 * whatever tab content is currently showing (usually the map) from where quick species search used
 * to sit — see [ActiveSearchSummary]'s own doc comment on why that quick panel is gone rather than
 * kept alongside this one. Started as just item 1's "advanced search" (location/radius/month); a
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
 *    opaque [background] already blocks every touch within its bounds from reaching the map
 *    underneath, so once opened it behaves like the drawer's own [SearchControls] sheet, not like
 *    the compass strip — and on the smallest
 *    supported phone (`w360dp-h640dp-xhdpi`), fully expanding Advanced search *and* Enter
 *    coordinates manually genuinely doesn't fit in the space [compactMainScaffold] hands this
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
 * picker. "Use current location" is the existing behaviour, unchanged.
 *
 * Item 3 — manual coordinates, kept, collapsed by default: "Enter coordinates manually" is the
 * label chosen for [CollapsibleSection]'s own header row, reused unmodified rather than a bare
 * chevron nobody would find on a first look, per that item's own explicit ask.
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
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                // Rule 1 above: Box + background, never Surface, over the map.
                .background(
                    color = if (isDarkTheme) CompassStripBackgroundColorDark else CompassStripBackgroundColorLight,
                    shape = RectangleShape,
                )
                .testTag(SEARCH_DROPDOWN_TAG),
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
                // Location row — map/navigation search-UI redo dispatch: "Set on map" and "Use
                // current location" promoted out of "Advanced search" up to the drawer's own top
                // level, same reasoning radius/month already got (a control reached for on nearly
                // every search doesn't belong a tap deeper). Removed from Advanced search entirely,
                // not duplicated — Advanced search now holds only "Enter coordinates manually", the
                // one location path most searches don't need to override. These are actions, not
                // selections: OutlinedButton/Button, not FilterChip, so they never read as members
                // of the category-chip row (now in SearchEntryBar, above this drawer entirely).
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onSetOnMap, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Set on map")
                    }
                    Button(onClick = onUseCurrentLocation, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Use current location")
                    }
                }

                HorizontalDivider()
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
                MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected)

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
                CollapsibleSection(title = "Advanced search") {
                    CollapsibleSection(title = "Enter coordinates manually") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(
                                value = uiState.manualLatText,
                                onValueChange = onManualLatChanged,
                                label = { Text("Latitude") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = uiState.manualLngText,
                                onValueChange = onManualLngChanged,
                                label = { Text("Longitude") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        OutlinedButton(onClick = onSearchManualCoordinates, modifier = Modifier.fillMaxWidth()) {
                            Text("Search this location")
                        }
                    }
                }
            }
        }
    }
}

/** See [SearchDropdown]'s own doc comment. */
internal const val SEARCH_DROPDOWN_TAG = "search-dropdown"

/** See [SearchEntryBar]'s own dismiss-elsewhere scrim doc comment, at its call site. */
internal const val SEARCH_DROPDOWN_SCRIM_TAG = "search-dropdown-scrim"

/** [SearchEntryBar]'s own species query field — a stable trigger regardless of its current text, for tests that don't want to depend on exact query wording. Also the pre-redesign name for [ActiveSearchSummary]'s own clickable row, kept unrenamed so existing tests that open the dropdown through this tag didn't all need retargeting for a purely mechanical rename. */
internal const val ACTIVE_SEARCH_SUMMARY_TAG = "active-search-summary"

/** [SearchEntryBar]'s own outer bounds — the whole bar, chips/field/divider included, for tests that need "where does the top strip end" (e.g. `topStripBottom()`) rather than a click target on the field specifically ([ACTIVE_SEARCH_SUMMARY_TAG]). */
internal const val SEARCH_ENTRY_BAR_TAG = "search-entry-bar"

private fun activeSearchSummary(uiState: AvailabilityUiState, distanceUnit: DistanceUnit): String {
    val month = Month.of(uiState.selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The radius of the search that actually ran, not the slider's pending value: moving the
    // slider doesn't re-run the search, so reporting it here would describe a search that hasn't
    // happened. Before any search there is no region, and this says so rather than implying one.
    val where = uiState.region?.let { formatDistanceKm(it.radiusKm, distanceUnit) } ?: "no location set"
    // Fungi is the only category now (owner decision) — leading with its name on every search
    // would be a label with nothing left to distinguish it from. A specific searched species is
    // still worth naming up front; nothing selected leads with the month instead of a blank
    // segment (a blank search still means "all fungi in this area and month" — see
    // AvailabilityUiState.taxonFilter's default).
    val species = (uiState.taxonFilter as? TaxonFilter.SpecificTaxon)?.label
    return listOfNotNull(species, month, where).joinToString(" · ")
}

/**
 * Failures that come from the drawer's controls or the app bar's search field, surfaced outside
 * both.
 *
 * The coordinate-validation and search-failure messages used to be rendered inside the ranked
 * list, which was the default tab. The Map tab is the default now and the controls that raise
 * these live behind a drawer that closes on search, so without this strip both messages could be
 * raised and never seen (CLAUDE.md: failures are reported, not swallowed). The taxon-search error
 * joined this strip rather than staying inline in [AvailabilitySearchTopBar]: that bar is a fixed
 * two-row sibling above the weighted tab content (see its own doc comment), so an inline error
 * line there would grow the app bar's height exactly when the map's share of the screen is being
 * protected — this strip already exists and already scrolls with nothing beneath it.
 */
@Composable
internal fun SearchNotice(uiState: AvailabilityUiState) {
    val message = uiState.errorMessage
        ?: uiState.taxonSearchErrorMessage
        ?: uiState.plannedTripsErrorMessage
        ?: if (uiState.locationPermissionDenied) {
            "Location permission was denied. Open search options and enter coordinates manually."
        } else {
            null
        }
    if (message == null) return

    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}


/**
 * Everything set far less than once per search, as three independently collapsible sections:
 * **Recent searches** (the offline cache's picker), **Advanced search** (location, radius, month)
 * and **Trip Planner** (rain-driven trip windows plus the planned-trips list). Each is a single
 * tappable header row when collapsed and expands on tap — see [CollapsibleSection] — rather than a
 * flat stack, per the user's own framing of this drawer ("single line until you tap it, then it
 * drops down").
 *
 * Shared by both window classes' drawers, unlike most of this file's compact-vs-medium/expanded
 * split: [AvailabilitySearchTopBar] hosts species/category search above these three sections for
 * medium/expanded, while [CompactToolsDrawerContent] hosts the identical
 * [SpeciesSearchControls] around this same composable for compact — see that
 * composable's own doc comment for why species search moved there instead.
 *
 * The scroll modifier on the outer [Column] is not optional. This is the same tall stack of
 * controls that starved the map when it lived in the main column; a drawer sheet is a
 * fixed-height container too, so without it the later controls would simply be unreachable on a
 * short screen or at a large font scale. Collapsing all sections by default shortens that stack
 * further, but doesn't remove the need for scroll — a large font scale with all sections expanded
 * still needs it.
 *
 * [includeAdvancedSearch] defaults `true` — medium/expanded's own call site doesn't pass it, so its
 * drawer is untouched. Compact passes `false`: map/navigation redesign dispatch C, item 1 moved
 * "Advanced search" (location, radius, month) to [AdvancedSearchDropdown], floating over the map
 * from where quick species search used to sit, so keeping a second copy here would put it back to
 * "two places instead of one" — the exact duplication that move exists to remove.
 */
@Composable
internal fun SearchControls(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    /**
     * [onUseCurrentLocation] through [onMonthSelected] are only read inside the
     * [includeAdvancedSearch]-gated section below (by [RegionControls]/[MonthSelector]) — all
     * default to a no-op since compact's own call site has nothing left to wire them to once that
     * section is excluded.
     */
    onUseCurrentLocation: () -> Unit = {},
    onManualLatChanged: (String) -> Unit = {},
    onManualLngChanged: (String) -> Unit = {},
    onSearchManualCoordinates: () -> Unit = {},
    onRadiusChanged: (Int) -> Unit = {},
    onMonthSelected: (Int) -> Unit = {},
    onDeletePlannedTrip: (String) -> Unit,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit = {},
    currentTime: CurrentTimeProvider,
    includeAdvancedSearch: Boolean = true,
    /**
     * Defaults `true` — medium/expanded's own call site doesn't pass it, so its drawer is
     * untouched. Compact passes `false`: map/navigation redesign dispatch C's own follow-up moved
     * Recent Searches (and species search alongside it) into [SearchDropdown], the same "one place
     * instead of two" reasoning [includeAdvancedSearch] already documents for Advanced Search.
     */
    includeRecentSearches: Boolean = true,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (includeRecentSearches) {
            // First in the column, and a section of its own rather than a control inside "Advanced
            // search". Two reasons, both about what this list is: one tap on an entry here *is* a
            // whole search, so burying it under a header about the individual pieces of a search
            // would put the shortest route to results two taps deeper than the long route; and it
            // is the only control in this drawer that still does something useful with no
            // connection, which is exactly when nobody wants to go hunting for it. It keeps the
            // drawer's established one-line-until-tapped behaviour rather than being the one
            // section that starts expanded.
            CollapsibleSection(title = "Recent searches") {
                RecentSearchesSection(
                    recentSearches = uiState.recentSearches,
                    currentTime = currentTime,
                    distanceUnit = distanceUnit,
                    onRecentSearchSelected = onRecentSearchSelected,
                )
            }
        }
        if (includeAdvancedSearch) {
            if (includeRecentSearches) HorizontalDivider()
            CollapsibleSection(title = "Advanced search") {
                RegionControls(
                    uiState = uiState,
                    distanceUnit = distanceUnit,
                    onUseCurrentLocation = onUseCurrentLocation,
                    onManualLatChanged = onManualLatChanged,
                    onManualLngChanged = onManualLngChanged,
                    onSearchManualCoordinates = onSearchManualCoordinates,
                    onRadiusChanged = onRadiusChanged,
                )
                HorizontalDivider()
                MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected)
            }
        }
        if (includeRecentSearches || includeAdvancedSearch) HorizontalDivider()
        CollapsibleSection(title = "Trip Planner") {
            TripPlannerSection(uiState = uiState, onDeletePlannedTrip = onDeletePlannedTrip)
        }
    }
}

/**
 * A single tappable header row that expands on tap to reveal [content], collapsing back on a
 * second tap. Collapsed by default: both drawer sections start as one line, per the user's
 * request, rather than remembering whichever state they were last left in.
 *
 * Lives in this file rather than becoming a generic shared component, because both call sites
 * (see [SearchControls]) are this drawer's two sections and nothing else in the app has asked for
 * this pattern yet — CLAUDE.md: no speculative generality ahead of a second real caller.
 */
@Composable
// internal rather than private: com.forager.app.ui.log's own drawer panel reuses this exact
// "tap header to expand" shape for its record sections, rather than re-implementing the same
// molecule a second time.
internal fun CollapsibleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                content()
            }
        }
    }
}

/**
 * The offline cache's recent searches, most recently used first, each re-runnable in one tap.
 *
 * The list is short by construction — the cache keeps five (see
 * [RoomSearchCacheRepository][com.forager.app.data.repository.RoomSearchCacheRepository]) — so
 * these are plain [Column] children inside the drawer's existing scroll rather than a nested
 * [LazyColumn], which inside a scrolling parent would need a height of its own and would scroll
 * independently of everything around it.
 *
 * Tapping an entry does **not** mean "show me the saved copy": it re-runs the search through the
 * ordinary flow, which tries live first — see
 * [AvailabilityViewModel.onRecentSearchSelected][com.forager.app.ui.availability.AvailabilityViewModel.onRecentSearchSelected].
 *
 * An empty list says so rather than rendering nothing, the same way [PlannedTripsList] does: a
 * section that expands to blank space is indistinguishable from one that failed to load.
 */
@Composable
private fun RecentSearchesSection(
    recentSearches: List<CachedSearchSummary>,
    currentTime: CurrentTimeProvider,
    distanceUnit: DistanceUnit,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (recentSearches.isEmpty()) {
            Text(
                "No searches saved yet. Each search you run is saved here, and the last five can " +
                    "be reopened without a connection.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            // Read once for the whole list rather than per row, so every "cached ..." label in one
            // rendering is measured from the same instant.
            val now = currentTime.nowEpochMillis()
            recentSearches.forEach { summary ->
                RecentSearchRow(
                    summary = summary,
                    nowEpochMillis = now,
                    distanceUnit = distanceUnit,
                    onClick = { onRecentSearchSelected(summary) },
                )
            }
        }
    }
}

/** One recent search: what was searched for, where and when, and how old the saved copy is. */
@Composable
private fun RecentSearchRow(
    summary: CachedSearchSummary,
    nowEpochMillis: Long,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The whole card is the target, not a button inside it: the row is one action, and
                // a small control in a card the user has already aimed at is a smaller target for
                // no reason — the same widening [DrawerHeader] does for its close affordance.
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val month = Month.of(summary.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
            Text("${summary.filter.label} · $month", style = MaterialTheme.typography.titleSmall)
            Text(
                "${"%.4f".format(summary.region.lat)}, ${"%.4f".format(summary.region.lng)} · " +
                    formatDistanceKm(summary.region.radiusKm, distanceUnit),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "cached ${relativeTimeLabel(summary.cachedAtEpochMillis, nowEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RegionControls(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onUseCurrentLocation: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(onClick = onUseCurrentLocation, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Spacing.sm))
            Text("Use current location")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = uiState.manualLatText,
                onValueChange = onManualLatChanged,
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.manualLngText,
                onValueChange = onManualLngChanged,
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        OutlinedButton(onClick = onSearchManualCoordinates, modifier = Modifier.fillMaxWidth()) {
            Text("Search this location")
        }

        Text("Search radius: ${formatDistanceKm(uiState.radiusKm, distanceUnit)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = uiState.radiusKm.toFloat(),
            onValueChange = { onRadiusChanged(it.toInt()) },
            valueRange = 1f..50f,
            steps = 48,
        )
    }
}

/**
 * The app bar: species/category search in place of a static "Forager" title, because it's the
 * control used on nearly every search rather than once a session — see [AvailabilityScreen]'s doc
 * comment. This was the most-buried control in the previous layout, at the same depth as location
 * and radius; putting it in the app bar itself is one step further than the prior promotion out
 * of the drawer, per the user's explicit request.
 *
 * **A deliberate two-row bar, not Material3's single-row [androidx.compose.material3.TopAppBar].**
 * That component's title slot is sized for a line of text; fitting category chips *and* a text
 * field into it would either clip one of them or force the chips and the field onto the same row,
 * where neither has enough width to be usable next to a navigation icon. A taller, custom bar is
 * the defensible choice here — flagged rather than picked silently, per CLAUDE.md — and its cost
 * is a fixed, known quantity: two rows have a fixed height regardless of query length or category
 * count, so [AvailabilityScreenLayoutTest]'s `MIN_MAP_SHARE_OF_SCREEN` floor is a one-time,
 * re-measurable cost rather than one that grows with what the user types.
 *
 * The category row scrolls rather than wraps for the same reason it did inside the old
 * [Row]-in-a-drawer version: "Lichens (approx.)" is long enough to threaten clipping in a
 * non-scrolling row on a narrow phone, and scrolling keeps the row's height fixed at one chip
 * tall regardless of device width or category count.
 *
 * The suggestion list is the one part of the old species bar whose height wasn't fixed by its own
 * content — it grew with however many matches the search returned, which is exactly the kind of
 * wrap-content sibling this bar cannot afford to have. [ExposedDropdownMenuBox] (the same
 * mechanism [MonthSelector] already uses) renders it as a popup anchored below the text field
 * instead of a layout child, so a long result list draws over the map rather than pushing this
 * bar — and everything below it — taller.
 *
 * The location icon is the trailing icon of the species text field, at the field's far right —
 * a shortcut to the same "Use current location" action the drawer's [RegionControls] already has
 * ([onUseCurrentLocation] is the identical callback, not a second location-fetch path), placed
 * here because location is, with species/category, the other control reached for on nearly every
 * search. It uses [Icons.Filled.MyLocation] rather than the drawer button's
 * [Icons.Filled.LocationOn] so the two don't read as the same icon in two places. It used to sit
 * at the end of the category-chip row instead, which crowded that row's scrollable chips against
 * a fixed icon at a variable-width boundary; the text field's trailing-icon slot is a stable,
 * purpose-built spot for exactly this kind of field-scoped action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvailabilitySearchTopBar(
    uiState: AvailabilityUiState,
    onOpenDrawer: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Tune, contentDescription = "Advanced search options")
                }
                Box(modifier = Modifier.weight(1f)) {
                    SpeciesSearchControls(
                        uiState = uiState,
                        onUseCurrentLocation = onUseCurrentLocation,
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                    )
                }
            }
        }
    }
}

/**
 * The species search controls themselves — the species text field and its suggestion dropdown —
 * factored out of [AvailabilitySearchTopBar] so [CompactToolsDrawerContent] can host the identical
 * control inside the drawer instead of the app bar (per the project owner's own framing: "the
 * whole side panel is the search feature"), rather than a second copy of the
 * [ExposedDropdownMenuBox] logic. [AvailabilitySearchTopBar]'s own external shape (the Surface,
 * the tune icon, the two-row layout) is unchanged by this extraction — only where the field piece
 * itself is called from moved.
 *
 * The category chip row this composable used to render alongside the field is gone (owner
 * decision): the app is fungi-only now, so there is nothing left to choose between. See
 * [AvailabilityUiState.taxonFilter]'s default and [activeSearchSummary].
 *
 * [queryFieldModifier]/[onQueryFieldFocusChanged]: both default to no-ops, exercised only by
 * [SearchEntryBar] — the compact top bar now hosts this composable's own species field directly
 * (map/navigation redesign dispatch D's own "the top bar should be the entry field" call; see that
 * composable's own doc comment), so it needs a stable [testTag] on the real field to tap/focus, and
 * a way to know when that focus changes so it can drive [SearchDropdown]'s own visibility. Neither
 * [AvailabilitySearchTopBar] nor [CompactToolsDrawerContent] pass either — their own species field
 * is not a trigger for anything else, so the defaults leave them unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesSearchControls(
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    queryFieldModifier: Modifier = Modifier,
    onQueryFieldFocusChanged: (Boolean) -> Unit = {},
    /**
     * Placeholder text shown while the field is empty, focused or not — map/navigation search-UI
     * redo dispatch, the owner's own direct call: the bar reads as the current filter summary
     * ("August · 9 mi", or a searched species' name ahead of it) always, not a generic hint that
     * blanks out what's currently searched the moment someone taps in to search. The generic hint
     * this used to swap to on focus (and the two other call sites, [AvailabilitySearchTopBar] and
     * [CompactToolsDrawerContent], used to default to outright) is gone from the app entirely, by
     * direct owner instruction — those two call sites now default to a blank placeholder instead
     * of reintroducing it.
     */
    restingPlaceholder: String = "",
    showLocationTrailingIcon: Boolean = true,
    fieldColors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    /**
     * Text style for the entered/placeholder text — map/navigation search-UI redo dispatch:
     * [SearchEntryBar] pins its field to a short, fixed height (twice the compass strip's own
     * measured row height), and the default [OutlinedTextField] text style (`bodyLarge`, sized for
     * a full ~56dp Material field) doesn't fit inside it — the text was clipped away entirely, not
     * merely cramped. The owner's own direct call: scale the text down to fit the box, rather than
     * the box up to fit default-sized text. The other two call sites keep Material's own default
     * ([LocalTextStyle.current]), unchanged.
     */
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    /**
     * Vertical (and horizontal) padding inside the field's own box — map/navigation search-UI
     * redo dispatch: shrinking [textStyle] alone wasn't enough to fit [SearchEntryBar]'s pinned
     * field height; [OutlinedTextField] reserves its own fixed padding around the text line
     * regardless of that style, so even a correctly-sized line was still being clipped by the box
     * itself. This is the owner's own direct follow-up call — reduce the padding, not the text
     * further. Implemented via the low-level [BasicTextField] + [OutlinedTextFieldDefaults.DecorationBox]
     * pair (the only Material3 path that exposes content padding at all) for every call site, but
     * defaulted to [OutlinedTextFieldDefaults.contentPadding] — Material's own stock value — so
     * the two call sites that don't override it render identically to before this parameter
     * existed.
     */
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val suggestionsOpen = uiState.taxonSearchResults.isNotEmpty() || uiState.taxonSearchHasNoResults
        val queryFieldInteractionSource = remember { MutableInteractionSource() }
        ExposedDropdownMenuBox(
            expanded = suggestionsOpen,
            onExpandedChange = {},
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            // BasicTextField + OutlinedTextFieldDefaults.DecorationBox, not the plain
            // OutlinedTextField composable — see [contentPadding]'s own doc comment for why: it's
            // the only Material3 path that exposes the field's internal padding at all.
            BasicTextField(
                value = uiState.taxonSearchQuery,
                onValueChange = onTaxonSearchQueryChanged,
                textStyle = textStyle.copy(color = fieldColors.focusedTextColor),
                singleLine = true,
                cursorBrush = SolidColor(fieldColors.cursorColor(isError = false)),
                interactionSource = queryFieldInteractionSource,
                modifier = queryFieldModifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .onFocusChanged {
                        onQueryFieldFocusChanged(it.isFocused)
                    },
            ) { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = uiState.taxonSearchQuery,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = queryFieldInteractionSource,
                    placeholder = {
                        // The filter summary ("Fungi · August · 9 mi"), not a generic hint,
                        // whether focused or not — the owner's own direct call: focusing the
                        // field to search shouldn't blank out what's currently searched, only
                        // typing should.
                        Text(
                            restingPlaceholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = textStyle,
                        )
                    },
                    trailingIcon = if (uiState.isSearchingTaxa || showLocationTrailingIcon) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.isSearchingTaxa) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                                if (showLocationTrailingIcon) {
                                    IconButton(onClick = onUseCurrentLocation) {
                                        Icon(Icons.Filled.MyLocation, contentDescription = "Use current location")
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    },
                    colors = fieldColors,
                    contentPadding = contentPadding,
                )
            }
            // A no-op onDismissRequest here before this fix meant the standard "tap outside
            // the popup" dismissal ExposedDropdownMenu already implements never actually
            // closed anything — the only way to get rid of the list was to pick a result or
            // clear the query back below MIN_QUERY_LENGTH. Wiring the real dismiss action in
            // is the fix, not new behavior invented on top of the component.
            ExposedDropdownMenu(expanded = suggestionsOpen, onDismissRequest = onDismissTaxonSuggestions) {
                if (uiState.taxonSearchResults.isEmpty() && uiState.taxonSearchHasNoResults) {
                    DropdownMenuItem(
                        text = { Text("No matches for “${uiState.taxonSearchQuery.trim()}”") },
                        onClick = {},
                        enabled = false,
                    )
                } else {
                    uiState.taxonSearchResults.forEach { result ->
                        DropdownMenuItem(
                            text = { TaxonSuggestionContent(result) },
                            onClick = { onTaxonSearchResultSelected(result) },
                        )
                    }
                }
            }
        }
    }
}

/** One suggestion's content inside a [DropdownMenuItem], which supplies the click and padding. */
@Composable
private fun TaxonSuggestionContent(result: TaxonSearchResult) {
    Column {
        Text(result.commonName ?: result.scientificName, style = MaterialTheme.typography.bodyMedium)
        val subtitle = result.scientificName + (result.iconicTaxonName?.let { " · $it" } ?: "")
        Text(subtitle, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSelector(selectedMonth: Int, onMonthSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val monthName = Month.of(selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = monthName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Month") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..12).forEach { month ->
                DropdownMenuItem(
                    text = { Text(Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                    onClick = {
                        onMonthSelected(month)
                        expanded = false
                    },
                )
            }
        }
    }
}

