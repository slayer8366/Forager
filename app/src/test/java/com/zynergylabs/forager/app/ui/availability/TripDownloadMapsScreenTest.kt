package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.routedata.contains
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.Duration
import java.time.LocalDate
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

/** The owner's words, held here as literals rather than read from the production constants. */
private const val STOPS_NOTE = "Some of this route's stops are outside this area. Download the rest as a second area."
private const val TRAIL_NOTE = "Part of this route's trail is outside this area. Download the rest as a second area."

/**
 * Dispatch 2026-11-05-08 (RECORD -2167, -2171): a trip's Download Maps in the Tools drawer's Trip Planner, through the real
 * screen and the real [AvailabilityViewModel], wired as MainActivity wires them ([MapLayersTestScreen]). Every touch is a real
 * one at screen coordinates, sampled across the control's own bounds ([ACROSS]); Back goes through the real dispatcher. The
 * picker map pans by a real drag, which settles where the test says, as [RealSearchScreenRig]'s does. Made-up points.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TripDownloadMapsScreenTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val downloads = RecordingOfflineMaps()
    private lateinit var viewModel: AvailabilityViewModel

    /** Where the next drag on a map settles its camera. */
    private var panTo = LatLng(0.0, 0.0)

    private val mapSlot: MapSlot = { _, _, renderMode, _, _, _, _, onCameraIdle, modifier ->
        val currentGesture by rememberUpdatedState(renderMode.onUserCameraGesture)
        val currentIdle by rememberUpdatedState(onCameraIdle)
        Box(
            modifier.testTag("trip-maps-map").pointerInput(Unit) {
                detectDragGestures(onDragStart = { currentGesture() }, onDragEnd = { currentIdle(panTo) }) { change, _ -> change.consume() }
            },
        )
    }

    private fun setScreen(vararg trips: PlannedTrip) {
        viewModel = mapLayersViewModel(plannedTrips = trips.toList(), offlineMapRepository = downloads)
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = mapSlot, store = AbsentForecastCellStore) }
        settle()
    }

    /** The screen and the ViewModel finish, the looper's clock included (RealSearchScreenRig.settle). */
    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
        composeRule.waitForIdle()
    }

    private fun touch(node: SemanticsNodeInteraction, fx: Float = 0.5f, fy: Float = 0.5f) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        settle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** A positive control that, failing, prints the screen's semantics tree with focus, so a test fault can be told from a finding. */
    private fun control(holds: Boolean, what: String) {
        if (holds) return
        val focused = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) ?: it.config.getOrNull(SemanticsProperties.EditableText)?.text ?: "?" }
        throw AssertionError("positive control: $what. Focused: $focused. Tree:\n" + composeRule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40))
    }

    private fun mapsSelected() = composeRule.onNodeWithText("Maps").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    private fun drawerShown(): Boolean = runCatching { composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).assertIsDisplayed() }.isSuccess

    private fun shown(text: String) = composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun pinLine(point: LatLng) = "Pin at: ${"%.4f".format(point.lat)}, ${"%.4f".format(point.lng)}"

    /** The line "Pin at: …" on screen now. */
    private fun pinText(): String = composeRule.onAllNodes(hasText("Pin at: ", substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes().single().config[SemanticsProperties.Text].joinToString { it.text }

    /** What the Offline Maps page's Name box holds. */
    private fun nameText(): String = composeRule.onNode(hasSetTextAction() and hasText("Name (optional)", substring = true))
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** The page's "Radius: …" line. */
    private fun radiusText(): String = composeRule.onAllNodes(hasText("Radius: ", substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes().single().config[SemanticsProperties.Text].joinToString { it.text }

    private fun offlineMapsShown() =
        shown("Download Maps") && composeRule.onAllNodes(hasText("Pin at: ", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The page's radius slider, the one control whose range is the offline radius's, 1 to 24 km. */
    private fun radiusSlider() = composeRule.onNode(
        SemanticsMatcher("the radius slider, 1 to 24 km") { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 1f..24f },
    )

    private fun noteShown() = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, TRIP_AREA_NOTE_TAG), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    /** Tools, then the Trip Planner fold on its first opening, each by a real touch. The fold is held above the drawer. */
    private fun openTripPlanner() {
        composeRule.openTools()
        assertTrue("positive control: the Tools drawer opened", drawerShown())
        touch(composeRule.onNodeWithTag(collapsibleSectionHeaderTag("Trip Planner")).performScrollTo())
    }

    private fun icon(trip: PlannedTrip) = composeRule.onNodeWithTag(tripDownloadMapsTag(trip.id)).performScrollTo()

    /** Tools, then its own Offline Maps row, by real touches: the page as anyone opens it today. */
    private fun openOfflineMapsFromTools() {
        composeRule.openTools()
        touch(composeRule.onNodeWithTag(TOOLS_OFFLINE_MAPS_ROW_TAG))
        assertTrue("positive control: Tools > Offline Maps shows the page", offlineMapsShown())
    }

    /** The page as Tools > Offline Maps shows it: its pin, its name and its radius. */
    private data class Page(val pin: String, val name: String, val radius: String)

    private fun page() = Page(pinText(), nameText(), radiusText())

    /** Tools > Offline Maps from the Maps tab, read, then Back twice to the Maps tab with the drawer closed. */
    private fun todaysPage(): Page {
        openOfflineMapsFromTools()
        val today = page()
        back()
        back()
        assertFalse("positive control: back on Maps with the drawer closed", drawerShown())
        return today
    }

    /** The page a trip's tap opened: shown, with [pin], [trip]'s name and [radius], and nothing downloading or asked. */
    private fun assertTripPage(where: String, trip: PlannedTrip, pin: LatLng, radius: String) {
        assertTrue("$where: the Offline Maps page shows", offlineMapsShown())
        assertFalse("$where: the drawer closed", drawerShown())
        assertEquals("$where: the pin is on the trip's point", pinLine(pin), pinText())
        assertEquals("$where: the Name box holds the trip's name", trip.name, nameText())
        assertEquals("$where: the radius", radius, radiusText())
        assertEquals("$where: no area download on the tap", emptyList<Pair<String, Region>>(), downloads.calls)
        assertFalse("$where: no \"Download this area?\" on the tap", shown("Download this area?"))
    }

    /** Back from the page: the Maps tab it was opened over, with Tools open again on the Trip Planner (the Tools row's way). */
    private fun assertBackToTheTripPlanner(where: String, trip: PlannedTrip) {
        back()
        composeRule.onNodeWithText("Maps").assertIsSelected()
        assertTrue("$where, then Back: Tools is open again", drawerShown())
        icon(trip).assertIsDisplayed()
    }

    @Test
    fun `real touches across a plain trip's maps icon open Offline Maps on the trip, and Back and a later opening are as today`() {
        setScreen(PLAIN)
        val today = todaysPage()
        openTripPlanner()
        icon(PLAIN).assertContentDescriptionEquals("Download Maps for ${PLAIN.name}")
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on the plain trip's maps icon"
            touch(icon(PLAIN), fx, fy)
            assertTripPage(where, PLAIN, PLAIN.location, radius = today.radius)
            assertFalse("$where: no route note on a plain trip", noteShown())
            assertBackToTheTripPlanner(where, PLAIN)
        }
        back()
        assertFalse("then Back again: the drawer closed", drawerShown())

        openOfflineMapsFromTools()
        assertEquals("Tools > Offline Maps afterwards opens as before the trip", today, page())
    }

    @Test
    fun `real touches across a route trip's maps icon open Offline Maps on the middle of its line, at the radius that covers it`() {
        setScreen(ROUTE_12)
        val today = todaysPage()
        openTripPlanner()
        icon(ROUTE_12).assertContentDescriptionEquals("Download Maps for ${ROUTE_12.name}")
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on the route trip's maps icon"
            touch(icon(ROUTE_12), fx, fy)
            assertTripPage(where, ROUTE_12, LatLng(40.02, -100.0), radius = "Radius: 12 km")
            assertFalse("$where: no note for a route one download covers", noteShown())

            // What the download would read: every point of the line inside its rectangle, and one km less leaving one out.
            val state = viewModel.uiState.value
            val center = LatLng(state.offlineMapLatText.toDouble(), state.offlineMapLngText.toDouble())
            val line = ROUTE_12.route!!.legs.flatMap { it.line }
            assertTrue("$where: every point of the line is inside the area that downloads", line.all { GeoDistance.boundingBox(center, state.offlineMapRadiusKm).contains(it) })
            assertFalse("$where: one km smaller leaves a point out", line.all { GeoDistance.boundingBox(center, state.offlineMapRadiusKm - 1).contains(it) })

            assertBackToTheTripPlanner(where, ROUTE_12)
        }
        back()

        openOfflineMapsFromTools()
        assertEquals("Tools > Offline Maps afterwards opens as before the trip", today, page())
    }

    /** A5: "Plain trip: maps, Directions, Delete" and "Route trip: maps, then Delete", the icon outside the route row's body. */
    @Test
    fun `the maps icon sits left of Directions on a plain trip and left of Delete, outside the body, on a route trip`() {
        setScreen(PLAIN, ROUTE_12)
        openTripPlanner()
        val maps = icon(PLAIN).getUnclippedBoundsInRoot()
        val directions = composeRule.onNodeWithContentDescription("Directions to ${PLAIN.name}").getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithContentDescription(plannedTripDeleteLabel(PLAIN)).getUnclippedBoundsInRoot()
        assertTrue("maps is left of Directions", maps.right <= directions.left)
        assertTrue("Directions is left of Delete", directions.right <= delete.left)

        val routeMaps = icon(ROUTE_12).getUnclippedBoundsInRoot()
        val body = composeRule.onNodeWithTag(plannedRouteRowTag(ROUTE_12.id)).getUnclippedBoundsInRoot()
        val routeDelete = composeRule.onNodeWithTag(plannedRouteDeleteTag(ROUTE_12.id)).getUnclippedBoundsInRoot()
        assertTrue("the route's maps icon is right of its body", body.right <= routeMaps.left)
        assertTrue("the route's maps icon is left of Delete", routeMaps.right <= routeDelete.left)
    }

    @Test
    fun `a route too big for one download opens at 24 km with the owner's stops note, which goes when the pin or the radius moves`() {
        setScreen(ROUTE_STOPS_OUTSIDE)
        openTripPlanner()
        touch(icon(ROUTE_STOPS_OUTSIDE))
        assertTripPage("a route bigger than 24 km", ROUTE_STOPS_OUTSIDE, LatLng(40.3, -100.0), radius = "Radius: 24 km")
        assertTrue("the owner's note shows when a stop is outside at 24 km", shown(STOPS_NOTE))
        assertFalse("the trail note does not show beside it", shown(TRAIL_NOTE))

        panTo = LatLng(40.5, -100.0)
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).performTouchInput {
            swipe(start = Offset(width * 0.3f, height * 0.3f), end = Offset(width * 0.6f, height * 0.7f), durationMillis = 300)
        }
        settle()
        assertEquals("positive control: the pin moved", pinLine(panTo), pinText())
        assertFalse("the note goes once the pin moves", noteShown())

        // The trip's area again, then a real touch on the slider's track near its start.
        composeRule.openTools()
        touch(icon(ROUTE_STOPS_OUTSIDE))
        assertTrue("positive control: the note is back with the trip's area", shown(STOPS_NOTE))
        touch(radiusSlider().performScrollTo(), 0.1f, 0.5f)
        assertTrue("positive control: the radius moved", radiusText() != "Radius: 24 km")
        assertFalse("the note goes once the radius moves", noteShown())
    }

    @Test
    fun `a route whose stops fit at 24 km but whose trail doesn't opens with the owner's trail note, and not the stops note`() {
        setScreen(ROUTE_TRAIL_OUTSIDE)
        openTripPlanner()
        touch(icon(ROUTE_TRAIL_OUTSIDE))
        assertTripPage("a route whose trail bends past 24 km", ROUTE_TRAIL_OUTSIDE, LatLng(40.05, -100.0), radius = "Radius: 24 km")
        assertTrue("the owner's trail note shows when every stop fits but part of the line doesn't", shown(TRAIL_NOTE))
        assertFalse("the stops note does not show: every stop is inside", shown(STOPS_NOTE))
    }

    /** B3: Tools opens over the page, so the page is still composed, with the pin the user panned to. */
    @Test
    fun `an Offline Maps page already open and panned moves its pin to the trip`() {
        setScreen(PLAIN)
        openOfflineMapsFromTools()
        panTo = LatLng(41.0, -101.0)
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).performTouchInput {
            swipe(start = Offset(width * 0.3f, height * 0.3f), end = Offset(width * 0.6f, height * 0.7f), durationMillis = 300)
        }
        settle()
        assertEquals("positive control: the pin was panned", pinLine(panTo), pinText())

        openTripPlanner()
        touch(icon(PLAIN))

        assertEquals("an open, panned page: the pin moved to the trip", pinLine(PLAIN.location), pinText())
        assertEquals("an open, panned page: the Name box holds the trip's name", PLAIN.name, nameText())
    }

    /** A2: "the page puts back what it held before the tap", the user's own pin, name and radius included. */
    @Test
    fun `leaving a trip's page without downloading puts back the user's own pin, name and radius`() {
        setScreen(ROUTE_12)
        openOfflineMapsFromTools()
        panTo = LatLng(41.0, -101.0)
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).performTouchInput {
            swipe(start = Offset(width * 0.3f, height * 0.3f), end = Offset(width * 0.6f, height * 0.7f), durationMillis = 300)
        }
        settle()
        composeRule.onNode(hasSetTextAction() and hasText("Name (optional)", substring = true)).performTextReplacement("My spot")
        settle()
        touch(composeRule.onNodeWithText("Download Maps").performScrollTo())
        touch(composeRule.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())))
        val mine = page()
        assertEquals("positive control: the user's own pin", pinLine(panTo), mine.pin)
        assertEquals("positive control: the user's own name", "My spot", mine.name)
        touch(composeRule.onNodeWithText("Maps"))
        control(mapsSelected() && !offlineMapsShown(), "the Maps tab is up, Offline Maps left")

        openTripPlanner()
        touch(icon(ROUTE_12))
        assertEquals("positive control: the trip's radius", "Radius: 12 km", radiusText())
        back()
        control(mapsSelected() && drawerShown(), "the first Back put back Maps with Tools open")
        back()
        control(!drawerShown(), "the second Back closed Tools")

        openOfflineMapsFromTools()
        assertEquals("Tools > Offline Maps afterwards opens as before the trip, the user's own entry put back", mine, page())
        assertEquals("nothing was downloaded", emptyList<Pair<String, Region>>(), downloads.calls)
    }

    /** A download from a trip's page is not undone by leaving: the page after it is the page after any download. */
    @Test
    fun `a trip's area downloaded from its page stays downloaded, and leaving does not put back the earlier entry`() {
        setScreen(PLAIN)
        val today = todaysPage()
        openOfflineMapsFromTools()
        composeRule.onNode(hasSetTextAction() and hasText("Name (optional)", substring = true)).performTextReplacement("My spot")
        settle()
        touch(composeRule.onNodeWithText("Maps"))
        control(mapsSelected() && !offlineMapsShown(), "the Maps tab is up, Offline Maps left")

        openTripPlanner()
        touch(icon(PLAIN))
        control(offlineMapsShown() && !drawerShown(), "the trip's tap opened Offline Maps with Tools closed")
        touch(composeRule.onNodeWithText("Download Maps").performScrollTo())
        control(shown("Download this area?"), "Download Maps asked \"Download this area?\"")
        touch(composeRule.onNode(hasText("Download") and hasAnyAncestor(isDialog())))
        assertEquals("the trip's area downloaded under its name", listOf(PLAIN.name to PLAIN.location), downloads.calls.map { (name, region) -> name to LatLng(region.lat, region.lng) })
        back()
        back()

        openOfflineMapsFromTools()
        assertEquals("after the download, the page is today's, not the entry from before the trip", today, page())
    }

    private class RecordingOfflineMaps : OfflineMapRepository {
        val calls = mutableListOf<Pair<String, Region>>()
        private val regions = mutableListOf<OfflineRegionSummary>()

        override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> {
            calls += name to region
            val summary = OfflineRegionSummary(calls.size.toLong(), name, region, 10.0, 15.0, tileCount = 1, sizeBytes = 1, createdAtEpochMillis = 0)
            regions += summary
            return Result.success(summary)
        }

        override suspend fun deleteRegion(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions.toList())
    }

    private companion object {
        fun stop(name: String, lat: Double, lng: Double) = RouteStop(name, LatLng(lat, lng))

        fun route(a: RouteStop, b: RouteStop, vararg via: LatLng): PlottedRoute {
            val line = listOf(a.position) + via + b.position
            val leg = if (via.isEmpty()) {
                RouteLeg.Straight(line, GeoDistance.metersBetween(a.position, b.position), com.zynergylabs.forager.app.domain.route.StraightReason.NO_REGION_COVERS)
            } else {
                RouteLeg.Trail(line, 1_000.0, trailFrom = 0, trailTo = line.lastIndex)
            }
            return PlottedRoute(listOf(a, b), listOf(leg))
        }

        fun routeTrip(id: String, name: String, route: PlottedRoute) = PlannedTrip(id, name, route.stops.first().position, null, route)

        val PLAIN = PlannedTrip("trip-plain", "Test trip A", LatLng(40.0, -100.0), LocalDate.of(2030, 6, 1))

        /** A trail that bends 0.08 degrees south and 0.10 north of stops 0.02 apart: 12 km, centred on the line's middle. */
        val ROUTE_12 = routeTrip(
            "trip-route-12", "Test route B",
            route(stop("A", 40.00, -100.0), stop("B", 40.02, -100.0), LatLng(39.92, -100.0), LatLng(40.12, -100.0)),
        )

        /** Stops 0.6 degrees apart, 33 km each way from the middle: both outside at 24 km. */
        val ROUTE_STOPS_OUTSIDE = routeTrip("trip-route-big", "Test route C", route(stop("A", 40.0, -100.0), stop("B", 40.6, -100.0)))

        /** Stops 0.1 apart, the trail 0.3 each way from the middle: both stops inside at 24 km, the bends outside. */
        val ROUTE_TRAIL_OUTSIDE = routeTrip(
            "trip-route-bends", "Test route D",
            route(stop("A", 40.0, -100.0), stop("B", 40.1, -100.0), LatLng(39.75, -100.0), LatLng(40.35, -100.0)),
        )
    }
}
