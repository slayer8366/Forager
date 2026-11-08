package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
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
 * top", and with Amendment 5's plain line all round, "Hide behind nav card". Found by the combined build
 * (`docs/audits/2026-11-07-nav-card-outline-combined-clash.md`): the line ran down to the route card's bottom, 16 dp below that top
 * (ROUTE_CARD_NAVIGATION_TUCK).
 *
 * Read from the drawn screen on the real Maps tab, recording and navigating, a route plotted, on both of the route card's sides (since
 * Amendment 5 the side against the side bar carries the line too): just above the navigation card's top the side carries the ruled line
 * (Bark at 40% by day, white at 40% at night, `RouteCardOutlineReading.kt`), and just below it the side's outermost pixels read level
 * with the pixels a few dp in, so nothing is drawn there. Below, for the first [BELOW], the navigation card's rounded top corner is still
 * well inward of the dp read (its arc reaches the card's side only at the corner's foot), so nothing there is the navigation card's own
 * outline. Nor is a line drawn across the card along that top, read up the card's middle. Amendment 5 replaced Amendment 3's exact-colour reading below the top: a 40% line drawn there would show a different colour
 * from the one above, and an exact-colour reading could not have seen it.
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
        val name = "${if (dark) "night" else "day"}, rotation $rotation"
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = true, dark = dark)
        rig.plotTo(2)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        val density = composeRule.density.density
        assertTrue("$name, precondition: the card rests on the navigation card at its width (${card.left}..${card.right} vs ${hud.left}..${hud.right})", kotlin.math.abs((card.left - hud.left).value) < 0.5f && kotlin.math.abs((card.right - hud.right).value) < 0.5f)
        assertTrue("$name, precondition: the card's bottom lies behind the navigation card's top (${card.bottom} vs ${hud.top})", card.bottom - hud.top > BELOW)
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val leftPx = ceil(card.left.value * density).toInt()
        val rightPx = floor(card.right.value * density).toInt() - 1
        val n = with(composeRule.density) { 8.dp.roundToPx() }
        // Each side's first n columns, outermost first.
        val sides = listOf("left side" to (leftPx until leftPx + n).toList(), "right side" to (rightPx downTo rightPx - n + 1).toList())
        val hudTopPx = hud.top.value * density
        val oneDp = with(composeRule.density) { 1.dp.roundToPx() }
        val aboveRows = (floor(hudTopPx).toInt() - with(composeRule.density) { ABOVE.roundToPx() }) until floor(hudTopPx).toInt() - oneDp
        val belowRows = (ceil(hudTopPx).toInt() + oneDp)..(ceil(hudTopPx).toInt() + with(composeRule.density) { BELOW.roundToPx() })
        val off = mutableListOf<String>()
        for ((side, columns) in sides) {
            // Above the navigation card's top: the route card's line is drawn, at its own edge.
            for (y in aboveRows) {
                val edge = columns.map { pixels[it, y] }
                whyNotRuledLine(edge, dark, density)?.let { off += "$side, ${"%.1f".format((hudTopPx - y) / density)} dp above the navigation card's top: $it" }
            }
            // Below it: nothing of the route card's line.
            for (y in belowRows) {
                val edge = columns.map { pixels[it, y] }
                println("MEASURED $name, $side, ${"%.1f".format((y - hudTopPx) / density)} dp below the navigation card's top: ${edge.take(6).map { it.hex() }}")
                whyNotBare(edge, density)?.let { off += "$side, ${"%.1f".format((y - hudTopPx) / density)} dp below the navigation card's top, no line: $it" }
            }
        }
        // Nor does a line run across the card along the navigation card's top: the bottom of the line is behind that card, not drawn
        // at its top. Read up the card's middle column from just above that top.
        val middle = (leftPx + rightPx) / 2
        val upward = (floor(hudTopPx).toInt() - 1 downTo floor(hudTopPx).toInt() - n).map { pixels[middle, it] }
        println("MEASURED $name, the card's middle just above the navigation card's top, upward: ${upward.take(6).map { it.hex() }}")
        whyNotBare(upward, density)?.let { off += "the card's middle just above the navigation card's top, no line across: $it" }
        assertTrue("$name, the route card's line runs down both sides to the navigation card's top and stops there; off: $off", off.isEmpty())
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
