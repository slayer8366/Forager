package com.forager.app.ui.availability

/**
 * The offline-region picker's own transient state — whether its last download attempt is in
 * flight, just succeeded, or just failed. Distinct from [AvailabilityUiState.offlineRegions], the
 * persisted list of what's actually on disk: since a region no longer replaces whatever was
 * downloaded before it (see [com.forager.app.domain.OfflineMapRepository]'s doc comment), there is
 * no single "the offline map" for this type to describe any more — only "how did the picker's most
 * recent download go."
 */
sealed interface OfflineMapStatus {
    data object Idle : OfflineMapStatus

    data class Downloading(val downloaded: Int, val total: Int) : OfflineMapStatus

    /** The picker's last download finished; [AvailabilityUiState.offlineRegions] already reflects it. */
    data object Succeeded : OfflineMapStatus

    data class Failed(val message: String) : OfflineMapStatus
}
