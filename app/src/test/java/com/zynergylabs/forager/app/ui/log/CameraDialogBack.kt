package com.zynergylabs.forager.app.ui.log

import androidx.activity.ComponentDialog
import org.robolectric.shadows.ShadowDialog

/**
 * Presses Back on the camera dialog the way the navigation bar does: through the dialog's own
 * `OnBackPressedDispatcher`, which is what a Compose `Dialog` (a `ComponentDialog`) routes a Back
 * key or gesture to, and which fires `onDismissRequest`. Not the Activity's dispatcher, which a
 * dialog window never sees, and not the `onDismiss` lambda called by hand, which would prove
 * nothing about Back. Done was removed on 2026-09-18 and this is its replacement in every test
 * that used to tap it.
 */
internal fun pressBackOnCameraDialog() {
    val dialog = ShadowDialog.getLatestDialog() as? ComponentDialog
        ?: error("no camera dialog is showing, or it is not a ComponentDialog: ${ShadowDialog.getLatestDialog()}")
    dialog.onBackPressedDispatcher.onBackPressed()
}
