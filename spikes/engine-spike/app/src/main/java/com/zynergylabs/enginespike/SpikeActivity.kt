package com.zynergylabs.enginespike

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import com.valhalla.valhalla.Valhalla
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * One measured run (dispatch 2026-09-28-446): one engine, one route, in a fresh process, so the
 * process's peak resident memory (VmHWM) belongs to this run alone. Started by the laptop's script:
 *
 *   adb shell am force-stop com.zynergylabs.enginespike
 *   adb shell am start -W -n com.zynergylabs.enginespike/.SpikeActivity --es engine valhalla --es route r1 --es run 1
 *
 * Inputs on the phone, in the app's internal files directory (never in the repo). Files adb pushes into
 * the external `Android/data` directory are owned by the shell and the app cannot open them (seen on
 * the S22: EACCES), so they are staged in /data/local/tmp and copied in with `run-as`:
 * `routes.json` ({"r1": [[lat, lon], [lat, lon], ...], ...}: positions stay off the repository),
 * `valhalla/config.json` with its tiles, and `brouter/segments4/` and `brouter/profiles2/`.
 *
 * One line per run under the tag EngineSpike, and the route's geometry in `out/`, both read back
 * on the laptop.
 */
class SpikeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = intent.getStringExtra("engine") ?: "valhalla"
        val routeId = intent.getStringExtra("route") ?: "r1"
        val run = intent.getStringExtra("run") ?: "1"
        val profile = intent.getStringExtra("profile") ?: "hiking-mountain"
        thread(name = "spike-run") {
            val result = JSONObject().put("engine", engine).put("route", routeId).put("run", run)
            try {
                val dir = filesDir
                val points = JSONObject(File(dir, "routes.json").readText()).getJSONArray(routeId)
                val stops = (0 until points.length()).map { points.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
                val out = File(dir, "out").apply { mkdirs() }
                when (engine) {
                    "valhalla" -> runValhalla(dir, stops, result, File(out, "valhalla-$routeId-$run.json"))
                    "brouter" -> runBrouter(dir, stops, profile, result, File(out, "brouter-$profile-$routeId-$run.json"))
                    else -> result.put("error", "unknown engine $engine")
                }
            } catch (e: Throwable) {
                result.put("error", "${e::class.java.simpleName}: ${e.message}")
            }
            result.put("vmhwm_kb", procStatusKb("VmHWM"))
                .put("vmrss_kb", procStatusKb("VmRSS"))
                .put("native_heap_kb", Debug.getNativeHeapAllocatedSize() / 1024)
                .put("java_heap_kb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024)
            Log.i(TAG, result.toString())
            runOnUiThread { finish() }
        }
    }

    private fun runValhalla(dir: File, stops: List<Pair<Double, Double>>, result: JSONObject, outFile: File) {
        val t0 = SystemClock.elapsedRealtime()
        Valhalla(File(dir, "valhalla/config.json").absolutePath).use { valhalla ->
            val t1 = SystemClock.elapsedRealtime()
            val request = JSONObject()
                .put("locations", JSONArray(stops.map { (lat, lon) -> JSONObject().put("lat", lat).put("lon", lon) }))
                .put("costing", "pedestrian")
                // sac_scale up to 6 allowed, so the route shows what the data permits; item 5 reads it.
                .put("costing_options", JSONObject().put("pedestrian", JSONObject().put("max_hiking_difficulty", 6)))
                .put("directions_options", JSONObject().put("units", "kilometers"))
            val response = valhalla.routeRaw(request.toString())
            val t2 = SystemClock.elapsedRealtime()
            outFile.writeText(response)
            val trip = JSONObject(response).optJSONObject("trip")
            result.put("ms_engine", t1 - t0).put("ms_route", t2 - t1).put("ms_total", t2 - t0)
            if (trip == null) {
                result.put("error", response.take(300))
            } else {
                result.put("length_m", (trip.getJSONObject("summary").getDouble("length") * 1000).toInt())
            }
        }
    }

    private fun runBrouter(dir: File, stops: List<Pair<Double, Double>>, profile: String, result: JSONObject, outFile: File) {
        val t0 = SystemClock.elapsedRealtime()
        val rc = RoutingContext().apply { localFunction = File(dir, "brouter/profiles2/$profile.brf").absolutePath }
        val waypoints = stops.mapIndexed { i, (lat, lon) ->
            OsmNodeNamed().apply {
                name = if (i == 0) "from" else if (i == stops.lastIndex) "to" else "via$i"
                ilon = ((lon + 180.0) * 1_000_000.0 + 0.5).toInt()
                ilat = ((lat + 90.0) * 1_000_000.0 + 0.5).toInt()
            }
        }
        val engine = RoutingEngine(null, null, File(dir, "brouter/segments4"), waypoints, rc, RoutingEngine.BROUTER_ENGINEMODE_ROUTING)
        engine.doRun(0) // 0: no time limit
        val t1 = SystemClock.elapsedRealtime()
        result.put("ms_engine", 0).put("ms_route", t1 - t0).put("ms_total", t1 - t0)
        val error = engine.errorMessage
        val track = engine.foundTrack
        if (error != null || track == null) {
            result.put("error", error ?: "no track")
            return
        }
        val coords = JSONArray(track.nodes.map { JSONArray().put(it.iLat / 1_000_000.0 - 90.0).put(it.iLon / 1_000_000.0 - 180.0) })
        outFile.writeText(JSONObject().put("distance_m", engine.distance).put("ascend_m", engine.ascend).put("latlon", coords).toString())
        result.put("length_m", engine.distance)
    }

    private fun procStatusKb(key: String): Long =
        File("/proc/self/status").readLines().firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull() ?: -1

    private companion object {
        const val TAG = "EngineSpike"
    }
}
