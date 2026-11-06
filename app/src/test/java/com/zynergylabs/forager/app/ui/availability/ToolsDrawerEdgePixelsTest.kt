package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.material3.DrawerDefaults
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OUTLINE_WIDTH
import com.zynergylabs.forager.app.ui.map.MapChromeShape
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
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
 * Dispatch 2026-11-05-09 (RECORD -2180; the owner, with a screenshot of the open Tools drawer over the map: "Give the tools drawer
 * an outline also please", then "Add it to 495"). The drawer is opened the way a user opens it, by a real touch on Tools in the
 * bottom bar or the rail, over the grey map of SeeThroughEdgePixelsTest, and read from pixels:
 *
 * - **Over the Maps tab** (D1, D3): its edge facing the map carries the map-chrome hairline (Bark at 0.4 by day, white at 0.4 at
 *   night) over its 0.8 fill, and so do the two rounded corners on that edge, round their curve, at the sheet's own radius. That is
 *   read from the shape the sheet is filled to (20 dp under ForagerTheme, not the 16 dp the outline tests of the pill and strip
 *   use). Its other three sides lie on the screen's edges and carry none (RECORD -967).
 * - **Over the List tab** (D2; the owner, for map chrome where it covers no map: "In the places that aren't covering a map, they
 *   can stay solid"): solid, with no line.
 *
 * Each pixel over the Maps tab is held to what that same pixel showed in a capture made before Tools opened, as C2 reads its menu
 * (SeeThroughEdgeMenuPixelsTest), so the pill, the strip and the bottom bar under the drawer's ends are read as they are. One
 * thing under the drawer changes as it opens: the Tools item takes its selected indicator and label colour. In portrait the bottom
 * corner lies over that item, so its samples are kept below the item's label. The straight edge is read at three heights where the
 * map under it is bare, checked in the capture before opening.
 */
abstract class ToolsDrawerEdgePixelsTests(private val dark: Boolean, private val rotation: Int) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val rig = EdgeShadeRig(composeRule, dark)

    private val landscape get() = rotation != Surface.ROTATION_0
    private val navTag get() = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG
    private val name get() = "tools-drawer-" + when (rotation) {
        Surface.ROTATION_90 -> "land90"
        Surface.ROTATION_270 -> "land270"
        else -> "portrait"
    }

    /** The drawer's colour role: navigationBarContainerColor(), the theme's surfaceContainer (NavigationBarColor.kt). */
    private val role get() = if (dark) SurfaceContainerDark else SurfaceContainerLight

    /**
     * Heights down the sheet, in dp from its top, where its map-facing edge is read. In portrait the icon bar lies across that edge
     * from about 222 to 602 dp and the bottom bar below 743 dp (run 42156737251's captures), so the map is bare there from about 77 to
     * 221 dp and 602 to 742 dp; in landscape it is bare from below the strip down. All clear of the drawer's own content, which sits
     * 16 dp in from each side, with full-width dividers only above its bottom two rows.
     */
    private val edgeHeightsDp get() = if (landscape) listOf(90, 170, 250) else listOf(110, 180, 680)

    private val d get() = rig.density
    private fun px(dp: Int) = (dp * d).roundToInt()

    /** The hairline's middle, in whole pixels in from the edge: 1 at 3 px per dp (pixel 1 to 2 of a 3 px line). */
    private val lineInset get() = (MAP_CHROME_OUTLINE_WIDTH.value * d / 2f).toInt()

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.waitForIdle()
    }

    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen()
        settle()
        assertEquals("the screen sees the rotation this case is about", rotation, composeRule.activity.window.decorView.display?.rotation)
    }

    private fun navItem(label: String) = composeRule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(navTag)))

    /** A real touch at the centre of the [label] item in the bottom bar or the rail. */
    private fun touchNav(label: String) {
        val c = navItem(label).fetchSemanticsNode().boundsInRoot.center
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(c) }
        settle()
    }

    private fun openTools() {
        touchNav("Tools")
        composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).assertIsDisplayed()
        navItem("Tools").assertIsSelected()
    }

    /** The open sheet in px, whether the map is on its right, and the radius of its two corners there, from its own shape. */
    private class Sheet(val l: Int, val t: Int, val r: Int, val b: Int, val mapOnRight: Boolean, val radius: Float)

    private fun sheet(): Sheet {
        val node = composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val l = node.positionInRoot.x.roundToInt()
        val t = node.positionInRoot.y.roundToInt()
        val r = l + node.size.width
        val b = t + node.size.height
        val screen = composeRule.onRoot().fetchSemanticsNode().size
        check((l == 0) != (r == screen.width)) { "the sheet ($l, $t)-($r, $b) px lies against one side of the ${screen.width} px screen" }
        check(t == 0 && b == screen.height) { "the sheet runs the screen's full height: $t to $b px of ${screen.height}" }
        val mapOnRight = l == 0
        // D3: in portrait the drawer opens from the left; in landscape from the rail's side, the right, at both rotations (-956).
        assertEquals("the drawer opens from the ${if (landscape) "right, the rail's side" else "left"}", !landscape, mapOnRight)
        val shape: Shape = node.config.getOrElseNullable(MapChromeShape) { null }
            ?: throw AssertionError("the sheet carries the shape it is filled to (MapChromeShape), to read its corners from")
        val direction = if (mapOnRight) LayoutDirection.Ltr else LayoutDirection.Rtl
        val outline = shape.createOutline(Size(node.size.width.toFloat(), node.size.height.toFloat()), direction, composeRule.density)
        val rr = (outline as? Outline.Rounded)?.roundRect ?: throw AssertionError("the sheet's outline is a rounded rectangle: $outline")
        val mapSide = if (mapOnRight) listOf(rr.topRightCornerRadius.x, rr.bottomRightCornerRadius.x) else listOf(rr.topLeftCornerRadius.x, rr.bottomLeftCornerRadius.x)
        val edgeSide = if (mapOnRight) listOf(rr.topLeftCornerRadius.x, rr.bottomLeftCornerRadius.x) else listOf(rr.topRightCornerRadius.x, rr.bottomRightCornerRadius.x)
        check(edgeSide.all { it == 0f }) { "the sheet's corners on the screen's edge are square: $edgeSide px" }
        check(mapSide[0] > 0f && mapSide[0] == mapSide[1]) { "the sheet's two corners facing the map are round and alike: $mapSide px" }
        println("TOOLSDRAWER|$name|sheet ($l, $t)-($r, $b) px, the map on its ${if (mapOnRight) "right" else "left"}, corners ${mapSide[0] / d} dp")
        return Sheet(l, t, r, b, mapOnRight, mapSide[0])
    }

    /** The column [j] px in from the sheet's map-facing edge (negative: outside it). */
    private fun Sheet.mapCol(j: Int) = if (mapOnRight) r - 1 - j else l + j

    /** The column [j] px in from the sheet's side on the screen's edge. */
    private fun Sheet.screenCol(j: Int) = if (mapOnRight) l + j else r - 1 - j

    /** A sample point on one of the two corners facing the map: the pixel on the line, and one 2.5 px outside the curve. */
    private class CornerSample(val where: String, val onLine: Pair<Int, Int>, val outside: Pair<Int, Int>?)

    /**
     * Each corner facing the map at 15° to 75° of its curve, every 10° (0° where it leaves the straight edge, 90° where it meets the
     * screen's top or bottom). The line is drawn inside the sheet's edge, so its middle lies [lineInset] + 0.5 px inside the curve:
     * the pixel read is the one whose centre lies nearest that, within half a pixel, so the whole pixel is under the line. Outside,
     * 2.5 px beyond the curve, where neither fill nor line reaches; none where that falls off the screen.
     */
    private fun corners(s: Sheet, height: Int): List<CornerSample> {
        val sx = if (s.mapOnRight) 1f else -1f
        val cx = if (s.mapOnRight) s.r - s.radius else s.l + s.radius
        val lineRadius = s.radius - (MAP_CHROME_OUTLINE_WIDTH.value * d / 2f)
        return listOf("top" to -1f, "bottom" to 1f).flatMap { (corner, sy) ->
            val cy = if (sy < 0f) s.t + s.radius else s.b - s.radius
            (15..75 step 10).map { deg ->
                val a = Math.toRadians(deg.toDouble())
                val ux = sx * cos(a).toFloat()
                val uy = sy * sin(a).toFloat()
                val out = nearestOnCircle(cx, cy, s.radius + 2.5f, ux, uy)
                CornerSample("$corner corner at $deg°", nearestOnCircle(cx, cy, lineRadius, ux, uy), out.takeIf { it.second in 0 until height })
            }
        }
    }

    private fun nearestOnCircle(cx: Float, cy: Float, radius: Float, ux: Float, uy: Float): Pair<Int, Int> {
        val tx = cx + radius * ux
        val ty = cy + radius * uy
        fun off(x: Int, y: Int) = abs(hypot(x + 0.5f - cx, y + 0.5f - cy) - radius)
        val best = (-2..2).flatMap { dx -> (-2..2).map { dy -> floor(tx).toInt() + dx to floor(ty).toInt() + dy } }
            .minBy { (x, y) -> off(x, y) + 0.1f * hypot(x + 0.5f - tx, y + 0.5f - ty) }
        check(off(best.first, best.second) <= 0.5f) { "a pixel centre within half a pixel of the $radius px circle near ($tx, $ty): $best" }
        return best
    }

    /**
     * What changes under the drawer as it opens: the Tools item's indicator, icon and label, from its cell's top to 1 dp below its
     * label. Samples there are not held to the capture before opening.
     */
    private fun toolsItemChanges(): Rect {
        val cell = navItem("Tools").fetchSemanticsNode().boundsInRoot
        val labels = composeRule.onAllNodes(hasText("Tools") and hasAnyAncestor(hasTestTag(navTag)), useUnmergedTree = true).fetchSemanticsNodes()
        check(labels.isNotEmpty()) { "the Tools item's label is found, to keep samples off it" }
        return Rect(cell.left, cell.top, cell.right, labels.maxOf { it.boundsInRoot.bottom } + d)
    }

    private fun assertDrawer(what: String, readings: List<EdgeReading>) {
        println("TOOLSDRAWER|$name|$what|" + readings.joinToString(" ; "))
        val off = readings.filterNot { it.ok }
        if (off.isNotEmpty()) throw AssertionError("$what; off: " + off.joinToString(", "))
    }

    @Test
    fun `over the Maps tab the drawer's edge facing the map carries the hairline over its 0_8 fill, and its other three sides none`() {
        setScreen()
        val before = rig.capture("$name-edge-before")
        openTools()
        val after = rig.capture("$name-edge")
        val s = sheet()
        val side = if (s.mapOnRight) "right" else "left"
        val readings = mutableListOf<EdgeReading>()
        for (h in edgeHeightsDp) {
            val y = s.t + px(h)
            for (j in -px(6)..px(12)) {
                val x = s.mapCol(j)
                check(edgeDiff(before[x, y], EDGE_SHADE_MAP_GREY) <= EDGE_FILL_TOLERANCE) {
                    "the map under the drawer's $side edge at $h dp is bare before opening: ($x, $y) reads ${edgeHex(before[x, y])}"
                }
            }
            val lx = s.mapCol(lineInset)
            readings += EdgeReading("$side edge at $h dp, the hairline", after[lx, y], edgeLine(dark, edgeFill(role, before[lx, y])), EDGE_LINE_TOLERANCE)
            for (k in 2..12) {
                val x = s.mapCol(px(k))
                readings += EdgeReading("$side edge at $h dp, $k dp in", after[x, y], edgeFill(role, before[x, y]), EDGE_FILL_TOLERANCE)
            }
            for (k in 3..6) {
                val x = s.mapCol(-px(k))
                readings += EdgeReading("$side edge at $h dp, $k dp outside, unchanged", after[x, y], before[x, y], EDGE_FILL_TOLERANCE)
            }
        }
        // The sides on the screen's edges: the start side halfway down, the top and bottom halfway across.
        val midY = (s.t + s.b) / 2
        val midX = (s.l + s.r) / 2
        val sides = listOf<Pair<String, (Int) -> Pair<Int, Int>>>(
            "the side on the screen's edge" to { j -> s.screenCol(j) to midY },
            "the top" to { j -> midX to s.t + j },
            "the bottom" to { j -> midX to s.b - 1 - j },
        )
        for ((what, at) in sides) {
            val (ex, ey) = at(lineInset)
            readings += EdgeReading("$what, no line", after[ex, ey], edgeFill(role, before[ex, ey]), EDGE_FILL_TOLERANCE)
            for (k in 2..3) {
                val (x, y) = at(px(k))
                readings += EdgeReading("$what, $k dp in", after[x, y], edgeFill(role, before[x, y]), EDGE_FILL_TOLERANCE)
            }
        }
        assertDrawer("the Tools drawer over the Maps tab reads its 0.8 fill from 2 dp in, the hairline at its $side edge and none at its other sides", readings)
    }

    @Test
    fun `over the Maps tab both corners on the drawer's edge facing the map carry the hairline round their curve`() {
        setScreen()
        val before = rig.capture("$name-corners-before")
        openTools()
        val after = rig.capture("$name-corners")
        val s = sheet()
        val changes = toolsItemChanges()
        fun changed(p: Pair<Int, Int>) = changes.contains(androidx.compose.ui.geometry.Offset(p.first + 0.5f, p.second + 0.5f))
        val samples = corners(s, after.height)
        val kept = samples.filterNot { changed(it.onLine) || it.outside?.let(::changed) == true }
        for (corner in listOf("top", "bottom")) {
            val n = kept.count { it.where.startsWith(corner) }
            check(n >= 4) { "at least 4 of the $corner corner's 7 samples lie clear of the Tools item: $n (${samples.filter { it.where.startsWith(corner) }.map { it.where }})" }
        }
        println("TOOLSDRAWER|$name|corners read: ${kept.map { it.where }}; kept off the Tools item: ${(samples - kept.toSet()).map { it.where }}")
        val readings = kept.flatMap { c ->
            val (x, y) = c.onLine
            listOfNotNull(
                EdgeReading("${c.where}, the hairline at ($x, $y)", after[x, y], edgeLine(dark, edgeFill(role, before[x, y])), 4f / 255f),
                c.outside?.let { (ox, oy) -> EdgeReading("${c.where}, outside the curve at ($ox, $oy), unchanged", after[ox, oy], before[ox, oy], EDGE_FILL_TOLERANCE) },
            )
        }
        assertDrawer("the Tools drawer over the Maps tab carries the hairline round its two ${s.radius / d} dp corners facing the map", readings)
    }

    @Test
    fun `over the List tab the drawer stays solid with no line at its edge facing the map`() {
        setScreen()
        touchNav("List")
        navItem("List").assertIsSelected()
        openTools()
        val after = rig.capture("$name-list")
        val s = sheet()
        val side = if (s.mapOnRight) "right" else "left"
        val readings = mutableListOf<EdgeReading>()
        for (h in edgeHeightsDp) {
            val y = s.t + px(h)
            val lx = s.mapCol(lineInset)
            readings += EdgeReading("$side edge at $h dp, no line", after[lx, y], role, EDGE_FILL_TOLERANCE)
            for (k in 2..12) {
                val x = s.mapCol(px(k))
                readings += EdgeReading("$side edge at $h dp, $k dp in", after[x, y], role, EDGE_FILL_TOLERANCE)
            }
        }
        for (c in corners(s, after.height)) {
            val (x, y) = c.onLine
            readings += EdgeReading("${c.where}, no line at ($x, $y)", after[x, y], role, EDGE_FILL_TOLERANCE)
        }
        assertDrawer("the Tools drawer over the List tab is solid, with no line at its $side edge or its corners", readings)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolsDrawerEdgePixelsPortraitDayTest : ToolsDrawerEdgePixelsTests(dark = false, rotation = Surface.ROTATION_0)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolsDrawerEdgePixelsPortraitNightTest : ToolsDrawerEdgePixelsTests(dark = true, rotation = Surface.ROTATION_0)

/** The S22 in landscape (823 x 384 dp), as SeeThroughEdgePixelsLandscapeTest, at each rotation (RECORD -956). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolsDrawerEdgePixelsLandscape90DayTest : ToolsDrawerEdgePixelsTests(dark = false, rotation = Surface.ROTATION_90)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolsDrawerEdgePixelsLandscape90NightTest : ToolsDrawerEdgePixelsTests(dark = true, rotation = Surface.ROTATION_90)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolsDrawerEdgePixelsLandscape270DayTest : ToolsDrawerEdgePixelsTests(dark = false, rotation = Surface.ROTATION_270)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolsDrawerEdgePixelsLandscape270NightTest : ToolsDrawerEdgePixelsTests(dark = true, rotation = Surface.ROTATION_270)

/**
 * D4, nothing but the line changes: the shape the sheet is now given, so its line can be drawn from the same value
 * (toolsDrawerShape), is Material's own default for a modal drawer, DrawerDefaults.shape, under the app's theme. This holds before
 * and after the line by design; it guards the sheet's shape, not the line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToolsDrawerShapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    @Test
    fun `the drawer sheet's shape is Material's own default for a modal drawer, by day and at night`() {
        val seen = mutableMapOf<Boolean, Pair<Shape, Shape>>()
        composeRule.setContent {
            ForagerTheme(darkTheme = false) { seen[false] = toolsDrawerShape() to DrawerDefaults.shape }
            ForagerTheme(darkTheme = true) { seen[true] = toolsDrawerShape() to DrawerDefaults.shape }
        }
        composeRule.waitForIdle()
        for (dark in listOf(false, true)) {
            val (ours, material) = seen[dark] ?: throw AssertionError("the theme composed with darkTheme = $dark")
            println("TOOLSDRAWER|shape|dark=$dark ours $ours, Material's $material")
            assertEquals("the sheet's shape with darkTheme = $dark", material, ours)
        }
    }
}
