package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
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
 * bar. Amendment 3 (RECORD -2187) drew it on the top and the side toward the map only; Amendment 5 (the owner: "Plain line all the way
 * round, like a bubble") draws the map chrome's line, Bark at 40% by day and white at 40% at night (`RouteCardOutlineReading.kt`), on
 * the top and both sides, the side against the side bar too. Read from drawn pixels on the real Maps tab, recording and navigating, a
 * route plotted. Where it meets the navigation card is [RouteCardMeetsNavigationCardTest]'s (Amendment 6, A1).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardOutlineOnNavigationCardTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun assertOutline(dark: Boolean, rotation: Int) {
        val name = "${if (dark) "night" else "day"}, rotation $rotation"
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = true, dark = dark)
        rig.plotTo(2)
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
        val sides = listOf("top" to column.take(n), "side toward the map" to mapSide, "side against the side bar" to barSide)
        val off = sides.mapNotNull { (side, edge) ->
            println("MEASURED $name, the card's $side: ${edge.take(6).map { it.hex() }}")
            whyNotRuledLine(edge, dark, px)?.let { "$side: $it" }
        }
        assertTrue("$name, the route card on the navigation card carries the ruled line on its top and both sides; off: $off", off.isEmpty())
    }

    @Test
    fun `by day in landscape navigation, the ruled outline is on the top and both sides, the side bar's too`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `at night in landscape navigation, the ruled outline is on the top and both sides, the side bar's too`() {
        assertOutline(dark = true, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `turned the other way by day, the side bar's side carries the ruled outline too`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_270)
    }
}
