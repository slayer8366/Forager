package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
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
 * Tools > Map Layers' fold and switches (dispatch 2026-10-28-02, steps 5 to 7 and 9), through the real [AvailabilityScreen]
 * and its real ViewModel, with real touches: folded the first time; once opened it stays open across closing the drawer,
 * changing tab and a trip into Settings (CLAUDE.md, UX defaults); each switch, by a real touch on its row, changes the stored
 * choice and the state the map is handed; and the two folds in Tools open and close on their headers, which carry the
 * spoken "Expand" or "Collapse" the arrow used to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapLayersToolsSectionTest {

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
    private lateinit var preferences: InMemoryLayerPreferences

    private fun setScreen() {
        preferences = InMemoryLayerPreferences()
        val viewModel = mapLayersViewModel(layerPreferences = preferences)
        composeRule.setContent { MapLayersTestScreen(viewModel, map.slot, com.zynergylabs.forager.app.domain.AbsentForecastCellStore) }
        composeRule.waitForIdle()
    }

    private fun mapLayersOpen() = composeRule.onAllNodesWithTag(mapLayerSwitchTag(MapLayerIds.FINDS)).fetchSemanticsNodes().isNotEmpty()

    private fun inDrawer(text: String) = composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TOOLS_DRAWER_SHEET_TAG)))

    private fun touchCentre(text: String) {
        inDrawer(text).performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun clickLabel(title: String) =
        composeRule.onNodeWithTag(collapsibleSectionHeaderTag(title)).fetchSemanticsNode().config[SemanticsActions.OnClick].label

    @Test
    fun `Map Layers is folded the first time, and once opened stays open across closing the drawer, a tab change and Settings`() {
        setScreen()
        composeRule.openTools()
        assertTrue("folded the first time", !mapLayersOpen())

        composeRule.toggleMapLayersFold()
        assertTrue("opened", mapLayersOpen())

        composeRule.closeTools()
        composeRule.openTools()
        assertTrue("still open after closing the drawer", mapLayersOpen())

        composeRule.closeTools()
        composeRule.onNode(hasText("Journal") and !hasAnyAncestor(hasTestTag(TOOLS_DRAWER_SHEET_TAG))).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.openTools()
        assertTrue("still open after a tab change", mapLayersOpen())

        touchCentre("Settings")
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue("still open after a trip into Settings", mapLayersOpen())

        composeRule.toggleMapLayersFold()
        assertTrue("a second touch on its header folds it", !mapLayersOpen())
    }

    @Test
    fun `each switch, by a real touch on its row, changes the stored choice and the state the map is handed`() {
        setScreen()
        composeRule.openToolsMapLayers()
        val ids = MAP_LAYER_OVERLAYS.map { it.layerId }
        ids.forEach { id ->
            val before = map.renderMode?.layers?.stateOf(id)?.visible ?: error("no layer state")
            composeRule.onNodeWithTag(mapLayerSwitchTag(id)).performScrollTo().performTouchInput { click(Offset(width * 0.3f, centerY)) }
            composeRule.waitForIdle()
            assertEquals("$id on the map", !before, map.renderMode?.layers?.stateOf(id)?.visible)
            assertEquals("$id stored", "visible $id ${!before}", preferences.writes.last())
            val state = composeRule.onNodeWithTag(mapLayerSwitchTag(id)).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
            assertEquals("$id's row", if (before) ToggleableState.Off else ToggleableState.On, state)
        }
        assertEquals("one write per switch", ids.size, preferences.writes.size)
    }

    @Test
    fun `Trip Planner and Map Layers open and close on a real touch on their headers, which say Expand or Collapse`() {
        setScreen()
        composeRule.openTools()
        for (title in listOf("Trip Planner", MAP_LAYERS_SECTION_TITLE)) {
            assertEquals("Expand $title", clickLabel(title))
            val header = composeRule.onNodeWithTag(collapsibleSectionHeaderTag(title)).performScrollTo()
            header.performTouchInput { click(Offset(width * 0.9f, centerY)) }
            composeRule.waitForIdle()
            assertEquals("$title opened by a touch at its right end, where the arrow was", "Collapse $title", clickLabel(title))
            composeRule.onNodeWithTag(collapsibleSectionHeaderTag(title)).performScrollTo().performTouchInput { click(Offset(width * 0.1f, centerY)) }
            composeRule.waitForIdle()
            assertEquals("$title closed by a touch at its left end", "Expand $title", clickLabel(title))
        }
    }
}
