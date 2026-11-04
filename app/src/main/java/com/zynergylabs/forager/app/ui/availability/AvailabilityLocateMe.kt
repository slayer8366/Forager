package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The map icon stack's locate-me button, moved out of [AvailabilityViewModel] unchanged (dispatch 2026-10-28-13, the
 * modular rule: the ViewModel was at 797 lines and the altitude work had to add to it). Every write goes through
 * [updateState], every read of the screen's state through [uiState], and the fetch launches in [scope], the
 * ViewModel's own `viewModelScope`, read each time rather than kept.
 */
internal class AvailabilityLocateMe(
    private val uiState: () -> AvailabilityUiState,
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val locationProvider: LocationProvider,
) {

    /**
     * The map icon stack's GPS/locate-me button: recenters the map on the device's live location.
     * Deliberately independent of [AvailabilityViewModel.useCurrentLocation] — that one sets the *search region*
     * and re-runs the search; this one only reports a point for the map to pan to, touching
     * neither [AvailabilityUiState.region] nor any search state. [LocateMeStatus] carries its own
     * denied/unavailable states rather than reusing [AvailabilityUiState.locationPermissionDenied],
     * which belongs to the other control.
     */
    fun locateMe() {
        scope().launch {
            updateState { it.copy(locateMeStatus = LocateMeStatus.Loading) }
            val status = when (val result = locationProvider.getCurrentLocation()) {
                // RECORD -1525: tagged with the search it arrived under, read now (see LocateMeStatus.Located).
                is LocationResult.Success ->
                    LocateMeStatus.Located(LatLng(result.lat, result.lng), result.altitude, uiState().searchFrameSerial)
                LocationResult.PermissionDenied -> LocateMeStatus.PermissionDenied
                LocationResult.LocationUnavailable -> LocateMeStatus.Unavailable
            }
            updateState { it.copy(locateMeStatus = status) }
        }
    }

    /**
     * The Activity denied the OS permission dialog before [locationProvider] was ever asked — see
     * [AvailabilityViewModel.onPermissionDenied] for why that gate is the Activity's job, not this class's. Mirrors it
     * for [LocateMeStatus] rather than [AvailabilityUiState.locationPermissionDenied], which is the
     * other control's field.
     */
    fun onLocateMePermissionDenied() {
        updateState { it.copy(locateMeStatus = LocateMeStatus.PermissionDenied) }
    }
}
