package com.zynergylabs.forager.app.routing

import btools.router.OsmTrack
import btools.router.RoutingContext
import btools.router.RoutingEngine
import btools.router.RoutingParamCollector
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailRoute
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [TrailRouter] on BRouter v1.7.10, in-process (dispatch 2026-10-23-02; vendored in third_party/brouter).
 *
 * **What it reuses from the engine spike** (`docs/navigation/2026-10-03-engine-spike-report.md`, item 6): BRouter
 * is called as its own Android app calls it, a `RoutingContext` with the profile's path, then
 * `RoutingEngine(...).doRun(...)`, on the stock `hiking-mountain` profile the spike measured.
 *
 * **What it changes:**
 * - The request is built by BRouter's own `RoutingParamCollector`, the same calls its command-line router makes,
 *   so a route here is the one `btools.server.BRouter` returns for the same query (the tests compare them).
 * - Dynamic-range matching is off (`use_dynamic_range=0`, RECORD -1546): an end with no way within the profile's
 *   catching range fails as outside the data, where BRouter's default retries up to 60 km out and moves the end.
 * - Success is read from the error message being null, as BRouter's command-line router reads it, never from
 *   the found track: `getFoundTrack()` is never null, and is an empty track when routing failed.
 * - The data folder is checked first ([RoutingDataFolder]), and the engine's messages are named
 *   ([BRouterFailures]).
 * - A time limit, an explicit operating limit (CLAUDE.md, Architecture).
 *
 * **Threading.** Routing runs on [dispatcher], never the caller's thread, and one route at a time in the whole
 * process: BRouter's `ProfileCache` is static and shared, and one route's memory at a time is the bound the
 * spike measured (about 25 MB over the app, item 3).
 *
 * @param profileDir holds `hiking-mountain.brf` and the `lookups.dat` it reads ([RoutingProfile]).
 */
class BRouterTrailRouter(
    private val profileDir: File,
    private val errorLog: ErrorLog,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxRunningTimeMillis: Long = MAX_RUNNING_TIME_MILLIS,
) : TrailRouter {

    override suspend fun route(start: LatLng, end: LatLng, routeData: File): TrailRouteResult =
        withContext(dispatcher) {
            ONE_ROUTE_AT_A_TIME.withLock { routeNow(start, end, routeData) }
        }

    private fun routeNow(start: LatLng, end: LatLng, routeData: File): TrailRouteResult {
        RoutingDataFolder.check(routeData, start, end)?.let { return TrailRouteResult.Failed(it) }
        val engine = try {
            runEngine(start, end, routeData)
        } catch (e: Exception) {
            // The constructor parses the profile; doRun catches what routing throws. Anything reaching here is
            // neither a route nor a failure BRouter named.
            errorLog.w(TAG, "BRouter threw", e)
            return TrailRouteResult.Failed(TrailRouteFailure.EngineFailed(e.message ?: e.javaClass.simpleName))
        }
        // The getters, not properties: RoutingEngine also has protected fields of these names.
        val message: String? = engine.getErrorMessage()
        if (message != null) {
            val failure = BRouterFailures.classify(message)
            if (failure is TrailRouteFailure.EngineFailed) errorLog.w(TAG, "BRouter failed", BRouterFailure(message))
            return TrailRouteResult.Failed(failure)
        }
        val track: OsmTrack = engine.getFoundTrack()
        if (track.nodes.size < 2) {
            // No error and no route: how BRouter reports an exception with no message (RoutingDataFolder's note).
            // Never handed on as a route.
            val detail = "no error reported and ${track.nodes.size} route points"
            errorLog.w(TAG, "BRouter failed", BRouterFailure(detail))
            return TrailRouteResult.Failed(TrailRouteFailure.EngineFailed(detail))
        }
        return TrailRouteResult.Found(
            TrailRoute(
                // BRouter positions are integer micro-degrees offset by 90 and 180 (RoutingParamCollector.readPosition).
                points = track.nodes.map { LatLng(lat = (it.getILat() - 90_000_000) / 1e6, lng = (it.getILon() - 180_000_000) / 1e6) },
                lengthMetres = track.distance,
            ),
        )
    }

    private fun runEngine(start: LatLng, end: LatLng, routeData: File): RoutingEngine {
        val collector = RoutingParamCollector()
        // "lon,lat|lon,lat", as the command-line router's lonlats; Double.toString gives back the same doubles.
        val waypoints = collector.getWayPointList("${start.lng},${start.lat}|${end.lng},${end.lat}")
        val context = RoutingContext()
        collector.setParams(
            context,
            waypoints,
            hashMapOf(
                "profile" to File(profileDir, RoutingProfile.PROFILE_FILE).absolutePath,
                "alternativeidx" to "0",
                "profile:use_dynamic_range" to USE_DYNAMIC_RANGE,
            ),
        )
        return RoutingEngine(null, null, routeData, waypoints, context, RoutingEngine.BROUTER_ENGINEMODE_ROUTING).apply {
            quite = true // BRouter's spelling: with no output file it would otherwise print the route as GPX to stdout
            doRun(maxRunningTimeMillis)
        }
    }

    private class BRouterFailure(message: String) : Exception(message)

    companion object {
        private const val TAG = "BRouterTrailRouter"

        /** RECORD -1546: off, so an end outside the data fails instead of moving to a way up to 60 km away. */
        internal const val USE_DYNAMIC_RANGE = "0"

        /**
         * The operating limit on one route. The spike's slowest route on the S22 took 1.2 s (a town grid, item 3);
         * BRouter's command-line router allows 60 s. Past this BRouter stops with "… timeout after N seconds",
         * which comes back as [TrailRouteFailure.EngineFailed].
         */
        const val MAX_RUNNING_TIME_MILLIS = 30_000L

        private val ONE_ROUTE_AT_A_TIME = Mutex()
    }
}
