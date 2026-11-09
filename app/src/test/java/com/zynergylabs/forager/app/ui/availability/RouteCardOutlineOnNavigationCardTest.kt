package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.hex
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
 * Dispatch 2026-11-06-05, the route card's outline in landscape navigation, where the card rests on the navigation card beside the side
 * bar. Amendment 5 (RECORD -2187; the owner: "Plain line all the way round, like a bubble") draws the map chrome's line, Bark at 40% by
 * day and white at 40% at night (`RouteCardOutlineReading.kt`), on its top and its side toward the map. Its side against the side bar
 * draws none while the side bar shows: RECORD -967 (the owner, 2026-10-14): "Where two outlined pieces touch, one shared 1 dp line is
 * drawn: ... strip or card against the rail", and the owner again on 2026-11-08 of that corner: "It has never been any other way". The
 * side bar's own line is the shared one, as it is for the navigation card under it (`landscapeColumnOutlineSides`). Until Sorrel's
 * review of PR #507 (item 1.1) these cases claimed the ruled line on the side against the side bar too, a second line beside the side
 * bar's. In fullscreen the side bar has slid away and the card follows the navigation card's rule: lined where a system bar is left
 * between it and the screen's edge, bare where nothing is (Robolectric's zero insets).
 *
 * Read from drawn pixels on the real Maps tab, recording and navigating, a route plotted. Where it meets the navigation card is
 * [RouteCardMeetsNavigationCardTest]'s (Amendment 6, A1).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardOutlineOnNavigationCardTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    /**
     * [railSideLined]: whether the side against the side bar (or, in [fullscreen], where it was) is expected to carry the ruled line;
     * the top and the side toward the map always do. [railInset]: a navigation bar delivered on the side bar's side, as the S22 has.
     */
    private fun assertOutline(dark: Boolean, rotation: Int, fullscreen: Boolean = false, railInset: Boolean = false, railSideLined: Boolean = false) {
        val name = "${if (dark) "night" else "day"}, rotation $rotation${if (fullscreen) ", fullscreen" else ""}${if (railInset) ", a 48 dp navigation bar on the side bar's side" else ""}"
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = true, dark = dark)
        if (railInset) rig.applyInsets(left = 25.dp, right = 48.dp)
        rig.plotTo(2)
        if (fullscreen) {
            composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(center) }
            rig.settle()
            assertEquals("precondition, $name: fullscreen is on", 1, rig.describedCount("Exit fullscreen"))
        }
        val card = rig.bounds(ROUTE_CARD_TAG)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        val screen = rig.screen()
        assertTrue("$name, precondition: the card rests on the navigation card at its width (${card.left}..${card.right} vs ${hud.left}..${hud.right})", kotlin.math.abs((card.left - hud.left).value) < 0.5f && kotlin.math.abs((card.right - hud.right).value) < 0.5f)
        // The card's own side is the side bar's (RouteCardPlacement.kt, routeCardOnLeft): the nearer screen edge.
        val onRight = screen.right - card.right < card.left - screen.left
        val pixels = composeRule.onNodeWithTag(ROUTE_CARD_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val px = composeRule.density.density
        val n = with(composeRule.density) { 8.dp.roundToPx() }
        // Read below the top corners' rounding, a third of the way down.
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 3] }
        val column = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        val mapSide = if (onRight) row.take(n) else row.takeLast(n).reversed()
        val barSide = if (onRight) row.takeLast(n).reversed() else row.take(n)
        val lined = listOf("top" to column.take(n), "side toward the map" to mapSide) + if (railSideLined) listOf("side by the side bar's place" to barSide) else emptyList()
        val off = lined.mapNotNull { (side, edge) ->
            println("MEASURED $name, the card's $side: ${edge.take(6).map { it.hex() }}")
            whyNotRuledLine(edge, dark, px)?.let { "$side, not lined: $it" }
        } + if (railSideLined) {
            emptyList()
        } else {
            println("MEASURED $name, the card's side against the side bar: ${barSide.take(6).map { it.hex() }}")
            listOfNotNull(whyNotBare(barSide, px)?.let { "side against the side bar, not bare (a second line beside the side bar's): $it" })
        }
        val expected = if (railSideLined) "the ruled line on its top and both sides" else "the ruled line on its top and its side toward the map, and none of its own against the side bar"
        assertTrue("$name, the route card on the navigation card carries $expected; off: $off", off.isEmpty())
    }

    @Test
    fun `by day in landscape navigation, the ruled outline is on the top and the map side, and the side against the side bar has none of its own`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `at night in landscape navigation, the ruled outline is on the top and the map side, and the side against the side bar has none of its own`() {
        assertOutline(dark = true, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `turned the other way by day, the side against the side bar has no line of its own`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_270)
    }

    @Test
    fun `turned the other way at night, the side against the side bar has no line of its own`() {
        assertOutline(dark = true, rotation = Surface.ROTATION_270)
    }

    @Test
    fun `in fullscreen with a navigation bar on that side, the side bar gone, the card's side there carries the ruled line, as the navigation card's does`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_90, fullscreen = true, railInset = true, railSideLined = true)
    }

    @Test
    fun `in fullscreen at night with a navigation bar on that side, the card's side where the side bar was carries the ruled line`() {
        assertOutline(dark = true, rotation = Surface.ROTATION_90, fullscreen = true, railInset = true, railSideLined = true)
    }

    @Test
    fun `in fullscreen with nothing left between it and the screen's edge, the card's side there is bare, as the navigation card's is`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_90, fullscreen = true, railSideLined = false)
    }
}
