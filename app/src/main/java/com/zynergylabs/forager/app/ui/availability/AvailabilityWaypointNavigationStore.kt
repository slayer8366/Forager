package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.WaypointNavigationRepository
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where [AvailabilityViewModel]'s waypoint navigation is kept across the process (UI split Phase 2.4, PR 6,
 * dispatch 2026-10-19-33, RECORD -1453), moved out of the ViewModel unchanged: the read that picks a kept
 * navigation back up at start, the write that keeps the current one, and whether the walker has chosen one
 * since the ViewModel was made. Every write to the screen's state goes through [updateState], and its
 * coroutines launch in [scope], the ViewModel's own `viewModelScope`, read each time rather than kept.
 *
 * Starting and ending a navigation stay in the ViewModel (`onNavigateToWaypoint`,
 * `onStopWaypointNavigation`): they pause and resume the return, which starts and stops the recording
 * engine's route tick (RECORD -1450). They set [waypointNavigationChosenHere] and call
 * [keepWaypointNavigation] at the same points as before.
 */
internal class AvailabilityWaypointNavigationStore(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val waypointNavigationRepository: WaypointNavigationRepository,
    private val errorLog: ErrorLog,
) {

    /**
     * Whether the walker has started or ended a waypoint navigation since this ViewModel was made. A kept
     * navigation read after that ([loadWaypointNavigation]) is older than what they just did, so it is not
     * applied over it.
     */
    var waypointNavigationChosenHere = false

    /**
     * Picks the waypoint navigation back up when the app opens (dispatch 2026-09-28-502, Amendment 1, "Pick
     * navigation back up"). A failed read is logged and nothing is resumed. Whether that waypoint still
     * exists is the screen's to judge, once the waypoints have loaded (`waypointNavigationTarget`).
     */
    fun loadWaypointNavigation() {
        scope().launch {
            waypointNavigationRepository.getCurrent().fold(
                onSuccess = { kept ->
                    if (kept != null && !waypointNavigationChosenHere) updateState { it.copy(waypointNavigation = kept) }
                },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the waypoint navigation to pick back up; none is resumed.", error) },
            )
        }
    }

    /** Writes [navigation] to the store; a failure is logged, and the navigation on screen carries on regardless. */
    fun keepWaypointNavigation(navigation: WaypointNavigation?) {
        scope().launch {
            waypointNavigationRepository.setCurrent(navigation).onFailure { error ->
                errorLog.w(TAG, "Couldn't keep the waypoint navigation; it will not be picked back up if the app is closed.", error)
            }
        }
    }
}
