package com.zynergylabs.forager.app.ui.availability

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

import android.content.res.Configuration
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.adaptive.currentWindowPortEdge
import com.zynergylabs.forager.app.ui.adaptive.isShortWindow
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.CentrePinConfirmActions
import com.zynergylabs.forager.app.ui.map.CentrePinInstruction
import com.zynergylabs.forager.app.ui.map.CentrePinMap
import com.zynergylabs.forager.app.ui.map.rememberCentrePinState
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
import androidx.compose.runtime.key
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.TwoStageSwipeRow
import com.zynergylabs.forager.app.ui.log.opensRecordDetails
import com.zynergylabs.forager.app.ui.log.rememberSwipeRevealGroup
import com.zynergylabs.forager.app.ui.log.swipeRevealTouchWatcher
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.estimateOfflineTileCount
import com.zynergylabs.forager.app.domain.estimateServedOfflineTileCount
import com.zynergylabs.forager.app.domain.isOfflineRegionStale
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.Spacing


/**
 * The "Offline Maps" submenu: an interactive topo map to pick a download region via the
 * centre-pin picker, the region's radius, current status, and the Download/Delete actions.
 *
 * Always downloads from the same one fixed source, unconditionally — see
 * `com.zynergylabs.forager.app.domain.OfflineMapRepository`'s doc comment for why this is no longer gated on,
 * or reactive to, [MapMode]/the quick-fire map mode picker: the project owner's own call was that
 * offline downloads should "assume [a fixed source] and [be] ready to function" regardless of
 * either. That fixed source is `com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository`'s Cloudflare
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
    onDownloadAgain: (Long) -> Unit = {},
    /**
     * Journal redesign J5c: passed through to the downloaded-region rows, where a tap on a closed row
     * opens that region's details sheet, given its id. The picker and download code above does not
     * read it. `null`, the default, leaves the rows without a tap.
     */
    onOpenRegionDetails: ((Long) -> Unit)? = null,
) {
    val pickedLat = uiState.offlineMapLatText.toDoubleOrNull()
    val pickedLng = uiState.offlineMapLngText.toDoubleOrNull()
    val hasValidRegion = pickedLat != null && pickedLat in -90.0..90.0 && pickedLng != null && pickedLng in -180.0..180.0
    val defaultCenter = uiState.offlineMapPickerDefaultCenter ?: OFFLINE_MAP_PICKER_DEFAULT_CENTER
    val now = currentTime.nowEpochMillis()

    val pickerRegion = Region(
        lat = pickedLat ?: defaultCenter.lat,
        lng = pickedLng ?: defaultCenter.lng,
        radiusKm = uiState.offlineMapRadiusKm,
    )
    // One pin for both layouts below (L1): turning the phone swaps the layout, not the pin.
    val pinState = rememberCentrePinState(pickerRegion)
    // The map moves between the two layouts rather than being composed twice: a second mapSlot call
    // would tear down and rebuild the MapView on every turn (the same guarantee
    // CartographyEntryReportScreen's fullscreen relies on), losing the camera the pin reads.
    val pickerMap = remember(pinState, mapSlot) {
        movableContentOf { night: Boolean, mapModifier: Modifier ->
            // Basemap.OPEN_TOPO_MAP: the pin this file's header comment names for Stage 2e-ii.
            CentrePinMap(state = pinState, mapSlot = mapSlot, basemap = Basemap.OPEN_TOPO_MAP, night = night, modifier = mapModifier)
        }
    }
    // Nothing to cancel back to: this panel had no confirm step before this picker
    // existed either — the offlineMapLatText/offlineMapLngText fields just keep
    // whatever they already held (blank, or a prior confirmed pick).
    val confirmActions: @Composable () -> Unit = {
        CentrePinConfirmActions(state = pinState, onConfirm = onRegionPicked, onCancel = {})
    }
    val instruction: @Composable () -> Unit = {
        Text(
            "Offline downloads cover the continental United States with vector map data. " +
                "Pan the map below to position the pin, then tap OK to choose where to download.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
    val details: @Composable ColumnScope.() -> Unit = {
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
            // The offline radius has its own ceiling, sized to the tile budget — not the search
            // radius's Region.MAX_RADIUS_KM it used to share. See OfflineMapRepository.MAX_RADIUS_KM
            // for the arithmetic (two-data-corrections dispatch, Part B).
            valueRange = Region.MIN_RADIUS_KM.toFloat()..OfflineMapRepository.MAX_RADIUS_KM.toFloat(),
            steps = OfflineMapRepository.MAX_RADIUS_KM - Region.MIN_RADIUS_KM - 1,
        )

        // So the tile budget is discovered here, while there's still time to pick a smaller
        // radius, rather than only on a refused download — a user should not discover the
        // ceiling at a trailhead.
        // Against the zoom the deployed source actually serves (SERVED_MAX_ZOOM, now equal to
        // MAX_ZOOM; the min inside is kept as the seam for their next divergence) — see
        // OfflineMapRepository.SERVED_MAX_ZOOM (tile-estimate dispatch; two-data-corrections dispatch).
        val estimatedTiles = estimateServedOfflineTileCount(pickerRegion)
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
    }
    val isDownloading = uiState.offlineDownloadStatus is OfflineMapStatus.Downloading
    // "Download Maps" asks first (owner, 2026-09-29: "Approve the Download Maps wording as is";
    // docs/plans/journal-redesign.md, "'Download Maps' asks first: the approved copy"). Part 2 finding (c):
    // one tap used to start a real download. Saveable, so a rotation or a night-mode rebuild with the
    // question up keeps asking it.
    var confirmingDownload by rememberSaveable { mutableStateOf(false) }
    val downloadButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(
            onClick = { confirmingDownload = true },
            enabled = hasValidRegion && !isDownloading,
            modifier = buttonModifier.fillMaxWidth(),
        ) { Text("Download Maps") }
    }
    val confirmDownloadDialog: @Composable () -> Unit = {
        if (confirmingDownload) {
            AlertDialog(
                onDismissRequest = { confirmingDownload = false },
                title = { Text("Download this area?") },
                text = {
                    Text(offlineDownloadConfirmationBody(uiState.offlineMapNameText, uiState.offlineMapRadiusKm, distanceUnit, estimateServedOfflineTileCount(pickerRegion)))
                },
                confirmButton = {
                    TextButton(onClick = { confirmingDownload = false; onDownloadOfflineMaps() }) { Text("Download") }
                },
                dismissButton = { TextButton(onClick = { confirmingDownload = false }) { Text("Cancel") } },
            )
        }
    }
    val regionsSection: @Composable () -> Unit = {
        HorizontalDivider()

        OfflineRegionsSection(
            // Journal redesign J4: a region whose delete is pending is left out of the rows, but
            // its tiles are still on disk until the delete runs, so the budget counts every region.
            regions = uiState.visibleOfflineRegions,
            tilesUsed = uiState.offlineRegions.sumOf { it.tileCount },
            errorMessage = uiState.offlineRegionsErrorMessage,
            staleThresholdDays = uiState.offlineStaleThresholdDays,
            distanceUnit = distanceUnit,
            nowEpochMillis = now,
            onDeleteOfflineRegion = onDeleteOfflineRegion,
            onDownloadAgain = onDownloadAgain,
            onOpenRegionDetails = onOpenRegionDetails,
        )
    }

    if (isShortWindow() && LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        OfflineMapsSideBySide(
            modifier = modifier,
            controlsFirst = currentWindowPortEdge() == ScreenEdge.Left,
            map = { mapModifier -> pickerMap(isNightMode, mapModifier) },
            instruction = instruction,
            details = details,
            regionsSection = regionsSection,
            confirmActions = confirmActions,
            downloadButton = downloadButton,
        )
        confirmDownloadDialog()
        return
    }

    // The whole panel scrolls as one unit now that OfflineRegionsSection's list has no bound on
    // its own length — a fixed-aspect-ratio picker map (below) plus a growing region list can
    // exceed whatever height this panel's own parent hands it (Modifier.weight(1f) from the drawer
    // sheet's Column, the same pattern SearchControls already uses for its own scroll in that same
    // parent), so verticalScroll here is meaningful rather than a no-op: weight(1f) gives a bounded,
    // not infinite, height to scroll within.
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        instruction()

        // A fixed aspect ratio, not weight(1f): the picker map used to claim all leftover space in
        // an unscrolled panel, but a panel that now scrolls as a whole has no "leftover space" for
        // weight to resolve against.
        //
        // The ratio is on the map viewport rather than a Box around the whole picker (map-pan
        // dispatch, §2b). Wrapping the whole picker constrained the instruction line and the
        // OK/Cancel row too, leaving the map itself as the remainder: measured 360x146dp inside a
        // 360x270dp box on a 360dp phone, a 2.5:1 letterbox rather than the 4:3 the constant
        // reads as. Constraining the map viewport makes it 360x270dp and lets the picker's own
        // chrome add its height below — see CentrePinLocationPicker.mapAspectRatio. Since L1 the
        // picker's three pieces are composed here directly, in CentrePinLocationPicker's own order,
        // so the map can move to the side-by-side layout; this layout is unchanged.
        Column(modifier = Modifier.fillMaxWidth()) {
            CentrePinInstruction()
            pickerMap(isNightMode, Modifier.fillMaxWidth().aspectRatio(MAP_PICKER_ASPECT_RATIO))
            confirmActions()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            details()
            downloadButton(Modifier)
        }

        regionsSection()
    }
    confirmDownloadDialog()
}

/**
 * The confirmation's body, in the owner's approved wording: "<name> · <radius> around the pin · about <N>
 * tiles", or "<radius> around the pin · about <N> tiles" when the name is blank. [radiusKm] is shown in the
 * units setting ([formatDistanceKm]).
 */
internal fun offlineDownloadConfirmationBody(name: String, radiusKm: Int, unit: DistanceUnit, estimatedTiles: Int): String {
    val area = "${formatDistanceKm(radiusKm, unit)} around the pin · about $estimatedTiles tiles"
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) area else "$trimmed · $area"
}

/**
 * L1 (dispatch `prompts/preserved/2026-09-28-47.md`, continuation `2026-09-28-49`; the device
 * evidence is `docs/audits/2026-09-28-backlog-device-check-part-b-run-record.md`, item 7): the
 * Offline Maps picker in a short landscape window, where the stacked panel's 4:3 map (480 dp at the
 * 640 dp column) is taller than the window and, once scrolled, leaves nothing but the map to drag.
 *
 * - **Side by side** (owner, "Side by side (Recommended)"): the map on one side at full height, the
 *   controls on the other, half the width each.
 * - **Pinned actions** (owner, "Pin OK/Download, rest scrolls (Recommended)", which supersedes "all
 *   reachable without scrolling": that cannot hold at 384 dp): OK with its Cancel, and the "Pin at"
 *   line that sits over them in the stacked layout, and Download are pinned at the bottom of the
 *   controls side, always visible. Everything else scrolls above them, in the stacked order.
 * - **Which side** (the planner's ruling, 2026-09-28-49): the map on the punch-hole side and the
 *   controls on the rail side, as the landscape search sheet (P8), the navigation HUD (P9) and the
 *   tools drawer (P12) sit by the rail with the map beyond them
 *   (`docs/plans/landscape-phone-design.md`). [controlsFirst] is true when the rail's port edge is
 *   the window's left (ROTATION_270), so the sides follow the rail on a turn between 90 and 270.
 *
 * A drag on the map pans the map: it is its own region of the row, not inside the controls' scroll.
 * The window test is the one B1-B3 and J5 use (`isShortWindow` and landscape).
 */
@Composable
private fun OfflineMapsSideBySide(
    modifier: Modifier,
    controlsFirst: Boolean,
    map: @Composable (Modifier) -> Unit,
    instruction: @Composable () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    regionsSection: @Composable () -> Unit,
    confirmActions: @Composable () -> Unit,
    downloadButton: @Composable (Modifier) -> Unit,
) {
    Row(modifier = modifier.fillMaxSize()) {
        val controls: @Composable RowScope.() -> Unit = {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag(OFFLINE_PICKER_CONTROLS_SCROLL_TAG),
                ) {
                    instruction()
                    CentrePinInstruction()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        content = details,
                    )
                    regionsSection()
                }
                confirmActions()
                downloadButton(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm))
            }
        }
        if (controlsFirst) controls()
        map(Modifier.weight(1f).fillMaxHeight())
        if (!controlsFirst) controls()
    }
}

/** [OfflineMapsSideBySide]'s scrolling part, above the pinned actions: what a test drags to reach the slider. */
internal const val OFFLINE_PICKER_CONTROLS_SCROLL_TAG = "offline-picker-controls-scroll"

/** The picker map's fixed width:height ratio — see [OfflineMapsPanel]'s doc comment for why this replaced `Modifier.weight(1f)`. */
private const val MAP_PICKER_ASPECT_RATIO = 4f / 3f

/**
 * An arbitrary opening viewport for [OfflineMapsPanel]'s picker map before a region has been
 * picked — the geographic center of the contiguous United States (near Lebanon, Kansas), since
 * offline downloads only ever cover the continental-US PMTiles archive
 * `com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository` reads from. Not a default region and never
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
 * [AvailabilityViewModel.loadOfflineRegions][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.loadOfflineRegions].
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
 * Deleting a downloaded region is not reversible without re-downloading it. It used to confirm
 * through a dialog behind each row's "Delete" button; since journal redesign J4 a row swipes (a
 * [TwoStageSwipeRow] since J4b L6: a short swipe reveals Delete, a full swipe deletes) and [onDeleteOfflineRegion] asks for a *pending* delete
 * (`AvailabilityViewModel.requestDeleteOfflineRegion`): the row hides, the Undo snackbar shows with
 * the reference warning the dialog carried, and MapLibre's tile delete runs only when the snackbar
 * ends without Undo — the only way Undo can be exact for a region.
 *
 * [tilesUsed] is passed separately from [regions] because a pending region is not a row but its
 * tiles still count against the budget until they are deleted.
 */
@Composable
private fun OfflineRegionsSection(
    regions: List<OfflineRegionSummary>,
    tilesUsed: Int,
    errorMessage: String?,
    staleThresholdDays: Int,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onDeleteOfflineRegion: (Long) -> Unit,
    onDownloadAgain: (Long) -> Unit = {},
    onOpenRegionDetails: ((Long) -> Unit)?,
) {

    // No scroll/height cap of its own: OfflineMapsPanel's whole Column scrolls as one unit (see
    // its doc comment), so this section just renders at its natural height as the last thing in
    // that scroll.
    // J4b L6: one open row at a time, and a touch elsewhere in this section closes it.
    val swipeGroup = rememberSwipeRevealGroup()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .swipeRevealTouchWatcher(swipeGroup)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Downloaded Maps", style = MaterialTheme.typography.titleSmall)

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
                key(region.id) {
                    // J4b L6: two-stage swipe. No Edit: nothing in the app edits a downloaded region
                    // (OfflineMapRepository has download, deleteRegion and listRegions only).
                    TwoStageSwipeRow(
                        testTag = swipeToDeleteTag(RecordType.OFFLINE_MAPS, region.id.toString()),
                        rowKey = region.id,
                        group = swipeGroup,
                        onDelete = { onDeleteOfflineRegion(region.id) },
                        onEdit = null,
                    ) {
                        OfflineRegionRow(
                            region = region,
                            isStale = isOfflineRegionStale(region.createdAtEpochMillis, nowEpochMillis, staleThresholdDays),
                            distanceUnit = distanceUnit,
                            nowEpochMillis = nowEpochMillis,
                            onClick = onOpenRegionDetails?.let { open -> { open(region.id) } },
                            onDownloadAgain = { onDownloadAgain(region.id) },
                        )
                    }
                }
            }
        }
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
 *
 * No delete control of its own since journal redesign J4 (the text "Delete" button is gone): its
 * callers wrap it in a [TwoStageSwipeRow] (J4b L6).
 *
 * [onClick] (journal redesign J5c) is what a tap on the row opens, the region's details sheet, or
 * `null` for no row tap. The All logbook passes `null` and puts the tap on its badged row instead,
 * so the type badge takes it too.
 */
@Composable
internal fun OfflineRegionRow(
    region: OfflineRegionSummary,
    isStale: Boolean,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onClick: (() -> Unit)? = null,
    /** What "Download again" does on a region restored from a backup ([OfflineRegionSummary.isDownloaded] `false`); unused for a downloaded one. */
    onDownloadAgain: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.opensRecordDetails(region.name, onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(region.name, style = MaterialTheme.typography.bodyMedium)
                if (isStale && region.isDownloaded) {
                    Text("Stale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (!region.isDownloaded) {
                // A region restored from a backup onto a phone that never downloaded it (owner, "1 B"): its stored centre
                // and radius, no tile count or "downloaded ... ago" it cannot honestly claim, and a way to get its tiles.
                Text("Not downloaded", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    "${formatDistanceKm(region.region.radiusKm, distanceUnit)} around ${decimalDegreesLabel(region.region.lat, region.region.lng)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (onDownloadAgain != null) {
                    OutlinedButton(onClick = onDownloadAgain) { Text("Download again") }
                }
            } else {
                Text(
                    "${formatDistanceKm(region.region.radiusKm, distanceUnit)} around " +
                        "${decimalDegreesLabel(region.region.lat, region.region.lng)} — " +
                        "${region.tileCount} tiles, ${offlineRegionSizeLabel(region)} — " +
                        "downloaded ${relativeTimeLabel(region.createdAtEpochMillis, nowEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(offlineRegionZoomNote(region), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A region's size on disk as its row prints it, "12.3 MB" (J5c: the details sheet prints the same). */
internal fun offlineRegionSizeLabel(region: OfflineRegionSummary): String = "${"%.1f".format(region.sizeBytes / 1_000_000.0)} MB"

/**
 * The row's zoom-readiness paragraph (see [OfflineRegionRow]'s doc comment for where it came from),
 * as one function since journal redesign J5c so the details sheet shows the identical text.
 */
internal fun offlineRegionZoomNote(region: OfflineRegionSummary): String =
    "Ready to zoom ${region.maxZoom.toInt()}: zoom ${region.minZoom.toInt()}–${region.maxZoom.toInt() - 1} " +
        "from the archive, zoom ${region.maxZoom.toInt()} detail fetched live from Protomaps when this " +
        "region downloaded — a region that shows here has both, since a zoom-${region.maxZoom.toInt()} " +
        "fetch failure fails the whole download rather than silently completing without it."

