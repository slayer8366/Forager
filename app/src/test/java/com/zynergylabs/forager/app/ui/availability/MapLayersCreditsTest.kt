package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.ui.map.ADDED_MAP_TYPES
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
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
 * D24 (dispatch 2026-10-30-08, RECORD -1966): the map's credits, off the map, in Tools > Map Layers. The owner: "Tools > Map
 * Layers (Recommended)", whose option read "After the Trails row: Trail, Topo, OSM, each with its credit underneath (e.g. Trail:
 * 'Protomaps © OpenStreetMap'). The trail credit goes under the Trails row. No heading". Through the real [AvailabilityScreen]
 * and its real ViewModel, opened by real touches. Every credit is written out here, not read from a constant, so a changed word
 * fails: they are today's caption wording, per map (Basemap.kt, MapLayers.kt:366, ColourFields.kt:28).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapLayersCreditsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val map = LayersRecordingMapSlot()

    private fun setScreen(store: ForecastCellStore = AbsentForecastCellStore) {
        val viewModel = mapLayersViewModel(layerPreferences = InMemoryLayerPreferences(MapLayerPreferences.NONE), store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel, map.slot, store) }
        composeRule.waitForIdle()
        composeRule.openToolsMapLayers()
    }

    private fun inDrawer(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TOOLS_DRAWER_SHEET_TAG)), useUnmergedTree = true)

    // Read without scrolling: the drawer is a plain scrolling Column, so every row is laid out and its unclipped bounds are in one
    // frame. A scroll between two reads would move the second against the first (CI run 41543362662).
    private fun boundsOf(text: String): Rect = inDrawer(text).getUnclippedBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun trailsRow(): Rect = composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.REFERENCE_FS_BAND))
        .getUnclippedBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    @Test
    fun `after the Trails row each map is listed by its chip word, its credit under it, in today's words`() {
        setScreen()
        // Then each added map type's, by its own chip word and credit (dispatch 2026-11-03-05 D7; ui/map/street/StreetMapTest
        // holds the Street map's "Street" and "© OpenStreetMap contributors" word for word).
        val expected = listOf(
            "Trail" to "Protomaps © OpenStreetMap",
            "Topo" to "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)",
        ) + ADDED_MAP_TYPES.map { it.mapMode.label to it.mapMode.basemap.attribution }
        var floor = boundsOf("Trails: USDA Forest Service, CC BY 4.0 · BLM").bottom
        for ((name, credit) in expected) {
            val n = boundsOf(name)
            val c = boundsOf(credit)
            assertTrue("$name ($n) below what comes before it ($floor)", n.top >= floor - 0.5f)
            assertTrue("$credit ($c) under $name ($n)", c.top >= n.bottom - 0.5f)
            floor = c.bottom
        }
    }

    @Test
    fun `the Trails credit is under the Trails row, which stays one line`() {
        setScreen()
        val row = trailsRow()
        val credit = boundsOf("Trails: USDA Forest Service, CC BY 4.0 · BLM")
        assertTrue("the credit ($credit) under the Trails row ($row)", credit.top >= row.bottom - 0.5f)
        assertTrue("the credit above the first map, Trail", credit.bottom <= boundsOf("Trail").top + 0.5f)
    }

    @Test
    fun `with the synthetic fields listed, Synthetic test data is under them, and without them it is not shown`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))
        val lastSlider = composeRule.onNodeWithTag(mapLayerOpacityTag(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS)).getUnclippedBoundsInRoot()
        val credit = boundsOf("Synthetic test data")
        assertTrue("under the last colour field ($credit, ${lastSlider.bottom})", credit.top >= lastSlider.bottom.value - 0.5f)
    }

    @Test
    fun `with no colour field listed there is no Synthetic test data line`() {
        setScreen()
        assertEquals(0, composeRule.onAllNodes(hasText("Synthetic test data") and hasAnyAncestor(hasTestTag(TOOLS_DRAWER_SHEET_TAG)), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a map entry is not a control, with no click action, and real touches across it change no map`() {
        setScreen()
        assertEquals(Basemap.FORAGER_TRAIL, map.renderMode?.basemap)
        for (name in listOf("Topo") + ADDED_MAP_TYPES.map { it.mapMode.label }) {
            val node = inDrawer(name).performScrollTo()
            assertFalse("$name has no click action", node.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
            ACROSS.forEach { (x, y) ->
                node.performTouchInput { click(percentOffset(x, y)) }
                composeRule.waitForIdle()
            }
            assertEquals("a touch on $name changed no map", Basemap.FORAGER_TRAIL, map.renderMode?.basemap)
        }
    }
}
