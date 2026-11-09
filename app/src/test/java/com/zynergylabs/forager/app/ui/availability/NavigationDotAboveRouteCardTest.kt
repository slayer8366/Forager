package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import com.zynergylabs.forager.app.ui.map.NavigationSideInsets
import com.zynergylabs.forager.app.ui.map.navigationViewPaddingPx
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
 * Dispatch 2026-11-06-01, Amendment 1, J8, with its answers (RECORD -2183). The owner, with the route card resting on the navigation
 * card: "(b) Above top card (Recommended)": the walker's dot is kept just above whichever card is on top, the route card's top when
 * it rests on the navigation card, otherwise the navigation card's; while the route card is expanded or elsewhere, D29's sideways move
 * as today.
 *
 * As `NavigationDotAboveCardTest` does, on the real Maps tab, recording and navigating in landscape, the camera being MapLibre's and a
 * stub here: what this reads is the padding the navigation view is asked for (`NavigationViewRequest.sideInsetsPx`, as the camera gets
 * it through [navigationViewPaddingPx]) and where that puts the walker's dot, the centre of the padded area. The route card's and the
 * navigation card's bounds are read off the screen, not from the code under test. Whether MapLibre then moves the camera so is
 * device-only.
 */
abstract class NavigationDotAboveRouteCardTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun insets(): NavigationSideInsets =
        rig.map.renderMode?.navigationView?.sideInsetsPx ?: throw AssertionError("navigating, so the map was asked for its navigation view")

    /** Where the camera puts the walker's dot for [side]: the centre of the padded area, read off the padding the camera is sent. */
    private fun dotFor(side: NavigationSideInsets): Pair<Dp, Dp> = with(composeRule.density) {
        val map = rig.bounds("map-slot")
        val widthPx = (map.right - map.left).toPx()
        val heightPx = (map.bottom - map.top).toPx()
        val (left, top, right, bottom) = navigationViewPaddingPx(heightPx.toInt(), side).toList()
        val x = map.left + (left + (widthPx - left - right) / 2).toFloat().toDp()
        val y = map.top + (top + (heightPx - top - bottom) / 2).toFloat().toDp()
        x to y
    }

    /** Whether [card] reaches into the dot's square (its clearance each way), by more than [slack]. */
    private fun covers(card: DpRect, dot: Pair<Dp, Dp>, slack: Dp = 0.dp): Boolean {
        val (x, y) = dot
        val r = WALKER_DOT_CLEARANCE - slack
        return card.left < x + r && card.right > x - r && card.top < y + r && card.bottom > y - r
    }

    private fun start() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.plotTo(2)
        rig.setExpanded(false)
    }

    /**
     * Where the route card does not rest on the navigation card, the dot is as the navigation card alone puts it (#500's J8): its
     * centre the clearance above that card's top where that card would cover it, else no bottom asked for. D29's sides as they are.
     */
    private fun assertAboveNavigationCardOnly(where: String) {
        val nav = rig.bounds(NAVIGATION_HUD_TAG)
        val side = insets()
        val before = dotFor(side.copy(bottomPx = 0.0))
        val now = dotFor(side)
        val navCovered = covers(nav, before)
        println("J8 route card, $where: navigation card $nav, side $side, dot before ${before.second.value} (x ${before.first.value}), now ${now.second.value}, covered by the navigation card before $navCovered, route cards ${rig.count(ROUTE_CARD_TAG)}")
        if (navCovered) {
            assertEquals("J8 route card, $where: the dot's centre sits its clearance above the navigation card's top ${nav.top}", (nav.top - WALKER_DOT_CLEARANCE).value, now.second.value, 0.5f)
        } else {
            assertEquals("J8 route card, $where: nothing is kept above, the navigation card clears the dot, so no bottom is asked for (side $side)", 0.0, side.bottomPx, 0.5)
        }
    }

    @Test
    fun `resting on the navigation card, the route card keeps the walker's dot its clearance above the route card's top`() {
        start()
        val route = rig.bounds(ROUTE_CARD_TAG)
        val nav = rig.bounds(NAVIGATION_HUD_TAG)
        val side = insets()
        val before = dotFor(side.copy(bottomPx = 0.0))
        val now = dotFor(side)
        println("J8 route card, resting: route card $route, navigation card $nav, side $side, dot before ${before.second.value} (x ${before.first.value}), now ${now.second.value} (x ${now.first.value})")
        assertTrue("precondition: the route card rests on the navigation card at its width (route card $route, navigation card $nav)", kotlin.math.abs((route.left - nav.left).value) < 0.5f && kotlin.math.abs((route.right - nav.right).value) < 0.5f && kotlin.math.abs((route.bottom - nav.top).value) < 0.5f)
        assertTrue("positive control: the route card resting there covers the walker's dot as the view stood (route card $route, dot at y ${before.second}, x ${before.first})", covers(route, before))
        assertTrue("J8 (b): the route card $route covers none of the walker's dot at y ${now.second}, x ${now.first}", !covers(route, now, slack = 0.5.dp))
        assertEquals("J8 (b): the dot's centre sits its clearance above the route card's top ${route.top} (navigation card's top ${nav.top})", (route.top - WALKER_DOT_CLEARANCE).value, now.second.value, 0.5f)
        assertEquals("J8: only up and down: the dot stays where it was across", before.first.value, now.first.value, 0.5f)
    }

    @Test
    fun `expanded, tucked and closed, the route card is not what the dot is kept above`() {
        start()
        rig.setExpanded(true)
        assertAboveNavigationCardOnly("expanded")
        rig.setExpanded(false)
        swipeCardTowardEdge()
        assertTrue("precondition: the card is tucked", rig.plotting.uiState.value.tucked)
        assertAboveNavigationCardOnly("tucked")
        rig.closeCard()
        assertEquals("precondition: the card has closed", 0, rig.count(ROUTE_CARD_TAG))
        assertAboveNavigationCardOnly("closed")
    }

    /** A real sideways swipe on the card toward its edge (the rail's side, on the navigation card), as RouteCardTuckCloseTest's. */
    private fun swipeCardTowardEdge() {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val screen = rig.screen()
        val rightward = screen.right - card.right < card.left - screen.left
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            val from = if (rightward) width * 0.2f else width * 0.8f
            val to = if (rightward) width * 0.8f else width * 0.2f
            swipe(Offset(from, y), Offset(to, y), durationMillis = 300)
        }
        rig.settle()
    }
}

/** The S22's landscape window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class NavigationDotAboveRouteCardAt823Test : NavigationDotAboveRouteCardTests()

/** The 780 x 360 window NavigationDotAboveCardTest reads too. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
class NavigationDotAboveRouteCardAt780Test : NavigationDotAboveRouteCardTests()
