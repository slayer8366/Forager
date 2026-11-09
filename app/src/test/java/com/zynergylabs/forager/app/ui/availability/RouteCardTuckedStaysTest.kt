package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-11-06-05, Amendment 6, A2, the owner's answers of 2026-11-08 (RECORD -2187): "Stays aside (Recommended)", whose option
 * read that while tucked the card keeps reporting its height, so the navigation card's evening line stays stepped aside and the strip
 * keeps its exact height and place, the line returning only when the card is closed; and "Stays under (Recommended)": in portrait
 * Re-center stays under the strip. This replaced Amendment 1 step 2's "Tucking gives the navigation card's evening line its room back".
 *
 * On the real Maps tab ([RouteCardBarRig]), tucked by a real sideways swipe on the card and brought back by a real tap on its strip.
 */
abstract class RouteCardTuckedStaysTests(clock: com.zynergylabs.forager.app.domain.CurrentTimeProvider = ROUTE_CARD_NOON_CLOCK) {

    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    internal val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT, clock = clock)

    protected fun tuck() {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val onRight = rig.screen().right - card.right < card.left - rig.screen().left
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            val from = if (onRight) width * 0.2f else width * 0.8f
            val to = if (onRight) width * 0.8f else width * 0.2f
            swipe(Offset(from, y), Offset(to, y), durationMillis = 300)
        }
        rig.settle()
        assertTrue("precondition: the swipe tucked the card", rig.plotting.uiState.value.tucked)
    }

    protected fun bringBack() {
        composeRule.onNodeWithTag(ROUTE_CARD_TAB_TAG).performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        rig.settle()
        assertTrue("precondition: the tap on the strip brought the card back", !rig.plotting.uiState.value.tucked)
    }
}

/**
 * The evening at font 1.5 in the S22's landscape window, where the open card makes the sundown line step aside (D42). Was font 2.0;
 * since dispatch 2026-11-06-01, Amendment 1, J3 step 3 and J6 (RECORD -2183, the owner: "Lines leave (as built)"), at 823 x 384 and
 * font 2.0 the card with its evening line can't fit even moved up, so the line is off before any route card opens (RECORD -955;
 * `RouteCardSundownStepsAsideFont20Test`: "sundown line shown 0, navigation card 266.0..384.0") and there is no line to step aside.
 * Font 1.5 is D42's positive control there (`RouteCardSundownStepsAsideFont15Test`: the line on the card with no route card, off with
 * it open), so the claim, tucked the line stays stepped aside, is read against a line that was there.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.5f)
class RouteCardTuckedStaysAsideTest : RouteCardTuckedStaysTests(clock = ROUTE_CARD_EVENING_CLOCK) {

    @Test
    fun `tucked on the navigation card, the evening line stays stepped aside and the strip keeps its height and place, until the card closes`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)
        assertEquals("precondition: with no card the sundown line shows", 1, rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG))
        rig.plotTo(3)
        assertEquals("precondition: the open card makes the sundown line step aside", 0, rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG))
        val card = rig.bounds(ROUTE_CARD_TAG)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        tuck()
        val strip = rig.bounds(ROUTE_CARD_TAB_DRAWN_TAG)
        println("TUCKED aside: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, strip ${strip.left.value}..${strip.right.value} x ${strip.top.value}..${strip.bottom.value}, navigation card ${rig.bounds(NAVIGATION_HUD_TAG).top.value}")
        assertEquals("tucked: the sundown line stays stepped aside", 0, rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG))
        rig.assertSame("tucked: the navigation card does not move", hud, rig.bounds(NAVIGATION_HUD_TAG))
        rig.assertSame("tucked: the card keeps its place and height", card, rig.bounds(ROUTE_CARD_TAG))
        assertEquals("tucked: the strip is the card's height", (card.bottom - card.top).value, (strip.bottom - strip.top).value, 0.5f)
        assertEquals("tucked: the strip's top is the card's", card.top.value, strip.top.value, 0.5f)
        assertEquals("tucked: the strip is at the card's edge, 24 dp wide", card.right.value, strip.right.value, 0.5f)
        assertEquals("tucked: the strip is 24 dp wide", 24f, (strip.right - strip.left).value, 0.5f)
        bringBack()
        assertEquals("back: the sundown line still stepped aside", 0, rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG))
        rig.closeCard()
        assertEquals("closed: the sundown line returns", 1, rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG))
    }
}

/** The S22's portrait window: navigating, Re-center under the card. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardTuckedStaysUnderTest : RouteCardTuckedStaysTests() {

    @Test
    fun `tucked in portrait, Re-center stays under the strip, which keeps the card's height and place`() {
        val trip = rig.savedTrip("Creek loop", 3)
        rig.setScreen(navigating = true, trips = listOf(trip))
        rig.open(trip)
        rig.leaveNavigationView()
        val card = rig.bounds(ROUTE_CARD_TAG)
        val reCenter = rig.bounds(RETURN_TO_ROUTE_TAG)
        assertTrue("precondition: Re-center under the card (${reCenter.top} vs ${card.bottom})", reCenter.top >= card.bottom)
        tuck()
        rig.leaveNavigationView()
        val strip = rig.bounds(ROUTE_CARD_TAB_DRAWN_TAG)
        println("TUCKED under: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, strip ${strip.left.value}..${strip.right.value} x ${strip.top.value}..${strip.bottom.value}, Re-center ${reCenter.top.value}..${reCenter.bottom.value}")
        rig.assertSame("tucked: Re-center does not move", reCenter, rig.bounds(RETURN_TO_ROUTE_TAG))
        rig.assertSame("tucked: the card keeps its place and height", card, rig.bounds(ROUTE_CARD_TAG))
        assertEquals("tucked: the strip is the card's height", (card.bottom - card.top).value, (strip.bottom - strip.top).value, 0.5f)
        assertEquals("tucked: the strip's bottom is the card's, over Re-center", card.bottom.value, strip.bottom.value, 0.5f)
        bringBack()
        rig.leaveNavigationView()
        rig.assertSame("back: Re-center does not move", reCenter, rig.bounds(RETURN_TO_ROUTE_TAG))
    }
}
