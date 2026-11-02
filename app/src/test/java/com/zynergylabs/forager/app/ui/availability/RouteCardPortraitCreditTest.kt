package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MAP_CREDIT_CAPTION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-10-30-08, D18 (RECORD -1958): in portrait the map's credit rises above the route card where their rectangles overlap at
 * the credit's resting place (D16's "meet"). D15 saw the card on the credit on the S22: by about 4 dp at font 1.0, and about 71 dp at
 * 2.0, the credit's text under the card's buttons. D13's rule was landscape only.
 *
 * The real Maps tab in the S22's portrait window, recording, with the S22's credit drawn where the map draws it (`RouteMapSlot`), the
 * card plotted to 1, 3 and 6 stops, peek and expanded, with the bar on either side. Each case asserts the drawn credit's bounds against
 * the card's, so a credit left under the card, or risen only part way, fails; and the credit back at rest once the card closes.
 */
abstract class RouteCardPortraitCreditTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    private fun overlaps(a: DpRect, b: DpRect): Boolean = a.left < b.right - 0.5.dp && b.left < a.right - 0.5.dp && a.top < b.bottom - 0.5.dp && b.top < a.bottom - 0.5.dp

    private fun walk(where: String, stops: List<Int> = listOf(1, 3, 6)) {
        val rest = rig.bounds(MAP_CREDIT_CAPTION_TAG)
        stops.forEach { n ->
            listOf(false, true).forEach { expanded ->
                rig.plotTo(n)
                rig.setExpanded(expanded)
                val at = "font ${composeRule.density.fontScale}, $where, $n stops, ${if (expanded) "expanded" else "peek"}"
                val card = rig.bounds(ROUTE_CARD_TAG)
                val credit = rig.bounds(MAP_CREDIT_CAPTION_TAG)
                val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
                println("D18 $at: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, credit ${credit.left.value}..${credit.right.value} x ${credit.top.value}..${credit.bottom.value} (rest ${rest.top.value}..${rest.bottom.value}), bar ${bar.left.value}..${bar.right.value} x ${bar.top.value}..${bar.bottom.value}")
                assertTrue("$at: precondition: at rest the credit $rest and the card $card overlap", overlaps(rest, card))
                assertTrue("$at: the credit $credit is clear of the card $card", !overlaps(credit, card))
            }
        }
        rig.setExpanded(false)
        rig.closeCard()
        rig.assertSame("$where: the card closed, the credit", rest, rig.bounds(MAP_CREDIT_CAPTION_TAG))
    }

    @Test
    fun `the bar on the right, the credit rises clear of the card at the left edge, and goes back when it closes`() {
        rig.setScreen()
        walk("the bar on the right")
    }

    @Test
    fun `the bar on the left, the credit rises clear of the card at the right edge, and goes back when it closes`() {
        rig.setScreen()
        rig.dragBarAcross(rightward = false)
        walk("the bar on the left")
    }
}

/** Font 1.0: D15 measured the card over the credit's top by about 4 dp on the S22 (Robolectric: 4 dp). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.0f)
class RouteCardPortraitCreditTest : RouteCardPortraitCreditTests()

/** Font 2.0: about 71 dp on the S22 (Robolectric: 68), the credit's text under the card's buttons. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
class RouteCardPortraitCreditFont20Test : RouteCardPortraitCreditTests()
