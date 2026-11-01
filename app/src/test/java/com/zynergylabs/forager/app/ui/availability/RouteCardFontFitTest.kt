package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Amendment 14's stop condition: whether the card's lines fit the narrower width. Under native graphics, because Robolectric's default
 * text is far too narrow to judge a width (a 28-character line measured 10 dp). The buttons must stay on the card at every scale; a cut
 * line is asserted against only at font scale 1, and printed at the larger scales, for the owner.
 */
abstract class RouteCardFontFitTests(private val mustFit: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun report(what: String, tag: String) {
        val line = rig.line(tag) ?: throw AssertionError("$what is not on screen")
        println("FONT ${composeRule.density.fontScale} $what ${line.second}")
        if (mustFit) assertEquals("$what is whole: ${line.second}", false, line.first)
    }

    @Test
    fun `the card's lines and buttons`() {
        rig.setScreen()
        rig.plotTo(1)
        val scale = composeRule.density.fontScale
        val hintCard = rig.bounds(ROUTE_CARD_TAG)
        println("FONT $scale card ${hintCard.left.value}..${hintCard.right.value} x ${hintCard.top.value}..${hintCard.bottom.value}")
        report("hint", ROUTE_CARD_COUNT_TAG)
        rig.plotTo(4)
        report("count", ROUTE_CARD_ROUTED_COUNT_TAG)
        report("total", ROUTE_CARD_ROUTED_TOTAL_TAG)
        report("elevation", ROUTE_CARD_ROUTED_ELEVATION_TAG)
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("FONT $scale card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}")
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
class RouteCardFontFitPortraitOneTest : RouteCardFontFitTests(mustFit = true)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
class RouteCardFontFitPortraitTwoTest : RouteCardFontFitTests(mustFit = false)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class RouteCardFontFitLandscapeOneTest : RouteCardFontFitTests(mustFit = true)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardFontFitLandscapeTwoTest : RouteCardFontFitTests(mustFit = false)
