package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FlashMode
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
 * [FlashChip] on its own, against the fake session: what a tap asks for, when the chip is there at
 * all, and where its glyph comes from. The clicks here are semantic and assert the **wiring** only
 * (CLAUDE.md); whether a finger on the strip reaches the chip over the viewfinder is a coordinate
 * test in `InAppCameraDialogTest`, once the chip is placed there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraFlashChipTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun openedSession(flashUnit: Boolean = true) =
        FakeCameraCaptureSession(flashUnitOnOpen = flashUnit).also { s -> composeRule.runOnUiThread { s.open(composeRule.activity) } }

    /**
     * Renamed and extended 2026-09-26 (decision B8, planner ruling 2): the cycle is Off, Auto, On,
     * Torch, Off, so four taps, each asserting the mode it lands on and the glyph the chip then
     * shows. Was `a tap asks the session for the next mode, Off to Torch to Off`, two taps.
     */
    @Test
    fun `a tap asks the session for the next mode, Off to Auto to On to Torch to Off`() {
        val session = openedSession()
        composeRule.setContent { FlashChip(session, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0) }

        val expected = listOf(
            FlashMode.Auto to FLASH_AUTO_LABEL,
            FlashMode.On to FLASH_ON_LABEL,
            FlashMode.Torch to TORCH_ON_LABEL,
            FlashMode.Off to FLASH_OFF_LABEL,
        )
        expected.forEachIndexed { i, (mode, label) ->
            composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).performClick()
            composeRule.waitForIdle()
            assertEquals("tap ${i + 1} is one request", i + 1, session.setFlashModeCalls)
            assertEquals("tap ${i + 1} lands on $mode", mode, session.flashMode)
            composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).assertContentDescriptionEquals(label)
        }
    }

    @Test
    fun `each mode has its own glyph and label`() {
        assertEquals(FlashGlyph(Icons.Filled.FlashOff, "Flash off"), flashGlyph(FlashMode.Off))
        assertEquals(FlashGlyph(Icons.Filled.FlashAuto, "Flash auto"), flashGlyph(FlashMode.Auto))
        assertEquals(FlashGlyph(Icons.Filled.FlashOn, "Flash on"), flashGlyph(FlashMode.On))
        assertEquals(FlashGlyph(Icons.Filled.FlashlightOn, "Torch on"), flashGlyph(FlashMode.Torch))
    }

    @Test
    fun `no flash unit, no chip`() {
        val session = openedSession(flashUnit = false)
        composeRule.setContent { FlashChip(session, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(CAMERA_FLASH_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `the chip goes when the unit does`() {
        val session = openedSession()
        composeRule.setContent { FlashChip(session, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0) }
        composeRule.onAllNodesWithTag(CAMERA_FLASH_CHIP_TAG).assertCountEquals(1)

        composeRule.runOnUiThread { session.hasFlashUnit = false }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(CAMERA_FLASH_CHIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `the glyph follows the session's mode, not a copy the chip keeps`() {
        val session = openedSession()
        composeRule.setContent { FlashChip(session, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0) }
        composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG, useUnmergedTree = false).assertContentDescriptionEquals(FLASH_OFF_LABEL)

        // Changed on the session, not through the chip: only a chip reading the session can follow.
        composeRule.runOnUiThread { session.setFlashMode(FlashMode.Torch) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).assertContentDescriptionEquals(TORCH_ON_LABEL)
    }
}
