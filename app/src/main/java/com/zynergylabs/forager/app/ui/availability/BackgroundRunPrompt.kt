package com.zynergylabs.forager.app.ui.availability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals

/**
 * A Snackbar the whole of which is the tap target, with no action button: the off-track reminder's
 * background prompt (dispatch 2026-09-28-626, plan T14). The owner's step path is "one prompt >
 * tap > the phone's battery setting for Forager", and the owner gave the prompt's words only, so it
 * carries no button label of its own. The map's Snackbar host makes a Snackbar with these visuals
 * clickable across its full bounds, and a tap is the Snackbar's action.
 */
internal class TappableNoticeVisuals(override val message: String) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

/**
 * Forager's own App info page, where Battery is one tap away (Amendment 1 to dispatch -626, RECORD
 * -627: "App info page"). `ACTION_APPLICATION_DETAILS_SETTINGS` needs no permission; the restricted
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` route is not used. No new-task flag: opened from the
 * Activity, so Back returns to the recording, as the app's other outside screens do.
 */
internal fun appDetailsSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Opens [appDetailsSettingsIntent] for this app. A phone with no such page is logged, not crashed on. */
internal fun launchAppDetailsSettings(context: Context) {
    try {
        context.startActivity(appDetailsSettingsIntent(context.packageName))
    } catch (e: ActivityNotFoundException) {
        Log.w(BACKGROUND_RUN_PROMPT_TAG, "This phone has no App info page to open; the background prompt did nothing.", e)
    }
}

private const val BACKGROUND_RUN_PROMPT_TAG = "BackgroundRunPrompt"
