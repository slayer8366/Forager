package com.zynergylabs.forager.app.photo.engine

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.photo.CameraEngineSeam
import com.zynergylabs.forager.app.photo.CameraXEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * The two ways the switch is reached (dispatch 2026-10-16-02: "through adb and a Settings debug row"), each through its
 * real entry point: the receiver's `onReceive` with the intent adb sends, and a click on the Diagnostics page's row. The
 * row's toggle is wiring, so a semantic click is the right touch here; nothing covers it on the page.
 *
 * The compose host is registered at runtime, as `DiagnosticsPanelTest` does it: the unit-test manifest carries no
 * ComponentActivity (app/build.gradle.kts, on ui-test-manifest). The first run of this class, over the stubs (CI run
 * 38897205517), failed every test on that missing activity instead of on the stub; this is the correction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraEngineTriggersTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun backToDefault() {
        CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_ENGINE, "camerax"))
        CameraEngineSwitch.forceFallback = false
    }

    @Test
    fun `adb chooses the prototype, then the forced fallback, then CameraX again`() {
        CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_ENGINE, "platform"))
        assertEquals(CameraEngineChoice.Platform, CameraEngineSwitch.choice)
        assertNotNull(CameraEngineSeam.factory)

        CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_FORCE_FALLBACK, true))
        assertTrue(CameraEngineSwitch.forceFallback)
        assertEquals("an extra on its own leaves the engine as it was", CameraEngineChoice.Platform, CameraEngineSwitch.choice)

        CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_ENGINE, "camerax"))
        assertEquals(CameraEngineChoice.CameraX, CameraEngineSwitch.choice)
        assertNull(CameraEngineSeam.factory)
        assertTrue(CameraEngineSeam.create(app) is CameraXEngine)
    }

    @Test
    fun `an engine name adb does not know changes nothing`() {
        CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_ENGINE, "Platfrom"))
        assertEquals(CameraEngineChoice.CameraX, CameraEngineSwitch.choice)
        assertNull(CameraEngineSeam.factory)
    }

    @Test
    fun `the burst and summary triggers with no platform camera open say so and change nothing`() {
        CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_BURST))
        CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_SUMMARY))
        assertEquals(CameraEngineChoice.CameraX, CameraEngineSwitch.choice)
        assertFalse(CameraEngineSwitch.forceFallback)
    }

    @Test
    fun `the Diagnostics row turns the prototype on and off`() {
        composeRule.setContent { CameraEngineRow() }

        composeRule.onNodeWithTag(CAMERA_ENGINE_ROW_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(CameraEngineChoice.Platform, CameraEngineSwitch.choice)
        assertNotNull(CameraEngineSeam.factory)

        composeRule.onNodeWithTag(CAMERA_ENGINE_ROW_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(CameraEngineChoice.CameraX, CameraEngineSwitch.choice)
        assertNull(CameraEngineSeam.factory)
    }
}
