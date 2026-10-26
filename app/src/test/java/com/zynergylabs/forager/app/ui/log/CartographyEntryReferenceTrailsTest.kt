package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
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
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Step 8b on the Cartography entry map (dispatch 2026-10-26-15 Amendment 2; the owner, RECORD -1682: "Yes, both maps"):
 * its Layers sheet offers "Official trails" only when its own map reports that the loaded style draws the band, and the
 * switch goes to the shared choices like the entry map's other switches. The slot stands in for `SightingsMap`'s report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyEntryReferenceTrailsTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    // As CartographyEntryMapLayersTest's: an entry with one kept waypoint, so its map section has a place to show.
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
    private var shared by mutableStateOf(MapLayersState.DEFAULT)
    private val switches = mutableListOf<Pair<String, Boolean>>()

    private fun slot(drawsBand: Boolean): MapSlot = { _, _, mode, _, _, tap, _, _, _ ->
        renderMode = mode
        onTap = tap
        LaunchedEffect(Unit) { mode.onReferenceTrailsShown(drawsBand) }
    }

    private fun openSheet(drawsBand: Boolean) {
        composeRule.setContent {
            CartographyEntryReportScreen(
                entry = entry,
                galleryPhotos = emptyList(),
                distanceUnit = DistanceUnit.MILES,
                mapSlot = slot(drawsBand),
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
        onTap?.invoke() ?: error("the map section never resolved")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Layers: Trail map. Choose the map type and overlays.")).performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun `on today's style the entry map's sheet has no Official trails row`() {
        openSheet(drawsBand = false)
        assertEquals(0, composeRule.onAllNodesWithText("Official trails").fetchSemanticsNodes().size)
    }

    @Test
    fun `with the band loaded the row shows, and a real touch on it goes to the shared choices and the map`() {
        openSheet(drawsBand = true)
        composeRule.onNodeWithText("Official trails").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Forest Service and BLM").assertIsDisplayed()

        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.REFERENCE_FS_BAND)).performScrollTo().performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(listOf(MapLayerIds.REFERENCE_FS_BAND to false), switches)
        assertEquals(false, renderMode?.layers?.stateOf(MapLayerIds.REFERENCE_FS_BAND)?.visible)
    }
}
