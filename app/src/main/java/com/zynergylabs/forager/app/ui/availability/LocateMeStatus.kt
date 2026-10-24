package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * What the map's GPS/locate-me icon shows, distinct from [OfflineMapStatus] and from
 * [AvailabilityUiState.locationPermissionDenied] (the "use current location" *search region*
 * control's own state — a different feature with a different call site, see this file's use in
 * `AvailabilityScreen.kt`'s icon stack). Denied/unavailable are explicit states here per
 * CLAUDE.md: recentering silently failing and leaving the map wherever it was would look
 * indistinguishable from "already centered".
 */
sealed interface LocateMeStatus {
    data object Idle : LocateMeStatus
    data object Loading : LocateMeStatus

    /**
     * [altitude] is the same nullable GPS altitude [com.zynergylabs.forager.app.domain.LocationResult.Success]
     * carries — null when this fix didn't report one, read by the top compass/elevation strip
     * (elevation is read off this same fix, not a separate network lookup), shown there as
     * unavailable rather than defaulted.
     *
     * [searchFrameSerial] is [AvailabilityUiState.searchFrameSerial] when this fix arrived: the search it belongs to (RECORD
     * -1525). The map pans to a fix only for the search it arrived under ([compactMapLocateFocus]), so a fix from before a
     * search is never replayed as that search's locate-me when the Maps tab is composed again.
     */
    data class Located(val location: LatLng, val altitude: Double?, val searchFrameSerial: Int = 0) : LocateMeStatus
    data object PermissionDenied : LocateMeStatus
    data object Unavailable : LocateMeStatus
}
