package com.forager.app.ui.log

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.forager.app.domain.CartographyEntryMapData
import com.forager.app.domain.GeoDistance
import com.forager.app.domain.LocationResult
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.ui.map.MapBarIconButton
import com.forager.app.ui.map.MapIconBar
import com.forager.app.ui.map.MapMode
import com.forager.app.ui.map.MapModePicker
import com.forager.app.ui.map.MapOverlayContent
import com.forager.app.ui.map.MapRenderMode
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.theme.Spacing
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
 * downloaded region on the device — see [com.forager.app.domain.GetCartographyEntryOfflineRegionUseCase]'s
 * own doc comment for why a withheld region must stay excluded here, not just in the text below.
 *
 * **Flipping the toggle changes nothing about what tiles this screen's map requests, in this stage.**
 * It only sets [MapRenderMode.useOfflineTiles], read by nothing yet — see that field's own doc
 * comment for why an unread field here is a deliberate seam for Stage 2e-ii, not dead code. One
 * piece of state drives both the inline switch below and the fullscreen chrome's own offline row
 * (fullscreen-maps dispatch, Part 1f) — flipping either agrees with the other by construction, not
 * by synchronization.
 *
 * ## Fullscreen (fullscreen-maps dispatch, Part 1)
 *
 * The preview above is a 4:3, tap-to-fullscreen live map, not a static snapshot — [isMapFullscreen]
 * gates only the chrome *around* the map (this screen's own header, the inline offline row, the
 * scrollable text below), never the [mapSlot] call itself, which stays the one, textually-single
 * call this file makes regardless of fullscreen state; only its enclosing [Box]'s own `Modifier`
 * changes (a fixed 4:3 width normally, [androidx.compose.foundation.layout.weight] 1f once the
 * siblings around it are gone). This is deliberate, not incidental: `remember`'s positional
 * memoization (see [com.forager.app.ui.map.SightingsMap]'s own `mapView` — a `remember` with no
 * keys) only survives across a `Modifier` change, never across the call itself moving to a
 * different branch of an `if`/`when` — a second, differently-positioned call would tear down and
 * rebuild the underlying `MapView`, refetching tiles and losing camera state. `CompactMapTab`'s own
 * fullscreen mode already relies on the identical guarantee (see that composable's own doc
 * comment), confirmed by reading `AndroidView`'s `factory` lambda before relying on it: `factory`
 * runs once per call-site instance, never re-run by a later `Modifier`-only recomposition.
 *
 * [entryMapMode] is local to this screen, independent of `AvailabilityScreen`'s own `mapMode` — a
 * confirmed, real state leak this dispatch fixes: before this, `basemap` threaded straight from the
 * live Maps screen, so switching this entry's own preview to Satellite silently changed the live
 * map too. Defaults to [MapMode.DEFAULT] (Topographical), reset per [entry] like every other
 * per-entry state above. The basemap picker (row 4 of [MapIconBar]) is disabled, not hidden, while
 * [useOfflineTiles] is on — offline is a single fixed style with nothing to choose between.
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
    getMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    getCoveringOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary?,
    /** See this file's own doc comment, "Fullscreen," for why this is a plain suspend call rather than a [com.forager.app.domain.LocationProvider] threaded through directly. */
    getCurrentLocation: suspend () -> LocationResult,
    onEdit: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    var entryMapMode by remember(entry.id) { mutableStateOf(MapMode.DEFAULT) }
    var isMapFullscreen by remember(entry.id) { mutableStateOf(false) }
    var showMapModePicker by remember(entry.id) { mutableStateOf(false) }
    // See MapOverlayContent.resetOrientationRequestId's own doc comment.
    var resetOrientationRequestId by remember(entry.id) { mutableStateOf(0) }
    // The one-shot camera pan a locate-me tap resolves to — see this file's own doc comment,
    // "Fullscreen," for why this is a plain LatLng?, never the main map's tracking token.
    var focusOverrideTarget by remember(entry.id) { mutableStateOf<LatLng?>(null) }

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
    BackHandler(enabled = isMapFullscreen) { isMapFullscreen = false }

    LaunchedEffect(entry.id) {
        val resolved = getMapData(entry, galleryPhotos)
        mapData = resolved
        coveringOfflineRegion = getCoveringOfflineRegion(entry, resolved.allPoints)
    }

    val isEntirelyEmpty = entry.text.isBlank() &&
        entry.tags.isEmpty() &&
        entry.photos.isEmpty() &&
        entry.findDecisions.none { it.kept } &&
        entry.trackDecisions.none { it.kept } &&
        entry.waypointDecisions.none { it.kept } &&
        entry.offlineRegionDecisions.none { it.kept }

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
        }

        val resolvedMapData = mapData
        val mapRegion = resolvedMapData?.takeUnless { it.isEmpty }?.let { GeoDistance.boundingRegion(it.allPoints) }
        if (resolvedMapData != null && mapRegion != null) {
            Box(
                modifier = if (isMapFullscreen) {
                    Modifier.fillMaxWidth().weight(1f)
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
                        resetOrientationRequestId = resetOrientationRequestId,
                    ),
                    MapRenderMode(basemap = entryMapMode.basemap, night = night, trackLiveLocation = false, useOfflineTiles = useOfflineTiles),
                    focusOverrideTarget,
                    {},
                    // Tap to enter fullscreen — the preview stays a live map either way (this file's
                    // own doc comment, "Fullscreen"), so a plain tap is a free gesture to promote it.
                    // No matching tap-to-exit: exiting is the Return row / back button only, so a
                    // stray tap while reading the fullscreen map never dismisses the chrome
                    // by surprise.
                    { if (!isMapFullscreen) isMapFullscreen = true },
                    { _, _, _ -> },
                    {},
                    Modifier.fillMaxSize(),
                )

                if (isMapFullscreen) {
                    MapIconBar(
                        isFullscreen = true,
                        onToggleFullscreen = { isMapFullscreen = false },
                        onLocateMe = onLocateMe,
                        onResetOrientation = { resetOrientationRequestId++ },
                        mapMode = entryMapMode,
                        onOpenMapModePicker = { showMapModePicker = true },
                        onAdd = {}, // Unused — fifthRow below replaces this row entirely.
                        isNightMode = night,
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
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                    )
                    MapModePicker(
                        visible = showMapModePicker,
                        mapMode = entryMapMode,
                        onModeSelected = { entryMapMode = it },
                        onDismiss = { showMapModePicker = false },
                        anchor = Alignment.TopEnd,
                    )
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

/** Reported verbatim, same reasoning as [EMPTY_ENTRY_MESSAGE] — the toggle's own label, Journal Stage 2e-i. */
private const val OFFLINE_TOGGLE_LABEL = "Offline map"

/**
 * Reported verbatim, same reasoning as [EMPTY_ENTRY_MESSAGE]. States the tradeoff plainly rather than
 * dressing it up as a warning — the current offline style is a hosting-cost constraint the owner
 * intends to lift later, not a design choice, but it is today's reality and a user should not meet it
 * as a surprise the first time they flip this switch.
 */
private const val OFFLINE_TOGGLE_CAPTION = "Offline maps show shapes only — no place names, road names, or icons."

/** Lets tests distinguish "the map section rendered" from "nothing resolved, no map section at all" without depending on [mapSlot]'s own real content — see this file's own doc comment, "No map section at all while loading, or if nothing resolved." */
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
