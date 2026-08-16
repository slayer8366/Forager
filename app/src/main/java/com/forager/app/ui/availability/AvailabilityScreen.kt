package com.forager.app.ui.availability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.ui.map.SightingsMap
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private enum class ResultsTab(val label: String) { LIST("List"), MAP("Map") }

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
) {
    var selectedTab by remember { mutableStateOf(ResultsTab.LIST) }

    LaunchedEffect(selectedTab, uiState.region, uiState.selectedMonth) {
        if (selectedTab == ResultsTab.MAP) onMapTabSelected()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Forager") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RegionControls(
                uiState = uiState,
                onUseCurrentLocation = onUseCurrentLocation,
                onManualLatChanged = onManualLatChanged,
                onManualLngChanged = onManualLngChanged,
                onSearchManualCoordinates = onSearchManualCoordinates,
                onRadiusChanged = onRadiusChanged,
            )
            MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected)

            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ResultsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (selectedTab) {
                ResultsTab.LIST -> ResultsSection(uiState = uiState)
                ResultsTab.MAP -> MapSection(uiState = uiState)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUseCurrentLocation, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Use current location")
        }

        if (uiState.locationPermissionDenied) {
            Text(
                "Location permission was denied. Enter coordinates manually below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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

@Composable
private fun ResultsSection(uiState: AvailabilityUiState) {
    when {
        uiState.isLoading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.errorMessage != null -> Text(
            uiState.errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )

        !uiState.hasSearched -> Text(
            "Choose a region to see which mushrooms have historically been found nearby this month.",
            style = MaterialTheme.typography.bodyMedium,
        )

        uiState.forecast != null && uiState.forecast.entries.isEmpty() -> Text(
            "No verifiable fungi observations found for this region and month. Try a wider radius.",
            style = MaterialTheme.typography.bodyMedium,
        )

        uiState.forecast != null -> {
            val forecast = uiState.forecast
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Based on ${forecast.totalObservationsConsidered} historical iNaturalist observations " +
                        "within ${forecast.region.radiusKm} km for " +
                        Month.of(forecast.month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(forecast.entries, key = { it.species.taxonId }) { entry ->
                        SpeciesRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSection(uiState: AvailabilityUiState) {
    when {
        !uiState.hasSearched -> Text(
            "Choose a region to see mapped sightings.",
            style = MaterialTheme.typography.bodyMedium,
        )

        uiState.isLoadingSightings -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.sightingsErrorMessage != null -> Text(
            uiState.sightingsErrorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )

        else -> {
            val region = uiState.region
            if (region != null) {
                SightingsMap(
                    region = region,
                    sightings = uiState.sightings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
