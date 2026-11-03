package com.zynergylabs.forager.app.ui.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import kotlinx.coroutines.delay

/**
 * The map's credit strip ([MapCreditCaption]), shown and then faded into MapLibre's "i". D25 (dispatch 2026-10-30-08, RECORD
 * -1968), after the licence check (-1967): OSMF's attribution guidelines want the OpenStreetMap credit shown on the map first,
 * then allow it to fade "automatically on map interaction such as panning, clicking, or zooming; automatically after five
 * seconds", with the "(i)" left to find it; the OSM tile policy says "Do not hide attribution beneath UI, behind toggles, or
 * off-screen". The owner: "Show, then fade to the 'i' (Recommended)". D26 (-1969) is the planner's reading of "when a map
 * opens", stated to the owner:
 *
 * - **It shows** when a map is made (this composable entering composition: the Maps tab, an entry's map, the pin picker, the
 *   offline panel, an Activity recreation) and whenever its words change (a basemap switch, the offline swap, the Trails toggle,
 *   topo at night). One showing per text ([remember] keyed on it), so the 5 s start again each time it shows.
 * - **It does not show again** on a rotation alone (MainActivity handles the rotation itself, so the map's composition and
 *   this state stay, and nothing here reads the window) or on a return from the background (the composition stays; the
 *   guideline: "If attribution is presented to the user upon application startup, it does not need to be presented to the
 *   user every time").
 * - **It fades** [MAP_CREDIT_STRIP_SHOWN_MILLIS] after it shows, or at the first user pan or zoom since it showed, whichever is
 *   first: [userCameraGestures] counts MapLibre's gesture reason (MapListeners.kt), never follow-me or a route fit.
 * - **It never fades while OpenStreetMap's raster tiles are drawn** ([drawsStreetRaster]), including the -1513 fallback
 *   and topo at night after it: the owner's option gave the reason "as those tiles require", so the rule follows the tiles.
 *   Leaving them is a new showing, so the strip then stays 5 s like any other.
 * - **Motion:** the fade alone, on the map pop-up's fade ([MotionTokens.mapPopUpFadeSpec]), kept under reduced motion like the
 *   pop-up's and the compass strip's sundown line (docs/motion-spec.md section 4; the planner's call, -1970). Drawn only
 *   ([graphicsLayer]): the strip keeps its place and size while faded, so D17's Re-center beside the L, which reads its size
 *   (onCreditSizeChanged), does not move when it fades. It took no touch before and takes none now.
 */
@Composable
internal fun MapCreditStrip(
    basemap: Basemap,
    useOfflineTiles: Boolean,
    layerCredits: List<String>,
    nightMode: Boolean,
    ownTrail: Boolean,
    userCameraGestures: Int,
    modifier: Modifier = Modifier,
) {
    val text = attributionCaption(mapCreditsFor(basemap, useOfflineTiles, layerCredits, nightMode, ownTrail))
    val keepShown = drawsNeverFadingCredit(basemap, useOfflineTiles, nightMode, ownTrail)
    // One showing per text and per whether OSM's tiles are drawn: leaving them starts a showing of its own. The gesture count it
    // started at, so a pan made before this showing does not end it.
    val showing = remember(text, keepShown) { MapCreditShowing(gesturesAtStart = userCameraGestures) }
    var timedOut by remember(showing) { mutableStateOf(false) }
    LaunchedEffect(showing) {
        delay(MAP_CREDIT_STRIP_SHOWN_MILLIS)
        timedOut = true
    }
    val shown = keepShown || !(timedOut || userCameraGestures != showing.gesturesAtStart)
    val alpha by animateFloatAsState(if (shown) 1f else 0f, animationSpec = MotionTokens.mapPopUpFadeSpec<Float>(), label = "mapCreditStripFade")
    MapCreditCaption(
        text = text,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .semantics { mapCreditStripAlpha = alpha },
    )
}

/** One showing of the credit strip: see [MapCreditStrip]. A class, not the count itself, so two showings are never equal. */
private class MapCreditShowing(val gesturesAtStart: Int)

/**
 * Whether the map draws a raster whose credit never fades ([creditNeverFades]; dispatch 2026-11-03-05 D7: today the Street
 * map's, `ui/map/street/`, OpenStreetMap's raster tiles, D26): the Street map; Trail Map once it has fallen back ([ownTrail] `false`,
 * RECORD -1513), which draws OSM's raster in its place ([rasterDrawn]); and topo at night after the fallback, which draws OSM's
 * raster below the night fade ([nightLowZoomBasemap]). Never offline, whose style is the downloaded vector one. The same
 * branches [mapStyleSourceFor] takes, so the two cannot disagree (`DrawsStreetRasterTest` checks every case against the
 * style JSON).
 */
internal fun drawsNeverFadingCredit(basemap: Basemap, useOfflineTiles: Boolean, night: Boolean, ownTrail: Boolean): Boolean {
    if (useOfflineTiles || ownTrailStyleSourceFor(basemap, night, ownTrail) != null) return false
    val raster = basemap.rasterDrawn()
    return raster.creditNeverFades() || (night && nightLowZoomBasemap(raster)?.creditNeverFades() == true)
}

/** The alpha the credit strip draws at now, 1 shown and 0 faded into the "i", readable in tests. */
val MapCreditStripAlphaKey = SemanticsPropertyKey<Float>("MapCreditStripAlpha")
var SemanticsPropertyReceiver.mapCreditStripAlpha by MapCreditStripAlphaKey

/** OSMF's attribution guidelines: the credit may fade "automatically after five seconds". */
internal const val MAP_CREDIT_STRIP_SHOWN_MILLIS = 5_000L
