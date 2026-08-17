package com.forager.app.ui.availability

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forager.app.BuildConfig
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.ForagingWeatherGuidance
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.MgrsConverter
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MgrsCoordinate
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.TripWindow
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.map.SightingsMapSlot
import com.forager.app.ui.map.VISITING_ORDER_DISCLAIMER
import com.forager.app.ui.map.foragingAreaSummary
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private enum class ResultsTab(val label: String) {
    LIST("List"),
    MAP("Map"),
}

/** How long a first back press keeps "exit on the next one" armed — see [AvailabilityScreen]. */
private const val DOUBLE_BACK_EXIT_WINDOW_MS = 2000L

/**
 * The screen's spacing scale. Padding and gap values across this file used to be picked ad hoc
 * (2dp here, 10dp there) with no relationship to each other, which is why visually-similar things
 * — card internal padding, the gap between a card's rows — didn't quite line up card to card.
 * Four steps, each used for a stated kind of gap, rather than a value invented per call site.
 */
private object Spacing {
    /** Within a tightly related group — a line and its own subtext, one card's internal rows. */
    val xs = 4.dp

    /** Between related but distinct items — chips in a row, a card's own sub-sections. */
    val sm = 8.dp

    /** A card's outer padding, and the standard gap between sibling cards. */
    val md = 12.dp

    /** Screen-level padding, and the gap between major regions of a tab. */
    val lg = 16.dp
}

/**
 * Map-first layout: the results (map or ranked list) own the content area, and everything set far
 * less than once per search — location, radius, month, the foraging-areas layer, and trip
 * planning — lives in a navigation drawer behind the app bar's tune icon, as two independently
 * collapsible sections; see [SearchControls]. Species/category, the one control used on nearly
 * every search, lives in the app bar itself; see [AvailabilitySearchTopBar].
 *
 * **Why the rest is still in a drawer.** The controls used to be stacked above the results in one
 * unscrolled [Column]. A Column measures its non-weighted children in order against the height
 * still unclaimed, so the eight-odd wrap-content controls took the viewport and whatever came
 * after them — the tab row, the conditions card, the map — was measured against what was left,
 * which on a ~780dp-tall screen is zero. Nothing scrolled, because the Column had no
 * `verticalScroll`, so the starved children were simply unreachable. Making that Column
 * scrollable was the other option and was rejected: a scrollable parent passes an infinite
 * height constraint down, which a map cannot be measured against at all, so the map would still
 * have needed a hard-coded height and would still not have been the primary thing on screen.
 *
 * The drawer is what makes the real fix possible: with most controls gone, the content area has
 * a small, bounded set of wrap-content siblings above the results (the top bar, the summary
 * strip, the tab row), and the results take the rest via [Modifier.weight], which is a definite
 * bounded height rather than a remainder.
 *
 * **The app bar is the one exception**, and the one place a change here can still reintroduce the
 * squeeze this file's whole layout exists to avoid — see [AvailabilitySearchTopBar]'s own doc
 * comment for why it's a fixed two-row bar rather than a single Material3 row, and
 * [AvailabilityScreenLayoutTest] for the measurement that verifies it hasn't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onMapTabSelected: () -> Unit,
    onToggleForagingAreas: (Boolean) -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onReopenTaxonSuggestions: () -> Unit,
    /** Called when a date and name are confirmed for a trip pin dropped via the map's long-press gesture. */
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    /**
     * What fills the map's box. Defaults to the real map, so no production caller passes it; see
     * [MapSlot] for why the map is reached through a slot rather than named directly here.
     */
    mapSlot: MapSlot = SightingsMapSlot,
) {
    // Map up front. The list is one tap away; the map is the thing this screen is arranged around.
    var selectedTab by remember { mutableStateOf(ResultsTab.MAP) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current

    // The authority for "should the drawer be open" — not drawerState.isOpen. DrawerState is an
    // animated slide-in/out; its currentValue only settles to Open/Closed once that animation
    // finishes, so routing back-press decisions off it left a real window, mid-animation, where a
    // rapid open-then-back could still read as closed and fall through to the exit-warning
    // handler below instead of the close-drawer one — a real bug, not a hypothetical, reported
    // against the previous version. isDrawerOpen flips the instant an open/close is requested,
    // synchronously, so both BackHandlers below always route correctly on the very next press no
    // matter where the slide animation currently is. The LaunchedEffect drives the actual
    // animated drawer as a side effect of this flag, and Compose's animateTo cancels and reverses
    // cleanly if the flag flips again before a prior animation finished, so rapid open/close
    // toggling stays responsive rather than queuing up stale animations.
    var isDrawerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) drawerState.open() else drawerState.close()
    }

    LaunchedEffect(selectedTab, uiState.region, uiState.selectedMonth, uiState.taxonFilter) {
        if (selectedTab == ResultsTab.MAP) onMapTabSelected()
    }

    // System back with the drawer open closes the drawer instead of exiting — the drawer has no
    // swipe-to-close (see gesturesEnabled below), so back is otherwise the only physical-button
    // way out, and letting it fall through to the app's own back-to-exit handling below would
    // exit the whole app from what's meant to read as "go back one step."
    BackHandler(enabled = isDrawerOpen) {
        isDrawerOpen = false
    }

    // System back with the drawer closed: a second press within the window actually exits;
    // a lone press just warns. This is a single-Activity app with nothing else back could
    // sensibly navigate to, so an un-warned single back press off the map (which a pan gesture
    // can graze) would otherwise dump the user straight out.
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = !isDrawerOpen) {
        if (backPressedOnce) {
            (context as? Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Tap Back Button Again to Exit", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(DOUBLE_BACK_EXIT_WINDOW_MS)
            backPressedOnce = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe-to-open is off on purpose: the content behind the drawer is a full-screen
        // pannable map, and a horizontal drag there means "pan", not "open the drawer". The
        // app-bar icon is the way in. Swipe-to-close still works — Material3 enables the drag
        // whenever the drawer is open regardless of this flag.
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                // The one visible way to close this drawer other than tapping the scrim: gestures
                // are off (see gesturesEnabled above), and the scrim alone is undiscoverable.
                DrawerHeader(onClose = { isDrawerOpen = false })
                SearchControls(
                    // The controls take whatever height is left over so the build footer stays
                    // pinned to the bottom of the sheet rather than sitting past the end of the
                    // controls' own scroll, where nobody would find it.
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    onUseCurrentLocation = {
                        isDrawerOpen = false
                        onUseCurrentLocation()
                    },
                    onManualLatChanged = onManualLatChanged,
                    onManualLngChanged = onManualLngChanged,
                    onSearchManualCoordinates = {
                        isDrawerOpen = false
                        onSearchManualCoordinates()
                    },
                    onRadiusChanged = onRadiusChanged,
                    onMonthSelected = onMonthSelected,
                    onDeletePlannedTrip = onDeletePlannedTrip,
                )
                BuildIdentityFooter()
            }
        },
    ) {
        Scaffold(
            topBar = {
                AvailabilitySearchTopBar(
                    uiState = uiState,
                    onOpenDrawer = {
                        // Dismissed here, not just left to whatever state the drawer's own
                        // content happens to leave it in: the suggestion popup is anchored to
                        // this bar and the drawer opens as an overlay above it, so without this
                        // the popup stayed visible underneath/behind the drawer instead of
                        // collapsing along with it.
                        onDismissTaxonSuggestions()
                        isDrawerOpen = true
                    },
                    onUseCurrentLocation = onUseCurrentLocation,
                    onCategorySelected = onCategorySelected,
                    onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                    onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                    onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Scaffold's padding carries the system bar insets, so nothing here is laid
                    // out under the status or navigation bar.
                    .padding(padding),
            ) {
                ActiveSearchSummary(uiState, onReopenTaxonSuggestions = onReopenTaxonSuggestions)
                SearchNotice(uiState)

                SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    ResultsTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }

                // weight(1f) states the intent: the results get whatever is left after the
                // wrap-content siblings above, and Compose measures weighted children last, so
                // that height is definite and bounded instead of a remainder that can reach zero.
                //
                // Measured caveat, from AvailabilityScreenLayoutTest: with the controls in the
                // drawer, removing this weight changes nothing — the tab content is then the last
                // unweighted child, so the remainder it is measured against is exactly the height
                // the weight would have granted, and all three configurations measure identically
                // either way. The drawer is what fixed the starvation; this weight is what keeps
                // it fixed the moment another wrap-content sibling is added below the results.
                when (selectedTab) {
                    ResultsTab.LIST -> ListTab(uiState = uiState, modifier = Modifier.weight(1f))
                    ResultsTab.MAP -> MapTab(
                        uiState = uiState,
                        mapSlot = mapSlot,
                        onPlaceTripPin = onPlaceTripPin,
                        onToggleForagingAreas = onToggleForagingAreas,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * What the screen is currently showing, in one line — "Fungi · August · 15 km".
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
private fun ActiveSearchSummary(uiState: AvailabilityUiState, onReopenTaxonSuggestions: () -> Unit) {
    Surface(
        onClick = onReopenTaxonSuggestions,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = activeSearchSummary(uiState),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

private fun activeSearchSummary(uiState: AvailabilityUiState): String {
    val month = Month.of(uiState.selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The radius of the search that actually ran, not the slider's pending value: moving the
    // slider doesn't re-run the search, so reporting it here would describe a search that hasn't
    // happened. Before any search there is no region, and this says so rather than implying one.
    val where = uiState.region?.let { "${it.radiusKm} km" } ?: "no location set"
    return "${uiState.taxonFilter.label} · $month · $where"
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
private fun SearchNotice(uiState: AvailabilityUiState) {
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
 * Which build this is, at the bottom of the drawer.
 *
 * Debug APKs get handed to a tester several times a session and, before this, the app had no way
 * to say which one was running: two different builds both reported "1.0". The versionCode is here
 * because that is the number Android compares when deciding whether an install replaces or
 * no-ops, and the versionName because it carries the commit sha that names the exact build. A
 * versionName starting with "UNVERSIONED" means the build could not derive its identity from git
 * — see resolveBuildIdentity in app/build.gradle.kts — and its versionCode is not trustworthy.
 */
@Composable
private fun BuildIdentityFooter() {
    HorizontalDivider()
    Text(
        text = "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            // The drawer sheet sits outside Scaffold's own automatic system-bar inset handling
            // (see AvailabilityScreen's enableEdgeToEdge() call in MainActivity), so this, the
            // bottom-most content in the sheet, needs its own bottom inset explicitly — otherwise
            // it would sit partly behind the now-transparent gesture/nav bar.
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    )
}

/**
 * A visible close affordance at the top of the drawer sheet.
 *
 * Gestures are deliberately disabled on this drawer (see the comment on `gesturesEnabled = false`
 * in [AvailabilityScreen]) because the content behind it is a pannable map, so a swipe there has
 * to mean "pan", not "close". That leaves tapping the scrim as the only other way out, which is
 * easy to miss — this gives the drawer its own explicit, discoverable close control.
 *
 * The whole bar is the tap target, not just an icon: a bare [IconButton] here is a 48dp target in
 * the corner of an otherwise-empty full-width row, which is easy to miss the same way the scrim
 * tap was. Widening the target to the whole row is a bigger, easier-to-hit close affordance for
 * exactly the same action.
 *
 * No visible icon on it, on purpose: the app bar's tune icon that opens this drawer sits at the
 * same height as this row, so the row's position alone reads as "tap here to undo what the tune
 * icon did" without needing its own marker — an X here was one more thing competing for attention
 * at the top of a sheet whose whole point is fewer things demanding a look. [Role.Button] and the
 * `contentDescription` below keep it a real, labelled action for TalkBack even with nothing drawn.
 */
@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClose)
            .semantics { contentDescription = "Close search options" },
    ) {}
}

/**
 * Everything set far less than once per search, as two independently collapsible sections:
 * **Advanced search** (location, radius, month) and **Trip Planner** (rain-driven trip windows
 * plus the planned-trips list). Each is a single tappable header row when collapsed and expands
 * on tap — see [CollapsibleSection] — rather than a flat stack, per the user's own framing of this
 * drawer ("single line until you tap it, then it drops down"). Species/category lives in
 * [AvailabilitySearchTopBar] instead, above the tab row, because it's the one control people
 * reach for on nearly every search rather than once a session. The foraging-areas layer toggle
 * used to live in "Advanced search" too; it moved to sit directly below the map itself — see
 * [ForagingAreasToggle]'s call site in [MapTab] — since turning that layer on or off is something
 * you do while looking at the map, not a setting to dig into a drawer for.
 *
 * The scroll modifier on the outer [Column] is not optional. This is the same tall stack of
 * controls that starved the map when it lived in the main column; a drawer sheet is a
 * fixed-height container too, so without it the later controls would simply be unreachable on a
 * short screen or at a large font scale. Collapsing both sections by default shortens that stack
 * further, but doesn't remove the need for scroll — a large font scale with both sections expanded
 * still needs it.
 */
@Composable
private fun SearchControls(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CollapsibleSection(title = "Advanced search") {
            RegionControls(
                uiState = uiState,
                onUseCurrentLocation = onUseCurrentLocation,
                onManualLatChanged = onManualLatChanged,
                onManualLngChanged = onManualLngChanged,
                onSearchManualCoordinates = onSearchManualCoordinates,
                onRadiusChanged = onRadiusChanged,
            )
            HorizontalDivider()
            MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected)
        }
        HorizontalDivider()
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
private fun CollapsibleSection(
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

@Composable
private fun RegionControls(
    uiState: AvailabilityUiState,
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

        Text("Search radius: ${uiState.radiusKm} km", style = MaterialTheme.typography.bodyMedium)
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
private fun AvailabilitySearchTopBar(
    uiState: AvailabilityUiState,
    onOpenDrawer: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TaxonFilter.DEFAULT_CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = uiState.taxonFilter == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category.label) },
                        )
                    }
                }
            }

            val suggestionsOpen = uiState.taxonSearchResults.isNotEmpty()
            ExposedDropdownMenuBox(
                expanded = suggestionsOpen,
                onExpandedChange = {},
                modifier = Modifier.padding(horizontal = Spacing.sm),
            ) {
                OutlinedTextField(
                    value = uiState.taxonSearchQuery,
                    onValueChange = onTaxonSearchQueryChanged,
                    placeholder = {
                        Text("Or search a species", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.isSearchingTaxa) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                            IconButton(onClick = onUseCurrentLocation) {
                                Icon(Icons.Filled.MyLocation, contentDescription = "Use current location")
                            }
                        }
                    },
                )
                // A no-op onDismissRequest here before this fix meant the standard "tap outside
                // the popup" dismissal ExposedDropdownMenu already implements never actually
                // closed anything — the only way to get rid of the list was to pick a result or
                // clear the query back below MIN_QUERY_LENGTH. Wiring the real dismiss action in
                // is the fix, not new behavior invented on top of the component.
                ExposedDropdownMenu(expanded = suggestionsOpen, onDismissRequest = onDismissTaxonSuggestions) {
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

/**
 * The ranked list, with the conditions card above it.
 *
 * The card sits here rather than in the drawer or over the map because rainfall is context for
 * the ranking and belongs next to it — but *next to*, not fused into it: it keeps its own card,
 * its own heading, and says nothing about the ranking below. See [ConditionsCard].
 *
 * Trip windows used to be shown here too; they now live in the drawer's Trip Planner section —
 * see [TripPlannerSection] — because "what's likely nearby this month" and "when in the next few
 * days is worth going" are different questions with different lifetimes (the ranking depends on
 * the browsed month, trip windows only on the next several days), and fusing them into one
 * scrolling column was one more step to reach whichever one wasn't currently showing.
 */
@Composable
private fun ListTab(uiState: AvailabilityUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        if (uiState.conditions != null) {
            ConditionsCard(conditions = uiState.conditions)
        }
        // Weighted for the same reason the tab content is: the ranked list scrolls, so it needs a
        // bounded height rather than whatever the card above it happens to leave.
        ResultsSection(uiState = uiState, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ResultsSection(uiState: AvailabilityUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            uiState.isLoading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            !uiState.hasSearched -> Text(
                "Choose a region to see what's historically been found nearby this month.",
                style = MaterialTheme.typography.bodyMedium,
            )

            uiState.forecast != null && uiState.forecast.entries.isEmpty() -> Text(
                "No verifiable observations of ${uiState.forecast.filter.label} found for this region and month. " +
                    "Try a wider radius or a different category.",
                style = MaterialTheme.typography.bodyMedium,
            )

            uiState.forecast != null -> {
                val forecast = uiState.forecast
                Text(
                    "Based on ${forecast.totalObservationsConsidered} historical iNaturalist observations " +
                        "of ${forecast.filter.label} within ${forecast.region.radiusKm} km for " +
                        Month.of(forecast.month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(forecast.entries, key = { it.species.taxonId }) { entry ->
                        SpeciesRow(entry)
                    }
                }
            }
        }
    }
}

/**
 * The map tab: the map itself takes the whole content area apart from the foraging-areas toggle
 * and detail below it, which are bounded so they can't repeat the starvation this layout exists
 * to fix.
 *
 * Owns the long-press-to-plan-a-trip flow: [MapSlot] only reports *where* a long-press landed
 * (see its own doc comment for why), so the pending location and the date-picker dialog that
 * turns it into a saved [PlannedTrip] both live here, next to the only place that location can
 * come from. [defaultTripName] is computed from the trip count already in state, here rather than
 * inside the dialog, so the dialog stays a dumb presenter of whatever default it's handed.
 */
@Composable
private fun MapTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onToggleForagingAreas: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingTripLocation by remember { mutableStateOf<LatLng?>(null) }

    when {
        !uiState.hasSearched -> MapMessage(
            "Choose a region in search options to see mapped sightings.",
            modifier = modifier,
        )

        uiState.isLoadingSightings -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.sightingsErrorMessage != null -> MapMessage(
            uiState.sightingsErrorMessage,
            modifier = modifier,
            color = MaterialTheme.colorScheme.error,
        )

        else -> {
            val region = uiState.region
            if (region != null) {
                Column(modifier = modifier.fillMaxWidth()) {
                    // Areas are only handed to the map when the layer is switched on; the
                    // clustering itself was already computed when the sightings loaded.
                    val visibleAreas = if (uiState.showForagingAreas) {
                        (uiState.foragingAreas as? ForagingAreas.Found)?.areas.orEmpty()
                    } else {
                        emptyList()
                    }
                    mapSlot(
                        region,
                        uiState.sightings,
                        visibleAreas,
                        uiState.plannedTrips,
                        { location -> pendingTripLocation = location },
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    // Always visible below the map, not gated on showForagingAreas itself — the
                    // switch is how the layer gets turned back on, so it has to be reachable while
                    // off. It moved here from the drawer's "Advanced search" section: whether to
                    // show the layer is a decision made while looking at the map, not a setting to
                    // dig into a drawer for.
                    ForagingAreasToggle(
                        checked = uiState.showForagingAreas,
                        onCheckedChange = onToggleForagingAreas,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                    // A fixed-height Box, always present, rather than conditionally including
                    // ForagingAreasPanel only when the layer is on: the panel used to appear and
                    // disappear from this Column outright, which changed how much of the fixed
                    // parent height was left over for the weighted mapSlot above — the map visibly
                    // grew and shrank as the switch was toggled. Reserving the same
                    // [FORAGING_AREAS_PANEL_MAX_HEIGHT] here regardless of the switch's state means
                    // the sibling stack above the map never changes height, so the map doesn't
                    // either — only what's drawn inside this fixed box changes.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FORAGING_AREAS_PANEL_MAX_HEIGHT)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        if (uiState.showForagingAreas) {
                            ForagingAreasPanel(foragingAreas = uiState.foragingAreas)
                        }
                    }
                }
            }
        }
    }

    pendingTripLocation?.let { location ->
        TripDatePickerDialog(
            defaultName = defaultTripName(uiState.plannedTrips.size),
            onConfirm = { date, name ->
                onPlaceTripPin(location, date, name)
                pendingTripLocation = null
            },
            onDismiss = { pendingTripLocation = null },
        )
    }
}

/**
 * The name a newly-placed trip pin is pre-filled with: `"Trip N"`, `N` being one more than how
 * many trips already exist. The simplest possible default, per the user's own framing of this —
 * it doesn't guarantee uniqueness against a renamed or since-deleted trip (there is no rename, and
 * deleting trip 1 of 2 then adding a new one names it "Trip 2" again, alongside the surviving
 * "Trip 2"), but a collision here is cosmetic, not a broken invariant: [PlannedTrip.id] is what
 * actually identifies a trip, and the name stays freely editable in [TripDatePickerDialog] before
 * it's saved.
 */
internal fun defaultTripName(existingTripCount: Int): String = "Trip ${existingTripCount + 1}"

/**
 * Confirms a date and name for a trip pin dropped via the map's long-press gesture.
 *
 * The date is restricted to today-or-later — planning a trip in the past makes no sense, per the
 * user's own framing of this feature. [SavePlannedTripUseCase][com.forager.app.domain.SavePlannedTripUseCase]
 * enforces the same floor independently; this is the UI convenience, not the invariant itself.
 *
 * The name field starts pre-filled with [defaultTripName] and stays freely editable; the confirm
 * button disables on a blank name so the "name is never blank" invariant
 * ([PlannedTrip.name][com.forager.app.domain.model.PlannedTrip.name]) can't be violated from this
 * dialog, mirroring `SavePlannedTripUseCase`'s own `require` for the same reason the date floor is
 * mirrored there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDatePickerDialog(
    defaultName: String,
    onConfirm: (LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC-midnight epoch millis regardless of device time zone, so both the
    // floor and the confirmed selection are converted through UTC to stay consistent with it.
    val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
        },
    )
    var name by remember { mutableStateOf(defaultName) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // Guards the "name is never blank" invariant from this dialog — see this
                // function's own doc comment.
                enabled = name.isNotBlank(),
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date, name)
                },
            ) { Text("Plan trip") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Trip name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            )
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * The Trip Planner drawer section's content: the planned-trips list (see [PlannedTripsList])
 * above the existing rain-driven trip windows, gated on a search the same way [MapTab] is —
 * before any region is chosen there is no rainfall history to plan from, so this says that rather
 * than rendering an empty [TripWindowsCard]. The planned-trips list has no such gate: it lists
 * absolute map points the user placed, independent of any search.
 */
@Composable
private fun TripPlannerSection(uiState: AvailabilityUiState, onDeletePlannedTrip: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PlannedTripsList(plannedTrips = uiState.plannedTrips, onDeletePlannedTrip = onDeletePlannedTrip)
        HorizontalDivider()
        if (uiState.hasSearched) {
            TripWindowsCard(uiState = uiState)
        } else {
            Text(
                "Choose a region in search options to see rain-driven trip windows.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The list of trips the user has placed on the map, sorted by
 * [GetPlannedTripsUseCase][com.forager.app.domain.GetPlannedTripsUseCase] with any dated today
 * already moved to the front — this composable renders that order, it doesn't recompute it.
 */
@Composable
private fun PlannedTripsList(plannedTrips: List<PlannedTrip>, onDeletePlannedTrip: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Planned Trips", style = MaterialTheme.typography.titleSmall)
        if (plannedTrips.isEmpty()) {
            Text(
                "No trips planned yet. Long-press the map to plan one.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val today = LocalDate.now()
            plannedTrips.forEach { trip ->
                PlannedTripRow(
                    trip = trip,
                    isToday = trip.date == today,
                    onDelete = { onDeletePlannedTrip(trip.id) },
                )
            }
        }
    }
}

/**
 * One planned trip: its user-chosen [PlannedTrip.name] as the primary identifying text (more
 * prominent than the coordinates below it, per the user's own framing — the name is what a
 * person recognizes a trip by, the coordinates are supporting detail), its MGRS grid reference
 * (the format asked for on this screen; see [MgrsConverter]) with decimal degrees kept alongside
 * since some readers still want them and MGRS can't cover every point (see
 * [MgrsCoordinate.Unsupported]), a "Directions" action that hands the location to whatever
 * navigation app is installed (see [launchDirections]), and delete.
 */
@Composable
private fun PlannedTripRow(trip: PlannedTrip, isToday: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isToday) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(trip.name, style = MaterialTheme.typography.titleSmall)
                Text(TRIP_WINDOW_DATE_FORMAT.format(trip.date), style = MaterialTheme.typography.bodyMedium)
                when (val mgrs = MgrsConverter.convert(trip.location)) {
                    is MgrsCoordinate.Grid -> Text(mgrs.value, style = MaterialTheme.typography.bodySmall)
                    // No line at all rather than a wrong or truncated one — see MgrsCoordinate's
                    // own doc comment. The decimal-degrees line below still locates the trip.
                    is MgrsCoordinate.Unsupported -> Unit
                }
                Text(
                    "${"%.4f".format(trip.location.lat)}, ${"%.4f".format(trip.location.lng)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { launchDirections(context, trip) }) {
                Icon(Icons.Filled.Directions, contentDescription = "Directions to ${trip.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove planned trip for ${trip.date}")
            }
        }
    }
}

/** Shown when no navigation app can handle [directionsIntent] — CLAUDE.md: report, don't swallow. */
private const val NO_MAPS_APP_MESSAGE = "No maps app is installed to show directions."

/**
 * The `geo:` intent for [trip]: a `geo:0,0?q=lat,lng(label)` URI resolves to whatever navigation
 * app is installed rather than assuming Google Maps specifically, which is the portable choice —
 * see this file's own doc comment for why this lives here and not in the ViewModel or `domain/`.
 * Exposed as its own function (rather than inlined into [launchDirections]) so a test can assert
 * on its action/data without needing a resolvable package or a running Activity.
 */
internal fun directionsIntent(trip: PlannedTrip): Intent {
    val label = Uri.encode(trip.name)
    val uri = Uri.parse("geo:0,0?q=${trip.location.lat},${trip.location.lng}($label)")
    return Intent(Intent.ACTION_VIEW, uri)
}

/**
 * Opens directions to [trip]'s location in whatever navigation app [directionsIntent] resolves
 * to. `Intent`/`Context` are Android framework types, so this launch has to happen from the
 * Compose UI layer — CLAUDE.md keeps both out of `domain/` and the ViewModel.
 *
 * Resolves the intent before launching, in addition to catching [ActivityNotFoundException]:
 * checking first is what lets this show one exact, always-correct message rather than depending
 * on whichever exception a given OEM build happens to raise for an unresolvable implicit intent —
 * the catch is the belt to the resolve check's suspenders, covering the narrow race where the
 * only maps app is uninstalled between the check and the launch. Either path shows a real message
 * (a [Toast]) rather than crashing or failing silently.
 */
internal fun launchDirections(context: Context, trip: PlannedTrip) {
    val intent = directionsIntent(trip)
    if (intent.resolveActivity(context.packageManager) == null) {
        Toast.makeText(context, NO_MAPS_APP_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, NO_MAPS_APP_MESSAGE, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MapMessage(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
    )
}

@Composable
private fun ForagingAreasToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Foraging areas", style = MaterialTheme.typography.titleSmall)
            Text(
                "Group the pins into spots that have produced repeatedly. Switch off to read the " +
                    "individual observations instead.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The detail below the map when the layer is on: the numbered areas and their stats, or an
 * explicit reason no area was found.
 *
 * Every branch says something. A silent blank panel would look identical to a still-loading one,
 * and "no repeat-producing areas here" is a real answer worth reading (CLAUDE.md: partial or
 * empty results are reported as such).
 */
@Composable
private fun ForagingAreasPanel(foragingAreas: ForagingAreas?, modifier: Modifier = Modifier) {
    when (foragingAreas) {
        null -> Text(
            "Foraging areas are grouped from the mapped sightings, which haven't loaded yet.",
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
        )

        is ForagingAreas.None -> Text(
            noAreasMessage(foragingAreas),
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
        )

        is ForagingAreas.Found -> Column(
            // fillMaxHeight, not heightIn(max = ...): the caller ([MapTab]) now wraps this whole
            // composable in a Box already fixed at FORAGING_AREAS_PANEL_MAX_HEIGHT regardless of
            // whether the layer is on, specifically so the map above never resizes when the
            // switch is toggled — filling that fixed height here (and scrolling within it) is
            // what makes the disclaimer/count caption and the area list behave as a footnote
            // rather than a wrap-content sibling that could still grow the space it's given.
            modifier = modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Tertiary rather than error: this is a caution about what the ordering isn't, not a
            // failure. Reusing the error color here would make a real failure (a failed sightings
            // fetch, a couldn't-load message) read as no more urgent than a standing disclaimer.
            Text(
                VISITING_ORDER_DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (foragingAreas.ungroupedObservationCount > 0) {
                Text(
                    "${foragingAreas.ungroupedObservationCount} scattered observations belong to no area.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Tapping a numbered marker on the map shows the same summary for that one area, so
            // this list staying a footnote here costs nothing.
            foragingAreas.areas.forEach { area -> ForagingAreaRow(area) }
        }
    }
}

/**
 * The footnote-not-competitor cap for [ForagingAreasPanel]. Bounds the disclaimer/count caption
 * together with the area list below it, so the panel as a whole stays small enough that the
 * mapSlot it shares a weighted region with keeps the larger share — see
 * [AvailabilityScreenLayoutTest]'s `MIN_MAP_SHARE_OF_SCREEN` for the map's own floor.
 */
private val FORAGING_AREAS_PANEL_MAX_HEIGHT = 60.dp

@Composable
private fun ForagingAreaRow(area: ForagingArea) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("${area.visitOrder}", style = MaterialTheme.typography.titleMedium)
            Text(foragingAreaSummary(area), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Why nothing was found, stated specifically. The clustering thresholds are never relaxed to
 * manufacture an area, so the honest answer here is sometimes "there isn't one".
 */
private fun noAreasMessage(none: ForagingAreas.None): String {
    val minPoints = ClusterForagingAreasUseCase.MIN_OBSERVATIONS_PER_AREA
    val radiusMeters = ClusterForagingAreasUseCase.NEIGHBORHOOD_RADIUS_METERS.toInt()
    return when (none.reason) {
        ForagingAreas.Reason.NO_OBSERVATIONS ->
            "No mapped observations in this radius, so there's nothing to group into areas. " +
                "Try a wider radius, a different month, or another category."

        ForagingAreas.Reason.TOO_FEW_OBSERVATIONS ->
            "Only ${none.observationsConsidered} mapped observation(s) in this radius — fewer than " +
                "the $minPoints it takes to call anywhere a repeat-producing area."

        ForagingAreas.Reason.NO_GROUP_MET_THRESHOLD ->
            "No repeat-producing areas found in this radius. All ${none.observationsConsidered} " +
                "mapped observations are scattered: none form a group of $minPoints or more within " +
                "${radiusMeters}m of each other. The threshold isn't loosened to find something."
    }
}

/**
 * Recent rainfall, shown as a standalone fact at the top of the ranked list — never described as
 * having factored into it. See [com.forager.app.domain.GetConditionsUseCase]'s doc comment for
 * why this stays unfused with the ranked list.
 */
@Composable
private fun ConditionsCard(conditions: ConditionsSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Current Conditions", style = MaterialTheme.typography.titleSmall)
            val totalMm = conditions.totalPrecipitationMm
            Text(
                "${"%.1f".format(totalMm)}mm of rain in the last 14 days",
                style = MaterialTheme.typography.bodyMedium,
            )
            val daysSince = conditions.daysSinceSignificantRain
            Text(
                when {
                    daysSince == null -> "No significant rain in the last 14 days."
                    daysSince == 0 -> "Rain today."
                    daysSince == 1 -> "1 day since last rain."
                    else -> "$daysSince days since last rain."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val TRIP_WINDOW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/**
 * Upcoming days that sit inside the stated post-rain lag range, next to the group's general
 * weather pattern.
 *
 * Two owned domain objects meet here and stay visually distinct: [TripWindowReport] is
 * measurements and date arithmetic only (see its own doc comment for why it must never grow a
 * score), and [ForagingWeatherGuidance] is the separately-stated rule of thumb that makes those
 * measurements interesting. The card shows both but never blends them into one sentence.
 *
 * Unlike [ConditionsCard], not gated to the browsed month: the days ahead of today are relevant
 * to planning a trip this week regardless of which month's species ranking is on screen.
 */
@Composable
private fun TripWindowsCard(uiState: AvailabilityUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Trip Windows", style = MaterialTheme.typography.titleSmall)

            when {
                uiState.isLoadingTripWindows -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                uiState.tripWindowsErrorMessage != null -> Text(
                    uiState.tripWindowsErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                uiState.tripWindowReport != null -> TripWindowReportContent(uiState.tripWindowReport)
            }

            HorizontalDivider()
            ForagingWeatherGuidanceSection(uiState.foragingSelection)
        }
    }
}

@Composable
private fun TripWindowReportContent(report: TripWindowReport) {
    if (report.windows.isEmpty()) {
        Text(noTripWindowMessage(report), style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        report.windows.forEach { window -> TripWindowRow(window) }
    }
}

@Composable
private fun TripWindowRow(window: TripWindow) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "${TRIP_WINDOW_DATE_FORMAT.format(window.startDate)} – ${TRIP_WINDOW_DATE_FORMAT.format(window.endDate)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val mostRecentRain = window.precedingRainEvents.first()
        Text(
            "${window.daysAfterMostRecentRainAtStart}–${window.daysAfterMostRecentRainAtEnd} days after " +
                "${"%.0f".format(mostRecentRain.totalMm)}mm of rain ending " +
                TRIP_WINDOW_DATE_FORMAT.format(mostRecentRain.endDate) +
                if (mostRecentRain.isForecast) " (forecast)" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (window.precipitationDuringWindowMm > 0.0) {
            Text(
                "${"%.1f".format(window.precipitationDuringWindowMm)}mm more rain forecast during the window",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        window.meanShallowSoilMoistureM3M3?.let { moisture ->
            Text(
                "Shallow soil moisture: ${"%.2f".format(moisture)} m³/m³",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        window.meanSoilTemperatureC?.let { temp ->
            Text("Soil temperature: ${"%.1f".format(temp)}°C", style = MaterialTheme.typography.bodySmall)
        }
        window.evapotranspirationSinceRainMm?.let { et0 ->
            Text(
                "${"%.1f".format(et0)}mm evapotranspiration since the rain",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Why no window was found, stated specifically with the numbers behind it — never a bare "none
 * found" (CLAUDE.md: partial or empty results are reported as such).
 */
private fun noTripWindowMessage(report: TripWindowReport): String = when (val reason = report.noWindowReason) {
    is NoTripWindowReason.NoQualifyingRainEvent ->
        "No run of rain in the last ${reason.daysExamined} days totaled the " +
            "${"%.0f".format(reason.requiredTotalMm)}mm this search treats as a soaking event — the " +
            "wettest run reached ${"%.0f".format(reason.largestRunTotalMm)}mm."

    is NoTripWindowReason.LagRangeOutsideHorizon ->
        "The most recent qualifying rain ended ${TRIP_WINDOW_DATE_FORMAT.format(reason.mostRecentEventEnd)}. " +
            "The ${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–" +
            "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} day window it points to is " +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.lagRangeStart)}–" +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.lagRangeEnd)}, past the " +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.horizonEnd)} horizon this search plans within."

    is NoTripWindowReason.NoForecastDays ->
        "No forecast days were returned for this location, so there's nothing to plan against."

    null -> "" // Unreachable: report.windows.isEmpty() implies a non-null reason.
}

/**
 * The general weather pattern for the current selection, stated as a rule of thumb next to the
 * measurements above it — never combined with them into a score. See
 * [ForagingWeatherGuidance]'s doc comment for the rules this enforces.
 */
@Composable
private fun ForagingWeatherGuidanceSection(selection: ForagingSelection) {
    val guidance = ForagingWeatherGuidance.forSelection(selection)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // labelMedium + a muted color, not titleSmall/labelLarge: Material3 sizes titleSmall and
        // labelLarge identically (14sp/500), so this heading and the card's own "Trip Windows"
        // title above it were reading as the same weight despite one being nested inside the
        // other. This is deliberately a step down from the card title, not a second one beside it.
        Text(
            guidance.heading,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        guidance.paragraphs.forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodySmall)
        }
        guidance.speciesDataCaveat?.let { caveat ->
            Text(caveat, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun SpeciesRow(entry: AvailabilityEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                entry.species.commonName ?: entry.species.scientificName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                entry.species.scientificName,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
            LinearProgressIndicator(
                progress = { entry.relativeLikelihood },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${entry.species.observationCount} observations",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
