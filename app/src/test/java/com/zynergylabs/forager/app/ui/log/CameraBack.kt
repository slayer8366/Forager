package com.zynergylabs.forager.app.ui.log

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule

/**
 * Presses Back on the camera the way the navigation bar does: through the **Activity's** own
 * `OnBackPressedDispatcher`, which is what the camera's `BackHandler` registers with and what a
 * Back key or gesture reaches.
 *
 * Until 2026-09-19 this pressed a *dialog's* dispatcher, because the camera was a Compose `Dialog`
 * and therefore a `ComponentDialog` with a dispatcher of its own that the Activity's never saw. The
 * camera now draws in the Activity's own window (`CameraWindowChrome.kt`), so there is one
 * dispatcher and this is it. Not the `onDismiss` lambda called by hand, which would prove nothing
 * about Back — that distinction is the reason this helper exists and it is unchanged.
 */
internal fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.pressBackOnCamera() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}
