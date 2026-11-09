package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * RECORD -2192 (the owner: "Exactly as I left it (Recommended)", "The map remembers its position and zoom across the restart, so
 * nothing moves. Also applies with no route open"). Not navigating, a font or night-mode change recreates MainActivity; the map
 * reopens at the camera the user left (target, zoom, bearing, tilt), not at a fresh map's search area, with a route open or none.
 *
 * Driven through the real Maps tab ([MapLayersTestScreen] over the real `AvailabilityScreen`), set in the host's `onCreate`
 * ([KeepFollowHostActivity]), and a real `ActivityScenario.recreate()`; every recreation first checks that a saveable value in
 * the same screen came back. The map is [RecreationMapStandIn] (a real MapView cannot run under Robolectric), so the user's pan
 * is the stand-in's, written to the camera memory as the real map's camera idle listener writes it.
 */
abstract class MapCameraOnRecreateTests(private val recreatedFontScale: Float) {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<KeepFollowHostActivity>? = null
    private var harnessProbe: androidx.compose.runtime.MutableIntState? = null

    @Before
    fun declareHost() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, KeepFollowHostActivity::class.java))
        KeepFollowHostActivity.content = { Screen() }
    }

    @After
    fun closeHost() {
        scenario?.close()
        KeepFollowHostActivity.content = null
    }

    private val map = RecreationMapStandIn()
    private val stops = listOf(CREEK, RIDGE, Waypoint("wp-falls", 45.3936, -121.7159, null, "Falls", "", 3_000L))
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val firstTrip = trip("camera-keep-1", "Creek to Ridge", 2)
    private val secondTrip = trip("camera-keep-2", "Creek to Falls", 3)
    private val store = OneCellStore()
    private val viewModel = mapLayersViewModel()

    /** Where the user left the camera: away from the search area, zoomed in, turned and tilted. */
    private val leftAt = StandInCamera(LatLng(45.4123, -121.7531), 15.25, 30.0, 20.0)

    private fun trip(id: String, name: String, n: Int): PlannedTrip {
        val shown = stops.take(n).map { it.stop() }
        val legs = shown.zipWithNext { a, b -> straight(a.position, b.position) }
        return PlannedTrip(id, name, shown.first().position, null, PlottedRoute(shown, legs))
    }

    /** The Maps tab, not navigating, with both saved trips stored. */
    @Composable
    private fun Screen() {
        val state by plotting.uiState.collectAsState()
        harnessProbe = rememberSaveable { mutableIntStateOf(0) }
        MapLayersTestScreen(
            viewModel = viewModel,
            mapSlot = map.slot,
            store = store,
            waypoints = stops,
            routePlotting = RoutePlottingControls(state, plotting),
            uiStateTransform = { it.copy(plannedTrips = listOf(firstTrip, secondTrip)) },
            currentTime = ROUTE_CARD_NOON_CLOCK,
        )
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun open(trip: PlannedTrip) {
        plotting.onOpenSavedRoute(trip, fromTripPlanner = false)
        settle()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(KeepFollowHostActivity::class.java)
        settle()
    }

    private fun recreate() {
        val mapsBefore = map.mapsComposed
        harnessProbe!!.intValue = PROBE_VALUE
        settle()
        RuntimeEnvironment.setFontScale(recreatedFontScale)
        scenario!!.recreate()
        settle()
        assertEquals("precondition: the recreation reached the new activity", Lifecycle.State.RESUMED, scenario!!.state)
        assertEquals("precondition: the screen's saved state came back (a saveable value in the same screen)", PROBE_VALUE, harnessProbe!!.intValue)
        var fontScale = 0f
        scenario!!.onActivity { fontScale = it.resources.configuration.fontScale }
        assertEquals("precondition: the recreated activity's font scale", recreatedFontScale, fontScale, 0.001f)
        assertTrue("precondition: the recreated activity composed a new map (${map.mapsComposed} after $mapsBefore)", map.mapsComposed > mapsBefore)
    }

    @Test
    fun `with a route open the map reopens where the user left it`() {
        launch()
        open(firstTrip)
        val fitId = map.renderMode?.cameraRequest?.id
        assertNotNull("precondition: the open route asked the map to fit it", fitId)
        assertEquals("precondition: the route's fit applied on opening", listOf(fitId), map.applied)
        map.pan(leftAt)

        recreate()

        assertEquals("the camera is where the user left it", leftAt, map.camera)
        assertFalse("the map is not following, as the user left it", map.following)
        assertEquals("the saved route's fit was not applied again", listOf(fitId), map.applied)
    }

    @Test
    fun `with no route open the map reopens where the user left it`() {
        launch()
        assertEquals("precondition: no camera request without a route", null, map.renderMode?.cameraRequest)
        map.pan(leftAt)

        recreate()

        assertEquals("the camera is where the user left it", leftAt, map.camera)
        assertFalse("the map is not following, as the user left it", map.following)
        assertTrue("no frame was applied", map.applied.isEmpty())
    }

    @Test
    fun `a new route opened after the recreation still frames`() {
        launch()
        open(firstTrip)
        val fitId = map.renderMode?.cameraRequest?.id!!
        map.pan(leftAt)
        recreate()

        open(secondTrip)

        val newFit = map.renderMode?.cameraRequest?.id
        assertNotNull("the new route asked the map to fit it", newFit)
        assertTrue("the request is the new route's fit: $newFit", newFit!!.startsWith("saved-route-${secondTrip.id}-"))
        assertEquals("the new route's fit applied, after the first route's only fit", listOf(fitId, newFit), map.applied)
        assertEquals("the camera went to the new route's fit", RecreationMapStandIn.fitCamera(newFit), map.camera)
    }
}

private const val PROBE_VALUE = 7

/** A recreation at the same font, as a night-mode change does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class MapCameraOnRecreateTest : MapCameraOnRecreateTests(recreatedFontScale = 1.0f)

/** A font change from 1.0 to 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class MapCameraOnRecreateFontTwoTest : MapCameraOnRecreateTests(recreatedFontScale = 2.0f)
