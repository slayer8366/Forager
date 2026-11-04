package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.GroundHeightSource
import com.zynergylabs.forager.app.domain.GroundHeightUnknown
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.LocalGroundHeightSource
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import kotlinx.coroutines.CompletableDeferred
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

/**
 * Dispatch 2026-11-03-07: a waypoint's bubble shows the ground's height at its spot, "Elevation 1352 ft", below its
 * coordinates (A1), and no line where none is known (A2), on every map with bubbles (D4), snapshot waypoints too (D5).
 * Through the real entry point: a real touch on the waypoint's glyph on the Maps tab and on a journal entry's map, with
 * the heights handed in as the app hands them, [LocalGroundHeightSource].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class WaypointBubbleElevationTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        object : ExternalResource() {
            override fun before() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
            }
        },
    ).around(composeRule)

    /** Heights by spot; anything else is not downloaded. Records every point it is asked for. */
    private class SpotHeights(private val known: Map<LatLng, GroundHeight>, private val gate: CompletableDeferred<Unit>? = null) : GroundHeightSource {
        val asked = mutableListOf<LatLng>()

        override suspend fun groundHeightAt(point: LatLng): GroundHeight {
            asked += point
            gate?.await()
            return known[point] ?: GroundHeight.Unknown(GroundHeightUnknown.NOT_DOWNLOADED)
        }
    }

    private val inside = LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng)
    private val map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id, 60.dp, 380.dp, inside)))

    private fun units(system: UnitSystem) = object : UnitSystemPreferenceRepository {
        override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(system)
        override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
    }

    private fun setMapsTab(heights: GroundHeightSource, system: UnitSystem = UnitSystem.METRIC) {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store, unitSystemPreferences = units(system))
        composeRule.setContent {
            CompositionLocalProvider(LocalGroundHeightSource provides heights) {
                MapLayersTestScreen(viewModel = viewModel, mapSlot = map.slot, store = store, waypoints = listOf(BUBBLE_WAYPOINT))
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapWaypoint(id: String = BUBBLE_WAYPOINT.id) {
        composeRule.onNodeWithTag(glyphTag(id)).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun elevationLines() = composeRule.onAllNodesWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true)

    private fun inBubble(text: String) =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG)), useUnmergedTree = true)

    private fun mgrs(at: LatLng): String = (MgrsConverter.convert(at) as MgrsCoordinate.Grid).value

    @Test
    fun `inside a downloaded area the bubble shows the ground height in metres, below the coordinates`() {
        val heights = SpotHeights(mapOf(inside to GroundHeight.Known(412.4)))
        setMapsTab(heights)

        tapWaypoint()

        inBubble("Creek pin").assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).assertTextEquals("Elevation 412 m")
        val coordinates = inBubble(mgrs(inside)).getUnclippedBoundsInRoot()
        val elevation = composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the line sits below the coordinates: ${elevation.top} vs ${coordinates.bottom}", elevation.top >= coordinates.bottom)
        assertEquals("asked once, for the waypoint's own spot", listOf(inside), heights.asked)
    }

    @Test
    fun `in feet the height reads as a whole number with no separator`() {
        // 412.4 m is 1353.0 ft; 412.0 m is 1351.7 ft, rounding to 1352.
        setMapsTab(SpotHeights(mapOf(inside to GroundHeight.Known(412.0))), UnitSystem.IMPERIAL)

        tapWaypoint()

        composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).assertTextEquals("Elevation 1352 ft")
    }

    @Test
    fun `outside every downloaded area the bubble has no elevation line and is otherwise as before`() {
        setMapsTab(SpotHeights(emptyMap()))

        tapWaypoint()

        inBubble("Creek pin").assertIsDisplayed()
        inBubble(mgrs(inside)).assertIsDisplayed()
        elevationLines().fetchSemanticsNodes().let { assertEquals("no elevation line", 0, it.size) }
    }

    @Test
    fun `a tile stored with no height data gives no line`() {
        setMapsTab(SpotHeights(mapOf(inside to GroundHeight.Unknown(GroundHeightUnknown.NO_DATA))))

        tapWaypoint()

        inBubble("Creek pin").assertIsDisplayed()
        assertEquals(0, elevationLines().fetchSemanticsNodes().size)
    }

    @Test
    fun `the bubble shows at once, and the line joins it when the height is read`() {
        val gate = CompletableDeferred<Unit>()
        setMapsTab(SpotHeights(mapOf(inside to GroundHeight.Known(412.4)), gate))

        tapWaypoint()

        inBubble("Creek pin").assertIsDisplayed()
        assertEquals("no line while the height is read", 0, elevationLines().fetchSemanticsNodes().size)

        gate.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).assertTextEquals("Elevation 412 m")
    }

    @Test
    fun `on a journal entry's map a waypoint known only from the entry shows the line too`() {
        val gone = LatLng(45.4, -122.5)
        val entry = CartographyEntry.draft(id = "entry-1", date = BUBBLE_DAY, updatedAtEpochMillis = 1_000L).copy(
            isDraft = false,
            text = "A wet morning on the ridge.",
            waypointDecisions = listOf(WaypointDecision("wp-gone", "Old gate", gone.lat, gone.lng, kept = true)),
        )
        val mapData = CartographyEntryMapData(
            trackPolylines = emptyList(),
            findMarkers = emptyList(),
            waypointMarkers = listOf(RecordPoint("wp-gone", gone)),
            photoMarkers = emptyList(),
            offlineRegionCircles = emptyList(),
        )
        val entryMap = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, "wp-gone", 60.dp, 120.dp, gone)))
        val heights = SpotHeights(mapOf(gone to GroundHeight.Known(1000.0)))
        var log by mutableStateOf(MushroomLogUiState())
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent {
            CompositionLocalProvider(LocalGroundHeightSource provides heights) {
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = entryMap.slot,
                    store = store,
                    logUiState = log,
                    onCloseLogEntry = { log = log.copy(editingEntry = null) },
                    cartographyUiState = CartographyUiState(entries = listOf(entry), editingEntry = entry),
                    getCartographyEntryMapData = { _, _ -> mapData },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()

        tapWaypoint("wp-gone")

        inBubble("Old gate").assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).assertTextEquals("Elevation 1000 m")
        assertEquals(listOf(gone), heights.asked)
    }
}
