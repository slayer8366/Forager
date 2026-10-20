package com.zynergylabs.forager.app.ui.availability

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s offline maps callbacks,
 * grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 8 separate parameters of the screen; each field keeps that parameter's default, or stays required where
 * the parameter was required, and its doc comment, and the screen unpacks the bundle into locals with the old names at
 * the top of its body, so the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** The Offline Maps submenu's inputs: the picker's fields, open, download, delete and download again, for [AvailabilityScreen]. Fields without a default were required parameters and stay required; the rest keep their defaults. */
data class AvailabilityScreenOfflineMaps(
    /** Set by panning the picker map to the centre pin and confirming, in the Offline Maps submenu — see `OfflineMapsPanel`. */
    val onOfflineMapLatChanged: (String) -> Unit,
    val onOfflineMapLngChanged: (String) -> Unit,
    val onOfflineMapRadiusChanged: (Int) -> Unit,
    val onOfflineMapNameChanged: (String) -> Unit,
    val onOfflineMapsOpened: () -> Unit,
    val onDownloadOfflineMaps: () -> Unit,
    val onDeleteOfflineRegion: (Long) -> Unit,
    /** "Download again" on a restored offline region. */
    val onDownloadAgain: (Long) -> Unit = {},
)
