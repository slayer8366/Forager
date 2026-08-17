package com.forager.app.ui.availability

import com.forager.app.domain.model.Region

/**
 * What the Settings panel's "Offline Maps" section shows, distinct from
 * [com.forager.app.domain.OfflineMapInfo]: that type is "what's on disk right now", this is "what
 * the UI is displaying", which also has to represent an in-flight download and a failure — neither
 * of which the domain type has any business modelling.
 */
sealed interface OfflineMapStatus {
    data object NotDownloaded : OfflineMapStatus

    data class Downloading(val downloaded: Int, val total: Int) : OfflineMapStatus

    data class Downloaded(
        val region: Region,
        val tileCount: Int,
        val sizeBytes: Long,
        val downloadedAtEpochMillis: Long,
    ) : OfflineMapStatus

    data class Failed(val message: String) : OfflineMapStatus
}
