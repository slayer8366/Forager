package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_CLOSE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** A waypoint that is not one of the route's stops, so a tap on it opens its bubble and selects nothing on the card. */
private val BUBBLE_SPOT = Waypoint("wp-bubble-spot", 45.3636, -121.6859, null, "Bubble spot", "", 7_000L)

/**
 * The S22 check of build 5054 (2026-11-09): a waypoint's bubble drew under the expanded route card (the bubble is composed before
 * all map chrome). The owner (RECORD -2187): "Kept above the card" (the bubble stays above the card's top edge, the way it stays
 * below the top strip), "Make the change this release actually" (dispatch to PR #507). BubbleFloor.kt.
 *
 * On the real compact Maps tab, every step by real touches: a saved route opened from its marker (the card at peek), tucked by a
 * sideways swipe, a waypoint tapped where the card rested (its anchor under the card's place), then the card brought back by a tap on
 * its strip and expanded by a tap on its handle. The bubble's card is compared with the route card's top at each step.
 */
abstract class RouteCardBubbleAboveCardTests(private val rotation: Int, private val landscape: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val glyphs = mutableStateListOf(StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 200.dp, 120.dp, ROUTE_TRIP.location))
    private val map = RouteMapSlot(glyphs)
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> error("nothing is saved here") },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE, BUBBLE_SPOT),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.text() = "${left.value}..${right.value} x ${top.value}..${bottom.value}"

    private fun bubbleEndsAbove(what: String, top: Dp) {
        val bubble = bounds(MAP_BUBBLE_TAG)
        println("BUBBLE $what: bubble ${bubble.text()}, limit ${top.value}")
        assertTrue("$what: the bubble ${bubble.text()} ends at or above ${top.value}", bubble.bottom <= top + 0.5.dp)
    }

    @Test
    fun `a waypoint bubble stays above the route card at peek and expanded, and only a tucked card's strip limits it`() {
        setScreen()
        composeRule.onNodeWithTag(glyphTag(ROUTE_TRIP.id)).performTouchInput { click(center) }
        settle()
        assertEquals("the route opened", ROUTE_TRIP.id, (plotting.uiState.value.mode as? RoutePlotMode.Showing)?.trip?.id)
        assertEquals("precondition: the card is at peek", false, plotting.uiState.value.expanded)
        val peek = bounds(ROUTE_CARD_TAG)
        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()
        val onRight = screen.right - peek.right < peek.left - screen.left
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            swipe(Offset(if (onRight) width * 0.2f else width * 0.8f, y), Offset(if (onRight) width * 0.8f else width * 0.2f, y), durationMillis = 300)
        }
        settle()
        assertTrue("precondition: the swipe tucked the card", plotting.uiState.value.tucked)
        val strip = bounds(ROUTE_CARD_TAB_DRAWN_TAG)

        // The waypoint where the card rested, on its far side from the strip, under the card's top.
        val slot = bounds("map-slot")
        val x = if (onRight) peek.left + 24.dp else peek.right - 24.dp
        val y = peek.top + minOf(16.dp, (peek.bottom - peek.top) / 2)
        glyphs.add(StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_SPOT.id, x - slot.left, y - slot.top, LatLng(BUBBLE_SPOT.lat, BUBBLE_SPOT.lng)))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(glyphTag(BUBBLE_SPOT.id)).performTouchInput { click(center) }
        settle()
        val tuckedBubble = bounds(MAP_BUBBLE_TAG)
        println("BUBBLE tucked: peek ${peek.text()}, strip ${strip.text()}, bubble ${tuckedBubble.text()}, glyph at ${x.value}, ${y.value}")
        val reachesStrip = tuckedBubble.left < strip.right && tuckedBubble.right > strip.left
        if (landscape) {
            // Landscape's window is wide enough for the bubble to sit clear of the strip sideways, where the card does not limit it.
            assertTrue("precondition: the bubble ${tuckedBubble.text()} is clear of the strip ${strip.text()} sideways", !reachesStrip)
            assertTrue("tucked: the card's place does not limit the bubble ${tuckedBubble.text()} (peek top ${peek.top.value})", tuckedBubble.bottom > peek.top + 0.5.dp)
        } else {
            // Portrait's bubble is near the window's width, so it always meets the strip sideways, and the strip limits it.
            assertTrue("precondition: the bubble ${tuckedBubble.text()} meets the strip ${strip.text()} sideways", reachesStrip)
            bubbleEndsAbove("tucked, over the strip", strip.top)
        }

        composeRule.onNodeWithTag(ROUTE_CARD_TAB_TAG).performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        settle()
        assertTrue("precondition: the tap on the strip brought the card back", !plotting.uiState.value.tucked)
        val back = bounds(ROUTE_CARD_TAG)
        assertEquals("precondition: back at peek where it rested", peek.top.value, back.top.value, 0.5f)
        bubbleEndsAbove("at peek", back.top)

        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(center) }
        settle()
        assertEquals("precondition: the card expanded", true, plotting.uiState.value.expanded)
        val expanded = bounds(ROUTE_CARD_TAG)
        assertTrue("precondition: the expanded card ${expanded.text()} rises above the peek's top ${peek.top.value}", expanded.top < peek.top - 1.dp)
        if (!landscape) {
            bubbleEndsAbove("expanded", expanded.top)
            return
        }
        // Landscape, expanded: no room between the strip and the card's top for the bubble. The owner (RECORD -2187): "Bubble over
        // the card (Recommended)": it is placed as the card were not there (where it was with the card tucked) and drawn over it.
        bubbleOverTheExpandedCard(expanded, tuckedBubble, slot)
    }

    private fun boundsPx(tag: String): List<Rect> = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun DpRect.px(): Rect = with(composeRule.density) { Rect(left.toPx(), top.toPx(), right.toPx(), bottom.toPx()) }

    private fun bubbleShown() = composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().isNotEmpty()

    /** A real touch at [at], px in the root. */
    private fun touch(at: Offset) {
        composeRule.onRoot().performTouchInput { click(at) }
        settle()
    }

    private fun bubbleOverTheExpandedCard(expanded: DpRect, unlimited: DpRect, slot: DpRect) {
        val bubble = bounds(MAP_BUBBLE_TAG)
        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()
        println("BUBBLE expanded, over the card: bubble ${bubble.text()}, card ${expanded.text()}, as tucked ${unlimited.text()}")
        assertTrue(
            "expanded: the bubble ${bubble.text()} overlaps the card ${expanded.text()}",
            bubble.bottom > expanded.top + 0.5.dp && bubble.left < expanded.right && bubble.right > expanded.left,
        )
        assertEquals("expanded: the bubble ${bubble.text()} is where the card does not limit it, ${unlimited.text()} (top)", unlimited.top.value, bubble.top.value, 0.5f)
        assertEquals("expanded: the bubble ${bubble.text()} is where the card does not limit it, ${unlimited.text()} (left)", unlimited.left.value, bubble.left.value, 0.5f)
        assertTrue("expanded: the bubble ${bubble.text()} is wholly in the window ${screen.text()}", bubble.top >= screen.top && bubble.bottom <= screen.bottom)

        // Touches on the bubble where it is over the card, clear of its buttons, go to the bubble: it stays open, and neither the map
        // nor the card takes them.
        val cardPx = expanded.px()
        val overlap = bubble.px().intersect(cardPx)
        // 12 dp: a button's 48 dp minimum touch target reaches 8 dp past a 32 dp close button (run 42601970670 touched it at 4 dp).
        val margin = with(composeRule.density) { 12.dp.toPx() }
        val buttons = listOf(MAP_BUBBLE_CLOSE_TAG, MAP_BUBBLE_NAVIGATE_TAG, MAP_BUBBLE_PLOT_ROUTE_TAG, MAP_BUBBLE_DIRECTIONS_TAG, MAP_BUBBLE_DETAILS_TAG)
            .flatMap { boundsPx(it) }.map { it.inflate(margin) }
        val points = (1..5).flatMap { i -> (1..3).map { j -> Offset(overlap.left + overlap.width * i / 6f, overlap.top + overlap.height * j / 4f) } }
            .filter { p -> buttons.none { it.contains(p) } }
        println("BUBBLE expanded: overlap $overlap, buttons $buttons, ${points.size} touches")
        assertTrue("precondition: ${points.size} touch points on the bubble over the card, clear of its buttons", points.size >= 3)
        val state = plotting.uiState.value
        val taps = map.taps
        points.forEach { p ->
            touch(p)
            assertTrue("a touch at $p on the bubble over the card left it open", bubbleShown())
            assertEquals("a touch at $p on the bubble over the card did not reach the map", taps, map.taps)
            assertEquals("a touch at $p on the bubble over the card did not reach the route card", state, plotting.uiState.value)
        }

        // A tap away, on the open map beside the card, closes the bubble as usual, and the card is there whole.
        val onRight = expanded.left - slot.left > slot.right - expanded.right
        val away = with(composeRule.density) {
            Offset((if (onRight) (slot.left + expanded.left) / 2 else (expanded.right + slot.right) / 2).toPx(), (slot.top + (slot.bottom - slot.top) * 0.6f).toPx())
        }
        assertFalse("precondition: the tap away $away is off the bubble", bubble.px().contains(away))
        assertFalse("precondition: the tap away $away is off the card", cardPx.contains(away))
        touch(away)
        assertEquals("the tap away at $away reached the map", taps + 1, map.taps)
        assertFalse("the tap away closed the bubble", bubbleShown())
        assertEquals("the card is still expanded", true, plotting.uiState.value.expanded)
        val after = bounds(ROUTE_CARD_TAG)
        assertEquals("the card ${after.text()} is where it was, ${expanded.text()} (top)", expanded.top.value, after.top.value, 0.5f)
        assertEquals("the card ${after.text()} is where it was, ${expanded.text()} (bottom)", expanded.bottom.value, after.bottom.value, 0.5f)
    }
}

/** The S22's portrait window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardBubbleAboveCardPortraitTest : RouteCardBubbleAboveCardTests(Surface.ROTATION_0, landscape = false)

/** The S22's landscape window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardBubbleAboveCardLandscapeTest : RouteCardBubbleAboveCardTests(Surface.ROTATION_90, landscape = true)
