package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The window's requested orientation as far as Robolectric can see it: `Activity.requestedOrientation`
 * reads back what the dialog set while it is composed, and what was there before once it leaves.
 * Whether the window then actually turns with the device, and whether it turns without an
 * animation, are the device's and the emulator's; so is whether an OEM honours the request at all.
 * Also here: the count keeps its centre when the session reports the device turned, which is the
 * in-place half of the glyph rule, measured on the real dialog rather than on a bare box.
 *
 * Until 2026-09-19 this was `WindowOrientationLockTest` and the setting-off assertion was `LOCKED`;
 * the owner reversed the lock so the status bar can travel with the phone (`WindowOrientation.kt`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class WindowOrientationTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private var setShown: (Boolean) -> Unit = {}
    private val session = FakeCameraCaptureSession()

    private fun setDialog() {
        composeRule.setContent {
            var shown by remember { mutableStateOf(true) }
            setShown = { shown = it }
            if (shown) {
                InAppCameraDialog(
                    session = session,
                    cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                    onPhotoCaptured = {},
                    onDismiss = {},
                    gridMode = GridMode.Off,
                    onGridModeChanged = {},
                    autoSaveLocationToPhotos = true,
                    onAutoSaveLocationToPhotosChanged = {},
                    levelProvider = FakeLevelProvider(),
                    viewfinder = { modifier -> Box(modifier) },
                )
            }
        }
    }

    /**
     * RECORD -980 and -981 (the owner: "The camera is to never, ever, rotate like this. This is a cardinal sin"): the camera's
     * window is held portrait while it is open, always, with no setting. Was `setting off, the window follows the device but
     * never into reverse portrait, SENSOR, ...`, asserting SENSOR and not FULL_SENSOR; before dispatch 2026-10-13-03 the
     * setting-on test asserted this same PORTRAIT. The controls turning in place in that held window is
     * `the count keeps its centre when the device turns, and turns its bounds in place`, below.
     */
    @Test
    fun `the window is held portrait while the camera is open, and the previous request is put back after`() {
        // A non-default previous value, so "restored" is distinguishable from "reset to unspecified".
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        setDialog()

        assertEquals(
            "PORTRAIT: the camera's window never turns on a phone; not SENSOR, which turned it with the device",
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            composeRule.activity.requestedOrientation,
        )

        setShown(false)
        composeRule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, composeRule.activity.requestedOrientation)
    }

    @Test
    fun `with no Activity behind the context nothing is requested and nothing crashes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides app) { RequestWindowOrientation() }
        }
        composeRule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, composeRule.activity.requestedOrientation)
    }

    @Test
    fun `the count keeps its centre when the device turns, and turns its bounds in place`() {
        // A portrait window (the class's qualifiers) with the setting off: the portrait arrangement,
        // whose controls turn. The landscape arrangement's controls do not; InAppCameraDialogLandscapeTest.
        setDialog()
        val countBefore = composeRule.onNodeWithTag(CAMERA_COUNT_TAG).getBoundsInRoot()

        session.deviceRotation = Surface.ROTATION_90
        composeRule.waitForIdle()

        val countAfter = composeRule.onNodeWithTag(CAMERA_COUNT_TAG).getBoundsInRoot()
        for ((before, after) in listOf(countBefore to countAfter)) {
            assertEquals(((before.left + before.right) / 2).value, ((after.left + after.right) / 2).value, 0.51f)
            assertEquals(((before.top + before.bottom) / 2).value, ((after.top + after.bottom) / 2).value, 0.51f)
            assertEquals("a quarter turn swaps the extents", before.width.value, after.height.value, 0.51f)
            assertEquals(before.height.value, after.width.value, 0.51f)
        }
    }
}
