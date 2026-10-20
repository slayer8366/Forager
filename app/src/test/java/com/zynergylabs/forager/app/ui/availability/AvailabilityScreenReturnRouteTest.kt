package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
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
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-09-28-423 (plan task T6): the HUD follows the route, read on the real screen.
 * [AvailabilityScreen] is composed while returning with each [ReturnRoute] state `MainActivity`
 * can pass it (`returnRouteOf(trackUiState.routeHome)`), and the HUD's own text is read back.
 * That the ViewModel produces those states, through Return, fixes and the polled track, is
 * `TrackRecordingViewModelTest`'s; `MainActivity`'s one line joining the two is not exercised by a
 * test, as the path-home line it replaces was not.
 *
 * The fix sits at 45.52 N, 122.68 W; the start is 0.01° of latitude due north of it (1112 m,
 * "0.7 mi"); the device faces 45° true (a trusted 45° and no declination). The route's lookahead
 * is due east, a 45° turn, so a needle aimed at the start (a 315° turn) cannot pass for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
// Native graphics, as AvailabilityScreenLandscapeB2Test: without it Robolectric measures text at a
// few dp wide (a heading label measured 3.5 dp), and the width question this class answers for
// "Try again", and the bounds its real touches sample, would be read off fake text.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvailabilityScreenReturnRouteTest {

    private val composeRule = createComposeRule()

    // As AvailabilityScreenLandscapeB2Test: the rule's host activity must be declared to the
    // package manager before the rule launches it.
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
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val east = LatLng(45.52, -122.679)

    private var route by mutableStateOf<ReturnRoute>(ReturnRoute.Pending)
    private var retries = 0
    private var longPresses = 0

    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    private fun setScreen(initial: ReturnRoute) {
        route = initial
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = true,
                    navigationTarget = start,
                    returnRoute = route,
                    onRetryRoute = { retries++ },
                ),
                compassProvider = FixedCompass(45f),
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
        composeRule.waitForIdle()
    }

    private fun textOfTag(tag: String, unmerged: Boolean = false): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun boundsOf(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `on the route the large figure is the route distance, the status line the straight line, and the needle turns to the lookahead`() {
        setScreen(ReturnRoute.Ahead(east, 1_500.0))

        assertEquals("0.9 mi", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        // RECORD -942: on the route there is no straight-line note. Was "0.7 mi straight".
        assertEquals("", textOfTag(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("right 45°", textOfTag(NAVIGATION_HUD_TARGET_TAG))
    }

    @Test
    fun `before the first route the large slot is a dash and the status line the straight line`() {
        setScreen(ReturnRoute.Pending)

        assertEquals("—", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("0.7 mi straight", textOfTag(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("", textOfTag(NAVIGATION_HUD_TARGET_TAG))
    }

    @Test
    fun `a withheld route reads Unable to calculate route in the large slot, with the straight line kept`() {
        setScreen(ReturnRoute.Unavailable(canRetry = false))

        assertEquals("Unable to calculate route", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("0.7 mi straight", textOfTag(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("", textOfTag(NAVIGATION_HUD_TARGET_TAG))
    }

    /**
     * The HUD is the same height on the route and pending. Dispatch 2026-10-12-06 (RECORD -932, step 3; RECORD -935): a withheld
     * route's message has moved from the large slot to its own line under the columns, so withheld with no usable points the HUD
     * is taller by that one line, and with "Try again" offered by "Try again"'s row, laid out at its 48 dp touch height, beside
     * the message. Was: "the HUD is one row taller only while Try again is offered", withheld with no usable points the same
     * height as on the route. No new surface either way: the map-chrome fill at 80% is the HUD's existing one.
     */
    @Test
    fun `the HUD grows by the route message's line when a route is withheld, and by Try again's row when it is offered`() {
        setScreen(ReturnRoute.Ahead(east, 1_500.0))
        val onRoute = boundsOf(NAVIGATION_HUD_TAG).let { it.bottom - it.top }
        composeRule.runOnIdle { route = ReturnRoute.Pending }
        composeRule.waitForIdle()
        assertEquals("pending, the HUD's height is the route's", onRoute.value, boundsOf(NAVIGATION_HUD_TAG).let { it.bottom - it.top }.value, 0.5f)
        composeRule.runOnIdle { route = ReturnRoute.Unavailable(canRetry = false) }
        composeRule.waitForIdle()
        val withheld = boundsOf(NAVIGATION_HUD_TAG).let { it.bottom - it.top }
        val message = boundsOf(NAVIGATION_HUD_DISTANCE_TAG)
        val line = message.bottom - message.top
        assertTrue("taller by the message's line, no more: $withheld against $onRoute, line $line", withheld > onRoute + 0.5.dp && withheld <= onRoute + line + 0.5.dp)
        composeRule.runOnIdle { route = ReturnRoute.Unavailable(canRetry = true) }
        composeRule.waitForIdle()
        val taller = boundsOf(NAVIGATION_HUD_TAG).let { it.bottom - it.top }
        val row = boundsOf(NAVIGATION_HUD_RETRY_TAG).let { it.bottom - it.top }
        assertTrue("taller with Try again: $taller against $withheld", taller > withheld + 0.5.dp)
        assertTrue("taller by no more than the row ($row) over the route's height: $taller against $onRoute", taller <= onRoute + row + 0.5.dp)
    }

    /**
     * CLAUDE.md, the `Surface` pitfall: real long-presses on the map just below the HUD, across its
     * width, reach the map, in the taller state with "Try again" offered and in the usual one. A
     * point inside any clickable control (the icon cluster) is skipped, and the number sampled is
     * asserted, so an empty sample cannot pass.
     */
    @Test
    fun `real long-presses on the map below the HUD reach the map, taller with Try again or not`() {
        setScreen(ReturnRoute.Unavailable(canRetry = true))
        longPressesBelowTheHudReachTheMap()
        composeRule.runOnIdle { route = ReturnRoute.Ahead(east, 1_500.0) }
        composeRule.waitForIdle()
        longPressesBelowTheHudReachTheMap()
        assertEquals("no long-press on the map asked for a new route", 0, retries)
    }

    private fun longPressesBelowTheHudReachTheMap() {
        val hud = boundsOf(NAVIGATION_HUD_TAG)
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().map {
            with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) }
        }
        var sampled = 0
        for (row in listOf(6.dp, 20.dp)) {
            val y = hud.bottom + row
            for (i in 0 until SAMPLES) {
                val x = hud.left + 8.dp + (hud.right - hud.left - 16.dp) * (i.toFloat() / (SAMPLES - 1))
                if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
                val before = longPresses
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onRoot().performTouchInput { longClick(p) }
                composeRule.waitForIdle()
                assertEquals("a long-press at ($x, $y), HUD bottom ${hud.bottom}, must reach the map", before + 1, longPresses)
                sampled++
            }
        }
        assertTrue("at least $MIN_SAMPLED points were sampled, not $sampled", sampled >= MIN_SAMPLED)
    }

    /**
     * "Try again" by real touches at five points across the row's own bounds (CLAUDE.md: a finger
     * is not a point, and a semantic click asserts wiring, not routing). Each must reach the retry,
     * and a touch on the message above it must not.
     */
    @Test
    fun `Try again is reached by real touches across its row, and not by a touch on the message`() {
        setScreen(ReturnRoute.Unavailable(canRetry = true))
        assertEquals("Try again", textOfTag(NAVIGATION_HUD_RETRY_TAG))

        val b = boundsOf(NAVIGATION_HUD_RETRY_TAG)
        val inset = 3.dp
        val samples = listOf(
            DpOffset((b.left + b.right) / 2, (b.top + b.bottom) / 2),
            DpOffset(b.left + inset, b.top + inset),
            DpOffset(b.right - inset, b.top + inset),
            DpOffset(b.left + inset, b.bottom - inset),
            DpOffset(b.right - inset, b.bottom - inset),
        )
        samples.forEachIndexed { index, sample ->
            val p = with(composeRule.density) { Offset(sample.x.toPx(), sample.y.toPx()) }
            composeRule.onRoot().performTouchInput { click(p) }
            composeRule.waitForIdle()
            assertEquals("touch $index at $sample must reach Try again", index + 1, retries)
        }
        val m = boundsOf(NAVIGATION_HUD_DISTANCE_TAG)
        val p = with(composeRule.density) { Offset(((m.left + m.right) / 2).toPx(), (m.top + 2.dp).toPx()) }
        composeRule.onRoot().performTouchInput { click(p) }
        composeRule.waitForIdle()
        assertEquals("the message is not the control", samples.size, retries)
    }

    /** One control: announced as a button whose action is "Try again", laid out at least 48 dp tall. */
    @Test
    fun `the Try again row is one button, labelled Try again, at least 48 dp tall`() {
        setScreen(ReturnRoute.Unavailable(canRetry = true))
        val config = composeRule.onNodeWithTag(NAVIGATION_HUD_RETRY_TAG).fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.Role.Button, config[SemanticsProperties.Role])
        assertEquals("Try again", config[SemanticsActions.OnClick].label)
        val b = boundsOf(NAVIGATION_HUD_RETRY_TAG)
        assertTrue("the row is ${b.bottom - b.top} tall", b.bottom - b.top >= 48.dp - 0.5.dp)
    }

    /** No usable points: nothing would change, so nothing is offered, and the message is not a control. */
    @Test
    fun `with no usable points there is no Try again and the message takes no taps`() {
        setScreen(ReturnRoute.Unavailable(canRetry = false))
        assertEquals(0, composeRule.onAllNodesWithTag(NAVIGATION_HUD_RETRY_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertFalse(SemanticsActions.OnClick in composeRule.onNodeWithTag(NAVIGATION_HUD_DISTANCE_TAG).fetchSemanticsNode().config)
        val b = boundsOf(NAVIGATION_HUD_DISTANCE_TAG)
        val p = with(composeRule.density) { Offset(((b.left + b.right) / 2).toPx(), ((b.top + b.bottom) / 2).toPx()) }
        composeRule.onRoot().performTouchInput { click(p) }
        composeRule.waitForIdle()
        assertEquals(0, retries)
    }

    /**
     * The owner's sentence shows whole, with "Try again" offered and without, at three phone
     * widths: the reason for "Own line under it". On one line beside "Try again" it showed 12, 16
     * and 20 of its 25 characters at these widths (measured in this class at 4401ce2d). Not
     * ellipsised, all 25 characters visible, and the box as wide as the text needs.
     */
    private fun theWholeSentenceShows(width: String) {
        var composed = false
        for (canRetry in listOf(true, false)) {
            val state = ReturnRoute.Unavailable(canRetry)
            if (!composed) {
                setScreen(state)
                composed = true
            } else {
                composeRule.runOnIdle { route = state }
                composeRule.waitForIdle()
            }
            val m = layoutOf(NAVIGATION_HUD_DISTANCE_TAG)
            val node = composeRule.onNodeWithTag(NAVIGATION_HUD_DISTANCE_TAG, useUnmergedTree = true).fetchSemanticsNode()
            val box = with(composeRule.density) { node.boundsInRoot.width.toDp() }
            val needs = with(composeRule.density) { m.multiParagraph.maxIntrinsicWidth.toDp() }
            println("MEASURED at $width, Try again offered=$canRetry: message box $box, needs $needs, visible chars=${m.getLineEnd(0, visibleEnd = true)} of 25")
            assertFalse("at $width, offered=$canRetry: not ellipsised", m.isLineEllipsized(0))
            assertEquals("at $width, offered=$canRetry: all 25 characters visible", 25, m.getLineEnd(0, visibleEnd = true))
            assertTrue("at $width, offered=$canRetry: box $box holds the text's $needs", box >= needs - 0.5.dp)
        }
    }

    @Test
    fun `the whole sentence shows at 360 dp`() = theWholeSentenceShows("w360dp")

    @Test
    @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `the whole sentence shows at 384 dp, the S22`() = theWholeSentenceShows("w384dp")

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `the whole sentence shows at 412 dp`() = theWholeSentenceShows("w412dp")

    private fun layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val MAP_TAG = "return-route-map"
        const val SAMPLES = 8
        const val MIN_SAMPLED = 8
    }
}
