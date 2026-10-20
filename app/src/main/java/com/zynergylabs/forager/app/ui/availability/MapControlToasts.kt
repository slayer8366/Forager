package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapIconCluster.kt (dispatch 2026-10-19-18, UI split Phase 1, PR 10b).

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * The two one-shot Toasts the map's controls raise, shared by the phone's Maps tab and the tablet's map: a
 * locate-me that was refused or failed, and a start-recording that was refused. Moved out of `CompactMapTab`
 * unchanged (J6c).
 */
@Composable
internal fun MapControlToasts(locateMeStatus: LocateMeStatus, startRecordingErrorMessage: String?) {
    val context = LocalContext.current
    LaunchedEffect(locateMeStatus) {
        when (locateMeStatus) {
            LocateMeStatus.PermissionDenied ->
                Toast.makeText(context, "Location permission denied. Can't center on your position.", Toast.LENGTH_SHORT).show()
            LocateMeStatus.Unavailable ->
                Toast.makeText(context, "Couldn't determine your location.", Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }
    // Same one-shot-per-transition shape as the locateMeStatus effect above: a refused/failed
    // startRecording() is an event ("the action you just took didn't happen"), not a persistent
    // condition — the field only clears on the next successful startRecording() (see
    // TrackRecordingViewModel), so a banner would outlive the moment it's relevant.
    LaunchedEffect(startRecordingErrorMessage) {
        startRecordingErrorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
