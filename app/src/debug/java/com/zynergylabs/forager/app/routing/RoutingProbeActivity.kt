package com.zynergylabs.forager.app.routing

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * S22 timing probe (dispatch 2026-10-23-02, verify-first item 2). Throwaway branch only. Routes the Harriman fixture's
 * inside-across-the-degree-line and inside-long routes five times each through [BRouterTrailRouter], called from the
 * main thread, and logs time, the process's peak memory, and how many 16 ms main-thread ticks ran during each route.
 * Data: files/routing-probe/segments/W75_N40.rd5, copied in with run-as.
 */
class RoutingProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            val profileDir = withContext(Dispatchers.IO) { RoutingProfile.install(this@RoutingProbeActivity) }
            val router = BRouterTrailRouter(profileDir, { tag, msg, e -> Log.w(tag, msg, e) })
            val data = File(filesDir, "routing-probe/segments")
            Log.i(TAG, "start hwm=${hwm()} data=${data.list()?.toList()}")
            val routes = listOf(
                "across" to (LatLng(41.2742564, -73.9978932) to LatLng(41.3036912, -74.0159457)),
                "long" to (LatLng(41.2772811, -74.0422743) to LatLng(41.292043, -73.996678)),
            )
            for (run in 1..5) for ((name, ends) in routes) {
                var ticks = 0
                val handler = Handler(Looper.getMainLooper())
                val tick = object : Runnable { override fun run() { ticks++; handler.postDelayed(this, 16) } }
                handler.post(tick)
                val t0 = SystemClock.elapsedRealtimeNanos()
                val result = router.route(ends.first, ends.second, data)
                val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                handler.removeCallbacks(tick)
                val what = when (result) {
                    is TrailRouteResult.Found -> "found ${result.route.lengthMetres} m ${result.route.points.size} points"
                    is TrailRouteResult.Failed -> "failed ${result.failure}"
                }
                Log.i(TAG, "run=$run route=$name ms=${"%.1f".format(ms)} mainTicks=$ticks hwm=${hwm()} $what")
            }
            Log.i(TAG, "done hwm=${hwm()}")
            finish()
        }
    }

    private fun hwm(): String = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmHWM") }?.substringAfter(":")?.trim() ?: "?"

    private companion object { const val TAG = "RoutingProbe" }
}
