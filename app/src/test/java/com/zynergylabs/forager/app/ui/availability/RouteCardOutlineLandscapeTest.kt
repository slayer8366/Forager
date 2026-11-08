package com.zynergylabs.forager.app.ui.availability

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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-05, Amendment 5 (RECORD -2187; the owner: "Plain line all the way round, like a bubble"): in landscape, not
 * navigating, the route card rises from the screen's bottom edge, and its bottom, flush to that edge, carries the map chrome's line as
 * its top and sides do (before Amendment 5 the bottom was left open). Bark at 40% by day, white at 40% at night
 * (`RouteCardOutlineReading.kt`), read from drawn pixels on the real Maps tab, recording, a route plotted. Portrait is
 * [RouteCardTuckCloseTest]'s, resting on the navigation card [RouteCardOutlineOnNavigationCardTest]'s.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardOutlineLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun assertAllRound(dark: Boolean) {
        val name = if (dark) "night" else "day"
        rig.setScreen(dark = dark)
        rig.plotTo(2)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val screen = rig.screen()
        println("MEASURED $name: the card's bottom at ${card.bottom}, the screen's at ${screen.bottom}")
        val pixels = composeRule.onNodeWithTag(ROUTE_CARD_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val px = composeRule.density.density
        val n = with(composeRule.density) { 8.dp.roundToPx() }
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2] }
        val column = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        val sides = listOf(
            "top" to column.take(n),
            "bottom" to column.takeLast(n).reversed(),
            "left side" to row.take(n),
            "right side" to row.takeLast(n).reversed(),
        )
        val off = sides.mapNotNull { (side, edge) ->
            println("MEASURED $name, the card's $side: ${edge.take(6).map { it.hex() }}")
            whyNotRuledLine(edge, dark, px)?.let { "$side: $it" }
        }
        assertTrue("$name, the landscape route card carries the ruled line on every side, its bottom on the screen's edge too; off: $off", off.isEmpty())
    }

    @Test
    fun `by day in landscape, the route card's bottom on the screen edge carries the ruled outline, as its other sides do`() = assertAllRound(dark = false)

    @Test
    fun `at night in landscape, the route card's bottom on the screen edge carries the ruled outline, as its other sides do`() = assertAllRound(dark = true)
}
