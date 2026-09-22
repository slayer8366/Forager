package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
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
 * [GridOverlay] over a preview of a known size, 300 by 450 dp: nothing at Off, and at Grid (and
 * Grid + Level) two vertical and two horizontal lines through the thirds, each spanning the whole
 * preview. Positions are the lines' centres, from their own bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class CameraGridOverlayTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun preview(mode: GridMode) = composeRule.setContent {
        Box(Modifier.size(300.dp, 450.dp).testTag("preview")) { GridOverlay(mode) }
    }

    @Test
    fun `at Off nothing is drawn`() {
        preview(GridMode.Off)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(CAMERA_GRID_TAG).assertCountEquals(0)
        for (tag in listOf(gridLineTag('v', 1), gridLineTag('v', 2), gridLineTag('h', 1), gridLineTag('h', 2))) {
            composeRule.onAllNodesWithTag(tag).assertCountEquals(0)
        }
    }

    @Test
    fun `at Grid four lines divide the preview into thirds, each spanning it`() = assertThirds(GridMode.Grid)

    @Test
    fun `at Grid and Level the same grid is drawn`() = assertThirds(GridMode.GridLevel)

    private fun assertThirds(mode: GridMode) {
        preview(mode)
        composeRule.waitForIdle()
        val p = composeRule.onNodeWithTag("preview").getBoundsInRoot()
        fun b(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot()

        val v1 = b(gridLineTag('v', 1)); val v2 = b(gridLineTag('v', 2))
        val h1 = b(gridLineTag('h', 1)); val h2 = b(gridLineTag('h', 2))
        assertEquals("first vertical at a third of the width", (p.left + (p.right - p.left) / 3).value, ((v1.left + v1.right) / 2).value, 0.51f)
        assertEquals("second vertical at two thirds", (p.left + (p.right - p.left) * 2 / 3).value, ((v2.left + v2.right) / 2).value, 0.51f)
        assertEquals("first horizontal at a third of the height", (p.top + (p.bottom - p.top) / 3).value, ((h1.top + h1.bottom) / 2).value, 0.51f)
        assertEquals("second horizontal at two thirds", (p.top + (p.bottom - p.top) * 2 / 3).value, ((h2.top + h2.bottom) / 2).value, 0.51f)
        assertEquals("a vertical spans the height", p.top.value, v1.top.value, 0.51f)
        assertEquals(p.bottom.value, v2.bottom.value, 0.51f)
        assertEquals("a horizontal spans the width", p.left.value, h1.left.value, 0.51f)
        assertEquals(p.right.value, h2.right.value, 0.51f)
        assertEquals("a line is the line plus its outline wide", (GRID_LINE_WIDTH + GRID_LINE_OUTLINE * 2).value, (v1.right - v1.left).value, 0.51f)
    }
}
