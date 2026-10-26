package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The app's one trail engine, built on first use and shared by the way home ([WayHomeRouting]) and the way to a waypoint
 * (`domain/wayto/FindWayToWaypointUseCase`) (dispatch 2026-10-26-10, step 6 PR 2). Before this the engine was private
 * to [WayHomeRouting]; moved here unchanged so a second feature does not build a second one: BRouter runs one route at a
 * time in the whole process ([BRouterTrailRouter]'s one-route lock, `runEngine`), so two engines would gain nothing.
 *
 * **What it builds:** BRouter ([BRouterTrailRouter]) and the trail snap by [method] (the planner's provisional choice,
 * L-389-1, until the S22 timing), over the engine's profile.
 *
 * **The profile.** The first call installs it, on [ioDispatcher], once per process (`RoutingProfile.install` in
 * production; a parameter so this file holds no `Context`). A failed install is handed back as a failure and nothing is
 * kept, so the next call tries again. Each caller logs it in its own words, with its own fallback.
 */
class TrailRoutingEngine(
    private val installProfile: () -> File,
    errorLog: ErrorLog,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val method: TrailSnapMethod = TrailSnapMethod.SNAP,
    private val newRouter: (profileDir: File) -> BRouterTrailRouter = { BRouterTrailRouter(it, errorLog) },
) : TrailEngineSource {
    private val building = Mutex()
    private var built: TrailEngine? = null

    override suspend fun engine(): Result<TrailEngine> = building.withLock {
        built?.let { return@withLock Result.success(it) }
        val profileDir = try {
            withContext(ioDispatcher) { installProfile() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Result.failure(e)
        }
        val router = newRouter(profileDir)
        Result.success(TrailEngine(router, BRouterTrailSnapper(router, method)).also { built = it })
    }
}
