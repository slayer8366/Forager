package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FindWayHomeUseCase
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The way home's engine, built on first use (dispatch 2026-10-24-10 Amendment 1, PR B): BRouter ([BRouterTrailRouter]),
 * the trail snap by [TrailSnapMethod.SNAP] (the planner's provisional choice, L-389-1, until the S22 timing), and
 * [FindWayHomeUseCase] over them. What `WayHomeWatch` calls in production.
 *
 * **The profile.** Nothing installed the engine's profile before this (`RoutingProfile.install` had no caller), so the
 * first way home installs it, on [ioDispatcher], once per process. A failed install is logged and gives the track with
 * "Couldn't find a trail route" ([WayHomeNote.NO_ROUTE_FOUND]); the next Return tries again.
 *
 * [installProfile] is `RoutingProfile.install(context)` in production; a parameter so this file holds no `Context`.
 */
class WayHomeRouting(
    private val installProfile: () -> File,
    private val offlineMapRepository: OfflineMapRepository,
    private val errorLog: ErrorLog,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val method: TrailSnapMethod = TrailSnapMethod.SNAP,
    private val newRouter: (profileDir: File) -> BRouterTrailRouter = { BRouterTrailRouter(it, errorLog) },
) {
    private val building = Mutex()
    private var findWayHome: FindWayHomeUseCase? = null

    suspend fun find(track: Track, origin: Waypoint?): WayHomeResult {
        val useCase = building.withLock {
            findWayHome ?: run {
                val profileDir = try {
                    withContext(ioDispatcher) { installProfile() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorLog.w(TAG, "The routing profile could not be installed; the way home follows the track.", e)
                    return WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND)
                }
                val router = newRouter(profileDir)
                FindWayHomeUseCase(offlineMapRepository, router, BRouterTrailSnapper(router, method), errorLog).also { findWayHome = it }
            }
        }
        return useCase(track, origin)
    }

    private companion object {
        const val TAG = "WayHomeRouting"
    }
}
