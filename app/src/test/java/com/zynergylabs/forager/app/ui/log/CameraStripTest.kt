package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
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
 * [CameraStrip] as a chip container. No chips composes nothing and takes no space — a production
 * state, on a camera with no flash unit. Chips give the strip its one control row, laid along the
 * edge as a row or a column, so "invisible when empty" is not "invisible always". The chips here
 * are stand-ins of a control's size; the real flash chip is tested in `CameraFlashChipTest` and in
 * the dialog tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraStripTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    @Test
    fun `a strip with no chips composes nothing and reserves no band`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().testTag("frame")) {
                CameraStrip(edge = ScreenEdge.Top, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0, chips = emptyList())
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(CAMERA_STRIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `a strip with a chip is one control row deep along its edge`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().testTag("frame")) {
                CameraStrip(edge = ScreenEdge.Top, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0, chips = listOf(standIn("a")))
            }
        }
        composeRule.waitForIdle()

        val frame = composeRule.onNodeWithTag("frame").getBoundsInRoot()
        val strip = composeRule.onNodeWithTag(CAMERA_STRIP_TAG).getBoundsInRoot()
        assertEquals(STRIP_ROW_HEIGHT.value, strip.height.value, 0.51f)
        assertEquals(frame.width.value, strip.width.value, 0.51f)
        assertEquals(frame.top.value, strip.top.value, 0.51f)
    }

    @Test
    fun `along a vertical edge the chips are a column, in order, one row wide`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().testTag("frame")) {
                CameraStrip(edge = ScreenEdge.Left, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0, chips = listOf(standIn("a"), standIn("b")))
            }
        }
        composeRule.waitForIdle()

        val frame = composeRule.onNodeWithTag("frame").getBoundsInRoot()
        val strip = composeRule.onNodeWithTag(CAMERA_STRIP_TAG).getBoundsInRoot()
        val a = composeRule.onNodeWithTag("a").getBoundsInRoot()
        val b = composeRule.onNodeWithTag("b").getBoundsInRoot()
        assertEquals("one control row wide", STRIP_ROW_HEIGHT.value, strip.width.value, 0.51f)
        assertEquals("full height", frame.height.value, strip.height.value, 0.51f)
        assertEquals("flush with the left edge", frame.left.value, strip.left.value, 0.51f)
        assertEquals("a column: one above the other", a.left.value, b.left.value, 0.51f)
        assertTrue("in order, a first: ${a.bottom} vs ${b.top}", a.bottom <= b.top)
    }

    private fun standIn(tag: String): StripChip = { _, _ -> Box(Modifier.size(STRIP_ROW_HEIGHT).testTag(tag)) }
}
