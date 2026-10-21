package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/**
 * Phase 2.6 E1 (dispatch 2026-10-19-40, RECORD -1484): pins which of the main screen's Back handlers wins in the four places
 * where two of them can be enabled at once. It pins today's behaviour; it does not choose a new one (whether today's winner is
 * what Back should do is the owner's question, RECORD -1479). It exists so the move of the seven handlers (E2) can be shown to
 * keep their order.
 *
 * The four overlaps, all with the Tools drawer's "Offline maps" return still pending (`toolsReturn`, the handler that reopens
 * Tools over the page the row was tapped from), and the winner today, the handler registered later:
 * - (a) on the plain Maps tab, not navigating: the home handler's exit warning wins;
 * - (b) on a fullscreen Maps tab: the tools return wins, over the fullscreen handler;
 * - (c) navigating home: the exit-navigation prompt wins;
 * - (d) navigating to a waypoint: the waypoint handler wins, ending the navigation.
 *
 * **How the state is reached, and why a semantic click.** The return stays pending only if the user reaches the Maps tab
 * before Records has arrived at the Offline maps chip. The test taps Tools and the row with real touches, stops the clock, and
 * selects Maps through the nav item's own click action before any frame runs, so the Journal never composes and the request
 * is never taken up. That last step is a semantic action, not a touch: at that moment the drawer is still drawn and its scrim
 * covers the nav. This test's claim is about the order of the Back handlers, not about where a touch lands (CLAUDE.md, "A
 * semantic performClick asserts wiring, not routing"). **Inferred, not checked:** a finger probably cannot reach this state on
 * a phone, because the same scrim covers the nav throughout the drawer's close animation, which outlasts the frame or two the
 * request takes to land.
 *
 * In (a), (c) and (d) today's winner is also the handler that wins with no return pending, so those cases cannot tell on
 * their own that the return was pending. Two things show it was: (a) and (d) end by tapping Journal, which opens on the
 * Offline maps chip only if the row's request was still waiting; and the reverted builds recorded in E1's PR, which move the
 * tools-return handler and make these cases fail.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AvailabilityScreenBackOverlapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.background(Color(0xFF2E7D32))) }
    private val creek = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)

    private val uiState = mutableStateOf(AvailabilityUiState())
    private var waypointNavigationStops = 0

    private fun setScreen(isReturning: Boolean = false, waypointNavigation: WaypointNavigation? = null) {
        uiState.value = AvailabilityUiState(liveFix = fix, waypointNavigation = waypointNavigation)
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState.value,
                recording = AvailabilityScreenRecording(isRecording = isReturning),
                records = AvailabilityScreenRecords(waypoints = listOf(creek), waypointsLoaded = true),
                navigation = AvailabilityScreenNavigation(
                    isReturning = isReturning,
                    onStopWaypointNavigation = {
                        waypointNavigationStops++
                        uiState.value = uiState.value.copy(waypointNavigation = null)
                    },
                ),
                compassProvider = FixedCompass,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { t + 1_000L },
                mapSlot = map,
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
            )
        }
        settle()
        assertTrue("precondition: on the Maps tab", navSelected("Maps"))
        assertFalse("precondition: the drawer is closed", drawerIsOpen())
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun navItem(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG)))

    private fun navSelected(label: String): Boolean = runCatching { navItem(label).assertIsSelected() }.isSuccess

    // The sheet on screen, not the Tools nav item selected: in fullscreen the bottom nav is off the screen and out of the tree,
    // so a check on the nav item reads "closed" there whatever the drawer does (the first run of case (b) read it that way).
    private fun drawerIsOpen(): Boolean = runCatching { composeRule.onNode(hasTestTag(TOOLS_DRAWER_SHEET_TAG)).assertIsDisplayed() }.isSuccess

    private fun isFullscreen(): Boolean =
        composeRule.onAllNodes(hasContentDescription("Exit fullscreen")).fetchSemanticsNodes().isNotEmpty()

    private fun promptShown(): Boolean =
        composeRule.onAllNodes(hasTestTag(EXIT_NAVIGATION_PROMPT_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** A real touch at the centre of [node], with no settling after it. */
    private fun touchCentre(node: SemanticsNodeInteraction) {
        val b = node.getUnclippedBoundsInRoot()
        val p = with(composeRule.density) { Offset(((b.left + b.right) / 2).toPx(), ((b.top + b.bottom) / 2).toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /**
     * Tools, then its "Offline maps" row, with real touches; then the Maps tab through its own click action before any frame
     * has run (see the class comment for why that one is not a touch). Leaves the return pending on the Maps tab.
     */
    private fun leaveForMapsBeforeRecordsArrives() {
        touchCentre(navItem("Tools"))
        settle()
        assertTrue("precondition: Tools opened", drawerIsOpen())
        composeRule.mainClock.autoAdvance = false
        touchCentre(composeRule.onNode(hasTestTag(TOOLS_OFFLINE_MAPS_ROW_TAG)))
        navItem("Maps").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.autoAdvance = true
        settle()
        assertTrue("precondition: on the Maps tab with the return pending", navSelected("Maps"))
        assertFalse("precondition: the drawer closed by the row", drawerIsOpen())
    }

    /** The control (see the class comment): Journal opens on Records' Offline maps chip only if the row's request still waits. */
    private fun assertTheRowsRequestWasStillWaiting() {
        touchCentre(navItem("Journal"))
        settle()
        assertTrue(
            "control: Journal opened on Records' Offline maps chip, so the Offline maps row's request was still pending (and with it the return, which clears only after the Journal arrives)",
            runCatching { composeRule.onNode(hasTestTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS))).assertIsSelected() }.isSuccess,
        )
    }

    @Test
    fun `a - on the plain Maps tab, Back with the tools return pending warns before exiting, and does not reopen Tools`() {
        setScreen()
        leaveForMapsBeforeRecordsArrives()

        pressBack()

        assertFalse("the tools return's handler won (it reopened Tools); today the home handler, registered after it, wins", drawerIsOpen())
        assertEquals("the home handler's exit warning", "Tap Back Button Again to Exit", ShadowToast.getTextOfLatestToast())
        assertTrue("still on the Maps tab", navSelected("Maps"))
        assertFalse("the app is not closing", composeRule.activity.isFinishing)
        assertTheRowsRequestWasStillWaiting()
    }

    @Test
    fun `b - on a fullscreen Maps tab, Back with the tools return pending reopens Tools and leaves fullscreen on`() {
        setScreen()
        leaveForMapsBeforeRecordsArrives()
        touchCentre(composeRule.onNode(hasContentDescription("Fullscreen")))
        settle()
        assertTrue("precondition: fullscreen", isFullscreen())

        pressBack()

        assertTrue(
            "the fullscreen handler won (fullscreen ended, drawer closed); today the tools return, registered after it, wins: " +
                "drawer open = ${drawerIsOpen()}, fullscreen = ${isFullscreen()}",
            drawerIsOpen() && isFullscreen(),
        )
        assertEquals("no exit warning", null, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `c - navigating home, Back with the tools return pending asks about navigation, and does not reopen Tools`() {
        setScreen(isReturning = true)
        leaveForMapsBeforeRecordsArrives()

        pressBack()

        assertFalse("the tools return's handler won (it reopened Tools); today the navigation handler, registered after it, wins", drawerIsOpen())
        assertTrue("the exit-navigation prompt is up", promptShown())
        assertEquals("no exit warning", null, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `d - navigating to a waypoint, Back with the tools return pending ends the navigation, and does not reopen Tools`() {
        setScreen(waypointNavigation = WaypointNavigation(waypointId = creek.id, resumesReturn = false))
        leaveForMapsBeforeRecordsArrives()

        pressBack()

        assertFalse("the tools return's handler won (it reopened Tools); today the waypoint handler, registered after it, wins", drawerIsOpen())
        assertEquals("the waypoint navigation was ended once", 1, waypointNavigationStops)
        assertTrue("still on the Maps tab", navSelected("Maps"))
        assertTheRowsRequestWasStillWaiting()
    }

    private object FixedCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(134f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}
