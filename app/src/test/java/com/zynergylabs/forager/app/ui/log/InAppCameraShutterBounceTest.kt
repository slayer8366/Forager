package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import com.zynergylabs.forager.app.ui.motion.PressBounceScaleKey
import com.zynergylabs.forager.app.ui.motion.ProvideReduceMotion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Amendment 2 to motion Part 1 (RECORD -659; the owner: "Shutter yes, rows no"): the camera's shutter disc dips while a real
 * finger is down on it and springs back, and stays still with the phone's animations off; the photo is taken either way. Driven
 * through [InAppCameraDialog], the shutter's real entry point, under the app's reduce-motion provider (as `ForagerTheme`
 * provides it), with the animator scale as its real source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppCameraShutterBounceTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val captured = mutableListOf<PhotoSource>()
    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun animationsBackOn() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun setDialog(session: FakeCameraCaptureSession) {
        composeRule.setContent {
            ProvideReduceMotion {
                InAppCameraDialog(
                    session = session,
                    cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                    onPhotoCaptured = { captured += it },
                    onDismiss = {},
                    gridMode = GridMode.Off,
                    onGridModeChanged = {},
                    autoSaveLocationToPhotos = true,
                    onAutoSaveLocationToPhotosChanged = {},
                    levelProvider = FakeLevelProvider(),
                    viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun scale(): Float =
        composeRule.onNode(SemanticsMatcher.keyIsDefined(PressBounceScaleKey) and hasAnyAncestor(hasTestTag(CAMERA_SHUTTER_TAG)), useUnmergedTree = true)
            .fetchSemanticsNode().config[PressBounceScaleKey]

    /** A real finger held on the shutter's centre for 300 ms, then lifted; returns the disc's scale while held. */
    private fun holdAndRelease(): Float {
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val held = scale()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        return held
    }

    @Test
    fun `the shutter dips while a finger is down, springs back, and takes the photo`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        assertEquals("at rest", 1f, scale(), 0.001f)
        val held = holdAndRelease()
        assertTrue("dipped while held: $held", held < 0.97f)
        assertEquals("back at rest", 1f, scale(), 0.001f)
        assertEquals("the photo was taken", 1, session.captureCalls)
    }

    @Test
    fun `with the phone's animations off the shutter does not dip and still takes the photo`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val session = FakeCameraCaptureSession()
        setDialog(session)
        assertEquals("no dip", 1f, holdAndRelease(), 0.001f)
        assertEquals("the photo was taken", 1, session.captureCalls)
    }
}
