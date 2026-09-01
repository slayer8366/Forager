package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.forager.app.domain.ComputeTrackStatisticsUseCase
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DerivedTrip
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * A Cartography entry's curation-and-writing surface — Journal Stage 2b. Two modes sharing one
 * layout, distinguished only by [candidates]:
 *
 * - **Curation** ([candidates] non-null, only right after [CartographyViewModel.onStartEntry]): every
 *   candidate the day's trip report produced is listed, defaulting to kept — "withholding is a
 *   first-class operation, not a filter." Tapping **Withhold** on a kept row is the deliberate act;
 *   tapping **Keep** restores it. See [CartographyViewModel]'s own doc comment for why this mode is
 *   creation-time only.
 * - **Editing** ([candidates] `null`, every reopen): only the entry's own already-kept items show,
 *   remove-only.
 *
 * **No field here is required, and nothing here reads as incomplete for being empty** —
 * `amendment-2b-optional-writing.md`: selection alone is a complete act of authorship, prose is one
 * optional component. The writing field's own placeholder says "optional" rather than prompting
 * completion, and [onFinish] (shown only while [CartographyEntry.isDraft]) never gates on [entry.text]
 * or any kept-item count.
 */
@Composable
internal fun CartographyEntryEditScreen(
    entry: CartographyEntry,
    candidates: DerivedTrip?,
    candidateOfflineRegions: List<OfflineRegionSummary>,
    galleryPhotos: List<GalleryPhoto>,
    distanceUnit: DistanceUnit,
    onTextChanged: (String) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
    onToggleKeptFind: (String) -> Unit,
    onToggleKeptTrack: (String) -> Unit,
    onToggleKeptWaypoint: (String) -> Unit,
    onToggleKeptOfflineRegion: (Long) -> Unit,
    onToggleKeptPhoto: (String) -> Unit,
    onFinish: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pullingPhoto by remember(entry.id) { mutableStateOf(false) }
    if (pullingPhoto) {
        PullPhotoPickerScreen(
            photos = galleryPhotos,
            onPhotoSelected = { photo -> onToggleKeptPhoto(photo.id); pullingPhoto = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    var confirmingDelete by remember(entry.id) { mutableStateOf(false) }
    var tagsText by remember(entry.id) { mutableStateOf(entry.tags.joinToString(", ")) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Cartography")
                }
                Text(entry.date.toString(), style = MaterialTheme.typography.titleMedium)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Entry options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete entry") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; confirmingDelete = true },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutlinedTextField(
                value = entry.text,
                onValueChange = onTextChanged,
                label = { Text("Your own account (optional)") },
                placeholder = { Text("Let the photos and selections speak, or write about the day.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            OutlinedTextField(
                value = tagsText,
                onValueChange = { text ->
                    tagsText = text
                    onTagsChanged(text.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                },
                label = { Text("Tags (optional, comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()

            KeptPhotosSection(
                entry = entry,
                galleryPhotos = galleryPhotos,
                onToggleKeptPhoto = onToggleKeptPhoto,
                onAddFromAlbum = { pullingPhoto = true },
            )

            KeptFindsSection(entry = entry, candidates = candidates, onToggle = onToggleKeptFind)
            KeptTracksSection(entry = entry, candidates = candidates, distanceUnit = distanceUnit, onToggle = onToggleKeptTrack)
            KeptWaypointsSection(entry = entry, candidates = candidates, onToggle = onToggleKeptWaypoint)
            KeptOfflineRegionsSection(
                entry = entry,
                candidates = candidateOfflineRegions,
                distanceUnit = distanceUnit,
                onToggle = onToggleKeptOfflineRegion,
            )

            if (entry.isDraft) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish entry") }
            }

            Spacer(modifier = Modifier.heightIn(min = Spacing.lg))
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This removes the entry and its kept selections. The finds, tracks, waypoints, and regions it kept stay in Records.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDeleteEntry() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeptPhotosSection(
    entry: CartographyEntry,
    galleryPhotos: List<GalleryPhoto>,
    onToggleKeptPhoto: (String) -> Unit,
    onAddFromAlbum: () -> Unit,
) {
    val photosById = galleryPhotos.associateBy { it.photo.id }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Photos", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            entry.keptPhotos.forEach { ref ->
                val photo = photosById[ref.photoId]
                Box {
                    if (photo != null) {
                        DecodedPhoto(relativePath = photo.photo.relativePath, modifier = Modifier.size(KEPT_PHOTO_SIZE_DP.dp))
                    } else {
                        // The GalleryPhoto row is gone — see CartographyEntryPhotoRefEntity's own doc
                        // comment for why this stays visible with its attach date rather than
                        // silently vanishing.
                        Box(
                            modifier = Modifier.size(KEPT_PHOTO_SIZE_DP.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Photo unavailable\n(attached ${attachDateLabel(ref.attachedAtEpochMillis)})",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleKeptPhoto(ref.photoId) },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove this photo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            IconButton(onClick = onAddFromAlbum) {
                Icon(Icons.Filled.Add, contentDescription = "Add a photo from the Album")
            }
        }
    }
}

@Composable
private fun KeptFindsSection(entry: CartographyEntry, candidates: DerivedTrip?, onToggle: (String) -> Unit) {
    val rows = if (candidates != null) {
        candidates.finds.map { find ->
            KeptRowState(
                id = find.id,
                title = "Find on ${find.foundOn}",
                subtitle = find.ownIdentification,
                kept = entry.keptFinds.any { it.findId == find.id },
            )
        }
    } else {
        entry.keptFinds.map { ref -> KeptRowState(id = ref.findId, title = "Find on ${ref.foundOn}", subtitle = ref.ownIdentification, kept = true) }
    }
    KeptItemSection(title = "Finds", rows = rows, onToggle = onToggle)
}

@Composable
private fun KeptTracksSection(entry: CartographyEntry, candidates: DerivedTrip?, distanceUnit: DistanceUnit, onToggle: (String) -> Unit) {
    val rows = if (candidates != null) {
        candidates.tracks.map { track ->
            // Recomputed directly rather than cached: this only runs during curation (creation-time
            // only, see CartographyViewModel's own doc comment), not a hot recomposition path, and a
            // day's track list is small — the same one-query/one-computation-per-item scale
            // TrackRecordingViewModel.loadWaypoints' own doc comment accepts for 4b's counts.
            val stats = ComputeTrackStatisticsUseCase()(track.points)
            KeptRowState(
                id = track.id,
                title = track.name ?: "Recorded track",
                subtitle = trackSubtitle(stats.distanceMeters, stats.durationMillis, distanceUnit),
                kept = entry.keptTracks.any { it.trackId == track.id },
            )
        }
    } else {
        entry.keptTracks.map { ref ->
            KeptRowState(
                id = ref.trackId,
                title = ref.name ?: "Recorded track",
                subtitle = trackSubtitle(ref.distanceMeters, ref.durationMillis, distanceUnit),
                kept = true,
            )
        }
    }
    KeptItemSection(title = "Tracks", rows = rows, onToggle = onToggle)
}

@Composable
private fun KeptWaypointsSection(entry: CartographyEntry, candidates: DerivedTrip?, onToggle: (String) -> Unit) {
    val rows = if (candidates != null) {
        candidates.waypoints.map { waypoint ->
            KeptRowState(
                id = waypoint.id,
                title = waypoint.name,
                subtitle = "${"%.4f".format(waypoint.lat)}, ${"%.4f".format(waypoint.lng)}",
                kept = entry.keptWaypoints.any { it.waypointId == waypoint.id },
            )
        }
    } else {
        entry.keptWaypoints.map { ref ->
            KeptRowState(id = ref.waypointId, title = ref.name, subtitle = "${"%.4f".format(ref.lat)}, ${"%.4f".format(ref.lng)}", kept = true)
        }
    }
    KeptItemSection(title = "Waypoints", rows = rows, onToggle = onToggle)
}

@Composable
private fun KeptOfflineRegionsSection(
    entry: CartographyEntry,
    candidates: List<OfflineRegionSummary>,
    distanceUnit: DistanceUnit,
    onToggle: (Long) -> Unit,
) {
    // candidates is only ever non-empty during curation (see CartographyViewModel.onStartEntry) —
    // an empty list during a reopen is indistinguishable from "no candidates loaded," which is
    // exactly the reopen case, so the same "fall back to entry's own kept list" branch below
    // handles both correctly without a separate nullable-vs-empty signal.
    val rows = if (candidates.isNotEmpty()) {
        candidates.map { region ->
            KeptRowState(
                id = region.id.toString(),
                title = region.name,
                subtitle = formatDistanceKm(region.region.radiusKm, distanceUnit),
                kept = entry.keptOfflineRegions.any { it.offlineRegionId == region.id },
            )
        }
    } else {
        entry.keptOfflineRegions.map { ref ->
            KeptRowState(id = ref.offlineRegionId.toString(), title = ref.name, subtitle = formatDistanceKm(ref.radiusKm, distanceUnit), kept = true)
        }
    }
    KeptItemSection(title = "Offline Regions", rows = rows) { id -> onToggle(id.toLong()) }
}

private data class KeptRowState(val id: String, val title: String, val subtitle: String?, val kept: Boolean)

@Composable
private fun KeptItemSection(title: String, rows: List<KeptRowState>, onToggle: (String) -> Unit) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        rows.forEach { row -> KeptItemRow(row = row, onToggle = { onToggle(row.id) }) }
    }
}

/**
 * One kept/withheld candidate row — the withhold interaction itself. A kept row reads normally with
 * a **Withhold** action; a withheld row (only reachable during curation, since a reopen only ever
 * shows already-kept items) reads visibly dimmed and struck through with a **Keep** action, so
 * withholding shows as a deliberate, reversible-in-session act rather than an unchecked filter box.
 */
@Composable
private fun KeptItemRow(row: KeptRowState, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().alpha(if (row.kept) 1f else 0.5f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (row.kept) TextDecoration.None else TextDecoration.LineThrough,
                )
                row.subtitle?.let { subtitle -> Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onToggle) { Text(if (row.kept) "Withhold" else "Keep") }
        }
    }
}

private fun trackSubtitle(distanceMeters: Double, durationMillis: Long, distanceUnit: DistanceUnit): String {
    val km = (distanceMeters / 1000.0).roundToInt()
    val distanceLabel = formatDistanceKm(km, distanceUnit)
    val totalMinutes = durationMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    return "$distanceLabel · $durationLabel"
}

private fun attachDateLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private const val KEPT_PHOTO_SIZE_DP = 88
