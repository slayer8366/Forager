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
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
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
 * The window lock as far as Robolectric can see it: `Activity.requestedOrientation` reads back
 * what the dialog set while it is composed, and what was there before once it leaves. Whether
 * the window then actually stops rotating is the device's; so is whether an OEM, or Android 16 on
 * a large screen, honours the request at all. Also here: the dialog's Done and count keep their
 * centres when the session reports the device turned, which is the in-place half of the same
 * requirement, measured on the real dialog rather than on a bare box.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class WindowOrientationLockTest {

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

    private fun setDialog(lockToPortrait: Boolean) {
        composeRule.setContent {
            var shown by remember { mutableStateOf(true) }
            setShown = { shown = it }
            if (shown) {
                InAppCameraDialog(
                    session = session,
                    cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                    lockToPortrait = lockToPortrait,
                    onPhotoCaptured = {},
                    onDismiss = {},
                    viewfinder = { modifier -> Box(modifier) },
                )
            }
        }
    }

    @Test
    fun `setting on, the window is forced portrait while the camera is open and the previous request is put back after`() {
        // A non-default previous value, so "restored" is distinguishable from "reset to unspecified".
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        setDialog(lockToPortrait = true)

        assertEquals("forced portrait is the point of the setting", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeRule.activity.requestedOrientation)

        setShown(false)
        composeRule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, composeRule.activity.requestedOrientation)
    }

    @Test
    fun `setting off, the window is pinned where it already is, LOCKED, and the previous request is put back after`() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        setDialog(lockToPortrait = false)

        assertEquals("nothing forced, so no flip: LOCKED keeps whatever the window was at open", ActivityInfo.SCREEN_ORIENTATION_LOCKED, composeRule.activity.requestedOrientation)

        setShown(false)
        composeRule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, composeRule.activity.requestedOrientation)
    }

    @Test
    fun `the lock value is a pure function of the setting`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, windowLockFor(lockToPortrait = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, windowLockFor(lockToPortrait = false))
    }

    @Test
    fun `with no Activity behind the context nothing is locked and nothing crashes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides app) { LockWindowOrientation(lockToPortrait = true) }
        }
        composeRule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, composeRule.activity.requestedOrientation)
    }

    @Test
    fun `Done and the count keep their centres when the device turns, and turn their bounds in place`() {
        // A portrait window (the class's qualifiers) with the setting off: the portrait arrangement,
        // whose controls turn. The landscape arrangement's controls do not; InAppCameraDialogLandscapeTest.
        setDialog(lockToPortrait = false)
        val doneBefore = composeRule.onNodeWithTag(CAMERA_DONE_TAG).getBoundsInRoot()
        val countBefore = composeRule.onNodeWithTag(CAMERA_COUNT_TAG).getBoundsInRoot()

        session.deviceRotation = Surface.ROTATION_90
        composeRule.waitForIdle()

        val doneAfter = composeRule.onNodeWithTag(CAMERA_DONE_TAG).getBoundsInRoot()
        val countAfter = composeRule.onNodeWithTag(CAMERA_COUNT_TAG).getBoundsInRoot()
        for ((before, after) in listOf(doneBefore to doneAfter, countBefore to countAfter)) {
            assertEquals(((before.left + before.right) / 2).value, ((after.left + after.right) / 2).value, 0.51f)
            assertEquals(((before.top + before.bottom) / 2).value, ((after.top + after.bottom) / 2).value, 0.51f)
            assertEquals("a quarter turn swaps the extents", before.width.value, after.height.value, 0.51f)
            assertEquals(before.height.value, after.width.value, 0.51f)
        }
    }
}
