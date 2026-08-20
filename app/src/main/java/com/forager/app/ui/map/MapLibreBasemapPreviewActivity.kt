package com.forager.app.ui.map

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.forager.app.map.ensureMapLibreStorageOutsideCache
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forager.app.ui.theme.ForagerTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Steps 1-2 of the osmdroid -> MapLibre migration (`docs/plans/maplibre-migration.md` §6, Track 2):
 * a basemap render, then sighting dots / numbered area markers / a dashed order connector as style
 * layers, re-created with the same colours and dash pattern [SightingsMap] uses so the "does this
 * still read against a real basemap" question (§6: "re-confirm the dash reads as dashed. Re-open
 * the colour question") is testable, not guessed at. Not wired into
 * [com.forager.app.MainActivity]/[MapSlot]/[AvailabilityScreen] at all — `SightingsMap`, `Basemap`,
 * and every production screen are completely untouched by this class.
 *
 * Launch it manually once installed, either:
 * `adb shell am start -n com.forager.app/com.forager.app.ui.map.MapLibreBasemapPreviewActivity`,
 * or — on a debug build, and with no separate adb host available to drive that command (e.g.
 * Termux running on the same phone, with no wireless-debugging pairing set up) — by tapping the
 * "MapLibre test" launcher icon `src/debug/AndroidManifest.xml` adds for exactly that case. That
 * overlay never reaches a release build.
 *
 * The basemap picker uses the *same* tile endpoints [BasemapTileSources] points osmdroid at
 * (USGS's ArcGIS `tile/{z}/{y}/{x}` order, OSM/OpenTopoMap's plain `{z}/{x}/{y}`), each wrapped in
 * the minimal MapLibre raster-style JSON a raster source needs — not the vector style catalogue
 * `Basemap` eventually becomes (that conversion is later migration work, gated on this step). The
 * overlay data is five synthetic sighting points and three synthetic area markers around a fixed
 * Portland, OR coordinate — not a real search result, since this activity has no repository wiring.
 *
 * Once confirmed on hardware, this class is deleted — it is scaffolding for verification, not a
 * feature.
 */
class MapLibreBasemapPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run before MapLibre.getInstance() below — see ensureMapLibreStorageOutsideCache's
        // doc comment for why this app has two such call sites and why whichever runs first has to
        // win the race.
        ensureMapLibreStorageOutsideCache(this)
        // Required once, before any MapView is created — the same "initialize the vendor SDK
        // before touching it" step every Mapbox-lineage map library needs.
        MapLibre.getInstance(this)

        setContent {
            ForagerTheme {
                var selectedBasemap by remember { mutableStateOf(PreviewBasemap.OSM_STANDARD) }

                Box {
                    MapLibreOverlayPreview(basemap = selectedBasemap, modifier = Modifier)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "MapLibre step-1/2 smoke test — synthetic overlay data. Not reachable from the real app.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    ) {
                        PreviewBasemap.entries.forEach { basemap ->
                            Button(onClick = { selectedBasemap = basemap }) {
                                Text(basemap.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The four basemaps [Basemap] already carries, reduced to just what a raster style needs to test
 * overlay legibility against them: label and tile URL. Not [Basemap] itself — that enum is
 * osmdroid-shaped (`maxZoom`/`coverage`/`attribution` for production UI this preview doesn't have),
 * and duplicating only the two fields this smoke test needs keeps it obviously throwaway rather
 * than looking like the start of a real replacement for that type.
 */
private enum class PreviewBasemap(val label: String, val tileUrlTemplate: String, val attribution: String) {
    OSM_STANDARD("OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors"),
    OPEN_TOPO_MAP("OpenTopoMap", "https://a.tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)"),
    USGS_TOPO("USGS Topo", "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}", "USGS The National Map"),
    USGS_IMAGERY("USGS Imagery", "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/{z}/{y}/{x}", "USGS The National Map, orthoimagery"),
}

private fun rasterStyleJson(basemap: PreviewBasemap): String = """
    {
      "version": 8,
      "glyphs": "$GLYPHS_URL_TEMPLATE",
      "sources": {
        "basemap": {
          "type": "raster",
          "tiles": ["${basemap.tileUrlTemplate}"],
          "tileSize": 256,
          "attribution": "${basemap.attribution}"
        }
      },
      "layers": [
        {"id": "basemap", "type": "raster", "source": "basemap"}
      ]
    }
""".trimIndent()

/**
 * MapLibre's own public glyph PBF endpoint (demotiles.maplibre.org, no key). Without a `glyphs`
 * URL in the style, a `SymbolLayer`'s `text-field` silently renders nothing: this was missing from
 * the first cut of this style JSON, and the numbered area-marker labels came up as plain circles
 * with no visible digit on real hardware — caught from the project owner's screenshot, not
 * predicted in advance.
 *
 * [AREA_MARKER_FONT_STACK] is `"Open Sans Semibold"`, not a guess: it's the exact font
 * `raw.githubusercontent.com/maplibre/demotiles/gh-pages/style.json` — the real, currently-serving
 * style this glyph endpoint backs — uses in its own `countries-label`/`geolines-label` layers'
 * `text-font`. A first attempt at this fix used `"Noto Sans Bold"` (a font directory that does
 * exist in the demotiles repo, per its GitHub file listing) but the labels still didn't render —
 * this environment's egress proxy blocks demotiles.maplibre.org directly, so that couldn't be
 * confirmed by fetching the glyph PBF itself; checking the reference style.json instead, rather
 * than re-guessing a second font name, is what actually settled it.
 */
private const val GLYPHS_URL_TEMPLATE = "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf"
private val AREA_MARKER_FONT_STACK = arrayOf("Open Sans Semibold")

@Composable
private fun MapLibreOverlayPreview(basemap: PreviewBasemap, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Mirrors SightingsMap's own osmdroid MapView lifecycle wiring — MapLibre's MapView needs the
    // same onCreate/onResume/onPause/onDestroy forwarding, being the same Mapbox-lineage API shape.
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(basemap) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder()
                .target(org.maplibre.android.geometry.LatLng(PREVIEW_CENTER_LAT, PREVIEW_CENTER_LNG))
                .zoom(13.0)
                .build()
            // setStyle replaces the whole style object, so every layer added below has to be
            // re-added on every basemap switch — there is nothing to diff against the old style.
            map.setStyle(Style.Builder().fromJson(rasterStyleJson(basemap))) { style ->
                addOverlayLayers(style)
            }
        }
        onDispose { }
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
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * Adds the sighting-dot, area-marker, and dashed-connector layers to [style], using the *same*
 * colours, dash pattern, and stroke width [SightingsMap] draws with (see that file's
 * `CONNECTOR_COLOR`/`SIGHTING_DOT_COLOR`/`AREA_MARKER_BACKGROUND_COLOR`/
 * `CONNECTOR_DASH_PATTERN_PX` constants) — the whole point of this step is testing whether *those*
 * colours read against a real basemap on real hardware, not some new palette invented for the
 * test.
 */
private fun addOverlayLayers(style: Style) {
    style.addSource(GeoJsonSource(SIGHTING_SOURCE_ID, sightingDotsGeoJson()))
    style.addLayer(
        CircleLayer(SIGHTING_LAYER_ID, SIGHTING_SOURCE_ID).withProperties(
            PropertyFactory.circleColor("#3B2E24"), // bark, matching SightingsMap.SIGHTING_DOT_COLOR
            PropertyFactory.circleOpacity(0.7f), // 0xB3 alpha ~= 70%, same reasoning as SightingsMap
            PropertyFactory.circleRadius(9f),
        ),
    )

    style.addSource(GeoJsonSource(CONNECTOR_SOURCE_ID, connectorLineGeoJson()))
    style.addLayer(
        LineLayer(CONNECTOR_LAYER_ID, CONNECTOR_SOURCE_ID).withProperties(
            PropertyFactory.lineColor("#C97B3D"), // mushroom orange, matching SightingsMap.CONNECTOR_COLOR
            PropertyFactory.lineWidth(6f),
            // MapLibre's line-dasharray is in units of line width, not raw pixels — this ratio
            // (18:14) is the same ratio as SightingsMap.CONNECTOR_DASH_PATTERN_PX, not the same
            // absolute pixel values, since the two rendering engines express dashing differently.
            PropertyFactory.lineDasharray(arrayOf(1.5f, 1.2f)),
        ),
    )

    style.addSource(GeoJsonSource(AREA_MARKER_SOURCE_ID, areaMarkerGeoJson()))
    style.addLayer(
        CircleLayer(AREA_MARKER_CIRCLE_LAYER_ID, AREA_MARKER_SOURCE_ID).withProperties(
            PropertyFactory.circleColor("#2E5339"), // forest green, matching SightingsMap.AREA_MARKER_BACKGROUND_COLOR
            PropertyFactory.circleRadius(16f),
        ),
    )
    style.addLayer(
        SymbolLayer(AREA_MARKER_LABEL_LAYER_ID, AREA_MARKER_SOURCE_ID).withProperties(
            PropertyFactory.textField("{label}"),
            PropertyFactory.textFont(AREA_MARKER_FONT_STACK), // must match a font in GLYPHS_URL_TEMPLATE's set, or text-field renders nothing
            PropertyFactory.textColor("#FFFFFF"), // matching SightingsMap.AREA_MARKER_FOREGROUND_COLOR
            PropertyFactory.textSize(14f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ),
    )
}

private const val PREVIEW_CENTER_LAT = 45.5
private const val PREVIEW_CENTER_LNG = -122.6

private const val SIGHTING_SOURCE_ID = "preview-sightings"
private const val SIGHTING_LAYER_ID = "preview-sightings-layer"
private const val CONNECTOR_SOURCE_ID = "preview-connector"
private const val CONNECTOR_LAYER_ID = "preview-connector-layer"
private const val AREA_MARKER_SOURCE_ID = "preview-area-markers"
private const val AREA_MARKER_CIRCLE_LAYER_ID = "preview-area-markers-circle-layer"
private const val AREA_MARKER_LABEL_LAYER_ID = "preview-area-markers-label-layer"

private fun sightingDotsGeoJson(): String {
    val offsets = listOf(
        0.004 to 0.006,
        -0.003 to 0.008,
        0.006 to -0.002,
        -0.005 to -0.004,
        0.001 to 0.001,
    )
    val features = offsets.joinToString(",") { (dLat, dLng) ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${PREVIEW_CENTER_LNG + dLng},${PREVIEW_CENTER_LAT + dLat}]},"properties":{}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun areaMarkerGeoJson(): String {
    val markers = listOf(
        Triple(1, 0.005, 0.005),
        Triple(2, -0.004, 0.007),
        Triple(3, 0.002, -0.005),
    )
    val features = markers.joinToString(",") { (number, dLat, dLng) ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${PREVIEW_CENTER_LNG + dLng},${PREVIEW_CENTER_LAT + dLat}]},"properties":{"label":"$number"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun connectorLineGeoJson(): String {
    val points = listOf(
        0.005 to 0.005,
        -0.004 to 0.007,
        0.002 to -0.005,
    )
    val coordinates = points.joinToString(",") { (dLat, dLng) -> "[${PREVIEW_CENTER_LNG + dLng},${PREVIEW_CENTER_LAT + dLat}]" }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]},"properties":{}}"""
}
