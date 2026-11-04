package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 15 (RECORD -1929), the owner: "Have the card slide in from the opposite side of the map icon bar
 * in portrait". Read with the clock held part way: the card comes in from the edge opposite the bar, along the bottom, and leaves the
 * same way, taking no touches as it goes. Under reduced motion it does not slide, as the sliding navigation card does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardSlideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    /** The card's bounds after [millis] of a change [change] makes, the clock then held. */
    private fun partWay(millis: Long, change: () -> Unit): DpRect {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread(change)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(millis)
        return rig.bounds(ROUTE_CARD_TAG)
    }

    private fun letGo() {
        composeRule.mainClock.autoAdvance = true
        rig.settle()
    }

    private fun walk(barOnLeft: Boolean) {
        rig.setScreen()
        if (barOnLeft) rig.dragBarAcross(rightward = false)
        rig.plotTo(1)
        val rest = rig.bounds(ROUTE_CARD_TAG)
        composeRule.runOnUiThread { rig.plotting.onCancel() }
        rig.settle()
        assertEquals("precondition: the card closed", 0, rig.count(ROUTE_CARD_TAG))

        val coming = partWay(80) { rig.plotting.onStartPlotting(CREEK.stop()) }
        println("A15 barOnLeft $barOnLeft, coming in: ${coming.left.value}..${coming.right.value}, rest ${rest.left.value}..${rest.right.value}")
        if (barOnLeft) {
            assertTrue("it comes in from the right: ${coming.left} is right of ${rest.left}", coming.left > rest.left + 4.dp)
        } else {
            assertTrue("it comes in from the left: ${coming.left} is left of ${rest.left}", coming.left < rest.left - 4.dp)
        }
        assertEquals("along the bottom", rest.bottom.value, coming.bottom.value, 0.5f)
        letGo()
        rig.assertSame("it stops beside the bar", rest, rig.bounds(ROUTE_CARD_TAG))

        val going = partWay(80) { rig.plotting.onCancel() }
        println("A15 barOnLeft $barOnLeft, leaving: ${going.left.value}..${going.right.value}")
        if (barOnLeft) {
            assertTrue("it leaves to the right: ${going.left}", going.left > rest.left + 4.dp)
        } else {
            assertTrue("it leaves to the left: ${going.left}", going.left < rest.left - 4.dp)
        }
        assertEquals("along the bottom", rest.bottom.value, going.bottom.value, 0.5f)
        letGo()
        assertEquals("it has gone", 0, rig.count(ROUTE_CARD_TAG))
    }

    @Test
    fun `the bar on the right - the card comes in from the left and leaves to the left`() = walk(barOnLeft = false)

    @Test
    fun `the bar on the left - the card comes in from the right and leaves to the right`() = walk(barOnLeft = true)

    @Test
    fun `leaving, the card takes no touch - it reaches the map`() {
        rig.setScreen()
        rig.plotTo(1)
        val cancel = rig.bounds(ROUTE_CARD_CANCEL_TAG)
        val going = partWay(16) { rig.plotting.onCancel() }
        assertTrue("precondition: the card is still over the touch's place: $going", going.right > cancel.left + 8.dp)
        val tapsBefore = rig.map.taps
        val at = Offset(rig.dp(going.right - 4.dp), rig.dp(cancel.top + 4.dp))
        composeRule.onRoot().performTouchInput { click(at) }
        composeRule.mainClock.advanceTimeBy(400)
        assertEquals("the touch went through to the map", tapsBefore + 1, rig.map.taps)
        letGo()
    }

    @Test
    fun `under reduced motion the card does not slide - it is in its place part way through`() {
        rig.setScreen(reduceMotion = true)
        rig.plotTo(1)
        val rest = rig.bounds(ROUTE_CARD_TAG)
        composeRule.runOnUiThread { rig.plotting.onCancel() }
        rig.settle()
        val coming = partWay(32) { rig.plotting.onStartPlotting(CREEK.stop()) }
        rig.assertSame("coming in", rest, coming)
        letGo()
        val going = partWay(32) { rig.plotting.onCancel() }
        rig.assertSame("leaving", rest, going)
        letGo()
    }
}
