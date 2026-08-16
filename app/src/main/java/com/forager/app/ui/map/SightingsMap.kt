package com.forager.app.ui.map

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
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Shows the searched region as a map with a marker per real observation ([sightings]). */
@Composable
fun SightingsMap(
    region: Region,
    sightings: List<Sighting>,
    modifier: Modifier = Modifier,
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
            view.invalidate()
        },
    )
}

/** A visual-only heuristic mapping search radius to a legible starting zoom level, not a domain prediction. */
private fun zoomForRadiusKm(radiusKm: Int): Double = when {
    radiusKm <= 5 -> 13.0
    radiusKm <= 15 -> 12.0
    radiusKm <= 30 -> 10.5
    else -> 9.0
}
