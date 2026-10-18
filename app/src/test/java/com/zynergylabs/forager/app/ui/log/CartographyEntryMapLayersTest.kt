package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.mapLayerSwitchTag
import com.zynergylabs.forager.app.ui.map.layers.LayerState
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Cartography entry map and the Layers sheet (map layers L0b, B1 and B3; owner's ruling 4, "Same
 * sheet"): one set of stored choices shared with the Maps tab, and a sheet that lists only the record
 * overlays this map draws. No colour field here (the planner's ruling on Q5): the entry map passes no
 * forecast store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyEntryMapLayersTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false, waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -122.5, kept = true)))

    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = listOf(RecordPoint("w1", LatLng(45.5, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private var renderMode: MapRenderMode? = null
    private var onTap: (() -> Unit)? = null
    private val slot: MapSlot = { _, _, mode, _, _, tap, _, _, _ ->
        renderMode = mode
        onTap = tap
    }

    private var shared by mutableStateOf(MapLayersState.DEFAULT)
    private val switches = mutableListOf<Pair<String, Boolean>>()

    private fun setScreen() {
        composeRule.setContent {
            CartographyEntryReportScreen(
                entry = entry,
                galleryPhotos = emptyList(),
                distanceUnit = DistanceUnit.MILES,
                mapSlot = slot,
                night = false,
                getMapData = { _, _ -> mapData },
                getCoveringOfflineRegion = { _, _ -> null },
                getCurrentLocation = { LocationResult.LocationUnavailable },
                onEdit = {},
                onDeleteEntry = {},
                onBack = {},
                layersState = shared,
                onLayerVisibilityChanged = { id, visible ->
                    switches += id to visible
                    shared = shared.copy(layers = shared.layers + (id to shared.stateOf(id).copy(visible = visible)))
                },
            )
        }
        composeRule.waitForIdle()
    }

    private fun enterFullscreen() {
        onTap?.invoke() ?: error("the map section never resolved")
        composeRule.waitForIdle()
    }

    @Test
    fun `the entry map draws with the shared layer choices, and with no forecast store`() {
        shared = MapLayersState(layers = mapOf(MapLayerIds.FINDS to LayerState(visible = false)))
        setScreen()

        assertEquals(false, renderMode?.layers?.stateOf(MapLayerIds.FINDS)?.visible)
        assertNull(renderMode?.forecast)
    }

    @Test
    fun `its sheet lists only the five record overlays this map draws, and a switch there goes to the shared choices`() {
        setScreen()
        enterFullscreen()

        composeRule.onNode(hasContentDescription("Layers: Street map. Choose the map type and overlays.")).performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
        listOf("Finds", "Photos", "Waypoints", "Tracks", "Offline maps").forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        listOf("Planned trips", "Recording trail").forEach { assertEquals(it, 0, composeRule.onAllNodesWithText(it).fetchSemanticsNodes().size) }

        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.WAYPOINTS)).performScrollTo().performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(listOf(MapLayerIds.WAYPOINTS to false), switches)
        assertEquals(false, renderMode?.layers?.stateOf(MapLayerIds.WAYPOINTS)?.visible)
    }
}
