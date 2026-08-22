package com.forager.app.ui.log

import androidx.activity.compose.BackHandler
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
import com.forager.app.ui.map.MapOverlayContent
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate

/**
 * The compact bottom nav's Journal destination: [LogGalleryScreen] by default, an entry's own
 * [LogEntryReportScreen] once one is opened from the gallery, [LogEntryDetailScreen] once "Edit
 * entry" is chosen from the report (or immediately, for a brand-new entry — see [mode]'s doc
 * comment), and — the one state none of those needs — [LogEntryLocationPicker] while placing a
 * brand new entry.
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
    // REPORT for an entry opened from the gallery (there's something to compile a report from and
    // no reason to assume an edit is wanted), EDIT for one just started (nothing to report yet, so
    // reporting first would just be an empty screen between the picker and the form the user is
    // there for). Reset to REPORT whenever a *different* entry becomes the open one, so returning
    // to an entry after editing shows the freshly-recompiled report rather than staying in edit mode.
    var mode by remember { mutableStateOf(JournalEntryMode.REPORT) }
    val editing = uiState.editingEntry

    // System back unwinds one of this tab's own nested states before AvailabilityScreen's
    // top-level "switch away from a non-Maps tab" handler ever sees it — Compose's
    // OnBackPressedDispatcher tries the most-recently-composed enabled callback first, so this one
    // (composed as part of the Journal tab's own content) naturally takes priority. Mirrors the
    // `when` below's own branch order: out of the edit form to the report, out of the report to the
    // gallery, out of the picker to the gallery.
    BackHandler(enabled = editing != null || pickingLocation) {
        when {
            editing != null && mode == JournalEntryMode.EDIT -> mode = JournalEntryMode.REPORT
            editing != null -> onCloseEntry()
            else -> pickingLocation = false
        }
    }

    when {
        editing != null && mode == JournalEntryMode.EDIT -> LogEntryDetailScreen(
            entry = editing,
            cameraCaptureFiles = cameraCaptureFiles,
            onEntryChanged = onEntryChanged,
            onAddPhoto = onAddPhoto,
            onRemovePhoto = onRemovePhoto,
            onDeleteEntry = { onDeleteEntry(editing.id) },
            onBack = { mode = JournalEntryMode.REPORT },
            modifier = modifier,
        )

        editing != null -> LogEntryReportScreen(
            entry = editing,
            onEdit = { mode = JournalEntryMode.EDIT },
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
                mode = JournalEntryMode.EDIT
                onStartEntry(location, LocalDate.now())
            },
            onCancel = { pickingLocation = false },
            modifier = modifier,
        )

        else -> LogGalleryScreen(
            entries = uiState.entries,
            isLoading = uiState.isLoadingEntries,
            loadErrorMessage = uiState.loadErrorMessage,
            onOpenEntry = { id ->
                mode = JournalEntryMode.REPORT
                onOpenEntry(id)
            },
            onAddEntry = { pickingLocation = true },
            modifier = modifier,
        )
    }
}

/**
 * Which screen [JournalTab] shows for [MushroomLogUiState.editingEntry] — "editing" is the accurate
 * name for what that field means (see [MushroomLogViewModel]'s doc comment on autosave), but which
 * of [LogEntryReportScreen]/[LogEntryDetailScreen] the *user* sees for it depends on how they got
 * there, tracked here rather than inferred from the entry's own content (an entry with nothing
 * recorded yet is a legitimate thing to view a report of too, once the user backs out of editing it
 * without filling anything in — REPORT stays correct for that case where "does it have data" would
 * not).
 */
private enum class JournalEntryMode { REPORT, EDIT }

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
                MapOverlayContent(),
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
