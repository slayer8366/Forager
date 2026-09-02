package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.ui.theme.Spacing

/**
 * The Cartography Entries list's default view for a **committed** entry — Journal Stage 2c. Recounts
 * exactly what [entry] already stores: the user's own writing and tags if any, and the snapshotted
 * text for every *kept* find, track, waypoint, offline region, and photo — never a candidate that
 * was withheld or left undecided, since this screen has no notion of "candidates" at all. See
 * [CartographyScreen]'s own doc comment for why: [CartographyEntryEditScreen] is what merges a live
 * trip report against this entry's decisions, and this screen deliberately never does — it renders
 * **only** from [entry]'s own persisted fields, no repository lookups, no live fetches, the same
 * "text is snapshotted, not re-derived" reasoning [CartographyEntry]'s own doc comment states for why
 * a kept track's name/distance/duration/point-count survive the track's own deletion. That is also
 * why a dangling reference (a kept item whose underlying track/waypoint/offline-region/find has since
 * been deleted from Records — reachable any time, since the 4b-style deletion warning never blocks)
 * can never produce an error state here: every line below reads straight off [entry], never resolves
 * an id against anything that could be gone.
 *
 * Routing is mode-based, see [CartographyScreen]'s own local `CartographyEntryMode` — tapping an
 * entry in the Entries tab opens this screen; tapping "Edit entry" in the overflow menu below
 * switches to [CartographyEntryEditScreen] on the same [entry]. A draft never reaches this screen at
 * all — [CartographyScreen] routes a draft straight into the editor, since an unfinished entry is
 * something to finish, not something to view a report of yet (mirroring [LogEntryReportScreen]'s own
 * "a brand-new entry... goes straight to editing").
 *
 * **Delete confirms here**, unlike [LogEntryReportScreen]'s own immediate delete — a Cartography
 * entry's writing and curation decisions exist nowhere else (a find can be re-logged; this can't),
 * and there is no trash/undo yet, so this matches [CartographyEntryEditScreen]'s own existing
 * confirmation rather than [LogEntryReportScreen]'s shape on this one point (Stage 2c dispatch,
 * planner decision).
 *
 * **Never styled or labelled as unfinished** (`amendment-2b-optional-writing.md`, restated for Stage
 * 2c): unlike [LogEntryReportScreen]'s own [ReportSection] (which prints "Not recorded yet." for an
 * empty section, since a `MushroomLogEntry` accumulates its many fields gradually and being
 * incomplete is expected), a `CartographyEntry` section with nothing kept is omitted entirely, not
 * shown empty — an entry made only of two kept photos must read as a complete entry about two
 * photos, not as a form with blanks. Writing and tags are the same: shown only if non-blank/non-empty,
 * with no placeholder line when they're not.
 *
 * Photos reuse [KeptPhotoOrUnavailable] (the exact same live-gallery-lookup-with-fallback
 * [CartographyEntryEditScreen]'s own `KeptPhotosSection` uses) rather than a second copy of that
 * fallback — Stage 2c dispatch, point 5.
 */
@Composable
internal fun CartographyEntryReportScreen(
    entry: CartographyEntry,
    galleryPhotos: List<GalleryPhoto>,
    distanceUnit: DistanceUnit,
    onEdit: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    var confirmingDelete by remember(entry.id) { mutableStateOf(false) }

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
                        text = { Text("Edit entry") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
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
            entry.text.takeIf { it.isNotBlank() }?.let { text ->
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }

            if (entry.tags.isNotEmpty()) {
                Text("Tags: ${entry.tags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }

            if (entry.photos.isNotEmpty()) {
                val photosById = galleryPhotos.associateBy { it.photo.id }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    entry.photos.forEach { attachment ->
                        KeptPhotoOrUnavailable(
                            attachment = attachment,
                            photo = photosById[attachment.photoId],
                            modifier = Modifier.size(KEPT_PHOTO_SIZE_DP.dp),
                        )
                    }
                }
            }

            if (entry.text.isNotBlank() || entry.tags.isNotEmpty() || entry.photos.isNotEmpty()) {
                HorizontalDivider()
            }

            ReportItemsSection(
                title = "Finds",
                items = entry.findDecisions.filter { it.kept }.map { ReportItem(title = "Find on ${it.foundOn}", subtitle = it.ownIdentification) },
            )
            ReportItemsSection(
                title = "Tracks",
                items = entry.trackDecisions.filter { it.kept }.map {
                    ReportItem(title = it.name ?: "Recorded track", subtitle = trackSubtitle(it.distanceMeters, it.durationMillis, distanceUnit))
                },
            )
            ReportItemsSection(
                title = "Waypoints",
                items = entry.waypointDecisions.filter { it.kept }.map {
                    ReportItem(title = it.name, subtitle = "${"%.4f".format(it.lat)}, ${"%.4f".format(it.lng)}")
                },
            )
            ReportItemsSection(
                title = "Offline Regions",
                items = entry.offlineRegionDecisions.filter { it.kept }.map {
                    ReportItem(title = it.name, subtitle = formatDistanceKm(it.radiusKm, distanceUnit))
                },
            )

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

private data class ReportItem(val title: String, val subtitle: String?)

/** One category of kept items — omitted entirely when [items] is empty, never shown with a "nothing yet" placeholder. See this file's own doc comment for why. */
@Composable
private fun ReportItemsSection(title: String, items: List<ReportItem>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        items.forEach { item ->
            Column {
                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
