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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Amendment 14's stop condition: "a card line can't fit at the narrower width at the largest font scale tested on this card". This
 * prints each line's measure and the buttons' bounds, for the report.
 */
abstract class RouteCardFontScaleNumbers {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    @Test
    fun `the card's lines at this size and font scale`() {
        rig.setScreen()
        rig.plotTo(1)
        val scale = composeRule.density.fontScale
        println("FONT $scale card ${rig.bounds(ROUTE_CARD_TAG)}")
        println("FONT $scale hint ${rig.line(ROUTE_CARD_COUNT_TAG)}")
        rig.plotTo(4)
        println("FONT $scale count ${rig.line(ROUTE_CARD_ROUTED_COUNT_TAG)}")
        println("FONT $scale total ${rig.line(ROUTE_CARD_ROUTED_TOTAL_TAG)}")
        println("FONT $scale elevation ${rig.line(ROUTE_CARD_ROUTED_ELEVATION_TAG)}")
        listOf(ROUTE_CARD_UNDO_TAG, ROUTE_CARD_CANCEL_TAG, ROUTE_CARD_SAVE_TAG).forEach { println("FONT $scale $it ${rig.bounds(it)}") }
        println("FONT $scale card ${rig.bounds(ROUTE_CARD_TAG)}")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
class RouteCardFontScaleTwoTest : RouteCardFontScaleNumbers()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.0f)
class RouteCardFontScaleOneTest : RouteCardFontScaleNumbers()
