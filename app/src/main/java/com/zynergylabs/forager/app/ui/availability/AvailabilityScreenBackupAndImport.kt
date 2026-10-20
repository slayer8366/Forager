package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.importgpx.GpxImportNotice
import com.zynergylabs.forager.app.ui.backup.BackupControls

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s backup and GPX
 * import inputs, grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were six separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** Settings' Backup section, the requests a backup or restore sends the screen, and the GPX import's notice and picker, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenBackupAndImport(
    /**
     * Settings' Backup section (journal backup and restore, dispatch 2026-09-28-127): its state and callbacks.
     * Defaulted, so a caller with no backup still composes the section, inert.
     */
    val backup: BackupControls = BackupControls(),
    /** Counts up when the person taps Done on the restore page: go to the Maps tab and close the drawer. */
    val returnToMapRequest: Int = 0,
    /** Counts up when a backup notification is tapped: open the Backup section in Tools, then Settings. */
    val openBackupRequest: Int = 0,
    /**
     * Plan T16: a finished GPX import to show, from the in-app "Import GPX" or from Open with or Share.
     * The screen goes to Journal > Records > Tracks with the first new track's details open, and says the
     * outcome's message ([com.zynergylabs.forager.app.importgpx.gpxImportMessage]) as a Toast, then calls
     * [onGpxImportNoticeShown] with its seq. `null` (the default) is nothing pending.
     */
    val gpxImportNotice: GpxImportNotice? = null,
    val onGpxImportNoticeShown: (Long) -> Unit = {},
    /** Plan T16: Records > Tracks > "Import GPX", given the picked file. `null` (the default) shows no button. */
    val onGpxFilePicked: ((android.net.Uri) -> Unit)? = null,
)
