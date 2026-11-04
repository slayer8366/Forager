package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.DataStoreMapPreferencesRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Waypoint names (dispatch 2026-11-02-04, C2), through the real Maps tab, the real [AvailabilityViewModel] and a real
 * [RoutePlottingViewModel]: what the map is handed. Names by default; the route's stops' names, from `RouteStop.name`, while
 * plotting and on a saved route; Settings > Map > "Waypoint names" under "Crowded icons", turned off and on by real touches
 * across its row, turning the waypoints' names off and leaving the stops' (B3, the owner: "Yes, stops keep names
 * (Recommended)"); and the choice surviving a restart on a new DataStore instance over the real `map_preferences` file.
 * Whether MapLibre then hides crowded names is native placement and untested until a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenWaypointNamesTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    /** A saved route whose stops' names are not its waypoints' names now: what is drawn is the route's own copy. */
    private val renamedTrip = PlannedTrip(
        "route-renamed",
        "Old names",
        CREEK.stop().position,
        null,
        PlottedRoute(
            listOf(RouteStop("Creek camp", CREEK.stop().position), RouteStop("", RIDGE.stop().position)),
            listOf(straight(CREEK.stop().position, RIDGE.stop().position)),
        ),
    )

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, renamedTrip.id, 60.dp, 420.dp, renamedTrip.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/map_preferences.preferences_pb")
    private val scopes = mutableListOf<Job>()
    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }

    @Before fun setUp() { dataStoreFile().delete() }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    private fun launchRepository(): Pair<DataStoreMapPreferencesRepository, Job> {
        val job = SupervisorJob().also { scopes += it }
        return DataStoreMapPreferencesRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    /** What the file itself holds under `map.waypoint_names`, read with no repository in between. */
    private fun rawStored(): Boolean? {
        val job = SupervisorJob().also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        return runBlocking { raw.data.first()[booleanPreferencesKey("map.waypoint_names")].also { job.cancelAndJoin() } }
    }

    private fun awaitIdle(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            composeRule.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "still waiting after 5 s for: $what (logged $logged)" }
            Thread.sleep(20)
        }
    }

    private fun release(scope: Job) {
        scope.cancel()
        awaitIdle("the released store to close") { scope.isCompleted }
    }

    private fun setScreen(viewModel: AvailabilityViewModel = mapLayersViewModel(plannedTrips = listOf(renamedTrip))) {
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
    }

    private fun tapGlyph(id: String) {
        composeRule.onNodeWithTag(glyphTag(id)).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun plotCreekToRidge() {
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()
        tapGlyph(RIDGE.id)
    }

    private fun openSettings() {
        // Opening Tools > Settings is not the claim here; the touches on the Waypoint names row are.
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    private fun backToMaps() {
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()
    }

    private fun checked(): Boolean {
        val node = composeRule.onNodeWithTag(WAYPOINT_NAMES_TAG).fetchSemanticsNode()
        val states = (listOf(node) + node.children).mapNotNull { it.config.getOrNull(SemanticsProperties.ToggleableState) }
        check(states.size == 1) { "one checkbox state in the row, got $states" }
        return states.single() == ToggleableState.On
    }

    private fun touchRow(fx: Float, fy: Float) {
        composeRule.onNodeWithTag(WAYPOINT_NAMES_TAG).performScrollTo().performTouchInput { click(Offset(width * fx, height * fy)) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the Maps tab hands the map its waypoints with their names on by default`() {
        setScreen()

        val content = map.content!!
        assertEquals(listOf(CREEK, RIDGE), content.waypoints)
        assertTrue("names on by default", content.waypointNames)
    }

    @Test
    fun `while plotting the map is handed the stops' names, from each RouteStop`() {
        setScreen()
        plotCreekToRidge()

        assertEquals(listOf(RouteStop("Creek", CREEK.stop().position), RouteStop("Ridge", RIDGE.stop().position)), map.content?.plottedRoute?.stopNames)
    }

    @Test
    fun `a saved route open on its card hands the map its own stops' names, not its waypoints' names now`() {
        setScreen()
        tapGlyph(renamedTrip.id)

        assertEquals(renamedTrip.route!!.stops, map.content?.plottedRoute?.stopNames)
    }

    @Test
    fun `Settings has Waypoint names under Crowded icons, checked on a new install`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithTag(WAYPOINT_NAMES_TAG).performScrollTo()
        assertTrue(composeRule.onAllNodesWithText("Waypoint names").fetchSemanticsNodes().isNotEmpty())
        assertTrue("checked by default", checked())
        val list = composeRule.onNodeWithTag(crowdedIconsTag(com.zynergylabs.forager.app.domain.CrowdedIconsMode.LIST)).fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag(WAYPOINT_NAMES_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("under Crowded icons' last row: $row, $list", row.top >= list.bottom)
    }

    @Test
    fun `real touches across the row turn names off and on, and off hides the waypoints' names on the map but not the stops'`() {
        val (repository, _) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, plannedTrips = listOf(renamedTrip), waypointNames = repository)
        setScreen(viewModel)
        plotCreekToRidge()
        openSettings()

        val touches = listOf(0.1f to 0.5f, 0.5f to 0.2f, 0.9f to 0.8f)
        touches.forEachIndexed { i, (fx, fy) ->
            touchRow(fx, fy)
            val expected = i % 2 == 1
            assertEquals("after touch ${i + 1} at ($fx, $fy)", expected, checked())
            assertEquals(expected, viewModel.uiState.value.waypointNamesShown)
        }
        backToMaps()

        assertEquals("names off reaches the map", false, map.content?.waypointNames)
        assertEquals("the stops stay named", listOf("Creek", "Ridge"), map.content?.plottedRoute?.stopNames?.map { it.name })
    }

    @Test
    fun `the choice survives a restart on a new DataStore instance, under map waypoint_names`() {
        val (repository, scope) = launchRepository()
        val viewModel = mapLayersViewModel(errorLog = errorLog, waypointNames = repository)
        setScreen(viewModel)
        openSettings()
        touchRow(0.5f, 0.5f)
        assertEquals(false, viewModel.uiState.value.waypointNamesShown)
        awaitIdle("off stored") { runBlocking { repository.getWaypointNamesShown().getOrNull() } == false }
        release(scope)

        assertEquals("the file holds it under map.waypoint_names", false, rawStored())
        val (relaunched, _) = launchRepository()
        val restarted = mapLayersViewModel(errorLog = errorLog, waypointNames = relaunched)
        awaitIdle("a new ViewModel reads off") { !restarted.uiState.value.waypointNamesShown }
        assertTrue("nothing logged: $logged", logged.none { "aypoint-names" in it })
    }
}
