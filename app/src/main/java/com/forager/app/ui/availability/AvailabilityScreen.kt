package com.forager.app.ui.availability

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forager.app.BuildConfig
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.ForagingWeatherGuidance
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.TripWindow
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.map.SightingsMapSlot
import com.forager.app.ui.map.VISITING_ORDER_DISCLAIMER
import com.forager.app.ui.map.foragingAreaSummary
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private enum class ResultsTab(val label: String) { LIST("List"), MAP("Map") }

/**
 * Map-first layout: the results (map or ranked list) own the content area, and every search
 * control lives in a navigation drawer behind the app bar's tune icon.
 *
 * **Why the controls are in a drawer.** They used to be stacked above the results in one
 * unscrolled [Column]. A Column measures its non-weighted children in order against the height
 * still unclaimed, so the eight-odd wrap-content controls took the viewport and whatever came
 * after them — the tab row, the conditions card, the map — was measured against what was left,
 * which on a ~780dp-tall screen is zero. Nothing scrolled, because the Column had no
 * `verticalScroll`, so the starved children were simply unreachable. Making that Column
 * scrollable was the other option and was rejected: a scrollable parent passes an infinite
 * height constraint down, which a map cannot be measured against at all, so the map would still
 * have needed a hard-coded height and would still not have been the primary thing on screen.
 *
 * The drawer is what makes the real fix possible: with the controls gone, the content area has
 * exactly one wrap-content sibling (the tab row) plus a one-line summary strip, and the results
 * take the rest via [Modifier.weight], which is a definite bounded height rather than a remainder.
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
    /**
     * What fills the map's box. Defaults to the real map, so no production caller passes it; see
     * [MapSlot] for why the map is reached through a slot rather than named directly here.
     */
    mapSlot: MapSlot = SightingsMapSlot,
) {
    // Map up front. The list is one tap away; the map is the thing this screen is arranged around.
    var selectedTab by remember { mutableStateOf(ResultsTab.MAP) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab, uiState.region, uiState.selectedMonth, uiState.taxonFilter) {
        if (selectedTab == ResultsTab.MAP) onMapTabSelected()
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
                SearchControls(
                    // The controls take whatever height is left over so the build footer stays
                    // pinned to the bottom of the sheet rather than sitting past the end of the
                    // controls' own scroll, where nobody would find it.
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    onUseCurrentLocation = {
                        scope.launch { drawerState.close() }
                        onUseCurrentLocation()
                    },
                    onManualLatChanged = onManualLatChanged,
                    onManualLngChanged = onManualLngChanged,
                    onSearchManualCoordinates = {
                        scope.launch { drawerState.close() }
                        onSearchManualCoordinates()
                    },
                    onRadiusChanged = onRadiusChanged,
                    onCategorySelected = onCategorySelected,
                    onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                    onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                    onMonthSelected = onMonthSelected,
                    onToggleForagingAreas = onToggleForagingAreas,
                )
                BuildIdentityFooter()
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Forager") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Tune, contentDescription = "Search options")
                        }
                    },
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
                ActiveSearchSummary(uiState)
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
                // one-line siblings above, and Compose measures weighted children last, so that
                // height is definite and bounded instead of a remainder that can reach zero.
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
 */
@Composable
private fun ActiveSearchSummary(uiState: AvailabilityUiState) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = activeSearchSummary(uiState),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
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
 * Failures that come from the drawer's own controls, surfaced outside it.
 *
 * The coordinate-validation and search-failure messages used to be rendered inside the ranked
 * list, which was the default tab. The Map tab is the default now and the controls that raise
 * these live behind a drawer that closes on search, so without this strip both messages could be
 * raised and never seen (CLAUDE.md: failures are reported, not swallowed).
 */
@Composable
private fun SearchNotice(uiState: AvailabilityUiState) {
    val message = uiState.errorMessage
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * Everything that defines a search, in the drawer.
 *
 * The scroll modifier is not optional. This is the same tall stack of controls that starved the
 * map when it lived in the main column; a drawer sheet is a fixed-height container too, so
 * without it the month selector and the areas toggle would simply be unreachable on a short
 * screen or at a large font scale.
 */
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SearchControls(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onToggleForagingAreas: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Search", style = MaterialTheme.typography.titleMedium)

        RegionControls(
            uiState = uiState,
            onUseCurrentLocation = onUseCurrentLocation,
            onManualLatChanged = onManualLatChanged,
            onManualLngChanged = onManualLngChanged,
            onSearchManualCoordinates = onSearchManualCoordinates,
            onRadiusChanged = onRadiusChanged,
        )
        HorizontalDivider()
        TaxonFilterControls(
            uiState = uiState,
            onCategorySelected = onCategorySelected,
            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
            onTaxonSearchResultSelected = onTaxonSearchResultSelected,
        )
        HorizontalDivider()
        MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected)
        HorizontalDivider()
        ForagingAreasToggle(
            checked = uiState.showForagingAreas,
            onCheckedChange = onToggleForagingAreas,
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUseCurrentLocation, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Use current location")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun TaxonFilterControls(
    uiState: AvailabilityUiState,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Searching for: ${uiState.taxonFilter.label}", style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaxonFilter.DEFAULT_CATEGORIES.forEach { category ->
                FilterChip(
                    selected = uiState.taxonFilter == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.label) },
                )
            }
        }

        OutlinedTextField(
            value = uiState.taxonSearchQuery,
            onValueChange = onTaxonSearchQueryChanged,
            label = { Text("Or search a species") },
            placeholder = { Text("e.g. chanterelle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (uiState.isSearchingTaxa) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            },
        )

        if (uiState.taxonSearchErrorMessage != null) {
            Text(
                uiState.taxonSearchErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (uiState.taxonSearchResults.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    uiState.taxonSearchResults.forEach { result ->
                        TaxonSuggestionRow(result = result, onClick = { onTaxonSearchResultSelected(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxonSuggestionRow(result: TaxonSearchResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
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
 */
@Composable
private fun ListTab(uiState: AvailabilityUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        if (uiState.conditions != null) {
            ConditionsCard(conditions = uiState.conditions)
        }
        if (uiState.region != null) {
            TripWindowsCard(uiState = uiState)
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
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(forecast.entries, key = { it.species.taxonId }) { entry ->
                        SpeciesRow(entry)
                    }
                }
            }
        }
    }
}

/**
 * The map tab: the map itself takes the whole content area apart from the foraging-areas detail
 * below it, which is bounded so it can't repeat the starvation this layout exists to fix.
 */
@Composable
private fun MapTab(uiState: AvailabilityUiState, mapSlot: MapSlot, modifier: Modifier = Modifier) {
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
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    if (uiState.showForagingAreas) {
                        ForagingAreasPanel(
                            foragingAreas = uiState.foragingAreas,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
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
            .padding(16.dp),
    )
}

@Composable
private fun ForagingAreasToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                VISITING_ORDER_DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.error,
            )
            if (foragingAreas.ungroupedObservationCount > 0) {
                Text(
                    "${foragingAreas.ungroupedObservationCount} scattered observations belong to no area.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Capped so the panel stays a footnote to the map rather than a second screen
            // competing with it; the list scrolls within the cap. Tapping a numbered marker on
            // the map shows the same summary for that one area.
            LazyColumn(
                modifier = Modifier.heightIn(max = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(foragingAreas.areas, key = { it.visitOrder }) { area -> ForagingAreaRow(area) }
            }
        }
    }
}

@Composable
private fun ForagingAreaRow(area: ForagingArea) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        report.windows.forEach { window -> TripWindowRow(window) }
    }
}

@Composable
private fun TripWindowRow(window: TripWindow) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(guidance.heading, style = MaterialTheme.typography.labelLarge)
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
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                entry.species.commonName ?: entry.species.scientificName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                entry.species.scientificName,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { entry.relativeLikelihood },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${entry.species.observationCount} observations",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
