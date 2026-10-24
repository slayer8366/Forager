package com.zynergylabs.forager.app.domain.routing

import com.zynergylabs.forager.app.domain.model.LatLng
import java.io.File

/**
 * Where the nearest trail is to a point, on the routing data stored on the phone (dispatch 2026-10-23-09 Amendment 1,
 * part 1): the test the way home uses to find where the walker left the trail and where they joined it
 * (`domain/wayhome/TrailJoins`). The interface Forager owns for it (CLAUDE.md, Architecture); the one implementation is
 * BRouter's (`routing/BRouterTrailSnapper`).
 *
 * **Two ways to answer, behind this one interface** (RECORD -1589, the owner: "Measure, then pick"): [TrailSnapMethod].
 * Which one ships is decided by timing both on the S22, not here.
 *
 * Never runs on the caller's thread and never throws for a routing outcome, as [TrailRouter.route].
 */
interface TrailSnapper {
    /**
     * The point on a routable way the engine matches [point] to, in [routeData]. [toward] is a second point the
     * [TrailSnapMethod.FULL_ROUTE] method routes to; the [TrailSnapMethod.SNAP] method does not read it.
     *
     * A point with no routable way within the engine's catching range (250 m for BRouter v1.7.10 with dynamic range
     * off) fails as [TrailRouteFailure.EndOutsideData] for [RouteEnd.START]. How far is "near a trail" is the caller's
     * figure, never this one's: this says only where the nearest way is.
     */
    suspend fun nearestTrail(point: LatLng, toward: LatLng, routeData: File): TrailSnapResult
}

/**
 * The two ways [TrailSnapper] can find the nearest trail (RECORD -1589, "Measure, then pick": timed on the S22 before
 * one is chosen).
 */
enum class TrailSnapMethod {
    /** A route from the point to itself: the engine matches the point to its nearest way and returns that one point. */
    SNAP,

    /** A full route from the point to `toward`, read for its first point, which is where the engine matched the point. */
    FULL_ROUTE,
}

/** What [TrailSnapper.nearestTrail] returns. */
sealed interface TrailSnapResult {
    /** [trailPoint] is the point on a routable way the engine matched the asked point to. */
    data class OnNetwork(val trailPoint: LatLng) : TrailSnapResult

    /** No answer, and which kind of failure: the same kinds as [TrailRouter.route]'s. */
    data class Failed(val failure: TrailRouteFailure) : TrailSnapResult
}
