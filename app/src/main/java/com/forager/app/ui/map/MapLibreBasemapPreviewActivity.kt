package com.forager.app.ui.map

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forager.app.ui.theme.ForagerTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Steps 1-3 of the osmdroid -> MapLibre migration (`docs/plans/maplibre-migration.md` §6, Track 2):
 * a basemap render, sighting dots / numbered area markers / a dashed order connector as style
 * layers, and now an offline-region download using MapLibre's own `OfflineManager` — the mechanism
 * meant to replace `PersistentTileWriter`'s hand-rolled writer. Not wired into
 * [com.forager.app.MainActivity]/[MapSlot]/[AvailabilityScreen] at all — `SightingsMap`, `Basemap`,
 * `OsmdroidOfflineMapRepository`, and `PersistentTileWriter` are completely untouched by this
 * class; retiring `PersistentTileWriter` for real is step 4, only once this step is seen working.
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
 * ## What step 3's offline download here does and does not prove
 *
 * `OfflineTilePyramidRegionDefinition` needs a real, fetchable style **URL** — not the inline-JSON
 * raster styles [rasterStyleJson] builds for the basemap picker above, which are never hosted
 * anywhere. So the offline download below targets [DEMO_STYLE_URL] (the same one step 1 already
 * proved renders), not USGS/OSM. This proves the mechanism — create a region, watch it download,
 * confirm the resource count and byte count are real and nonzero, list it back after the app
 * restarts — which is genuinely what `OfflineManager` replacing `PersistentTileWriter` depends on
 * working. It does **not** prove USGS/OSM tiles specifically download and replay offline, and it
 * is not the pre-built-regional-PMTiles-extract pipeline `maplibre-migration.md` §3's Option C
 * calls for — that needs a real processing pipeline and real hosting this session has no way to
 * stand up. Once `Basemap` itself becomes a hosted style URL (later migration work), the same
 * `OfflineManager` call proven here applies to it directly.
 *
 * To actually verify offline replay: download a region, then turn on airplane mode and relaunch
 * this activity — the downloaded area of the demo-style map should still render with no network.
 *
 * ## Crash capture
 *
 * A download attempt has crashed the whole app on real hardware twice, with no `OfflineRegionObserver.onError`
 * call first — a sign of a native-level failure, not a Kotlin exception, and this environment has
 * no device to attach a debugger or `adb logcat` to. adb-over-Termux and the OS's own "Take bug
 * report" tool both proved impractical to get working for the project owner's actual setup. So
 * this installs its own [Thread.UncaughtExceptionHandler] before anything else runs, writing any
 * *Kotlin/Java*-level crash to [CRASH_LOG_FILE_NAME] in app-private storage and showing it on
 * screen the next time this activity launches — no adb, no OS tooling, just this app and a
 * relaunch. It cannot catch a genuine native (JNI) crash, which bypasses the JVM's exception
 * mechanism entirely — if the crash log stays empty across another repro, that itself is real
 * information: it rules out a Kotlin-level cause and points at MapLibre's native code specifically.
 *
 * A third hardware repro confirmed exactly that: crash log empty, still a whole-app crash. Per
 * CLAUDE.md, two failed fix attempts on one symptom (the original 110km/z0-8 region, then the
 * shrink to ~4.4km/z10-14) means stop guessing a fourth region size and get more data instead —
 * [writeProgressLog] does that by persisting the last-observed [OfflineRegionStatus] to
 * [PROGRESS_LOG_FILE_NAME] on every `onStatusChanged` call, so a native crash that the exception
 * handler above cannot see still leaves behind exactly how many resources/bytes had completed at
 * the moment the process died — turning "did it crash" into "how far did it get," which is what
 * the next region-size or concurrency decision should be based on rather than another guess.
 *
 * Once confirmed on hardware, this class is deleted — it is scaffolding for verification, not a
 * feature.
 */
class MapLibreBasemapPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger(this)
        // Required once, before any MapView is created — the same "initialize the vendor SDK
        // before touching it" step every Mapbox-lineage map library needs.
        MapLibre.getInstance(this)

        val previousCrashLog = readAndClearCrashLog(this)
        val previousProgressLog = readAndClearProgressLog(this)

        setContent {
            ForagerTheme {
                val context = LocalContext.current
                var selectedBasemap by remember { mutableStateOf(PreviewBasemap.OSM_STANDARD) }
                var offlineStatus by remember { mutableStateOf<OfflineDownloadStatus>(OfflineDownloadStatus.Idle) }
                var savedRegionsStatus by remember { mutableStateOf("Not checked yet") }

                Box {
                    MapLibreOverlayPreview(basemap = selectedBasemap, modifier = Modifier)
                    if (previousCrashLog != null || previousProgressLog != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            if (previousCrashLog != null) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                    Text(
                                        "Crash caught on the previous run:\n\n$previousCrashLog",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (previousProgressLog != null) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                    Text(
                                        "Last known download progress before this launch (may predate a crash the logger above didn't catch):\n\n$previousProgressLog",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "MapLibre step-1/2/3 smoke test — synthetic overlay data, demo-style offline download. Not reachable from the real app.",
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
                        Button(onClick = { startOfflineDownload(context) { status -> offlineStatus = status } }) {
                            Text("Download offline region (demo style)")
                        }
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                offlineStatus.describe(),
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = { listSavedRegions(context) { status -> savedRegionsStatus = status } }) {
                            Text("List saved regions (survives restart?)")
                        }
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                savedRegionsStatus,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface OfflineDownloadStatus {
    data object Idle : OfflineDownloadStatus
    data class Downloading(val completedResources: Long, val requiredResources: Long) : OfflineDownloadStatus
    data class Complete(val resourceCount: Long, val byteCount: Long) : OfflineDownloadStatus
    data class Failed(val reason: String) : OfflineDownloadStatus
}

private fun OfflineDownloadStatus.describe(): String = when (this) {
    OfflineDownloadStatus.Idle -> "Offline region: not downloaded"
    is OfflineDownloadStatus.Downloading -> "Offline region: downloading, $completedResources/$requiredResources resources"
    is OfflineDownloadStatus.Complete -> "Offline region: complete, $resourceCount resources, $byteCount bytes"
    is OfflineDownloadStatus.Failed -> "Offline region: failed — $reason"
}

/**
 * Creates and starts an offline region download against [DEMO_STYLE_URL] — see this file's class
 * doc comment ("What step 3's offline download here does and does not prove") for why the demo
 * style, not USGS/OSM. [OfflineManager] is a process-wide singleton (not tied to any particular
 * [MapView]), so this needs only a [Context] — the download runs and is persisted independent of
 * whether the preview map itself is still on screen.
 */
private fun startOfflineDownload(context: Context, onStatus: (OfflineDownloadStatus) -> Unit) {
    onStatus(OfflineDownloadStatus.Downloading(completedResources = 0, requiredResources = 0))

    val bounds = LatLngBounds.Builder()
        .include(LatLng(PREVIEW_CENTER_LAT + OFFLINE_REGION_HALF_SPAN_DEGREES, PREVIEW_CENTER_LNG + OFFLINE_REGION_HALF_SPAN_DEGREES))
        .include(LatLng(PREVIEW_CENTER_LAT - OFFLINE_REGION_HALF_SPAN_DEGREES, PREVIEW_CENTER_LNG - OFFLINE_REGION_HALF_SPAN_DEGREES))
        .build()
    val definition = OfflineTilePyramidRegionDefinition(
        DEMO_STYLE_URL,
        bounds,
        OFFLINE_REGION_MIN_ZOOM,
        OFFLINE_REGION_MAX_ZOOM,
        context.resources.displayMetrics.density,
    )

    OfflineManager.getInstance(context).createOfflineRegion(
        definition,
        OFFLINE_REGION_NAME.toByteArray(),
        object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        writeProgressLog(
                            context,
                            "completed=${status.completedResourceCount}/${status.requiredResourceCount} resources, " +
                                "bytes=${status.completedResourceSize}, isComplete=${status.isComplete}",
                        )
                        onStatus(
                            if (status.isComplete) {
                                OfflineDownloadStatus.Complete(status.completedResourceCount, status.completedResourceSize)
                            } else {
                                OfflineDownloadStatus.Downloading(status.completedResourceCount, status.requiredResourceCount)
                            },
                        )
                    }

                    override fun onError(error: OfflineRegionError) {
                        onStatus(OfflineDownloadStatus.Failed(error.message))
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        onStatus(OfflineDownloadStatus.Failed("tile count limit exceeded ($limit)"))
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                onStatus(OfflineDownloadStatus.Failed(error))
            }
        },
    )
}

/**
 * Lists every offline region [OfflineManager] currently has persisted — the actual proof this
 * step needs, since a region "downloading" on screen proves nothing about whether it survives an
 * app restart (or, for the real feature, a killed foreground process). Run this after relaunching
 * the activity (or the whole app) following a download to confirm persistence, not just progress.
 */
private fun listSavedRegions(context: Context, onStatus: (String) -> Unit) {
    OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
        override fun onList(offlineRegions: Array<OfflineRegion>?) {
            onStatus("${offlineRegions?.size ?: 0} saved region(s)")
        }

        override fun onError(error: String) {
            onStatus("Failed to list regions: $error")
        }
    })
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
                .target(LatLng(PREVIEW_CENTER_LAT, PREVIEW_CENTER_LNG))
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

/** MapLibre's own public demo style — the same one step 1 confirmed renders. See "What step 3's offline download here does and does not prove" above for why the offline test targets this rather than USGS/OSM. */
private const val DEMO_STYLE_URL = "https://demotiles.maplibre.org/style.json"
private const val OFFLINE_REGION_NAME = "forager-maplibre-step3-smoke-test"

// Deliberately small, and NOT starting from minZoom 0 — a first attempt at ~110km across, z0-8,
// crashed the whole app on real hardware rather than failing gracefully through
// OfflineRegionObserver.onError, which points at a native-level failure, not a Kotlin exception
// this code could have caught. maplibre/maplibre-native#2515 documents exactly this class of bug:
// an out-of-memory crash downloading large bounds via OfflineManager, worse for vector styles
// (like this demo style) where even a modest tile count can carry real geometry weight. This is
// also a closer match to what production offline downloads actually look like — a small local
// area at the zoom range someone would actually use in the field, not the whole tile pyramid down
// from a global overview.
private const val OFFLINE_REGION_HALF_SPAN_DEGREES = 0.02 // ~4.4km across
private const val OFFLINE_REGION_MIN_ZOOM = 10.0
private const val OFFLINE_REGION_MAX_ZOOM = 14.0

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

private const val CRASH_LOG_FILE_NAME = "maplibre_preview_crash.txt"

/**
 * Wraps the process-wide default [Thread.UncaughtExceptionHandler], writing any *Kotlin/Java*
 * crash to [CRASH_LOG_FILE_NAME] before handing off to whatever handler was previously installed
 * (Android's own, which shows the "app has a bug" dialog and terminates the process — this does
 * not suppress that, it only gets a copy of the stack trace onto disk first). See this activity's
 * "Crash capture" doc comment for why this exists instead of adb/OS tooling. Installed fresh every
 * `onCreate`, so it always wraps whatever the current default handler is rather than risking
 * double-wrapping across relaunches within the same process.
 */
private fun installCrashLogger(context: Context) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            File(context.filesDir, CRASH_LOG_FILE_NAME).writeText(throwable.stackTraceToString())
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}

/** Reads back and deletes [CRASH_LOG_FILE_NAME], so a caught crash is shown exactly once, on the next launch after it happened. */
private fun readAndClearCrashLog(context: Context): String? {
    val file = File(context.filesDir, CRASH_LOG_FILE_NAME)
    if (!file.exists()) return null
    return runCatching { file.readText() }.getOrNull()?.also { file.delete() }
}

private const val PROGRESS_LOG_FILE_NAME = "maplibre_preview_progress.txt"

/**
 * Overwrites [PROGRESS_LOG_FILE_NAME] with the latest download status on every
 * [OfflineRegion.OfflineRegionObserver.onStatusChanged] call. This exists because
 * [installCrashLogger] can only catch a Kotlin/Java exception — the two hardware crashes so far
 * show no such exception (see this activity's "Crash capture" doc comment), which points at a
 * native (JNI) failure the JVM handler never sees. A native crash still leaves this file on disk
 * with whatever the last-observed progress was, which turns "did it crash" into "how far did it
 * get before it died" — the resource count and byte count at the moment of death, without needing
 * adb or a debugger attached.
 */
private fun writeProgressLog(context: Context, status: String) {
    runCatching { File(context.filesDir, PROGRESS_LOG_FILE_NAME).writeText(status) }
}

/** Reads back and deletes [PROGRESS_LOG_FILE_NAME], so stale progress from an old run is shown exactly once. */
private fun readAndClearProgressLog(context: Context): String? {
    val file = File(context.filesDir, PROGRESS_LOG_FILE_NAME)
    if (!file.exists()) return null
    return runCatching { file.readText() }.getOrNull()?.also { file.delete() }
}
