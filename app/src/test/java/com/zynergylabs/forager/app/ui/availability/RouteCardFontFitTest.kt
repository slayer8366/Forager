package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Amendment 16 (RECORD -1932), the owner: "Wrap to two lines (Recommended)". When a line can't fit in the room beside the bar it wraps;
 * nothing is cut with an ellipsis, and the distance at the right stays in place. Under native graphics, because Robolectric's default
 * text is far too narrow to judge a width (a 28-character line measured 10 dp). [wraps] is whether the count line must wrap: the case
 * Amendment 14's report gave the numbers for, "4 Waypoints routed" needing 227 dp of 199.3 dp at font scale 2.0.
 */
abstract class RouteCardFontFitTests(private val wraps: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun cardLine(what: String, tag: String) {
        val line = rig.line(tag) ?: throw AssertionError("$what is not on screen")
        println("FONT ${composeRule.density.fontScale} $what ${line.second}")
    }

    @Test
    fun `the card's lines are whole, the distance stays at the right, and the buttons stay on the card`() {
        rig.setScreen()
        rig.plotTo(1)
        val scale = composeRule.density.fontScale
        val hintCard = rig.bounds(ROUTE_CARD_TAG)
        println("FONT $scale card ${hintCard.left.value}..${hintCard.right.value} x ${hintCard.top.value}..${hintCard.bottom.value}")
        rig.assertWhole("font $scale, the hint", ROUTE_CARD_COUNT_TAG, "1 Waypoint selected, tap another to route")
        rig.plotTo(4)
        val lines = rig.assertWhole("font $scale, the count", ROUTE_CARD_ROUTED_COUNT_TAG, "4 Waypoints routed")
        assertEquals("font $scale: the count's lines", if (wraps) 2 else 1, lines)
        cardLine("total", ROUTE_CARD_ROUTED_TOTAL_TAG)
        cardLine("elevation", ROUTE_CARD_ROUTED_ELEVATION_TAG)
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("FONT $scale card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}")
        val total = rig.bounds(ROUTE_CARD_ROUTED_TOTAL_TAG)
        val count = rig.bounds(ROUTE_CARD_ROUTED_COUNT_TAG)
        assertTrue("font $scale: the distance ${total.left} sits at the count's right ${count.right}", total.left >= count.right)
        assertEquals("font $scale: the distance stays at the card's right", (card.right - 12.dp).value, total.right.value, 0.5f)
        listOf(ROUTE_CARD_UNDO_TAG, ROUTE_CARD_CANCEL_TAG, ROUTE_CARD_SAVE_TAG).forEach { tag ->
            val button = rig.bounds(tag)
            println("FONT $scale $tag ${button.left.value}..${button.right.value}")
            assertTrue("$tag is inside the card: $button, $card", button.left >= card.left && button.right <= card.right)
        }
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.0f)
class RouteCardFontFitPortraitOneTest : RouteCardFontFitTests(wraps = false)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
class RouteCardFontFitPortraitTwoTest : RouteCardFontFitTests(wraps = true)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class RouteCardFontFitLandscapeOneTest : RouteCardFontFitTests(wraps = false)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardFontFitLandscapeTwoTest : RouteCardFontFitTests(wraps = false)
