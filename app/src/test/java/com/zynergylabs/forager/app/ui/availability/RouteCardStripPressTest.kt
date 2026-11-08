package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpRect
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sorrel's review of PR #507, item 2.2, and the owner's answer (RECORD -2187): "Option 1, but remove the tap highlight since it spills
 * out the zone". The tucked card's strip shows 24 dp, and its touch area stays 48 dp from the edge at the card's height
 * (`RouteCardTuck.kt`, ROUTE_CARD_STRIP_TOUCH_WIDTH), so a press there must draw nothing past the strip: no press highlight.
 *
 * A real coordinate touch is pressed and held in the touch area's outer half, the clear map beside the strip; the screen beside the strip
 * (between the strip's inner edge and the touch area's) is read while it is held and compared pixel for pixel with the same screen read
 * before the press. Lifting the finger must then bring the card back, so the press is known to have landed in the touch area.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardStripPressTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    /** A real sideways swipe across the card toward its edge, so it tucks, as [RouteCardTabOutlineTests] does. */
    private fun tuck() {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val onRight = (rig.screen().right - card.right).value < 1f
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            swipe(Offset(if (onRight) width * 0.2f else width * 0.8f, y), Offset(if (onRight) width * 0.8f else width * 0.2f, y), durationMillis = 300)
        }
        rig.settle()
        assertTrue("precondition: the swipe tucked the card", rig.plotting.uiState.value.tucked)
    }

    private fun screenPixels(): ImageBitmap = composeRule.onRoot().captureToImage()

    /** The pixels, in px, between the strip's inner edge and the touch area's, at the card's height, less a pixel at each side. */
    private fun band(strip: DpRect, touch: DpRect): Pair<IntRange, IntRange> = with(composeRule.density) {
        val onRight = strip.left > touch.left
        val xs = if (onRight) (touch.left.toPx().toInt() + 1)..(strip.left.toPx().toInt() - 2) else (strip.right.toPx().toInt() + 1)..(touch.right.toPx().toInt() - 2)
        xs to (touch.top.toPx().toInt() + 1)..(touch.bottom.toPx().toInt() - 2)
    }

    private fun pressBesideTheStrip(dark: Boolean) {
        val name = if (dark) "night" else "day"
        val trip = rig.savedTrip("Creek loop", 2)
        rig.setScreen(trips = listOf(trip), dark = dark)
        rig.open(trip)
        tuck()
        val strip = rig.bounds(ROUTE_CARD_TAB_DRAWN_TAG)
        val touch = rig.bounds(ROUTE_CARD_TAB_TAG)
        assertTrue("precondition, $name: the touch area ($touch) reaches past the strip ($strip)", touch.right - touch.left > strip.right - strip.left)
        val (xs, ys) = band(strip, touch)
        assertFalse("precondition, $name: there is a band beside the strip to read ($xs x $ys)", xs.isEmpty() || ys.isEmpty())

        val before = screenPixels().toPixelMap()
        val onRight = strip.left > touch.left
        // The middle of the band: beside the strip, inside the touch area, halfway down the card.
        val at = with(composeRule.density) {
            Offset((((if (onRight) touch.left else strip.right) + (if (onRight) strip.left else touch.right)) / 2).toPx(), ((touch.top + touch.bottom) / 2).toPx())
        }
        composeRule.onRoot().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        val held = screenPixels().toPixelMap()
        val changed = mutableListOf<String>()
        var count = 0
        for (y in ys step 2) for (x in xs) {
            if (!held[x, y].near(before[x, y], tolerance = 2f / 255f)) {
                count++
                if (changed.size < 6) changed += "($x, $y) ${before[x, y].hex()} -> ${held[x, y].hex()}"
            }
        }
        composeRule.onRoot().performTouchInput { up() }
        rig.settle()
        println("MEASURED $name: band $xs x $ys beside the strip; $count pixels changed while pressed; first $changed")
        assertFalse("precondition, $name: lifting the finger brought the card back, so the press was in the touch area", rig.plotting.uiState.value.tucked)
        assertEquals("$name: a press held beside the tucked strip, in its touch area, draws nothing past the strip (no press highlight); changed: $changed", 0, count)
    }

    @Test
    fun `by day, a press held beside the tucked strip draws no highlight past the strip, and lifting brings the card back`() = pressBesideTheStrip(dark = false)

    @Test
    fun `at night, a press held beside the tucked strip draws no highlight past the strip, and lifting brings the card back`() = pressBesideTheStrip(dark = true)
}
