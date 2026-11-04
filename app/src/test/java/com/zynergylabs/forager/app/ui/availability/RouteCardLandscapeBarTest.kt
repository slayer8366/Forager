package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.routeCardLegTag
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

private const val STRIP = "compass-elevation-strip"

/**
 * Dispatch 2026-10-30-08, C18 with C20 (RECORD -1947, -1953). The owner: "Portrait, but change landscape: same card size as portrait,
 * have it set to the far left side next to where the navigation tab would be, so they're side by side when the user is navigating.
 * Else, have it on the far right side where the navigation tab would be"; "Follow the navigation card (Recommended)"; "Touching it
 * (Recommended)"; "Rises from the bottom, tab on top (Recommended)". C20, with the L kept on the rail side: "The other bottom corner
 * (Recommended)".
 *
 * The real Maps tab in a landscape window, recording, at C19's width (the reference card's, 246 dp at normal text size, never wider
 * than the room beside the L). Not navigating: in the navigation card's spot, on the rail's side, unless the L is on that side, when
 * it takes the other bottom corner. Navigating: touching the navigation card, side by side. Both rotations: the rail is on the screen's
 * right at both (RECORD -956), so the two look alike. The L never changes, and record, return, Layers, "+" and Save take real touches
 * across their bounds.
 */
abstract class RouteCardLandscapeBarTests(private val rotation: Int) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun setScreen(navigating: Boolean = false) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = navigating)
    }

    /** C19's width: the reference card's at normal text size, or the room beside the L where that is less. */
    private fun ruledWidth(room: Float): Float = minOf(REFERENCE_CARD_DP, room)

    private fun lowerStops(stops: Int, expanded: Boolean, where: String, body: () -> Unit) {
        rig.plotTo(stops)
        rig.setExpanded(expanded)
        val save = rig.bounds(ROUTE_CARD_SAVE_TAG)
        val card = rig.bounds(ROUTE_CARD_TAG)
        assertTrue("$where: Save ${save.top}..${save.bottom} is inside the card ${card.top}..${card.bottom}", save.top >= card.top && save.bottom <= card.bottom + 0.5.dp)
        if (expanded && stops >= 5) assertRowsScroll(where, stops)
        body()
    }

    /** The rows reach their last leg by scrolling, up to the total line, and the card stays where it is. */
    private fun assertRowsScroll(where: String, stops: Int) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val tag = routeCardLegTag(stops - 2)
        composeRule.onNodeWithTag("route-card-legs", useUnmergedTree = true).performScrollToNode(hasTestTag(tag))
        rig.settle()
        val last = rig.bounds(tag)
        val total = rig.bounds("route-card-routed")
        println("A14 $where: card ${card.top.value}..${card.bottom.value}, last leg ${last.top.value}..${last.bottom.value}, total line top ${total.top.value}")
        assertTrue("$where: the last leg's bottom ${last.bottom} shows, at or above the total line's top ${total.top}", last.bottom <= total.top + 0.5.dp)
    }

    private fun assertTouches(where: String) {
        rig.touchesReachStopAndReturn(where, ACROSS)
        rig.touchesReachLayersAndAdd(where, ACROSS)
        rig.touchesAcrossSave(where, ACROSS)
    }

    private fun assertInItsSpot(where: String, barOnLeft: Boolean, strip: DpRect, topLimit: DpRect) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val rail = rig.bounds(COMPACT_NAVIGATION_RAIL_TAG)
        println("C18 rot $rotation $where: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, bar ${bar.left.value}..${bar.right.value}, rail ${rail.left.value}")
        assertEquals("$where: rising from the screen's bottom edge", rig.screen().bottom.value, card.bottom.value, 0.5f)
        if (barOnLeft) {
            assertEquals("$where: in the navigation card's spot, against the rail", rail.left.value, card.right.value, 0.5f)
            assertEquals("$where: C19's width", ruledWidth((rail.left - bar.right - 8.dp).value), (card.right - card.left).value, 0.5f)
        } else {
            assertEquals("$where: C20, the L on the rail's side: the other bottom corner", 0f, card.left.value, 0.5f)
            assertEquals("$where: C19's width", ruledWidth((bar.left - 8.dp).value), (card.right - card.left).value, 0.5f)
        }
        assertTrue("$where: the top ${card.top} is below the strip's bottom ${strip.bottom}", card.top >= strip.bottom)
        assertTrue("$where: and below the search bar's bottom ${topLimit.bottom}", card.top >= topLimit.bottom)
    }

    private fun walk(side: String, barOnLeft: Boolean) {
        setScreen()
        if (!barOnLeft) rig.dragBarAcross(rightward = true)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        assertEquals("precondition: the L's side", barOnLeft, bar.right < rig.screen().right / 2)
        val strip = rig.bounds(STRIP)
        val search = rig.bounds(SEARCH_ENTRY_BAR_TAG)
        println("A14 $side: strip ${strip.left.value}..${strip.right.value} x ${strip.top.value}..${strip.bottom.value}, search ${search.left.value}..${search.right.value} x ..${search.bottom.value}")
        listOf(1, 3, 5, 6).forEach { stops ->
            listOf(false, true).forEach { expanded ->
                val where = "landscape $side, $stops stops, ${if (expanded) "expanded" else "peek"}"
                lowerStops(stops, expanded, where) {
                    assertInItsSpot(where, barOnLeft, strip, search)
                    rig.assertBarUntouched(where, bar)
                    assertTouches(where)
                }
            }
        }
    }

    @Test
    fun `the L on the left - the card is in the navigation card's spot at C19's width`() = walk("L left", barOnLeft = true)

    @Test
    fun `the L on the rail's side - the card takes the other bottom corner at C19's width`() = walk("L right", barOnLeft = false)

    @Test
    fun `moving the L across shifts the card over, peek and expanded`() {
        setScreen()
        val strip = rig.bounds(STRIP)
        val search = rig.bounds(SEARCH_ENTRY_BAR_TAG)
        rig.plotTo(3)
        listOf(false, true).forEach { expanded ->
            rig.setExpanded(expanded)
            rig.dragBarAcross(rightward = true)
            assertInItsSpot("to the right, expanded $expanded", barOnLeft = false, strip, search)
            rig.dragBarAcross(rightward = false)
            assertInItsSpot("back, expanded $expanded", barOnLeft = true, strip, search)
        }
    }

    /** Whether [back] is drawn before [front]: composed earlier under their nearest shared parent. */
    private fun isBehind(back: SemanticsNode, front: SemanticsNode): Boolean {
        fun chain(node: SemanticsNode): List<SemanticsNode> = generateSequence(node) { it.parent }.toList().reversed()
        val a = chain(back)
        val b = chain(front)
        val split = a.indices.first { i -> a[i].id != b.getOrNull(i)?.id }
        val siblings = a[split - 1].children
        return siblings.indexOfFirst { it.id == a[split].id } < siblings.indexOfFirst { it.id == b[split].id }
    }

    /**
     * C21 (RECORD -1955, the owner: "On top of the navigation card (Recommended)", "sits on it, at its width"): while navigating the
     * card rises from behind the navigation card and rests on it, at its width, edge to edge, its lower edge tucked behind it by
     * [ROUTE_CARD_NAVIGATION_TUCK] and all its content, Save included, above it. Restored from C11 (8a6126f9d), replacing C18's
     * "touching it" side by side.
     */
    @Test
    fun `navigating - the card rests on the navigation card at its width, behind it, with Save above it`() {
        setScreen(navigating = true)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val strip = rig.bounds(STRIP)
        println("C21 rot $rotation navigating: navigation card ${hud.left.value}..${hud.right.value} x ${hud.top.value}..${hud.bottom.value}, bar ${bar.left.value}..${bar.right.value}, strip bottom ${strip.bottom.value}")
        listOf(1, 3, 5, 6).forEach { stops ->
            listOf(false, true).forEach { expanded ->
                val where = "navigating, $stops stops, ${if (expanded) "expanded" else "peek"}"
                lowerStops(stops, expanded, where) {
                    val card = rig.bounds(ROUTE_CARD_TAG)
                    val save = rig.bounds(ROUTE_CARD_SAVE_TAG)
                    println("C21 rot $rotation $where: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, Save ${save.top.value}..${save.bottom.value}")
                    assertEquals("$where: the navigation card's left", hud.left.value, card.left.value, 0.5f)
                    assertEquals("$where: the navigation card's right", hud.right.value, card.right.value, 0.5f)
                    assertEquals("$where: its lower edge tucked behind the navigation card's top ${hud.top}", ROUTE_CARD_NAVIGATION_TUCK.value, (card.bottom - hud.top).value, 0.5f)
                    assertTrue("$where: Save's bottom ${save.bottom} shows at or above the navigation card's top ${hud.top}", save.bottom <= hud.top + 0.5.dp)
                    assertTrue("$where: the top ${card.top} is below the strip's bottom ${strip.bottom}", card.top >= strip.bottom)
                    rig.assertBarUntouched(where, bar)
                    assertTouches(where)
                }
            }
        }
        val card = composeRule.onNodeWithTag(ROUTE_CARD_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val navigationCard = composeRule.onNodeWithTag(NAVIGATION_HUD_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the card is behind the navigation card", isBehind(card, navigationCard))
    }

    // D13 (RECORD -1955): "the credit and the i rise above the card only where it sits over them, with the L on the right" was
    // here, reading the render mode's inset with no credit drawn. The credit now rises where the card covers it at rest, so its
    // case needs the credit drawn: RouteCardCreditTest, in this window and the short one.

}

/**
 * C19's "about 246 dp at normal size": the reference card at text size 1.0, at this window's density. The 246 dp was measured on a
 * "12.3 mi" total (RECORD -1952); the widest two-digit mile figure, "99.9 mi", is 1.7 dp wider.
 */
internal const val REFERENCE_CARD_DP = 247.67f

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardLandscapeBarTest : RouteCardLandscapeBarTests(Surface.ROTATION_90)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardLandscapeBar270Test : RouteCardLandscapeBarTests(Surface.ROTATION_270)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi")
class RouteCardShortLandscapeBarTest : RouteCardLandscapeBarTests(Surface.ROTATION_90)
