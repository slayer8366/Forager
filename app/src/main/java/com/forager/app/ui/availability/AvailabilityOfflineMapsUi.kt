package com.forager.app.ui.availability

// Split-AvailabilityScreen Stage E: offline maps, moved verbatim out of AvailabilityScreen.kt —
// one contiguous block, lines 2449-2817 of the file as of 8d9cd1f: OfflineMapsPanel,
// MAP_PICKER_ASPECT_RATIO, OFFLINE_MAP_PICKER_DEFAULT_CENTER, JOURNAL_PICKER_DEFAULT_REGION,
// OfflineDownloadStatusContent, OfflineRegionsSection, OfflineRegionRow. Same package as Stages
// A, C and D, for the same reason: RecordsTab (log package) imports and composes OfflineMapsPanel,
// already internal, and that import resolves unchanged. Pure move: no signature, name or body
// changed. One widening, private -> internal: JOURNAL_PICKER_DEFAULT_REGION, whose four callers
// (the journal pickers' fallback region) stay in AvailabilityScreen.kt. No symbol left behind is
// reached from here.
//
// Two things worth knowing rather than re-deriving:
// - For the pending offline style swap (Stage 2e-ii, see MapSlot.kt's deliberately inert
//   offline-region parameter and MapLibreOfflineMapRepository's OFFLINE_STYLE_URL): the region
//   picker below pins `basemap = Basemap.OPEN_TOPO_MAP` in its CentrePinLocationPicker call. If
//   the offline style is meant to show inside the picker, that pin is the exact line 2e-ii
//   touches. This move changed its file, not its shape. Nothing here touches MapLibre
//   initialisation (initializeMapLibre lives in MapLibreStorage.kt and is called only from
//   SightingsMap and MapLibreOfflineMapRepository); this panel reaches the map through MapSlot.
// - CartographyEntryEditScreen (log package) has its own *private* function also named
//   OfflineRegionsSection. It is a different function, not a reference to the one here — a grep
//   coincidence, not a dependency.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.estimateOfflineTileCount
import com.forager.app.domain.isOfflineRegionStale
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.ui.log.JournalTab
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.CentrePinLocationPicker
import com.forager.app.ui.map.MapMode
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.theme.Spacing


/**
 * The "Offline Maps" submenu: an interactive topo map to pick a download region via the
 * centre-pin picker, the region's radius, current status, and the Download/Delete actions.
 *
 * Always downloads from the same one fixed source, unconditionally — see
 * `com.forager.app.domain.OfflineMapRepository`'s doc comment for why this is no longer gated on,
 * or reactive to, [MapMode]/the quick-fire map mode picker: the project owner's own call was that
 * offline downloads should "assume [a fixed source] and [be] ready to function" regardless of
 * either. That fixed source is `com.forager.app.map.MapLibreOfflineMapRepository`'s Cloudflare
 * Worker now, not USGS — this panel's own picker map below is unrelated to that choice, see the
 * next paragraph.
 *
 * ## Picking a region via [CentrePinLocationPicker]
 *
 * [onRegionPicked] fires from [CentrePinLocationPicker]'s own OK button — see that composable's
 * class doc comment for why every location-placing site in this app, this one included, replaced
 * long-press with a fixed centre pin (an accessibility decision, not a style one). There is no
 * name-and-date dialog in between OK and the pick landing: a confirmed point becomes the region's
 * centre immediately, since there is nothing else to ask the user for. [Basemap.OPEN_TOPO_MAP] here
 * is only terrain context for choosing *where* to download — this picker map is unrelated to which
 * source the download itself actually reads from underneath. Was `Basemap.USGS_TOPO` (US-only)
 * until [MapMode] removed it from the app entirely; [Basemap.OPEN_TOPO_MAP] is the worldwide
 * equivalent, a better fit for a picker with no reason to inherit a US-only limit it never needed.
 *
 * Before anything is confirmed, [uiState]'s `offlineMapLatText`/`offlineMapLngText` are blank, so
 * the picker centres on [OFFLINE_MAP_PICKER_DEFAULT_CENTER] purely so there is a map to navigate
 * — not a claim about where the user is or wants to download. "Download Maps" stays disabled until
 * a real point has been confirmed (see `hasValidRegion` below), so that default viewport can never
 * itself be submitted as a region.
 *
 * The map keeps a fixed aspect ratio rather than filling leftover space — see the `Box` below's
 * own comment for why `weight(1f)` stopped working once this whole panel became one scrolling unit.
 */
@Composable
internal fun OfflineMapsPanel(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    mapSlot: MapSlot,
    /** Night mode for the region picker this panel hosts — see [CentrePinLocationPicker]. */
    isNightMode: Boolean,
    onRegionPicked: (LatLng) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onOfflineMapNameChanged: (String) -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
) {
    val pickedLat = uiState.offlineMapLatText.toDoubleOrNull()
    val pickedLng = uiState.offlineMapLngText.toDoubleOrNull()
    val hasValidRegion = pickedLat != null && pickedLat in -90.0..90.0 && pickedLng != null && pickedLng in -180.0..180.0
    val defaultCenter = uiState.offlineMapPickerDefaultCenter ?: OFFLINE_MAP_PICKER_DEFAULT_CENTER
    val now = currentTime.nowEpochMillis()

    // The whole panel scrolls as one unit now that OfflineRegionsSection's list has no bound on
    // its own length — a fixed-aspect-ratio picker map (below) plus a growing region list can
    // exceed whatever height this panel's own parent hands it (Modifier.weight(1f) from the drawer
    // sheet's Column, the same pattern SearchControls already uses for its own scroll in that same
    // parent), so verticalScroll here is meaningful rather than a no-op: weight(1f) gives a bounded,
    // not infinite, height to scroll within.
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            "Offline downloads cover the continental United States with vector map data. " +
                "Pan the map below to position the pin, then tap OK to choose where to download.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )

        val pickerRegion = Region(
            lat = pickedLat ?: defaultCenter.lat,
            lng = pickedLng ?: defaultCenter.lng,
            radiusKm = uiState.offlineMapRadiusKm,
        )
        // A fixed aspect ratio, not weight(1f): the picker map used to claim all leftover space in
        // an unscrolled panel, but a panel that now scrolls as a whole has no "leftover space" for
        // weight to resolve against.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(MAP_PICKER_ASPECT_RATIO)) {
            CentrePinLocationPicker(
                mapSlot = mapSlot,
                region = pickerRegion,
                basemap = Basemap.OPEN_TOPO_MAP,
                night = isNightMode,
                onConfirm = onRegionPicked,
                // Nothing to cancel back to: this panel had no confirm step before this picker
                // existed either — the offlineMapLatText/offlineMapLngText fields just keep
                // whatever they already held (blank, or a prior confirmed pick).
                onCancel = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                if (hasValidRegion) {
                    "Download region: ${"%.4f".format(pickedLat)}, ${"%.4f".format(pickedLng)}"
                } else {
                    "No location picked yet — pan the map above and tap OK."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = uiState.offlineMapNameText,
                onValueChange = onOfflineMapNameChanged,
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Radius: ${formatDistanceKm(uiState.offlineMapRadiusKm, distanceUnit)}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = uiState.offlineMapRadiusKm.toFloat(),
                onValueChange = { onOfflineMapRadiusChanged(it.toInt()) },
                valueRange = Region.MIN_RADIUS_KM.toFloat()..Region.MAX_RADIUS_KM.toFloat(),
                steps = Region.MAX_RADIUS_KM - Region.MIN_RADIUS_KM - 1,
            )

            // So the tile budget is discovered here, while there's still time to pick a smaller
            // radius, rather than only on a refused download — a user should not discover the
            // ceiling at a trailhead.
            val estimatedTiles = estimateOfflineTileCount(pickerRegion, OfflineMapRepository.MIN_ZOOM, OfflineMapRepository.MAX_ZOOM)
            val remainingBudget = OfflineMapRepository.TILE_COUNT_LIMIT - uiState.offlineRegions.sumOf { it.tileCount }
            val exceedsBudget = estimatedTiles > remainingBudget
            Text(
                if (exceedsBudget) {
                    "~$estimatedTiles tiles — exceeds your remaining budget of $remainingBudget"
                } else {
                    "~$estimatedTiles tiles"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (exceedsBudget) MaterialTheme.colorScheme.error else Color.Unspecified,
            )

            OfflineDownloadStatusContent(uiState.offlineDownloadStatus)

            val isDownloading = uiState.offlineDownloadStatus is OfflineMapStatus.Downloading
            Button(
                onClick = onDownloadOfflineMaps,
                enabled = hasValidRegion && !isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download Maps") }
        }

        HorizontalDivider()

        OfflineRegionsSection(
            regions = uiState.offlineRegions,
            errorMessage = uiState.offlineRegionsErrorMessage,
            staleThresholdDays = uiState.offlineStaleThresholdDays,
            distanceUnit = distanceUnit,
            nowEpochMillis = now,
            onDeleteOfflineRegion = onDeleteOfflineRegion,
            entryReferenceCounts = uiState.offlineRegionEntryReferenceCounts,
        )
    }
}

/** The picker map's fixed width:height ratio — see [OfflineMapsPanel]'s doc comment for why this replaced `Modifier.weight(1f)`. */
private const val MAP_PICKER_ASPECT_RATIO = 4f / 3f

/**
 * An arbitrary opening viewport for [OfflineMapsPanel]'s picker map before a region has been
 * picked — the geographic center of the contiguous United States (near Lebanon, Kansas), since
 * offline downloads only ever cover the continental-US PMTiles archive
 * `com.forager.app.map.MapLibreOfflineMapRepository` reads from. Not a default region and never
 * submitted as one: "Download Maps" stays disabled until the centre pin has been confirmed with OK.
 */
private val OFFLINE_MAP_PICKER_DEFAULT_CENTER = LatLng(39.8283, -98.5795)

/**
 * [JournalTab]'s location-picker fallback viewport, for whenever no region has ever been searched
 * — reuses [OFFLINE_MAP_PICKER_DEFAULT_CENTER] rather than inventing a second "nowhere in
 * particular to start from" default. `radiusKm` here only sets the picker's opening zoom level
 * ([SightingsMap] derives zoom from it); it is never submitted anywhere, the same way the offline
 * picker's own default centre never is.
 */
internal val JOURNAL_PICKER_DEFAULT_REGION =
    Region(lat = OFFLINE_MAP_PICKER_DEFAULT_CENTER.lat, lng = OFFLINE_MAP_PICKER_DEFAULT_CENTER.lng, radiusKm = 15)

/**
 * What [OfflineMapsPanel]'s picker shows for its own last download attempt — every branch says
 * something, per CLAUDE.md, except [OfflineMapStatus.Idle]/[OfflineMapStatus.Succeeded], which
 * deliberately render nothing: a completed download is already reflected in
 * [OfflineRegionsSection]'s list right below, so there is nothing left for this transient status to
 * say once it succeeds.
 */
@Composable
private fun OfflineDownloadStatusContent(status: OfflineMapStatus) {
    when (status) {
        OfflineMapStatus.Idle, OfflineMapStatus.Succeeded -> Unit

        is OfflineMapStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (status.total > 0) {
                LinearProgressIndicator(
                    progress = { status.downloaded.toFloat() / status.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${status.downloaded} / ${status.total} tiles", style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Starting download…", style = MaterialTheme.typography.bodySmall)
            }
        }

        is OfflineMapStatus.Failed -> Text(
            status.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Every region currently on disk: name, centre, radius, size, download date, the zoom-readiness
 * note main's old single-region `Downloaded` branch used to carry (see [OfflineRegionRow]'s own
 * doc comment for where that text landed), and per-region delete. [errorMessage] surfaces a read
 * failure without clearing whatever was last successfully loaded — see
 * [AvailabilityViewModel.loadOfflineRegions][com.forager.app.ui.availability.AvailabilityViewModel.loadOfflineRegions].
 *
 * [errorMessage] renders with no error color, deliberately: per the error-presentation spec, a
 * region-list-load failure (or a failed delete, which surfaces through the same field) isn't
 * belief-changing the way a failed download is — the user isn't mid-action, they just want to see
 * what's on disk, so this matches the neutral "Rainfall data unavailable"-style treatment other
 * read failures in this screen already use, not [OfflineDownloadStatusContent]'s error-red.
 *
 * The tile-budget line and the "sizes don't add up" caveat: [OfflineMapRepository.TILE_COUNT_LIMIT]
 * is the ceiling this app sets deliberately (see that constant's doc comment), and the caveat
 * exists because the resource table dedupes tiles across overlapping regions, so summed per-region
 * tile counts overstate real disk usage and a delete can free far less than its region's own
 * reported size — this text deliberately never promises a specific amount reclaimed.
 *
 * Deleting a downloaded region is not reversible without re-downloading it, so each row's "Delete"
 * button opens a confirmation dialog ([pendingDeleteRegion]) rather than deleting immediately on tap.
 */
@Composable
private fun OfflineRegionsSection(
    regions: List<OfflineRegionSummary>,
    errorMessage: String?,
    staleThresholdDays: Int,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onDeleteOfflineRegion: (Long) -> Unit,
    /** How many Cartography entries currently keep a reference to each region (by id) — Journal Stage 2b's 4b deletion warning, shown in the confirm dialog below. */
    entryReferenceCounts: Map<Long, Int> = emptyMap(),
) {
    var pendingDeleteRegion by remember { mutableStateOf<OfflineRegionSummary?>(null) }

    // No scroll/height cap of its own: OfflineMapsPanel's whole Column scrolls as one unit (see
    // its doc comment), so this section just renders at its natural height as the last thing in
    // that scroll.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Downloaded Maps", style = MaterialTheme.typography.titleSmall)

        val tilesUsed = regions.sumOf { it.tileCount }
        Text(
            "Tile budget: $tilesUsed / ${OfflineMapRepository.TILE_COUNT_LIMIT}. Sizes don't add up to " +
                "total disk usage — overlapping regions share tiles, so deleting one may free less " +
                "than its own size suggests.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall)
        }

        if (regions.isEmpty()) {
            Text("No regions downloaded yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            regions.forEach { region ->
                OfflineRegionRow(
                    region = region,
                    isStale = isOfflineRegionStale(region.createdAtEpochMillis, nowEpochMillis, staleThresholdDays),
                    distanceUnit = distanceUnit,
                    nowEpochMillis = nowEpochMillis,
                    onDelete = { pendingDeleteRegion = region },
                )
            }
        }
    }

    pendingDeleteRegion?.let { region ->
        val referencingEntryCount = entryReferenceCounts[region.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingDeleteRegion = null },
            title = { Text("Delete \"${region.name}\"?") },
            text = {
                Text(
                    // No permanence claim (a future trash lands this becoming false) — states the
                    // consequence, not that it's irreversible. See amendment-2b-finds-and-trash.md.
                    if (referencingEntryCount > 0) {
                        "This region appears in $referencingEntryCount ${if (referencingEntryCount == 1) "journal entry" else "journal entries"}. " +
                            "This deletes the downloaded map tiles for this region. You can re-download it later."
                    } else {
                        "This deletes the downloaded map tiles for this region. You can re-download it later."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteOfflineRegion(region.id)
                        pendingDeleteRegion = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteRegion = null }) { Text("Cancel") } },
        )
    }
}

/**
 * One downloaded region's row in [OfflineRegionsSection].
 *
 * Carries the zoom-readiness note main's old single-region `OfflineMapStatusContent` used to show
 * in its `Downloaded` branch — per Workstream B's dispatch, the information moves here rather than
 * being dropped: [OfflineMapStatus.Succeeded] (this panel's new download-attempt status) is a bare
 * marker with no region data left to attach it to, and every completed region in this list is
 * exactly the thing that text was originally describing, so it's reworded to apply per-row instead
 * of to "the one download that just finished."
 */
@Composable
private fun OfflineRegionRow(
    region: OfflineRegionSummary,
    isStale: Boolean,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(region.name, style = MaterialTheme.typography.bodyMedium)
                if (isStale) {
                    Text("Stale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "${formatDistanceKm(region.region.radiusKm, distanceUnit)} around " +
                    "${"%.4f".format(region.region.lat)}, ${"%.4f".format(region.region.lng)} — " +
                    "${region.tileCount} tiles, ${"%.1f".format(region.sizeBytes / 1_000_000.0)} MB — " +
                    "downloaded ${relativeTimeLabel(region.createdAtEpochMillis, nowEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Ready to zoom ${region.maxZoom.toInt()}: zoom ${region.minZoom.toInt()}–${region.maxZoom.toInt() - 1} " +
                    "from the archive, zoom ${region.maxZoom.toInt()} detail fetched live from Protomaps when this " +
                    "region downloaded — a region that shows here has both, since a zoom-${region.maxZoom.toInt()} " +
                    "fetch failure fails the whole download rather than silently completing without it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onDelete) { Text("Delete") }
    }
}

