package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomePlace
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress
import com.zynergylabs.forager.app.domain.wayhome.placeOnWayHome
import com.zynergylabs.forager.app.domain.wayhome.wayHomeAhead
import com.zynergylabs.forager.app.domain.wayto.StrayVerdict
import com.zynergylabs.forager.app.domain.wayto.WayToRetry
import com.zynergylabs.forager.app.domain.wayto.WayToStray
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * What the Maps tab shows of the way to a waypoint along trails (step 6 PR 3, dispatch 2026-10-26-11).
 *
 * [hud] is what the HUD is given as its route:
 * - `null`: today's straight-line HUD, while the route is worked out (the step path's step 2) and while the walker is
 *   off the line by more than the far band, which is when it is worked out again (step 5);
 * - [ReturnRoute.Ahead]: the distance along the line, "by trail", the needle along it (steps 3 and 4);
 * - [ReturnRoute.Straight]: no trail route, so today's straight line, with the note saying why (step 8).
 *
 * [line] is the line ahead the map draws in place of the straight dashed line: the lead-in, the trail and the final leg,
 * each in its kind. `null` draws today's straight dashed line. It carries no passed line (`atReturn` is empty): the step
 * path shows three parts, and the way home's grey passed line under the thin dashed legs would show between their dashes.
 */
data class WaypointRouteShown(val hud: ReturnRoute?, val line: RouteLine?)

/**
 * The way to the waypoint being navigated to along trails, worked out and followed on this ViewModel's own fixes, so in
 * the foreground only (RECORD -1652 Q5), with or without a recording. The ViewModel starts [follow] when a navigation is
 * chosen or picked back up and cancels it when it ends (RECORD -1450); it decides nothing else on its own. Within one
 * navigation it applies the step path's rules:
 *
 * - **At the first fix** it asks [findWayToWaypoint] for the way from there (`FindWayToWaypointUseCase`, PR 2). Until
 *   the answer comes the screen shows today's straight line, then the answer swaps in with no blank frame, since the
 *   straight line is held beside it ([AvailabilityWaypointLine]) and the screen picks one or the other.
 * - **Each fix** places the walker on the line and gives the figure, the needle and the line ahead exactly as the way
 *   home's do ([placeOnWayHome], [wayHomeAhead]); off the line by more than the far band, today's straight line shows.
 * - **A stray** of more than 50 m re-routes once from where the walker is ([WayToStray]), quietly.
 * - **No trail route** ([WayToWaypointResult.StraightLine]) is today's straight line with its note, until [WayToRetry]
 *   says to try again: on walking into an area holding the walker and the waypoint, or, far from any trail, after walking
 *   on more than 50 m (Amendment 1, item C, RECORD -1669). Arrival is not judged here: the HUD and the map judge it on
 *   the straight distance, as today.
 *
 * Every write goes through [updateState]. Runs on the ViewModel's scope, one thread, so its fields need no lock; the
 * engine moves its own work off that thread (`TrailRouter.route`). [findWayToWaypoint] `null` (a ViewModel built without
 * it, in tests) works nothing out: the screen is today's.
 */
internal class AvailabilityWaypointRoute(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val readWaypoint: suspend (String) -> Result<Waypoint?>,
    private val findWayToWaypoint: (suspend (LatLng, LatLng) -> WayToWaypointResult)?,
    /** The downloaded regions as the ViewModel last read them, for [WayToRetry]'s area check at each fix. */
    private val regions: () -> List<OfflineRegionSummary>,
    private val errorLog: ErrorLog,
) {

    /**
     * Follows the way to waypoint [waypointId] on each fix [liveFixes] gives, until the caller cancels it; `null` only
     * clears it. Cleared first either way, so a way to another waypoint is never kept for this one.
     */
    suspend fun follow(waypointId: String?, liveFixes: Flow<LocationFix.Update?>) {
        updateState { it.copy(waypointRoute = null) }
        if (waypointId == null) return
        val find = findWayToWaypoint ?: return
        val target = readWaypoint(waypointId).getOrElse { error ->
            errorLog.w(TAG, "Couldn't read the waypoint being navigated to; no trail route is worked out to it.", error)
            return
        } ?: return // Not stored: the screen resolves it as gone and ends the navigation with its message.
        coroutineScope {
            val following = Following(LatLng(target.lat, target.lng), find, this)
            liveFixes.collect { fix -> if (fix != null) following.onFix(LatLng(fix.lat, fix.lng)) }
        }
    }

    /** One navigation's way along trails: the line shown, the walker's place on it, the stray rule and the search. */
    private inner class Following(
        private val waypoint: LatLng,
        private val find: suspend (LatLng, LatLng) -> WayToWaypointResult,
        private val scope: CoroutineScope,
    ) {
        private var line: WayHomeLine? = null
        private var generation = 0L
        private var lastPlace: WayHomePlace? = null
        private var band = HopBand.NONE
        private var search: Job? = null
        private var lastHere: LatLng? = null
        /** Set while a straight-line result stands: says when to search again. */
        private var retry: WayToRetry? = null
        private val stray = WayToStray()
        private var loggedWhy: StrayVerdict? = null

        fun onFix(here: LatLng) {
            lastHere = here
            val shown = line
            if (shown == null) {
                // The first fix, or a straight-line result [retry] says to try again. One search at a time: no judging while one runs.
                if (search == null && retry?.judge(here, regions()) != false) startSearch(here)
                return
            }
            show(shown, here)
            when (val verdict = stray.judge(shown.metersFrom(here), searching = search != null)) {
                StrayVerdict.STAY -> loggedWhy = null
                StrayVerdict.REROUTE -> {
                    loggedWhy = null
                    startSearch(here)
                }
                StrayVerdict.SEARCHING, StrayVerdict.ALREADY_REROUTED -> {
                    if (verdict != loggedWhy) {
                        errorLog.w(TAG, "The walker strayed from the way to the waypoint and it was not re-routed: ${verdict.name}.", WayToWaypointFallback(verdict.name))
                    }
                    loggedWhy = verdict
                }
            }
        }

        /** The walker's place on [shown] from [here], as the way home's (L-395-4: the far band's hysteresis held across off the line). */
        private fun show(shown: WayHomeLine, here: LatLng) {
            val progress = placeOnWayHome(shown, here, lastPlace, band)
            val ahead = (progress as? WayHomeProgress.Placed)?.let { wayHomeAhead(shown, it, here, generation) as? RouteHome.Ahead }
            if (progress is WayHomeProgress.Placed) {
                lastPlace = progress.place
                band = progress.place.hopBand
            } else {
                band = HopBand.FAR
            }
            val next = ahead?.let {
                WaypointRouteShown(
                    hud = ReturnRoute.Ahead(it.lookahead, it.routeMeters),
                    line = RouteLine(atReturn = emptyList(), ahead = it.routeAhead, aheadIsCurrent = true, aheadParts = it.aheadParts, atReturnGeneration = generation),
                )
            } ?: WaypointRouteShown(hud = null, line = null)
            updateState { it.copy(waypointRoute = next) }
        }

        private fun startSearch(from: LatLng) {
            val regionsAtSearch = regions()
            search = scope.launch {
                val result = try {
                    find(from, waypoint)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Throwable, as the way home's job (Sorrel's M-395-2): an engine error must not end the navigation.
                    errorLog.w(TAG, "Working out the way to the waypoint failed; it is the straight line.", e)
                    WayToWaypointResult.StraightLine(WayToWaypointNote.NO_ROUTE_FOUND)
                }
                search = null
                generation++
                lastPlace = null
                band = HopBand.NONE
                when (result) {
                    is WayToWaypointResult.AlongTrails -> {
                        val found = WayHomeLine(result.way)
                        line = found
                        retry = null
                        lastHere?.let { show(found, it) }
                    }
                    // FindWayToWaypointUseCase has logged why.
                    is WayToWaypointResult.StraightLine -> {
                        line = null
                        retry = WayToRetry(waypoint, result.note, from, regionsAtSearch)
                        updateState { it.copy(waypointRoute = WaypointRouteShown(hud = ReturnRoute.Straight(result.note), line = null)) }
                    }
                }
            }
        }
    }

    /** What [errorLog] is handed for a stray that started nothing: [ErrorLog] takes a throwable, and this names the reason. */
    private class WayToWaypointFallback(reason: String) : Exception(reason)
}
