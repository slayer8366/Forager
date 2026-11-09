package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.CENTRE_PIN_CONFIRM_ROW_TAG
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.motion.MapPopUpScaleKey
import com.zynergylabs.forager.app.ui.motion.TabLeavingKey
import com.zynergylabs.forager.app.ui.track.RecordingNotice
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 2 (dispatch 2026-09-28-666), items 3, 4, 5 and 7, through [AvailabilityScreen] with a stand-in map that counts
 * real long-presses on itself, as the navigation view's tests do.
 *
 * Item 5 (the owner, RECORD -651: "Let taps through at once": "From the moment something starts to leave, taps go to the map
 * beneath it"): each pop-up is made to leave, caught with the clock stopped while it is still on screen, and long-pressed with
 * a real finger where it was; the map must take the long-press, and the pop-up's own action must not fire. Item 4's "touch areas
 * unchanged": caught mid-grow, a real touch across the pop-up's settled bounds still reaches its control. Reduced motion is the
 * transition scale alone at 0, so Compose's own animations still run (they follow the animator scale) and what changes is the
 * app's reading of the setting: no grow, no slide, no glide.
 *
 * Nothing here has been run: written with the code, before the build (dispatch: Gradle on the planner's go).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class MapPopUpMotionTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val sighting = Sighting(
        observationId = 7L,
        taxonId = 107L,
        scientificName = "Cantharellus formosus",
        commonName = "Pacific golden chanterelle",
        lat = 45.52,
        lng = -122.68,
        observedOn = LocalDate.of(2025, 8, 1),
        photoUrl = null,
    )

    private var returning by mutableStateOf(false)
    private var tripStartWarning by mutableStateOf<RecordingNotice?>(null)
    private val compass = MutableStateFlow<CompassReading?>(CompassReading(80f, HeadingUncertainty.Estimated(2f), 0L))

    private var lastRenderMode: MapRenderMode? = null
    private var onSightingTap: ((Sighting, Offset, Float) -> Unit)? = null
    private var longPresses = 0

    private val map: MapSlot = { _, _, renderMode, _, _, _, sightingTap, _, modifier ->
        lastRenderMode = renderMode
        onSightingTap = sightingTap
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    /** The phone's reduce-motion setting, as the transition scale alone (see the class comment). */
    private fun reduceMotionOn() = Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)

    private fun setScreen() {
        // Under the app's theme, as MainActivity composes it, so the reduce-motion setting is provided (ForagerTheme's
        // ProvideReduceMotion); without it every screen reads the default, motion on.
        composeRule.setContent {
            ForagerTheme {
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
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    /** Stops the clock and lets [frames] frames through, so a leave or an entrance just begun is caught on screen. */
    private fun pauseAfter(frames: Int = 2, action: () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        action()
        // With the clock stopped, a state write outside composition reaches the next frame only once applied (motion Part 1's
        // report, the fan's tests): the action, then the apply, then frames.
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        repeat(frames) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun root() = composeRule.onAllNodes(isRoot()).onFirst()

    private fun px(p: DpOffset) = with(composeRule.density) { Offset(p.x.toPx(), p.y.toPx()) }

    /** A real tap, delivered without settling. */
    private fun tapAt(p: DpOffset) = root().performTouchInput { click(px(p)) }

    /** A real long-press: down here, the clock run past the long-press timeout, up. The hit path is fixed at the down. */
    private fun longPressAt(p: DpOffset) {
        root().performTouchInput { down(px(p)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        root().performTouchInput { up() }
    }

    private fun boundsOf(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun centre(b: DpRect) = DpOffset((b.left + b.right) / 2, (b.top + b.bottom) / 2)

    /**
     * Five points across a control's own bounds (CLAUDE.md: a finger is not a point), inside its drawn shape: Return to Route is a
     * stadium whose Surface clips its touches to that shape, so the corner samples are 12 dp in from the ends and 6 dp from the
     * edges (the navigation view's own pill test uses 12 and 4), inside the rounded ends.
     */
    private fun samples(b: DpRect, insetX: Dp = 12.dp, insetY: Dp = 6.dp) = listOf(
        centre(b),
        DpOffset(b.left + insetX, b.top + insetY),
        DpOffset(b.right - insetX, b.top + insetY),
        DpOffset(b.left + insetX, b.bottom - insetY),
        DpOffset(b.right - insetX, b.bottom - insetY),
    )

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** The draw-only grow of the pop-up holding [tag]: 1 at rest. */
    private fun popUpScale(tag: String): Float =
        composeRule.onNode(SemanticsMatcher.keyIsDefined(MapPopUpScaleKey) and hasAnyDescendant(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNode().config[MapPopUpScaleKey]

    private fun switchReturning(value: Boolean) {
        composeRule.runOnIdle { returning = value }
        settle()
    }

    private fun view() = lastRenderMode?.navigationView ?: throw AssertionError("navigating, so the map was asked for its navigation view")

    /** The real map reports a drag away from the navigation view through this; Return to Route then shows. */
    private fun moveTheMapAway() = composeRule.runOnUiThread { view().onLeftView() }

    // ── Return to Route (scout N3) ─────────────────────────────────────────────────────────────

    @Test
    fun `a leaving Return to Route takes no touch - long-presses across where it was reach the map`() {
        setScreen()
        switchReturning(true)
        for (index in 0 until 5) {
            moveTheMapAway()
            settle()
            assertTrue("move $index shows the pill", shown(RETURN_TO_ROUTE_TAG))
            val b = boundsOf(RETURN_TO_ROUTE_TAG)
            val point = samples(b)[index]
            val requestsBefore = view().restoreRequestId
            // The tap that brings the view back is what makes the pill leave.
            pauseAfter { tapAt(centre(b)) }
            assertTrue("the pill is still on screen, leaving", shown(RETURN_TO_ROUTE_TAG))
            assertEquals("the tap on it was its own", requestsBefore + 1, view().restoreRequestId)
            val pressesBefore = longPresses
            longPressAt(point)
            settle()
            assertEquals("a long-press at $point on the leaving pill reached the map", pressesBefore + 1, longPresses)
            assertEquals("and the leaving pill did not take it", requestsBefore + 1, view().restoreRequestId)
            assertFalse("gone in the end", shown(RETURN_TO_ROUTE_TAG))
        }
    }

    @Test
    fun `mid-grow, Return to Route takes touches across its full settled bounds`() {
        setScreen()
        switchReturning(true)
        moveTheMapAway()
        settle()
        val settled = boundsOf(RETURN_TO_ROUTE_TAG)
        // Bring the view back, so the next move away shows the pill afresh.
        tapAt(centre(settled))
        settle()
        for ((index, point) in samples(settled).withIndex()) {
            pauseAfter { moveTheMapAway() }
            assertTrue("growing $index: its drawing is still small", popUpScale(RETURN_TO_ROUTE_TAG) < 0.999f)
            assertEquals("laid out where it settles from its first frame", settled, boundsOf(RETURN_TO_ROUTE_TAG))
            val before = view().restoreRequestId
            tapAt(point)
            settle()
            assertEquals("a real touch at $point mid-grow reached the pill", before + 1, view().restoreRequestId)
        }
    }

    @Test
    fun `under reduced motion Return to Route fades without growing, and still lets taps through as it leaves`() {
        reduceMotionOn()
        setScreen()
        switchReturning(true)
        pauseAfter { moveTheMapAway() }
        assertTrue(shown(RETURN_TO_ROUTE_TAG))
        assertEquals("no grow under reduced motion", 1f, popUpScale(RETURN_TO_ROUTE_TAG), 0.0001f)
        settle()
        val b = boundsOf(RETURN_TO_ROUTE_TAG)
        pauseAfter { tapAt(centre(b)) }
        assertTrue("still fading out", shown(RETURN_TO_ROUTE_TAG))
        val before = longPresses
        longPressAt(samples(b)[1])
        settle()
        assertEquals("the long-press on the leaving pill reached the map", before + 1, longPresses)
    }

    // ── The bubble (scout M5) ──────────────────────────────────────────────────────────────────

    private fun openBubble() {
        composeRule.runOnUiThread { onSightingTap!!(sighting, Offset(300f, 600f), 0f) }
    }

    @Test
    fun `a leaving bubble takes no touch - a long-press on its card reaches the map`() {
        setScreen()
        openBubble()
        settle()
        assertTrue("the bubble shows", shown(BUBBLE_TAG))
        val card = boundsOf(BUBBLE_TAG)
        val close = boundsOf(BUBBLE_CLOSE_TAG)
        pauseAfter { tapAt(centre(close)) }
        assertTrue("the bubble is still on screen, leaving", shown(BUBBLE_TAG))
        val before = longPresses
        longPressAt(DpOffset(card.left + 12.dp, (card.top + card.bottom) / 2))
        settle()
        assertEquals("the long-press on the leaving card reached the map", before + 1, longPresses)
        assertFalse("gone in the end", shown(BUBBLE_TAG))
    }

    @Test
    fun `mid-grow, the bubble's close button takes a real touch where it settles, and the bubble grows only with motion on`() {
        setScreen()
        openBubble()
        settle()
        val close = boundsOf(BUBBLE_CLOSE_TAG)
        tapAt(centre(close))
        settle()
        assertFalse(shown(BUBBLE_TAG))

        pauseAfter { openBubble() }
        assertTrue("growing: drawn small", popUpScale(BUBBLE_TAG) < 0.999f)
        assertEquals("the close button is laid out where it settles", close, boundsOf(BUBBLE_CLOSE_TAG))
        tapAt(centre(close))
        settle()
        assertFalse("the touch mid-grow closed it", shown(BUBBLE_TAG))
    }

    @Test
    fun `under reduced motion the bubble does not grow`() {
        reduceMotionOn()
        setScreen()
        pauseAfter { openBubble() }
        assertTrue(shown(BUBBLE_TAG))
        assertEquals(1f, popUpScale(BUBBLE_TAG), 0.0001f)
    }

    // ── Back during a tab fade (Amendment 1, RECORD -672, item 5) ──────────────────────────────

    /**
     * With a bubble open on Maps, the bubble's Back handler is the innermost one. Leaving Maps for List and pressing Back
     * mid-fade must act on the arriving side: the screen's own Back, which from List goes to Maps. Before the leaving tab's
     * handlers were switched off, the leaving bubble took that Back, closed itself, and the screen stayed on List.
     */
    @Test
    fun `Back during a tab fade acts on the arriving tab, not on the leaving tab's bubble`() {
        setScreen()
        openBubble()
        settle()
        assertTrue(shown(BUBBLE_TAG))
        val list = composeRule.onNodeWithText("List").getUnclippedBoundsInRoot()
        pauseAfter { tapAt(centre(list)) }
        // The leaving tab keeps only its leaving marker in the semantics tree (Amendment 2), so that is what is read here.
        assertEquals("Maps is still on screen, fading out", 1, composeRule.onAllNodes(SemanticsMatcher.expectValue(TabLeavingKey, true), useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertTrue("Back from List went to Maps: the arriving side took it", shown(MAP_TAG))
    }

    // ── The centre pin and its OK/Cancel row (scout M9) ────────────────────────────────────────

    private fun openTripPicker(paused: Boolean) {
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").getUnclippedBoundsInRoot().let { tapAt(centre(it)) }
        settle()
        val trip = composeRule.onNodeWithText("Trip").getUnclippedBoundsInRoot()
        if (paused) pauseAfter { tapAt(centre(trip)) } else { tapAt(centre(trip)); settle() }
    }

    private fun buttonBounds(label: String): DpRect = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()

    @Test
    fun `a leaving OK and Cancel row takes no touch - a long-press on its OK reaches the map and picks nothing`() {
        setScreen()
        openTripPicker(paused = false)
        assertTrue(shown(CENTRE_PIN_CONFIRM_ROW_TAG))
        val ok = buttonBounds("OK")
        pauseAfter { tapAt(centre(buttonBounds("Cancel"))) }
        assertTrue("the row is still on screen, leaving", shown(CENTRE_PIN_CONFIRM_ROW_TAG))
        val before = longPresses
        longPressAt(centre(ok))
        settle()
        assertEquals("the long-press on the leaving OK reached the map", before + 1, longPresses)
        assertTrue("and OK did not open the trip's date dialog", composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty())
        assertFalse("gone in the end", shown(CENTRE_PIN_CONFIRM_ROW_TAG))
    }

    @Test
    fun `mid-grow, OK takes a real touch where it settles`() {
        setScreen()
        openTripPicker(paused = false)
        val ok = buttonBounds("OK")
        tapAt(centre(buttonBounds("Cancel")))
        settle()
        openTripPicker(paused = true)
        assertTrue("growing: drawn small", popUpScale(CENTRE_PIN_CONFIRM_ROW_TAG) < 0.999f)
        assertEquals("OK is laid out where it settles", ok, buttonBounds("OK"))
        tapAt(centre(ok))
        settle()
        assertTrue("the touch on OK mid-grow opened the trip's date dialog", composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())
    }

    // ── Navigation start and stop (scouts N1, N2) ──────────────────────────────────────────────

    /**
     * Dispatch 2026-10-12-08 (RECORD -948, -950; the owner: "have the card just extend down rather than a dual tuck"): starting
     * navigation, the compass strip stays where it is and the navigation info grows down from under its line, on the navigation
     * view's timing; stopping, it shrinks back, and while it leaves its controls take no touch (motion Part 2, item 5): a
     * long-press on the leaving X reaches the map. Was "starting navigation, the leaving strip takes no touch and the navigation
     * display slides down into place": the strip leaving (`assertFalse("the strip is gone once navigating", shown(STRIP_TAG))`),
     * a long-press on it reaching the map, and the display sliding (`hudMid.top < hudSettled.top`).
     */
    @Test
    fun `starting navigation, the strip stays and the navigation info grows down under its line, and leaving it takes no touch`() {
        setScreen()
        val strip = boundsOf(STRIP_TAG)
        switchReturning(true)
        val infoSettled = boundsOf(NAVIGATION_HUD_TAG)
        val cardSettled = boundsOf(STRIP_TAG)
        assertEquals("navigating, the strip's top is where it was", strip.top.value, cardSettled.top.value, 0.5f)
        switchReturning(false)

        // Six frames in, part way through the growth (at two the card had not yet grown past the strip's own line: run 38259665051).
        pauseAfter(frames = 6) { composeRule.runOnUiThread { returning = true } }
        assertTrue("the strip is on screen throughout", shown(STRIP_TAG))
        assertTrue("the info is on screen, arriving", shown(NAVIGATION_HUD_TAG))
        val infoMid = boundsOf(NAVIGATION_HUD_TAG)
        val cardMid = boundsOf(STRIP_TAG)
        assertEquals("mid-start the info's top ${infoMid.describe()} is where it settles ${infoSettled.describe()}", infoSettled.top.value, infoMid.top.value, 0.5f)
        // The card (the strip's own card, which the info extends) grows down: laid out, so its fill grows with it.
        assertTrue("mid-start the card ${cardMid.describe()} is shorter than it settles ${cardSettled.describe()}: it extends down", cardMid.bottom < cardSettled.bottom - 0.5.dp)
        assertTrue("and taller than the strip alone ${strip.describe()}", cardMid.bottom > strip.bottom + 0.5.dp)
        settle()
        assertEquals("and the info settled in its place", infoSettled, boundsOf(NAVIGATION_HUD_TAG))

        val exit = boundsOf(NAVIGATION_HUD_EXIT_TAG)
        pauseAfter { composeRule.runOnUiThread { returning = false } }
        assertTrue("the info is still on screen, leaving", shown(NAVIGATION_HUD_TAG))
        val before = longPresses
        longPressAt(DpOffset((exit.left + exit.right) / 2, (exit.top + exit.bottom) / 2))
        settle()
        assertEquals("the long-press on the leaving X reached the map", before + 1, longPresses)
        assertFalse("the info is gone once stopped", shown(NAVIGATION_HUD_TAG))
        assertTrue("and the strip is where it was", shown(STRIP_TAG))
    }

    // Dispatch 2026-10-12-08: the navigation info grows rather than slides (portrait); under reduced motion it neither grows nor
    // slides, it fades in place. The assertion is unchanged.
    @Test
    fun `under reduced motion the navigation display does not slide, it fades in place`() {
        reduceMotionOn()
        setScreen()
        switchReturning(true)
        val hudSettled = boundsOf(NAVIGATION_HUD_TAG)
        switchReturning(false)
        pauseAfter { composeRule.runOnUiThread { returning = true } }
        assertTrue(shown(NAVIGATION_HUD_TAG))
        assertEquals("no slide under reduced motion", hudSettled, boundsOf(NAVIGATION_HUD_TAG))
    }

    // ── Snackbar heights (scout S6) ────────────────────────────────────────────────────────────

    private fun showSnackbar() {
        composeRule.runOnIdle { tripStartWarning = RecordingNotice(id = 1, message = "Do Not Disturb is on. If you go off track, the alert may not be felt.") }
        composeRule.waitUntil(timeoutMillis = 5_000) { shown(COMPACT_SNACKBAR_TAG) }
        settle()
    }

    @Test
    fun `the snackbar glides up above Return to Route rather than jumping`() {
        setScreen()
        switchReturning(true)
        showSnackbar()
        val low = boundsOf(COMPACT_SNACKBAR_TAG)
        pauseAfter(frames = 3) { moveTheMapAway() }
        val mid = boundsOf(COMPACT_SNACKBAR_TAG)
        settle()
        val high = boundsOf(COMPACT_SNACKBAR_TAG)
        assertTrue("it ends higher: ${low.describe()} to ${high.describe()}", high.bottom < low.bottom - 1.dp)
        assertTrue("caught mid-glide: ${mid.describe()} between ${low.describe()} and ${high.describe()}", mid.bottom < low.bottom - 0.5.dp && mid.bottom > high.bottom + 0.5.dp)
    }

    @Test
    fun `under reduced motion the snackbar moves above Return to Route at once`() {
        reduceMotionOn()
        setScreen()
        switchReturning(true)
        showSnackbar()
        pauseAfter(frames = 3) { moveTheMapAway() }
        val mid = boundsOf(COMPACT_SNACKBAR_TAG)
        settle()
        assertEquals("no glide", boundsOf(COMPACT_SNACKBAR_TAG), mid)
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val MAP_TAG = "map-popup-motion-map"
        const val BUBBLE_TAG = "observation-bubble"
        const val BUBBLE_CLOSE_TAG = "observation-bubble-close"
        const val STRIP_TAG = "compass-elevation-strip"
    }
}
