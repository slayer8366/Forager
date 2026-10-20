package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.motion.PressHighlightShapeKey
import com.zynergylabs.forager.app.ui.motion.PressBounceScaleKey
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.foundation.shape.CircleShape
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.BackByChoice
import com.zynergylabs.forager.app.domain.BackByShown
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.JournalMenuContainerColor
import com.zynergylabs.forager.app.ui.map.JournalMenuContentColor
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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

/**
 * The map's quick settings on the real [AvailabilityScreen] with the real [AvailabilityViewModel]
 * (dispatch 2026-09-28-645, Amendments 1 to 3, RECORD -646 to -648): the three-dot button at the
 * strip's far right and in the navigation display, opened by real touches across its 36 dp square; the menu's three sections,
 * driven by real touches; its sundown and off-track rows changing the same values Settings shows; Back
 * by's line in the strip's last hour; and map long-presses still reaching the map around the button
 * (CLAUDE.md, the `Surface` pitfall).
 *
 * Back by's choices are asserted as what the screen hands `MainActivity` ([BackByChoice]); what the
 * recording ViewModel does with one is `TrackRecordingBackByTest`'s. Where the button sits against the
 * real top inset is device-only (CLAUDE.md: Robolectric reports zero insets).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenQuickSettingsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val minute = 60_000L
    private val now = 1_791_050_400_000L // 2026-10-03T18:00:00Z
    private val origin = Waypoint("origin", 45.31, -122.634, null, "Start", "", 500L, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private val utc = TimeZone.getTimeZone("UTC")
    private val clock = object : SundownClock {
        override fun full(epochMillis: Long): String = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
    }

    private var recording by mutableStateOf(false)
    private var returning by mutableStateOf(false)
    private var backBy by mutableStateOf<BackByShown?>(null)
    private val choices = mutableListOf<BackByChoice>()
    private var clears = 0
    private var longPresses = 0

    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    private lateinit var viewModel: AvailabilityViewModel

    /** What the Battery saver row stored, in order (dispatch 2026-09-28-767). */
    private val storedBatterySaver = mutableListOf<Boolean>()

    private fun setScreen() {
        viewModel = mapLayersViewModel(setBatterySaverEnabled = { storedBatterySaver += it; Result.success(Unit) })
        viewModel.onEnteredForeground()
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            // key: a new screen for each sample of the touch test below (see screenGeneration).
            key(screenGeneration) {
            CompositionLocalProvider(LocalSundownClock provides clock) {
                AvailabilityScreen(
                    uiState = state,
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
                        // The real ViewModel's own Settings callbacks, as MainActivity passes them.
                        onSundownAlertsEnabledChanged = viewModel::onSundownAlertsEnabledChanged,
                        onDarknessMarginChanged = viewModel::onDarknessMarginChanged,
                        onOffTrackReminderChanged = viewModel::onOffTrackReminderChanged,
                        onBatterySaverChanged = viewModel::onBatterySaverChanged,
                    ),
                    mapSlot = map,
                    navigation = AvailabilityScreenNavigation(
                        isReturning = returning,
                        onToggleReturning = { returning = !returning },
                        navigationTarget = if (recording) origin else null,
                        returnRoute = ReturnRoute.Ahead(LatLng(45.32, -122.634), 1_800.0),
                    ),
                    recording = AvailabilityScreenRecording(
                        isRecording = recording,
                        recordingBackBy = backBy,
                        onBackByChoice = { choices += it },
                        onClearBackBy = { clears++ },
                    ),
                    compassProvider = FixedCompass(0f),
                    computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                    currentTime = CurrentTimeProvider { now },
                )
            }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Bumped to compose the screen afresh, which takes the open menu down with it. The touch test's samples use it between
     * touches: under Robolectric neither Back sent to the activity nor a touch on the map's window reaches the menu's own
     * popup window, so neither closes it there (first run, RECORD -687: "closed before the next touch"). On a phone both do;
     * that is the device's to show.
     */
    private var screenGeneration by mutableStateOf(0)

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** The dot is drawn inside the button's clickable square, so its node is merged into the button's: read unmerged. */
    private fun dotShown() = composeRule.onAllNodesWithTag(MAP_QUICK_SETTINGS_DOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun touchAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
        composeRule.waitForIdle()
    }

    /** A real touch at the centre of [tag]'s own bounds (a popup's node is reached through the popup's root). */
    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun recordingWithBackBy(at: Long?, publishedAt: Long = now) {
        composeRule.runOnIdle {
            recording = true
            backBy = at?.let { BackByShown("t1", it, publishedAt) }
        }
        composeRule.waitForIdle()
    }

    // ── Where the button sits (Amendments 2 and 3) ──

    /**
     * Amendment 3 (RECORD -648): "a 3 dot menu at the far right", a 36 dp tap target
     * ([QUICK_SETTINGS_TAP_TARGET]); with Q2, "Taller strip", its whole square is inside the strip and
     * nothing hangs over the map.
     */
    @Test
    fun `portrait, the three-dot button sits at the strip's far right, its whole 36 dp square inside the strip`() {
        setScreen()
        val strip = bounds(STRIP_TAG)
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        assertEquals("36 dp is what the owner said, to start with", 36f, QUICK_SETTINGS_TAP_TARGET.value, 0f)
        assertEquals("the owner's 36 dp: $button", 36f, (button.bottom - button.top).value, 0.5f)
        assertEquals("the owner's 36 dp: $button", 36f, (button.right - button.left).value, 0.5f)
        assertTrue("inside the strip, top to bottom: $button in $strip", button.top >= strip.top - 0.5.dp && button.bottom <= strip.bottom + 0.5.dp)
        assertTrue("at the strip's far right: $button in $strip", strip.right - button.right < 1.dp)
    }

    /**
     * Landscape: the strip is content-width in a top corner; the button is at the strip's own far right
     * there too, whichever corner, because its place follows the strip, not the screen (RECORD -649,
     * the owner: "Not because it's on the far right on the screen, but because it's far right in the strip").
     */
    @Test
    @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `landscape, the three-dot button sits at the strip's far right, inside it`() {
        setScreen()
        val strip = bounds(STRIP_TAG)
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        assertTrue("at the strip's far right: $button in $strip", strip.right - button.right < 1.dp)
        assertTrue("inside the strip, top to bottom: $button in $strip", button.top >= strip.top - 0.5.dp && button.bottom <= strip.bottom + 0.5.dp)
    }

    // ── Real touches on the button ──

    /** A finger is not a point (CLAUDE.md): touches sampled across the 36 dp square, corners included, each open the menu. */
    @Test
    fun `real touches across the button's square each open the menu`() {
        setScreen()
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        val inset = 3.dp
        val points = listOf(
            (button.left + button.right) / 2 to (button.top + button.bottom) / 2,
            button.left + inset to button.top + inset,
            button.right - inset to button.top + inset,
            button.left + inset to button.bottom - inset,
            button.right - inset to button.bottom - inset,
        )
        for ((x, y) in points) {
            touchAt(x, y)
            assertTrue("a real touch at (${x.value}, ${y.value}) in $button opened the menu", shown(MAP_QUICK_SETTINGS_MENU_TAG))
            composeRule.runOnIdle { screenGeneration++ }
            composeRule.waitForIdle()
            assertFalse("a fresh screen before the next touch", shown(MAP_QUICK_SETTINGS_MENU_TAG))
        }
    }

    /**
     * Motion Part 1 on the three-dot button (RECORD -687): held down, the icon dips and its press is drawn as a circle over
     * the button's own square; lifted, it springs back. The touch area is the test above's: the whole square, corners too.
     */
    @Test
    fun `held down, the three-dot icon dips under a round press, and springs back on release`() {
        setScreen()
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        val icon = composeRule.onNode(hasContentDescription(MAP_QUICK_SETTINGS_DESCRIPTION) and hasAnyAncestor(hasTestTag(MAP_QUICK_SETTINGS_BUTTON_TAG)), useUnmergedTree = true)
        fun scale() = icon.fetchSemanticsNode().config[PressBounceScaleKey]
        assertEquals("at rest", 1f, scale(), 0.001f)
        val highlight = composeRule.onNode(hasTestTag(MAP_QUICK_SETTINGS_PRESS_HIGHLIGHT_TAG), useUnmergedTree = true)
        assertEquals("the press is drawn round", CircleShape, highlight.fetchSemanticsNode().config[PressHighlightShapeKey])
        val drawn = highlight.getUnclippedBoundsInRoot()
        assertEquals("over the button's square: $drawn, button $button", (button.right - button.left).value, (drawn.right - drawn.left).value, 0.5f)
        val at = with(composeRule.density) { Offset(((button.left + button.right) / 2).toPx(), ((button.top + button.bottom) / 2).toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val pressed = scale()
        assertTrue("dipped while pressed: $pressed", pressed < 0.97f && pressed >= MotionTokens.PRESS_BOUNCE_SCALE - 0.05f)
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals("back at rest", 1f, scale(), 0.001f)
    }

    /**
     * Around the button the map still takes long-presses: points in the strip's own row beside the
     * button, and just under the strip below it, sampled and counted. The button and the coordinates are
     * controls; everything else in the strip takes no touch.
     */
    @Test
    fun `long-presses around the button and just under the strip reach the map`() {
        setScreen()
        recordingWithBackBy(now + 30 * minute)
        val strip = bounds(STRIP_TAG)
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().map {
            with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) }
        }
        var sampled = 0
        val ys = listOf(strip.top + 3.dp, (strip.top + strip.bottom) / 2, strip.bottom - 3.dp, strip.bottom + 6.dp)
        // Right around the 36 dp square: 3 dp and 10 dp to its left, and under it just below the strip.
        val xs = listOf(button.left - 3.dp, button.left - 10.dp, button.left - 30.dp, button.left - 80.dp, (button.left + button.right) / 2, button.right - 3.dp, strip.left + 40.dp)
        for (y in ys) {
            for (x in xs) {
                val inButton = x >= button.left && x <= button.right && y >= button.top && y <= button.bottom
                if (inButton) continue
                if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
                val before = longPresses
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(p) }
                composeRule.waitForIdle()
                assertEquals("a long-press at (${x.value}, ${y.value}), button $button, strip $strip, must reach the map", before + 1, longPresses)
                sampled++
            }
        }
        assertTrue("the row under the button, just below the strip, is sampled", sampled >= 4)
        assertTrue("at least $MIN_SAMPLED points sampled, not $sampled", sampled >= MIN_SAMPLED)
    }

    // ── The menu ──

    @Test
    fun `the menu is at the map chrome's opacity, its content opaque`() {
        setScreen()
        touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)
        val node = composeRule.onNodeWithTag(MAP_QUICK_SETTINGS_MENU_TAG).fetchSemanticsNode()
        assertEquals("container alpha", MAP_CHROME_OVER_MAP_ALPHA, node.config[JournalMenuContainerColor].alpha, 0.001f)
        assertEquals("content alpha", 1f, node.config[JournalMenuContentColor].alpha, 0.001f)
    }

    @Test
    fun `not recording, Back by says how to get one and offers no choice`() {
        setScreen()
        touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)
        assertEquals(BACK_BY_NOT_RECORDING_TEXT, text(QUICK_BACK_BY_NOT_RECORDING_TAG))
        assertFalse(shown(quickBackByHoursTag(1)))
        assertFalse(shown(QUICK_BACK_BY_PICK_TAG))
        assertTrue("Sundown is there without a recording", shown(QUICK_SUNDOWN_ALERTS_TAG))
        assertTrue("and Off-track", shown(QUICK_OFF_TRACK_TAG))
    }

    @Test
    fun `recording, real touches on +1 h, +2 h and +3 h hand those choices on, and the menu stays open`() {
        setScreen()
        recordingWithBackBy(null)
        touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)
        for (hours in listOf(1, 2, 3)) {
            touchCentreOf(quickBackByHoursTag(hours))
            assertTrue("the menu stays open after +$hours h (Q3)", shown(MAP_QUICK_SETTINGS_MENU_TAG))
        }
        assertEquals(listOf(BackByChoice.HoursFromNow(1), BackByChoice.HoursFromNow(2), BackByChoice.HoursFromNow(3)), choices)
    }

    @Test
    fun `Pick a time opens the clock picker over the map at 80 percent, and Set hands on the picked time`() {
        setScreen()
        recordingWithBackBy(null)
        touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)
        touchCentreOf(QUICK_BACK_BY_PICK_TAG)
        assertTrue(shown(BACK_BY_TIME_PICKER_TAG))
        val dialog = composeRule.onNodeWithTag(BACK_BY_TIME_PICKER_TAG).fetchSemanticsNode()
        assertEquals("dialog alpha", MAP_CHROME_OVER_MAP_ALPHA, dialog.config[com.zynergylabs.forager.app.ui.map.MapChromeContainerColor].alpha, 0.001f)
        touchCentreOf(BACK_BY_TIME_PICKER_SET_TAG)
        assertFalse(shown(BACK_BY_TIME_PICKER_TAG))
        assertEquals("one picked time, a clock time", 1, choices.size)
        assertTrue(choices.single() is BackByChoice.AtTime)
    }

    /**
     * Dispatch 2026-09-28-796, the walk's "Pick a time, showed 1:55" (RECORD -795): the owner picked an
     * afternoon time with the clock starting at an afternoon hour (an hour from now). Whatever reaches
     * `BackByChoice.AtTime` must be that afternoon's hour, 13, not 1: an hour of 1 is 1:55 AM, which
     * `backByAtFor` makes tomorrow's, so it would never fire on the walk. The phone's zone is pinned to
     * UTC so the picker opens at 7 PM ([now] is 18:00 UTC). The dial's numbers are reached by their
     * own accessibility labels; the claim is what the picker hands on, not what a finger reaches.
     */
    @Test
    fun `Pick a time, an afternoon hour picked on a clock that opened in the afternoon, hands on that afternoon's hour`() {
        val zoneBefore = TimeZone.getDefault()
        TimeZone.setDefault(utc)
        try {
            setScreen()
            recordingWithBackBy(null)
            touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)
            touchCentreOf(QUICK_BACK_BY_PICK_TAG)
            composeRule.onNodeWithContentDescription("1 o'clock").performClick()
            composeRule.waitForIdle()
            // The dial moves to minutes on a finger's tap; a semantic click does not, so the header's
            // minutes selector is chosen first when the minutes are not up yet.
            if (composeRule.onAllNodes(hasContentDescription("55 minutes")).fetchSemanticsNodes().isEmpty()) {
                val selectors = composeRule.onAllNodes(hasContentDescription("Select minutes", substring = true)).fetchSemanticsNodes()
                val labels = composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { root -> descriptionsUnder(root) }
                assertTrue("no minutes selector; the picker's labels: $labels", selectors.isNotEmpty())
                composeRule.onAllNodes(hasContentDescription("Select minutes", substring = true)).onFirst().performClick()
                composeRule.waitForIdle()
            }
            composeRule.onNodeWithContentDescription("55 minutes").performClick()
            composeRule.waitForIdle()
            touchCentreOf(BACK_BY_TIME_PICKER_SET_TAG)
            assertEquals("the picked time, as the ViewModel receives it", listOf(BackByChoice.AtTime(13, 55)), choices.toList())
        } finally {
            TimeZone.setDefault(zoneBefore)
        }
    }

    private fun descriptionsUnder(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty() + node.children.flatMap { descriptionsUnder(it) }

    /** Q3, "Menu shows it + dot (Recommended)", kept by Amendment 3: with a time set, the menu's top line says it, Clear clears, and the button has its dot. */
    @Test
    fun `with a time set, the menu's top line shows it, Clear clears it, and the button carries a dot`() {
        setScreen()
        recordingWithBackBy(null)
        assertFalse("no dot with none set", dotShown())
        recordingWithBackBy(now + 2 * 60 * minute)
        assertTrue("a dot while set", dotShown())
        touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)
        assertEquals("Back by ${clock.full(now + 2 * 60 * minute)}", text(QUICK_BACK_BY_SET_TAG))
        touchCentreOf(QUICK_BACK_BY_CLEAR_TAG)
        assertEquals(1, clears)
    }

    /** The sundown and off-track rows are Settings' own values: a real touch changes the ViewModel's state, which Settings reads. */
    @Test
    fun `the Sundown and Off-track rows change the same values Settings shows`() {
        setScreen()
        assertTrue(viewModel.uiState.value.sundownAlertsEnabled)
        assertTrue(viewModel.uiState.value.offTrackReminderEnabled)
        touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)

        touchCentreOf(QUICK_SUNDOWN_ALERTS_TAG)
        assertFalse("Sundown alerts off", viewModel.uiState.value.sundownAlertsEnabled)
        touchCentreOf(quickDarknessMarginTag(90))
        assertEquals("Dark under trees: 1 h 30", 90, viewModel.uiState.value.darknessMarginMinutes)
        touchCentreOf(QUICK_OFF_TRACK_TAG)
        assertFalse("Off-track reminder off", viewModel.uiState.value.offTrackReminderEnabled)
    }

    /**
     * Dispatch 2026-09-28-767: the Battery saver row, off by default, in the owner's words (RECORD -768),
     * and real touches across the row's own width, not one at its centre (CLAUDE.md), each flipping it
     * and each stored, which is what moves the fix requests.
     */
    @Test
    fun `the Battery saver row starts off, says what it does, and real touches across it turn it on and off and store each`() {
        setScreen()
        assertFalse("off by default", viewModel.uiState.value.batterySaverEnabled)
        touchCentreOf(MAP_QUICK_SETTINGS_BUTTON_TAG)
        assertTrue(shown(QUICK_BATTERY_SAVER_TAG))
        assertTrue(
            "the label and its one line",
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(QUICK_BATTERY_SAVER_LABEL)).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(androidx.compose.ui.test.hasText(QUICK_BATTERY_SAVER_EXPLANATION)).fetchSemanticsNodes().isNotEmpty(),
        )
        val expected = mutableListOf<Boolean>()
        for (fraction in listOf(0.05f, 0.3f, 0.6f, 0.95f)) {
            composeRule.onNodeWithTag(QUICK_BATTERY_SAVER_TAG).performTouchInput { click(Offset(width * fraction, height / 2f)) }
            composeRule.waitForIdle()
            val on = expected.size % 2 == 0
            expected += on
            assertEquals("a touch at $fraction of the row's width", on, viewModel.uiState.value.batterySaverEnabled)
        }
        assertEquals("each change stored, in order", expected, storedBatterySaver)
    }

    // ── The line (Amendment 1) ──

    @Test
    fun `the strip shows Back by under the sunset line only in the last hour`() {
        setScreen()
        recordingWithBackBy(now + 61 * minute)
        assertFalse("61 min away: no line", shown(STRIP_BACK_BY_LINE_TAG))
        recordingWithBackBy(now + 60 * minute)
        assertEquals("Back by ${clock.full(now + 60 * minute)}", text(STRIP_BACK_BY_LINE_TAG))
        recordingWithBackBy(now - 5 * minute)
        assertEquals("passed and still set: the line stays", "Back by ${clock.full(now - 5 * minute)}", text(STRIP_BACK_BY_LINE_TAG))
        recordingWithBackBy(null)
        assertFalse("cleared: no line", shown(STRIP_BACK_BY_LINE_TAG))
    }

    /**
     * Dispatch 2026-10-12-08 (RECORD -947, the owner: "Yes, only on the strip (Recommended)"; RECORD -950, -951 item 5): navigating,
     * the strip stays and keeps the three-dot button, above the X; the Back by line moves into the navigation info, shown once.
     * Was (Q1, "Gear in nav display too"; dispatch 2026-10-12-06): "navigating, the button and the line move into the navigation
     * display, under the exit", asserting `assertFalse("the strip is hidden", shown(STRIP_TAG))`, the button inside the HUD, and
     * `button.top >= exit.bottom`.
     */
    @Test
    fun `navigating, the button stays on the strip above the exit, and the line moves into the navigation info, once`() {
        setScreen()
        recordingWithBackBy(now + 20 * minute)
        composeRule.runOnIdle { returning = true }
        composeRule.waitForIdle()
        assertTrue("the strip stays", shown(STRIP_TAG))
        assertEquals("Back by ${clock.full(now + 20 * minute)}", text(NAVIGATION_HUD_BACK_BY_LINE_TAG))
        assertFalse("the strip's own Back by line does not also show", shown(STRIP_BACK_BY_LINE_TAG))
        assertEquals("one three-dot button", 1, composeRule.onAllNodesWithTag(MAP_QUICK_SETTINGS_BUTTON_TAG).fetchSemanticsNodes().size)
        val strip = bounds(STRIP_TAG)
        val hud = bounds(NAVIGATION_HUD_TAG)
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        assertTrue("the button is on the strip, above the navigation info: $button, info $hud", button.top >= strip.top && button.bottom <= hud.top + 0.5.dp)
        val exit = bounds(NAVIGATION_HUD_EXIT_TAG)
        assertTrue("above the exit: $button, exit $exit", button.bottom <= exit.top + 0.5.dp)
        assertEquals("the same 36 dp", 36f, (button.right - button.left).value, 0.5f)
        touchAt((button.left + button.right) / 2, (button.top + button.bottom) / 2)
        assertTrue(shown(MAP_QUICK_SETTINGS_MENU_TAG))
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val MAP_TAG = "quick-settings-map"
        const val STRIP_TAG = "compass-elevation-strip"
        const val MIN_SAMPLED = 10
    }
}
