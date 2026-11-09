package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.cameraRequestStopsFollowing
import com.zynergylabs.forager.app.ui.map.shouldApplyCameraRequest
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * RECORD -2192 (the owner: "Fix: remember framing (Recommended)", "After the restart the map carries on following you; nothing
 * else changes"). Navigating with a saved route's card open, a font or night-mode change recreates MainActivity, which does not
 * handle those configuration changes itself. The Maps tab's camera memory was a plain `remember`, so the recreated map had no
 * record that the saved route's fit (`savedRouteCameraRequest`, id `saved-route-<trip>-<serial>`) was already applied; it took
 * the fit as new, stopped following and framed the route (at font 2.0, the whole world), and the navigation view saved "follow
 * broken" for good.
 *
 * Driven through the real Maps tab ([MapLayersTestScreen] over the real `AvailabilityScreen`) and a real
 * `ActivityScenario.recreate()`, at the font the recreation brings. The screen is set in the activity's `onCreate`
 * ([KeepFollowHostActivity]), as MainActivity sets its own, so the recreated activity restores saved state as the app does; a
 * first version that set the content again after the recreation restored none (probe run 42563641078: a saveable counter set to
 * 7 came back 0), so every recreation here first checks that a saveable value in the same screen survived. The route plotting
 * ViewModel and the screen's ViewModel are made outside the activity, so they survive the recreation as the real ViewModels do.
 *
 * A real MapView cannot run under Robolectric, so the map here is [FollowingMapSlot]: it applies the screen's camera requests
 * with the production rules MapDataRefresh uses (`cameraRequestStopsFollowing` and `shouldApplyCameraRequest` against
 * `MapCameraMemory.appliedCameraRequestId`, else the map's own id; MapDataRefresh.kt:109-125 at 3cfa59d61). What MapLibre
 * then does with a frame, and the navigation view's listener, are the S22's to show.
 */
abstract class KeepFollowOnRecreateTests(private val recreatedFontScale: Float) {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<KeepFollowHostActivity>? = null

    /** A saveable value in the same screen, set before each recreation: if it does not come back, nothing saveable did. */
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

    private fun activity(): ComponentActivity {
        var current: ComponentActivity? = null
        scenario!!.onActivity { current = it }
        return current!!
    }

    private val map = FollowingMapSlot()

    private val stops = listOf(
        CREEK,
        RIDGE,
        Waypoint("wp-falls", 45.3936, -121.7159, null, "Falls", "", 3_000L),
    )

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private val firstTrip = trip("keep-follow-1", "Creek to Ridge", 2)
    private val secondTrip = trip("keep-follow-2", "Creek to Falls", 3)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.37, lng = -121.69, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val origin = Waypoint(id = "origin", lat = 45.38, lng = -121.69, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1")

    private val store = OneCellStore()
    private val viewModel = mapLayersViewModel()

    private fun trip(id: String, name: String, n: Int): PlannedTrip {
        val shown = stops.take(n).map { it.stop() }
        val legs = shown.zipWithNext { a, b -> straight(a.position, b.position) }
        return PlannedTrip(id, name, shown.first().position, null, PlottedRoute(shown, legs))
    }

    /** The Maps tab, navigating to [origin], with both saved trips stored (a trip missing from the store closes its card). */
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
            uiStateTransform = { it.copy(liveFix = fix, plannedTrips = listOf(firstTrip, secondTrip)) },
            navigation = AvailabilityScreenNavigation(isReturning = true, onToggleReturning = {}, navigationTarget = origin),
            recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = {}),
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

    /** A font change (to [recreatedFontScale]) or a night-mode change: the activity is recreated and sets the same screen again. */
    private fun recreate() {
        val mapsBefore = map.mapsComposed
        val activityBefore = activity()
        harnessProbe!!.intValue = HARNESS_PROBE_VALUE
        settle()
        RuntimeEnvironment.setFontScale(recreatedFontScale)
        scenario!!.recreate()
        settle()
        assertEquals("precondition: the recreation reached the new activity", Lifecycle.State.RESUMED, scenario!!.state)
        assertTrue("precondition: a new activity", activity() !== activityBefore)
        assertEquals("precondition: the screen's saved state came back (a saveable value in the same screen)", HARNESS_PROBE_VALUE, harnessProbe!!.intValue)
        assertEquals("precondition: the recreated activity's font scale", recreatedFontScale, activity().resources.configuration.fontScale, 0.001f)
        assertTrue("precondition: the recreated activity composed a new map (${map.mapsComposed} after $mapsBefore)", map.mapsComposed > mapsBefore)
    }

    /** Navigating, a saved route opened (its fit applied, which ends following), and the user re-centred, so the map follows. */
    private fun navigatingWithTheRouteOpenAndFollowing(): String {
        scenario = ActivityScenario.launch(KeepFollowHostActivity::class.java)
        settle()
        open(firstTrip)
        val fit = map.renderMode?.cameraRequest
        assertNotNull("precondition: the open route asked the map to fit it", fit)
        assertTrue("precondition: the request is the saved route's fit: ${fit!!.id}", fit.id.startsWith("saved-route-${firstTrip.id}-"))
        assertEquals("precondition: the route's fit applied once on opening", listOf(fit.id), map.applied)
        assertEquals("precondition: opening the route ended following once", 1, map.followingStops)
        map.recenter()
        return fit.id
    }

    @Test
    fun `after a recreation the map carries on following and the saved route is not fitted again`() {
        val fitId = navigatingWithTheRouteOpenAndFollowing()

        recreate()

        assertEquals("the screen still asks for the same fit after the recreation", fitId, map.renderMode?.cameraRequest?.id)
        assertEquals("the saved route's fit was applied once, not again after the recreation", listOf(fitId), map.applied)
        assertEquals("following was not ended by the recreation", 1, map.followingStops)
        assertTrue("the recreated map is still following", map.following)
    }

    @Test
    fun `a new route opened after a recreation still frames`() {
        val fitId = navigatingWithTheRouteOpenAndFollowing()
        recreate()

        open(secondTrip)

        val newFit = map.renderMode?.cameraRequest?.id
        assertNotNull("the new route asked the map to fit it", newFit)
        assertTrue("the request is the new route's fit: $newFit", newFit!!.startsWith("saved-route-${secondTrip.id}-"))
        assertEquals("the new route's fit applied, after the first route's only fit", listOf(fitId, newFit), map.applied)
        assertEquals("opening the new route ended following, as opening a route does", 2, map.followingStops)
    }
}

private const val HARNESS_PROBE_VALUE = 7

/** Sets [content] in `onCreate`, as MainActivity sets its screen, so a recreation restores saved state as the app's does. */
class KeepFollowHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = content ?: error("KeepFollowHostActivity: no content set")
        setContent { screen() }
    }

    companion object {
        var content: (@Composable () -> Unit)? = null
    }
}

/** A recreation at the same font, as a night-mode change does (the S22 at 18:24, RECORD -2192). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class KeepFollowOnRecreateTest : KeepFollowOnRecreateTests(recreatedFontScale = 1.0f)

/** A font change from 1.0 to 2.0, the S22's case that went to the whole world (RECORD -2192). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class KeepFollowOnRecreateFontTwoTest : KeepFollowOnRecreateTests(recreatedFontScale = 2.0f)

/**
 * The map, standing in for SightingsMap's request handling (MapDataRefresh.kt:109-125 at 3cfa59d61) with the same production
 * rules: a request that stops following ends following once per id, and a request applies when not following and not already
 * applied. "Already applied" is the screen's `MapCameraMemory.appliedCameraRequestId`, else this map's own id, as there. Each
 * map starts following, as a fresh map in the navigation view does while "follow broken" is not saved (NavigationView.kt).
 */
private class FollowingMapSlot {
    /** Every request id applied, in order, across every map this slot drew. */
    val applied = mutableListOf<String>()
    var followingStops = 0
    var mapsComposed = 0
    var renderMode: MapRenderMode? = null
    private var currentFollowing: BooleanArray = booleanArrayOf(true)

    val following: Boolean get() = currentFollowing[0]

    /** The user's Re-center: the current map follows again. */
    fun recenter() {
        currentFollowing[0] = true
    }

    val slot: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
        this.renderMode = renderMode
        val ownApplied = remember { arrayOfNulls<String>(1) }
        val followingNow = remember { booleanArrayOf(true) }
        LaunchedEffect(Unit) {
            mapsComposed++
            currentFollowing = followingNow
        }
        val request = renderMode.cameraRequest
        LaunchedEffect(request) {
            val memory = renderMode.cameraMemory
            val appliedId = memory?.appliedCameraRequestId ?: ownApplied[0]
            if (cameraRequestStopsFollowing(followingNow[0], request, appliedId)) {
                followingNow[0] = false
                followingStops++
            }
            if (request != null && shouldApplyCameraRequest(followingNow[0], request, appliedId)) {
                applied += request.id
                ownApplied[0] = request.id
                memory?.appliedCameraRequestId = request.id
            }
        }
        Box(modifier.testTag("map-slot"))
    }
}
