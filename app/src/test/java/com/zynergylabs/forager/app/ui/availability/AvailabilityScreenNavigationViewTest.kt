package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.track.RecordingNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Dispatch 2026-09-28-430 (plan task T22; rulings in continuation 2026-09-28-432): the map's
 * navigation view, as the screen asks for it. [AvailabilityScreen] is composed with a stand-in map
 * that records the [MapRenderMode] it is handed and counts real long-presses on itself, and that
 * reports a user's move away from the view as the real map does (the request's `onLeftView`).
 *
 * What this cannot show, said plainly: the map's real tilt and rotation, MapLibre's camera modes,
 * and the compass on a phone. A real MapView cannot run under Robolectric; those are the S22 step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenNavigationViewTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private var now = t + 1_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private var returning by mutableStateOf(false)
    private var tripStartWarning by mutableStateOf<RecordingNotice?>(null)
    private val compass = MutableStateFlow<CompassReading?>(CompassReading(80f, HeadingUncertainty.Estimated(2f), 0L))

    /** Every MapRenderMode the map was handed, the last one current. */
    private var lastRenderMode: MapRenderMode? = null
    private var longPresses = 0

    private val map: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
        lastRenderMode = renderMode
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    private fun setScreen(restoration: StateRestorationTester? = null) {
        val set: (@Composable () -> Unit) -> Unit = restoration?.let { r -> { content: @Composable () -> Unit -> r.setContent(content) } } ?: { content -> composeRule.setContent(content) }
        set {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                    tripStartWarning = tripStartWarning,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning,
                    onToggleReturning = { returning = !returning },
                    navigationTarget = start,
                ),
                compassProvider = object : CompassProvider { override val heading: Flow<CompassReading?> = compass },
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { now },
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
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun switchReturning(value: Boolean) {
        composeRule.runOnIdle { returning = value }
        settle()
    }

    private fun view() = lastRenderMode?.navigationView

    /** The real map reports a drag (or "Reset orientation") away from the view through this. */
    private fun userMovesTheMapAway() {
        val request = view() ?: throw AssertionError("a move away needs a navigation view")
        composeRule.runOnIdle { request.onLeftView() }
        settle()
    }

    private fun boundsOf(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun touch(x: Dp, y: Dp) {
        val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onRoot().performTouchInput { click(p) }
        settle()
    }

    private fun touchCentreOf(bounds: DpRect) = touch((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2)

    private fun pillShown() = composeRule.onAllNodesWithTag(RETURN_TO_ROUTE_TAG).fetchSemanticsNodes().isNotEmpty()

    private fun headingLabel(): String =
        composeRule.onNodeWithTag(NAVIGATION_HUD_HEADING_TAG).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun `the navigation view is asked for with Return, following and facing up, and not before or after`() {
        setScreen()
        assertNull("not navigating: the map is north-up and flat as before", view())

        switchReturning(true)
        val request = view() ?: throw AssertionError("Return asks for the navigation view")
        assertTrue(request.following)
        assertEquals(NavigationFacing.FACING_UP, request.facing)
        assertFalse(pillShown())

        switchReturning(false)
        assertNull("Stop: flat and north-up again", view())
    }

    @Test
    fun `every map on the screen is handed the true heading, navigating or not`() {
        setScreen()
        assertNotNull("not navigating", lastRenderMode?.trueHeading)
        switchReturning(true)
        assertNotNull("navigating", lastRenderMode?.trueHeading)
    }

    /**
     * A move away shows "Return to Route"; a real touch on it brings the view back and it follows
     * again. Five touches across the pill's own bounds, each after its own move away (CLAUDE.md: a
     * finger is not a point; a semantic click asserts wiring, not routing).
     */
    @Test
    fun `a move away shows Return to Route, and a real touch anywhere on it brings the view back`() {
        setScreen()
        switchReturning(true)
        val firstId = view()!!.restoreRequestId
        repeat(5) { index ->
            userMovesTheMapAway()
            assertTrue("move $index shows the pill", pillShown())
            assertFalse(view()!!.following)
            composeRule.onNodeWithText("Return to Route").assertExists()
            val b = boundsOf(RETURN_TO_ROUTE_TAG)
            val inset = 4.dp
            val samples = listOf(
                DpOffset((b.left + b.right) / 2, (b.top + b.bottom) / 2),
                DpOffset(b.left + inset * 3, b.top + inset),
                DpOffset(b.right - inset * 3, b.top + inset),
                DpOffset(b.left + inset * 3, b.bottom - inset),
                DpOffset(b.right - inset * 3, b.bottom - inset),
            )
            touch(samples[index].x, samples[index].y)
            assertFalse("touch $index at ${samples[index]} hid the pill", pillShown())
            assertTrue("touch $index: following again", view()!!.following)
            assertEquals("touch $index asked for the view again", firstId + index + 1, view()!!.restoreRequestId)
        }
    }

    /** CLAUDE.md, the `Surface` pitfall: real long-presses beside the pill, either side of it along its height, reach the map. */
    @Test
    fun `long-presses beside Return to Route reach the map`() {
        setScreen()
        switchReturning(true)
        userMovesTheMapAway()
        val b = boundsOf(RETURN_TO_ROUTE_TAG)
        var sampled = 0
        for (x in listOf(b.left - 16.dp, b.right + 16.dp)) {
            for (y in listOf(b.top + 6.dp, (b.top + b.bottom) / 2, b.bottom - 6.dp)) {
                val before = longPresses
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onRoot().performTouchInput { longClick(p) }
                composeRule.waitForIdle()
                assertEquals("a long-press at ($x, $y) beside the pill must reach the map", before + 1, longPresses)
                sampled++
            }
        }
        assertEquals(6, sampled)
        assertTrue("the pill is still there", pillShown())
    }

    /** Ruling D: locate while navigating brings the navigation view back, as "Return to Route" does. A real touch on it. */
    @Test
    fun `locate while navigating asks for the navigation view again`() {
        setScreen()
        switchReturning(true)
        userMovesTheMapAway()
        val before = view()!!.restoreRequestId
        touchCentreOf(composeRule.onNodeWithContentDescription("Center on my location").getUnclippedBoundsInRoot())
        assertTrue(view()!!.following)
        assertEquals(before + 1, view()!!.restoreRequestId)
        assertFalse(pillShown())
    }

    /** CLAUDE.md, UX defaults: a move away survives leaving the Maps tab and coming back; the pill is still there. */
    @Test
    fun `a move away survives a tab round trip`() {
        setScreen()
        switchReturning(true)
        userMovesTheMapAway()
        touchCentreOf(composeRule.onNodeWithText("List").getUnclippedBoundsInRoot())
        assertFalse("the map left with its tab", composeRule.onAllNodesWithTag(MAP_TAG).fetchSemanticsNodes().isNotEmpty())
        touchCentreOf(composeRule.onNodeWithText("Maps").getUnclippedBoundsInRoot())
        assertTrue(pillShown())
        assertFalse(view()!!.following)
    }

    /** Stop clears a move away, so the next Return starts in the view, following. */
    @Test
    fun `Stop clears a move away, and the next Return follows`() {
        setScreen()
        switchReturning(true)
        userMovesTheMapAway()
        switchReturning(false)
        assertFalse(pillShown())
        switchReturning(true)
        assertTrue(view()!!.following)
        assertFalse(pillShown())
    }

    /**
     * The owner's step path for a stuck compass, through the screen: "Compass calibrating…" in the
     * HUD's heading label (ruling E) while it is retried, the map still facing as it was; after 15 s
     * north-up, and the label says so; good readings held for the trust judge's 2 s bring facing-up
     * back by themselves, and the label's heading.
     */
    @Test
    fun `a stuck compass reads Compass calibrating, then north-up after 15 s, then facing-up again on recovery`() {
        setScreen()
        switchReturning(true)
        assertEquals("80° E", headingLabel())

        compass.value = CompassReading(80f, HeadingUncertainty.Estimated(20f), 1_000L)
        settle()
        assertEquals("Compass calibrating…", headingLabel())
        assertEquals(NavigationFacing.CALIBRATING, view()!!.facing)

        now += 14_000L
        settle()
        assertEquals("still retried at 14 s", NavigationFacing.CALIBRATING, view()!!.facing)

        now += 2_000L
        settle()
        assertEquals("Compass unavailable · north up", headingLabel())
        assertEquals(NavigationFacing.NORTH_UP, view()!!.facing)
        assertTrue("north-up still follows", view()!!.following)

        compass.value = CompassReading(90f, HeadingUncertainty.Estimated(2f), 2_000L)
        settle()
        compass.value = CompassReading(90f, HeadingUncertainty.Estimated(2f), 4_000L)
        settle()
        assertEquals(NavigationFacing.FACING_UP, view()!!.facing)
        assertEquals("90° E", headingLabel())
    }

    @Test
    fun `no compass at all is north-up from the start, and the label says so`() {
        compass.value = null
        setScreen()
        switchReturning(true)
        assertEquals(NavigationFacing.NORTH_UP, view()!!.facing)
        assertEquals("Compass unavailable · north up", headingLabel())
    }

    /** Continuation -432: a notice shown while "Return to Route" is up sits above it, not over it. */
    @Test
    fun `a snackbar shown while Return to Route is up sits above it`() {
        setScreen()
        switchReturning(true)
        userMovesTheMapAway()
        composeRule.runOnIdle { tripStartWarning = RecordingNotice(id = 1, message = "Do Not Disturb is on. If you go off track, the alert may not be felt.") }
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithTag(COMPACT_SNACKBAR_TAG).fetchSemanticsNodes().isNotEmpty() }
        settle()
        val snackbar = boundsOf(COMPACT_SNACKBAR_TAG)
        val pill = boundsOf(RETURN_TO_ROUTE_TAG)
        assertTrue("the snackbar's bottom ${snackbar.bottom} is at or above the pill's top ${pill.top}", snackbar.bottom <= pill.top + 0.5.dp)
    }

    // ── Dispatch 2026-09-28-440: the zoom when navigation starts, once ──────────────────────

    private fun theMapAppliesTheStartZoom() {
        val request = view() ?: throw AssertionError("navigating")
        composeRule.runOnIdle { request.onStartZoomApplied() }
        settle()
    }

    @Test
    fun `starting navigation asks once for the set zoom, and not again once the map has applied it`() {
        setScreen()
        assertNull(view())
        switchReturning(true)
        assertTrue("Return asks for the start zoom", view()!!.zoomOnStart)
        theMapAppliesTheStartZoom()
        assertFalse("applied once, not asked again", view()!!.zoomOnStart)
    }

    @Test
    fun `a facing change does not ask for the zoom again`() {
        setScreen()
        switchReturning(true)
        theMapAppliesTheStartZoom()
        compass.value = CompassReading(80f, HeadingUncertainty.Estimated(20f), 1_000L)
        settle()
        assertEquals(NavigationFacing.CALIBRATING, view()!!.facing)
        assertFalse(view()!!.zoomOnStart)
    }

    @Test
    fun `Return to Route brings the view back at the walker's zoom, without asking for the set one`() {
        setScreen()
        switchReturning(true)
        theMapAppliesTheStartZoom()
        userMovesTheMapAway()
        touchCentreOf(boundsOf(RETURN_TO_ROUTE_TAG))
        assertTrue(view()!!.following)
        assertFalse(view()!!.zoomOnStart)
    }

    @Test
    fun `a new navigation after Stop asks for the set zoom again`() {
        setScreen()
        switchReturning(true)
        theMapAppliesTheStartZoom()
        switchReturning(false)
        switchReturning(true)
        assertTrue(view()!!.zoomOnStart)
    }

    /** A configuration change (a rotation) mid-navigation recreates the screen's state from saved state: not a new start. */
    @Test
    fun `a configuration change mid-navigation does not ask for the zoom again`() {
        val restoration = StateRestorationTester(composeRule)
        setScreen(restoration)
        switchReturning(true)
        theMapAppliesTheStartZoom()
        restoration.emulateSavedInstanceStateRestore()
        settle()
        assertTrue("still navigating", view() != null)
        assertFalse(view()!!.zoomOnStart)
    }

    @Test
    fun `leaving the Maps tab and coming back does not ask for the zoom again`() {
        setScreen()
        switchReturning(true)
        theMapAppliesTheStartZoom()
        touchCentreOf(composeRule.onNodeWithText("List").getUnclippedBoundsInRoot())
        touchCentreOf(composeRule.onNodeWithText("Maps").getUnclippedBoundsInRoot())
        assertFalse(view()!!.zoomOnStart)
    }

    /** "Nothing fully obstructs the map view": the pill's fill is the map chrome's 80%. */
    @Test
    fun `Return to Route has the map chrome's 80 percent fill`() {
        setScreen()
        switchReturning(true)
        userMovesTheMapAway()
        val fill: Color = composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val MAP_TAG = "navigation-view-map"
    }
}
