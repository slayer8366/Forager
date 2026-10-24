package com.zynergylabs.forager.app.ui.availability

/**
 * The offline-region picker's own transient state — whether its last download attempt is in
 * flight, just succeeded, or just failed. Distinct from [AvailabilityUiState.offlineRegions], the
 * persisted list of what's actually on disk: since a region no longer replaces whatever was
 * downloaded before it (see [com.zynergylabs.forager.app.domain.OfflineMapRepository]'s doc comment), there is
 * no single "the offline map" for this type to describe any more — only "how did the picker's most
 * recent download go."
 */
sealed interface OfflineMapStatus {
    data object Idle : OfflineMapStatus

    /**
     * [gettingRoutes]: the tiles are in and the region's trail routes are being fetched (dispatch 2026-10-23-03, L4).
     * [paused]: the download lost signal and waits for it, continuing by itself (dispatch 2026-10-23-06 Amendment 4); the
     * counts stay those of the last progress, so the bar stays where it was.
     */
    data class Downloading(
        val downloaded: Int,
        val total: Int,
        val gettingRoutes: Boolean = false,
        val paused: Boolean = false,
    ) : OfflineMapStatus

    /** The picker's last download finished; [AvailabilityUiState.offlineRegions] already reflects it. */
    data object Succeeded : OfflineMapStatus

    data class Failed(val message: String) : OfflineMapStatus
}
