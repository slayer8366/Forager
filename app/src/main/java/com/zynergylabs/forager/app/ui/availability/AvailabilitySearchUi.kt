package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage C: the search UI, moved verbatim out of AvailabilityScreen.kt —
// SearchEntryBar, SearchDropdown, SpeciesSearchControls, SearchNotice,
// SearchControls, RecentSearchesSection, MonthSelector,
// CollapsibleSection, their private helpers (activeSearchSummary, RecentSearchRow,
// TaxonSuggestionContent) and the four internal test-tag constants. Same package as Stage A, for
// the same reason: the seven test files that reach the tags and the log package's own
// CollapsibleSection import all resolve unchanged. Pure move: no signature, name or body changed.
// Six composables went private -> internal because their callers stay in AvailabilityScreen.kt;
// three symbols that stayed there (TripPlannerSection, CompassStripBackgroundColorDark/Light) went
// internal because code here composes them.
// UI split Phase 1, PR 10a (dispatch 2026-10-19-17): SearchEntryBar, its four tags and the pill sizes stay here; the rest moved, unchanged, to SearchSummaryText.kt, SearchDropdown.kt, SearchNotice.kt, SearchControls.kt, SearchDropdownSections.kt and SpeciesSearchField.kt.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.MapPillColorDark
import com.zynergylabs.forager.app.ui.map.MapPillColorLight
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.mapChromeEdgesReported
import com.zynergylabs.forager.app.ui.map.mapChromeShape
import com.zynergylabs.forager.app.ui.map.mapChromeOutline
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.theme.mapChromeContentColor
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing


/**
 * The top search bar, replacing the old read-only summary that opened [SearchDropdown] — map/navigation
 * redesign dispatch D, the project owner's own direct call:
 * "the search bar at the top should be the entry field for searches," with the old read-only
 * summary-that-opens-a-second-text-field removed as the redundant "second search bar"
 * it had become. (That summary, `ActiveSearchSummary`, survived on the tablet's results pane until the
 * tablet tree was removed, dispatch 2026-09-28-245.) [SpeciesSearchControls]' own species field is hosted here directly — the same field
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
 * chrome family ([MapIconBar], [ControlPill]), rather than a new one-off colour.
 * [LocalForagerDarkTheme], not the app's own system light/dark [MaterialTheme.colorScheme]: this
 * bar sits immediately above map chrome that already keys off that same night/day concept
 * (`isDarkTheme` throughout this file), and reads as one coherent system with it rather than two
 * surfaces following two different theme signals stacked on top of each other.
 */
@Composable
internal fun SearchEntryBar(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onFieldFocused: () -> Unit,
    /** RECORD -723: the Clear text button at the bar's right end, shown only while a search is showing. */
    onClearSearch: () -> Unit = {},
    /** Whether this bar is the Maps tab's, over its map: its species suggestions are then at the map chrome's alpha (owner, "1 A"). */
    overMap: Boolean = false,
    /**
     * RECORD -729: in a short landscape window, not navigating, the compass strip's measured height, which this bar takes so
     * the two meet at one height (the owner: "The search bar height can change to meet the height of the strip"). The field is
     * centred in what is above the divider. Since RECORD -736 the strip keeps its portrait height at every font, so at a large
     * font the field and Clear are shorter than twice the line (rowFieldHeight). `null` keeps the bar's own height (portrait, the
     * other tabs, navigating, and before the strip is first measured).
     */
    landscapeHeight: Dp? = null,
    /**
     * Dispatch 2026-10-12-06 (RECORD -932, -934): over the map the bar is the rounded pill the navigation card and the compass
     * strip tuck under (see RoundedMapCard.kt): these corners, a 2 dp shadow, and no divider (the divider was the seam against
     * the square strip; the pill's own edge is the seam now, and a 1 dp spacer keeps the bar's height). `null`, the other tabs,
     * keeps the square bar with its divider, unchanged (RECORD -934: "the pill is over the map only").
     * Superseded for List, Seasonal and Journal by dispatch 2026-10-17-09 (RECORD -1224, -1225): they pass the pill too, solid
     * ([overMap] false), so `null` is now no caller's choice; the square bar is kept as a path, not removed.
     */
    pillCorners: MapCardCorners? = null,
    /**
     * Dispatch 2026-10-13-02 (RECORD -966, -967): with [pillCorners], the pill's sides that carry the map chrome outline (the icon
     * bar's, [mapChromeOutline]), and every rounded corner. The pill draws its join with the compass strip, the one line between
     * them: its bottom in portrait, where the strip tucks under it, and its inner side in landscape. `null` draws none.
     */
    pillOutlineSides: MapChromeOutlineSides? = null,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val contentColor = mapChromeContentColor(isDarkTheme)
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
    val fieldContentPadding = OutlinedTextFieldDefaults.contentPaddingWithoutLabel(top = 2.dp, bottom = 2.dp)
    // RECORD -736 (the owner: "Yes for landscape I'm willing to accept a shorter search bar. The compass strip must remain the
    // same height as portrait"): with the strip's height, the field and Clear are at most what is above the divider, so at a
    // large font they are shorter than twice the line rather than the strip growing to them. Unchanged at font 1.0 (32 dp in 35).
    val rowFieldHeight = landscapeHeight?.let { minOf(fieldHeight, (it - DividerDefaults.Thickness).coerceAtLeast(0.dp)) } ?: fieldHeight
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
    // Dispatch 2026-10-12-08 (RECORD -945, -951): over the map the pill is a shade lighter than the strip and card tucked under
    // it (MapPillColorLight / MapPillColorDark, still at 0.8); the square bar on the other tabs keeps the chrome token.
    // Dispatch 2026-10-17-09 (RECORD -1224, -1225): the pill is on the other tabs too, solid there; mapChromeFill gives the role
    // at MAP_CHROME_OVER_MAP_ALPHA over the map (the Maps tab, unchanged: MapPillColorDark/Light are that role at 0.8) and the
    // role itself, solid, where no map is behind it.
    val barFill = when {
        pillCorners == null -> if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
        else -> mapChromeFill((if (isDarkTheme) MapPillColorDark else MapPillColorLight).copy(alpha = 1f), overMap)
    }
    val barShape = pillCorners?.shape() ?: RectangleShape
    Surface(
        color = barFill,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = if (pillCorners != null) MAP_CARD_SHADOW_ELEVATION else 0.dp,
        shape = barShape,
        modifier = Modifier.fillMaxWidth().testTag(SEARCH_ENTRY_BAR_TAG).mapChromeContainerColor(barFill).mapChromeShape(barShape).then(
            if (pillCorners != null && pillOutlineSides != null) {
                Modifier.mapChromeEdgesReported(
                    mapChromeOutline(), pillCorners.topLeft, pillCorners.topRight, pillCorners.bottomRight, pillCorners.bottomLeft, pillOutlineSides,
                )
            } else {
                Modifier
            },
        ),
    ) {
        // RECORD -728 (the owner: "There is a slightly hang down of chrome from the search menu that extends into the strip
        // zone, making it look taller than it is"): the bottom padding sat below the divider, so 4 dp of this bar's fill hung
        // under its seam, against the strip. The padding is now above the divider (the Spacer below is 2 x xs), so the divider
        // is the bar's last 1 dp and the strip starts at it. The bar's height is unchanged (compactMainScaffold's searchBarHeight).
        Column(
            modifier = if (landscapeHeight != null) {
                Modifier.fillMaxWidth().height(landscapeHeight)
            } else if (pillCorners != null) {
                // RECORD -943 (the owner: "The search panel has a very thick margin and it can afford to thin out"): the pill's
                // padding above and below its field is thinner, MAP_PILL_TOP_PADDING and MAP_PILL_BOTTOM_PADDING in place of 4 dp
                // and 8 dp, so the pill is 40 dp tall at font 1.0 where the bar was 45. RECORD -951: never under the planner's
                // 40 dp floor (MAP_PILL_MIN_HEIGHT), which at a font scale under 1.0 the field's twice-a-line height would cross;
                // the field then centres in it. The scaffold's mapPillHeight takes the same floor.
                Modifier.fillMaxWidth().heightIn(min = MAP_PILL_MIN_HEIGHT).padding(top = MAP_PILL_TOP_PADDING)
            } else {
                Modifier.fillMaxWidth().padding(top = Spacing.xs)
            },
            // RECORD -951: where the pill's floor is taller than its content (a font scale under 1.0), the field centres in it.
            verticalArrangement = if (landscapeHeight == null && pillCorners != null) Arrangement.Center else Arrangement.Top,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // RECORD -729: with the strip's height, the field row takes what is above the divider and centres in it.
                modifier = if (landscapeHeight != null) Modifier.weight(1f) else Modifier,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(start = Spacing.lg).size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    SpeciesSearchControls(
                        uiState = uiState,
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                        queryFieldModifier = Modifier.testTag(ACTIVE_SEARCH_SUMMARY_TAG).height(rowFieldHeight),
                        onQueryFieldFocusChanged = { focused -> if (focused) onFieldFocused() },
                        restingPlaceholder = activeSearchSummary(uiState, distanceUnit),
                        fieldColors = fieldColors,
                        contentPadding = fieldContentPadding,
                        suggestionsOverMap = overMap,
                    )
                }
                // RECORD -723: "a 'Clear' text button at the right end of the search bar, shown only while a search is
                // showing" (a region is set, which is what the summary reads as a search). It removes the search and its
                // results (AvailabilityViewModel.clearSearch); the summary goes back to "Search a location". A sibling
                // of the field, not its trailing icon, so a tap on it never focuses the field or opens the dropdown.
                //
                // Exactly the field's height, with no 48 dp minimum touch target: the bar's height is the owner's direct
                // ask (twice the compass row, see fieldHeight above), and a 48 dp Clear grew the bar from 45 to 61 dp while a
                // search showed, which moved the landscape L and cluster (LandscapeLRulingsTest and others, full suite at
                // the build). The target is therefore the field's height tall and the button's width wide (reported).
                if (uiState.region != null) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        TextButton(
                            onClick = onClearSearch,
                            contentPadding = PaddingValues(horizontal = Spacing.md),
                            // RECORD -728, the owner: "Keep 29 dp tall, wide (Recommended)": the whole word and its padding, at least 48 dp.
                            modifier = Modifier.padding(end = Spacing.xs).height(rowFieldHeight).widthIn(min = 48.dp).testTag(SEARCH_BAR_CLEAR_TAG),
                        ) {
                            Text("Clear", color = contentColor)
                        }
                    }
                }
            }
            if (landscapeHeight == null) Spacer(Modifier.height(if (pillCorners != null) MAP_PILL_BOTTOM_PADDING else Spacing.xs * 2))
            if (pillCorners != null) {
                // The pill has no divider; this keeps the bar the height the scaffold reckons it (searchBarHeight), so the
                // card tucked under it and everything placed below it stay where they are measured to be.
                Spacer(Modifier.height(DividerDefaults.Thickness))
            } else {
                HorizontalDivider(
                    color = if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT,
                    modifier = Modifier.testTag(SEARCH_ENTRY_BAR_DIVIDER_TAG),
                )
            }
        }
    }
}

/** [SearchEntryBar]'s divider, its visible bottom edge (RECORD -728), for tests. */
internal const val SEARCH_ENTRY_BAR_DIVIDER_TAG = "search-entry-bar-divider"

/** [SearchEntryBar]'s Clear text button (RECORD -723), for tests. */
internal const val SEARCH_BAR_CLEAR_TAG = "search-bar-clear"

/** [SearchEntryBar]'s own species query field — a stable trigger regardless of its current text, for tests that don't want to depend on exact query wording. Also the pre-redesign name for the old `ActiveSearchSummary`'s clickable row, kept unrenamed so existing tests that open the dropdown through this tag didn't all need retargeting for a purely mechanical rename. */
internal const val ACTIVE_SEARCH_SUMMARY_TAG = "active-search-summary"

/** [SearchEntryBar]'s own outer bounds — the whole bar, chips/field/divider included, for tests that need "where does the top strip end" (e.g. `topStripBottom()`) rather than a click target on the field specifically ([ACTIVE_SEARCH_SUMMARY_TAG]). */
internal const val SEARCH_ENTRY_BAR_TAG = "search-entry-bar"

/** RECORD -943: the Maps tab's search pill's padding above its field (the bar elsewhere keeps Spacing.xs). */
internal val MAP_PILL_TOP_PADDING = 3.dp

/** RECORD -943: the pill's padding below its field, before its 1 dp spacer (the bar elsewhere keeps twice Spacing.xs). */
internal val MAP_PILL_BOTTOM_PADDING = 4.dp

/** RECORD -943's floor, enforced by RECORD -951: the Maps tab's pill is never under 40 dp tall, so it stays easy to tap. */
internal val MAP_PILL_MIN_HEIGHT = 40.dp
