package com.zynergylabs.forager.app.routing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.routing.TrailSnapResult
import com.zynergylabs.forager.app.domain.routing.TrailSnapper
import com.zynergylabs.forager.app.domain.wayhome.FindWayHomeUseCase
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * THROWAWAY, never merged (dispatch 2026-10-24-10 Amendment 1, the timing run; report section 6). Times the way home's
 * two trail-snap methods on a saved track, on the phone, through adb:
 *
 * ```
 * adb shell am broadcast -n com.zynergylabs.forager.app/.routing.WayHomeTimingReceiver --es op list
 * adb shell am broadcast -n com.zynergylabs.forager.app/.routing.WayHomeTimingReceiver --es op time --es track ID --es method SNAP
 * adb shell am broadcast -n ... --es op time --es track ID --es method FULL_ROUTE --el cancelAfterMs 300
 * ```
 *
 * Logs under [TAG]: per engine call its method, kind (snap or route), outcome and milliseconds; then a summary. No
 * positions are logged: ids, counts, metres and times only.
 */
class WayHomeTimingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Finished at once: a long scan outlives a broadcast's time limit. Keep the app in the foreground while timing.
        val container = (context.applicationContext as ForagerApplication).container
        scope.launch {
            try {
                when (intent.getStringExtra("op")) {
                    "list" -> container.trackRepository.getAll().onSuccess { tracks ->
                        tracks.forEach { Log.i(TAG, "track id=${it.id} points=${it.points.size} ended=${it.endedAtEpochMillis != null}") }
                    }.onFailure { Log.w(TAG, "list failed", it) }
                    "regions" -> container.offlineMapRepository.listRegions().onSuccess { regions ->
                        regions.forEach { Log.i(TAG, "region id=${it.id} name=${it.name} routes=${it.routeData.javaClass.simpleName}") }
                    }
                    "time" -> time(context, intent)
                    else -> Log.w(TAG, "unknown op ${intent.getStringExtra("op")}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "timing failed", e)
            }
        }
    }

    private suspend fun time(context: Context, intent: Intent) {
        val container = (context.applicationContext as ForagerApplication).container
        val trackId = intent.getStringExtra("track") ?: return Log.w(TAG, "no track").let { }
        val method = TrailSnapMethod.valueOf(intent.getStringExtra("method") ?: "SNAP")
        val cancelAfterMs = intent.getLongExtra("cancelAfterMs", -1L)
        val track = container.trackRepository.getById(trackId).getOrNull() ?: return Log.w(TAG, "track $trackId not found").let { }
        val profileDir = RoutingProfile.install(context)
        val calls = mutableListOf<Call>()
        val router = BRouterTrailRouter(profileDir, container.errorLog)
        val timedRouter = object : TrailRouter {
            override suspend fun route(start: LatLng, end: LatLng, routeData: File): TrailRouteResult {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val r = router.route(start, end, routeData)
                calls += Call("route", if (r is TrailRouteResult.Found) "found" else (r as TrailRouteResult.Failed).failure.javaClass.simpleName, (SystemClock.elapsedRealtimeNanos() - t0) / 1e6)
                Log.i(TAG, "call method=$method kind=route outcome=${calls.last().outcome} ms=${"%.1f".format(calls.last().ms)}")
                return r
            }
        }
        val snapper = BRouterTrailSnapper(router, method)
        val timedSnapper = object : TrailSnapper {
            override suspend fun nearestTrail(point: LatLng, toward: LatLng, routeData: File): TrailSnapResult {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val r = snapper.nearestTrail(point, toward, routeData)
                calls += Call("snap", if (r is TrailSnapResult.OnNetwork) "onNetwork" else (r as TrailSnapResult.Failed).failure.javaClass.simpleName, (SystemClock.elapsedRealtimeNanos() - t0) / 1e6)
                Log.i(TAG, "call method=$method kind=snap outcome=${calls.last().outcome} ms=${"%.1f".format(calls.last().ms)}")
                return r
            }
        }
        val useCase = FindWayHomeUseCase(container.offlineMapRepository, timedRouter, timedSnapper, container.errorLog)
        val rt = Runtime.getRuntime()
        val javaBefore = rt.totalMemory() - rt.freeMemory()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val t0 = SystemClock.elapsedRealtimeNanos()
        var result: WayHomeResult? = null
        val job = scope.launch { result = useCase(track, null) }
        if (cancelAfterMs >= 0) {
            delay(cancelAfterMs)
            val c0 = SystemClock.elapsedRealtimeNanos()
            job.cancelAndJoin()
            Log.i(TAG, "cancel method=$method afterMs=$cancelAfterMs cancelToEndMs=${"%.1f".format((SystemClock.elapsedRealtimeNanos() - c0) / 1e6)} callsBeforeCancel=${calls.size}")
        } else {
            job.join()
        }
        val totalMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
        val javaAfter = rt.totalMemory() - rt.freeMemory()
        val nativeAfter = Debug.getNativeHeapAllocatedSize()
        fun stats(kind: String, outcome: String?): String {
            val ms = calls.filter { it.kind == kind && (outcome == null || it.outcome == outcome) }.map { it.ms }
            if (ms.isEmpty()) return "n=0"
            val sorted = ms.sorted()
            return "n=${ms.size} first=${"%.1f".format(ms.first())} min=${"%.1f".format(sorted.first())} median=${"%.1f".format(sorted[sorted.size / 2])} max=${"%.1f".format(sorted.last())}"
        }
        Log.i(TAG, "summary method=$method track=$trackId storedPoints=${track.points.size} engineCalls=${calls.size} totalMs=${"%.1f".format(totalMs)} result=${resultText(result)}")
        for (outcome in calls.filter { it.kind == "snap" }.map { it.outcome }.distinct()) Log.i(TAG, "summary method=$method snaps outcome=$outcome ${stats("snap", outcome)}")
        Log.i(TAG, "summary method=$method routes ${stats("route", null)}")
        Log.i(TAG, "summary method=$method javaHeapDeltaKb=${(javaAfter - javaBefore) / 1024} nativeHeapDeltaKb=${(nativeAfter - nativeBefore) / 1024}")
    }

    private fun resultText(result: WayHomeResult?): String = when (result) {
        null -> "cancelled"
        is WayHomeResult.TrackOnly -> "TrackOnly(${result.note})"
        is WayHomeResult.AlongTrails -> "AlongTrails(trackBack=${result.wayHome.trackBack.meters.toInt()} trail=${result.wayHome.trail.meters.toInt()} trackToStart=${result.wayHome.trackToStart.meters.toInt()} total=${result.wayHome.totalMeters.toInt()})"
    }

    private class Call(val kind: String, val outcome: String, val ms: Double)

    private companion object {
        const val TAG = "WayHomeTiming"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
