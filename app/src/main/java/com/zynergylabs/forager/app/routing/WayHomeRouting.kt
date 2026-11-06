package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FindWayHomeUseCase
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * The way home on the app's trail engine (dispatch 2026-10-24-10 Amendment 1, PR B): [FindWayHomeUseCase] over the
 * engine [engines] builds on first use. What `WayHomeWatch` calls in production.
 *
 * **The engine is shared** with the way to a waypoint since dispatch 2026-10-26-10 (step 6 PR 2): [TrailRoutingEngine]
 * holds it, and `AppContainer` hands the same one to both. Before that it was built here, privately; the building moved
 * to [TrailRoutingEngine] unchanged, and the way home's behaviour with it: the engine is asked for first, before the
 * track or the regions are read, as it was.
 *
 * **The profile.** A failed install is logged here and gives the track with "Couldn't find a trail route"
 * ([WayHomeNote.NO_ROUTE_FOUND]); the next Return tries again.
 */
class WayHomeRouting(
    private val engines: TrailEngineSource,
    private val offlineMapRepository: OfflineMapRepository,
    private val errorLog: ErrorLog,
) {
    /**
     * A way home with an engine of its own, as this class was built before the engine was shared: what its own tests
     * construct (`WayHomeRoutingTest`, unchanged by dispatch 2026-10-26-10). Production uses the shared engine.
     */
    constructor(
        installProfile: () -> File,
        offlineMapRepository: OfflineMapRepository,
        errorLog: ErrorLog,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        method: TrailSnapMethod = TrailSnapMethod.SNAP,
        newRouter: (profileDir: File) -> BRouterTrailRouter = { BRouterTrailRouter(it, errorLog) },
    ) : this(TrailRoutingEngine(installProfile, errorLog, ioDispatcher, method, newRouter), offlineMapRepository, errorLog)

    suspend fun find(track: Track, origin: Waypoint?): WayHomeResult {
        val engine = engines.engine().getOrElse { e ->
            errorLog.w(TAG, "The routing profile could not be installed; the way home follows the track.", e)
            return WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND)
        }
        return FindWayHomeUseCase(offlineMapRepository, engine.router, engine.snapper, errorLog)(track, origin)
    }

    private companion object {
        const val TAG = "WayHomeRouting"
    }
}
