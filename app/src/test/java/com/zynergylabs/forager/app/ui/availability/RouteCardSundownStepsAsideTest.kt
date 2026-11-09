package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
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

/**
 * D42 (dispatch 2026-10-30-08, RECORD -2021; the owner: "Only where room is short"). On the S22 in the evening (session 10, build 4489)
 * the navigation card carried its sundown line on three lines (194 dp), and the route card resting on it had no room: its title cut to
 * "6 Waypoints", its buttons squeezed to 28 px, no leg row when expanded. The sundown line now leaves the navigation card only while the
 * route card rests on it and would not otherwise fit, decided by measurement, and comes back when it closes or the room is there.
 *
 * At [ROUTE_CARD_EVENING_CLOCK] (22:45 UTC, the sundown line due), the S22's insets and rotation, navigating, real touches on the handle.
 */
abstract class RouteCardSundownStepsAsideTests(legHeights: Boolean = true, clock: com.zynergylabs.forager.app.domain.CurrentTimeProvider = ROUTE_CARD_EVENING_CLOCK) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT, clock = clock, legHeights = legHeights)

    private fun sundownShown() = rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG)

    private fun clipped(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun unclipped(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun tapHandle() {
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        rig.settle()
    }

    private fun start() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)
    }

    /**
     * Where the room is short, the line steps aside (D42) and, if that is not enough at peek, the elevation line too (D44, RECORD
     * -2027): the card is whole and its rows show. [lineExpected] is whether the sundown line shows with no card in this window.
     * At 640 x 360 with the S22's insets, in the evening, it shows since Amendment 1, H10 of dispatch 2026-11-05-10 (RECORD -2181): the
     * rig's lost fix and missing compass take one line under the parts, not two, so the line fits by -955's rule and D42 steps it aside
     * when the card opens (CI run 42278603468). Was `lineExpected = false` there: "at 640 x 360 with the S22's insets -955's dot rule
     * already leaves it out, so only D44 is left to make the room".
     * Dispatch 2026-11-06-01, Amendment 1, J6 (RECORD -2183, the owner: "Lines leave (as built)"): `false` again at 640 x 360, font 2.0,
     * in the evening and just after sunset. With the card's height counted as drawn (J5's defect fix) the card with its sundown line
     * can't fit even moved up, so it stays at the bottom and the line steps off (RECORD -955) before any card opens. Was `true`.
     * With `false` the "steps aside" assertion below is trivially true, so those classes now say what they prove (the line is
     * already off before the card opens); D42's own positive control is [RouteCardSundownStepsAsideFont15Test], `true` (Sorrel's
     * review of PR #500, item 1.1).
     */
    protected fun assertStepsAsideWhereShort(lineExpected: Boolean? = null) {
        start()
        val closedShown = sundownShown()
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        println("D42 short, no card: sundown line shown $closedShown, navigation card ${hud.top.value}..${hud.bottom.value}")
        lineExpected?.let { assertEquals("precondition: the sundown line with no card", if (it) 1 else 0, closedShown) }
        listOf(3, 6).forEach { n ->
            rig.plotTo(n)
            listOf(false, true).forEach { expanded ->
                val where = "$n stops, ${if (expanded) "expanded" else "peek"}"
                if (rig.plotting.uiState.value.expanded != expanded) tapHandle()
                assertEquals("$where: the card's state", expanded, rig.plotting.uiState.value.expanded)
                val card = rig.bounds(ROUTE_CARD_TAG)
                val title = unclipped(ROUTE_CARD_ROUTED_COUNT_TAG)
                val titleShown = clipped(ROUTE_CARD_ROUTED_COUNT_TAG)
                val save = unclipped(ROUTE_CARD_SAVE_TAG)
                val saveShown = clipped(ROUTE_CARD_SAVE_TAG)
                println("D42 short $where: sundown line ${sundownShown()}, card ${card.top.value}..${card.bottom.value}, title ${title.top.value}..${title.bottom.value} shown ${titleShown.top.value}..${titleShown.bottom.value}, Save ${save.top.value}..${save.bottom.value} shown ${saveShown.top.value}..${saveShown.bottom.value}, navigation card ${rig.bounds(NAVIGATION_HUD_TAG).top.value}")
                assertEquals("$where: D42, the sundown line steps aside (1 is the line still on the navigation card)", 0, sundownShown())
                rig.assertSame("$where: the title is whole, nothing of it clipped", title, titleShown)
                rig.assertSame("$where: Save is whole, nothing of it clipped", save, saveShown)
                rig.assertSame("$where: Undo is whole, nothing of it clipped", unclipped(ROUTE_CARD_UNDO_TAG), clipped(ROUTE_CARD_UNDO_TAG))
                assertTrue("$where: Save is full height, ${save.bottom - save.top}", save.bottom - save.top >= 40.dp - 0.5.dp)
                if (expanded) {
                    val row = clipped(routeCardLegTag(0))
                    assertTrue("$where: leg row 0 shows, height ${row.bottom - row.top}", row.bottom - row.top > 0.dp)
                }
            }
            tapHandle()
        }
        rig.closeCard()
        assertEquals("the card closed: the sundown line is back as it was with no card", closedShown, sundownShown())
    }

    /** A normal window, font 1.0: the room is there, so the line stays with the card open, peek and expanded. */
    protected fun assertStaysWhereRoom() {
        start()
        assertEquals("precondition: with no card the sundown line shows", 1, sundownShown())
        rig.plotTo(3)
        listOf(false, true).forEach { expanded ->
            if (rig.plotting.uiState.value.expanded != expanded) tapHandle()
            val card = rig.bounds(ROUTE_CARD_TAG)
            println("D42 normal, 3 stops, expanded $expanded: sundown line ${sundownShown()}, card ${card.top.value}..${card.bottom.value}, navigation card ${rig.bounds(NAVIGATION_HUD_TAG).top.value}")
            assertEquals("3 stops, expanded $expanded: D42, where the room is there the sundown line stays", 1, sundownShown())
        }
        rig.closeCard()
        assertEquals("the card closed: the sundown line shows", 1, sundownShown())
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardSundownStepsAsideShortTest : RouteCardSundownStepsAsideTests() {
    @Test
    fun `evening, 640 x 360, font 2_0, the S22's insets - J6, the line is already off before the card opens - the card is whole at peek and its rows show when expanded`() = assertStepsAsideWhereShort(lineExpected = false)
}

/** The same with legs that carry no trail heights, so no elevation line at all: the S22's case in session 10. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardSundownStepsAsideShortNoElevationTest : RouteCardSundownStepsAsideTests(legHeights = false) {
    @Test
    fun `evening, 640 x 360, font 2_0, the S22's insets, no elevation line - J6, the line is already off before the card opens - the card is whole at peek and its rows show when expanded`() = assertStepsAsideWhereShort(lineExpected = false)
}

/** The S22's normal landscape window (823 x 384 dp) at font 1.0. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class RouteCardSundownStaysNormalTest : RouteCardSundownStepsAsideTests() {
    @Test
    fun `evening, 823 x 384, font 1_0 - the room is there, so the sundown line stays with the card open`() = assertStaysWhereRoom()
}

/**
 * The S22's normal window at font 2.0. Robolectric showed the line here with no card once (run 41738664095: the navigation card
 * 186..384 dp), so this was the class that bit. Since J6 the line is off before the card opens here too (run 42447453302: "sundown line
 * shown 0, navigation card 266.0..384.0"), so it proves the card whole and its rows shown, not the stepping aside; that is
 * [RouteCardSundownStepsAsideFont15Test]'s (Sorrel's review of PR #500, item 1.1).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardSundownStepsAsideFont20Test : RouteCardSundownStepsAsideTests() {
    @Test
    fun `evening, 823 x 384, font 2_0 - J6, the line is already off before the card opens - the card is whole and its rows show`() = assertStepsAsideWhereShort(lineExpected = false)
}

/**
 * D42's positive control (Sorrel's review of PR #500, item 1.1): the S22's normal window at font 1.5, where the evening line is on the
 * navigation card before the route card opens and the route card needs its room. So the line is there first, is gone with the card
 * open at 3 and 6 stops, peek and expanded, and is back when the card closes. Chosen by a probe of nine windows (run 42470512923): at
 * 823 x 384 the line shows with no card from font 1.15 to 1.75, stays with the card open (the room is there) at 1.15 and 1.3, and steps
 * aside at 1.5 and 1.75; 1.75 cuts the expanded card's leg row, so 1.5. The windows the review suggested do not show it: 823 x 384 at
 * font 2.0 has the line off before the card opens (above), and 640 x 360 at font 1.3 keeps it with the card open.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.5f)
class RouteCardSundownStepsAsideFont15Test : RouteCardSundownStepsAsideTests() {
    @Test
    fun `evening, 823 x 384, font 1_5 - D42, the line is on the card, steps aside while the route card is open, and comes back`() = assertStepsAsideWhereShort(lineExpected = true)
}

/**
 * Just after sunset ([ROUTE_CARD_AFTER_SUNSET_CLOCK]), when the sundown line does fit the navigation card at 640 x 360 with no card:
 * here D42 has a line to step aside, and it comes back when the card closes.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardSundownStepsAsideShortAfterSunsetTest : RouteCardSundownStepsAsideTests(clock = ROUTE_CARD_AFTER_SUNSET_CLOCK) {
    @Test
    fun `just after sunset, 640 x 360, font 2_0, the S22's insets - J6, the line is already off before the card opens - the card is whole and its rows show`() = assertStepsAsideWhereShort(lineExpected = false)
}
