package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ReturnLeg
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_SHEET_TAG
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_TITLE_TAG
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-502 (plan tasks T8 and T9) with Amendments 1 and 2, through the real
 * [AvailabilityScreen] and the real [AvailabilityViewModel]: "Navigate" from a waypoint's bubble on the
 * Maps tab, its details sheet there, a Records row and the details sheet from Records each navigate to
 * that waypoint, with or without a recording: the HUD aims at it with its straight-line distance, the
 * navigation control is the X in a circle, and the map is handed the dashed line from the walker to it.
 * Arrival is the start's (the ring and "Arrived"). Back ends it and goes back to where Navigate was
 * tapped; the X-circle and the HUD's ✕ end it where the walker is. A return under way is paused and picks
 * back up. A waypoint deleted meanwhile, or while the app was closed, ends it with a message. An entry
 * map's bubble offers Navigate too since RECORD -890, and Back from it returns to the entry.
 *
 * Every touch on a control is a real one at screen coordinates (CLAUDE.md, Testing); getting to a tab may
 * be a semantic click. The map is [BubbleMapSlot], which records what it is handed and reports glyph taps
 * as `SightingsMap` does: how MapLibre draws the line and the ring is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenWaypointNavigateTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val creek = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)
    private val oak = Waypoint("wp-2", 45.30, -122.60, null, "Big oak", "", 2_000L)
    private val origin = Waypoint("origin", 45.31, -122.634, null, "Start", "", 500L, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    /** A fix [metersSouth] metres due south of the creek pin, with the S22's constant 3.79 m accuracy. */
    private fun southOfCreek(metersSouth: Double) =
        LocationFix.Update(lat = creek.lat - metersSouth / 111_195.08, lng = creek.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private val returnLine = RouteLine(
        atReturn = listOf(LatLng(45.32, -122.634), LatLng(45.31, -122.634)),
        ahead = listOf(LatLng(45.32, -122.634), LatLng(45.31, -122.634)),
        aheadIsCurrent = true,
    )

    private var fix by mutableStateOf(southOfCreek(500.0))
    /** False while the phone has no fix at all, as just after the app opens. */
    private var fixPresent by mutableStateOf(true)
    private var waypoints by mutableStateOf(listOf(creek, oak))
    private var waypointsLoaded by mutableStateOf(true)
    private var recording by mutableStateOf(false)
    private var returning by mutableStateOf(false)
    private var cartography by mutableStateOf(CartographyUiState())

    private val store = InMemoryWaypointNavigation()
    private var pauses = 0
    private var resumes = 0

    /** The return, as `TrackRecordingReturnLeg` hands it over: pausing is the return's Stop, resuming its Return. */
    private val leg = object : ReturnLeg {
        override val isReturning: Boolean get() = returning
        override fun pause() {
            pauses++
            returning = false
        }
        override fun resume() {
            resumes++
            returning = true
        }
    }

    private var map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, "wp-1", 60.dp, 380.dp, LatLng(creek.lat, creek.lng))))
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen(entryMapData: CartographyEntryMapData? = null) {
        viewModel = mapLayersViewModel(waypointNavigationRepository = store, returnLeg = leg)
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = state.copy(liveFix = if (fixPresent) fix else null),
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = map.slot,
                records = AvailabilityScreenRecords(
                    waypoints = waypoints,
                    waypointsLoaded = waypointsLoaded,
                ),
                recording = AvailabilityScreenRecording(
                    isRecording = recording,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning,
                    onToggleReturning = { returning = !returning },
                    navigationTarget = if (recording) origin else null,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.32, -122.634), 1_800.0),
                    routeLine = if (returning) returnLine else null,
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                    onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                ),
                compassProvider = FixedCompass(0f),
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { t + 1_000L },
                journalEntries = AvailabilityScreenJournalEntries(
                    cartographyUiState = cartography,
                ),
                entryReportData = AvailabilityScreenEntryReportData(
                    getCartographyEntryMapData = { _, _ -> entryMapData ?: CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) },
                ),
            )
        }
        composeRule.waitForIdle()
    }

    // ── Helpers ──

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun controlLabel(): String? =
        composeRule.onNodeWithTag(RETURN_CONTROL_TAG).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    private fun openBubble() = touchCentreOf(glyphTag("wp-1"))

    private fun openRecords() {
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun touchRowNavigate() {
        composeRule.onNodeWithTag(waypointRowNavigateTag("wp-1")).performScrollTo()
        touchCentreOf(waypointRowNavigateTag("wp-1"))
    }

    private fun navigatingTo(): WaypointNavigation? = viewModel.uiState.value.waypointNavigation

    /** The HUD aims at the creek pin, due north of a walker facing north, with the straight line to it in the large slot. */
    private fun assertHudAimsAtCreek() {
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals("the needle aims at the waypoint", "ahead 0°", text(NAVIGATION_HUD_TARGET_TAG))
        val straight = GeoDistance.metersBetween(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng))
        assertEquals(formatDistanceWithAccuracy(straight, fix.accuracyMeters, viewModel.uiState.value.distanceUnit), text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("the straight-line HUD: no route, so no \"Straight line\" label", "", text(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("Stop navigating", controlLabel())
    }

    private fun assertNotNavigating() {
        assertNull(navigatingTo())
        assertFalse("no HUD", shown(NAVIGATION_HUD_TAG))
        assertNull("no line on the map", map.content!!.route)
    }

    // ── Navigate from each of the three places ──

    @Test
    fun `from a waypoint's bubble on the Maps tab, Navigate targets that waypoint, with no recording`() {
        setScreen()
        openBubble()

        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        assertEquals(WaypointNavigation("wp-1", resumesReturn = false), navigatingTo())
        assertFalse("the bubble closes", shown(MAP_BUBBLE_TAG))
        assertHudAimsAtCreek()
        composeRule.onNodeWithTag(RETURN_CONTROL_TAG).assertIsEnabled()
        val route = map.content!!.route!!
        assertEquals("the dashed line, from the walker to the waypoint", listOf(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng)), route.straight)
        assertNull("no way back is drawn", route.line)
        assertTrue("the waypoint's pin is drawn", map.content!!.waypoints.any { it.id == "wp-1" })
    }

    @Test
    fun `from the waypoint's details sheet on the Maps tab, Navigate targets that waypoint`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(RECORD_DETAILS_NAVIGATE_TAG).performScrollTo()
        touchCentreOf(RECORD_DETAILS_NAVIGATE_TAG)

        assertEquals(WaypointNavigation("wp-1", resumesReturn = false), navigatingTo())
        assertFalse("the sheet closes", shown(RECORD_DETAILS_SHEET_TAG))
        assertHudAimsAtCreek()
    }

    @Test
    fun `from a Records row, Navigate targets that waypoint on the Maps tab`() {
        setScreen()
        openRecords()

        touchRowNavigate()

        assertEquals(WaypointNavigation("wp-1", resumesReturn = false), navigatingTo())
        assertHudAimsAtCreek()
        assertTrue("on the Maps tab", map.content != null && shown(NAVIGATION_HUD_TAG))
    }

    @Test
    fun `from the details sheet opened in Records, Navigate targets that waypoint on the Maps tab`() {
        setScreen()
        openRecords()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-1")).performScrollTo().performTouchInput { click(Offset(width * 0.2f, height * 0.5f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(RECORD_DETAILS_NAVIGATE_TAG).performScrollTo()
        touchCentreOf(RECORD_DETAILS_NAVIGATE_TAG)

        assertEquals(WaypointNavigation("wp-1", resumesReturn = false), navigatingTo())
        assertHudAimsAtCreek()
    }

    // ── Back goes back to where Navigate was tapped (Amendment 1) ──

    @Test
    fun `Back from a navigation started in the bubble ends it and reopens the bubble, and the next Back closes the bubble`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        back()

        assertNotNavigating()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNode(hasText("Creek pin") and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG)), useUnmergedTree = true).assertIsDisplayed()

        back()
        assertFalse("one step at a time: the bubble closes next", shown(MAP_BUBBLE_TAG))
    }

    @Test
    fun `Back from a navigation started in the sheet on the map ends it and reopens the sheet`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        composeRule.onNodeWithTag(RECORD_DETAILS_NAVIGATE_TAG).performScrollTo()
        touchCentreOf(RECORD_DETAILS_NAVIGATE_TAG)

        back()

        assertNotNavigating()
        assertTrue("the sheet is open again", shown(RECORD_DETAILS_SHEET_TAG))
        assertEquals("Creek pin", text(RECORD_DETAILS_TITLE_TAG))
    }

    @Test
    fun `Back from a navigation started in a Records row ends it and returns to Records`() {
        setScreen()
        openRecords()
        touchRowNavigate()

        back()

        assertNull(navigatingTo())
        assertFalse(shown(NAVIGATION_HUD_TAG))
        assertTrue("Records, on the Waypoints chip, is showing again", shown(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-1")))
        assertFalse("no sheet: it was not opened", shown(RECORD_DETAILS_SHEET_TAG))
    }

    @Test
    fun `Back from a navigation started in the sheet in Records ends it and returns to Records with the sheet open`() {
        setScreen()
        openRecords()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-1")).performScrollTo().performTouchInput { click(Offset(width * 0.2f, height * 0.5f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RECORD_DETAILS_NAVIGATE_TAG).performScrollTo()
        touchCentreOf(RECORD_DETAILS_NAVIGATE_TAG)

        back()

        assertNull(navigatingTo())
        assertTrue("the sheet is open again", shown(RECORD_DETAILS_SHEET_TAG))
        assertEquals("Creek pin", text(RECORD_DETAILS_TITLE_TAG))
    }

    // ── The X-circle and the HUD's ✕ end it where the walker is ──

    @Test
    fun `the X-circle ends it and stays on the map, with the line removed`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        touchCentreOf(RETURN_CONTROL_TAG)

        assertNotNavigating()
        assertFalse("the bubble is not reopened: only Back retraces", shown(MAP_BUBBLE_TAG))
        assertTrue("its own label again: ${controlLabel()}", controlLabel() != "Stop navigating")
    }

    @Test
    fun `the HUD's X ends it and stays on the map`() {
        setScreen()
        openRecords()
        touchRowNavigate()

        touchCentreOf(NAVIGATION_HUD_EXIT_TAG)

        assertNotNavigating()
        assertFalse("not sent back to Records", shown(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-1")))
    }

    // ── Arrival as at the start (T7, -498) ──

    @Test
    fun `within 15 m of the waypoint the map is handed its ring instead of its pin, the line ends, and the HUD reads Arrived`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        composeRule.runOnIdle { fix = southOfCreek(10.0) }
        composeRule.waitForIdle()

        val route = map.content!!.route!!
        assertEquals(LatLng(creek.lat, creek.lng), route.arrivedAt)
        assertNull("the dashed line ends", route.straight)
        assertTrue("the waypoint's pin is left out", map.content!!.waypoints.none { it.id == "wp-1" })
        assertEquals(ARRIVED_TEXT, text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("navigation stays on until ended", WaypointNavigation("wp-1", resumesReturn = false), navigatingTo())
    }

    // ── The owner's "Keep the last line, faded" (desk check, 2026-10-04) ──

    @Test
    fun `once the fix is lost the map keeps the last line, faded, and a fresh fix brings it back current`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)
        val drawn = listOf(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng))
        assertEquals(drawn, map.content!!.route!!.straight)
        assertTrue("current", map.content!!.route!!.straightIsCurrent)

        // The same place, last heard from ten minutes ago: lost.
        composeRule.runOnIdle { fix = fix.copy(timestampEpochMillis = t - 10 * 60_000L) }
        composeRule.waitForIdle()

        assertEquals("the last line is kept", drawn, map.content!!.route!!.straight)
        assertFalse("faded", map.content!!.route!!.straightIsCurrent)
        assertTrue("grey, offline (the owner: \"Grey once location is lost\")", map.content!!.route!!.straightOffline)

        // Held above the tab switch: a trip to the Journal and back keeps it.
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maps").performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("kept after leaving the Maps tab and coming back", drawn, map.content!!.route!!.straight)
        assertFalse(map.content!!.route!!.straightIsCurrent)

        composeRule.runOnIdle { fix = southOfCreek(300.0) }
        composeRule.waitForIdle()

        assertEquals(listOf(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng)), map.content!!.route!!.straight)
        assertTrue("current again", map.content!!.route!!.straightIsCurrent)
    }

    /** The owner's "Fade with the HUD" (relayed by the planner, RECORD -506): a fix 30 s to 5 min old draws the line from it, faded. */
    @Test
    fun `a stale fix draws the line from it, faded, while the HUD dims its distance, and a fresh fix brings both back`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        // A minute old: stale, not lost.
        composeRule.runOnIdle { fix = southOfCreek(400.0).copy(timestampEpochMillis = t - 60_000L) }
        composeRule.waitForIdle()

        assertEquals("from the stale fix", listOf(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng)), map.content!!.route!!.straight)
        assertFalse("faded with the HUD", map.content!!.route!!.straightIsCurrent)
        assertFalse("stale is not offline: still blue", map.content!!.route!!.straightOffline)

        composeRule.runOnIdle { fix = southOfCreek(300.0) }
        composeRule.waitForIdle()

        assertTrue("current again", map.content!!.route!!.straightIsCurrent)
    }

    @Test
    fun `picked back up with no fix yet, there is no line until the first fix`() {
        store.stored = WaypointNavigation("wp-1", resumesReturn = false)
        fixPresent = false

        setScreen()

        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertNull("no last line to keep: the app has just opened", map.content!!.route!!.straight)

        composeRule.runOnIdle { fixPresent = true }
        composeRule.waitForIdle()

        assertEquals(listOf(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng)), map.content!!.route!!.straight)
        assertTrue(map.content!!.route!!.straightIsCurrent)
    }

    // ── A return under way (steps 6 and 7) ──

    @Test
    fun `a return under way is overruled by Navigate, Back picks it back up, and the next Backs close the bubble and ask before exiting`() {
        recording = true
        returning = true
        setScreen()
        assertTrue("precondition: the return's line is drawn", map.content!!.route?.line != null)
        openBubble()

        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        assertEquals(1, pauses)
        assertEquals(WaypointNavigation("wp-1", resumesReturn = true), navigatingTo())
        assertHudAimsAtCreek()
        assertNull("the return's line is not drawn meanwhile", map.content!!.route!!.line)

        back()

        assertEquals("the return picks up again", 1, resumes)
        assertNull(navigatingTo())
        // RECORD -942: on the route the return's HUD has no straight-line note; its large figure is the route's, by trail. Was:
        // the status line ends in " straight".
        assertEquals("the return's HUD: no straight-line note on the route", "", text(NAVIGATION_HUD_STATUS_TAG))
        assertEquals(returnLine, map.content!!.route!!.line)
        assertTrue("the bubble is back", shown(MAP_BUBBLE_TAG))

        back()
        assertFalse(shown(MAP_BUBBLE_TAG))
        assertFalse("not yet asked", shown(EXIT_NAVIGATION_PROMPT_TAG))
        back()
        assertTrue("on the bare map, Back asks before exiting the return, as today", shown(EXIT_NAVIGATION_PROMPT_TAG))
    }

    // ── Deleted meanwhile, or while the app was closed (Amendment 1) ──

    @Test
    fun `a waypoint deleted while navigating to it ends the navigation and says so`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        composeRule.runOnIdle { waypoints = listOf(oak) }
        composeRule.waitForIdle()

        assertNotNavigating()
        composeRule.onNodeWithText(WAYPOINT_NAVIGATION_GONE_TEXT).assertIsDisplayed()
        assertNull("nothing is left to pick back up", store.stored)
    }

    @Test
    fun `the app opened again picks the navigation back up`() {
        store.stored = WaypointNavigation("wp-1", resumesReturn = false)

        setScreen()

        assertHudAimsAtCreek()
        assertEquals(listOf(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng)), map.content!!.route!!.straight)
    }

    @Test
    fun `picked back up for a waypoint deleted while the app was closed, it ends with the same message`() {
        store.stored = WaypointNavigation("wp-gone", resumesReturn = false)

        setScreen()

        assertNotNavigating()
        composeRule.onNodeWithText(WAYPOINT_NAVIGATION_GONE_TEXT).assertIsDisplayed()
        assertNull(store.stored)
    }

    @Test
    fun `before the waypoints have loaded, a navigation picked back up waits rather than ending`() {
        store.stored = WaypointNavigation("wp-1", resumesReturn = false)
        waypoints = emptyList()
        waypointsLoaded = false

        setScreen()

        assertEquals("still kept", WaypointNavigation("wp-1", resumesReturn = false), navigatingTo())
        assertFalse(shown(NAVIGATION_HUD_TAG))
        assertFalse("no message", composeRule.onAllNodes(hasText(WAYPOINT_NAVIGATION_GONE_TEXT)).fetchSemanticsNodes().isNotEmpty())

        composeRule.runOnIdle {
            waypoints = listOf(creek, oak)
            waypointsLoaded = true
        }
        composeRule.waitForIdle()

        assertHudAimsAtCreek()
    }

    // ── Entry maps offer Navigate too (RECORD -890; until then Directions only, Amendment 1) ──

    /** Sets the screen with a saved entry holding the creek pin, open on the Journal with its map drawn, and opens the pin's bubble by a real touch. */
    private fun openEntryBubble() {
        val entry = CartographyEntry.draft(id = "entry-1", date = java.time.LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 1_000L).copy(
            isDraft = false,
            text = "Along the creek.",
            waypointDecisions = listOf(WaypointDecision("wp-1", "Creek pin", creek.lat, creek.lng, kept = true)),
        )
        cartography = CartographyUiState(entries = listOf(entry), editingEntry = entry)
        // The entry's map is shorter than the Maps tab's: its glyph sits near its top, as CartographyEntryMapBubblesTest's do.
        map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, "wp-1", 60.dp, 120.dp, LatLng(creek.lat, creek.lng))))
        setScreen(CartographyEntryMapData(emptyList(), emptyList(), listOf(RecordPoint("wp-1", LatLng(creek.lat, creek.lng))), emptyList(), emptyList()))
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Along the creek.").assertExists()
        touchCentreOf(glyphTag("wp-1"))
    }

    /**
     * RECORD -890 (the owner: "Waypoints only, as described (Recommended)"): a waypoint's bubble on a journal entry's map
     * offers Navigate beside Directions and Details, and a real touch on it opens the Maps tab navigating to that
     * waypoint, as from Records. Until 2026-10-11 this test was `a waypoint's bubble on a journal entry's map offers
     * Directions, not Navigate` (dispatch -502, Amendment 1); its "no Navigate" assertion is inverted here, as -890 rules.
     */
    @Test
    fun `from a waypoint's bubble on a journal entry's map, Navigate targets that waypoint on the Maps tab`() {
        openEntryBubble()
        composeRule.onNodeWithTag(MAP_BUBBLE_DIRECTIONS_TAG).assertExists()
        composeRule.onNodeWithTag(MAP_BUBBLE_DETAILS_TAG).assertExists()

        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        assertEquals(WaypointNavigation("wp-1", resumesReturn = false), navigatingTo())
        assertHudAimsAtCreek()
        assertTrue("on the Maps tab", map.content != null && shown(NAVIGATION_HUD_TAG))
        composeRule.onNodeWithText("Along the creek.").assertDoesNotExist()
    }

    /**
     * RECORD -890: "Back behaves as it does after starting navigation from Records": one Back ends the navigation and goes
     * back to where Navigate was tapped, here the journal entry, not Records and not the Maps tab.
     */
    @Test
    fun `Back from a navigation started on a journal entry's map ends it and returns to the entry`() {
        openEntryBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        back()

        assertNull(navigatingTo())
        assertFalse(shown(NAVIGATION_HUD_TAG))
        composeRule.onNodeWithText("Along the creek.").assertExists()
        assertFalse("not Records", shown(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-1")))
    }

    // ── CLAUDE.md, the Surface pitfall ──

    /** With the waypoint's HUD up and the dashed line handed to the map, real taps just below the HUD reach the map. */
    @Test
    fun `navigating to a waypoint, real taps on the map below the HUD reach the map`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)
        val hud = composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).getUnclippedBoundsInRoot()
        // Every control drawn over the map, the map's own tap target left out: it is what the taps are meant to reach.
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .filterNot { it.config.getOrNull(SemanticsProperties.TestTag) == "map-slot" }
            .map { with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) } }
        var sampled = 0
        for (row in listOf(6.dp, 20.dp)) {
            val y = hud.bottom + row
            for (i in 0 until SAMPLES) {
                val x = hud.left + 8.dp + (hud.right - hud.left - 16.dp) * (i.toFloat() / (SAMPLES - 1))
                if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
                val before = map.taps
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onRoot().performTouchInput { click(p) }
                composeRule.waitForIdle()
                assertEquals("a tap at ($x, $y) must reach the map", before + 1, map.taps)
                sampled++
            }
        }
        assertTrue("at least $MIN_SAMPLED points sampled, not $sampled", sampled >= MIN_SAMPLED)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val RETURN_CONTROL_TAG = "control-pill-return-to-vehicle"
        const val SAMPLES = 8
        const val MIN_SAMPLED = 8
    }
}
