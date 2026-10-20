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
//   picker below pins its basemap in its CentrePinMap call. If the offline style is meant to show
//   inside the picker, that pin is the exact line 2e-ii touches. This move changed its file, not
//   its shape. The pin was `Basemap.OPEN_TOPO_MAP` until dispatch 2026-10-18-07 (Forager RECORD
//   -1309; the owner, from a screenshot of this screen showing Topo: "Offline maps displays topo
//   maps. Have it display the street map", then, asked whether it should always be Street or
//   follow the Layers choice, "Always be street."). It is `Basemap.OSM_STANDARD` now, named
//   directly rather than read from `MapMode.DEFAULT` (which is also Street today), so a later
//   change to the app's opening map does not move this one, and rather than the Layers choice,
//   which the owner turned down. Night mode is unchanged (Street's night paint, as on the Maps
//   tab), and so is what gets downloaded: the Worker's offline style, which this picker map has
//   never shown. Nothing here touches MapLibre
//   initialisation (initializeMapLibre lives in MapLibreStorage.kt and is called only from
//   SightingsMap and MapLibreOfflineMapRepository); this panel reaches the map through MapSlot.
// - CartographyEntryEditScreen (log package) has its own *private* function also named
//   OfflineRegionsSection. It is a different function, not a reference to the one here — a grep
//   coincidence, not a dependency.
//
// UI split Phase 1, PR 10c (dispatch 2026-10-19-19): the side-by-side layout and the download status moved to OfflineMapsSideBySide.kt, the downloaded-regions list to OfflineRegionsList.kt.

import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.adaptive.isLandscapeWindow
import com.zynergylabs.forager.app.ui.adaptive.isLargeScreenWindow
import com.zynergylabs.forager.app.ui.map.CentrePinConfirmActions
import com.zynergylabs.forager.app.ui.map.CentrePinMap
import com.zynergylabs.forager.app.ui.map.rememberCentrePinState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.estimateServedOfflineTileCount
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
 * [onRegionPicked] fires from Download Maps, with the pin's point, just before the confirmation opens
 * (dispatch 2026-10-18-03, Forager RECORD -1269; the owner: "Remove both OK and Cancel and just have the
 * Download Maps option"). Until then it fired from the picker's own OK, which this screen no longer
 * has; every other picker keeps OK and Cancel. See [CentrePinLocationPicker]'s class doc comment for
 * why every location-placing site in this app, this one included, replaced long-press with a fixed
 * centre pin (an accessibility decision, not a style one). [Basemap.OSM_STANDARD] here
 * is only context for choosing *where* to download — this picker map is unrelated to which
 * source the download itself actually reads from underneath. Always Street since dispatch
 * 2026-10-18-07 (Forager RECORD -1309; the owner: "Always be street."), whatever the Layers choice;
 * see this file's header comment. Before that it was [Basemap.OPEN_TOPO_MAP], and before that
 * `Basemap.USGS_TOPO` (US-only), until [MapMode] removed it from the app entirely.
 *
 * Before anything is picked, [uiState]'s `offlineMapLatText`/`offlineMapLngText` are blank, so the
 * picker opens on the device's location when there is one ([AvailabilityUiState.offlineMapPickerDefaultCenter])
 * or else on [OFFLINE_MAP_PICKER_DEFAULT_CENTER], so there is a map to navigate. Download Maps
 * downloads wherever the pin sits, and the "Download this area?" confirmation names the radius and
 * the tile count before anything starts. Until dispatch 2026-10-18-03 Download Maps stayed disabled
 * until OK confirmed a point, so the opening viewport could never be submitted unconfirmed; the
 * owner's step path replaces that guard with the confirmation (Elder's top-up audit, item 1: the
 * greyed button with nothing saying why read as broken).
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
    val defaultCenter = uiState.offlineMapPickerDefaultCenter ?: OFFLINE_MAP_PICKER_DEFAULT_CENTER
    val now = currentTime.nowEpochMillis()

    val pickerRegion = Region(
        lat = pickedLat ?: defaultCenter.lat,
        lng = pickedLng ?: defaultCenter.lng,
        radiusKm = uiState.offlineMapRadiusKm,
    )
    // One pin for both layouts below (L1): turning the phone swaps the layout, not the pin.
    val pinState = rememberCentrePinState(pickerRegion)
    // Dispatch 2026-10-18-03: Download Maps downloads wherever the pin sits, so the tile figures read the pin too (the line under
    // the slider and the confirmation's). They read the last confirmed point while OK existed; with OK gone that point would lag.
    val pin = pinState.cameraCenter
    val pinRegion = Region(lat = pin.lat, lng = pin.lng, radiusKm = uiState.offlineMapRadiusKm)
    val pinIsAPlace = pin.lat in -90.0..90.0 && pin.lng in -180.0..180.0
    // The map moves between the two layouts rather than being composed twice: a second mapSlot call
    // would tear down and rebuild the MapView on every turn (the same guarantee
    // CartographyEntryReportScreen's fullscreen relies on), losing the camera the pin reads.
    val pickerMap = remember(pinState, mapSlot) {
        movableContentOf { night: Boolean, mapModifier: Modifier ->
            // Always Street (dispatch 2026-10-18-07, RECORD -1309): the pin this file's header comment names, for Stage 2e-ii too.
            CentrePinMap(state = pinState, mapSlot = mapSlot, basemap = Basemap.OSM_STANDARD, night = night, modifier = mapModifier)
        }
    }
    // Dispatch 2026-10-18-03 (Forager RECORD -1269): the "Pin at" line alone under the map, with no OK and no Cancel. Back leaves
    // the screen, as Cancel did (Cancel here was a no-op: this panel had nothing to cancel back to).
    val confirmActions: @Composable () -> Unit = {
        CentrePinConfirmActions(state = pinState, onConfirm = onRegionPicked, onCancel = {}, showConfirmButtons = false)
    }
    // Dispatch 2026-10-17-09, RECORD -1243 and -1245 (the owner: "Trim the top text out, the design is intuitive to use without
    // explaining (3 times). Add at the bottom the note on the Continental US vector map. Have the user be faced with the
    // optional name box at top with the radius slider shown"; to the three lines as listed, "Option 1, but do the same for
    // portrait"). Gone from this screen, in both orientations:
    //  1. the instruction above the map, "Offline downloads cover the continental United States with vector map data. Pan the
    //     map below to position the pin, then tap OK to choose where to download.", whose first sentence is now the note at the
    //     bottom (usContinentalNote);
    //  2. the centre-pin instruction, "Pan the map to position the pin, then confirm." (CentrePinInstruction; only here: every
    //     other picker keeps it);
    //  3. the picked-region line, "No location picked yet — pan the map above and tap OK." or "Download region: <lat>, <lng>":
    //     the pinned "Pin at: <lat>, <lng>" line under the map already shows where the pin is.
    // So the controls start with the Name box, the radius and its slider under it.
    val usContinentalNote: @Composable () -> Unit = {
        Text(
            OFFLINE_US_CONTINENTAL_NOTE,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
    val details: @Composable ColumnScope.() -> Unit = {
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
        val estimatedTiles = estimateServedOfflineTileCount(pinRegion)
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
    // Dispatch 2026-10-18-03 (Forager RECORD -1269; the owner's step path: "pan the map to place the pin → ... → tap Download
    // Maps → the existing 'Download this area?' confirmation → it downloads"): the tap picks the pin's point (the two text fields
    // the download reads, as OK did) and asks. Enabled whenever the pin is on a real place and no download is running.
    val downloadButton: @Composable (Modifier, PaddingValues) -> Unit = { buttonModifier, padding ->
        Button(
            onClick = {
                onRegionPicked(pinState.cameraCenter)
                confirmingDownload = true
            },
            enabled = pinIsAPlace && !isDownloading,
            contentPadding = padding,
            modifier = buttonModifier.fillMaxWidth(),
        ) { Text("Download Maps", maxLines = 1) }
    }
    val confirmDownloadDialog: @Composable () -> Unit = {
        if (confirmingDownload) {
            // C1: the picker's map is always drawn beneath this dialog (`pickerMap`, both layouts), so it takes the
            // map chrome's 0.8 like the other dialogs (owner's edge-case ruling: "Dialogs ... 80% over the map"); it
            // was opaque. The navigation bar's colour, as every dialog over a map now is.
            val downloadDialogColor = mapChromeFill(navigationBarContainerColor(), overMap = true)
            AlertDialog(
                onDismissRequest = { confirmingDownload = false },
                containerColor = downloadDialogColor,
                modifier = Modifier.testTag(DOWNLOAD_CONFIRM_DIALOG_TAG).mapChromeContainerColor(downloadDialogColor),
                title = { Text("Download this area?") },
                text = {
                    Text(offlineDownloadConfirmationBody(uiState.offlineMapNameText, uiState.offlineMapRadiusKm, distanceUnit, estimateServedOfflineTileCount(pinRegion)))
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

    if (isLandscapeWindow()) {
        OfflineMapsSideBySide(
            modifier = modifier,
            // RECORD -956 (the planner's answer 3): the controls on the right, beside the rail, at both rotations. Was "first
            // when the port edge is the left", which put them on the left at ROTATION_270.
            controlsFirst = false,
            map = { mapModifier -> pickerMap(isNightMode, mapModifier) },
            details = details,
            usContinentalNote = usContinentalNote,
            regionsSection = regionsSection,
            // Dispatch 2026-10-18-03 (Forager RECORD -1269): "Pin at: …", then Download Maps across the controls side. Was, under
            // RECORD -1253 ("C: OK, Cancel and Download on one line"), OK, Cancel and Download on one line under "Pin at".
            pinnedActions = {
                CentrePinConfirmActions(
                    state = pinState,
                    onConfirm = onRegionPicked,
                    onCancel = {},
                    trailing = { downloadButton(Modifier.weight(1f), ButtonDefaults.ContentPadding) },
                    showConfirmButtons = false,
                )
            },
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
            // Dispatch 2026-10-18-10 (RECORD -1340): on a large screen in portrait the map is a square on top, the info under it.
            // A phone keeps the 4:3 map.
            val portraitMapRatio = if (isLargeScreenWindow()) 1f else MAP_PICKER_ASPECT_RATIO
            pickerMap(isNightMode, Modifier.fillMaxWidth().aspectRatio(portraitMapRatio).testTag(OFFLINE_PICKER_MAP_TAG))
            confirmActions()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            details()
            downloadButton(Modifier, ButtonDefaults.ContentPadding)
        }

        regionsSection()
        // RECORD -1245: the continental-US note, the screen's last line.
        usContinentalNote()
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
 * The offline-maps screen's last line (RECORD -1245), word for word the first sentence of the instruction that used to open the
 * screen ("Offline downloads cover the continental United States with vector map data. Pan the map below…").
 */
internal const val OFFLINE_US_CONTINENTAL_NOTE = "Offline downloads cover the continental United States with vector map data."

/** The picker map's fixed width:height ratio — see [OfflineMapsPanel]'s doc comment for why this replaced `Modifier.weight(1f)`. */
private const val MAP_PICKER_ASPECT_RATIO = 4f / 3f

/**
 * An arbitrary opening viewport for [OfflineMapsPanel]'s picker map before a region has been
 * picked — the geographic center of the contiguous United States (near Lebanon, Kansas), since
 * offline downloads only ever cover the continental-US PMTiles archive
 * `com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository` reads from. Not a default region and never
 * submitted unasked: Download Maps opens the "Download this area?" confirmation first (dispatch 2026-10-18-03).
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

/** "Download this area?", for tests reading its container colour. */
internal const val DOWNLOAD_CONFIRM_DIALOG_TAG = "download-confirm-dialog"
