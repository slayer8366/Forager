package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The S22's own landscape figures (docs/audits/2026-11-01-s22-build-449-454.md on tools/s22-combined): 3.75 px per dp, screen 3088 x
 * 1440 px, the pill at [130,1179][490,1359] px, the card's right edge at 2554 px (the rail's edge). Robolectric has no cut-out, so the
 * L sits further left here than on the phone; this reads the card's width from the real tab and sets it against the phone's pill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardPhoneFiguresTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    @Test
    fun `on the phone's figures the card's left clears the pill's right`() {
        rig.setScreen()
        rig.plotTo(3)
        rig.setExpanded(true)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val widthPx = (card.right - card.left).value * 3.75f
        val cardLeftPx = 2554f - widthPx
        println("A14 phone: card ${(card.right - card.left).value} dp wide, its left at $cardLeftPx px against the pill's right at 490 px")
        assertTrue("the card's left $cardLeftPx px clears the pill's right 490 px", cardLeftPx >= 490f)
    }
}
