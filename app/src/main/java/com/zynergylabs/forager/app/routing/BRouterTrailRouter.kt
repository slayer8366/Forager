package com.zynergylabs.forager.app.routing

import btools.router.OsmNodeNamed
import btools.router.OsmPathElement
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
import com.zynergylabs.forager.app.domain.routing.TrailSnapResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

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
 * - Each point keeps the trail data's height, or `null` where the data has none ([trailRouteOf]; dispatch 2026-10-30-06).
 *
 * **Threading.** Routing runs on [dispatcher], never the caller's thread, and one route at a time in the whole
 * process: BRouter's `ProfileCache` is static and shared, and one route's memory at a time is the bound the
 * spike measured (about 25 MB over the app, item 3). The engine is built there; its search, the blocking
 * `doRun`, runs on the router's own engine thread while the call waits for it, so the call can hear a cancel.
 *
 * **Cancelling stops the engine** (Sorrel's review of #382, M1; dispatch 2026-10-23-09 Amendment 1, part 1). A
 * cancelled call calls `RoutingEngine.terminate()`, which the search reads once per pass of its loop
 * (`RoutingEngine.java:1975`, and the retry at `:1722`), then waits, not cancellably, until the engine has
 * stopped, and only then gives up the one-route lock and rethrows the cancellation. So the next route never
 * waits behind a search nobody wants, and two engines are never in memory at once. Waypoint matching and file
 * reads do not read the flag, so a cancel lands at the search's next pass, not instantly. A cancelled call
 * returns no result and logs nothing: it is not an engine failure.
 *
 * @param profileDir holds `hiking-mountain.brf` and the `lookups.dat` it reads ([RoutingProfile]).
 */
class BRouterTrailRouter internal constructor(
    private val profileDir: File,
    private val errorLog: ErrorLog,
    private val dispatcher: CoroutineDispatcher,
    private val maxRunningTimeMillis: Long,
    /** Builds the engine for a request. The real one in production; a test's to hold a search open (M1). */
    private val newEngine: (routeData: File, waypoints: List<OsmNodeNamed>, context: RoutingContext) -> RoutingEngine,
) : TrailRouter {

    constructor(
        profileDir: File,
        errorLog: ErrorLog,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        maxRunningTimeMillis: Long = MAX_RUNNING_TIME_MILLIS,
    ) : this(profileDir, errorLog, dispatcher, maxRunningTimeMillis, { data, waypoints, context -> routingEngine(data, waypoints, context) })

    override suspend fun route(start: LatLng, end: LatLng, routeData: File): TrailRouteResult =
        when (val outcome = runEngine(start, end, routeData)) {
            is EngineOutcome.Failed -> TrailRouteResult.Failed(outcome.failure)
            is EngineOutcome.Ran -> foundRoute(outcome.track)
        }

    /**
     * The first point of a route from [start] to [end]: where the engine matched [start] to its nearest way
     * (`routing/BRouterTrailSnapper`). Unlike [route], a route of one point is an answer here: a route from a point
     * to itself is that one matched point (seen on BRouter v1.7.10 with the Harriman fixture).
     */
    internal suspend fun firstRoutePoint(start: LatLng, end: LatLng, routeData: File): TrailSnapResult =
        when (val outcome = runEngine(start, end, routeData)) {
            is EngineOutcome.Failed -> TrailSnapResult.Failed(outcome.failure)
            is EngineOutcome.Ran -> {
                val first = outcome.track.nodes.firstOrNull()
                if (first == null) {
                    val detail = "no error reported and no route points"
                    errorLog.w(TAG, "BRouter failed", BRouterFailure(detail))
                    TrailSnapResult.Failed(TrailRouteFailure.EngineFailed(detail))
                } else {
                    TrailSnapResult.OnNetwork(latLngOf(first.getILat(), first.getILon()))
                }
            }
        }

    private fun foundRoute(track: OsmTrack): TrailRouteResult {
        if (track.nodes.size < 2) {
            // No error and no route: how BRouter reports an exception with no message (RoutingDataFolder's note).
            // Never handed on as a route.
            val detail = "no error reported and ${track.nodes.size} route points"
            errorLog.w(TAG, "BRouter failed", BRouterFailure(detail))
            return TrailRouteResult.Failed(TrailRouteFailure.EngineFailed(detail))
        }
        return TrailRouteResult.Found(trailRouteOf(track.nodes, track.distance))
    }

    /** One engine run, start to end, under the one-route lock: what both [route] and [firstRoutePoint] read. */
    private suspend fun runEngine(start: LatLng, end: LatLng, routeData: File): EngineOutcome =
        withContext(dispatcher) {
            ONE_ROUTE_AT_A_TIME.withLock {
                RoutingDataFolder.check(routeData, start, end)?.let { return@withLock EngineOutcome.Failed(it) }
                val engine = try {
                    buildEngine(start, end, routeData).also { runToEnd(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The constructor parses the profile; doRun catches what routing throws. Anything reaching here is
                    // neither a route nor a failure BRouter named.
                    errorLog.w(TAG, "BRouter threw", e)
                    return@withLock EngineOutcome.Failed(TrailRouteFailure.EngineFailed(e.message ?: e.javaClass.simpleName))
                }
                // The getters, not properties: RoutingEngine also has protected fields of these names.
                val message: String? = engine.getErrorMessage()
                if (message != null) {
                    val failure = BRouterFailures.classify(message)
                    if (failure is TrailRouteFailure.EngineFailed) errorLog.w(TAG, "BRouter failed", BRouterFailure(message))
                    return@withLock EngineOutcome.Failed(failure)
                }
                EngineOutcome.Ran(engine.getFoundTrack())
            }
        }

    private fun buildEngine(start: LatLng, end: LatLng, routeData: File): RoutingEngine {
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
        return newEngine(routeData, waypoints, context).apply {
            quite = true // BRouter's spelling: with no output file it would otherwise print the route as GPX to stdout
        }
    }

    /**
     * Runs [engine]'s blocking search on the engine thread and waits for it. A cancel while waiting terminates the
     * engine, waits (not cancellably) for it to stop, and rethrows: see the class doc, "Cancelling stops the engine".
     */
    private suspend fun runToEnd(engine: RoutingEngine) {
        val run = ENGINE_RUNS.async { engine.doRun(maxRunningTimeMillis) }
        try {
            run.await()
        } catch (e: CancellationException) {
            engine.terminate()
            withContext(NonCancellable) { run.join() }
            throw e
        }
    }

    private sealed interface EngineOutcome {
        class Ran(val track: OsmTrack) : EngineOutcome

        class Failed(val failure: TrailRouteFailure) : EngineOutcome
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

        /**
         * The engine thread: one daemon thread, since one route runs at a time. Its own thread rather than the
         * caller's [dispatcher], so a call waiting on a search can always be resumed to cancel it, even on a
         * one-thread dispatcher that the search would otherwise be holding.
         */
        private val ENGINE_RUNS = CoroutineScope(
            SupervisorJob() + Executors.newSingleThreadExecutor { r -> Thread(r, "brouter-engine").apply { isDaemon = true } }.asCoroutineDispatcher(),
        )

        private fun routingEngine(routeData: File, waypoints: List<OsmNodeNamed>, context: RoutingContext): RoutingEngine =
            RoutingEngine(null, null, routeData, waypoints, context, RoutingEngine.BROUTER_ENGINEMODE_ROUTING)

        /**
         * The route along [nodes], the engine's found track, with its [lengthMetres]: each node's position and its height
         * ([heightMetresOf]). Its own function so the tests can hand it built nodes, for mixes no fixture has.
         */
        internal fun trailRouteOf(nodes: List<OsmPathElement>, lengthMetres: Int): TrailRoute = TrailRoute(
            points = nodes.map { latLngOf(it.getILat(), it.getILon()) },
            lengthMetres = lengthMetres,
            heightsMetres = nodes.map { heightMetresOf(it.getSElev()) },
        )

        /**
         * A node's height in metres from BRouter's stored value, in quarter metres (`OsmPathElement.getElev`); `null` for
         * [Short.MIN_VALUE], BRouter's "no height" (its GeoJSON and GPX writers leave the height out for it, and a square
         * built without heights gives it at every node: the test fixture's command-line output reads Elevation -8192,
         * which is -32768 / 4). Never 0 for missing. 0 and below are real heights (sea level, below it).
         */
        internal fun heightMetresOf(selev: Short): Double? = null // STUB

        /** BRouter positions are integer micro-degrees offset by 90 and 180 (RoutingParamCollector.readPosition). */
        private fun latLngOf(iLat: Int, iLon: Int): LatLng = LatLng(lat = (iLat - 90_000_000) / 1e6, lng = (iLon - 180_000_000) / 1e6)
    }
}
