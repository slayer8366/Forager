package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_LEGS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAME_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardLegTag
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The trail leg of `RouteCardShownTotalsTest`: 0.44 mi, climbing 4.6 m, so a row reads 0.4 mi and +15 ft. */
internal fun totalLineTrail(from: LatLng, to: LatLng) = RouteLeg.Trail(listOf(from, from, to, to), 0.44 * 1609.344, trailFrom = 1, trailTo = 2, trailHeightsMetres = listOf(100.0, 104.6))

private const val PILL = "control-pill"

/**
 * Dispatch 2026-10-30-08, Amendment 12 (RECORD -1910), the owner: "when the card gets expanded, simply have that info stay at the
 * bottom while the card with the info slides up, to be the total of the info". The real Maps tab at the S22's size, four waypoints
 * plotted over trail legs with heights, so the peek carries its elevation line. Every expand and collapse is a real touch or drag
 * on the handle, and positions are read in pixels from the semantics tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardTotalLineTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val falls = Waypoint("wp-falls", 45.346, -122.654, null, "Falls", "", 3_000L)
    private val pond = Waypoint("wp-pond", 45.356, -122.664, null, "Pond", "", 4_000L)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 150.dp, 200.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 150.dp, 250.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, falls.id, 220.dp, 200.dp, LatLng(falls.lat, falls.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, pond.id, 220.dp, 250.dp, LatLng(pond.lat, pond.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, LONG_TRIP.id, 150.dp, 320.dp, LONG_TRIP.location),
        ),
    )

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> yield(); totalLineTrail(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        val vm = mapLayersViewModel(
            plannedTrips = listOf(LONG_TRIP),
            unitSystemPreferences = object : UnitSystemPreferenceRepository {
                override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
                override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
            },
        )
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = vm,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE, falls, pond),
                routePlotting = RoutePlottingControls(state, plotting),
                recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = {}),
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun bounds(tag: String): DpRect = node(tag).getUnclippedBoundsInRoot()

    private fun topPx(tag: String): Float = node(tag).fetchSemanticsNode().positionInRoot.y

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    private fun cardTexts(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null } and hasAnyAncestor(hasTestTag(ROUTE_CARD_TAG)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.Text].map { t -> t.text } }

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
    }

    private fun plotFour() {
        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        touchCentreOf(glyphTag(falls.id))
        touchCentreOf(glyphTag(pond.id))
    }

    /** A finger down on the handle and moved by [dyDp], in two steps and still held. [lift] ends it. */
    private fun holdDrag(dyDp: Dp) {
        val dy = with(composeRule.density) { dyDp.toPx() }
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput {
            down(Offset(width * 0.5f, 16.dp.toPx()))
            moveBy(Offset(0f, dy / 2))
            moveBy(Offset(0f, dy / 2))
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
    }

    private fun lift() {
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { up() }
        settle()
    }

    private fun assertTotalLine(where: String) {
        assertEquals(where, "4 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertEquals(where, "1.2 mi", text(ROUTE_CARD_ROUTED_TOTAL_TAG))
        assertEquals(where, "Elevation: +45 ft (↑ 45 ↓ 0)", text(ROUTE_CARD_ROUTED_ELEVATION_TAG))
    }

    @Test
    fun `the peek shows the total line with its distance and elevation, then the buttons, and nothing else`() {
        setScreen()
        plotFour()

        assertTotalLine("the peek")
        assertEquals("the peek's texts, in order", listOf("4 Waypoints routed", "1.2 mi", "Elevation: +45 ft (↑ 45 ↓ 0)", "Undo", "Cancel", "Save"), cardTexts())
        assertTrue("the elevation line is under the count", bounds(ROUTE_CARD_ROUTED_ELEVATION_TAG).top >= bounds(ROUTE_CARD_ROUTED_COUNT_TAG).bottom)
    }

    @Test
    fun `expanded, the rows start right under the handle, the top line is gone, and the total shows once under the rows`() {
        setScreen()
        plotFour()
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)

        val texts = cardTexts()
        assertTrue("no selected line: $texts", texts.none { "selected" in it })
        assertEquals("the total appears once: $texts", 1, texts.count { it == "1.2 mi" })
        assertEquals("the routed line appears once: $texts", 1, texts.count { it == "4 Waypoints routed" })
        assertEquals("the first row starts where the handle's 32 dp end", 32f, (bounds(ROUTE_CARD_LEGS_TAG).top - bounds(ROUTE_CARD_TAG).top).value, 0.5f)
        assertTrue("the rows end at or above the total line", bounds(routeCardLegTag(2)).bottom <= bounds(ROUTE_CARD_ROUTED_TAG).top + 0.5.dp)
        assertTotalLine("expanded")
    }

    @Test
    fun `the total line's top is the same pixel at peek and expanded, by a real drag up and down, and held mid-drag`() {
        setScreen()
        plotFour()
        val line = topPx(ROUTE_CARD_ROUTED_TAG)
        val peekTop = bounds(ROUTE_CARD_TAG).top

        holdDrag(-60.dp)
        assertEquals("mid-drag up, in pixels", line, topPx(ROUTE_CARD_ROUTED_TAG), 0f)
        val risen = peekTop - bounds(ROUTE_CARD_TAG).top
        assertTrue("the card follows the finger mid-drag: risen $risen", risen >= 30.dp && risen <= 61.dp)
        assertTrue("the first row is showing mid-drag", exists(routeCardLegTag(0)) && bounds(routeCardLegTag(0)).top < bounds(ROUTE_CARD_ROUTED_TAG).top)
        assertTotalLine("mid-drag up")
        lift()
        assertTrue("past the threshold it expands", plotting.uiState.value.expanded)
        assertEquals("expanded, in pixels", line, topPx(ROUTE_CARD_ROUTED_TAG), 0f)
        val openTop = bounds(ROUTE_CARD_TAG).top
        assertTrue("the rows opened the card upward: $openTop above $peekTop", openTop < peekTop - 60.dp)

        holdDrag(60.dp)
        assertEquals("mid-drag down, in pixels", line, topPx(ROUTE_CARD_ROUTED_TAG), 0f)
        val fallen = bounds(ROUTE_CARD_TAG).top - openTop
        assertTrue("the card follows the finger back: fallen $fallen", fallen >= 30.dp && fallen <= 61.dp)
        lift()
        assertTrue("past the threshold it collapses", !plotting.uiState.value.expanded)
        assertEquals("back at peek, in pixels", line, topPx(ROUTE_CARD_ROUTED_TAG), 0f)
        assertTrue("the rows are gone behind it", !exists(routeCardLegTag(0)))
        assertEquals("the peek's top comes back", peekTop.value, bounds(ROUTE_CARD_TAG).top.value, 0.5f)
    }

    @Test
    fun `a drag short of the threshold goes back to where it was`() {
        setScreen()
        plotFour()
        val peekTop = bounds(ROUTE_CARD_TAG).top
        holdDrag(-20.dp)
        lift()
        assertTrue("still at peek", !plotting.uiState.value.expanded)
        assertEquals("the card went back", peekTop.value, bounds(ROUTE_CARD_TAG).top.value, 0.5f)
        assertTrue("the rows are gone", !exists(routeCardLegTag(0)))
    }

    @Test
    fun `expanded, the handle keeps its 48 dp over the first row - real touches 46 dp down still collapse it`() {
        setScreen()
        plotFour()
        listOf(0.1f, 0.5f, 0.9f).forEach { fx ->
            touchCentreOf(ROUTE_CARD_HANDLE_TAG)
            assertTrue("precondition: expanded", plotting.uiState.value.expanded)
            val at = with(composeRule.density) { bounds(ROUTE_CARD_TAG).let { Offset((it.left + (it.right - it.left) * fx).toPx(), (it.top + 46.dp).toPx()) } }
            composeRule.onRoot().performTouchInput { click(at) }
            settle()
            assertTrue("a touch 46 dp down at $fx of the width collapses the card", !plotting.uiState.value.expanded)
        }
    }

    @Test
    fun `a saved route keeps its name on top, the total line stays put, and the rows slide up between the two`() {
        setScreen()
        touchCentreOf(glyphTag(LONG_TRIP.id))
        assertEquals("the peek's texts: no elevation on a saved route until step 3b", listOf("Long way", "3 Waypoints routed", "0.8 mi", "Directions"), cardTexts())
        val line = topPx(ROUTE_CARD_ROUTED_TAG)
        val name = bounds(ROUTE_CARD_NAME_TAG).top
        assertTrue("the total line is under the name", bounds(ROUTE_CARD_ROUTED_TAG).top >= bounds(ROUTE_CARD_NAME_TAG).bottom)

        holdDrag(-60.dp)
        assertEquals("mid-drag, in pixels", line, topPx(ROUTE_CARD_ROUTED_TAG), 0f)
        lift()

        assertTrue("expanded", plotting.uiState.value.expanded)
        assertEquals("expanded, in pixels", line, topPx(ROUTE_CARD_ROUTED_TAG), 0f)
        assertTrue("the name rose with the card", bounds(ROUTE_CARD_NAME_TAG).top < name)
        assertTrue("the rows are under the name", bounds(routeCardLegTag(0)).top >= bounds(ROUTE_CARD_NAME_TAG).bottom)
        assertTrue("and above the total line", bounds(routeCardLegTag(1)).bottom <= bounds(ROUTE_CARD_ROUTED_TAG).top + 0.5.dp)
        assertEquals("the total appears once: ${cardTexts()}", 1, cardTexts().count { it == "0.8 mi" })
    }

    @Test
    fun `one stop keeps its hint, peek and expanded, with no total line`() {
        setScreen()
        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        assertEquals("1 Waypoint selected, tap another to route", text(ROUTE_CARD_COUNT_TAG))
        assertTrue("no total line", !exists(ROUTE_CARD_ROUTED_TAG))
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        assertEquals("1 Waypoint selected, tap another to route", text(ROUTE_CARD_COUNT_TAG))
        assertTrue("no total line", !exists(ROUTE_CARD_ROUTED_TAG))
    }

    @Test
    fun `the taller peek - the column, its pill and the i clear it where the column opens, dragged lowest, and on the left`() {
        setScreen()
        val before = map.renderMode!!.let { it.attributionBottomInset ?: it.bottomInset }
        plotFour()
        fun assertClear(where: String) {
            val card = bounds(ROUTE_CARD_TAG)
            val column = bounds(MAP_ICON_CLUSTER_TAG)
            val pill = bounds(PILL)
            val mode = map.renderMode!!
            val iBottom = bounds("map-slot").bottom - (mode.attributionBottomInset ?: mode.bottomInset)
            println("A12 $where: card top ${card.top} height ${card.bottom - card.top}, column ${column.top}..${column.bottom}, pill bottom ${pill.bottom}, i bottom $iBottom (inset before the card $before)")
            assertTrue("$where: the column's bottom ${column.bottom} clears the card's top ${card.top}", column.bottom <= card.top + 0.5.dp)
            assertTrue("$where: the pill's bottom ${pill.bottom} clears the card's top ${card.top}", pill.bottom <= card.top + 0.5.dp)
            assertTrue("$where: the i's bottom $iBottom clears the card's top ${card.top}", iBottom <= card.top + 0.5.dp)
        }
        assertClear("where it opens")
        dragCluster(Offset(0f, with(composeRule.density) { 900.dp.toPx() }))
        assertClear("dragged lowest")
        dragCluster(Offset(-with(composeRule.density) { 200.dp.toPx() }, 0f))
        assertClear("lowest, on the left")
        composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertClear("fullscreen")
    }

    private fun dragCluster(by: Offset) {
        val handle = bounds("map-icon-bar-minimize-handle")
        val from = with(composeRule.density) { Offset(((handle.left + handle.right) / 2).toPx(), ((handle.top + handle.bottom) / 2).toPx()) }
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(from + by)
            advanceEventTime(50)
            up()
        }
        settle()
    }

    private companion object {
        val LONG_TRIP = ROUTE_TRIP.copy(
            id = "route-long",
            name = "Long way",
            route = PlottedRoute(
                listOf(CREEK.stop(), RIDGE.stop(), CREEK.stop()),
                listOf(totalLineTrail(CREEK.stop().position, RIDGE.stop().position), totalLineTrail(RIDGE.stop().position, CREEK.stop().position)),
            ),
        )
    }
}
