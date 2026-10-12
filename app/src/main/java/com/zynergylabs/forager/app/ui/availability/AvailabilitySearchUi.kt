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

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
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
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.mapChromeContentColor
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale


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
     */
    pillCorners: MapCardCorners? = null,
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
    val fieldContentPadding = OutlinedTextFieldDefaults.contentPadding(top = 2.dp, bottom = 2.dp)
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
    Surface(
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = if (pillCorners != null) MAP_CARD_SHADOW_ELEVATION else 0.dp,
        shape = pillCorners?.shape() ?: RectangleShape,
        modifier = Modifier.fillMaxWidth().testTag(SEARCH_ENTRY_BAR_TAG).mapChromeContainerColor(if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight),
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
                // and 8 dp, so the pill is 41 dp tall at font 1.0 where the bar was 45 (the planner's floor: 40).
                Modifier.fillMaxWidth().padding(top = MAP_PILL_TOP_PADDING)
            } else {
                Modifier.fillMaxWidth().padding(top = Spacing.xs)
            },
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

/**
 * RECORD -732: the landscape search bar's floor, in pixels: never narrower than its field and Clear need. Measured as the bar
 * lays them out: the search icon's start padding (Spacing.lg) and 18 dp, the field's own content padding (16 dp each side,
 * OutlinedTextFieldDefaults), its resting text (the search summary, in the field's text style, bodyLarge) whole, and, while a
 * search shows, Clear (its word and Spacing.md each side, at least Material's 58 dp button width, then Spacing.xs). The strip
 * narrows before the bar goes under this (landscapeStripFit), down to its coordinates.
 */
@Composable
internal fun rememberLandscapeBarFloorPx(uiState: AvailabilityUiState, distanceUnit: DistanceUnit): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fieldStyle = MaterialTheme.typography.bodyLarge
    val clearStyle = MaterialTheme.typography.labelLarge
    fun widthOf(text: String, style: androidx.compose.ui.text.TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    return with(density) {
        val field = Spacing.lg.roundToPx() + 18.dp.roundToPx() + 2 * LANDSCAPE_BAR_FIELD_SIDE_PADDING.roundToPx() + widthOf(activeSearchSummary(uiState, distanceUnit), fieldStyle)
        val clear = if (uiState.region != null) {
            maxOf(LANDSCAPE_BAR_CLEAR_MIN_WIDTH.roundToPx(), widthOf("Clear", clearStyle) + 2 * Spacing.md.roundToPx()) + Spacing.xs.roundToPx()
        } else {
            0
        }
        field + clear
    }
}

/** OutlinedTextFieldDefaults' horizontal content padding, which the bar's field keeps (it sets only top and bottom). */
private val LANDSCAPE_BAR_FIELD_SIDE_PADDING = 16.dp

/** Material3's minimum button width (ButtonDefaults.MinWidth), which Clear's TextButton keeps. */
private val LANDSCAPE_BAR_CLEAR_MIN_WIDTH = 58.dp

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
    CompositionLocalProvider(LocalContentColor provides mapChromeContentColor(isDarkTheme)) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                // Rule 1 above: Box + background, never Surface, over the map.
                .background(
                    color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                    shape = RectangleShape,
                )
                .testTag(SEARCH_DROPDOWN_TAG)
                .mapChromeContainerColor(if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight),
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

/** [SearchEntryBar]'s divider, its visible bottom edge (RECORD -728), for tests. */
internal const val SEARCH_ENTRY_BAR_DIVIDER_TAG = "search-entry-bar-divider"

/** [SearchEntryBar]'s Clear text button (RECORD -723), for tests. */
internal const val SEARCH_BAR_CLEAR_TAG = "search-bar-clear"

/** See [SearchDropdown]'s own doc comment. */
internal const val SEARCH_DROPDOWN_TAG = "search-dropdown"

/** See [SearchEntryBar]'s own dismiss-elsewhere scrim doc comment, at its call site. */
internal const val SEARCH_DROPDOWN_SCRIM_TAG = "search-dropdown-scrim"

/** [SearchEntryBar]'s own species query field — a stable trigger regardless of its current text, for tests that don't want to depend on exact query wording. Also the pre-redesign name for the old `ActiveSearchSummary`'s clickable row, kept unrenamed so existing tests that open the dropdown through this tag didn't all need retargeting for a purely mechanical rename. */
internal const val ACTIVE_SEARCH_SUMMARY_TAG = "active-search-summary"

/** [SearchEntryBar]'s own outer bounds — the whole bar, chips/field/divider included, for tests that need "where does the top strip end" (e.g. `topStripBottom()`) rather than a click target on the field specifically ([ACTIVE_SEARCH_SUMMARY_TAG]). */
internal const val SEARCH_ENTRY_BAR_TAG = "search-entry-bar"

private fun activeSearchSummary(uiState: AvailabilityUiState, distanceUnit: DistanceUnit): String {
    val month = Month.of(uiState.selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The radius of the search that actually ran, not the slider's pending value: moving the
    // slider doesn't re-run the search, so reporting it here would describe a search that hasn't
    // happened. Before any search there is no region, and this says so rather than implying one.
    // Before any search there is no region, and this says so as what the user can do about it
    // (owner, 2026-09-28: "Change it to 'September · Search a location'", so the line gives "a spark
    // or motivation to action"). Tapping it focuses
    // SearchEntryBar's field, which opens SearchDropdown.
    val where = uiState.region?.let { formatDistanceKm(it.radiusKm, distanceUnit) } ?: "Search a location"
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
 * joined this strip rather than staying inline in the old `AvailabilitySearchTopBar`: that bar is a fixed
 * two-row sibling above the weighted tab content (see its own doc comment), so an inline error
 * line there would grow the app bar's height exactly when the map's share of the screen is being
 * protected — this strip already exists and already scrolls with nothing beneath it.
 */
@Composable
internal fun SearchNotice(
    uiState: AvailabilityUiState,
    /** Whether a map is drawn beneath the banner: the Maps tab's own, inside its map's Box. Map chrome at 80%. */
    overMap: Boolean = false,
) {
    val message = searchNoticeMessage(uiState) ?: return

    // The fill and its content colour, pinned to the fill's own role (`contentColorFor` matches a
    // colour-scheme role exactly; see `MapLayersSheet`). The text keeps its own explicit colour.
    val noticeColor = mapChromeFill(MaterialTheme.colorScheme.errorContainer, overMap)
    val noticeContentColor = contentColorFor(MaterialTheme.colorScheme.errorContainer)
    Surface(
        color = noticeColor,
        contentColor = noticeContentColor,
        modifier = Modifier
            .absolutePadding(left = LocalSearchNoticeInset.current.left, right = LocalSearchNoticeInset.current.right)
            .testTag(SEARCH_NOTICE_TAG)
            .mapChromeContainerColor(noticeColor),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .mapChromeContentColor(LocalContentColor.current)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

/**
 * Room [SearchNotice] leaves at its left and right ends. The landscape L's map tab provides the L's side and width here (owner's
 * ruling (b), continuation 2026-09-28-172): the notice and the L must not overlap, and a composition local reaches the notice inside
 * the search slot without widening the slot's signature. [None] everywhere else, so every other notice is as it was.
 */
internal data class SearchNoticeInset(val left: Dp = 0.dp, val right: Dp = 0.dp) {
    companion object {
        val None = SearchNoticeInset()
    }
}

internal val LocalSearchNoticeInset = compositionLocalOf { SearchNoticeInset.None }

/**
 * The text [SearchNotice] shows, or `null` when it shows nothing. One definition for the banner and for the Maps tab's layout, which
 * places the banner below the compass strip and keeps the icon cluster below it only while it shows (dispatch 2026-09-28-104, item 2).
 */
internal fun searchNoticeMessage(uiState: AvailabilityUiState): String? =
    uiState.errorMessage
        ?: uiState.taxonSearchErrorMessage
        ?: uiState.plannedTripsErrorMessage
        ?: if (uiState.locationPermissionDenied) {
            // RECORD -700, the owner: "Option 1, but only refer to "Set on map" since that is right there."
            LOCATION_PERMISSION_DENIED_MESSAGE
        } else {
            null
        }

/** The banner when location permission is denied; the owner's wording, RECORD -700. */
internal const val LOCATION_PERMISSION_DENIED_MESSAGE = "Location permission was denied. Tap Set on map to choose a place."

/** [SearchNotice]'s banner, for tests. */
internal const val SEARCH_NOTICE_TAG = "search-notice"


/**
 * The Tools drawer's search section: the Trip Planner (the one thing it still holds).
 * [CompactToolsDrawerContent] hosts it. Species search and Recent Searches live in [SearchDropdown],
 * and "Advanced search" (location, radius, month) in [AdvancedSearchDropdown], both over the map, so
 * none of them is repeated here.
 *
 * Until dispatch 2026-09-28-245 the tablet's permanent drawer hosted the other two sections here
 * through [includeAdvancedSearch] and [includeRecentSearches] flags, defaulted on for it; with the
 * tablet tree removed both flags, and the parameters that only they read, are gone.
 *
 * The scroll modifier on the outer [Column] is not optional: a drawer sheet is a fixed-height
 * container, so without it the section would be unreachable on a short screen or at a large font
 * scale.
 */
@Composable
internal fun SearchControls(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    onDeletePlannedTrip: (String) -> Unit,
    /**
     * Drawn under the Trip Planner's header, in the same scroll: the Tools page's Sundown section
     * (dispatch 2026-09-28-707). A slot rather than a second scrolling column, because a scroll nested
     * in a scroll on the same axis is measured with infinite height and cannot lay out. Under the
     * Trip Planner rather than above it so the drawer still opens with the Trip Planner's one-line
     * header at its top, which six test classes read as "the drawer is open"; the Trip Planner
     * starts collapsed, so the Sundown section is the second thing on the page.
     */
    following: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CollapsibleSection(title = "Trip Planner") {
            TripPlannerSection(uiState = uiState, onDeletePlannedTrip = onDeletePlannedTrip)
        }
        following()
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
// internal rather than private: com.zynergylabs.forager.app.ui.log's own drawer panel reuses this exact
// "tap header to expand" shape for its record sections, rather than re-implementing the same
// molecule a second time.
internal fun CollapsibleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The one-shot expandRequested/onExpandRequestConsumed pair that was here had one caller, the
    // search panel's two coordinate folds, which dispatch 2026-09-28-697 removed; so did it.
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithShapedPress { expanded = !expanded },
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
 * [RoomSearchCacheRepository][com.zynergylabs.forager.app.data.repository.RoomSearchCacheRepository]) — so
 * these are plain [Column] children inside the drawer's existing scroll rather than a nested
 * [LazyColumn], which inside a scrolling parent would need a height of its own and would scroll
 * independently of everything around it.
 *
 * Tapping an entry does **not** mean "show me the saved copy": it re-runs the search through the
 * ordinary flow, which tries live first — see
 * [AvailabilityViewModel.onRecentSearchSelected][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onRecentSearchSelected].
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

/**
 * The species search controls themselves: the species text field and its suggestion dropdown. One
 * caller, [SearchEntryBar], the compact top bar that hosts this field directly (map/navigation
 * redesign dispatch D's "the top bar should be the entry field"). Factored out when the old
 * `AvailabilitySearchTopBar` and [CompactToolsDrawerContent] hosted it too; neither does now (the
 * first is gone, the second holds no search), so the parameters below that existed for them have
 * been removed or are defaulted for this one caller (dispatch 2026-09-28-658, scout item F3: the
 * location trailing icon, which only those callers showed, and the callback that fed it).
 *
 * The category chip row this composable used to render alongside the field is gone (owner
 * decision): the app is fungi-only now, so there is nothing left to choose between. See
 * [AvailabilityUiState.taxonFilter]'s default and [activeSearchSummary].
 *
 * [queryFieldModifier]/[onQueryFieldFocusChanged]: [SearchEntryBar] needs a stable [testTag] on
 * the real field to tap/focus, and a way to know when that focus changes so it can drive
 * [SearchDropdown]'s own visibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesSearchControls(
    uiState: AvailabilityUiState,
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
     * this used to swap to on focus is gone from the app entirely, by direct owner instruction.
     */
    restingPlaceholder: String = "",
    fieldColors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    /**
     * Text style for the entered/placeholder text — map/navigation search-UI redo dispatch:
     * [SearchEntryBar] pins its field to a short, fixed height (twice the compass strip's own
     * measured row height), and the default [OutlinedTextField] text style (`bodyLarge`, sized for
     * a full ~56dp Material field) doesn't fit inside it — the text was clipped away entirely, not
     * merely cramped. The owner's own direct call: scale the text down to fit the box, rather than
     * the box up to fit default-sized text. Defaulted to Material's own ([LocalTextStyle.current]).
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
     * defaulted to [OutlinedTextFieldDefaults.contentPadding], Material's own stock value.
     */
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    /** Whether the suggestions open over a map drawn on screen; they are then at the map chrome's alpha (owner, "1 A"). */
    suggestionsOverMap: Boolean = false,
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
                    trailingIcon = if (uiState.isSearchingTaxa) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
            // The menu's default container role, passed explicitly, and its content colour pinned to
            // the role's own (`contentColorFor` matches a colour-scheme role exactly; see
            // `MapLayersSheet`), as J8's menus do (`journalMenuContentColor`).
            // Owner, verbatim "1 A" (planner message 2026-09-28-77, Q3): at the map chrome's alpha on its
            // own over the Maps tab, stacking over the 0.8 search panel; solid where no map is drawn.
            val suggestionsColor = mapChromeFill(navigationBarContainerColor(), suggestionsOverMap)
            val suggestionsContentColor = contentColorFor(navigationBarContainerColor())
            ExposedDropdownMenu(
                expanded = suggestionsOpen,
                onDismissRequest = onDismissTaxonSuggestions,
                containerColor = suggestionsColor,
                modifier = Modifier
                    .testTag(TAXON_SUGGESTIONS_MENU_TAG)
                    .mapChromeContainerColor(suggestionsColor),
            ) {
                CompositionLocalProvider(LocalContentColor provides suggestionsContentColor) {
                    // The content colour as read inside the menu, on its first row, for tests.
                    val readInside = Modifier.mapChromeContentColor(LocalContentColor.current)
                    if (uiState.taxonSearchResults.isEmpty() && uiState.taxonSearchHasNoResults) {
                        DropdownMenuItem(
                            text = { Text("No matches for “${uiState.taxonSearchQuery.trim()}”") },
                            onClick = {},
                            enabled = false,
                            modifier = readInside,
                        )
                    } else {
                        uiState.taxonSearchResults.forEachIndexed { index, result ->
                            DropdownMenuItem(
                                text = { TaxonSuggestionContent(result) },
                                onClick = { onTaxonSearchResultSelected(result) },
                                modifier = if (index == 0) readInside else Modifier,
                            )
                        }
                    }
                }
            }
        }
        // Back closes the suggestions first, as it does other popups (dispatch 2026-09-28-104, item 4; the
        // map-chrome device check saw three Backs change nothing). Diagnosed, not assumed: under Robolectric,
        // with the list open, Back reached none of this screen's handlers (the home handler, the search
        // panel's, and a first version of this one all stayed silent), and `ExposedDropdownMenuBox`'s bytecode
        // (material3 1.5.0-alpha26) calls its own internal `BackHandler` after composing its content. That
        // handler answers with `onExpandedChange(false)`, which this box leaves a no-op on purpose (a tap on
        // the field must not toggle the list), so Back was taken and did nothing. Handlers win in
        // registration order, so this one is composed after the box, not inside it or before it.
        BackHandler(enabled = suggestionsOpen) { onDismissTaxonSuggestions() }
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
private fun MonthSelector(selectedMonth: Int, onMonthSelected: (Int) -> Unit, overMap: Boolean = false) {
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
        // As the species suggestions above: the default role, passed explicitly, and its content colour.
        // As the species suggestions (owner, "1 A"): 0.8 on its own over the Maps tab, stacking.
        val monthMenuColor = mapChromeFill(navigationBarContainerColor(), overMap)
        val monthMenuContentColor = contentColorFor(navigationBarContainerColor())
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = monthMenuColor,
            modifier = Modifier
                .testTag(MONTH_MENU_TAG)
                .mapChromeContainerColor(monthMenuColor),
        ) {
            CompositionLocalProvider(LocalContentColor provides monthMenuContentColor) {
                // The content colour as read inside the menu, on its first row, for tests.
                val readInside = Modifier.mapChromeContentColor(LocalContentColor.current)
                (1..12).forEach { month ->
                    DropdownMenuItem(
                        text = { Text(Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                        onClick = {
                            onMonthSelected(month)
                            expanded = false
                        },
                        modifier = if (month == 1) readInside else Modifier,
                    )
                }
            }
        }
    }
}

/** The species suggestions' menu ([SpeciesSearchControls]), for tests. */
internal const val TAXON_SUGGESTIONS_MENU_TAG = "taxon-suggestions-menu"

/** The Month menu ([MonthSelector]), for tests. */
internal const val MONTH_MENU_TAG = "month-menu"

/** RECORD -943: the Maps tab's search pill's padding above its field (the bar elsewhere keeps Spacing.xs). */
internal val MAP_PILL_TOP_PADDING = 4.dp

/** RECORD -943: the pill's padding below its field, before its 1 dp spacer (the bar elsewhere keeps twice Spacing.xs). */
internal val MAP_PILL_BOTTOM_PADDING = 8.dp
