package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
 * J6c, the tablet map's controls (dispatch 2026-09-28-152 item 14, ruled in continuation 2026-09-28-173:
 * `prompts/preserved/2026-09-29-31.md`), through the real [AvailabilityScreen] with a stub map that counts
 * the taps and long-presses it is handed.
 *
 * The tablet takes the phone's PORTRAIT cluster arrangement (the bar over the record | return pill), never
 * the landscape L. Every control is touched at real screen coordinates (CLAUDE.md, "a semantic click asserts
 * wiring, not routing"), the map must still get a long-press beside the cluster, and the cluster drags,
 * snaps, minimises and restores. Fullscreen hides the Journal column and the search bar and exit restores
 * both; the compass strip runs across the top with the chip row below it.
 *
 * One body of tests, three sizes: the SM-X800 in portrait (824 x 1318 dp, the tabbed map), in landscape
 * (1318 x 824, the map beside the list) and 1280 x 900. Robolectric draws no real map and reports zero
 * system-bar insets, so nothing here says anything about the map's rendering or the bars' real insets
 * (the device-only list in the J6 report).
 *
 * Written before the build and pushed: at base every test that needs the cluster fails for the reason its
 * message gives.
 */
abstract class WideMapControlsTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var mapTaps by mutableIntStateOf(0)
    private var mapLongPresses by mutableIntStateOf(0)
    private var capturedContent: MapOverlayContent? = null
    private var locateMeCalls by mutableIntStateOf(0)
    private var recording by mutableStateOf(false)
    private var toggleReturningCalls by mutableIntStateOf(0)
    private var fullscreenWrites = mutableListOf<Boolean>()

    private val stubMap: MapSlot = { _, content, _, _, _, onTap, _, _, modifier ->
        capturedContent = content
        Box(
            modifier
                .testTag(MAP_SLOT)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { mapTaps++; onTap() }, onLongPress = { mapLongPresses++ })
                },
        )
    }

    private val fakeCompass = object : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(null)
    }

    protected fun setScreen(shownEntry: Boolean = false, withTrip: Boolean = false, navigating: Boolean = false) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED.copy(plannedTrips = if (withTrip) listOf(TRIP) else emptyList()),
                onUseCurrentLocation = {},
                onLocateMe = { locateMeCalls++ },
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
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
                onMapFullscreenChanged = { fullscreenWrites += it },
                isRecording = recording,
                onToggleRecording = { recording = !recording },
                returnToStart = if (recording) ReturnToStartInfo(bearingDegrees = 180.0, distanceMeters = 1200.0, elevationDifferenceMeters = null) else null,
                isReturning = navigating,
                onToggleReturning = { toggleReturningCalls++ },
                cartographyUiState = if (shownEntry) ONE_SHOWN_ENTRY else CartographyUiState(),
                compassProvider = fakeCompass,
                mapSlot = stubMap,
            )
        }
        composeRule.waitForIdle()
    }

    // ── Helpers ──

    private fun exists(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun descExists(d: String) = composeRule.onAllNodesWithContentDescription(d).fetchSemanticsNodes().isNotEmpty()
    private fun boundsTag(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun boundsDesc(d: String) = composeRule.onNodeWithContentDescription(d).getUnclippedBoundsInRoot()

    /** A real touch at the centre of [b], through the root, so it goes where a finger would and is hit-tested. */
    private fun touch(b: DpRect) {
        composeRule.onRoot().performTouchInput { click(Offset((b.left.value + b.width.value / 2f) * density, (b.top.value + b.height.value / 2f) * density)) }
        composeRule.waitForIdle()
    }

    private fun touchDesc(d: String) = touch(boundsDesc(d))
    private fun touchTag(tag: String) = touch(boundsTag(tag))

    /**
     * Three real touches across [b]'s inboard part. Not its outer sliver: the minimise handle straddles the bar's
     * outer edge at the bar's mid-height, which is the locate row's height, by the phone's own design ("the
     * handle deliberately overlaps the bar's own outermost sliver", `AvailabilityCompactMapUi`), so a touch there
     * minimises the cluster (a first draft of this test sampled to 75% across and found exactly that).
     */
    private fun sampledTouches(b: DpRect, action: () -> Unit) {
        for ((fx, fy) in listOf(0.2f to 0.25f, 0.35f to 0.5f, 0.5f to 0.75f)) {
            composeRule.onRoot().performTouchInput { click(Offset((b.left.value + b.width.value * fx) * density, (b.top.value + b.height.value * fy) * density)) }
            composeRule.waitForIdle()
            action()
        }
    }

    private fun mapPane(): DpRect = boundsTag(MAP_SLOT)
    private fun cluster(): DpRect = boundsTag(MAP_ICON_CLUSTER_TAG)
    private fun drawerShowing() = composeRule.onAllNodesWithText("Trip Planner").fetchSemanticsNodes().isNotEmpty()
    private fun searchBarShowing() = descExists("Advanced search options")

    private fun showMap() {
        // The tabbed layout (portrait) opens on Maps already; nothing to do at any of the three sizes.
    }

    // ── The cluster is on the tablet map, in place of the two separate buttons ──

    @Test
    fun `FAILS AT BASE the icon cluster is on the tablet map, at its right edge, and the separate Layers and plus buttons are gone`() {
        setScreen()
        showMap()

        assertTrue("the icon cluster is composed on the tablet map", exists(MAP_ICON_CLUSTER_TAG))
        val pane = mapPane()
        val c = cluster()
        assertTrue("the cluster ($c) is inside the map pane ($pane), at its right edge", c.right <= pane.right && c.right >= pane.right - 24.dp && c.left >= pane.left)
        assertEquals("the wide map's own Layers button is gone", false, exists("wide-layers-button"))
        assertEquals("one 'plan a trip or log a find' control, the bar's own row", 1, composeRule.onAllNodesWithContentDescription("Plan a trip or log a find here").fetchSemanticsNodes().size)
    }

    @Test
    fun `FAILS AT BASE the tablet takes the phone's portrait arrangement, the pill under the bar, never the L`() {
        setScreen()
        val c = cluster()
        val bar = boundsDesc("Center on my location")
        val record = boundsTag("control-pill-record")
        assertTrue("the record pill is below the bar's rows ($record vs $bar)", record.top >= bar.bottom)
        assertTrue("and inside the cluster's width, not beside the bar", record.left >= c.left && record.right <= c.right)
    }

    // ── Real touches on every control ──

    @Test
    fun `FAILS AT BASE a real touch across the locate row calls locate-me`() {
        setScreen()
        val before = locateMeCalls
        sampledTouches(boundsDesc("Center on my location")) { }
        assertEquals("three real touches, three locate-me calls", before + 3, locateMeCalls)
    }

    @Test
    fun `FAILS AT BASE a real touch on orient-to-north asks the map to reset its orientation`() {
        setScreen()
        val before = capturedContent?.resetOrientationRequestId ?: 0
        touchDesc("Reset orientation to north")
        assertEquals("the map is handed a new reset request", before + 1, capturedContent?.resetOrientationRequestId)
    }

    @Test
    fun `FAILS AT BASE a real touch on the Layers row opens the Layers sheet`() {
        setScreen()
        // The wide map's own separate Layers button carries this description too (and passed at base), so the
        // row touched must be the bar's: inside the cluster.
        assertTrue("the cluster is on the map", exists(MAP_ICON_CLUSTER_TAG))
        val row = boundsDescPrefix("Layers:")
        assertTrue("the Layers control ($row) is a row of the cluster (${cluster()})", row.left >= cluster().left && row.right <= cluster().right && row.top >= cluster().top && row.bottom <= cluster().bottom)
        touch(row)
        assertTrue("the Layers sheet is open", exists(MAP_LAYERS_SHEET_TAG))
    }

    private fun boundsDescPrefix(prefix: String): DpRect {
        val nodes = composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription(prefix, substring = true)).fetchSemanticsNodes()
        assertTrue("a control whose description starts '$prefix' is on screen", nodes.isNotEmpty())
        val b = nodes.first().boundsInRoot
        return DpRect(left = b.left.dp, top = b.top.dp, right = b.right.dp, bottom = b.bottom.dp)
    }

    @Test
    fun `FAILS AT BASE a real touch on the plus row opens the plan-or-log chooser`() {
        setScreen()
        assertTrue("the cluster is on the map", exists(MAP_ICON_CLUSTER_TAG))
        val row = boundsDesc("Plan a trip or log a find here")
        assertTrue("the plus control ($row) is a row of the cluster (${cluster()}), not the separate button", row.left >= cluster().left && row.right <= cluster().right && row.top >= cluster().top && row.bottom <= cluster().bottom)
        touch(row)
        assertTrue("the chooser is open", composeRule.onAllNodesWithText("What would you like to do here?").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `FAILS AT BASE real touches on the record pill start and stop recording, and return is reachable while recording`() {
        setScreen()
        touchTag("control-pill-record")
        assertTrue("recording started", recording)
        touchTag("control-pill-return-to-vehicle")
        assertEquals("the return control took the touch", 1, toggleReturningCalls)
        touchTag("control-pill-record")
        assertEquals("recording stopped", false, recording)
    }

    // ── The map still gets what is beside the cluster ──

    @Test
    fun `FAILS AT BASE a real long-press on the map beside the cluster reaches the map, and a tap on it does too`() {
        setScreen()
        val pane = mapPane()
        val c = cluster()
        val y = (c.top.value + c.height.value / 2f)
        // Clear of the cluster and its handle on the inboard side, and above and below it.
        val points = listOf(
            (c.left.value - 60f) to y,
            ((pane.left.value + c.left.value) / 2f) to (c.bottom.value + 40f),
            ((pane.left.value + c.left.value) / 2f) to 400f,
        )
        val before = mapLongPresses
        for ((x, py) in points) {
            composeRule.onRoot().performTouchInput { longClick(Offset(x * density, py * density)) }
            composeRule.waitForIdle()
        }
        assertTrue("the cluster is there to be beside", exists(MAP_ICON_CLUSTER_TAG))
        assertEquals("all three long-presses beside the cluster reached the map", before + 3, mapLongPresses)
    }

    // ── Drag, snap, minimise ──

    @Test
    fun `FAILS AT BASE a long-press drag on the handle moves the cluster up and down within the map pane`() {
        setScreen()
        val pane = mapPane()
        val before = cluster()
        val handle = boundsDesc("Hide map controls")
        val start = Offset(((handle.left.value + handle.right.value) / 2f), ((handle.top.value + handle.bottom.value) / 2f))
        composeRule.onRoot().performTouchInput {
            down(Offset(start.x * density, start.y * density))
            advanceEventTime(700)
            moveBy(Offset(0f, -150f * density), 300)
            up()
        }
        composeRule.waitForIdle()
        val after = cluster()
        assertTrue("the cluster moved up by about 150 dp (was ${before.top}, now ${after.top})", (before.top - after.top).value in 100f..170f)
        assertTrue("and stays inside the pane ($after in $pane)", after.top >= pane.top && after.bottom <= pane.bottom)
    }

    @Test
    fun `FAILS AT BASE dragged far up the cluster stops below the compass strip and the chip row`() {
        setScreen(shownEntry = true)
        val handle = boundsDesc("Hide map controls")
        val start = Offset(((handle.left.value + handle.right.value) / 2f), ((handle.top.value + handle.bottom.value) / 2f))
        composeRule.onRoot().performTouchInput {
            down(Offset(start.x * density, start.y * density))
            advanceEventTime(700)
            moveBy(Offset(0f, -2000f * density), 600)
            up()
        }
        composeRule.waitForIdle()
        val chips = boundsTag(JOURNAL_ENTRIES_CHIP_TAG)
        val strip = boundsTag("compass-elevation-strip")
        val c = cluster()
        assertTrue("the cluster's top (${c.top}) is at or below the chip row's bottom (${chips.bottom})", c.top >= chips.bottom)
        assertTrue("and below the strip's bottom (${strip.bottom})", c.top >= strip.bottom)
    }

    @Test
    fun `FAILS AT BASE a long-press drag past the snap distance moves the cluster to the other side, and it stays there`() {
        setScreen()
        val pane = mapPane()
        val handle = boundsDesc("Hide map controls")
        val start = Offset(((handle.left.value + handle.right.value) / 2f), ((handle.top.value + handle.bottom.value) / 2f))
        composeRule.onRoot().performTouchInput {
            down(Offset(start.x * density, start.y * density))
            advanceEventTime(700)
            moveBy(Offset(-200f * density, 0f), 300)
            up()
        }
        composeRule.waitForIdle()
        val c = cluster()
        assertTrue("the cluster is at the pane's left edge now ($c in $pane)", c.left <= pane.left + 24.dp)
    }

    @Test
    fun `FAILS AT BASE the handle minimises the cluster and the restore handle brings it back where it was`() {
        setScreen()
        val before = cluster()
        touchDesc("Hide map controls")
        composeRule.mainClock.advanceTimeBy(1000)
        assertEquals("the cluster is gone", false, exists(MAP_ICON_CLUSTER_TAG))
        assertTrue("its restore handle is on screen", descExists("Show map controls"))
        touchDesc("Show map controls")
        composeRule.mainClock.advanceTimeBy(1000)
        assertTrue("the cluster is back", exists(MAP_ICON_CLUSTER_TAG))
        assertEquals("at the same place (remembered)", before.top.value, cluster().top.value, 2f)
    }

    // ── Fullscreen ──

    @Test
    fun `FAILS AT BASE fullscreen hides the Journal column and the search bar, the map fills the window, and exit restores both`() {
        setScreen()
        assertTrue("before: the drawer shows", drawerShowing())
        assertTrue("before: the search bar shows", searchBarShowing())
        val paneBefore = mapPane()

        touchDesc("Fullscreen")
        composeRule.mainClock.advanceTimeBy(1000)

        assertEquals("the drawer (the Journal column) is hidden", false, drawerShowing())
        assertEquals("the search bar is hidden", false, searchBarShowing())
        assertTrue("the map is wider than before ($paneBefore -> ${mapPane()})", mapPane().width > paneBefore.width)
        assertEquals("the write reached the persistence callback", listOf(true), fullscreenWrites)

        touchDesc("Exit fullscreen")
        composeRule.mainClock.advanceTimeBy(1000)

        assertTrue("after exit: the drawer is back", drawerShowing())
        assertTrue("after exit: the search bar is back", searchBarShowing())
        assertEquals(listOf(true, false), fullscreenWrites)
    }

    @Test
    fun `FAILS AT BASE Back leaves fullscreen and a tap on the map in fullscreen restores the chrome, as on the phone`() {
        setScreen()
        touchDesc("Fullscreen")
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertTrue("Back restored the drawer", drawerShowing())

        touchDesc("Fullscreen")
        composeRule.mainClock.advanceTimeBy(1000)
        val pane = mapPane()
        composeRule.onRoot().performTouchInput { click(Offset((pane.left.value + 60f) * density, (pane.top.value + pane.height.value / 2f) * density)) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1000)
        assertTrue("a tap on the map restored the drawer", drawerShowing())
    }

    // ── The strip and the chips ──

    @Test
    fun `FAILS AT BASE the compass strip runs across the top of the map pane with the chip row below it`() {
        setScreen(shownEntry = true)
        val pane = mapPane()
        val strip = boundsTag("compass-elevation-strip")
        val chips = boundsTag(JOURNAL_ENTRIES_CHIP_TAG)
        assertTrue("the strip spans the pane's width ($strip in $pane)", strip.left <= pane.left + 1.dp && strip.right >= pane.right - 1.dp)
        assertTrue("the strip is at the pane's top", strip.top <= pane.top + 1.dp)
        assertTrue("the chip row is below the strip (chips ${chips.top}, strip ${strip.bottom})", chips.top >= strip.bottom)
    }

    @Test
    fun `FAILS AT BASE while navigating the navigation HUD replaces the strip and the chip row stays clear of it`() {
        setScreen(shownEntry = true, navigating = true)
        assertEquals("the strip is not shown while navigating", false, exists("compass-elevation-strip"))
        assertTrue("the HUD is shown", exists(NAVIGATION_HUD_TAG))
        val hud = boundsTag(NAVIGATION_HUD_TAG)
        val chips = boundsTag(JOURNAL_ENTRIES_CHIP_TAG)
        assertTrue("the chip row (top ${chips.top}) is below the HUD (bottom ${hud.bottom})", chips.top >= hud.bottom)
    }

    // ── The map before a search still has the controls ──

    @Test
    fun `FAILS AT BASE the cluster is on the map with planned trips before any search`() {
        setScreen(withTrip = true)
        assertTrue("the cluster is on the tablet map", exists(MAP_ICON_CLUSTER_TAG))
    }

    protected companion object {
        const val MAP_SLOT = "map-slot"
        val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
        val SEARCHED = AvailabilityUiState(region = REGION)
        val TRIP = PlannedTrip(id = "trip-1", name = "Creek loop", location = LatLng(45.33, -122.63), date = LocalDate.of(2026, 10, 3))
        val ONE_SHOWN_ENTRY = CartographyUiState(
            entries = listOf(CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 1_000L).copy(isDraft = false, text = "A walk.", shownOnMap = true)),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-mdpi")
class WideMapControlsPortraitTest : WideMapControlsTests()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-mdpi")
class WideMapControlsLandscapeTest : WideMapControlsTests()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h900dp-mdpi")
class WideMapControls1280Test : WideMapControlsTests()
