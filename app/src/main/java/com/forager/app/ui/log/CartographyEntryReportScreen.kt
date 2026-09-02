package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.forager.app.domain.GeoDistance
import com.forager.app.domain.CartographyEntryMapData
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.MapOverlayContent
import com.forager.app.ui.map.MapRenderMode
import com.forager.app.ui.map.MapSlot
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
 *
 * **[isEntirelyEmpty]** (Stage 2d): omitting every empty category, as above, leaves a fully-empty
 * entry as a date and nothing else — the same dead end [LogEntryReportScreen] hits, and the same
 * fix in spirit, but **not the same words**: a find is an observation record with taxonomic fields;
 * a Cartography entry is a day assembled from that day's records. [EMPTY_ENTRY_MESSAGE] is written
 * for what this screen actually holds — see that constant's own doc comment for the exact wording
 * and why. Fires only when *nothing at all* is kept and both [CartographyEntry.text]/
 * [CartographyEntry.tags] are empty — a wordless entry with kept photos is complete per the standing
 * "writing is never required" rule and gets no message, exactly like [entry.photos]-only already
 * renders correctly above without one.
 *
 * ## The map (Journal Stage 2d)
 *
 * Above the snapshotted text, framed on whatever of [entry]'s kept data actually resolved.
 * [getMapData] is the one live-fetch seam this screen has — a plain suspend function, not the whole
 * [com.forager.app.domain.GetCartographyEntryMapDataUseCase]/repository graph threaded through,
 * matching this codebase's own established "a plain suspend lambda, not the dependency it closes
 * over" shape (`MushroomLogViewModel.getPhotoEntryReferenceCount`,
 * `TrackRecordingViewModel.getWaypointReferenceCount`). Fired once per [entry], in a
 * `LaunchedEffect` keyed on [CartographyEntry.id] — this screen's own render body still touches no
 * repository directly, only the resolved [CartographyEntryMapData] once it lands, so the Stage 2c
 * "no live fetches" rule still describes this composable's *render*, just not the composable's
 * *lifecycle* as a whole (Stage 2d necessarily changes that half — see the dispatch's own table for
 * why a live fetch is unavoidable for tracks/finds/photos).
 *
 * **No map section at all while loading, or if nothing resolved** ([CartographyEntryMapData.isEmpty]) —
 * never an empty map frame with nothing on it. An entry made entirely of photos with no coordinates
 * is a real, reachable state, reported as "nothing to frame" rather than guessing a default location
 * (see [GeoDistance.boundingRegion]'s own doc comment). `MapRenderMode.trackLiveLocation` is `false`
 * here specifically — see that field's own doc comment for the real bug this avoids (the map seizing
 * the camera for the device's *current* location the instant permission is granted, overriding the
 * framing computed here for what is, after all, a historical place).
 */
@Composable
internal fun CartographyEntryReportScreen(
    entry: CartographyEntry,
    galleryPhotos: List<GalleryPhoto>,
    distanceUnit: DistanceUnit,
    mapSlot: MapSlot,
    basemap: Basemap,
    night: Boolean,
    getMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    onEdit: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    var confirmingDelete by remember(entry.id) { mutableStateOf(false) }
    var mapData by remember(entry.id) { mutableStateOf<CartographyEntryMapData?>(null) }

    LaunchedEffect(entry.id) {
        mapData = getMapData(entry, galleryPhotos)
    }

    val isEntirelyEmpty = entry.text.isBlank() &&
        entry.tags.isEmpty() &&
        entry.photos.isEmpty() &&
        entry.findDecisions.none { it.kept } &&
        entry.trackDecisions.none { it.kept } &&
        entry.waypointDecisions.none { it.kept } &&
        entry.offlineRegionDecisions.none { it.kept }

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

        val resolvedMapData = mapData
        val mapRegion = resolvedMapData?.takeUnless { it.isEmpty }?.let { GeoDistance.boundingRegion(it.allPoints) }
        if (resolvedMapData != null && mapRegion != null) {
            Box(modifier = Modifier.fillMaxWidth().height(CARTOGRAPHY_MAP_HEIGHT_DP.dp).testTag(CARTOGRAPHY_MAP_TEST_TAG)) {
                mapSlot(
                    mapRegion,
                    MapOverlayContent(
                        // Synthesized, id/name/note carry no real data — this map has no tap handling,
                        // so nothing ever reads them; only lat/lng reach the layer's own GeoJSON feature.
                        waypoints = resolvedMapData.waypointMarkers.mapIndexed { index, point ->
                            Waypoint(
                                id = "cartography-map-waypoint-$index",
                                lat = point.lat,
                                lng = point.lng,
                                altitude = null,
                                name = "Waypoint",
                                note = "",
                                createdAtEpochMillis = 0L,
                            )
                        },
                        keptTrackPolylines = resolvedMapData.trackPolylines,
                        findMarkers = resolvedMapData.findMarkers,
                        photoMarkers = resolvedMapData.photoMarkers,
                        offlineRegionCircles = resolvedMapData.offlineRegionCircles,
                    ),
                    MapRenderMode(basemap = basemap, night = night, trackLiveLocation = false),
                    null,
                    {},
                    {},
                    { _, _, _ -> },
                    {},
                    Modifier.fillMaxSize(),
                )
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
            if (isEntirelyEmpty) {
                Text(EMPTY_ENTRY_MESSAGE, style = MaterialTheme.typography.bodyMedium)
            } else {
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

/**
 * Reported verbatim per the Stage 2d dispatch's own instruction ("write the wording yourself... and
 * report the exact string you used, so the owner can adjust it") — a `const val` so the exact text
 * is visible in code, not buried in a chained string-builder call. Deliberately not a copy of
 * [LogEntryReportScreen]'s own empty-state text: a find and a Cartography entry hold different
 * things (a find is an observation record with taxonomic fields; a Cartography entry is a day
 * assembled from that day's records), so this names what an entry actually holds rather than
 * echoing the find screen's wording.
 */
private const val EMPTY_ENTRY_MESSAGE =
    "This entry has nothing kept yet. An entry can hold the finds, tracks, waypoints, offline " +
        "regions, and photos you choose to keep from a day's records, plus anything you write. " +
        "Tap the three-dot menu, then Edit, to add something."

/**
 * The map's fixed height above the scrollable text — Journal Stage 2d. Not [Modifier.weight], since
 * the scrollable [Column] below it keeps its own load-bearing `weight(1f)` (CLAUDE.md, restated in
 * this file's own dispatch: a `Surface`/pointer-input-owning composable stretching to fill an
 * unconstrained container silently swallows touches meant for content below it — the map is exactly
 * such a composable here, so it gets an explicit bound rather than sharing the weight).
 */
private const val CARTOGRAPHY_MAP_HEIGHT_DP = 200

/** Lets tests distinguish "the map section rendered" from "nothing resolved, no map section at all" without depending on [mapSlot]'s own real content — see this file's own doc comment, "No map section at all while loading, or if nothing resolved." */
internal const val CARTOGRAPHY_MAP_TEST_TAG = "cartography-entry-map"

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
