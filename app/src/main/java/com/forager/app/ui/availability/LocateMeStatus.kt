package com.forager.app.ui.availability

import com.forager.app.domain.model.LatLng

/**
 * What the map's GPS/locate-me icon shows, distinct from [OfflineMapStatus] and from
 * [AvailabilityUiState.locationPermissionDenied] (the "use current location" *search region*
 * control's own state — a different feature with a different call site, see [LocateMeStatus]'s
 * use in `AvailabilityScreen.kt`'s icon stack). Denied/unavailable are explicit states here per
 * CLAUDE.md: recentering silently failing and leaving the map wherever it was would look
 * indistinguishable from "already centered".
 */
sealed interface LocateMeStatus {
    data object Idle : LocateMeStatus
    data object Loading : LocateMeStatus

    /**
     * [altitude] is the same nullable GPS altitude [com.forager.app.domain.LocationResult.Success]
     * carries — null when this fix didn't report one, read by the top compass/elevation strip
     * (see decision #8 in `docs/plans/map-redesign-gaia.md`: elevation is read off this same fix,
     * not a separate network lookup), shown there as unavailable rather than defaulted.
     */
    data class Located(val location: LatLng, val altitude: Double?) : LocateMeStatus
    data object PermissionDenied : LocateMeStatus
    data object Unavailable : LocateMeStatus
}
