package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import com.forager.app.domain.model.Region
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate

/**
 * The compact bottom nav's Journal destination: [LogGalleryScreen] by default, an entry's own
 * [LogEntryDetailScreen] once one is open or newly started, and — the one state neither of those
 * two needs — [LogEntryLocationPicker] while placing a brand new entry.
 *
 * This exists alongside [LogPanel] rather than replacing it: [LogPanel] is still what the
 * medium/expanded window's drawer shows (`DrawerPanel.Log` in `AvailabilityScreen.kt`, untouched by
 * the map redesign — see `docs/plans/map-redesign.md`'s "Scope decision" section for why that
 * window class stays on its existing navigation shape). This is the compact-only equivalent, reached
 * from the bottom nav instead of the drawer, so it owns no "back to search" affordance — there is no
 * drawer to return to, only another bottom nav tab to tap.
 *
 * [onStartEntry] is the exact same handler the map's long-press "Log a find" option calls (see
 * `AvailabilityScreen.kt`'s `onLogFindHere`) — the location picker below hands it a point the same
 * shape a long-press would, rather than a parallel entry-creation path.
 */
@Composable
internal fun JournalTab(
    uiState: MushroomLogUiState,
    cameraCaptureFiles: CameraCaptureFiles,
    mapSlot: MapSlot,
    pickerRegion: Region,
    basemap: Basemap,
    onOpenEntry: (String) -> Unit,
    onCloseEntry: () -> Unit,
    onStartEntry: (LatLng, LocalDate) -> Unit,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onDeleteEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingLocation by remember { mutableStateOf(false) }
    val editing = uiState.editingEntry

    when {
        editing != null -> LogEntryDetailScreen(
            entry = editing,
            cameraCaptureFiles = cameraCaptureFiles,
            onEntryChanged = onEntryChanged,
            onAddPhoto = onAddPhoto,
            onRemovePhoto = onRemovePhoto,
            onDeleteEntry = { onDeleteEntry(editing.id) },
            onBack = onCloseEntry,
            modifier = modifier,
        )

        pickingLocation -> LogEntryLocationPicker(
            mapSlot = mapSlot,
            region = pickerRegion,
            basemap = basemap,
            onLocationConfirmed = { location ->
                pickingLocation = false
                onStartEntry(location, LocalDate.now())
            },
            onCancel = { pickingLocation = false },
            modifier = modifier,
        )

        else -> LogGalleryScreen(
            entries = uiState.entries,
            isLoading = uiState.isLoadingEntries,
            onOpenEntry = onOpenEntry,
            onAddEntry = { pickingLocation = true },
            modifier = modifier,
        )
    }
}

/**
 * A minimal map picker for the gallery's "+" tile — long-press to place a pin, then confirm, the
 * same long-press-to-point gesture [OfflineMapsPanel] in `AvailabilityScreen.kt` already uses for
 * picking a download region, reused here rather than a new gesture. No radius, no download: this
 * picks exactly one point and hands it straight to [onLocationConfirmed].
 *
 * [region] is the picker's opening viewport only, not a claim about where the entry belongs — it
 * starts centred on the current search region (or a fallback if none has been searched yet) purely
 * so there is a map to navigate before anything is placed; "Place entry here" stays disabled until
 * a real long-press sets a point, so that opening viewport can never itself be submitted.
 */
@Composable
internal fun LogEntryLocationPicker(
    mapSlot: MapSlot,
    region: Region,
    basemap: Basemap,
    onLocationConfirmed: (LatLng) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickedLocation by remember(region) { mutableStateOf<LatLng?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Long-press the map to place this entry.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = LogSpacing.lg, vertical = LogSpacing.sm),
        )

        val mapRegion = pickedLocation?.let { region.copy(lat = it.lat, lng = it.lng) } ?: region
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            mapSlot(
                mapRegion,
                emptyList(),
                emptyList(),
                emptyList(),
                basemap,
                null,
                { location -> pickedLocation = location },
                {},
                Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LogSpacing.lg, vertical = LogSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LogSpacing.sm),
        ) {
            Text(
                pickedLocation?.let { "Selected: ${"%.4f".format(it.lat)}, ${"%.4f".format(it.lng)}" }
                    ?: "No location picked yet — long-press the map above.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
                Button(
                    onClick = { pickedLocation?.let(onLocationConfirmed) },
                    enabled = pickedLocation != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Place entry here") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}
