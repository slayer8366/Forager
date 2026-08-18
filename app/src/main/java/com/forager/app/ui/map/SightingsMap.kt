package com.forager.app.ui.map

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.PathShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Shows the searched region as a map with a marker per real observation ([sightings]).
 *
 * When [areas] is non-empty, numbered foraging-area markers and dashed order connectors are
 * drawn over the individual pins (the pins stay: they're the evidence the areas were derived
 * from). Pass an empty list to show pins alone.
 *
 * [plannedTrips] draws a third, distinct marker per planned trip — a diamond, to read as
 * different from both the translucent sighting dots (density of what's been observed) and the
 * numbered foraging-area labels (where to go based on that history): a planned trip is neither of
 * those, it's a place the user chose for themselves.
 *
 * [onLongPress] fires with the geographic point under a long-press, for the caller to turn into a
 * planned trip (via a date picker it owns — this composable knows nothing about dates or
 * persistence, only where the gesture happened). Wired through [MapEventsOverlay] rather than a
 * gesture detector on the [AndroidView] itself, because osmdroid's own touch handling already
 * owns pan/zoom/marker-tap on this `MapView`; layering a second, independent gesture detector on
 * top would race it for the same touch stream. [MapEventsOverlay] is osmdroid's own mechanism for
 * this and composes with its existing overlays instead of fighting them.
 *
 * ## Why the clip
 *
 * The [Modifier.clipToBounds] on the [AndroidView] below is load-bearing. Without it this map
 * paints over the tab row above it and the caption below it, and the dashed order connector runs
 * up into the app bar. Two facts combine, neither a bug on its own:
 *
 * 1. **Compose does not clip a hosted View to its slot.** `AndroidViewsHandler` sets
 *    `clipChildren = false` and draws the holder with the one-argument `View.draw(Canvas)` from a
 *    `drawBehind` inside the layout node, so the clip `ViewGroup.drawChild` normally imposes on a
 *    child never happens — and no Compose clip happens either unless a modifier asks for one.
 *    Whatever the View paints outside `[0, 0, width, height]` lands on the siblings around it.
 * 2. **osmdroid paints outside that rectangle on purpose**, because it assumes the host clips.
 *    `MapView.dispatchDraw` sets no clip. `TilesOverlay` draws whole tile bitmaps at tile-grid
 *    positions, so edge tiles overhang the viewport by up to one tile — 256px at an integer zoom,
 *    362px at the 10.5 [zoomForRadiusKm] picks for radii up to 30km — which is several times the
 *    height of the tab row above the map. `LinearRing.setClipArea` keeps polyline geometry out to
 *    2.2x the view's half-diagonal so that map rotation can never crop a line, roughly a whole
 *    map-height past the top and bottom edges, and `PolyOverlayWithIW` draws that path uncropped.
 *    `Marker.drawAt` even asks `canvas.getClipBounds()` whether a marker is on screen — with no
 *    clip set that is answered against the whole window, so off-map area markers draw too.
 *
 * The overspill is measured from the map's own centre and edges, so it gets worse as this
 * composable gets smaller — which is why it was reported as the map breaking its bounds *when
 * resized*. Shrinking the slot (the foraging-areas panel below appearing, then growing when
 * clusters load) or zooming in pushes geometry that used to be inside the rectangle out of it.
 *
 * Rejected: `clipChildren`/`clipToPadding` on the `MapView`, which govern child *Views* — the
 * tiles, polyline and markers are canvas drawing done in `dispatchDraw`, so neither flag touches
 * them. Also rejected: clipping at the call site in `AvailabilityScreen`, because "this composable
 * does not paint outside its bounds" is this composable's invariant, not each caller's to
 * remember.
 *
 * Not a cause, checked and ruled out: stale layout bounds after a resize. `MapView.dispatchDraw`
 * and `myOnLayout` both call `resetProjection()`, and `Projection` derives its offsets from
 * `getScreenCenterX/Y` of the *current* intrinsic screen rect, so osmdroid re-centres itself on a
 * size change without help from [AndroidView]'s `update` block.
 *
 * ## The basemap is a parameter, and the overlays don't care what it is
 *
 * [basemap] chooses which service supplies the raster tiles underneath. Everything this composable
 * draws — the sighting dots, the numbered area markers, the dashed order connector, the planned-trip
 * diamonds, the search-centre pin — is app-drawn geometry in `view.overlays`, positioned by
 * `GeoPoint` and rebuilt from the parameters on every `update`. The tile source feeds `TilesOverlay`
 * alone. Swapping it therefore cannot disturb any of them, and [applyBasemap] deliberately does not
 * touch the overlay list; `SightingsMapBasemapSwapTest` asserts the overlays are still there after a
 * swap rather than leaving that to the argument.
 */
@Composable
fun SightingsMap(
    region: Region,
    sightings: List<Sighting>,
    modifier: Modifier = Modifier,
    areas: List<ForagingArea> = emptyList(),
    plannedTrips: List<PlannedTrip> = emptyList(),
    basemap: Basemap = Basemap.DEFAULT,
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    focusOverride: LatLng? = null,
    onLongPress: (LatLng) -> Unit = {},
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onTap: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            // The initial source comes from the same applyBasemap the update block uses, so there
            // is only one place that decides what "this basemap" means on a MapView.
            applyBasemap(this, basemap)
            setMultiTouchControls(true)
            overlays.add(CopyrightOverlay(context))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        // Load-bearing, not cosmetic. See "Why the clip" on this composable's doc comment.
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        update = { view ->
            // Before the zoom below, not after: applyBasemap installs this basemap's zoom ceiling,
            // and osmdroid's setZoomLevel clamps against whatever ceiling is in place when it runs.
            applyBasemap(view, basemap)
            val center = GeoPoint(region.lat, region.lng)
            view.controller.setZoom(zoomForRadiusKm(region.radiusKm))
            // focusOverride pans the camera without moving the search-location marker or the
            // zoom-from-radius heuristic below, both of which stay anchored to region — see
            // MapSlot's doc comment on this parameter for why the two are kept independent.
            val cameraTarget = focusOverride?.let { GeoPoint(it.lat, it.lng) } ?: center
            view.controller.setCenter(cameraTarget)

            // Rebuild markers each update; keep the copyright overlay, which is index 0.
            while (view.overlays.size > 1) view.overlays.removeAt(view.overlays.size - 1)

            // Added early, not that it matters for touch dispatch (osmdroid offers a long-press to
            // overlays top-down and Marker never consumes one, so this would fire regardless of
            // position) — added here so it reads next to the overlay list it belongs to.
            view.overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            onTap()
                            // false: unlike longPressHelper below, a plain tap isn't meant as
                            // "consumed" — osmdroid still needs it for marker taps, which this
                            // overlay is added ahead of only for touch-dispatch ordering (see the
                            // comment above), not to intercept every tap.
                            return false
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p == null) return false
                            onLongPress(LatLng(p.latitude, p.longitude))
                            return true
                        }
                    },
                ),
            )

            view.overlays.add(
                Marker(view).apply {
                    position = center
                    title = "Search location"
                    snippet = "Radius: ${region.radiusKm} km"
                },
            )
            // One shared drawable for every observation: osmdroid sets bounds and draws the
            // markers one at a time, and nothing here mutates it.
            val dot = sightingDotIcon(view.context.resources.displayMetrics.density)
            sightings.forEach { sighting ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(sighting.lat, sighting.lng)
                        icon = dot
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = sighting.commonName ?: sighting.scientificName
                        snippet = sighting.observedOn?.toString() ?: sighting.scientificName
                    },
                )
            }
            addForagingAreaOverlays(view, center, areas)
            addPlannedTripOverlays(view, plannedTrips)
            view.invalidate()
        },
    )
}

/**
 * Points [view] at [basemap]'s tile source and installs that basemap's zoom ceiling.
 *
 * Called from both the `factory` and the `update` block of [SightingsMap], so "what this basemap
 * means on a MapView" is decided once.
 *
 * **The guard matters.** `update` runs on every recomposition, and
 * `MapTileProviderBase.setTileSource` calls `clearTileCache()` unconditionally — so setting the
 * source every pass would throw away every in-memory tile and re-fetch the visible grid each time
 * anything on the screen changed. Comparing `name()` first means the swap, and the flush that comes
 * with it, happen only on an actual basemap change. `name()` is the right identity to compare on:
 * it is the same string osmdroid uses as the `provider` column that namespaces the disk cache, so
 * two sources that compare equal here really are one basemap.
 *
 * **`setMaxZoomLevel` is not redundant with osmdroid's own ceiling, and it fixes a bug that predates
 * the basemap selector.** The obvious assumption — that a `MapView` with no explicit maximum already
 * stops at whatever its tile source declares — is wrong, and it was measured rather than reasoned:
 * deleting the call below makes `SightingsMapBasemapSwapTest` report a ceiling of **29** on every
 * basemap, not 19 or 15. The chain is that `MapView.getMaxZoomLevel()` falls back to
 * `TilesOverlay` → `MapTileProviderArray.getMaximumZoomLevel()`, which returns the **maximum across
 * its module providers** rather than the tile source's value. `MapTileDownloader` does report the
 * source's 15 or 19, but `MapTileProviderBasic` also stacks a `MapTileApproximater` — the module that
 * fabricates a missing high-zoom tile by upscaling a lower-zoom one — and that one answers
 * `TileSystem.getMaximumZoomLevel()`, i.e. `primaryKeyMaxZoomLevel = 29`. The larger number wins.
 *
 * So before this change the map already let the user zoom to 29 over an OpenStreetMap source that
 * serves tiles to 19, the last ten levels being upscaled blur. That is the same class of defect the
 * task set out to avoid on USGS, and it was already shipping on MAPNIK.
 *
 * Set every pass rather than only inside the swap branch above because `mMaximumZoomLevel` is
 * *sticky*: once set, `getMaxZoomLevel()` prefers it forever, so a ceiling installed on one basemap
 * and never lifted would silently cap a later one. Unconditional and idempotent is cheaper than
 * remembering which paths have to re-apply it.
 *
 * This is also where CLAUDE.md's "a reported range is not an operating limit" lands: the number
 * installed is [Basemap.maxZoom], this app's own explicit limit, and it is deliberately neither the
 * 29 osmdroid derives nor the 23 the USGS service advertises for itself — see [Basemap]'s doc comment
 * for the three conflicting answers USGS gives about its own maximum.
 *
 * Zoom is re-clamped as a consequence rather than by hand: osmdroid's `setZoomLevel` computes
 * `max(min, min(max, requested))`, and both `MapView.setTileSource` (which re-applies the current
 * zoom) and [SightingsMap]'s own `setZoom` call below run after this. So a pinch past USGS Topo's 15
 * stops at 15 rather than climbing into tiles the service answers with 404.
 */
private fun applyBasemap(view: MapView, basemap: Basemap) {
    val desired = tileSourceFor(basemap)
    if (view.tileProvider.tileSource.name() != desired.name()) {
        view.setTileSource(desired)
    }
    view.setMaxZoomLevel(basemap.maxZoom.toDouble())
}

/**
 * A small translucent dot for the per-observation markers.
 *
 * osmdroid's stock marker is a full-size pin. A dense radius puts a few hundred of them on the
 * map and they merge into one unreadable mass, which throws away the density that is the whole
 * signal in this data. A flat dot at partial alpha reads as density instead — the more
 * observations overlap, the darker the patch — and it leaves the pin shape meaning one specific
 * thing (the search centre) and the numbered labels meaning another (the foraging areas).
 *
 * Built in code rather than as a drawable resource so the size stays in dp next to the reason
 * for it. Unverified visually: see README's "Not yet verified".
 */
private fun sightingDotIcon(density: Float): Drawable {
    val diameterPx = (SIGHTING_DOT_DIAMETER_DP * density).toInt().coerceAtLeast(1)
    return ShapeDrawable(OvalShape()).apply {
        intrinsicWidth = diameterPx
        intrinsicHeight = diameterPx
        paint.color = SIGHTING_DOT_COLOR
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }
}

/**
 * Draws the foraging-area layer: a dashed connector through the area centres in visiting order,
 * then a numbered marker per area on top of it.
 *
 * A separate path from the per-sighting pin loop above rather than a flag threaded through it,
 * per CLAUDE.md; passing an empty [areas] is a no-op.
 *
 * The connector is **dashed on purpose**. It is a straight line between area centres in the
 * order the ordering step produced — it is not a walking path, and this project has no trail,
 * terrain, or land-ownership data with which to draw one. A solid line would read as a route
 * you could follow, potentially across a river, a motorway, a cliff, or private land. Do not
 * "tidy" this to a solid stroke: the line style is carrying the honesty, together with
 * [VISITING_ORDER_DISCLAIMER], which is also attached to the line's own info window.
 */
private fun addForagingAreaOverlays(view: MapView, searchCenter: GeoPoint, areas: List<ForagingArea>) {
    if (areas.isEmpty()) return

    // Starts at the search centre because that is where the ordering starts from.
    val orderedPoints = listOf(searchCenter) + areas.map { GeoPoint(it.center.lat, it.center.lng) }
    view.overlays.add(
        Polyline(view).apply {
            setPoints(orderedPoints)
            outlinePaint.apply {
                color = CONNECTOR_COLOR
                strokeWidth = CONNECTOR_STROKE_WIDTH_PX
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(CONNECTOR_DASH_PATTERN_PX, 0f)
            }
            title = "Visiting order"
            snippet = VISITING_ORDER_DISCLAIMER
        },
    )

    areas.forEach { area ->
        view.overlays.add(
            Marker(view).apply {
                position = GeoPoint(area.center.lat, area.center.lng)
                // Colours and size must be set before setTextIcon: it renders the icon there and then.
                setTextLabelBackgroundColor(AREA_MARKER_BACKGROUND_COLOR)
                setTextLabelForegroundColor(AREA_MARKER_FOREGROUND_COLOR)
                setTextLabelFontSize(AREA_MARKER_FONT_SIZE_PX)
                setTextIcon(area.visitOrder.toString())
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Area ${area.visitOrder}"
                snippet = foragingAreaSummary(area)
            },
        )
    }
}

/**
 * Draws one diamond marker per planned trip. A separate loop rather than folding into the
 * sighting or area loops above, per CLAUDE.md — a planned trip is neither an observation nor a
 * derived area, so it doesn't belong threaded through either one as a flag.
 */
private fun addPlannedTripOverlays(view: MapView, plannedTrips: List<PlannedTrip>) {
    if (plannedTrips.isEmpty()) return

    val icon = plannedTripIcon(view.context.resources.displayMetrics.density)
    plannedTrips.forEach { trip ->
        view.overlays.add(
            Marker(view).apply {
                position = GeoPoint(trip.location.lat, trip.location.lng)
                this.icon = icon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Planned trip"
                snippet = trip.date.toString()
            },
        )
    }
}

/**
 * A solid diamond, distinct in shape and colour from both the translucent sighting
 * [OvalShape] dots and the numbered-label area markers, so "planned" reads as its own kind of
 * pin rather than a variant of either.
 */
private fun plannedTripIcon(density: Float): Drawable {
    val sizePx = (PLANNED_TRIP_MARKER_SIZE_DP * density)
    val diamond = Path().apply {
        moveTo(sizePx / 2f, 0f)
        lineTo(sizePx, sizePx / 2f)
        lineTo(sizePx / 2f, sizePx)
        lineTo(0f, sizePx / 2f)
        close()
    }
    return ShapeDrawable(PathShape(diamond, sizePx, sizePx)).apply {
        intrinsicWidth = sizePx.toInt().coerceAtLeast(1)
        intrinsicHeight = sizePx.toInt().coerceAtLeast(1)
        paint.color = PLANNED_TRIP_MARKER_COLOR
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }
}

// Mushroom orange over the app's forest green, matching ui/theme/Color.kt. osmdroid draws on a
// raw Android Canvas, so these are android.graphics colours, not Compose ones.
private const val CONNECTOR_COLOR = 0xFFC97B3D.toInt()

/** Bark at ~70% alpha: overlapping observations darken instead of blotting each other out. */
private const val SIGHTING_DOT_COLOR = 0xB33B2E24.toInt()
private const val SIGHTING_DOT_DIAMETER_DP = 9f
private const val AREA_MARKER_BACKGROUND_COLOR = 0xFF2E5339.toInt()
private val AREA_MARKER_FOREGROUND_COLOR = Color.WHITE
private const val AREA_MARKER_FONT_SIZE_PX = 36
private const val CONNECTOR_STROKE_WIDTH_PX = 6f

/** A sky blue, distinct from both the area marker's forest green and the connector's orange. */
private const val PLANNED_TRIP_MARKER_COLOR = 0xFF3B6EA5.toInt()
private const val PLANNED_TRIP_MARKER_SIZE_DP = 22f

/** 18px dash, 14px gap: long enough to read as deliberate dashing at any usable zoom. */
private val CONNECTOR_DASH_PATTERN_PX = floatArrayOf(18f, 14f)

/**
 * A visual-only heuristic mapping search radius to a legible starting zoom level, not a domain
 * prediction.
 *
 * It knows the radius but not the size of the composable it is being drawn into, so it cannot
 * promise the whole searched circle fits. A pixel at zoom 12 spans ~38m at the equator and ~24m
 * at 50°N, so the edge of a 15km radius sits 390–690px from the centre depending on latitude —
 * which at the top of that range is more than the half-height of a short map slot. An area out
 * there is simply
 * off the visible map and the connector to it runs off the edge — normal map behaviour, and it is
 * reachable by panning. It is worth stating because before the clip on the [AndroidView] above,
 * that same geometry was drawn *outside* the map instead of being cropped by it. Nothing is lost
 * from the UI either way: ForagingAreasPanel lists every area with its number and summary
 * regardless of where the map viewport happens to sit.
 */
private fun zoomForRadiusKm(radiusKm: Int): Double = when {
    radiusKm <= 5 -> 13.0
    radiusKm <= 15 -> 12.0
    radiusKm <= 30 -> 10.5
    else -> 9.0
}
