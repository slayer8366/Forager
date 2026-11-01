package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.repository.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import kotlinx.coroutines.yield
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

private const val RECORD = "control-pill-record"
private const val RETURN = "control-pill-return-to-vehicle"
private const val PILL = "control-pill"

/** The real Maps tab with two stops plotted over trail legs with heights and a recording running. */
private class ClearanceRig(private val composeRule: ComposeTestRule) {
    val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> yield(); totalLineTrail(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val map = RouteMapSlot(emptyList())
    var stopTouches = 0
    var returnTouches = 0

    fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    fun setScreen(rotation: Int?) {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        val viewModel = mapLayersViewModel(
            unitSystemPreferences = object : UnitSystemPreferenceRepository {
                override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
                override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
            },
        )
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
                navigation = AvailabilityScreenNavigation(onToggleReturning = { returnTouches++ }),
                recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = { stopTouches++ }),
            )
        }
        settle()
    }

    fun plotTwoStops() {
        composeRule.runOnUiThread {
            plotting.onStartPlotting(CREEK.stop())
            plotting.onWaypointTapped(RIDGE.stop())
        }
        settle()
    }

    fun expandByTouch() {
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertTrue("precondition: expanded", plotting.uiState.value.expanded)
    }

    fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    fun dragLToTheRight() {
        val handle = bounds("map-icon-bar-minimize-handle")
        val from = with(composeRule.density) { Offset(((handle.left + handle.right) / 2).toPx(), ((handle.top + handle.bottom) / 2).toPx()) }
        val by = with(composeRule.density) { Offset(300.dp.toPx(), 0f) }
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(from + by)
            advanceEventTime(50)
            up()
        }
        settle()
    }

    fun assertStopAndReturnTakeTouches(where: String) {
        val stopBefore = stopTouches
        ACROSS.forEachIndexed { i, (fx, fy) ->
            composeRule.onNodeWithTag(RECORD).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("$where: stop, a touch at ($fx, $fy)", stopBefore + i + 1, stopTouches)
        }
        val returnBefore = returnTouches
        ACROSS.forEachIndexed { i, (fx, fy) ->
            composeRule.onNodeWithTag(RETURN).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("$where: return, a touch at ($fx, $fy)", returnBefore + i + 1, returnTouches)
        }
    }

    fun assertCardClearsPill(where: String, rightSide: Boolean) {
        val card = bounds(ROUTE_CARD_TAG)
        val pill = bounds(PILL)
        println("A13 $where: card ${card.left}..${card.right} x ${card.top}..${card.bottom}, pill ${pill.left}..${pill.right} x ${pill.top}..${pill.bottom}")
        if (rightSide) {
            assertTrue("$where: the card's right ${card.right} clears the pill's left ${pill.left}", card.right <= pill.left - 8.dp + 0.5.dp)
        } else {
            assertTrue("$where: the card's left ${card.left} clears the pill's right ${pill.right}", card.left >= pill.right + 8.dp - 0.5.dp)
        }
    }

    fun walk(where: String, rightSide: Boolean) {
        if (rightSide) {
            dragLToTheRight()
            assertTrue("precondition: the L went to the right", bounds(MAP_ICON_CLUSTER_TAG).left > 400.dp)
        }
        plotTwoStops()
        assertCardClearsPill("$where, peek", rightSide)
        val l = bounds(MAP_ICON_CLUSTER_TAG)
        val card = bounds(ROUTE_CARD_TAG)
        assertTrue("$where: the card ${card.left}..${card.right} clears the L ${l.left}..${l.right}", if (rightSide) card.right <= l.left else card.left >= l.right)
        assertStopAndReturnTakeTouches("$where, peek")
        expandByTouch()
        assertCardClearsPill("$where, expanded", rightSide)
        assertStopAndReturnTakeTouches("$where, expanded")
    }
}

/**
 * Dispatch 2026-10-30-08, Amendment 13 (RECORD -1919), the owner: "Inset the card past the pill (Recommended)". The real Maps tab at
 * the S22's landscape size, with the L on the left and on the right, at both rotations, at peek and expanded: the card clears the
 * pill and the column, and stop and return take real touches sampled across their bounds. Robolectric reports no insets, so a real
 * cut-out or navigation bar on either side is a device check; both the L and the card sit inside the same controls padding.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardPillClearanceTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = ClearanceRig(composeRule)

    private fun walk(rotation: Int, where: String, rightSide: Boolean) {
        rig.setScreen(rotation)
        rig.walk(where, rightSide)
    }

    @Test
    fun `rotation 90, the L on the left`() = walk(Surface.ROTATION_90, "90 left", rightSide = false)

    @Test
    fun `rotation 270, the L on the left`() = walk(Surface.ROTATION_270, "270 left", rightSide = false)

    @Test
    fun `rotation 90, the L on the right`() = walk(Surface.ROTATION_90, "90 right", rightSide = true)

    @Test
    fun `rotation 270, the L on the right`() = walk(Surface.ROTATION_270, "270 right", rightSide = true)

    @Test
    fun `the numbers the amendment names - the pill at 8 to 104, the card narrowed to start 8 dp past it and end at the rail`() {
        rig.setScreen(Surface.ROTATION_90)
        rig.plotTwoStops()
        val card = rig.bounds(ROUTE_CARD_TAG)
        val pill = rig.bounds(PILL)
        assertEquals("the pill's left", 8f, pill.left.value, 0.5f)
        assertEquals("the pill's right", 104f, pill.right.value, 0.5f)
        assertEquals("the card's left", 112f, card.left.value, 0.5f)
        assertEquals("the card keeps its right edge at the rail", 744f, card.right.value, 1f)
    }
}

/** Portrait is unchanged: the card is the window's width, with the column on either side (Amendment 13). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardPillClearancePortraitTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = ClearanceRig(composeRule)

    @Test
    fun `the card fills the window as it did`() {
        rig.setScreen(null)
        rig.plotTwoStops()
        val card = rig.bounds(ROUTE_CARD_TAG)
        assertEquals("the card's left", 0f, card.left.value, 0.01f)
        assertEquals("the card's right", 384f, card.right.value, 0.01f)
    }
}

/** A landscape tablet: the centred card already clears the L, so it stays where it was, at its full 640 dp (Amendment 13). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land-xhdpi")
class RouteCardPillClearanceTabletTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = ClearanceRig(composeRule)

    @Test
    fun `the card stays centred at its full width`() {
        rig.setScreen(Surface.ROTATION_90)
        rig.plotTwoStops()
        val card = rig.bounds(ROUTE_CARD_TAG)
        val pill = rig.bounds(PILL)
        println("A13 tablet: card ${card.left}..${card.right}, pill ${pill.left}..${pill.right}")
        assertEquals("the card's width", 640f, (card.right - card.left).value, 0.5f)
        assertEquals("centred beside the rail", 1318f - card.right.value - 79.5f, card.left.value, 1.5f)
    }
}

/**
 * All the card's text still fits at the narrower width at font scale 2.0, the largest this repo tests (no test had run the card at
 * any font scale before this one). Native graphics, or Robolectric measures text a few dp wide and every fit passes on fake widths
 * (`LandscapeLargeFontTest`). This runs it at the S22's landscape size and at the smallest common landscape phone.
 */
abstract class RouteCardFontFit(private val expectedPeek: List<String> = PEEK, private val expectedOpen: List<String> = OPEN) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = ClearanceRig(composeRule)

    private val cardText = SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null } and hasAnyAncestor(hasTestTag(ROUTE_CARD_TAG))

    private fun assertWhole(where: String, expected: List<String>) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val nodes = composeRule.onAllNodes(cardText, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("$where: the card's texts", expected, nodes.flatMap { it.config[SemanticsProperties.Text].map { t -> t.text } })
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.single()
            val text = layout.layoutInput.text.text
            println("A13 font $where: \"$text\" width ${layout.size.width} px, lines ${layout.lineCount}, card ${card.left}..${card.right}")
            assertTrue("$where: \"$text\" is whole", !layout.hasVisualOverflow)
            val right = with(composeRule.density) { node.boundsInRoot.right.toDp() }
            assertTrue("$where: \"$text\" ends at $right inside the card's right ${card.right}", right <= card.right)
        }
    }

    @Test
    fun `every text is whole at peek and expanded`() {
        assertEquals("the font scale this test is about", 2.0f, composeRule.density.fontScale, 0.01f)
        rig.setScreen(Surface.ROTATION_90)
        rig.plotTwoStops()
        rig.assertCardClearsPill("font, peek", rightSide = false)
        assertWhole("peek", expectedPeek)
        rig.expandByTouch()
        assertWhole("expanded", expectedOpen)
    }

    private companion object {
        val PEEK = listOf("2 Waypoints routed", "0.4 mi", "Elevation: +15 ft (↑ 15 ↓ 0)", "Undo", "Cancel", "Save")
        val OPEN = listOf("Creek to Ridge", "0.4 mi", "Elevation: +15 ft (↑ 15 ↓ 0)") + PEEK
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RouteCardPillClearanceFontTest : RouteCardFontFit()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RouteCardPillClearanceFontSmallPhoneTest : RouteCardFontFit()
