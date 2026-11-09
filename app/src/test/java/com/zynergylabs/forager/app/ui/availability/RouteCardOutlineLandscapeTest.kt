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
 * Dispatch 2026-11-06-05, Amendment 5 (RECORD -2187; the owner: "Plain line all the way round, like a bubble"): in landscape, not
 * navigating, the route card rises from the screen's bottom edge, and its bottom, flush to that edge, carries the map chrome's line as
 * its top and its side toward the map do (before Amendment 5 the bottom was left open). Bark at 40% by day, white at 40% at night
 * (`RouteCardOutlineReading.kt`), read from drawn pixels on the real Maps tab, recording, a route plotted. Portrait is
 * [RouteCardTuckCloseTest]'s, resting on the navigation card [RouteCardOutlineOnNavigationCardTest]'s.
 *
 * Its side against the side bar draws no line of its own: Sorrel's review of PR #507, item 1.1, and the owner's answer (RECORD -2187,
 * 2026-11-08T22:50:26Z): "(c) All three (Recommended)", the landscape plotting card among them, under RECORD -967 ("Where two outlined
 * pieces touch, one shared 1 dp line is drawn: ... strip or card against the rail"). Until then these cases claimed the ruled line on
 * every side, the "right side" (the side bar's) included. With the icon bar moved to the side bar's side the card sits in the far
 * corner, where no side meets the side bar, and every side keeps its line.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardOutlineLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    /**
     * [rotation] null leaves the display as the qualifier sets it. [barToRailSide]: the icon bar dragged across to the side bar's side
     * first, so the card takes the far corner (C20) and no side of it meets the side bar.
     */
    private fun assertOutline(dark: Boolean, rotation: Int? = null, barToRailSide: Boolean = false) {
        val name = "${if (dark) "night" else "day"}${rotation?.let { ", rotation $it" } ?: ""}${if (barToRailSide) ", the icon bar on the side bar's side" else ""}"
        rotation?.let { Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(it) }
        rig.setScreen(dark = dark)
        if (barToRailSide) {
            val barLeft = rig.bounds(MAP_ICON_CLUSTER_TAG).right < rig.screen().right / 2
            rig.dragBarAcross(rightward = barLeft)
        }
        rig.plotTo(2)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val screen = rig.screen()
        // The side bar is the one on the screen's edge with something between it and the card: the card's own corner is either on
        // the side bar (a gap of the bar's width to that screen edge) or in the far corner (no gap to the screen edge).
        val leftGap = (card.left - screen.left).value
        val rightGap = (screen.right - card.right).value
        val onRailSide = !barToRailSide
        assertEquals("$name, precondition: the card is ${if (onRailSide) "against the side bar" else "in the far corner, on the screen's edge"} (gaps $leftGap / $rightGap dp)", onRailSide, minOf(leftGap, rightGap) > 1f)
        println("MEASURED $name: the card at ${card.left}..${card.right}, its bottom at ${card.bottom}, the screen's at ${screen.bottom}")
        val pixels = composeRule.onNodeWithTag(ROUTE_CARD_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val px = composeRule.density.density
        val n = with(composeRule.density) { 8.dp.roundToPx() }
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2] }
        val column = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        val railOnRight = rightGap < leftGap
        val left = "left side" to row.take(n)
        val right = "right side" to row.takeLast(n).reversed()
        val lined = listOf("top" to column.take(n), "bottom" to column.takeLast(n).reversed()) +
            if (!onRailSide) listOf(left, right) else listOf(if (railOnRight) left else right)
        val bare = if (onRailSide) listOf(if (railOnRight) right else left) else emptyList()
        val off = lined.mapNotNull { (side, edge) ->
            println("MEASURED $name, the card's $side: ${edge.take(6).map { it.hex() }}")
            whyNotRuledLine(edge, dark, px)?.let { "$side, not lined: $it" }
        } + bare.mapNotNull { (side, edge) ->
            println("MEASURED $name, the card's $side, against the side bar: ${edge.take(6).map { it.hex() }}")
            whyNotBare(edge, px)?.let { "$side, against the side bar, not bare (a second line beside the side bar's): $it" }
        }
        val expected = if (onRailSide) "the ruled line on its top, bottom and side toward the map, and none of its own against the side bar" else "the ruled line on every side"
        assertTrue("$name, the landscape route card carries $expected; off: $off", off.isEmpty())
    }

    @Test
    fun `by day in landscape, the route card's bottom on the screen edge carries the ruled outline, and its side against the side bar has none of its own`() = assertOutline(dark = false)

    @Test
    fun `at night in landscape, the route card's bottom on the screen edge carries the ruled outline, and its side against the side bar has none of its own`() = assertOutline(dark = true)

    @Test
    fun `by day turned one way, the plotting card's side against the side bar has no line of its own`() = assertOutline(dark = false, rotation = Surface.ROTATION_90)

    @Test
    fun `at night turned one way, the plotting card's side against the side bar has no line of its own`() = assertOutline(dark = true, rotation = Surface.ROTATION_90)

    @Test
    fun `by day turned the other way, the plotting card's side against the side bar has no line of its own`() = assertOutline(dark = false, rotation = Surface.ROTATION_270)

    @Test
    fun `at night turned the other way, the plotting card's side against the side bar has no line of its own`() = assertOutline(dark = true, rotation = Surface.ROTATION_270)

    @Test
    fun `with the icon bar on the side bar's side, the card in the far corner keeps the ruled line on every side`() = assertOutline(dark = false, barToRailSide = true)
}
