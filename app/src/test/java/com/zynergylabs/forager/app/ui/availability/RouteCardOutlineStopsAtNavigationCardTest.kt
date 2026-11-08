package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Dispatch 2026-11-06-05, Amendment 3, the addition of 2026-11-07T23:49:50Z (RECORD -2187): where the route card rests on the navigation
 * card in landscape navigation, its outline ends at the navigation card's top edge and does not run on behind it. The owner: "Stop at the
 * top". Found by the combined build (`docs/audits/2026-11-07-nav-card-outline-combined-clash.md`): the line ran down to the route card's
 * bottom, 16 dp below that top (ROUTE_CARD_NAVIGATION_TUCK).
 *
 * Read from the drawn screen on the real Maps tab, recording and navigating, a route plotted: the columns where the route card's
 * map-facing line is drawn just above the navigation card's top, and the same columns just below it. Below, the navigation card's
 * rounded top corner is still well inward of those columns for the first [BELOW] (its arc reaches the card's side only at the corner's
 * foot), so nothing there is the navigation card's own outline; before this change the route card's line read there in its exact colour.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardOutlineStopsAtNavigationCardTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun assertStopsAtTop(dark: Boolean, rotation: Int) {
        val outline = if (dark) Color(0xFFEDE3D0) else Color(0xFF3B2E24)
        val name = "${if (dark) "night" else "day"}, rotation $rotation"
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = true, dark = dark)
        rig.plotTo(2)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        val screen = rig.screen()
        val density = composeRule.density.density
        assertTrue("$name, precondition: the card rests on the navigation card at its width (${card.left}..${card.right} vs ${hud.left}..${hud.right})", kotlin.math.abs((card.left - hud.left).value) < 0.5f && kotlin.math.abs((card.right - hud.right).value) < 0.5f)
        assertTrue("$name, precondition: the card's bottom lies behind the navigation card's top (${card.bottom} vs ${hud.top})", card.bottom - hud.top > BELOW)
        val onRight = screen.right - card.right < card.left - screen.left
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val leftPx = ceil(card.left.value * density).toInt()
        val rightPx = floor(card.right.value * density).toInt() - 1
        val n = with(composeRule.density) { 6.dp.roundToPx() }
        // The map-facing edge's first n columns, outermost first.
        val columns = if (onRight) (leftPx until leftPx + n).toList() else (rightPx downTo rightPx - n + 1).toList()
        val hudTopPx = hud.top.value * density
        val oneDp = with(composeRule.density) { 1.dp.roundToPx() }

        // Above the navigation card's top: the route card's line is drawn, at its own edge.
        val aboveRows = (floor(hudTopPx).toInt() - with(composeRule.density) { ABOVE.roundToPx() }) until floor(hudTopPx).toInt() - oneDp
        val lineColumns = mutableSetOf<Int>()
        for (y in aboveRows) {
            val edge = columns.map { pixels[it, y] }
            val run = com.zynergylabs.forager.app.ui.map.runOf(edge, outline)
            assertTrue("$name, ${"%.1f".format((hudTopPx - y) / density)} dp above the navigation card's top: the route card's map-facing side carries its ${outline.hex()} line at its edge; it reads ${edge.map { it.hex() }}", run != null && run.first <= 1)
            run!!.forEach { lineColumns += columns[it] }
        }
        println("MEASURED $name: the line above the navigation card's top in columns $lineColumns, rows ${aboveRows.first}..${aboveRows.last}")

        // Below it: in those same columns, nothing of the route card's line.
        val belowRows = (ceil(hudTopPx).toInt() + oneDp)..(ceil(hudTopPx).toInt() + with(composeRule.density) { BELOW.roundToPx() })
        for (y in belowRows) {
            val read = lineColumns.sorted().map { pixels[it, y] }
            println("MEASURED $name, ${"%.1f".format((y - hudTopPx) / density)} dp below the navigation card's top: ${read.map { it.hex() }}")
            assertTrue("$name, ${"%.1f".format((y - hudTopPx) / density)} dp below the navigation card's top: the route card's line stops at that top, so its columns carry no ${outline.hex()}; they read ${read.map { it.hex() }}", read.none { it == outline })
        }
    }

    @Test
    fun `by day, the route card's line stops at the navigation card's top`() {
        assertStopsAtTop(dark = false, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `at night, the route card's line stops at the navigation card's top`() {
        assertStopsAtTop(dark = true, rotation = Surface.ROTATION_90)
    }

    @Test
    fun `turned the other way by day, the route card's line stops at the navigation card's top`() {
        assertStopsAtTop(dark = false, rotation = Surface.ROTATION_270)
    }

    private companion object {
        /** How far above the navigation card's top the line is read: below the route card's own rounded top corners on any card. */
        val ABOVE = 4.dp

        /** How far below that top the columns are read: inside the 16 dp tuck, above where the navigation card's corner arc meets them. */
        val BELOW = 3.dp
    }
}
