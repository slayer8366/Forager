package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.map.runOf
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Dispatch 2026-11-06-05, Amendment 3, the planner's addition (RECORD -2187): the route card's outline in landscape navigation, where
 * the card rests on the navigation card beside the side bar. The owner, to "In landscape navigation, which edges of the route card get
 * the outline?": "Every edge facing the map" ("Top and left here (right meets the side bar, bottom meets the navigation card)"). So the
 * top and the side toward the map carry the 1 dp `onSurface` outline (Bark #3B2E24 by day, #EDE3D0 at night, written out as hex), and
 * the side against the side bar carries none. Read from drawn pixels on the real Maps tab, recording and navigating, a route plotted.
 * The bottom, behind the navigation card's top, is printed, not asserted: what is drawn there is the navigation card's.
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
        val outline = if (dark) Color(0xFFEDE3D0) else Color(0xFF3B2E24)
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
        val n = with(composeRule.density) { 6.dp.roundToPx() }
        // Read below the top corners' rounding and above the tuck behind the navigation card.
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 3] }
        val column = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        val mapSide = if (onRight) row.take(n) else row.takeLast(n).reversed()
        val barSide = if (onRight) row.takeLast(n).reversed() else row.take(n)
        for ((side, edge) in listOf("top" to column.take(n), "side toward the map" to mapSide)) {
            val run = runOf(edge, outline)
            assertNotNull("$name, the card's $side faces the map: a ${outline.hex()} outline is drawn; the edge reads ${edge.map { it.hex() }}", run)
            assertTrue("$name, the card's $side: the outline is at the card's own edge, ${run!!.first} px in", run.first <= 1)
            println("MEASURED $name, the card's $side: ${outline.hex()} for ${run.last - run.first + 1} px from ${run.first} px in")
        }
        assertNull("$name, the side against the side bar carries no outline; it reads ${barSide.map { it.hex() }}", runOf(barSide, outline))
        println("MEASURED $name, the card's bottom reads ${column.takeLast(n).reversed().map { it.hex() }} (not asserted)")
    }

    @Test
    fun `by day in landscape navigation, the outline is on the top and the side toward the map, not the side bar's`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `at night in landscape navigation, the outline is on the top and the side toward the map, not the side bar's`() {
        assertOutline(dark = true, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `turned the other way by day, the side bar's side still carries none`() {
        assertOutline(dark = false, rotation = Surface.ROTATION_270)
    }
}
