package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow

/**
 * The straight dashed line to the waypoint being navigated to, and its freshness clock (step 6 PR 1, dispatch
 * 2026-10-26-06, RECORD -1653, -1655 and -1657). Until then the line was a `remember`, a `produceState` and a
 * `LaunchedEffect` in `AvailabilityScreen`; the plan named this move (-1450). What it draws is unchanged: the
 * pure rules are [nextStraightLine] and [millisUntilFreshnessChanges], as before.
 *
 * The waypoint is looked up by id with [readWaypoint] when [follow] starts, which is when a navigation is chosen
 * or picked back up (the owner, -1657: "Option 2: look up by id"). No hiding rule of its own: a deleted or
 * Undo-pending waypoint resolves as gone in the screen, which ends the navigation, and the screen draws this line
 * only while its own target is found. The copy read here does not go stale: nothing changes a placed waypoint's
 * position (every `WaypointRepository.save` caller writes a new id, `CreateWaypointUseCase` and `ImportGpxUseCase`,
 * and the one update query only clears `trackId`).
 *
 * It decides nothing on its own: the ViewModel starts [follow] and cancels it (-1450). Every write goes through
 * [updateState].
 */
internal class AvailabilityWaypointLine(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val readWaypoint: suspend (String) -> Result<Waypoint?>,
    private val currentTime: CurrentTimeProvider,
    private val errorLog: ErrorLog,
) {

    /**
     * Draws the line to waypoint [waypointId] from each fix [liveFixes] gives, until the caller cancels it; `null`
     * only clears the line. It is cleared first either way, so a line drawn to another waypoint is never kept for
     * this one (as before, where [nextStraightLine] kept a line only when it ended at the same target). Between
     * fixes the clock moves on at each moment the fix's freshness changes, so the line fades then without a new fix.
     */
    suspend fun follow(waypointId: String?, liveFixes: Flow<LocationFix.Update?>) {
        // STUB (fail-first): draws nothing yet.
        updateState { it.copy(waypointStraightLine = null) }
    }
}
