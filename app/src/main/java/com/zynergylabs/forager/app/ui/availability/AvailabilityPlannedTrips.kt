package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The planned trips of [AvailabilityViewModel] (UI split Phase 2.4, PR 1, dispatch 2026-10-19-33,
 * RECORD -1453), moved out of the ViewModel unchanged. It holds no state of its own. Every write to
 * the screen's state goes through [updateState], and its coroutines launch in [scope], the
 * ViewModel's own `viewModelScope`, read each time rather than kept. It starts and stops none of the
 * ViewModel's tracked jobs; the ViewModel calls [loadPlannedTrips] from `init` and from
 * `reloadAfterRestore`, at the same positions as before.
 */
internal class AvailabilityPlannedTrips(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val getPlannedTrips: GetPlannedTripsUseCase,
    private val savePlannedTrip: SavePlannedTripUseCase,
    private val deletePlannedTrip: DeletePlannedTripUseCase,
    private val errorLog: ErrorLog,
) {

    fun loadPlannedTrips(): Job {
        return scope().launch {
            getPlannedTrips().fold(
                onSuccess = { trips -> updateState { it.copy(plannedTrips = trips, plannedTripsErrorMessage = null) } },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't load planned trips.", error)
                    updateState {
                        it.copy(plannedTripsErrorMessage = "Couldn't load planned trips.")
                    }
                },
            )
        }
    }

    /** Called from the trip-planning flow once a date and name are confirmed for a pin placed via [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker]. */
    fun onPlaceTripPin(location: LatLng, date: LocalDate, name: String) {
        scope().launch {
            savePlannedTrip(location, date, name).fold(
                onSuccess = { loadPlannedTrips() },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't save the planned trip.", error)
                    updateState {
                        it.copy(plannedTripsErrorMessage = "Couldn't save the planned trip.")
                    }
                },
            )
        }
    }

    fun onDeletePlannedTrip(id: String) {
        scope().launch {
            deletePlannedTrip(id).fold(
                onSuccess = { loadPlannedTrips() },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't delete the planned trip.", error)
                    updateState {
                        it.copy(plannedTripsErrorMessage = "Couldn't delete the planned trip.")
                    }
                },
            )
        }
    }
}
