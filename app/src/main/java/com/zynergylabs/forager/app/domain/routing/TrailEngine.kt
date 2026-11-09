package com.zynergylabs.forager.app.domain.routing

/**
 * The one trail engine the app routes on: its [router] and its [snapper] (dispatch 2026-10-26-10, step 6 PR 2). The way
 * home and the way to a waypoint share it, because the engine runs one route at a time in the whole process
 * (`routing/BRouterTrailRouter`, its one-route lock), so a second engine would only queue behind the first and hold a
 * second copy of the engine's profile in memory.
 */
class TrailEngine(val router: TrailRouter, val snapper: TrailSnapper)

/**
 * Where a feature gets the [TrailEngine]: built on first use, not at app start, because building it installs the
 * engine's profile from the app's assets (`routing/TrailRoutingEngine`). The interface Forager owns for it, so the
 * domain never names the engine (CLAUDE.md, Architecture).
 */
interface TrailEngineSource {
    /**
     * The engine, built on the first call and the same one on every call after. A failure when it could not be built
     * (the profile could not be installed): nothing is kept, so the next call tries again. The caller logs the failure
     * in its own words and falls back as its feature does; this logs nothing itself, so a failure is logged once.
     */
    suspend fun engine(): Result<TrailEngine>
}
