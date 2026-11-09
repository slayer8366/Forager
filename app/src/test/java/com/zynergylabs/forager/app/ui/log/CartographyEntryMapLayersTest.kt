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
import com.zynergylabs.forager.app.ui.map.MAP_TYPE_PANEL_TAG
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.mapModeChipTag
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import androidx.compose.ui.test.onAllNodesWithTag
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
 * The Cartography entry map's layers (map layers L0b, B1 and B3; owner's ruling 4): one set of stored choices shared
 * with the Maps tab, switched in Tools > Map Layers since dispatch 2026-10-28-02, and a Layers button that opens only the
 * map type panel. No colour field here (the planner's ruling on Q5): the entry map passes no forecast store.
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
    fun `its Layers button opens the map type panel and no switches, and a choice there changes this map's type`() {
        setScreen()
        enterFullscreen()

        composeRule.onNode(hasContentDescription("Layers: Trail map. Choose the map type.")).performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MAP_TYPE_PANEL_TAG).assertIsDisplayed()
        listOf("Finds", "Photos", "Waypoints", "Tracks", "Offline Maps", "Trails").forEach { assertEquals(it, 0, composeRule.onAllNodesWithText(it).fetchSemanticsNodes().size) }

        composeRule.onNodeWithTag(mapModeChipTag(MapMode.TOPOGRAPHIC)).performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(MapMode.TOPOGRAPHIC.basemap, renderMode?.basemap)
        assertEquals("one tap closes it", 0, composeRule.onAllNodesWithTag(MAP_TYPE_PANEL_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `a change to the shared choices, as Tools makes it, changes what this map draws`() {
        setScreen()
        assertEquals(true, renderMode?.layers?.stateOf(MapLayerIds.WAYPOINTS)?.visible)

        shared = shared.copy(layers = shared.layers + (MapLayerIds.WAYPOINTS to shared.stateOf(MapLayerIds.WAYPOINTS).copy(visible = false)))
        composeRule.waitForIdle()

        assertEquals(false, renderMode?.layers?.stateOf(MapLayerIds.WAYPOINTS)?.visible)
    }
}
