package com.zynergylabs.forager.app.photo

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
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
 * The flash part of [CameraCaptureSession], as the fake every screen test uses carries it. The
 * chip reads its glyph from the session, so the fake has to report what it was told and a change
 * has to recompose what reads it; a plain `var` would pass the first check and fail the second on
 * the screen, silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraFlashSessionTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    @Test
    fun `the fake reports the flash mode it was told, and a composition reading it follows`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Text(session.flashMode.name, modifier = Modifier.testTag("mode")) }
        composeRule.runOnUiThread { session.open(composeRule.activity) }
        composeRule.onNodeWithTag("mode").assertTextEquals("Off")

        composeRule.runOnUiThread { session.setFlashMode(FlashMode.Torch) }
        composeRule.waitForIdle()

        assertEquals("the fake reports what it was told", FlashMode.Torch, session.flashMode)
        composeRule.onNodeWithTag("mode").assertTextEquals("Torch")

        composeRule.runOnUiThread { session.setFlashMode(FlashMode.Off) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mode").assertTextEquals("Off")
    }

    @Test
    fun `the fake has no flash unit until open, reports it once opened, and close resets the mode`() {
        val session = FakeCameraCaptureSession()
        assertEquals("no unit before open, as the interface says", false, session.hasFlashUnit)

        composeRule.runOnUiThread { session.open(composeRule.activity) }
        assertEquals("the bound camera's unit is reported once open", true, session.hasFlashUnit)

        composeRule.runOnUiThread { session.setFlashMode(FlashMode.Torch) }
        assertEquals(FlashMode.Torch, session.flashMode)

        composeRule.runOnUiThread { session.close() }
        assertEquals("torch does not persist past close", FlashMode.Off, session.flashMode)
        assertEquals("and the unit goes with the camera", false, session.hasFlashUnit)
    }

    @Test
    fun `every setFlashMode is counted, and a camera with no flash unit stays Off`() {
        val session = FakeCameraCaptureSession(flashUnitOnOpen = false)
        composeRule.runOnUiThread { session.open(composeRule.activity) }

        composeRule.runOnUiThread { session.setFlashMode(FlashMode.Torch) }

        assertEquals("the request was made", 1, session.setFlashModeCalls)
        assertEquals("no unit to be lit", false, session.hasFlashUnit)
        assertEquals("and no mode the hardware cannot be in", FlashMode.Off, session.flashMode)
    }
}
