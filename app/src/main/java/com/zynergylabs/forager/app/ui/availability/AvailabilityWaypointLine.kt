package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * The straight dashed line to the waypoint being navigated to, and its freshness clock (step 6 PR 1, dispatch
 * 2026-10-26-06, RECORD -1653, -1655 and -1657). Until then the line was a `remember`, a `produceState` and a
 * `LaunchedEffect` in `AvailabilityScreen`; the plan named this move (-1450). What it draws is unchanged: the
 * pure rules are [nextStraightLine] and [millisUntilFreshnessChanges], as before.
 *
 * Held in the ViewModel's state for the owner's "Keep the last line, faded" (dispatch -502, S22 desk check): a lost fix
 * does not lose the line by a trip to the Journal. Not across a restart, where there is no last line and the first fix
 * draws it (the option the owner chose said so). Since the move it also outlives the Activity being rebuilt by a
 * dark-mode or language change, which the screen's `remember` did not (accepted, dispatch 2026-10-26-06 Amendment 1).
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
        updateState { it.copy(waypointStraightLine = null) }
        if (waypointId == null) return
        val target = readWaypoint(waypointId).getOrElse { error ->
            errorLog.w(TAG, "Couldn't read the waypoint being navigated to; no straight line is drawn to it.", error)
            return
        } ?: return // Not stored: the screen resolves it as gone and ends the navigation with its message.
        liveFixes.collectLatest { fix ->
            var now = currentTime.nowEpochMillis()
            while (true) {
                val at = now
                updateState { it.copy(waypointStraightLine = nextStraightLine(it.waypointStraightLine, fix, target, at)) }
                val untilNextChange = millisUntilFreshnessChanges(fix, now) ?: break
                delay(untilNextChange)
                now = currentTime.nowEpochMillis()
            }
        }
    }
}
