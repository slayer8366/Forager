package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CLOSE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Dispatch 2026-11-06-05, Amendment 1, plan step 1 (RECORD -2187), the owner: "(b) Card leaves room", which replaces -1861 ("Pill sits
 * above the card") and -1943's rise: "Re-center sits under the route card. The card always leaves room for it, so the card never moves."
 * The option the owner chose read: "the card never moves, but while navigating there is an empty band under it". With the card tucked or
 * closed, Re-center is where it is today.
 *
 * The real compact Maps tab with the real [RoutePlottingViewModel], navigating, a saved route open. Every touch is a real one at screen
 * coordinates, several across each target (CLAUDE.md: a finger is not a point), and long-presses around the two reach the map
 * (CLAUDE.md: a Surface intercepts touches across its bounds).
 */
abstract class RouteCardReCenterUnderCardTests {

    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.37, lng = -121.69, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.38, lng = -121.69, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private val map = RouteMapSlot(emptyList())
    protected val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val navigating = mutableStateOf(true)
    protected val navigatedTo = mutableListOf<String>()

    protected fun setScreen() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            val now = navigating.value
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = AbsentForecastCellStore,
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
                uiStateTransform = { (if (now) it.copy(liveFix = fix) else it).copy(plannedTrips = listOf(ROUTE_TRIP)) },
                navigation = if (now) {
                    AvailabilityScreenNavigation(isReturning = true, onToggleReturning = {}, navigationTarget = start, onNavigateToWaypoint = { navigatedTo += it })
                } else {
                    AvailabilityScreenNavigation(onNavigateToWaypoint = { navigatedTo += it })
                },
                recording = AvailabilityScreenRecording(isRecording = now),
                currentTime = ROUTE_CARD_NOON_CLOCK,
            )
        }
        settle()
    }

    protected fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    protected fun setNavigating(on: Boolean) {
        navigating.value = on
        settle()
    }

    protected fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    protected fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    protected fun pill(): DpRect = bounds(RETURN_TO_ROUTE_TAG)
    protected fun card(): DpRect = bounds(ROUTE_CARD_TAG)

    protected fun restoreId(): Int = map.renderMode?.navigationView?.restoreRequestId ?: throw AssertionError("navigating, so the map was asked for its navigation view")

    /** Moves the map away from the navigation view, so Re-center shows. */
    protected fun leaveView() {
        val view = map.renderMode?.navigationView ?: throw AssertionError("navigating, so the map was asked for its navigation view")
        composeRule.runOnUiThread { view.onLeftView() }
        settle()
        assertEquals("moved away from the navigation view, Re-center shows", 1, count(RETURN_TO_ROUTE_TAG))
    }

    /** One real touch on Re-center at ([sx], [sy]); inside the stadium's round ends (RoutePlottingChromeTest's samples). */
    protected fun touchPill(sx: Float, sy: Float) {
        composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).performTouchInput {
            val inX = 12.dp.toPx()
            val inY = 6.dp.toPx()
            fun along(s: Float, size: Float, inset: Float) = when { s < 0f -> inset; s > 0.75f -> size - inset; else -> size * s }
            click(Offset(along(sx, width.toFloat(), inX), along(sy, height.toFloat(), inY)))
        }
        settle()
    }

    /** Back on the navigation follow: a real touch on Re-center if it shows (opening a saved route moves the camera to fit it). */
    protected fun follow() {
        if (count(RETURN_TO_ROUTE_TAG) > 0) touchPill(0.5f, 0.5f)
        assertEquals("following the navigation, Re-center does not show", 0, count(RETURN_TO_ROUTE_TAG))
    }

    protected fun openRoute() {
        composeRule.runOnUiThread { plotting.onOpenSavedRoute(ROUTE_TRIP, fromTripPlanner = false) }
        settle()
        assertTrue("the saved route opened", plotting.uiState.value.mode is RoutePlotMode.Showing)
    }

    protected fun longPressAt(where: String, x: Dp, y: Dp) {
        val before = map.longPresses
        val offset = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onRoot().performTouchInput { longClick(offset) }
        settle()
        assertEquals("a long-press $where ($x, $y) reaches the map", before + 1, map.longPresses)
    }

    /** A real sideways swipe across the card's middle, which tucks it (RouteCardTuck.kt). */
    protected fun tuck() {
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), durationMillis = 300)
        }
        settle()
        assertTrue("precondition: the card is tucked", plotting.uiState.value.tucked)
    }

    protected fun assertSame(what: String, expected: DpRect, actual: DpRect) {
        listOf(expected.left to actual.left, expected.top to actual.top, expected.right to actual.right, expected.bottom to actual.bottom).forEach { (e, a) ->
            assertEquals("$what: expected $expected, was $actual", e.value, a.value, 0.5f)
        }
    }

    protected fun log(where: String) {
        val p = if (count(RETURN_TO_ROUTE_TAG) > 0) pill().let { "${it.left.value}..${it.right.value} x ${it.top.value}..${it.bottom.value}" } else "none"
        val c = if (count(ROUTE_CARD_TAG) > 0) card().let { "${it.left.value}..${it.right.value} x ${it.top.value}..${it.bottom.value}" } else "none"
        println("STEP1 $where: Re-center $p, card $c")
    }

    protected fun touchAcross(tag: String, fx: Float, fy: Float) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * fx, height * fy)) }
        settle()
    }
}

/** The S22's portrait window, where Re-center at bottom centre shares the card's column. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardReCenterUnderCardTest : RouteCardReCenterUnderCardTests() {

    /** Re-center with no card, moved away from the navigation view: today's place, which the open card leaves room for. */
    private fun home(): DpRect {
        setScreen()
        leaveView()
        val home = pill()
        log("no card")
        return home
    }

    @Test
    fun `navigating, Re-center sits under the open card, a gap below it, where it is with no card`() {
        val home = home()
        openRoute()
        leaveView()
        log("card open")
        val card = card()
        assertTrue("precondition: Re-center ${home.left}..${home.right} shares the card's column ${card.left}..${card.right}", home.left < card.right && card.left < home.right)
        assertSame("Re-center under the open card, where it is with no card", home, pill())
        assertEquals("Re-center's top sits one gap (Spacing.sm, 8 dp) under the card's bottom", 8f, (pill().top - card.bottom).value, 0.5f)
    }

    @Test
    fun `the open card never moves as Re-center appears and goes, through real touches across Re-center`() {
        setScreen()
        openRoute()
        follow()
        val still = card()
        log("following")
        listOf(0.5f to 0.5f, -1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
            val at = "a touch at ($sx, $sy) on Re-center"
            leaveView()
            assertSame("$at: before it, Re-center showing, the card", still, card())
            val was = restoreId()
            touchPill(sx, sy)
            assertEquals("$at brings the navigation view back", was + 1, restoreId())
            assertEquals("$at: Re-center goes", 0, count(RETURN_TO_ROUTE_TAG))
            assertSame("$at: after it, Re-center gone, the card", still, card())
        }
    }

    @Test
    fun `not navigating the card sits where it does today, and navigating it leaves Re-center's room under it`() {
        val home = home()
        setNavigating(false)
        openRoute()
        val resting = card()
        log("not navigating")
        assertEquals("not navigating, no band: the card's bottom is the bottom Re-center sits on", home.bottom.value, resting.bottom.value, 0.5f)
        setNavigating(true)
        val raised = card()
        log("navigating")
        assertEquals("navigating, the card's bottom is Re-center's height and a gap higher", (home.top - 8.dp).value, raised.bottom.value, 0.5f)
        assertEquals("only its place changes, not its size", (resting.bottom - resting.top).value, (raised.bottom - raised.top).value, 0.5f)
    }

    @Test
    fun `tucked or closed, Re-center is where it is today`() {
        val home = home()
        openRoute()
        tuck()
        leaveView()
        log("tucked")
        assertSame("the card tucked, Re-center", home, pill())
        composeRule.runOnUiThread { plotting.onSetTucked(false) }
        settle()
        leaveView()
        assertSame("the card back, Re-center", home, pill())
        composeRule.runOnUiThread { plotting.onCloseSavedRoute() }
        settle()
        leaveView()
        assertSame("the card closed, Re-center", home, pill())
    }

    @Test
    fun `with Re-center under the card, real touches across Navigate, Directions and the X each reach their own control`() {
        setScreen()
        openRoute()
        ACROSS.forEach { (fx, fy) ->
            leaveView()
            val navigations = navigatedTo.size
            touchAcross(ROUTE_CARD_NAVIGATE_TAG, fx, fy)
            assertEquals("a touch at ($fx, $fy) on Navigate starts navigating to the first stop", listOf(CREEK.id), navigatedTo.drop(navigations))
            assertTrue("a touch at ($fx, $fy) on Navigate leaves the card open", plotting.uiState.value.mode is RoutePlotMode.Showing)
        }
        ACROSS.forEachIndexed { i, (fx, fy) ->
            leaveView()
            touchAcross(ROUTE_CARD_DIRECTIONS_TAG, fx, fy)
            // No maps app in the test, so Directions says so in a toast (L4): that toast is Directions being reached.
            assertEquals("a touch at ($fx, $fy) on Directions reaches it", i + 1, ShadowToast.shownToastCount())
            assertTrue("a touch at ($fx, $fy) on Directions leaves the card open", plotting.uiState.value.mode is RoutePlotMode.Showing)
        }
        ACROSS.forEach { (fx, fy) ->
            if (plotting.uiState.value.mode !is RoutePlotMode.Showing) openRoute()
            leaveView()
            touchAcross(ROUTE_CARD_CLOSE_TAG, fx, fy)
            assertEquals("a touch at ($fx, $fy) on the X closes the route", RoutePlotMode.Off, plotting.uiState.value.mode)
            assertEquals("a touch at ($fx, $fy) on the X: Re-center still shows", 1, count(RETURN_TO_ROUTE_TAG))
        }
    }

    @Test
    fun `real long-presses around Re-center and the card reach the map, with Re-center showing and in the empty band without it`() {
        setScreen()
        openRoute()
        leaveView()
        val card = card()
        val pill = pill()
        val pillMiddle = (pill.top + pill.bottom) / 2
        longPressAt("in the gap between the card and Re-center", (pill.left + pill.right) / 2, (card.bottom + pill.top) / 2)
        longPressAt("beside Re-center, toward the card's start", (card.left + pill.left) / 2, pillMiddle)
        // Just past Re-center's end side. On the S22 the card (0..198 dp) ends inside Re-center's span (167..217 dp), so the midpoint of
        // the two ends would be on Re-center itself, which takes the press; 12 dp past whichever reaches further is beside both.
        longPressAt("beside Re-center, on its end side", maxOf(pill.right, card.right) + 12.dp, pillMiddle)
        longPressAt("just above the card", (card.left + card.right) / 2, card.top - 12.dp)
        follow()
        longPressAt("in the band under the card, where Re-center was", (pill.left + pill.right) / 2, pillMiddle)
        longPressAt("in the band under the card, toward its start", (card.left + pill.left) / 2, pillMiddle)
    }
}

/**
 * A tablet in portrait: the card at its one width against the edge, Re-center at bottom centre in the other column
 * (`RouteCardRecenterTabletTest`). Re-center is not under the card here, so the card leaves no band and Re-center does not move.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-xhdpi")
class RouteCardReCenterBesideCardTabletTest : RouteCardReCenterUnderCardTests() {

    @Test
    fun `on a tablet in portrait the card in the other column leaves no band, and Re-center stays where it is`() {
        setScreen()
        leaveView()
        val home = pill()
        openRoute()
        leaveView()
        log("tablet")
        val card = card()
        assertTrue("precondition: the card ${card.left}..${card.right} is in the other column from Re-center ${home.left}..${home.right}", home.right <= card.left || card.right <= home.left)
        assertSame("Re-center", home, pill())
        assertEquals("the card's bottom is the bottom Re-center sits on: no band", home.bottom.value, card.bottom.value, 0.5f)
    }
}
