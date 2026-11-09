package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.theme.MapPalette
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.motion.MapPopUp
import com.zynergylabs.forager.app.ui.motion.PopUpPivot

/**
 * Every site in this app that places something on the map — the offline-region picker, log-entry
 * creation, waypoint dropping, trip planning — used a long-press to do it, before this file
 * existed. **Replaced everywhere by one idiom: a marker fixed at screen centre, the map panning
 * underneath it, committed with OK or cancelled.**
 *
 * **This is not a style preference.** Long-press is unreliable for people with thicker fingers,
 * and a *draggable* marker has the identical defect — the same finger that's supposed to be
 * precise about a point is the thing covering that point while it drags. A marker pinned to
 * screen centre keeps the finger off the target entirely: the finger drives the map, never the
 * pin. This is an accessibility decision with a stated reason. Do not "improve" this into a
 * draggable marker — that reintroduces the exact defect this replaces, just with extra steps.
 *
 * ## Why this needs [MapSlot.onCameraIdle], not [MapSlot.onLongPress]
 *
 * [MapSlot] had no way to answer "where is the camera pointing right now" before this file —
 * every existing use only ever told the map where to go ([Region], [MapSlot.focusOverride]), never
 * asked it where it ended up. A centre-pinned marker needs exactly that: the picked point *is*
 * wherever the camera settles, read back once panning stops. [MapSlot.onCameraIdle] is that read
 * side, verified against the pinned `org.maplibre.gl:android-sdk:13.5.0` artifact
 * (`MapLibreMap.addOnCameraIdleListener`/`getCameraPosition` — see [SightingsMap]'s own wiring).
 *
 * ## Two shapes, one idiom
 *
 * [CentrePinLocationPicker] owns its own [mapSlot] instance — for a site with no map already on
 * screen (the offline-region picker's panel, the journal's own entry-location screen).
 * [CentrePinLocationPickerOverlay] is just the pin-and-buttons chrome, meant to sit inside a `Box`
 * that already has its own `mapSlot(...)` call — for a site where the map is already rendering
 * something else (sightings, planned trips, waypoints) that a second, separate picker map
 * instance would either duplicate or hide. Both render the identical pin and OK/Cancel row; only
 * who owns the map differs.
 */
@Composable
fun CentrePinLocationPicker(
    mapSlot: MapSlot,
    region: Region,
    basemap: Basemap,
    modifier: Modifier = Modifier,
    /**
     * Night mode, passed through to the map the same way the main screen's does. Defaulted so the
     * journal's own pickers keep today's behaviour until their callers thread it — see this
     * parameter's call sites.
     */
    night: Boolean = false,
    onConfirm: (LatLng) -> Unit,
    onCancel: () -> Unit,
    /**
     * Width-to-height ratio for **the map viewport itself**, or `null` (the default) to let the
     * map take whatever height is left over after this composable's own instruction line and
     * OK/Cancel row.
     *
     * Opt-in per call site rather than a constant here, because the two shapes want opposite
     * things. The journal's entry-location pickers ([com.zynergylabs.forager.app.ui.log.JournalTab],
     * [com.zynergylabs.forager.app.ui.log.LogPanel]) hand this composable a `weight(1f)` slot and want the map
     * to fill it — on a tall phone that is considerably more than 4:3, so pinning a ratio there
     * would make those maps *shorter*, the opposite of what the sizing is for. The offline-region
     * picker wants a determinate 4:3 map and passes one.
     *
     * Rejected alternative: putting `aspectRatio` on the caller's own wrapping `Box`, which is
     * what `OfflineMapsPanel` did before. That constrains the whole picker — instruction line and
     * button row included — so the *map* ends up the leftover, measured at 360x146dp on a 360dp
     * phone against a 360x270dp box: a 2.5:1 letterbox, not the 4:3 it looked like. Constraining
     * the map and letting the chrome add its own height is what actually makes the viewport 4:3.
     */
    mapAspectRatio: Float? = null,
) {
    val state = rememberCentrePinState(region)
    // Fills its slot only when the map is the leftover (mapAspectRatio == null). With a ratio, the
    // map has a determinate height and this column wraps its content, so the caller gets a picker
    // whose height is the map plus its own chrome rather than one that stretches.
    //
    // L1 (dispatch 2026-09-28-47): the instruction, the map and the confirm row are three
    // composables now, holding one [CentrePinState], so the offline panel can lay the same pieces
    // out side by side in a short landscape window. This layout is unchanged.
    Column(modifier = if (mapAspectRatio == null) modifier.fillMaxSize() else modifier.fillMaxWidth()) {
        CentrePinInstruction()
        CentrePinMap(
            state = state,
            mapSlot = mapSlot,
            basemap = basemap,
            night = night,
            modifier = if (mapAspectRatio == null) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth().aspectRatio(mapAspectRatio)
            },
        )
        CentrePinConfirmActions(state = state, onConfirm = onConfirm, onCancel = onCancel)
    }
}

/**
 * The picker's pin state: whether the user has touched the map, the region the map is handed, and
 * where the camera last settled. Moved here unchanged from [CentrePinLocationPicker]'s own
 * `remember`s (L1), so a caller can hold it across two layouts (the offline panel, stacked and
 * side by side).
 *
 * "Follow until you touch it" (owner ruling, picker-fixes dispatch F1): until the user's first
 * touch on the map, the region handed to mapSlot, and the pin, follow the caller's region — so a
 * picker opened before any location fix moves to the first one. After that touch, a new caller
 * region changes neither: the find picker's region is the device's live fix, a new one about
 * every second, and before this each one reset the pin and (tracking already ended by the pan)
 * moved SightingsMap's camera back to the device at zoom 13 — the owner's "snaps back after
 * every pan". The touch is MapRenderMode.onUserCameraGesture, not onCameraIdle, because the
 * first activation's ease to zoom 16 and every region-driven camera move end in an idle too.
 *
 * Idle events are still never fed back into mapSlot's region argument: that would re-run
 * SightingsMap's region-keyed camera effect, and zoomForRadiusKm, on every pan.
 */
@Stable
internal class CentrePinState(region: Region) {
    var touched by mutableStateOf(false)
        private set
    var mapRegion by mutableStateOf(region)
        private set
    var cameraCenter by mutableStateOf(LatLng(region.lat, region.lng))
        private set
    private var previousRegion by mutableStateOf(region)

    val onUserCameraGesture: () -> Unit = { touched = true }
    val onCameraIdle: (LatLng) -> Unit = { location -> cameraCenter = location }

    /**
     * Writes during composition, of state this holder owns, converging in one pass (the next pass
     * sees region == previousRegion). Chosen over a LaunchedEffect so the map is never handed one
     * frame of the old region after the caller's has changed.
     */
    fun follow(region: Region) {
        if (region == previousRegion) return
        val next = centrePinMapRegion(previousRegion, region, mapRegion, touched, cameraCenter)
        previousRegion = region
        if (!touched) cameraCenter = LatLng(next.lat, next.lng)
        mapRegion = next
    }
}

/** A [CentrePinState] remembered by the caller, following [region] as [CentrePinState.follow] says. */
@Composable
internal fun rememberCentrePinState(region: Region): CentrePinState {
    val state = remember { CentrePinState(region) }
    state.follow(region)
    return state
}

/** The picker's one instruction line, above the map in the stacked layout. */
@Composable
internal fun CentrePinInstruction() {
    Text(
        "Pan the map to position the pin, then confirm.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

/** The picker's map and its centre pin, sized by [modifier]. */
@Composable
internal fun CentrePinMap(state: CentrePinState, mapSlot: MapSlot, basemap: Basemap, night: Boolean, modifier: Modifier) {
    Box(modifier = modifier) {
        mapSlot(
            state.mapRegion,
            MapOverlayContent(),
            MapRenderMode(basemap, night, onUserCameraGesture = state.onUserCameraGesture),
            null,
            {},
            {},
            { _, _, _ -> },
            state.onCameraIdle,
            Modifier.fillMaxSize(),
        )
        CentrePin(night = night, modifier = Modifier.align(Alignment.Center))
    }
}

/** The "Pin at:" line and the OK/Cancel row under it. */
@Composable
internal fun CentrePinConfirmActions(
    state: CentrePinState,
    onConfirm: (LatLng) -> Unit,
    onCancel: () -> Unit,
    /**
     * Dispatch 2026-10-17-09, RECORD -1253 (the owner: "C: OK, Cancel and Download on one line"): more buttons on OK and Cancel's
     * line, after them (the offline-maps panel's Download, in landscape). `null` (every other caller): OK and Cancel share the
     * line half and half, as before.
     */
    trailing: (@Composable RowScope.() -> Unit)? = null,
    /**
     * Dispatch 2026-10-18-03, item 1 (Forager RECORD -1269; the owner: "Remove both OK and Cancel and just have the Download Maps
     * option"): `false` drops OK and Cancel, leaving the "Pin at" line and [trailing] (the offline-maps screen, whose Download
     * Maps reads the pin itself). `true`, the default, is every other picker's, unchanged.
     */
    showConfirmButtons: Boolean = true,
) {
    CentrePinConfirmRow(
        // UI-defects dispatch, §2: this is the pin's current map position, live from
        // onCameraIdle above — it updates continuously as the map is panned, whether or not
        // OK has ever been pressed. "Selected:" read as a completed pick and contradicted a
        // sibling "No location picked yet" line that reads the *confirmed* pick instead (a
        // different piece of state — see OfflineMapsPanel's own hasValidRegion). Both lines
        // were individually correct; only the wording claimed a selection that hadn't
        // happened yet.
        selectedText = "Pin at: ${"%.4f".format(state.cameraCenter.lat)}, ${"%.4f".format(state.cameraCenter.lng)}",
        onConfirm = { onConfirm(state.cameraCenter) },
        onCancel = onCancel,
        trailing = trailing,
        showConfirmButtons = showConfirmButtons,
    )
}

/**
 * The region [CentrePinLocationPicker] hands its map when the caller's region changes from
 * [previous] to [caller], given the region it is showing ([shown]), whether the user has touched
 * the map ([touched]) and where the camera last settled ([cameraCenter]).
 *
 * - **Not touched:** the caller's region, whole (follow until touched, F1).
 * - **Touched, and only the radius changed** (the offline picker's radius slider, F3 / the
 *   diagnosis's M2): the panned point at the new radius, so the pin and OK keep the panned point
 *   and the zoom still follows the radius as it did before (`zoomForRadiusKm`).
 * - **Touched, and the centre changed** (a new live fix, the offline picker's late device centre,
 *   the offline picker's own confirmed pick): unchanged. This includes a centre and radius change
 *   together: the find picker's first fix after a pan swaps the search region (15 km) for the
 *   device's (1 km), and that is a follow the user has already overridden, not a radius they chose.
 *
 * Telling "only the radius changed" apart by comparing [previous] with [caller], rather than a
 * separate radius parameter, keeps every call site as it was: the offline panel already builds its
 * region from the slider's value, and the find pickers' radius never changes on its own.
 */
internal fun centrePinMapRegion(previous: Region, caller: Region, shown: Region, touched: Boolean, cameraCenter: LatLng): Region = when {
    !touched -> caller
    caller.lat == previous.lat && caller.lng == previous.lng && caller.radiusKm != previous.radiusKm ->
        Region(cameraCenter.lat, cameraCenter.lng, caller.radiusKm)
    else -> shown
}

/**
 * The pin-and-buttons chrome alone, for a site whose map is already on screen showing something
 * else — see this file's class doc comment. [onConfirm] takes no argument: the caller already
 * owns the [mapSlot] call this sits on top of, and is already tracking [MapSlot.onCameraIdle]
 * itself (it has to, to draw anything else that depends on the current viewport), so it already
 * has the coordinate in hand when this fires.
 */
@Composable
fun CentrePinLocationPickerOverlay(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * How far above this overlay's own bottom edge the OK/Cancel row sits. Owner finding on
     * device: the compact Map tab's content Box spans the full screen height (its bottom nav
     * floats over it and slides away in fullscreen, and the app is edge-to-edge), so a row aligned
     * to that Box's bottom lands under the app's nav outside fullscreen and under Android's own
     * navigation bar in it. The caller passes whichever of those is currently underneath: its
     * measured nav height (which already includes the system bar it consumes) or the system
     * navigation-bar inset. Zero keeps the previous behaviour for callers whose Scaffold already
     * pads for its bottom bar.
     */
    bottomInset: Dp = 0.dp,
    /**
     * Padding for the OK/Cancel row alone (dispatch 2026-09-28-104, item 5): in a short landscape
     * window the Maps tab passes its `controlsPadding` (the cut-out band and the rail's measured width on
     * the port side), so the row clears the rail and the system bar the rail takes. **Not applied to the
     * overlay's own frame**, because the pin is centred in that frame and marks the map's centre: padding
     * one side would move the pin off the coordinate it reports. Zero (the default) keeps every other
     * caller, and portrait, as it was.
     */
    rowPadding: PaddingValues = PaddingValues(0.dp),
    /**
     * Night Maps, as the map underneath is drawing it: the caller passes its own
     * `MapRenderMode.night`, so the pin's colours follow the map's (colour build C2 (e)). Defaults to
     * day for callers that have no night value.
     */
    night: Boolean = false,
) {
    // The row's fill and content colour: CentrePinConfirmSurface's comment (moved there with motion Part 2).
    Box(modifier = modifier.fillMaxSize()) {
        CentrePin(night = night, modifier = Modifier.align(Alignment.Center))
        CentrePinConfirmSurface(
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset)
                .padding(rowPadding)
                .fillMaxWidth(),
        )
    }
}

/**
 * [CentrePinLocationPickerOverlay] coming and going over the Maps tab's map (motion Part 2, item 4, dispatch 2026-09-28-666;
 * scout M9; the owner, RECORD -651: "Fade and grow", each from where it belongs): composed whether or not it shows, with
 * [visible] deciding. The pin grows from its tip, which marks the map's centre, and the OK/Cancel row up from its bottom edge;
 * each shrinks back as it leaves, and from that moment takes no touch (item 5, "Let taps through at once"). Laid out exactly as
 * the overlay is, from the same pieces, so the pin marks the same point and the row takes the same touches; the other screens'
 * pickers keep the overlay, which appears and goes at once as before.
 */
@Composable
internal fun CentrePinLocationPickerPopUp(
    visible: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    rowPadding: PaddingValues = PaddingValues(0.dp),
    night: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        MapPopUp(visible = visible, pivot = PopUpPivot.Centre, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                CentrePin(night = night, modifier = Modifier.align(Alignment.Center))
            }
        }
        MapPopUp(
            visible = visible,
            pivot = PopUpPivot.BottomCentre,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset)
                .padding(rowPadding)
                .fillMaxWidth(),
        ) {
            CentrePinConfirmSurface(onConfirm = onConfirm, onCancel = onCancel, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The OK/Cancel row's surface, at the map chrome's alpha: this sits only inside a map's own Box, so it is always over a map.
 * Its content colour is pinned to the fill's own role (`contentColorFor` matches a colour-scheme role exactly; see
 * `MapLayersSheet`). [modifier] places it.
 */
@Composable
private fun CentrePinConfirmSurface(onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val rowColor = mapChromeFill(navigationBarContainerColor(), overMap = true)
    val rowContentColor = contentColorFor(navigationBarContainerColor())
    Surface(
        modifier = modifier
            .testTag(CENTRE_PIN_CONFIRM_ROW_TAG)
            .mapChromeContainerColor(rowColor),
        color = rowColor,
        contentColor = rowContentColor,
        shadowElevation = 4.dp,
    ) {
        CentrePinConfirmRow(
            selectedText = null,
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = Modifier.mapChromeContentColor(LocalContentColor.current),
        )
    }
}

/**
 * The centre pin: Material `LocationOn`, filled in [MapPalette.centrePin] with a casing behind it in
 * [MapPalette.casing], both from [MapPalette.forMode] for [night] (colour build C2 (e)). Before C2 it
 * was tinted with the theme's `primary`, which followed the device theme rather than the map. Its
 * shape, 40dp size and anchor are unchanged.
 */
@Composable
private fun CentrePin(night: Boolean, modifier: Modifier = Modifier) {
    val palette = MapPalette.forMode(night)
    val casingVector = remember(palette.casing) { centrePinCasingVector(palette.casing) }
    // LocationOn's drawn point sits at the bottom-centre of its bounding box, not the geometric
    // centre Alignment.Center gives every caller — shifted up by half the icon's own height so the
    // pin's tip, not the icon's box, is what actually marks the coordinate onConfirm reports.
    Box(modifier = modifier.padding(bottom = CENTRE_PIN_SIZE / 2).size(CENTRE_PIN_SIZE)) {
        Icon(
            imageVector = casingVector,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Pin marks the location that will be picked",
            tint = Color(palette.centrePin),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The centre pin's casing: `LocationOn`'s own paths, filled and stroked in [casing] with round joins,
 * the stroke [CASING_WIDTH_DP] wide on each side of the outline at [CENTRE_PIN_SIZE]. Drawn behind
 * the pin, it shows only as the outline outside the pin's edge and round its hole. The paths are
 * copied, not approximated, so the casing matches the icon exactly. `LocationOn`'s root holds its
 * paths directly (no groups, no transforms); anything else is an error rather than a silently wrong
 * outline.
 */
internal fun centrePinCasingVector(casing: Int): ImageVector {
    val source = Icons.Filled.LocationOn
    // The stroke straddles the outline, so its full width is two casings, in viewport units.
    val strokeUnits = 2 * CASING_WIDTH_DP * source.viewportWidth / CENTRE_PIN_SIZE.value
    val builder = ImageVector.Builder(
        name = "LocationOnCasing",
        defaultWidth = source.defaultWidth,
        defaultHeight = source.defaultHeight,
        viewportWidth = source.viewportWidth,
        viewportHeight = source.viewportHeight,
    )
    for (node in source.root) {
        val path = node as? VectorPath ?: error("LocationOn has a ${node::class.simpleName}; the casing copies paths only")
        builder.addPath(
            pathData = path.pathData,
            pathFillType = path.pathFillType,
            fill = SolidColor(Color(casing)),
            stroke = SolidColor(Color(casing)),
            strokeLineWidth = strokeUnits,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        )
    }
    return builder.build()
}

@Composable
private fun CentrePinConfirmRow(
    selectedText: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showConfirmButtons: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (selectedText != null) {
            Text(selectedText, style = MaterialTheme.typography.bodySmall)
        }
        // Dispatch 2026-10-18-03: with no OK and Cancel, only what the caller adds, and no row at all when it adds nothing.
        if (!showConfirmButtons) {
            if (trailing != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically, content = trailing)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (trailing == null) {
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("OK") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            } else {
                // RECORD -1253: three on one line in a half-width panel. OK and Cancel take their labels' width, with the
                // compact padding (CENTRE_PIN_COMPACT_BUTTON_PADDING), and the trailing buttons the rest.
                Button(onClick = onConfirm, contentPadding = CENTRE_PIN_COMPACT_BUTTON_PADDING) { Text("OK", maxLines = 1) }
                OutlinedButton(onClick = onCancel, contentPadding = CENTRE_PIN_COMPACT_BUTTON_PADDING) { Text("Cancel", maxLines = 1) }
                trailing()
            }
        }
    }
}

private val CENTRE_PIN_SIZE = 40.dp

/**
 * RECORD -1253: the horizontal padding of buttons sharing OK and Cancel's line with more buttons: [Spacing.md] in place of
 * Material's 24 dp, so three fit a half-width panel on one line. The vertical padding is Material's own (8 dp).
 */
internal val CENTRE_PIN_COMPACT_BUTTON_PADDING = PaddingValues(horizontal = Spacing.md, vertical = 8.dp)

/** [CentrePinLocationPickerOverlay]'s own OK/Cancel surface — what tests measure its real placement by. */
const val CENTRE_PIN_CONFIRM_ROW_TAG = "centre-pin-confirm-row"
