package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, D (dispatch 2026-10-19-40, RECORD -1484): the pop-up notices' triggers, moved out of AvailabilityScreen's
// body into one composable. The body calls it unconditionally, at the line where the first of them was and right after the
// two snackbar remembers, so every effect here launches in the same order, at the same point among the body's own effects.

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.zynergylabs.forager.app.domain.SETTINGS_RESET_ACTION_LABEL
import com.zynergylabs.forager.app.domain.SETTINGS_RESET_MESSAGE
import com.zynergylabs.forager.app.domain.WaypointNavigationTarget
import com.zynergylabs.forager.app.ui.backup.BackupControls
import com.zynergylabs.forager.app.ui.log.PendingDeleteNotice
import com.zynergylabs.forager.app.ui.log.PendingDeleteSnackbarEffects
import com.zynergylabs.forager.app.ui.track.RecordingNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The snackbars and prompts the screen raises in its one host. The keys and the blocks read the values passed in, which are
 * the values of the composition that launches each effect, as the body's own effects read them; the settings-reset
 * snackbar's "Settings" writes the request and the drawer flag through [state], when it is tapped.
 */
@Composable
internal fun AvailabilityScreenNotices(
    state: AvailabilityScreenState,
    logDraftSnackbarHostState: SnackbarHostState,
    logDraftSnackbarScope: CoroutineScope,
    settingsResetNoticePending: Boolean,
    onSettingsResetNoticeShown: () -> Unit,
    pendingDeleteNotices: List<PendingDeleteNotice>,
    backup: BackupControls,
    tripStartWarning: RecordingNotice?,
    backgroundRunPrompt: RecordingNotice?,
    networkFixesNotice: RecordingNotice?,
    waypointTarget: WaypointNavigationTarget,
    endWaypointNavigation: (Boolean) -> Unit,
) {
    // RECORD -660/-661 (the owner: "Snackbar with 'Settings' button"): a corrupt settings file was reset.
    // Cleared before it is shown, and shown on the host's own scope, as the backup launch notice below
    // is, so clearing it (which changes this effect's key) cannot cancel it. It stays until dismissed;
    // "Settings" opens Tools, then Settings, as a backup notification's tap does, without the Backup scroll.
    LaunchedEffect(settingsResetNoticePending) {
        if (!settingsResetNoticePending) return@LaunchedEffect
        onSettingsResetNoticeShown()
        logDraftSnackbarScope.launch {
            val result = logDraftSnackbarHostState.showSnackbar(
                message = SETTINGS_RESET_MESSAGE,
                actionLabel = SETTINGS_RESET_ACTION_LABEL,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                state.setOpenSettingsOnlyRequest(state.openSettingsOnlyRequest() + 1)
                state.setIsDrawerOpen(true)
            }
        }
    }
    // Journal redesign J4: the pending deletes' Undo snackbars share this host too.
    PendingDeleteSnackbarEffects(pendingDeleteNotices, logDraftSnackbarHostState)
    // A scheduled-backup notice that could not be a notification is shown here once, at launch (dispatch 2026-09-28-153,
    // item 1; owner "1 A"): the approved text through this same host, no action and no new surface. It is forgotten before it
    // is shown (showSnackbar suspends until it goes), so a launch that is interrupted does not show it twice.
    LaunchedEffect(backup.state.launchNotice) {
        val notice = backup.state.launchNotice ?: return@LaunchedEffect
        backup.onLaunchNoticeShown()
        // Launched on the host's own scope: forgetting the notice changes this effect's key and cancels it, which would
        // cancel a snackbar shown from inside it before it was ever drawn.
        logDraftSnackbarScope.launch { logDraftSnackbarHostState.showSnackbar(message = notice.text, duration = SnackbarDuration.Long) }
    }
    // Alert-delivery dispatch, Item 3: the trip-start audibility warning shares this host — a host
    // is a slot, not a message — rather than adding a second surface over the map. A foreground
    // moment by construction (the user just tapped record), so a composed effect is the right
    // place for it, unlike the off-track alert it warns about.
    LaunchedEffect(tripStartWarning?.id) {
        tripStartWarning?.let { warning ->
            logDraftSnackbarHostState.showSnackbar(message = warning.message, duration = SnackbarDuration.Long)
        }
    }
    val promptContext = LocalContext.current
    LaunchedEffect(backgroundRunPrompt?.id) {
        backgroundRunPrompt?.let { prompt ->
            val result = logDraftSnackbarHostState.showSnackbar(TappableNoticeVisuals(prompt.message))
            if (result == SnackbarResult.ActionPerformed) launchAppDetailsSettings(promptContext)
        }
    }
    LaunchedEffect(networkFixesNotice?.id) {
        networkFixesNotice?.let { notice ->
            logDraftSnackbarHostState.showSnackbar(message = notice.message, duration = SnackbarDuration.Long)
        }
    }
    // Dispatch -502, Amendment 1: the waypoint being navigated to was deleted, while navigating (its pending delete hides it
    // at once) or while the app was closed. The navigation ends and says so, in this same host, not a silent drop. Shown on
    // the host's own scope, as the backup notice is: ending the navigation changes this effect's key and would cancel a
    // snackbar shown from inside it.
    val waypointTargetGone = waypointTarget is WaypointNavigationTarget.Gone
    LaunchedEffect(waypointTargetGone) {
        if (waypointTargetGone) {
            endWaypointNavigation(false)
            logDraftSnackbarScope.launch { logDraftSnackbarHostState.showSnackbar(message = WAYPOINT_NAVIGATION_GONE_TEXT, duration = SnackbarDuration.Long) }
        }
    }
}
