package com.zynergylabs.forager.app.ui.log

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.entryMapFrame
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.ui.map.HIDE_FROM_MAP_LABEL
import com.zynergylabs.forager.app.ui.map.MapBarIconButton
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.ENTRY_MAP_OVERLAYS
import com.zynergylabs.forager.app.ui.map.MapLayersSheet
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.MapBubbleLayer
import com.zynergylabs.forager.app.ui.map.MapCameraRequest
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.SHOW_ON_MAP_LABEL
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.focusedFeature
import com.zynergylabs.forager.app.ui.map.tappedThingOf
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import java.util.UUID
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.adaptive.isShortWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

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
 * [com.zynergylabs.forager.app.domain.GetCartographyEntryMapDataUseCase]/repository graph threaded through,
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
 * (see [GeoDistance.boundingRegion]'s own doc comment). **"Resolved" excludes kept offline regions**
 * (plate pulse, owner ruling on item 4): a region alone — a green circle with nothing in it — is not
 * a day, so an entry whose only kept item is a region gets no map here, exactly like the
 * photos-with-no-coordinates case; the circle still draws whenever anything else resolved alongside
 * it. For the same reason [getCoveringOfflineRegion] is asked about
 * [CartographyEntryMapData.drawablePoints], not [CartographyEntryMapData.allPoints]: a region
 * always contains its own centre, so passing region centres made every kept region trivially
 * "cover" its entry — coverage means the day's own data sits on the region's tiles.
 * `MapRenderMode.trackLiveLocation` is `false` here specifically — see that field's own doc comment
 * for the real bug this avoids (the map seizing the camera for the device's *current* location the
 * instant permission is granted, overriding the framing computed here for what is, after all, a
 * historical place). `MapRenderMode.showSearchCentre` is `false` here too, and separately: the
 * region this map is framed on is a computed box midpoint, not a place the user chose, so the
 * search-centre dot the live map draws there would mark a point where nothing happened (plate
 * pulse, owner ruling on item 5; see that field's own doc comment for why it is its own flag).
 *
 * **The opening frame** (owner, 2026-09-28, "Fit all kept records"). The map opens on
 * [com.zynergylabs.forager.app.domain.entryMapFrame]: the bounds of the kept tracks' points, finds,
 * located photos and waypoints, 48 dp in and zoomed no closer than 17, or zoom 16 on one place. It
 * reaches the map as one [MapRenderMode.cameraRequest] per screen instance, applied once, and so
 * replaces the region's zoom-from-radius as this map's opening camera. The region above is still
 * passed, because [MapSlot] needs one, and a locate-me pan still zooms by its radius, as before.
 * Kept regions never count toward the frame. Before this, the region centre was in the framing, so a
 * kept region elsewhere could pull the opening view away from the day's own records, and the
 * zoom-from-radius stopped at 13 however small the day was.
 *
 * ## The offline-map toggle (Journal Stage 2e-i)
 *
 * A manual switch below the map — never over it, so the standing 80% chrome-over-the-map opacity
 * rule doesn't apply here at all, since nothing is drawn on top of the map surface. Shown only once
 * [getCoveringOfflineRegion] resolves a non-null [OfflineRegionSummary] — absent both while that's
 * still resolving and when it resolves to `null` (no kept region covers this entry's data), the same
 * "absent, not disabled-and-visible" treatment the map section above already uses for its own
 * nothing-resolved case. [getCoveringOfflineRegion] is scoped to [entry]'s own **kept**
 * [CartographyEntry.offlineRegionDecisions] only, deliberately never falling back to searching every
 * downloaded region on the device — see [com.zynergylabs.forager.app.domain.GetCartographyEntryOfflineRegionUseCase]'s
 * own doc comment for why a withheld region must stay excluded here, not just in the text below.
 *
 * **Flipping the toggle swaps the map's style** (Stage 2e-ii): it sets [MapRenderMode.useOfflineTiles],
 * which [com.zynergylabs.forager.app.ui.map.SightingsMap] reads to load the downloaded regions' own style by
 * URI in place of the basemap — see that field's own doc comment. Every overlay is re-added in the
 * style callback exactly as on a basemap swap, and the camera does not move. One piece of state
 * drives both the inline switch below and the fullscreen chrome's own offline row (fullscreen-maps
 * dispatch, Part 1f) — flipping either agrees with the other by construction, not by
 * synchronization.
 *
 * ## Fullscreen (fullscreen-maps dispatch, Part 1)
 *
 * The preview above is a 4:3, tap-to-fullscreen live map, not a static snapshot — [isMapFullscreen]
 * gates only the chrome *around* the map (this screen's own header, the inline offline row, the
 * scrollable text below), never the [mapSlot] call itself, which stays the one, textually-single
 * call this file makes regardless of fullscreen state; only its enclosing [Box]'s own `Modifier`
 * changes (a fixed 4:3 width normally, [androidx.compose.foundation.layout.weight] 1f once the
 * siblings around it are gone). This is deliberate, not incidental: `remember`'s positional
 * memoization (see [com.zynergylabs.forager.app.ui.map.SightingsMap]'s own `mapView` — a `remember` with no
 * keys) only survives across a `Modifier` change, never across the call itself moving to a
 * different branch of an `if`/`when` — a second, differently-positioned call would tear down and
 * rebuild the underlying `MapView`, refetching tiles and losing camera state. `CompactMapTab`'s own
 * fullscreen mode already relies on the identical guarantee (see that composable's own doc
 * comment), confirmed by reading `AndroidView`'s `factory` lambda before relying on it: `factory`
 * runs once per call-site instance, never re-run by a later `Modifier`-only recomposition.
 *
 * [entryMapMode] is local to this screen — a confirmed, real state leak this dispatch fixes: before
 * this, `basemap` threaded straight from the live Maps screen, so switching this entry's own preview
 * to Satellite silently changed the live map too. **Seeded, never written back** (owner, 2026-09-29,
 * "1 B"; dispatch 2026-09-28-104, item 8): when an entry map opens it starts on the Maps tab's own
 * basemap ([initialMapMode]), and changing it here changes this entry's map only. The leak was the
 * write-back, and that stays closed; it used to open on [MapMode.DEFAULT] (Topographical) whatever the
 * Maps tab showed. Reset per [entry] like every other per-entry state above. The basemap picker (row 4 of [MapIconBar]) is hidden, not disabled, while
 * [useOfflineTiles] is on (fullscreen-fixes dispatch, Item 3, reversing this file's own earlier
 * "disabled, not hidden" call) — offline is a single fixed style with nothing to choose between,
 * and an absent control reads as a feature not yet built rather than a limitation the app has.
 *
 * [onLocateMe] never sets [org.maplibre.android.location.modes.CameraMode.TRACKING] — this map is
 * about a historical place ([MapRenderMode.trackLiveLocation] is `false` here specifically, see
 * that field's own doc comment), so a locate-me tap pans the camera once via [MapSlot]'s own
 * `focusOverride`, resolved from [getCurrentLocation] (a plain one-shot suspend call, matching this
 * screen's own established shape for [getMapData]/[getCoveringOfflineRegion]), never the main map's
 * `resumeTrackingRequestId`/tracking token, which would be both a silent no-op here
 * ([MapRenderMode.trackLiveLocation] gates it) and, if it somehow fired, exactly the wrong behavior.
 * Shown and prompting always, even before permission is granted — requesting it directly via
 * [rememberLauncherForActivityResult], the same in-composable pattern
 * [rememberPhotoAcquisitionLaunchers] already establishes for camera/media permission in this exact
 * package, rather than threading a new case through `MainActivity`'s own shared location-permission
 * launcher, which only ever routes into ViewModel methods — this screen's own recenter target is
 * local composable state with nothing to route to at that level.
 */
@Composable
internal fun CartographyEntryReportScreen(
    entry: CartographyEntry,
    galleryPhotos: List<GalleryPhoto>,
    distanceUnit: DistanceUnit,
    mapSlot: MapSlot,
    night: Boolean,
    /** The Maps tab's basemap as a mode, seeding [entryMapMode] when this screen opens; see this file's doc comment. */
    initialMapMode: MapMode = MapMode.DEFAULT,
    getMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    getCoveringOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary?,
    /** See this file's own doc comment, "Fullscreen," for why this is a plain suspend call rather than a [com.zynergylabs.forager.app.domain.LocationProvider] threaded through directly. */
    getCurrentLocation: suspend () -> LocationResult,
    onEdit: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Map layers L0b (owner's ruling 4, "Same sheet"): the layer choices this map shares with the Maps
     * tab, and its Layers sheet's overlay switch. The sheet lists only what this map draws
     * ([ENTRY_MAP_OVERLAYS]) and no colour field: this map passes no forecast feed (planner's ruling
     * on Q5), so `SightingsMap` hides every colour field here whatever the stored choice. Its geometry
     * still comes only from [getMapData] (`GetCartographyEntryMapDataUseCase`).
     */
    layersState: MapLayersState = MapLayersState.DEFAULT,
    onLayerVisibilityChanged: (String, Boolean) -> Unit = { _, _ -> },
    /** Off while the Tools drawer is open over the Journal, so Back closes the drawer (intent 2026-09-28-28); see [JournalTab]'s parameter of the same name. `true` (the default) is every other caller, unchanged. */
    backEnabled: Boolean = true,
    /**
     * M1 (owner: "Yes, same bubbles"): what this map's glyph bubbles look records up in and open, from
     * [CartographyScreen]'s caller. Its find action is "Open find" (owner, Q4), which opens the find
     * over this entry so Back returns to it as it was. Defaulted, so another caller's bubbles find
     * nothing and close with a logged line.
     */
    mapBubbleSources: MapRecordSources = MapRecordSources(),
    /**
     * J8-3 (owner: "Entry report menu (Recommended)"): with it, a saved entry's menu offers "Show on
     * map" or, while it is shown, "Hide from map", writing its `shownOnMap` with the new value. A draft
     * is never shown on the map (owner: "Saved entries only"), so its menu offers neither; nor does any
     * caller that passes `null` (the default).
     */
    onSetShownOnMap: ((shown: Boolean) -> Unit)? = null,
) {
    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    var confirmingDelete by remember(entry.id) { mutableStateOf(false) }
    var mapData by remember(entry.id) { mutableStateOf<CartographyEntryMapData?>(null) }
    var coveringOfflineRegion by remember(entry.id) { mutableStateOf<OfflineRegionSummary?>(null) }
    // The user's own choice — local, never persisted, resets per entry. Stage 2e-i's own manual
    // toggle: see this file's own doc comment, "The offline-map toggle," for why flipping this does
    // not yet change any tile request. Also the fullscreen chrome's own offline row, per "Fullscreen"
    // above — one var, two controls.
    var useOfflineTiles by remember(entry.id) { mutableStateOf(false) }
    // See this file's own doc comment, "Fullscreen" — local to this screen, independent of
    // AvailabilityScreen's own mapMode.
    var entryMapMode by remember(entry.id) { mutableStateOf(initialMapMode) }
    var isMapFullscreen by remember(entry.id) { mutableStateOf(false) }
    var showLayersSheet by remember(entry.id) { mutableStateOf(false) }
    // See MapOverlayContent.resetOrientationRequestId's own doc comment.
    var resetOrientationRequestId by remember(entry.id) { mutableStateOf(0) }
    // The one-shot camera pan a locate-me tap resolves to — see this file's own doc comment,
    // "Fullscreen," for why this is a plain LatLng?, never the main map's tracking token.
    var focusOverrideTarget by remember(entry.id) { mutableStateOf<LatLng?>(null) }
    // M1: this map's one tapped thing (no sightings are drawn here, so always a glyph).
    var tapped by remember(entry.id) { mutableStateOf<TappedMapThing?>(null) }
    // See openingCameraRequest below.
    val openingFrameToken = remember(entry.id) { "entry-map-${entry.id}-${UUID.randomUUID()}" }
    val onFeatureTap: (MapFeatureTap) -> Unit = remember(entry.id) { { tap -> tappedThingOf(tap)?.let { tapped = it } } }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    suspend fun resolveAndCenter(): LocationResult {
        val result = getCurrentLocation()
        when (result) {
            is LocationResult.Success -> focusOverrideTarget = LatLng(result.lat, result.lng)
            LocationResult.LocationUnavailable ->
                Toast.makeText(context, "Couldn't determine your location.", Toast.LENGTH_SHORT).show()
            LocationResult.PermissionDenied -> Unit // The caller decides whether to prompt.
        }
        return result
    }

    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            coroutineScope.launch { resolveAndCenter() }
        } else {
            Toast.makeText(context, "Location permission denied. Can't center on your position.", Toast.LENGTH_SHORT).show()
        }
    }

    val onLocateMe: () -> Unit = {
        coroutineScope.launch {
            if (resolveAndCenter() == LocationResult.PermissionDenied) {
                requestLocationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        }
    }

    // Innermost enabled BackHandler wins — this codebase's own established convention. Exiting
    // fullscreen first, one pop at a time, mirrors CompactMapTab's own back-unwind chain.
    BackHandler(enabled = backEnabled && isMapFullscreen) { isMapFullscreen = false }

    LaunchedEffect(entry.id) {
        val resolved = getMapData(entry, galleryPhotos)
        mapData = resolved
        coveringOfflineRegion = getCoveringOfflineRegion(entry, resolved.drawablePoints)
    }

    val isEntirelyEmpty = entry.text.isBlank() &&
        entry.tags.isEmpty() &&
        entry.photos.isEmpty() &&
        entry.findDecisions.none { it.kept } &&
        entry.trackDecisions.none { it.kept } &&
        entry.waypointDecisions.none { it.kept } &&
        entry.offlineRegionDecisions.none { it.kept }

    // L2: read once here, so the map's Modifier below is the only thing that changes with it.
    val shortWindow = isShortWindow()
    val shortWindowMapCap = (LocalConfiguration.current.screenHeightDp * SHORT_WINDOW_MAP_HEIGHT_FRACTION).dp

    // Read here, above the header, rather than beside the map below (moved unchanged): whether the entry
    // has a map decides the fill of the header's menu and of the delete dialog, which cover it (map
    // chrome at 80%, dispatch 2026-09-28-56 as amended by -58).
    val resolvedMapData = mapData
    val mapRegion = resolvedMapData?.takeUnless { it.isEmpty }?.let { GeoDistance.boundingRegion(it.allPoints) }
    val entryMapShown = resolvedMapData != null && mapRegion != null

    Column(modifier = modifier.fillMaxWidth()) {
        // Hidden via composition (an if, not an opacity/size-zero modifier), same convention
        // CompactMapTab's own fullscreen mode uses for its surrounding chrome — an unmounted
        // composable can't be the thing silently holding onto stale menu/dialog state, and this
        // row's own DropdownMenu never needs to survive a fullscreen round-trip anyway (opening the
        // map preview closes it structurally, same as it would if the user backed out entirely).
        if (!isMapFullscreen) {
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
                    // The menu's default container role, passed explicitly, and its content colour
                    // pinned to the role's own (see `MapLayersSheet`), as J8's menus do.
                    // The menu drops from the header onto the entry's map, when it has one.
                    val entryMenuColor = mapChromeFill(MenuDefaults.containerColor, entryMapShown)
                    val entryMenuContentColor = contentColorFor(MenuDefaults.containerColor)
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = entryMenuColor,
                        modifier = Modifier
                            .testTag(ENTRY_OVERFLOW_MENU_TAG)
                            .mapChromeContainerColor(entryMenuColor),
                    ) {
                      CompositionLocalProvider(LocalContentColor provides entryMenuContentColor) {
                        DropdownMenuItem(
                            text = { Text("Edit entry") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() },
                            // The content colour as read inside the menu, for tests.
                            modifier = Modifier.mapChromeContentColor(LocalContentColor.current),
                        )
                        // "Show on map" only when there is something to show (dispatch 2026-09-28-104, item 7):
                        // absent, not disabled, as this screen's other unavailable controls are (see the doc
                        // comments on the offline row and the basemap picker). "Hide from map" stays on an
                        // entry already shown, so a shown entry can always be hidden.
                        if (!entry.isDraft && onSetShownOnMap != null && (entry.shownOnMap || entry.keepsHighlightableRecord)) {
                            DropdownMenuItem(
                                text = { Text(if (entry.shownOnMap) HIDE_FROM_MAP_LABEL else SHOW_ON_MAP_LABEL) },
                                leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null) },
                                onClick = { menuExpanded = false; onSetShownOnMap(!entry.shownOnMap) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete entry") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; confirmingDelete = true },
                        )
                      }
                    }
                }
            }
        }

        // The opening frame (owner, 2026-09-28, "Fit all kept records"): one request per screen
        // instance, framed on the kept records and never the kept regions (entryMapFrame). Its id is
        // this instance's own token, not the entry's id, so a request is new exactly when this screen
        // is, and the map applies it once: the fullscreen switch, a bubble, and a find overlay's Back
        // (M1's Q4, "exactly as it was") all keep this screen, and so keep the same request.
        val openingCameraRequest = remember(openingFrameToken, resolvedMapData) {
            resolvedMapData?.let(::entryMapFrame)?.let { MapCameraRequest(openingFrameToken, it) }
        }
        if (resolvedMapData != null && mapRegion != null) {
            Box(
                modifier = if (isMapFullscreen) {
                    Modifier.fillMaxWidth().weight(1f)
                } else if (shortWindow) {
                    // L2 (dispatch 2026-09-28-47): see shortWindowEntryMapHeight.
                    Modifier.fillMaxWidth().shortWindowEntryMapHeight(shortWindowMapCap)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                }.testTag(CARTOGRAPHY_MAP_TEST_TAG),
            ) {
                // One call, textually, regardless of isMapFullscreen — see this file's own doc
                // comment, "Fullscreen," for why a second call site would tear down and rebuild the
                // MapView. Only the enclosing Box's own Modifier above varies.
                mapSlot(
                    mapRegion,
                    MapOverlayContent(
                        // The kept waypoint's own id (map layers L0a: the map's tap reports it through
                        // MapRenderMode.onFeatureTap), with its snapshot's coordinate. Since M1 its
                        // name is the entry's own snapshot of it (entryMapWaypoints), no longer a
                        // placeholder, so its bubble names it; what the pin draws is unchanged.
                        waypoints = entryMapWaypoints(entry, resolvedMapData.waypointMarkers),
                        keptTrackPolylines = resolvedMapData.trackPolylines,
                        findMarkers = resolvedMapData.findMarkers,
                        photoMarkers = resolvedMapData.photoMarkers,
                        offlineRegionCircles = resolvedMapData.offlineRegionCircles,
                        resetOrientationRequestId = resetOrientationRequestId,
                        focusedFeature = tapped.focusedFeature,
                    ),
                    MapRenderMode(
                        basemap = entryMapMode.basemap,
                        night = night,
                        trackLiveLocation = false,
                        useOfflineTiles = useOfflineTiles,
                        showSearchCentre = false,
                        layers = layersState,
                        onFeatureTap = onFeatureTap,
                        cameraRequest = openingCameraRequest,
                    ),
                    focusOverrideTarget,
                    {},
                    // Tap to enter fullscreen — the preview stays a live map either way (this file's
                    // own doc comment, "Fullscreen"), so a plain tap is a free gesture to promote it.
                    // No matching tap-to-exit: exiting is the Return row / back button only, so a
                    // stray tap while reading the fullscreen map never dismisses the chrome
                    // by surprise.
                    //
                    // M1: a tap on empty map while a bubble shows closes the bubble and does nothing
                    // else; a tap on a glyph opens its bubble and is not a plain tap at all.
                    {
                        if (tapped != null) {
                            tapped = null
                        } else if (!isMapFullscreen) {
                            isMapFullscreen = true
                        }
                    },
                    { _, _, _ -> },
                    {},
                    Modifier.fillMaxSize(),
                )

                // M1: the same bubbles as the Maps tab (owner's ruling 3), with the entry's own
                // snapshot names for a kept waypoint no longer in Records. Back closes the bubble
                // before it leaves fullscreen: this layer's handler is composed after this screen's.
                MapBubbleLayer(
                    tapped = tapped,
                    onDismiss = { tapped = null },
                    sources = mapBubbleSources.copy(
                        snapshotWaypoints = entryMapWaypoints(entry, resolvedMapData.waypointMarkers),
                        // F3: a kept track gone from Records still has its saved line drawn; a tap on it names it from the entry.
                        snapshotTracks = entry.trackDecisions.filter { it.kept },
                    ),
                    forecast = null,
                    onViewSightingOnINaturalist = { tapped = null },
                    backEnabled = backEnabled && !showLayersSheet,
                )

                if (isMapFullscreen) {
                    MapIconBar(
                        isFullscreen = true,
                        onToggleFullscreen = { isMapFullscreen = false },
                        onLocateMe = onLocateMe,
                        onResetOrientation = { resetOrientationRequestId++ },
                        mapMode = entryMapMode,
                        onOpenLayers = { showLayersSheet = true },
                        onAdd = {}, // Unused — fifthRow below replaces this row entirely.
                        mapModePickerEnabled = !useOfflineTiles,
                        // A toggle, not a momentary action like the default add row this replaces —
                        // MapIconBar has no fifth-row concept for a second map surface to reuse
                        // beyond this same slot, so this reuses MapBarIconButton's own activeColor
                        // tint (the identical mechanism ControlPill's return-to-vehicle row already
                        // uses for its own on/off state) rather than the add row's green fillColor,
                        // which would misleadingly borrow "add" styling for an unrelated toggle.
                        fifthRow = {
                            MapBarIconButton(
                                icon = Icons.Filled.CloudOff,
                                contentDescription = if (useOfflineTiles) "Offline maps on" else "Offline maps off",
                                onClick = { useOfflineTiles = !useOfflineTiles },
                                activeColor = if (useOfflineTiles) MaterialTheme.colorScheme.primary else null,
                            )
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(Spacing.sm),
                    )
                    if (showLayersSheet) {
                        MapLayersSheet(
                            mapMode = entryMapMode,
                            onMapModeSelected = { entryMapMode = it },
                            overlays = ENTRY_MAP_OVERLAYS,
                            colourFields = emptyList(),
                            state = layersState,
                            onVisibilityChanged = onLayerVisibilityChanged,
                            onOpacityChanged = { _, _ -> },
                            onColourFieldMoved = { _, _ -> },
                            onDismiss = { showLayersSheet = false },
                        )
                    }
                }
            }

            if (!isMapFullscreen) {
                val availableOfflineRegion = coveringOfflineRegion
                if (availableOfflineRegion != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(OFFLINE_TOGGLE_LABEL, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                OFFLINE_TOGGLE_CAPTION,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = useOfflineTiles,
                            onCheckedChange = { useOfflineTiles = it },
                            modifier = Modifier.testTag(OFFLINE_TOGGLE_TEST_TAG),
                        )
                    }
                }
            }
        }

        if (!isMapFullscreen) {
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
                            ReportItem(
                                title = it.name ?: "Recorded track",
                                // The report has no live track to hand; the snapshot's point count is what keeps an empty track from being a silent one (timestamp-filter dispatch, Item 3).
                                subtitle = trackSubtitle(it.distanceMeters, it.durationMillis, distanceUnit) + trackExclusionSuffix(liveTrack = null, snapshotPointCount = it.pointCount),
                            )
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
    }

    if (confirmingDelete) {
        val deleteDialogColor = mapChromeFill(AlertDialogDefaults.containerColor, entryMapShown)
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    "This removes the entry and its kept selections. The finds, tracks, waypoints, and regions it kept stay in Records.",
                    modifier = Modifier.mapChromeContentColor(LocalContentColor.current),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDeleteEntry() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
            modifier = Modifier.testTag(ENTRY_DELETE_DIALOG_TAG).mapChromeContainerColor(deleteDialogColor),
            containerColor = deleteDialogColor,
        )
    }
}

/** The report header's overflow menu, for tests. */
internal const val ENTRY_OVERFLOW_MENU_TAG = "entry-overflow-menu"

/** The entry delete dialog, for tests. */
internal const val ENTRY_DELETE_DIALOG_TAG = "entry-delete-dialog"

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

/** Reported verbatim, same reasoning as [EMPTY_ENTRY_MESSAGE] — the toggle's own label, Journal Stage 2e-i. */
private const val OFFLINE_TOGGLE_LABEL = "Offline map"

/**
 * Reported verbatim, same reasoning as [EMPTY_ENTRY_MESSAGE]. States the tradeoff plainly rather than
 * dressing it up as a warning — the current offline style is a hosting-cost constraint the owner
 * intends to lift later, not a design choice, but it is today's reality and a user should not meet it
 * as a surprise the first time they flip this switch.
 */
private const val OFFLINE_TOGGLE_CAPTION = "Offline maps show shapes only — no place names, road names, or icons."

/**
 * The entry map's waypoint pins (M1): each resolved marker, named by the entry's own snapshot of that
 * waypoint (`CartographyEntry.waypointDecisions`), which is what the entry's text lists too. A marker
 * the entry has no decision for keeps the placeholder name it always had.
 */
internal fun entryMapWaypoints(entry: CartographyEntry, markers: List<RecordPoint>): List<Waypoint> = markers.map { marker ->
    Waypoint(
        id = marker.recordId,
        lat = marker.at.lat,
        lng = marker.at.lng,
        altitude = null,
        name = entry.waypointDecisions.firstOrNull { it.waypointId == marker.recordId }?.name ?: "Waypoint",
        note = "",
        createdAtEpochMillis = 0L,
    )
}

/** Lets tests distinguish "the map section rendered" from "nothing resolved, no map section at all" without depending on [mapSlot]'s own real content — see this file's own doc comment, "No map section at all while loading, or if nothing resolved." */
/**
 * L2 (dispatch `prompts/preserved/2026-09-28-47.md`; the device evidence is
 * `docs/audits/2026-09-28-backlog-device-check-part-b-run-record.md`, item 7). **The rule:** in a
 * short window (under 480 dp tall, `isShortWindow`, whatever the orientation) the entry map's
 * preview is 4:3 by its width, as everywhere else, but never taller than this fraction of the
 * window's height: 40%, which is **153.6 dp** in the S22 Ultra's 384 dp landscape window.
 *
 * Why a cap was needed at all: in the 640 dp landscape column a 4:3 map is 480 dp tall, taller than
 * the whole window. `aspectRatio` measures it at 480 dp regardless, and Compose centres an oversized
 * child on its slot, so the map was drawn about 104 dp above its own slot, over the header row, and
 * the offline row and the text below got no height at all.
 *
 * Why 40%: what else must fit beside it in that window, measured headless at `w823dp-h384dp-land`:
 * the Journal's 48 dp short-window row and the report's 64 dp header row take 112 dp of the 384, so
 * a 154 dp map leaves about 118 dp for the offline-map row (about 64 dp on a device) and the start
 * of the scrolling text. Half the window (192 dp) would leave the text about 16 dp once the offline
 * row shows. Rejected: capping the width too, to keep 4:3 (205 x 154 dp), which wastes the column
 * for no gain, since a tap still opens the fullscreen map.
 */
internal const val SHORT_WINDOW_MAP_HEIGHT_FRACTION = 0.4f

/**
 * L2's map box: the full width it is given, and a height of 3/4 of that width, but no more than
 * [cap] and never more than the incoming constraints allow, so the box can never be measured taller
 * than its slot (the overflow `aspectRatio` produced). A layout modifier rather than `heightIn` on
 * `aspectRatio`, because `aspectRatio` falls back to its unconstrained size when nothing satisfies
 * the constraints, which is the overflow itself.
 */
private fun Modifier.shortWindowEntryMapHeight(cap: Dp): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = minOf(width * 3 / 4, cap.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

internal const val CARTOGRAPHY_MAP_TEST_TAG = "cartography-entry-map"

/** Lets tests select the offline-map [Switch] directly, rather than relying on an untagged toggleable-semantics query. */
internal const val OFFLINE_TOGGLE_TEST_TAG = "cartography-entry-offline-toggle"

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
