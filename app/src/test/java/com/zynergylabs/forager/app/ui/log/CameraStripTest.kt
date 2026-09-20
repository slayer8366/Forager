package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [CameraStrip]'s empty case: no content composes nothing and takes no space — a production state
 * now that Done is gone, reached by gating the placeholder off. Also that content gives the strip
 * its one control row, so "invisible when empty" is not "invisible always".
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
    fun `an empty strip composes nothing and reserves no band`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().testTag("frame")) {
                CameraStrip(edge = ScreenEdge.Top, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0, content = null)
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(CAMERA_STRIP_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(CAMERA_STRIP_PLACEHOLDER_TAG).assertCountEquals(0)
    }

    @Test
    fun `a strip with content is one control row deep along its edge`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().testTag("frame")) {
                CameraStrip(edge = ScreenEdge.Top, deviceRotation = null, displayRotation = android.view.Surface.ROTATION_0, content = { e, r, d -> StripPlaceholder(e, r, d) })
            }
        }
        composeRule.waitForIdle()

        val frame = composeRule.onNodeWithTag("frame").getBoundsInRoot()
        val strip = composeRule.onNodeWithTag(CAMERA_STRIP_TAG).getBoundsInRoot()
        assertEquals(STRIP_ROW_HEIGHT.value, strip.height.value, 0.51f)
        assertEquals(frame.width.value, strip.width.value, 0.51f)
        assertEquals(frame.top.value, strip.top.value, 0.51f)
    }
}
