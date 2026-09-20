package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `Modifier.rotateWithDevice` measured: the square footprint, the content centred in it, and a
 * real turn to 90° leaving the centre exactly where it was with the bounding box turned with it.
 * `getBoundsInRoot` maps the whole box through the graphics layer's transform, which is also what
 * hit-testing uses, so "the touch target turns with the pixels" is the same fact. (The unclipped
 * variant maps only the origin and keeps the untransformed size, and reads a rotated node as a
 * translated one; that is how a first draft of this test failed.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class RotateWithDeviceModifierTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            org.robolectric.Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(android.content.ComponentName(app, androidx.activity.ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var setRotation: (Int?) -> Unit = {}

    private fun setContent(initial: Int? = null) {
        composeRule.setContent {
            var rotation by androidx.compose.runtime.remember { mutableStateOf(initial) }
            setRotation = { rotation = it }
            Box(Modifier.testTag("outer").rotateWithDevice(rotation)) {
                Box(Modifier.size(width = 100.dp, height = 20.dp).testTag("inner"))
            }
        }
    }

    @Test
    fun `the footprint is a square of the larger side, with the content centred in it`() {
        setContent()
        val outer = composeRule.onNodeWithTag("outer").getBoundsInRoot()
        val inner = composeRule.onNodeWithTag("inner").getBoundsInRoot()

        assertEquals(100.dp, outer.width)
        assertEquals(100.dp, outer.height)
        assertEquals(100.dp, inner.width)
        assertEquals(20.dp, inner.height)
        assertEquals("centred vertically", 40.dp, inner.top - outer.top)
        assertEquals("centred horizontally", 0.dp, inner.left - outer.left)
    }

    @Test
    fun `turning to 90 degrees keeps the centre and turns the bounds with the pixels`() {
        setContent()
        val before = composeRule.onNodeWithTag("inner").getBoundsInRoot()
        val centreBefore = (before.left + before.right) / 2 to (before.top + before.bottom) / 2

        setRotation(Surface.ROTATION_90)
        composeRule.waitForIdle() // the animation runs to completion on the test clock

        val after = composeRule.onNodeWithTag("inner").getBoundsInRoot()
        val centreAfter = (after.left + after.right) / 2 to (after.top + after.bottom) / 2
        assertEquals(centreBefore.first.value, centreAfter.first.value, 0.51f)
        assertEquals(centreBefore.second.value, centreAfter.second.value, 0.51f)
        assertEquals("a 100×20 turned a quarter is 20 wide", 20f, after.width.value, 0.51f)
        assertEquals("and 100 tall", 100f, after.height.value, 0.51f)
        val outer = composeRule.onNodeWithTag("outer").getBoundsInRoot()
        assertEquals("the footprint did not change", 100f, outer.width.value, 0.51f)
    }

    @Test
    fun `a control that starts turned is placed turned, with no animation from upright`() {
        setContent(initial = Surface.ROTATION_270)
        val inner = composeRule.onNodeWithTag("inner").getBoundsInRoot()
        assertEquals(20f, inner.width.value, 0.51f)
        assertEquals(100f, inner.height.value, 0.51f)
    }
}
