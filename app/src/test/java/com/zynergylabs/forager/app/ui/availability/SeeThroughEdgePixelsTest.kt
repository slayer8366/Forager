package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpRect
import com.zynergylabs.forager.app.ui.map.BUBBLE_TAIL_LENGTH
import com.zynergylabs.forager.app.ui.map.CENTRE_PIN_CONFIRM_ROW_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_TYPE_PANEL_TAG
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerHighestDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLowestLight
import kotlin.math.atan2
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-06, Phase 2, B1 (RECORD -2154, the owner: "Thin outline (Recommended)", whose option read "No shadow. A crisp
 * 1 dp line at the edge ... The window reads an even 80% right to its edge, and the map shows through evenly"; -2155 and -2156 for
 * the notice, the centre-pin row and the bubble's tail). Each window is opened the way a user opens it, through the real screen and
 * a real touch on its real control, then read from pixels over a grey map: from 2 dp inside every edge it is the 0.8 composite of
 * its fill over the map, and at the edge itself it carries the map-chrome hairline, or none where the rulings leave that side open.
 *
 * Before this dispatch each of these windows had an elevation shadow under its 0.8 fill. The renderer draws it as a ring whose
 * hole it expects the window to hide, so it shows through as a band darker than the composite (Phase 1 report, section 1). In
 * Robolectric the band is narrower than on the phone (about 8 dp at a bubble's sides, against about 19.5 dp), so these samples
 * start 2 dp in to fall inside it; the band's width on the phone is the device check's (B3).
 */
abstract class SeeThroughEdgePixelsTests(private val dark: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val rig = EdgeShadeRig(composeRule, dark)

    private val token get() = if (dark) SurfaceContainerDark else SurfaceContainerLight
    private val pillRole get() = if (dark) SurfaceContainerHighestDark else SurfaceContainerLowestLight
    private val scrimmed get() = edgeOver(Color.Black, EDGE_SHADE_SCRIM_ALPHA, EDGE_SHADE_MAP_GREY)

    private fun read(name: String, pixels: PixelMap, tag: String, role: Color, under: Color, specs: List<EdgeSpec>) {
        val fill = edgeFill(role, under)
        assertEdges(name, readEdges(pixels, rig.density, rig.bounds(tag), fill, edgeLine(dark, fill), specs))
    }

    private fun openBubble(): PixelMap {
        rig.setScreen()
        rig.touchTag(glyphTag(BUBBLE_WAYPOINT.id))
        return rig.capture("bubble")
    }

    /**
     * The bubble's card (MapBubble.kt's shell): its content sits 12 dp in at the start, 8 dp in at the top and bottom, and 4 dp in
     * at the end, beside the close button, so each edge is read in that padding. The bottom is read at whichever quarter of it is
     * further from the tail's base (see the tail test below).
     */
    @Test
    fun `a map bubble opened by a touch is the 0_8 composite from 2 dp inside all four edges, with the hairline at each`() {
        val pixels = openBubble()
        val under = rig.reference(pixels, 0.01f, 0.75f, EDGE_SHADE_MAP_GREY)
        val tail = bubbleTail()
        val bottomAlong = if (tail.base.x < (tail.card.left + tail.card.right) / 2f) 0.75f else 0.25f
        read(
            "the bubble", pixels, MAP_BUBBLE_TAG, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 10, line = true),
                EdgeSpec(EdgeSide.TOP, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 3, line = true),
                EdgeSpec(EdgeSide.BOTTOM, bottomAlong, 2, 6, line = true),
            ),
        )
    }

    /** The bubble's card in px, and its tail as MapBubbleShell draws it, worked out the same way from the card and the tapped point. */
    private class Tail(val card: androidx.compose.ui.geometry.Rect, val tip: Offset, val base: Offset, val base1: Offset, val base2: Offset)

    private fun bubbleTail(): Tail {
        val d = rig.density
        fun DpRect.px() = androidx.compose.ui.geometry.Rect(left.value * d, top.value * d, right.value * d, bottom.value * d)
        val card = rig.bounds(MAP_BUBBLE_TAG).px()
        val tip = rig.bounds(glyphTag(BUBBLE_WAYPOINT.id)).px().center
        val centre = card.center
        val toTip = tip - centre
        check(kotlin.math.abs(toTip.x) > card.width / 2f || kotlin.math.abs(toTip.y) > card.height / 2f) {
            "the tapped point $tip is outside the card $card, so the bubble draws a tail"
        }
        val angleDeg = Math.toDegrees(atan2(toTip.x.toDouble(), -toTip.y.toDouble())).toFloat()
        val base = centre + rectEdgeIntersection(card.width / 2f, card.height / 2f, angleDeg)
        val dir = (tip - base) / (tip - base).getDistance()
        val half = Spacing.xs.value * d // MapBubble.kt's BUBBLE_TAIL_BASE_HALF_WIDTH
        return Tail(card, tip, base, Offset(base.x - dir.y * half, base.y + dir.x * half), Offset(base.x + dir.y * half, base.y - dir.x * half))
    }

    /**
     * RECORD -2156 (the owner: "Outline around both (Recommended)", whose option read "One continuous line around the bubble and out
     * along the tail's two sides to its tip, with no line across where they join, like an outlined speech bubble"). The tail's
     * geometry is worked out here exactly as MapBubbleShell works it out, from the card's bounds and the tapped point:
     * - **along each side:** 1.5 px inside the side, 60% of the way from its base corner to the tip, the hairline;
     * - **where they join:** in the middle of the stretch of the card's edge the tail covers, 1.5 px inside the card, no line;
     * - **once, not twice:** inside both the card and the tail's triangle, the 0.8 composite, not the 0.96 two fills would make.
     */
    @Test
    fun `the bubble's outline runs along its tail's two sides and not across where the tail joins the card`() {
        val pixels = openBubble()
        val under = rig.reference(pixels, 0.01f, 0.75f, EDGE_SHADE_MAP_GREY)
        val tail = bubbleTail()
        val fill = edgeFill(token, under)
        val line = edgeLine(dark, fill)
        val inset = 1.5f
        val inside = { p: Offset -> p.x > tail.card.left && p.x < tail.card.right && p.y > tail.card.top && p.y < tail.card.bottom }
        val centroid = (tail.base1 + tail.base2 + tail.tip) / 3f
        fun pixel(p: Offset) = pixels[p.x.roundToInt(), p.y.roundToInt()]
        val readings = mutableListOf<EdgeReading>()
        listOf("first" to tail.base1, "second" to tail.base2).forEach { (name, corner) ->
            val q = corner + (tail.tip - corner) * 0.6f
            val along = (tail.tip - corner) / (tail.tip - corner).getDistance()
            var normal = Offset(-along.y, along.x)
            if ((centroid - q).x * normal.x + (centroid - q).y * normal.y < 0f) normal = -normal
            val at = q + normal * inset
            check(!inside(at)) { "the $name side's sample $at is outside the card ${tail.card}" }
            readings += EdgeReading("tail's $name side at $at", pixel(at), line, 4f / 255f)
        }
        // The card edge the tail leaves from, and where the tail's triangle crosses it: the stretch between is where they join.
        val card = tail.card
        val onBottom = kotlin.math.abs(tail.base.y - card.bottom) < 1f
        val onTop = kotlin.math.abs(tail.base.y - card.top) < 1f
        val horizontal = onBottom || onTop
        val edgeAt = when {
            onBottom -> card.bottom
            onTop -> card.top
            kotlin.math.abs(tail.base.x - card.left) < 1f -> card.left
            else -> card.right
        }
        val inward = when {
            onBottom -> Offset(0f, -1f)
            onTop -> Offset(0f, 1f)
            edgeAt == card.left -> Offset(1f, 0f)
            else -> Offset(-1f, 0f)
        }
        fun crossing(a: Offset, b: Offset): Float? {
            val (ca, cb) = if (horizontal) a.y to b.y else a.x to b.x
            if ((ca - edgeAt) * (cb - edgeAt) > 0f || ca == cb) return null
            val t = (edgeAt - ca) / (cb - ca)
            val p = a + (b - a) * t
            return if (horizontal) p.x else p.y
        }
        val crossings = listOfNotNull(
            crossing(tail.base1, tail.base2), crossing(tail.base1, tail.tip), crossing(tail.base2, tail.tip),
        )
        check(crossings.size >= 2) { "the tail's triangle crosses the card's edge at two points (crossings $crossings, base ${tail.base})" }
        val from = crossings.min()
        val to = crossings.max()
        check((to - from) >= 3f * rig.density) { "the joined stretch $from to $to px is at least 3 dp, so its middle is clear of the line at its ends" }
        val middle = (from + to) / 2f
        val joinAt = (if (horizontal) Offset(middle, edgeAt) else Offset(edgeAt, middle)) + inward * inset
        readings += EdgeReading("where the tail joins, at $joinAt, no line", pixel(joinAt), fill, 3f / 255f)
        // Inside the card and the triangle both: the corner of the triangle that lies inside the card, with the base point and the
        // crossing of that corner's side, make a small triangle wholly inside both; its centroid is read.
        val cornerInside = listOf(tail.base1, tail.base2).firstOrNull(inside)
        if (cornerInside != null) {
            val sideCrossing = crossing(cornerInside, tail.tip)
            if (sideCrossing != null) {
                val sideAt = if (horizontal) Offset(sideCrossing, edgeAt) else Offset(edgeAt, sideCrossing)
                val both = (tail.base + cornerInside + sideAt) / 3f
                readings += EdgeReading("inside the card and the tail both, at $both, filled once", pixel(both), fill, 3f / 255f)
            }
        }
        println("EDGESHADE|tail|card ${tail.card} tip ${tail.tip} base ${tail.base} corners ${tail.base1} ${tail.base2} tailLength ${BUBBLE_TAIL_LENGTH}")
        assertEdges("the bubble's tail", readings)
    }

    /** The add tile (AddActionTile.kt): its chips sit 8 dp in on every side. Opened by a real touch on the icon bar's add row. */
    @Test
    fun `the add tile opened from the icon bar is the 0_8 composite from 2 dp inside all four edges, with its hairline`() {
        rig.setScreen()
        rig.touchDescribed(BAR_ADD)
        val pixels = rig.capture("add-tile")
        val under = rig.reference(pixels, 0.01f, 0.75f, scrimmed)
        read(
            "the add tile", pixels, ADD_ACTION_TILE_TAG, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.TOP, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.BOTTOM, 0.5f, 2, 6, line = true),
            ),
        )
    }

    /** The map type panel (MapTypePanel.kt): its chips sit 8 dp in. Opened by a real touch on the icon bar's Layers row. */
    @Test
    fun `the map type panel opened from the icon bar is the 0_8 composite from 2 dp inside all four edges, with its hairline`() {
        rig.setScreen()
        rig.touchDescribedStarting("Layers:")
        val pixels = rig.capture("map-type-panel")
        val under = rig.reference(pixels, 0.01f, 0.75f, scrimmed)
        read(
            "the map type panel", pixels, MAP_TYPE_PANEL_TAG, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.TOP, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.BOTTOM, 0.5f, 2, 6, line = true),
            ),
        )
    }

    /**
     * RECORD -2154, the owner: "Follow the choice (Recommended)": the search pill's own 2 dp shadow goes the same way. Its fill is
     * its lighter role (RECORD -945). Outlined all round in portrait (RECORD -976). Its search icon sits 16 dp in at the start and
     * its field 3 dp below its top, so the top and bottom are read 2 to 3 dp in. Shown at rest: the Maps tab opens with it.
     */
    @Test
    fun `the search pill over the map is the 0_8 composite from 2 dp inside all four edges, with its hairline`() {
        rig.setScreen()
        val pixels = rig.capture("search-pill")
        val under = rig.reference(pixels, 0.01f, 0.75f, EDGE_SHADE_MAP_GREY)
        read(
            "the search pill", pixels, SEARCH_ENTRY_BAR_TAG, pillRole, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 12, line = true),
                EdgeSpec(EdgeSide.TOP, 0.5f, 2, 2, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.BOTTOM, 0.5f, 2, 3, line = true),
            ),
        )
    }

    /**
     * The navigation card in portrait is the compass strip, carrying the navigation info below its line (RECORD -950). Reached by a
     * real touch on the return button. Its top is tucked under the pill, with the pill's hand-drawn cast shadow on it, which the owner
     * kept (RECORD -945, -2154), so its top is not read; its sides and bottom are outlined (RECORD -976).
     */
    @Test
    fun `the compass strip carrying the navigation info is the 0_8 composite from 2 dp inside its sides and bottom, with its hairline`() {
        rig.setScreen()
        rig.touchTag(BAR_RETURN)
        val pixels = rig.capture("navigation-strip")
        val under = rig.reference(pixels, 0.01f, 0.75f, EDGE_SHADE_MAP_GREY)
        read(
            "the compass strip while navigating", pixels, BAR_STRIP, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.6f, 2, 3, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.6f, 2, 3, line = true),
                EdgeSpec(EdgeSide.BOTTOM, 0.25f, 2, 3, line = true),
            ),
        )
    }

    /**
     * RECORD -2155 (the owner: "Top and both sides (Recommended)"): the OK/Cancel row of the map's pin picker, raised by the add
     * tile's Waypoint. Its bottom sits on the bottom bar, whose own top line is the one line there, so the row draws none. Its
     * buttons sit 16 dp in at the sides and 8 dp at the top and bottom.
     */
    @Test
    fun `the centre-pin row is the 0_8 composite from 2 dp inside all four edges, outlined at its top and sides only`() {
        rig.setScreen()
        rig.touchDescribed(BAR_ADD)
        rig.touchText("Waypoint")
        val pixels = rig.capture("centre-pin-row")
        val under = rig.reference(pixels, 0.01f, 0.75f, EDGE_SHADE_MAP_GREY)
        read(
            "the centre-pin row", pixels, CENTRE_PIN_CONFIRM_ROW_TAG, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 12, line = true),
                EdgeSpec(EdgeSide.TOP, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 12, line = true),
                EdgeSpec(EdgeSide.BOTTOM, 0.5f, 2, 6, line = false),
            ),
        )
    }

    /**
     * RECORD -2155 (the owner: "App's own notice (Recommended)"): the notice along the bottom of the Maps tab, its message 16 dp in
     * at the start and 8 dp in at the end, in a box at least 48 dp tall. Raised by a trip-start warning; its box is the tagged one
     * laid exactly over the drawn surface (ScaffoldSnackbarHost.kt).
     */
    @Test
    fun `a notice along the bottom of the Maps tab is the 0_8 composite from 2 dp inside all four edges, with the hairline at each`() {
        rig.setScreen()
        rig.warning = com.zynergylabs.forager.app.ui.track.RecordingNotice(1, "Test warning")
        composeRule.waitForIdle()
        val pixels = rig.capture("notice")
        val under = rig.reference(pixels, 0.01f, 0.5f, EDGE_SHADE_MAP_GREY)
        read(
            "the notice", pixels, COMPACT_SNACKBAR_TAG, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 12, line = true),
                EdgeSpec(EdgeSide.TOP, 0.25f, 2, 6, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.BOTTOM, 0.25f, 2, 6, line = true),
            ),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeeThroughEdgePixelsLightTest : SeeThroughEdgePixelsTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeeThroughEdgePixelsDarkTest : SeeThroughEdgePixelsTests(dark = true)
