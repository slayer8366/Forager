package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.estimateOfflineTileCount
import com.zynergylabs.forager.app.domain.estimateServedOfflineTileCount
import com.zynergylabs.forager.app.domain.model.Region
import kotlinx.coroutines.flow.update

/*
 * The Offline maps submenu's ViewModel functions: the picker's fields, download, download again, opening the submenu and the region deletes, moved out of AvailabilityViewModel.kt unchanged as extension functions on [AvailabilityViewModel]
 * (dispatch 2026-10-23-03 Amendment 1; the owner: "Split it now, before merge (Recommended)", RECORD -1490's
 * rule that a file over 800 lines is split before a feature goes in). A pure move: each function's body and doc
 * comment are as they were, they are still ViewModel functions (RECORD -1450: every decision to start or stop a job
 * stays in a ViewModel function), and every caller reaches them by the same name. The members they read were made
 * `internal` for this, and nothing else changed.
 */

/** See [AvailabilityOfflineMaps.onOfflineMapLatChanged]. */
fun AvailabilityViewModel.onOfflineMapLatChanged(text: String) = offlineMaps.onOfflineMapLatChanged(text)

/** See [AvailabilityOfflineMaps.onOfflineMapLngChanged]. */
fun AvailabilityViewModel.onOfflineMapLngChanged(text: String) = offlineMaps.onOfflineMapLngChanged(text)

/** See [AvailabilityOfflineMaps.onOfflineMapRadiusChanged]. */
fun AvailabilityViewModel.onOfflineMapRadiusChanged(radiusKm: Int) = offlineMaps.onOfflineMapRadiusChanged(radiusKm)

/** See [AvailabilityOfflineMaps.onOfflineMapNameChanged]. */
fun AvailabilityViewModel.onOfflineMapNameChanged(text: String) = offlineMaps.onOfflineMapNameChanged(text)

/**
 * "Download again" on a region restored from a backup (owner, "1 B"). It downloads from the row's stored centre and
 * radius through the same [OfflineMapRepository.download] a new download uses, held to the same tile budget. The
 * download makes a new MapLibre region with a new id, so on success the restored row is replaced by it and every
 * reference to the old id follows ([OfflineMapRepository.replaceRegion]). A failure leaves the row as it was.
 * The picker's fields are not touched: this is not the picker's download.
 */
fun AvailabilityViewModel.onDownloadAgain(id: Long) {
    val row = _uiState.value.offlineRegions.firstOrNull { it.id == id && !it.isDownloaded } ?: return
    val estimatedTiles = estimateServedOfflineTileCount(row.region)
    val remainingBudget = OfflineMapRepository.TILE_COUNT_LIMIT - _uiState.value.offlineRegions.sumOf { it.tileCount }
    if (estimatedTiles > remainingBudget) {
        _uiState.update { it.withTileBudgetExceeded(estimatedTiles, remainingBudget) }
        return
    }
    _uiState.update { it.copy(offlineDownloadStatus = OfflineMapStatus.Downloading(downloaded = 0, total = 0)) }
    offlineMaps.beginDownloadAgain(row)
}

/** See [AvailabilityOfflineMaps.onOfflineMapsOpened]. */
fun AvailabilityViewModel.onOfflineMapsOpened() = offlineMaps.onOfflineMapsOpened()

/**
 * Adds a region to whatever's already downloaded — see
 * [com.zynergylabs.forager.app.domain.OfflineMapRepository]'s doc comment for why this no longer replaces a
 * prior download. A blank name defaults to "Region N" rather than blocking the download, the
 * same "default rather than require" pattern [com.zynergylabs.forager.app.domain.model.PlannedTrip.name]
 * established for planned trips.
 *
 * Refuses before ever calling [offlineMapRepository] if [estimateOfflineTileCount] projects
 * this region would push the total over [OfflineMapRepository.TILE_COUNT_LIMIT] — see that
 * constant's doc comment for why this app-side check exists at all: MapLibre's own
 * `setOfflineMapboxTileCountLimit` does not actually stop an explicit region download from
 * exceeding it, confirmed on hardware (three regions totalling 9118 tiles downloaded against a
 * "limit" of 6000). This is the real enforcement.
 *
 * A download failure is belief-changing — the user just tapped Download and is owed a yes or
 * no — so it's reported through [AvailabilityUiState.offlineDownloadStatus] with the error-red
 * treatment, unlike [AvailabilityOfflineMaps.loadOfflineRegions]'s neutral read-failure.
 */
fun AvailabilityViewModel.onDownloadOfflineMaps() {
    val state = _uiState.value
    val lat = state.offlineMapLatText.toDoubleOrNull()
    val lng = state.offlineMapLngText.toDoubleOrNull()
    if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
        _uiState.update {
            it.copy(
                offlineDownloadStatus = OfflineMapStatus.Failed(
                    "Enter a valid latitude (-90 to 90) and longitude (-180 to 180).",
                ),
            )
        }
        return
    }
    val region = Region(lat, lng, state.offlineMapRadiusKm)
    val name = state.offlineMapNameText.trim().ifBlank { "Region ${state.offlineRegions.size + 1}" }

    val tilesAlreadyUsed = state.offlineRegions.sumOf { it.tileCount }
    // Against the zoom the deployed source actually serves (SERVED_MAX_ZOOM, now equal to MAX_ZOOM;
    // the min inside is kept as the seam for their next divergence) — the same function the
    // panel's "~N tiles" label uses, so the gate and the label agree; see
    // OfflineMapRepository.SERVED_MAX_ZOOM (tile-estimate dispatch; two-data-corrections dispatch).
    val estimatedTiles = estimateServedOfflineTileCount(region)
    val remainingBudget = OfflineMapRepository.TILE_COUNT_LIMIT - tilesAlreadyUsed
    if (estimatedTiles > remainingBudget) {
        _uiState.update { it.withTileBudgetExceeded(estimatedTiles, remainingBudget) }
        return
    }

    _uiState.update { it.copy(offlineDownloadStatus = OfflineMapStatus.Downloading(downloaded = 0, total = 0)) }
    offlineMaps.beginDownload(name, region)
}

/** A swipe on an offline region's Records row: see [AvailabilityOfflineRegionDeletes.requestDeleteOfflineRegion]. */
fun AvailabilityViewModel.requestDeleteOfflineRegion(id: Long) = offlineRegionDeletes.requestDeleteOfflineRegion(id)

/** The snackbar's Undo: see [AvailabilityOfflineRegionDeletes.undoDeleteOfflineRegion]. */
fun AvailabilityViewModel.undoDeleteOfflineRegion(id: Long) = offlineRegionDeletes.undoDeleteOfflineRegion(id)

/** The snackbar ended without Undo: see [AvailabilityOfflineRegionDeletes.commitDeleteOfflineRegion]. */
fun AvailabilityViewModel.commitDeleteOfflineRegion(id: Long) = offlineRegionDeletes.commitDeleteOfflineRegion(id)

/** See [AvailabilityOfflineMaps.onDeleteOfflineRegion]. */
fun AvailabilityViewModel.onDeleteOfflineRegion(id: Long) = offlineMaps.onDeleteOfflineRegion(id)
