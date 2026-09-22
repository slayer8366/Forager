package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * [LevelLine] against [FakeLevelProvider]: shown only at Grid + Level, its angle from the fake's
 * roll, the snapped state within a degree of level, and the sensor subscription present exactly
 * while the line is. Angle and snapped state are read from semantics: the line is drawn by a
 * `graphicsLayer` turn, which bounds cannot show reliably, and its colour is not in semantics.
 *
 * The line turns **opposite** to the phone: turned 12° clockwise, the real horizon appears 12°
 * anticlockwise on the screen, so the line's angle is -12 at `ROTATION_0`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraLevelLineTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val level = FakeLevelProvider(initial = 0f)
    private var mode by mutableStateOf(GridMode.GridLevel)

    private fun show(displayRotation: Int = Surface.ROTATION_0) = composeRule.setContent {
        Box(Modifier.size(300.dp, 450.dp)) { LevelLine(mode, level, displayRotation) }
    }

    private fun angle(): Float = composeRule.onNodeWithTag(CAMERA_LEVEL_TAG).fetchSemanticsNode().config[LevelLineAngle]
    private fun snapped(): Boolean = composeRule.onNodeWithTag(CAMERA_LEVEL_TAG).fetchSemanticsNode().config[LevelLineSnapped]

    private fun tilt(degrees: Float) {
        composeRule.runOnUiThread { level.tilt(degrees) }
        composeRule.waitForIdle()
    }

    @Test
    fun `only at Grid and Level, and the sensor is listened to only while the line is shown`() {
        mode = GridMode.Grid
        show()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(CAMERA_LEVEL_TAG).assertCountEquals(0)
        assertEquals("no line, no listener", 0, level.collectors)

        mode = GridMode.GridLevel
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(CAMERA_LEVEL_TAG).assertCountEquals(1)
        assertEquals("one listener while shown", 1, level.collectors)

        mode = GridMode.Off
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(CAMERA_LEVEL_TAG).assertCountEquals(0)
        assertEquals("and none once it goes", 0, level.collectors)
    }

    @Test
    fun `the line's angle follows the fake's roll, opposite to the phone`() {
        show()
        tilt(12f)
        assertEquals(-12f, angle(), 0.01f)
        tilt(-20f)
        assertEquals(20f, angle(), 0.01f)
    }

    @Test
    fun `with the window turned the same quarter as the phone, the line is flat`() {
        show(displayRotation = Surface.ROTATION_90)
        tilt(-90f) // turned a quarter anticlockwise: what ROTATION_90 is
        assertEquals(0f, angle(), 0.01f)
        tilt(-80f)
        assertEquals("ten degrees off it, the line shows ten", -10f, angle(), 0.01f)
    }

    @Test
    fun `snapped within a degree of level, not beyond it, in any of the four holds`() {
        show()
        tilt(0.5f); assertTrue("0.5 is level", snapped())
        tilt(1.5f); assertFalse("1.5 is not", snapped())
        tilt(-0.9f); assertTrue(snapped())
        tilt(90.5f); assertTrue("level in landscape", snapped())
        tilt(-91.5f); assertFalse(snapped())
    }

    @Test
    fun `no sensor, no line`() {
        show()
        tilt(3f)
        composeRule.onAllNodesWithTag(CAMERA_LEVEL_TAG).assertCountEquals(1)
        composeRule.runOnUiThread { level.tilt(null) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(CAMERA_LEVEL_TAG).assertCountEquals(0)
    }
}
