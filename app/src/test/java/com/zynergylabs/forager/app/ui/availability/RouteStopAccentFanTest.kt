package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailRecord
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.RouteStopLink
import com.zynergylabs.forager.app.testing.MadeUpPlace
import com.zynergylabs.forager.app.ui.map.FAN_ACCENT_KEY_PROPERTY
import com.zynergylabs.forager.app.ui.map.FEATURE_ID_PROPERTY
import com.zynergylabs.forager.app.ui.map.FanOutIds
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.MarkerIcon
import com.zynergylabs.forager.app.ui.map.PlottedRouteIds
import com.zynergylabs.forager.app.ui.map.fanFrameCollections
import com.zynergylabs.forager.app.ui.map.fanHiddenAccentKeys
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.FanOffset
import com.zynergylabs.forager.app.ui.map.fanout.FanOutTestScene
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.fanout.memberPositionDp
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import com.zynergylabs.forager.app.ui.map.plottedRouteFeatureCollections
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 6 B (RECORD -2187), the owner: "When fanning out, the highlighted icon stays put while the icon it
 * represents is in the fan. Add the highlight to the fan without any duplicates". While a stack holding the selected stop is fanned,
 * the accent is on that stop's copy at its fanned place and nowhere at the stack's centre; when the fan closes it is back on the pin.
 *
 * On the real compact Maps tab with the real [RoutePlottingViewModel]: the route, the stack and a fanned copy are each reached by a
 * real touch, through the real [MapTapHandler] and fan clock, and Back goes through the activity's dispatcher. The map is a stub (a
 * `MapView` cannot be built under Robolectric), as in [AvailabilityScreenCrowdedStackTest], with SightingsMap's line that hands the
 * fan the selected stop (`accent = plottedRoute?.selected?.fanKey`). What is read is what the map is given: the fan's copies as
 * [fanFrameCollections] builds them, and the accent source ([plottedRouteFeatureCollections]) against the keys the accent layer hides
 * ([fanHiddenAccentKeys], the filter `applyFanOutHiding` sets). That MapLibre then draws them is the S22 step's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteStopAccentFanTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val fan = MarkerFanOutState()
    private var scene: FanOutTestScene? = null
    private var content: MapOverlayContent? = null

    private val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        this.content = content
        val density = LocalDensity.current.density
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnFeatureTap by rememberUpdatedState(renderMode.onFeatureTap)
        val currentOnCloseBubble by rememberUpdatedState(renderMode.onCloseBubble)
        val bubbleShown = content.focusedFeature != null || content.focusedObservationId != null
        val currentBubbleShown by rememberUpdatedState(bubbleShown)
        val probe = remember {
            FanOutTestScene(density).also { s ->
                scene = s
                s.hidden = { fan.members.map { m -> m.key }.toSet() }
            }
        }
        val handler = remember {
            MapTapHandler(
                fan = fan,
                probe = probe,
                drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) },
                sinks = object : MapTapSinks {
                    override fun onPlainTap() = currentOnTap()
                    override fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float) = currentOnTap()
                    override fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng) =
                        currentOnFeatureTap(MapFeatureTap(layerId, featureId, Offset(xPx, yPx), 0f, at))
                    override fun onUnidentifiedFeature(layerId: String) = currentOnTap()
                    override fun onCloseBubble() = currentOnCloseBubble()
                    override fun onReferenceTrailTap(layerId: String, record: ReferenceTrailRecord, xPx: Float, yPx: Float, at: LatLng) =
                        currentOnFeatureTap(MapFeatureTap.forReferenceTrail(layerId, record, Offset(xPx, yPx), 0f, at))
                },
                bubbleOpen = { currentBubbleShown },
            )
        }
        // SightingsMap's own lines: the host is told of the fan, and the fan's Back is composed while it is open.
        SideEffect { renderMode.crowdedStacks?.fan = fan }
        MarkerFanOutHost(fan)
        MarkerFanOutBackHandler(fan, bubbleOpen = bubbleShown, backEnabled = renderMode.backEnabled)
        Box(modifier.testTag(ACCENT_FAN_MAP_TAG).pointerInput(Unit) { detectTapGestures(onTap = { handler.onMapTap(LatLng(0.0, 0.0), it.x, it.y) }) })
    }

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private val density get() = composeRule.density.density

    private fun setScreen(waypoints: List<Waypoint>, plannedTrips: List<PlannedTrip>) {
        val viewModel = mapLayersViewModel(plannedTrips = plannedTrips)
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = slot,
                store = OneCellStore(),
                waypoints = waypoints,
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** A real touch on the map at ([xDp], [yDp]) of the map view. */
    private fun touchMap(xDp: Float, yDp: Float) {
        composeRule.onNodeWithTag(ACCENT_FAN_MAP_TAG).performTouchInput { click(Offset(xDp * density, yDp * density)) }
        settle()
    }

    /** Places a marker of the scene at ([xDp], [yDp]) of the map view. */
    private fun place(layerId: String, featureId: String, xDp: Float, yDp: Float) =
        checkNotNull(scene).addAtScreen(layerId, featureId, xDp * density, yDp * density)

    /** The test's projection for the fan's copies: a copy's place on screen, in dp, as a coordinate (lat = y / 10, lng = x / 10, in range). */
    private fun dpAsLatLng(at: FanOffset) = LatLng(at.yDp / 10.0, at.xDp / 10.0)

    /** The fan's copies as the map is given them now, with SightingsMap's accent (the route's selected stop). */
    private fun copies(): List<Feature> = fanFrameCollections(
        fan.members, { dpAsLatLng(memberPositionDp(it, fan.progress)) }, null, fan.progress,
        drawOrder = orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT),
        accent = content?.plottedRoute?.selected?.fanKey,
    ).icons.features().orEmpty()

    private fun copiesWith(icon: MarkerIcon) = copies().filter { it.getStringProperty(FanOutIds.IMAGE_PROPERTY) == icon.imageId }

    private fun copyOf(id: String) = copies().single { it.getStringProperty(FEATURE_ID_PROPERTY) == id }

    private fun at(feature: Feature): LatLng = (feature.geometry() as Point).let { LatLng(it.latitude(), it.longitude()) }

    private fun member(layerId: String, id: String): FanMember = fan.members.single { it.key == FanKey(layerId, id) }

    /** The accent at the selected stop's own spot, as its source holds it: `null` for none. */
    private fun centreAccent(): Feature? =
        plottedRouteFeatureCollections(content?.plottedRoute).getValue(PlottedRouteIds.SELECTED_SOURCE).features().orEmpty().singleOrNull()

    /** Whether the accent layer draws that accent now: it is there, and its marker is not one the fan hides. */
    private fun centreAccentDrawn(): Boolean {
        val accent = centreAccent() ?: return false
        assertTrue("the accent names the marker it stands on", accent.hasProperty(FAN_ACCENT_KEY_PROPERTY))
        return accent.getStringProperty(FAN_ACCENT_KEY_PROPERTY) !in fanHiddenAccentKeys(fan.members)
    }

    @Test
    fun `a selected waypoint in a fanned stack carries the accent at its fanned place, none at the centre, and it is back when the fan closes`() {
        val third = Waypoint("wp-third", MadeUpPlace.LAT + 0.001, MadeUpPlace.LNG, null, "Third", "", 3_000L)
        setScreen(waypoints = listOf(CREEK, RIDGE, third), plannedTrips = listOf(ROUTE_TRIP))
        place(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, ROUTE_X_DP, ROUTE_Y_DP)
        listOf(CREEK, RIDGE, third).forEach { place(MapLayerIds.WAYPOINTS, it.id, STACK_X_DP, STACK_Y_DP) }

        touchMap(ROUTE_X_DP, ROUTE_Y_DP)
        assertEquals("the route opened", ROUTE_TRIP.id, (plotting.uiState.value.mode as? RoutePlotMode.Showing)?.trip?.id)
        assertEquals("precondition: Creek, the first stop, is selected", FanKey(MapLayerIds.WAYPOINTS, CREEK.id), content?.plottedRoute?.selected?.fanKey)
        assertTrue("precondition: no fan, the accent on Creek's pin", centreAccentDrawn())
        assertTrue("precondition: no copies", copies().isEmpty())

        touchMap(STACK_X_DP, STACK_Y_DP)
        assertTrue("the stack fanned", fan.isOpen)
        assertEquals(3, fan.members.size)
        assertEquals("spread", 1f, fan.progress, 0f)

        val creek = member(MapLayerIds.WAYPOINTS, CREEK.id)
        val creekFanned = dpAsLatLng(memberPositionDp(creek, 1f))
        assertNotEquals("Creek's fanned place is away from the stack", dpAsLatLng(memberPositionDp(creek, 0f)), creekFanned)
        assertEquals("one copy carries the accent, Creek's", listOf(CREEK.id), copiesWith(MarkerIcon.WAYPOINT_ROUTE_SELECTED).map { it.getStringProperty(FEATURE_ID_PROPERTY) })
        assertEquals("the accent is at Creek's fanned place", creekFanned, at(copyOf(CREEK.id)))
        assertEquals("the others keep the plain pin", 2, copiesWith(MarkerIcon.WAYPOINT).size)
        assertFalse("no accent left at the stack's centre", centreAccentDrawn())

        // Ridge picked from the open fan by a real touch on its copy: the accent moves to Ridge's copy, still none at the centre.
        val ridgeFanned = memberPositionDp(member(MapLayerIds.WAYPOINTS, RIDGE.id), 1f)
        touchMap(ridgeFanned.xDp, ridgeFanned.yDp)
        assertEquals("Ridge is selected", FanKey(MapLayerIds.WAYPOINTS, RIDGE.id), content?.plottedRoute?.selected?.fanKey)
        assertTrue("the fan stays open", fan.isOpen)
        assertEquals("the accent moved to Ridge's copy", listOf(RIDGE.id), copiesWith(MarkerIcon.WAYPOINT_ROUTE_SELECTED).map { it.getStringProperty(FEATURE_ID_PROPERTY) })
        assertEquals("at Ridge's fanned place", dpAsLatLng(ridgeFanned), at(copyOf(RIDGE.id)))
        assertEquals("Creek's copy is plain again", MarkerIcon.WAYPOINT.imageId, copyOf(CREEK.id).getStringProperty(FanOutIds.IMAGE_PROPERTY))
        assertFalse("still no accent at the stack's centre", centreAccentDrawn())

        closeTheFan()
        assertTrue("no copies once folded", copies().isEmpty())
        assertTrue("the accent is back on Ridge's pin", centreAccentDrawn())
        assertEquals("in its place", LatLng(RIDGE.lat, RIDGE.lng), at(checkNotNull(centreAccent())))
    }

    @Test
    fun `a selected trip stop's flag goes into the fan the same way`() {
        val saddle = PlannedTrip("trip-saddle", "Saddle loop", LatLng(MadeUpPlace.LAT + 0.02, MadeUpPlace.LNG), null)
        val bench = PlannedTrip("trip-bench", "Bench walk", LatLng(MadeUpPlace.LAT + 0.021, MadeUpPlace.LNG), null)
        val saddleStop = RouteStop(saddle.name, saddle.location, RouteStopLink.Trip(saddle.id))
        val routeTrip = PlannedTrip(
            "route-saddle", "Saddle to Creek", saddle.location, null,
            PlottedRoute(listOf(saddleStop, CREEK.stop()), listOf(straight(saddle.location, CREEK.stop().position))),
        )
        setScreen(waypoints = listOf(CREEK), plannedTrips = listOf(saddle, bench, routeTrip))
        place(MapLayerIds.PLANNED_TRIPS, routeTrip.id, ROUTE_X_DP, ROUTE_Y_DP)
        place(MapLayerIds.PLANNED_TRIPS, saddle.id, STACK_X_DP, STACK_Y_DP)
        place(MapLayerIds.PLANNED_TRIPS, bench.id, STACK_X_DP, STACK_Y_DP)

        touchMap(ROUTE_X_DP, ROUTE_Y_DP)
        assertEquals("the route opened", routeTrip.id, (plotting.uiState.value.mode as? RoutePlotMode.Showing)?.trip?.id)
        assertEquals("precondition: Saddle, a trip, is selected", FanKey(MapLayerIds.PLANNED_TRIPS, saddle.id), content?.plottedRoute?.selected?.fanKey)
        assertTrue("precondition: the accent flag on Saddle", centreAccentDrawn())

        touchMap(STACK_X_DP, STACK_Y_DP)
        assertTrue("the stack fanned", fan.isOpen)
        assertEquals(2, fan.members.size)

        val saddleFanned = dpAsLatLng(memberPositionDp(member(MapLayerIds.PLANNED_TRIPS, saddle.id), 1f))
        assertEquals("one copy carries the accent flag, Saddle's", listOf(saddle.id), copiesWith(MarkerIcon.PLANNED_TRIP_ROUTE_SELECTED).map { it.getStringProperty(FEATURE_ID_PROPERTY) })
        assertEquals("at Saddle's fanned place", saddleFanned, at(copyOf(saddle.id)))
        assertEquals("Bench keeps the plain flag", MarkerIcon.PLANNED_TRIP.imageId, copyOf(bench.id).getStringProperty(FanOutIds.IMAGE_PROPERTY))
        assertFalse("no accent flag left at the stack's centre", centreAccentDrawn())

        closeTheFan()
        assertTrue("no copies once folded", copies().isEmpty())
        assertTrue("the accent flag is back on Saddle", centreAccentDrawn())
        assertEquals("in its place", saddle.location, at(checkNotNull(centreAccent())))
    }

    /** Back until the fan has folded and let go of its members: a bubble a fanned tap opened closes first (-255), then the fan. */
    private fun closeTheFan() {
        repeat(2) { if (fan.members.isNotEmpty()) back() }
        assertTrue("the fan closed", fan.members.isEmpty())
        assertTrue("the route still shows", plotting.uiState.value.mode is RoutePlotMode.Showing)
    }

    private companion object {
        const val ACCENT_FAN_MAP_TAG = "accent-fan-map"
        const val ROUTE_X_DP = 200f
        const val ROUTE_Y_DP = 260f
        const val STACK_X_DP = 190f
        const val STACK_Y_DP = 420f
    }
}
