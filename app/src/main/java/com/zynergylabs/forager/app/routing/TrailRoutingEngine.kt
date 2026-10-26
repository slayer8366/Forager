package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/** STUB (dispatch 2026-10-26-10, fail first): never builds an engine. */
class TrailRoutingEngine(
    private val installProfile: () -> File,
    errorLog: ErrorLog,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val method: TrailSnapMethod = TrailSnapMethod.SNAP,
    private val newRouter: (profileDir: File) -> BRouterTrailRouter = { BRouterTrailRouter(it, errorLog) },
) : TrailEngineSource {
    override suspend fun engine(): Result<TrailEngine> = Result.failure(UnsupportedOperationException("stub"))
}
