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
 * shrink to ~4.4km/z10-14) means stop guessing a fourth region size and get more data instead — a
 * first pass at that (persisting only the last [OfflineRegionStatus] to disk) showed the crash
 * happening at `completed=0/1 resources`, i.e. essentially immediately, which rules out "OOM from
 * too many tiles" as the cause: there is no smaller region that explains dying before resource 1
 * of 1. [appendStepLog] replaces that single overwritten line with an ordered, appended trace of
 * every lifecycle checkpoint — region created, observer set, `setDownloadState` called/returned,
 * every `onStatusChanged`/`onError` — to [STEP_LOG_FILE_NAME], so the next hardware repro shows
 * *which specific step* precedes the native crash rather than only the last resource count.
 *
 * ## Isolating the glyph hypothesis
 *
 * The step trace from that build was conclusive about *timing*, not cause: every checkpoint
 * through `setDownloadState(ACTIVE) returned` is logged, then exactly one `onStatusChanged` fires
 * at `completed=0/1`, then nothing — the crash lands in native code between the first and second
 * status callback, before any real tile volume has moved. [DEMO_STYLE_URL] is a vector style with
 * two `text-field` layers (`geolines-label`, `countries-label`, both `Open Sans Semibold`), which
 * is exactly the resource-list work that would be happening at that point: MapLibre's offline code
 * resolves the style's glyph ranges as part of building the real resource pyramid, a step entirely
 * independent of the tile bounds/zoom this file already shrank once. [RASTER_STYLE_ASSET_URL] has
 * no glyphs, no vector source, nothing to resolve there — same bounds, same zoom range, same
 * instrumentation. If it downloads cleanly where the vector style dies at the identical checkpoint,
 * that is real evidence glyph-range resolution is the native crash's cause, not region size — the
 * next real fix would then target *what this app asks MapLibre to render offline* (drop the label
 * layers, or accept raster-only for the real feature) rather than a fifth region-size guess.
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
        val previousStepLog = readAndClearStepLog(this)

        setContent {
            ForagerTheme {
                val context = LocalContext.current
                var selectedBasemap by remember { mutableStateOf(PreviewBasemap.OSM_STANDARD) }
                var offlineStatus by remember { mutableStateOf<OfflineDownloadStatus>(OfflineDownloadStatus.Idle) }
                var savedRegionsStatus by remember { mutableStateOf("Not checked yet") }

                Box {
                    MapLibreOverlayPreview(basemap = selectedBasemap, modifier = Modifier)
                    if (previousCrashLog != null || previousStepLog != null) {
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
                            if (previousStepLog != null) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                    Text(
                                        "Steps before this launch (last line ran right before a crash the logger above didn't catch):\n\n$previousStepLog",
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
                        Button(
                            onClick = {
                                startOfflineDownload(context, DEMO_STYLE_URL, VECTOR_OFFLINE_REGION_NAME) { status ->
                                    offlineStatus = status
                                }
                            },
                        ) {
                            Text("Download offline region (demo style — vector + glyphs)")
                        }
                        Button(
                            onClick = {
                                startOfflineDownload(context, RASTER_STYLE_ASSET_URL, RASTER_OFFLINE_REGION_NAME) { status ->
                                    offlineStatus = status
                                }
                            },
                        ) {
                            Text("Download offline region (raster style — no glyphs)")
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
 * Creates and starts an offline region download against [styleUrl] — see this file's class doc
 * comment ("What step 3's offline download here does and does not prove", and "Isolating the
 * glyph hypothesis") for why there are two callers of this: [DEMO_STYLE_URL] (vector + glyphs) and
 * [RASTER_STYLE_ASSET_URL] (raster only, no glyphs). [OfflineManager] is a process-wide singleton
 * (not tied to any particular [MapView]), so this needs only a [Context] — the download runs and
 * is persisted independent of whether the preview map itself is still on screen.
 */
private fun startOfflineDownload(context: Context, styleUrl: String, regionName: String, onStatus: (OfflineDownloadStatus) -> Unit) {
    onStatus(OfflineDownloadStatus.Downloading(completedResources = 0, requiredResources = 0))
    // Fresh trace per download attempt — an in-session retry shouldn't blend into an earlier one.
    File(context.filesDir, STEP_LOG_FILE_NAME).delete()
    appendStepLog(context, "download button tapped ($regionName), building region definition")

    val bounds = LatLngBounds.Builder()
        .include(LatLng(PREVIEW_CENTER_LAT + OFFLINE_REGION_HALF_SPAN_DEGREES, PREVIEW_CENTER_LNG + OFFLINE_REGION_HALF_SPAN_DEGREES))
        .include(LatLng(PREVIEW_CENTER_LAT - OFFLINE_REGION_HALF_SPAN_DEGREES, PREVIEW_CENTER_LNG - OFFLINE_REGION_HALF_SPAN_DEGREES))
        .build()
    val definition = OfflineTilePyramidRegionDefinition(
        styleUrl,
        bounds,
        OFFLINE_REGION_MIN_ZOOM,
        OFFLINE_REGION_MAX_ZOOM,
        context.resources.displayMetrics.density,
    )

    appendStepLog(context, "calling OfflineManager.createOfflineRegion")
    OfflineManager.getInstance(context).createOfflineRegion(
        definition,
        regionName.toByteArray(),
        object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                appendStepLog(context, "region created, setting observer")
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        appendStepLog(
                            context,
                            "onStatusChanged: completed=${status.completedResourceCount}/${status.requiredResourceCount} resources, " +
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
                        appendStepLog(context, "onError: reason=${error.reason} message=${error.message}")
                        onStatus(OfflineDownloadStatus.Failed(error.message))
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        appendStepLog(context, "mapboxTileCountLimitExceeded: limit=$limit")
                        onStatus(OfflineDownloadStatus.Failed("tile count limit exceeded ($limit)"))
                    }
                })
                appendStepLog(context, "observer set, calling setDownloadState(ACTIVE)")
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                appendStepLog(context, "setDownloadState(ACTIVE) returned")
            }

            override fun onError(error: String) {
                appendStepLog(context, "createOfflineRegion onError: $error")
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

/**
 * A minimal raster-only style bundled as an app asset ([offline_test_raster_style.json] under
 * `app/src/main/assets/`) — no vector source, no `glyphs` URL, no `sprite`, no `text-field` layer.
 * `asset://` resolves through MapLibre's `AssetFileSource`, the same generic scheme dispatch used
 * for any other resource fetch, so this needs no external hosting the way [DEMO_STYLE_URL] does.
 *
 * The tiles it references are USGS Topo's — the one source this codebase already has standing
 * permission to bulk-download (see `docs/plans/maplibre-migration.md` §1: OSM/OpenTopoMap
 * prohibit bulk prefetch, USGS does not), the same tile URL template [PreviewBasemap.USGS_TOPO]
 * uses for the live-render smoke test above.
 *
 * See this file's class doc comment, "Isolating the glyph hypothesis," for why this exists: the
 * third hardware repro's step trace showed the native crash landing right after the first
 * `onStatusChanged` callback for the vector [DEMO_STYLE_URL], at `completed=0/1` — before any real
 * tile volume, but exactly where MapLibre's offline code would start resolving the demo style's
 * `geolines-label`/`countries-label` glyph ranges (`Open Sans Semibold`). A raster style has no
 * glyphs to resolve at all. If this one survives past that same point, that is real evidence for
 * glyph-range resolution as the crash's native cause, independent of another region-size guess.
 */
private const val RASTER_STYLE_ASSET_URL = "asset://offline_test_raster_style.json"

private const val VECTOR_OFFLINE_REGION_NAME = "forager-maplibre-step3-smoke-test-vector"
private const val RASTER_OFFLINE_REGION_NAME = "forager-maplibre-step3-smoke-test-raster"

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

private const val STEP_LOG_FILE_NAME = "maplibre_preview_steps.txt"

/**
 * Appends one timestamped line to [STEP_LOG_FILE_NAME] at each offline-download lifecycle
 * checkpoint (button tapped, region created, observer set, `setDownloadState` called and
 * returned, every `onStatusChanged`/`onError` callback). This exists because [installCrashLogger]
 * can only catch a Kotlin/Java exception — three hardware repros in a row show no such exception
 * (see this activity's "Crash capture" doc comment) and the first attempt at reading progress off
 * a single overwritten status line showed the crash happening at `completed=0/1`, i.e. essentially
 * immediately, which ruled out the "OOM from a large tile count" theory the region-shrink fix was
 * built on. A single overwritten line can't say *which* step preceded death, only the last status
 * value — appending every checkpoint turns that into an ordered trace: whichever line is last in
 * the file on the next launch is the last thing that ran before the process died, without needing
 * adb or a debugger attached.
 */
private fun appendStepLog(context: Context, step: String) {
    runCatching {
        File(context.filesDir, STEP_LOG_FILE_NAME).appendText("${System.currentTimeMillis()} $step\n")
    }
}

/** Reads back and deletes [STEP_LOG_FILE_NAME], so a stale trace from an old run is shown exactly once. */
private fun readAndClearStepLog(context: Context): String? {
    val file = File(context.filesDir, STEP_LOG_FILE_NAME)
    if (!file.exists()) return null
    return runCatching { file.readText() }.getOrNull()?.also { file.delete() }
}
