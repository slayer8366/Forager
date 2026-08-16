package com.forager.app.ui.map

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Shows the searched region as a map with a marker per real observation ([sightings]).
 *
 * When [areas] is non-empty, numbered foraging-area markers and dashed order connectors are
 * drawn over the individual pins (the pins stay: they're the evidence the areas were derived
 * from). Pass an empty list to show pins alone.
 */
@Composable
fun SightingsMap(
    region: Region,
    sightings: List<Sighting>,
    modifier: Modifier = Modifier,
    areas: List<ForagingArea> = emptyList(),
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
            setTileSource(TileSourceFactory.MAPNIK)
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
        modifier = modifier.fillMaxSize(),
        update = { view ->
            val center = GeoPoint(region.lat, region.lng)
            view.controller.setZoom(zoomForRadiusKm(region.radiusKm))
            view.controller.setCenter(center)

            // Rebuild markers each update; keep the copyright overlay, which is index 0.
            while (view.overlays.size > 1) view.overlays.removeAt(view.overlays.size - 1)

            view.overlays.add(
                Marker(view).apply {
                    position = center
                    title = "Search location"
                    snippet = "Radius: ${region.radiusKm} km"
                },
            )
            sightings.forEach { sighting ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(sighting.lat, sighting.lng)
                        title = sighting.commonName ?: sighting.scientificName
                        snippet = sighting.observedOn?.toString() ?: sighting.scientificName
                    },
                )
            }
            addForagingAreaOverlays(view, center, areas)
            view.invalidate()
        },
    )
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

// Mushroom orange over the app's forest green, matching ui/theme/Color.kt. osmdroid draws on a
// raw Android Canvas, so these are android.graphics colours, not Compose ones.
private const val CONNECTOR_COLOR = 0xFFC97B3D.toInt()
private const val AREA_MARKER_BACKGROUND_COLOR = 0xFF2E5339.toInt()
private val AREA_MARKER_FOREGROUND_COLOR = Color.WHITE
private const val AREA_MARKER_FONT_SIZE_PX = 36
private const val CONNECTOR_STROKE_WIDTH_PX = 6f

/** 18px dash, 14px gap: long enough to read as deliberate dashing at any usable zoom. */
private val CONNECTOR_DASH_PATTERN_PX = floatArrayOf(18f, 14f)

/** A visual-only heuristic mapping search radius to a legible starting zoom level, not a domain prediction. */
private fun zoomForRadiusKm(radiusKm: Int): Double = when {
    radiusKm <= 5 -> 13.0
    radiusKm <= 15 -> 12.0
    radiusKm <= 30 -> 10.5
    else -> 9.0
}
