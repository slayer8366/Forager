package com.zynergylabs.labelcheck

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import kotlin.math.cos

/**
 * Dispatch 2026-09-28-490: does MapLibre Android 13.5.0 (Forager's pin) draw the labelled Forager style
 * online, and download it as an offline region, without the native crash PR #23 found? Driven by adb:
 *
 *   view:     am start -n com.zynergylabs.labelcheck/.LabelCheckActivity --es mode view --es style URL --ef lat .. --ef lon .. --ef zoom ..
 *   download: ... --es mode download --es style URL --ef lat .. --ef lon .. --ef halfKm 1 --es name NAME
 *   list:     ... --es mode list
 *
 * Every step is logged under the tag LabelCheck and, for a download, also appended and flushed to
 * files/download-NAME.log line by line, so the last step before a native crash survives it.
 * Positions come in from the laptop's script and are never logged.
 */
class LabelCheckActivity : Activity() {
    private var mapView: MapView? = null
    private val started = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        when (intent.getStringExtra("mode") ?: "view") {
            "view" -> view(savedInstanceState)
            "download" -> download()
            "list" -> list()
            else -> log("unknown mode")
        }
    }

    private fun view(savedInstanceState: Bundle?) {
        val style = intent.getStringExtra("style") ?: return log("view: no style")
        val map = MapView(this).also { mapView = it }
        setContentView(map)
        map.onCreate(savedInstanceState)
        map.addOnDidFailLoadingMapListener { log("view: failed loading map: $it") }
        map.addOnDidFinishLoadingStyleListener { log("view: style loaded") }
        map.addOnDidFinishRenderingMapListener { fully -> log("view: rendering finished, fully=$fully") }
        map.addOnDidBecomeIdleListener { log("view: idle") }
        map.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lon", 0f).toDouble()))
                .zoom(intent.getFloatExtra("zoom", 12f).toDouble())
                .build()
            log("view: loading style ${style.substringAfterLast('/')}")
            m.setStyle(style)
        }
    }

    private fun download() {
        val style = intent.getStringExtra("style") ?: return log("download: no style")
        val name = intent.getStringExtra("name") ?: "region"
        val file = File(filesDir, "download-$name.log")
        val step = { line: String ->
            log("download $name: $line")
            file.appendText("${SystemClock.elapsedRealtime() - started} ms $line\n")
        }
        val lat = intent.getFloatExtra("lat", 0f).toDouble()
        val lon = intent.getFloatExtra("lon", 0f).toDouble()
        val half = intent.getFloatExtra("halfKm", 1f).toDouble()
        val dLat = half / 111.32
        val dLon = half / (111.32 * cos(Math.toRadians(lat)))
        val bounds = LatLngBounds.Builder().include(LatLng(lat - dLat, lon - dLon)).include(LatLng(lat + dLat, lon + dLon)).build()
        // As Forager does (MapLibreOfflineMapRepository.download): a tile pyramid, z10 to z15, the screen's density.
        val definition = OfflineTilePyramidRegionDefinition(style, bounds, 10.0, 15.0, resources.displayMetrics.density)
        step("creating: style ${style.substringAfterLast('/')}, z10-15, ${2 * half} km square, density ${resources.displayMetrics.density}")
        OfflineManager.getInstance(this).createOfflineRegion(definition, name.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                step("created, id ${offlineRegion.id}")
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        step("status completed=${status.completedResourceCount}/${status.requiredResourceCount} bytes=${status.completedResourceSize} tiles=${status.completedTileCount} complete=${status.isComplete}")
                        if (status.isComplete) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            step("DOWNLOAD COMPLETE")
                        }
                    }

                    override fun onError(error: OfflineRegionError) = step("error: ${error.reason} ${error.message}")

                    override fun mapboxTileCountLimitExceeded(limit: Long) = step("tile count limit exceeded: $limit")
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                step("download started")
            }

            override fun onError(error: String) = step("create failed: $error")
        })
    }

    private fun list() {
        OfflineManager.getInstance(this).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                log("list: ${offlineRegions?.size ?: 0} region(s): ${offlineRegions?.joinToString { "${it.id}=${String(it.metadata)}" }}")
            }

            override fun onError(error: String) = log("list failed: $error")
        })
    }

    private fun log(line: String) {
        Log.i(TAG, "${SystemClock.elapsedRealtime() - started} ms $line")
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroy() { mapView?.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }

    private companion object {
        const val TAG = "LabelCheck"
    }
}
