package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
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
import kotlin.math.roundToInt

/**
 * Two defects the S22 found on the combined build 5032 (RECORD -2187, "S22 part A on the combined build"; under -2190, the owner: "Fix
 * the three"), where the landscape route card meets the side bar and draws no line of its own there (Sorrel's review of PR #507, item
 * 1.1, "(c) All three"; RECORD -967: the side bar's line is the one shared line). `RouteCardRailEdge.kt` has the fixes.
 *
 * **(1) The top corner reaches the side bar's line.** On the phone the corner's curve ended about 1 dp short of the side bar's line,
 * a band of map between. The navigation card's corner beside the side bar is the control: since RECORD -990 it runs out to that line's
 * centre, so in the corner's lowest rows, where its curve meets the side, the card's outermost pixel column is the ruled line over the
 * card's fill (`RouteCardOutlineReading.kt`: Bark at 40% by day, white at 40% at night). Read at the same rows, [CORNER_ROWS_DP] above
 * where the 16 dp corner meets the side (there a 1 dp line from that centre covers the whole outermost pixel; a line ending inside the
 * edge leaves it map), first on the navigation card alone, then on the route card at the same edge: plotting, and resting on the
 * navigation card. A control that fails means the reading is wrong, not the card.
 *
 * **(2) The edge beside the side bar's bottom corner carries the line.** The plotting card sits on the screen's bottom edge, where the
 * side bar's inner corner rounds away (RECORD -952), and on the phone map showed between the card's square edge and that curve, no line
 * on either. As the navigation card's E20/E21 (RECORD -2181): over the side bar's corner, the card's edge carries the line in the side
 * bar's line's columns, just past the card's edge. Read against the same screen with no route card: in every row of the card's bottom
 * 16 dp where that column is bare map without the card, with it the column reads the ruled line over that map; the spot must be met,
 * so the reading cannot pass without reaching it. Above the corner the column reads as it does without the card: one line there, the
 * side bar's. Resting and tucked: the strip's edge is the same edge in the same place (the S22's tucked screenshot shows the same map).
 */
abstract class RouteCardSideBarLineTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private val density get() = composeRule.density.density

    private fun px(dp: Dp): Int = with(composeRule.density) { dp.roundToPx() }

    private fun capture(): PixelMap = composeRule.onRoot().captureToImage().toPixelMap()

    /** The side bar is on the side of the card nearer the screen's edge (RouteCardPlacement.kt), with its width between them. */
    private fun railOnRight(card: DpRect): Boolean {
        val screen = rig.screen()
        return screen.right - card.right < card.left - screen.left
    }

    /**
     * (1) A piece whose rounded top corner (16 dp) at [piece]'s side-bar edge meets the side bar: in the corner's lowest rows, the
     * piece's outermost column is the ruled line over its fill (the fill read 4 dp further in, level with 5 dp in). Returns what is off.
     */
    private fun cornerOff(what: String, image: PixelMap, piece: DpRect, onRight: Boolean, dark: Boolean): List<String> {
        val edgePx = ((if (onRight) piece.right else piece.left).value * density).roundToInt()
        val topPx = piece.top.value * density
        val n = px(6.dp)
        val ref = px(ROUTE_CARD_LINE_REFERENCE_DP.dp)
        val further = px((ROUTE_CARD_LINE_REFERENCE_DP + 1).dp)
        return CORNER_ROWS_DP.mapNotNull { dy ->
            val y = kotlin.math.floor(topPx + (16f - dy) * density).toInt()
            val edge = (0 until n).map { i -> image[if (onRight) edgePx - 1 - i else edgePx + i, y] }
            val reference = edge[ref]
            println("MEASURED $what, $dy dp above where its corner meets its side, inward from its edge: ${edge.take(4).map { it.hex() }}, fill ${reference.hex()}")
            if (!reference.near(edge[further])) return@mapNotNull "$what, $dy dp up: bad sample, the fill 4 dp in ${reference.hex()} is not level with 5 dp in ${edge[further].hex()}"
            val expected = routeCardRuledLine(dark).compositeOver(reference)
            if (edge[0].near(expected)) null else "$what, $dy dp above where its corner meets its side: the outermost pixel reads ${edge[0].hex()}, not the ruled line over the fill ${expected.hex()} (the corner ends short of the side bar's line); inward ${edge.take(4).map { it.hex() }}"
        }
    }

    protected fun assertTopCornerMeetsSideBar(dark: Boolean, rotation: Int, restingOnNavigationCard: Boolean) {
        val name = "${if (dark) "night" else "day"}, rotation $rotation, ${if (restingOnNavigationCard) "resting on the navigation card" else "plotting"}"
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(dark = dark)
        // The control: the navigation card alone, its corner at the same edge.
        rig.navigatingNow.value = true
        rig.settle()
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        val controlOff = cornerOff("$name: the navigation card (control)", capture(), hud, railOnRight(hud), dark)
        if (!restingOnNavigationCard) {
            rig.navigatingNow.value = false
            rig.settle()
        }
        rig.plotTo(2)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val onRight = railOnRight(card)
        val sideEdge = if (onRight) card.right else card.left
        val hudSideEdge = if (onRight) hud.right else hud.left
        println("MEASURED $name: route card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, navigation card's edge ${hudSideEdge.value}")
        assertTrue("$name, precondition: the route card's side-bar edge is the navigation card's (${sideEdge.value} vs ${hudSideEdge.value})", kotlin.math.abs((sideEdge - hudSideEdge).value) < 0.5f)
        assertTrue("$name, the control: the navigation card's corner reaches the side bar's line in this reading; off: $controlOff", controlOff.isEmpty())
        val off = cornerOff("$name: the route card", capture(), card, onRight, dark)
        assertTrue("$name, the route card's top corner on the side bar's side reaches the side bar's line, as the navigation card's does; off: $off", off.isEmpty())
    }

    protected fun assertEndBesideSideBarCorner(dark: Boolean, rotation: Int, tucked: Boolean) {
        val name = "${if (dark) "night" else "day"}, rotation $rotation, ${if (tucked) "tucked" else "resting"}"
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(dark = dark)
        val alone = capture()
        rig.plotTo(2)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val onRight = railOnRight(card)
        if (tucked) tuck(onRight)
        val screen = rig.screen()
        println("MEASURED $name: route card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, screen bottom ${screen.bottom.value}")
        assertTrue("$name, precondition: the card is on the screen's bottom edge (${card.bottom.value} vs ${screen.bottom.value})", kotlin.math.abs((card.bottom - screen.bottom).value) < 0.5f)
        val image = capture()
        val edgePx = ((if (onRight) card.right else card.left).value * density).roundToInt()
        // The line's columns: a stroke wide, just past the card's edge, where the side bar's line lies.
        val columns = (0 until px(1.dp)).map { if (onRight) edgePx + it else edgePx - 1 - it }
        val mapAt = { y: Int -> alone[if (onRight) edgePx - px(8.dp) else edgePx + px(8.dp), y] }
        val bottomPx = (card.bottom.value * density).roundToInt()
        val off = mutableListOf<String>()
        var spot = 0
        for (y in (bottomPx - px(16.dp)) until bottomPx) {
            val bare = columns.all { alone[it, y].near(mapAt(y)) }
            if (!bare) continue
            spot++
            for (x in columns) {
                val expected = routeCardRuledLine(dark).compositeOver(alone[x, y])
                if (!image[x, y].near(expected)) off += "${"%.1f".format((bottomPx - y) / density)} dp above the bottom, column $x: reads ${image[x, y].hex()}, not the ruled line over the map ${expected.hex()} (without the card ${alone[x, y].hex()})"
            }
        }
        // Above the side bar's corner, one line: the side bar's, as without the card.
        for (y in (bottomPx - px(22.dp)) until (bottomPx - px(17.dp))) {
            for (x in columns) {
                if (!image[x, y].near(alone[x, y])) off += "${"%.1f".format((bottomPx - y) / density)} dp above the bottom, column $x, above the side bar's corner: reads ${image[x, y].hex()}, not the side bar's own ${alone[x, y].hex()} (a second line beside it)"
            }
        }
        println("MEASURED $name: rows of bare map beside the side bar's corner without the card $spot; off ${off.size}; first ${off.firstOrNull()}")
        assertTrue("$name, precondition: the spot beside the side bar's bottom corner was reached (rows of bare map there without the card: $spot)", spot > 0)
        assertTrue("$name, the card's edge carries the line where the side bar's corner curves away, continuous with the side bar's; off (${off.size}): ${off.take(4)}", off.isEmpty())
    }

    /** A real sideways swipe across the middle of the card, toward the side bar, tucks it (as AttributionWhileTuckedTests). */
    private fun tuck(onRight: Boolean) {
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            swipe(Offset(if (onRight) width * 0.2f else width * 0.8f, y), Offset(if (onRight) width * 0.8f else width * 0.2f, y), durationMillis = 300)
        }
        rig.settle()
        assertTrue("the swipe tucked the card", rig.plotting.uiState.value.tucked)
    }

    protected companion object {
        /**
         * (1) The rows read, in dp above where the 16 dp corner meets the side (the row starting there, so its centre is a sixth of a dp
         * lower): where a 1 dp line run out to the side bar's line's centre covers the whole outermost pixel (from 3.6 to 5.4 dp at 3 px
         * per dp), and a line ending a stroke inside the edge leaves it outside the card's shape, map.
         */
        val CORNER_ROWS_DP = listOf(4f, 4.5f, 5f)
    }
}

/** The S22's landscape window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardTopCornerMeetsSideBarTest : RouteCardSideBarLineTests() {
    @Test
    fun `plotting by day, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = false, rotation = Surface.ROTATION_90, restingOnNavigationCard = false)

    @Test
    fun `plotting at night, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = true, rotation = Surface.ROTATION_90, restingOnNavigationCard = false)

    @Test
    fun `plotting turned the other way by day, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = false, rotation = Surface.ROTATION_270, restingOnNavigationCard = false)

    @Test
    fun `plotting turned the other way at night, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = true, rotation = Surface.ROTATION_270, restingOnNavigationCard = false)

    @Test
    fun `resting on the navigation card by day, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = false, rotation = Surface.ROTATION_90, restingOnNavigationCard = true)

    @Test
    fun `resting on the navigation card at night, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = true, rotation = Surface.ROTATION_90, restingOnNavigationCard = true)

    @Test
    fun `resting on the navigation card turned the other way by day, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = false, rotation = Surface.ROTATION_270, restingOnNavigationCard = true)

    @Test
    fun `resting on the navigation card turned the other way at night, the route card's top corner reaches the side bar's line`() = assertTopCornerMeetsSideBar(dark = true, rotation = Surface.ROTATION_270, restingOnNavigationCard = true)
}

/** The S22's landscape window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardEndBesideSideBarCornerTest : RouteCardSideBarLineTests() {
    @Test
    fun `by day, the plotting card's edge carries the line where the side bar's bottom corner curves away`() = assertEndBesideSideBarCorner(dark = false, rotation = Surface.ROTATION_90, tucked = false)

    @Test
    fun `at night, the plotting card's edge carries the line where the side bar's bottom corner curves away`() = assertEndBesideSideBarCorner(dark = true, rotation = Surface.ROTATION_90, tucked = false)

    @Test
    fun `turned the other way by day, the plotting card's edge carries the line where the side bar's bottom corner curves away`() = assertEndBesideSideBarCorner(dark = false, rotation = Surface.ROTATION_270, tucked = false)

    @Test
    fun `turned the other way at night, the plotting card's edge carries the line where the side bar's bottom corner curves away`() = assertEndBesideSideBarCorner(dark = true, rotation = Surface.ROTATION_270, tucked = false)

    @Test
    fun `tucked by day, the strip's edge carries the line where the side bar's bottom corner curves away`() = assertEndBesideSideBarCorner(dark = false, rotation = Surface.ROTATION_90, tucked = true)

    @Test
    fun `tucked at night, the strip's edge carries the line where the side bar's bottom corner curves away`() = assertEndBesideSideBarCorner(dark = true, rotation = Surface.ROTATION_90, tucked = true)
}
