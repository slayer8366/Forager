package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
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
 * Dispatch 2026-11-06-05, Amendment 6, A1 (RECORD -2187). The owner, from build 4972 on the S26: "The outline doesn't cover the entire
 * edge, it stops short of completion. Also there is a visible overlap between the two cards." Restated and confirmed ("Yes, that's
 * it"), and the corners settled as "(2) Card fills corner": in landscape navigation the route card's bottom meets the navigation card's
 * top with nothing of it behind that card; its sides run straight down into the navigation card's rounded top corners, filling them, so
 * no map shows there; the one line where they meet is the navigation card's own top line with its curves, and the route card draws no
 * line of its own along it.
 *
 * Read from the drawn screen on the real Maps tab, recording and navigating, by day and at night, at both rotations, in the S22's
 * landscape window and a short one. Every expected colour comes from the screen itself or the ruling, not from the code under test:
 * - **Nothing behind the navigation card.** Down the cards' middle, the navigation card's top rows read the same with the route card
 *   open as with no route card at all (the same rows below that card's top, read before the route is plotted). A route card running
 *   on behind the 80% navigation card shows through it, so those rows differ.
 * - **One line, the navigation card's.** Just above that top, up the middle, the route card reads bare (`whyNotBare`): no line of its
 *   own along the meeting edge. The navigation card's line is the one in the rows above.
 * - **Its sides run down into the corners.** On both sides, above the navigation card's top and for [INTO_CORNER] below it, inside
 *   the wedge the navigation card's rounded corner leaves, the route card's outermost pixels carry the ruled line
 *   (`RouteCardOutlineReading.kt`: Bark at 40% by day, white at 40% at night) over its own fill.
 * - **The corners are filled.** A pixel inside each wedge, [WEDGE_IN] in from the side and [WEDGE_DOWN] below the top, well clear of
 *   the corner's arc, reads the route card's own fill, as the card reads just above the top: no map shows there.
 */
abstract class RouteCardMeetsNavigationCardTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    protected fun assertMeets(dark: Boolean, rotation: Int) {
        val name = "${if (dark) "night" else "day"}, rotation $rotation"
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = true, dark = dark)
        val density = composeRule.density.density
        fun px(dp: Dp): Int = with(composeRule.density) { dp.roundToPx() }

        // The navigation card alone, before any route card.
        val alone = composeRule.onRoot().captureToImage().toPixelMap()
        val hudAlone = rig.bounds(NAVIGATION_HUD_TAG)

        rig.plotTo(2)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        println("MEASURED $name: route card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, navigation card ${hud.left.value}..${hud.right.value} x ${hud.top.value}..${hud.bottom.value} (alone ${hudAlone.top.value})")
        assertTrue("$name, precondition: the route card rests on the navigation card at its width (${card.left}..${card.right} vs ${hud.left}..${hud.right})", kotlin.math.abs((card.left - hud.left).value) < 0.5f && kotlin.math.abs((card.right - hud.right).value) < 0.5f)
        assertTrue("$name, precondition: the navigation card is where it was alone (${hud.top} vs ${hudAlone.top}), so its rows compare", kotlin.math.abs((hud.top - hudAlone.top).value) < 0.5f && kotlin.math.abs((hud.left - hudAlone.left).value) < 0.5f)
        val open = composeRule.onRoot().captureToImage().toPixelMap()

        val off = mutableListOf<String>()
        val leftPx = ceil(card.left.value * density).toInt()
        val rightPx = floor(card.right.value * density).toInt() - 1
        val middle = (leftPx + rightPx) / 2
        val hudTopPx = hud.top.value * density
        val topRow = ceil(hudTopPx).toInt()

        // Nothing behind the navigation card: its top rows down the middle read as they did alone.
        for (y in topRow until topRow + px(BEHIND)) {
            val was = alone[middle, y]
            val now = open[middle, y]
            if (!now.near(was)) off += "middle, ${"%.1f".format((y - hudTopPx) / density)} dp below the navigation card's top: reads ${now.hex()} with the route card open, ${was.hex()} with the navigation card alone (something drawn behind it)"
        }

        // One line, the navigation card's: the route card draws none of its own along the meeting edge.
        val upward = (topRow - 1 downTo topRow - px(8.dp)).map { open[middle, it] }
        println("MEASURED $name, the middle just above the navigation card's top, upward: ${upward.take(6).map { it.hex() }}")
        whyNotBare(upward, density)?.let { off += "the middle just above the navigation card's top, no line of the route card's own: $it" }

        // The sides run down into the navigation card's corners, and the corners are filled.
        val n = px(8.dp)
        val sides = listOf("left side" to (leftPx until leftPx + n).toList(), "right side" to (rightPx downTo rightPx - n + 1).toList())
        val oneDp = px(1.dp)
        val rows = ((floor(hudTopPx).toInt() - px(4.dp)) until floor(hudTopPx).toInt() - oneDp) + ((topRow + oneDp)..(topRow + px(INTO_CORNER)))
        for ((side, columns) in sides) {
            for (y in rows) {
                val edge = columns.map { open[it, y] }
                val where = if (y < hudTopPx) "${"%.1f".format((hudTopPx - y) / density)} dp above" else "${"%.1f".format((y - hudTopPx) / density)} dp below"
                if (y > hudTopPx) println("MEASURED $name, $side, $where the navigation card's top: ${edge.take(6).map { it.hex() }}")
                whyNotRuledLine(edge, dark, density)?.let { off += "$side, $where the navigation card's top: $it" }
            }
            val wedge = open[columns[px(WEDGE_IN)], topRow + px(WEDGE_DOWN)]
            val own = open[columns[px(WEDGE_IN)], floor(hudTopPx).toInt() - px(WEDGE_DOWN)]
            println("MEASURED $name, $side corner: wedge ${wedge.hex()}, the card just above ${own.hex()}, alone ${alone[columns[px(WEDGE_IN)], topRow + px(WEDGE_DOWN)].hex()}")
            if (!wedge.near(own)) off += "$side corner, ${WEDGE_IN} in and ${WEDGE_DOWN} below the navigation card's top: reads ${wedge.hex()}, not the route card's own fill ${own.hex()} (map shows in the corner)"
        }
        assertTrue("$name, the route card meets the navigation card: nothing behind it, one line (the navigation card's), the sides run down into its filled corners; off: $off", off.isEmpty())
    }

    protected companion object {
        /** How far below the navigation card's top its rows are compared with and without the route card. */
        val BEHIND = 6.dp

        /** How far below the navigation card's top the sides are read: inside the corner's wedge, above its arc at 5 dp in. */
        val INTO_CORNER = 3.dp

        /** A point inside each corner's wedge: in from the side, past the line, and below the top, well above the arc there. */
        val WEDGE_IN = 2.dp
        val WEDGE_DOWN = 2.dp
    }
}

/** The S22's landscape window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardMeetsNavigationCardTest : RouteCardMeetsNavigationCardTests() {
    @Test
    fun `by day, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = false, rotation = Surface.ROTATION_90)

    @Test
    fun `at night, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = true, rotation = Surface.ROTATION_90)

    @Test
    fun `turned the other way by day, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = false, rotation = Surface.ROTATION_270)

    @Test
    fun `turned the other way at night, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = true, rotation = Surface.ROTATION_270)
}

/** A short landscape window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi")
class RouteCardMeetsNavigationCardShortTest : RouteCardMeetsNavigationCardTests() {
    @Test
    fun `short window by day, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = false, rotation = Surface.ROTATION_90)

    @Test
    fun `short window at night, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = true, rotation = Surface.ROTATION_90)

    @Test
    fun `short window turned the other way by day, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = false, rotation = Surface.ROTATION_270)

    @Test
    fun `short window turned the other way at night, the route card meets the navigation card at its top, filling its corners`() = assertMeets(dark = true, rotation = Surface.ROTATION_270)
}
