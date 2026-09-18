package com.zynergylabs.forager.app.ui.log

import androidx.activity.ComponentDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.robolectric.shadows.ShadowDialog

/**
 * System back, as the in-app camera dialog receives it: through the dialog's own
 * `OnBackPressedDispatcher`, where Compose's `Dialog` registers its `dismissOnBackPress` handling.
 * The Activity's dispatcher would be the wrong path: while the dialog is up, back goes to the
 * dialog's window, not the Activity's.
 *
 * Since 2026-09-18 this is the only way out of the camera a user has (Done was removed; the owner's
 * call is that the navigation bar serves the function), so the tests that used to tap Done go
 * through here.
 */
internal fun ComposeTestRule.pressBackOnCameraDialog() {
    val dialog = checkNotNull(ShadowDialog.getLatestDialog() as? ComponentDialog) {
        "no ComponentDialog showing: expected the in-app camera, got ${ShadowDialog.getLatestDialog()}"
    }
    runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}
