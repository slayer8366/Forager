package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.ForecastAvailability
import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellsResult
import com.zynergylabs.forager.app.domain.ForecastDriver
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.ui.log.formatTrackDuration
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import android.view.KeyEvent
import androidx.compose.ui.test.assertTextEquals
import org.robolectric.shadows.ShadowDialog
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.FIND_OVER_VIEW_TAG
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.PHOTO_VIEWER_TAG
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_SHEET_TAG
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_TITLE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_CHANCE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_CLOSE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_OPEN_FIND_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_STALE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_VIEW_PHOTO_TAG
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_REFERENCE_CLASS
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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

private typealias BubblesRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** One glyph the stub map draws: a 24 dp square whose centre is at ([x], [y]) in the map slot. */
internal data class StubGlyph(val layerId: String, val featureId: String, val x: Dp, val y: Dp, val at: LatLng)

/**
 * A map slot for the bubble tests (M1): it draws each [StubGlyph] as a 24 dp square that, on a real
 * touch, reports a feature tap through `renderMode.onFeatureTap` exactly as `SightingsMap` does,
 * at the glyph's centre; it counts every plain tap that reaches the map (as
 * `LayersRecordingMapSlot` does) and fires the slot's `onTap`; and [simulateCameraIdle] stands in for
 * a pan settling, re-reporting the focused point feature (`content.focusedFeature`) moved by
 * [idleShift], the same re-projection `SightingsMap` does on camera idle.
 */
internal class BubbleMapSlot(private val glyphs: List<StubGlyph>) {
    var renderMode: MapRenderMode? = null
    var content: MapOverlayContent? = null
    var taps = 0
    var featureTaps = 0
    private var idleRequests by mutableIntStateOf(0)
    var idleShift: Offset = Offset(0f, 0f)

    fun simulateCameraIdle(shift: Offset) {
        idleShift = shift
        idleRequests++
    }

    val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        this.renderMode = renderMode
        this.content = content
        val density = LocalDensity.current
        val requests = idleRequests
        LaunchedEffect(requests) {
            if (requests == 0) return@LaunchedEffect
            val focus = content.focusedFeature ?: return@LaunchedEffect
            val glyph = glyphs.first { it.layerId == focus.layerId && it.featureId == focus.featureId }
            val centre = with(density) { Offset(glyph.x.toPx(), glyph.y.toPx()) }
            renderMode.onFeatureTap(MapFeatureTap(glyph.layerId, glyph.featureId, centre + idleShift, 0f, glyph.at))
        }
        Box(
            modifier
                .testTag("map-slot")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    taps++
                    onTap()
                },
        ) {
            // A planned-trip glyph is drawn only for a trip the screen handed the map (`content.plannedTrips`), as the
            // real planned-trips source holds only what it is given; every other kind is drawn from the fixed list.
            glyphs.filter { it.layerId != MapLayerIds.PLANNED_TRIPS || content.plannedTrips.any { trip -> trip.id == it.featureId } }.forEach { glyph ->
                Box(
                    Modifier
                        .offset(glyph.x - 12.dp, glyph.y - 12.dp)
                        .size(24.dp)
                        .testTag(glyphTag(glyph.featureId))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            featureTaps++
                            val centre = with(density) { Offset(glyph.x.toPx(), glyph.y.toPx()) }
                            renderMode.onFeatureTap(MapFeatureTap(glyph.layerId, glyph.featureId, centre, 0f, glyph.at))
                        },
                )
            }
        }
    }
}

internal fun glyphTag(featureId: String) = "stub-glyph-$featureId"

private fun hostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

// The records every test draws.

internal val BUBBLE_DAY: LocalDate = LocalDate.of(2026, 9, 12)
internal val BUBBLE_FIND: MushroomLogEntry = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.51, -122.61), date = BUBBLE_DAY)
    .copy(isDraft = false, ownIdentification = "Golden chanterelle")
internal val BUBBLE_PHOTO = GalleryPhoto(LogPhoto("ph-1", "photos/ph-1.jpg", 1_700_000_000_000L, 45.53, -122.63), referencingEntryIds = listOf("find-1"))
internal val BUBBLE_WAYPOINT = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)
internal val BUBBLE_TRIP = PlannedTrip("trip-1", "Saddle loop", LatLng(45.56, -122.66), LocalDate.of(2026, 10, 3))
internal val BUBBLE_TRACK = Track(
    id = "trk-1",
    name = "Morning loop",
    startedAtEpochMillis = 1_700_000_000_000L,
    endedAtEpochMillis = 1_700_003_600_000L,
    points = listOf(TrackPoint(45.50, -122.60, null, 5f, 1_700_000_000_000L), TrackPoint(45.51, -122.60, null, 5f, 1_700_003_600_000L)),
)
internal val BUBBLE_REGION = OfflineRegionSummary(
    7L, "Forest Park", Region(45.57, -122.67, 5), 0.0, 15.0, 1200, 12_300_000L,
    createdAtEpochMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90),
)
internal val BUBBLE_CELL = ForecastCell(
    group = "chanterelles",
    week = MAP_LAYERS_TEST_WEEK,
    centre = LatLng(45.5, -122.6),
    chance = 0.37,
    uncertaintyLow = 0.21,
    uncertaintyHigh = 0.58,
    applicable = true,
    drivers = listOf(ForecastDriver("rain, last 14 days", "62 mm")),
    weatherThrough = LocalDate.of(2026, 9, 26),
    modelVersion = "synthetic-1",
)

/** A store with both groups available and one stored chanterelle cell; it counts the cell reads. */
internal class OneCellStore : ForecastCellStore {
    var reads = 0
    override suspend fun availability(week: LocalDate) = ForecastAvailability.Groups(BOTH_FORECAST_GROUPS)
    override suspend fun cells(group: String, week: LocalDate, blocks: Set<ForecastBlock>): ForecastCellsResult {
        reads++
        val cells = if (group == BUBBLE_CELL.group && ForecastBlock.containing(BUBBLE_CELL.centre) in blocks) listOf(BUBBLE_CELL) else emptyList()
        return ForecastCellsResult.Cells(cells, rejectedCount = 0)
    }
}

/** Every kind at its own spot, clear of the chrome: the left half of the map, below the compass strip. */
private fun glyphsAt(xDp: Dp, firstYDp: Dp): List<StubGlyph> = listOf(
    StubGlyph(MapLayerIds.WAYPOINTS, "wp-1", xDp, firstYDp, LatLng(45.326, -122.634)),
    StubGlyph(MapLayerIds.FINDS, "find-1", xDp, firstYDp + 40.dp, LatLng(45.51, -122.61)),
    StubGlyph(MapLayerIds.PHOTOS, "ph-1", xDp, firstYDp + 80.dp, LatLng(45.53, -122.63)),
    StubGlyph(MapLayerIds.KEPT_TRACKS, "trk-1", xDp, firstYDp + 120.dp, LatLng(45.505, -122.6)),
    StubGlyph(MapLayerIds.PLANNED_TRIPS, "trip-1", xDp, firstYDp + 160.dp, LatLng(45.56, -122.66)),
    StubGlyph(MapLayerIds.OFFLINE_REGION_OUTLINE, "7", xDp, firstYDp + 200.dp, LatLng(45.6, -122.67)),
    StubGlyph(MapLayerIds.FORECAST_CHANTERELLES, "45.5,-122.6", xDp + 60.dp, firstYDp + 200.dp, LatLng(45.47, -122.62)),
)

private fun BubblesRule.touchAt(xDp: Dp, yDp: Dp) {
    val at = with(density) { Offset(xDp.toPx(), yDp.toPx()) }
    onRoot().performTouchInput { click(at) }
    waitForIdle()
}

private fun BubblesRule.touchCentreOf(tag: String) {
    onNodeWithTag(tag).performTouchInput { click(center) }
    waitForIdle()
}

private fun BubblesRule.bubbleBounds(tag: String = MAP_BUBBLE_TAG): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

private fun BubblesRule.back() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

private fun BubblesRule.registerGeoHandler() {
    val componentName = ComponentName(activity, "com.example.fakemaps.MapsActivity")
    val shadowPackageManager = Shadows.shadowOf(activity.packageManager)
    shadowPackageManager.addActivityIfNotPresent(componentName)
    shadowPackageManager.addIntentFilterForActivity(
        componentName,
        android.content.IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("geo")
        },
    )
}

/** Touches at several points across [tag]'s own bounds, on parts that are not a button: its title row and its padding. */
private fun BubblesRule.touchAcrossBubbleText(titleText: String) {
    val title = onNodeWithText(titleText, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val card = bubbleBounds()
    val midY = (title.top + title.bottom) / 2
    listOf(
        title.left + 2.dp to midY,
        (title.left + title.right) / 2 to midY,
        title.right - 2.dp to midY,
        card.left + 4.dp to card.top + 4.dp,
        card.left + 4.dp to (card.top + card.bottom) / 2,
        card.left + 4.dp to card.bottom - 4.dp,
    ).forEach { (x, y) -> touchAt(x, y) }
}

/**
 * M1's bubbles on the compact Maps tab, portrait, through the real screen and the real
 * [AvailabilityViewModel], at the S22 Ultra's portrait size. Every touch is a real one at screen
 * coordinates (CLAUDE.md, Testing), and a glyph tap goes through the stub slot's
 * `renderMode.onFeatureTap`, as `SightingsMap`'s does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenMapBubblesTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(glyphsAt(60.dp, 380.dp))
    private val store = OneCellStore()
    private var log by mutableStateOf(MushroomLogUiState(entries = listOf(BUBBLE_FIND), galleryPhotos = listOf(BUBBLE_PHOTO)))

    // Dispatch -616 changed this: `waypoints` added, the one bubble waypoint by default, as before.
    private fun setScreen(waypoints: List<Waypoint> = listOf(BUBBLE_WAYPOINT)) {
        val viewModel = mapLayersViewModel(store = store, plannedTrips = listOf(BUBBLE_TRIP), offlineRegions = listOf(BUBBLE_REGION))
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = store,
                logUiState = log,
                waypoints = waypoints,
                tracks = listOf(BUBBLE_TRACK),
                onOpenLogEntry = { id -> log = log.copy(editingEntry = log.entries.firstOrNull { it.id == id }) },
                onCloseLogEntry = { log = log.copy(editingEntry = null) },
            )
        }
        composeRule.waitForIdle()
    }

    private fun tapGlyph(id: String) = composeRule.touchCentreOf(glyphTag(id))

    @Test
    fun `a real touch on a waypoint glyph shows its bubble with its name and MGRS, and is not a plain map tap`() {
        setScreen()
        val before = map.taps

        tapGlyph("wp-1")

        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Creek pin", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("a glyph tap is a feature tap and nothing else (Bubble only)", before, map.taps)
        assertEquals(1, map.featureTaps)
    }

    @Test
    fun `real touches across the bubble do not reach the map, and the bubble stays`() {
        setScreen()
        tapGlyph("wp-1")
        val before = map.taps

        composeRule.touchAcrossBubbleText("Creek pin")

        assertEquals("none of the six touches on the bubble reached the map", before, map.taps)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
    }

    @Test
    fun `a touch on empty map dismisses the bubble`() {
        setScreen()
        tapGlyph("wp-1")
        val before = map.taps

        composeRule.touchAt(200.dp, 700.dp)

        assertEquals(before + 1, map.taps)
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).assertCountEqualsZero()
    }

    @Test
    fun `the close button and Back each dismiss the bubble`() {
        setScreen()
        tapGlyph("wp-1")
        composeRule.touchCentreOf(MAP_BUBBLE_CLOSE_TAG)
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).assertCountEqualsZero()

        tapGlyph("wp-1")
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.back()
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).assertCountEqualsZero()
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
    }

    @Test
    fun `one bubble at a time - a second glyph replaces the first`() {
        setScreen()
        tapGlyph("wp-1")
        tapGlyph("trk-1")

        assertEquals(1, composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Morning loop", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Creek pin", useUnmergedTree = true).assertCountEqualsZero()
    }

    @Test
    fun `a waypoint's Details opens the J5c sheet on the Maps tab, and closes the bubble`() {
        setScreen()
        tapGlyph("wp-1")

        composeRule.touchCentreOf(MAP_BUBBLE_DETAILS_TAG)

        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RECORD_DETAILS_TITLE_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText("Creek pin").fetchSemanticsNodes().isNotEmpty().let { assertTrue("the sheet names the waypoint", it) }
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).assertCountEqualsZero()
    }

    @Test
    fun `a waypoint's Directions hands the waypoint to a navigation app`() {
        setScreen()
        composeRule.registerGeoHandler()
        tapGlyph("wp-1")

        composeRule.touchCentreOf(MAP_BUBBLE_DIRECTIONS_TAG)

        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("geo:0,0?q=45.326,-122.634(Creek%20pin)", started?.data.toString())
    }

    @Test
    fun `a planned trip's bubble shows its row's lines and its Directions`() {
        setScreen()
        composeRule.registerGeoHandler()
        tapGlyph("trip-1")

        composeRule.onNodeWithText("Saddle loop", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Oct 3, 2026", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(MAP_BUBBLE_DETAILS_TAG).assertCountEqualsZero()
        composeRule.touchCentreOf(MAP_BUBBLE_DIRECTIONS_TAG)

        assertEquals("geo:0,0?q=45.56,-122.66(Saddle%20loop)", Shadows.shadowOf(composeRule.activity).nextStartedActivity?.data.toString())
    }

    @Test
    fun `a track's bubble shows its distance and duration, and its Details opens the sheet`() {
        setScreen()
        tapGlyph("trk-1")
        composeRule.onNodeWithText("1h 0m", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()
        assertEquals("the sheet is the track's", 1, composeRule.onAllNodesWithText("Morning loop").fetchSemanticsNodes().size)
    }

    /**
     * Dispatch -616 (plan T10), amended by -618: the track sheet opened from a bubble lists the waypoints
     * dropped on the walk; a real touch on one opens its details, and a real Back on that sheet's own
     * window returns to the walk's details, not to the map.
     */
    @Test
    fun `from a track's Details, a waypoint dropped on the walk opens its own details, and Back returns to the walk's`() {
        val dropped = Waypoint("wp-walk", 45.505, -122.60, null, "Bolete log", "", 1_700_001_000_000L, trackId = "trk-1")
        setScreen(waypoints = listOf(BUBBLE_WAYPOINT, dropped))
        tapGlyph("trk-1")
        composeRule.touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        composeRule.onNodeWithTag(RECORD_DETAILS_TITLE_TAG).assertTextEquals("Morning loop")

        composeRule.onNodeWithTag("record-details-walk-waypoint-wp-walk").performScrollTo()
        composeRule.touchCentreOf("record-details-walk-waypoint-wp-walk")
        composeRule.onNodeWithTag(RECORD_DETAILS_TITLE_TAG).assertTextEquals("Bolete log")

        val dialog = ShadowDialog.getLatestDialog()
        composeRule.runOnUiThread {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RECORD_DETAILS_TITLE_TAG).assertTextEquals("Morning loop")
    }

    @Test
    fun `an offline region's bubble says Stale when stale, and its Details opens the sheet`() {
        setScreen()
        tapGlyph("7")
        composeRule.onNodeWithTag(MAP_BUBBLE_STALE_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("5 km · 12.3 MB", useUnmergedTree = true).assertIsDisplayed()
        composeRule.touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Forest Park").assertIsDisplayed()
    }

    @Test
    fun `a photo's bubble says what it is attached to, and View photo opens the viewer in place`() {
        setScreen()
        tapGlyph("ph-1")
        composeRule.onNodeWithText("In Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()

        composeRule.touchCentreOf(MAP_BUBBLE_VIEW_PHOTO_TAG)

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).assertCountEqualsZero()
        composeRule.onNodeWithText("Maps").assertExists()
    }

    @Test
    fun `a forecast cell's bubble shows the stored cell's numbers beside its layer name, dates and reference class`() {
        setScreen()
        val readsBefore = store.reads

        tapGlyph("45.5,-122.6")

        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Test forecast: chanterelles (synthetic data)", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_BUBBLE_CHANCE_TAG, useUnmergedTree = true).assertTextIs("37%")
        composeRule.onNodeWithText("Uncertainty 21% to 58%", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("rain, last 14 days: 62 mm", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Week of Sep 28, 2026, weather to Sep 26, 2026", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(LEGEND_REFERENCE_CLASS, useUnmergedTree = true).assertExists()
        assertTrue("the cell was re-read from the store (${store.reads} after $readsBefore)", store.reads > readsBefore)
    }

    @Test
    fun `a point bubble follows its glyph when the camera settles, and a dismissed one does not come back`() {
        setScreen()
        tapGlyph("wp-1")
        val first = composeRule.bubbleBounds()

        val shift = with(composeRule.density) { Offset(0f, 120.dp.toPx()) }
        map.simulateCameraIdle(shift)
        composeRule.waitForIdle()
        val moved = composeRule.bubbleBounds()
        assertTrue("the bubble moved down with its glyph (${first.top} to ${moved.top})", abs((moved.top - first.top).value - 120f) < 2f)

        composeRule.touchCentreOf(MAP_BUBBLE_CLOSE_TAG)
        map.simulateCameraIdle(shift * 2f)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).assertCountEqualsZero()
    }

    @Test
    // Re-based 2026-09-30 (dispatch 2026-09-29-57, item 8, the owner's amendment -256): this test asserted that Back from a find
    // opened with "Open in Journal" lands on the Journal, which the owner has said is wrong ("it should return you to the
    // map"). Back now arrives on the Maps tab; the claim about the Journal, that it keeps its saved chip, is now checked when the
    // user next goes there. The new behaviour's own tests are AvailabilityScreenReturnToMapTest.
    fun `Open in Journal opens the find in its report over the Journal, Back returns to the Maps tab, and the Journal keeps its saved chip`() {
        setScreen()
        // The user's own Journal state first: Records, with the Waypoints chip.
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.touchCentreOf("journal-switch-records")
        composeRule.onNodeWithTag("records-chip-waypoints").performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("records-chip-waypoints").assertIsSelected()
        composeRule.onNodeWithText("Maps").performTouchInput { click(center) }
        composeRule.waitForIdle()

        tapGlyph("find-1")
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()
        composeRule.touchCentreOf(MAP_BUBBLE_OPEN_FIND_TAG)

        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertIsDisplayed()
        assertEquals("the find is open", "find-1", log.editingEntry?.id)
        composeRule.onNodeWithText("Your own identification: Golden chanterelle").assertIsDisplayed()

        composeRule.back()

        composeRule.onAllNodesWithTag(FIND_OVER_VIEW_TAG).assertCountEqualsZero()
        assertEquals(null, log.editingEntry)
        // This fixture's ViewModel loads no map records, so the find is not drawn and no bubble reopens (that case is logged); the
        // bubble coming back is asserted with the records loaded, in AvailabilityScreenReturnToMapTest.
        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("records-chip-waypoints").assertIsSelected()
    }
}

/** The bubble in the dispatch's short landscape window (`w823dp-h384dp-land`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class AvailabilityScreenMapBubblesShortLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(glyphsAt(420.dp, 150.dp))

    private fun setScreen() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent {
            MapLayersTestScreen(viewModel, map.slot, store, waypoints = listOf(BUBBLE_WAYPOINT), tracks = listOf(BUBBLE_TRACK))
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `in a short landscape window touches across the bubble stay on it, and a touch on empty map dismisses it`() {
        setScreen()
        composeRule.touchCentreOf(glyphTag("wp-1"))
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        val before = map.taps

        composeRule.touchAcrossBubbleText("Creek pin")
        assertEquals("none of the touches on the bubble reached the map", before, map.taps)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()

        composeRule.touchAt(600.dp, 330.dp)
        assertEquals(before + 1, map.taps)
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).assertCountEqualsZero()
    }
}

/**
 * The entry map's bubbles (owner: "Yes, same bubbles") through the compact Journal tab with a day
 * entry open, and "Open find" (owner, Q4: "Open find, Back returns"): the find opens over the entry,
 * and Back returns to the entry exactly as it was, here still in its fullscreen map.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CartographyEntryMapBubblesTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(glyphsAt(60.dp, 380.dp))
    private var log by mutableStateOf(MushroomLogUiState(entries = listOf(BUBBLE_FIND), galleryPhotos = listOf(BUBBLE_PHOTO)))

    private val entry = CartographyEntry.draft(id = "entry-1", date = BUBBLE_DAY, updatedAtEpochMillis = 1_000L).copy(
        isDraft = false,
        text = "A wet morning on the ridge.",
        findDecisions = listOf(FindDecision("find-1", BUBBLE_DAY, "Golden chanterelle", hasPhotos = false, kept = true)),
        // A kept waypoint no longer in Records: the entry map still names it from the snapshot.
        waypointDecisions = listOf(WaypointDecision("wp-gone", "Old gate", 45.4, -122.5, kept = true)),
    )
    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = listOf(RecordPoint("find-1", LatLng(45.51, -122.61))),
        waypointMarkers = listOf(RecordPoint("wp-gone", LatLng(45.4, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private fun setScreen(glyphs: BubbleMapSlot = map, entry: CartographyEntry = this.entry) {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = glyphs.slot,
                store = store,
                logUiState = log,
                onOpenLogEntry = { id -> log = log.copy(editingEntry = log.entries.firstOrNull { it.id == id }) },
                onCloseLogEntry = { log = log.copy(editingEntry = null) },
                cartographyUiState = CartographyUiState(entries = listOf(entry), editingEntry = entry),
                getCartographyEntryMapData = { _, _ -> mapData },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    @Test
    fun `on the entry map a kept waypoint's bubble names it from the entry, with no details for a waypoint gone from Records`() {
        val entryMap = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, "wp-gone", 60.dp, 120.dp, LatLng(45.4, -122.5))))
        setScreen(entryMap)
        composeRule.onNodeWithText("A wet morning on the ridge.").assertExists()

        composeRule.touchCentreOf(glyphTag("wp-gone"))

        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNode(
            androidx.compose.ui.test.hasText("Old gate") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(MAP_BUBBLE_TAG)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onAllNodesWithTag(MAP_BUBBLE_DETAILS_TAG).assertCountEqualsZero()
        composeRule.onNodeWithTag(MAP_BUBBLE_DIRECTIONS_TAG).assertExists()
    }

    // F3 (dispatch 2026-09-28-195, item 5): the bubble for a tapped track line, mirroring the waypoint case
    // above. The entry kept a track that has since left Records; its line is drawn from the saved path, and a
    // tap on it must still name the track from the entry's own snapshot, with no Details (there is no record).
    @Test
    fun `on the entry map a kept track's bubble names it from the entry, with no details, for a track gone from Records`() {
        val keptTrack = TrackDecision("trk-gone", "Old ridge", 1234.0, 3_660_000L, 5, kept = true)
        val withTrack = entry.copy(trackDecisions = listOf(keptTrack))
        val entryMap = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.KEPT_TRACKS, "trk-gone", 60.dp, 120.dp, LatLng(45.4, -122.5))))
        setScreen(entryMap, withTrack)
        composeRule.onNodeWithText("A wet morning on the ridge.").assertExists()

        composeRule.touchCentreOf(glyphTag("trk-gone"))

        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNode(
            androidx.compose.ui.test.hasText("Old ridge") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(MAP_BUBBLE_TAG)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNode(
            androidx.compose.ui.test.hasText(formatTrackDuration(3_660_000L), substring = true) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(MAP_BUBBLE_TAG)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onAllNodesWithTag(MAP_BUBBLE_DETAILS_TAG).assertCountEqualsZero()
    }

    @Test
    fun `Open find opens the find over the day entry, and Back returns to the entry as it was, still fullscreen`() {
        setScreen()
        // Fullscreen first, by a real touch on the entry's map (a plain tap enters it).
        val slot = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot()
        composeRule.touchAt(slot.right - 20.dp, slot.bottom - 20.dp)
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertExists()

        composeRule.touchCentreOf(glyphTag("find-1"))
        composeRule.onNodeWithText(com.zynergylabs.forager.app.ui.map.OPEN_FIND_LABEL, useUnmergedTree = true).assertIsDisplayed()
        composeRule.touchCentreOf(MAP_BUBBLE_OPEN_FIND_TAG)

        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertIsDisplayed()
        assertEquals("find-1", log.editingEntry?.id)

        composeRule.back()

        composeRule.onAllNodesWithTag(FIND_OVER_VIEW_TAG).assertCountEqualsZero()
        assertEquals(null, log.editingEntry)
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertExists()
        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
    }
}

/**
 * The entry map's opening frame (owner, 2026-09-28, "Fit all kept records"; planner message
 * 2026-09-28-35), through the compact Journal tab with a day entry open: the map slot receives one
 * camera request, framed on the kept find and waypoint, and no new request after the entry map goes
 * fullscreen, a bubble opens, and an M1 "Open find" overlay opens and closes, because Back returns
 * "exactly as it was" (M1's Q4). The slot is recomposed along the way (the count grows), so the
 * absence of a new request is observed on recompositions that could have carried one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CartographyEntryMapOpeningFrameTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.FINDS, "find-1", 60.dp, 120.dp, LatLng(45.51, -122.61))))
    private val received = mutableListOf<com.zynergylabs.forager.app.ui.map.MapCameraRequest?>()
    private val recordingSlot: MapSlot = { region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier ->
        received += renderMode.cameraRequest
        map.slot(region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier)
    }
    private var log by mutableStateOf(MushroomLogUiState(entries = listOf(BUBBLE_FIND), galleryPhotos = listOf(BUBBLE_PHOTO)))

    private val entry = CartographyEntry.draft(id = "entry-1", date = BUBBLE_DAY, updatedAtEpochMillis = 1_000L).copy(
        isDraft = false,
        text = "A wet morning on the ridge.",
        findDecisions = listOf(FindDecision("find-1", BUBBLE_DAY, "Golden chanterelle", hasPhotos = false, kept = true)),
        waypointDecisions = listOf(WaypointDecision("wp-gone", "Old gate", 45.4, -122.5, kept = true)),
    )
    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = listOf(RecordPoint("find-1", LatLng(45.51, -122.61))),
        waypointMarkers = listOf(RecordPoint("wp-gone", LatLng(45.4, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private fun setScreen() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = recordingSlot,
                store = store,
                logUiState = log,
                onOpenLogEntry = { id -> log = log.copy(editingEntry = log.entries.firstOrNull { it.id == id }) },
                onCloseLogEntry = { log = log.copy(editingEntry = null) },
                cartographyUiState = CartographyUiState(entries = listOf(entry), editingEntry = entry),
                getCartographyEntryMapData = { _, _ -> mapData },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the entry map is asked once to open on its kept records, and not again after fullscreen, a bubble and Open find with Back`() {
        setScreen()
        val requests = received.filterNotNull().distinct()
        assertEquals("one request on open", 1, requests.size)
        val request = requests.single()
        assertEquals(
            com.zynergylabs.forager.app.domain.EntryMapFrame.Fit(
                com.zynergylabs.forager.app.domain.model.GeoBoundingBox(north = 45.51, south = 45.4, east = -122.5, west = -122.61),
                paddingDp = 48,
                maxZoom = 17.0,
            ),
            request.frame,
        )
        val compositionsOnOpen = received.size

        val slot = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot()
        composeRule.touchAt(slot.right - 20.dp, slot.bottom - 20.dp)
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertExists()
        composeRule.touchCentreOf(glyphTag("find-1"))
        composeRule.touchCentreOf(MAP_BUBBLE_OPEN_FIND_TAG)
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertIsDisplayed()
        composeRule.back()
        composeRule.onAllNodesWithTag(FIND_OVER_VIEW_TAG).assertCountEqualsZero()
        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()

        assertTrue("the slot was recomposed after opening ($compositionsOnOpen, now ${received.size})", received.size > compositionsOnOpen)
        assertEquals("no new request after the round trip", listOf(request), received.filterNotNull().distinct())
        assertEquals("the slot still holds the same request", request, received.last())
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsZero() {
    assertEquals(0, fetchSemanticsNodes().size)
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextIs(expected: String) {
    val text = fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
    assertEquals(expected, text)
}
