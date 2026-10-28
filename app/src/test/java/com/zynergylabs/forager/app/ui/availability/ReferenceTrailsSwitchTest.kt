package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.mapModeChipTag
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
 * Tools > Map Layers' "Trails" switch (dispatch 2026-10-28-02 Amendment 2, RECORD -1766; the owner: "Option 1. But when
 * selecting a map type that doesn't use Trails, just have it greyed out instead of removing it from the list"), through the
 * real screen and the real [AvailabilityViewModel]. The map slot stands in for `SightingsMap` and reports, as it does after
 * each style load ([MapRenderMode.onReferenceTrailsShown]), whether its loaded style draws the band: here, on Trail and not
 * on Topo or OSM (`ReferenceTrailsVisibilityTest` covers that report against the style fixtures). Every touch is a real one
 * at screen coordinates, and the map type is changed through the Layers button's panel, as a user changes it.
 *
 * The owner's confirmed step path: map type Topo or OSM > Tools > Map Layers > Trails is listed but grey, showing its stored
 * on/off, and a tap does nothing > switch back to Trail > Trails is normal and works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class ReferenceTrailsSwitchTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var renderMode: MapRenderMode? = null
    private lateinit var preferences: InMemoryLayerPreferences

    /** A map that draws the band on Trail Map only, reported as `SightingsMap` reports it after each style load. */
    private val slot: MapSlot = { _, _, mode, _, _, _, _, _, modifier ->
        renderMode = mode
        LaunchedEffect(mode.basemap) { mode.onReferenceTrailsShown(mode.basemap == Basemap.FORAGER_TRAIL) }
        Box(modifier.testTag("map-slot"))
    }

    private fun setScreen(stored: MapLayerPreferences = MapLayerPreferences.NONE) {
        preferences = InMemoryLayerPreferences(stored)
        val viewModel = mapLayersViewModel(layerPreferences = preferences)
        composeRule.setContent { MapLayersTestScreen(viewModel, slot, com.zynergylabs.forager.app.domain.AbsentForecastCellStore) }
        composeRule.waitForIdle()
    }

    /** The Layers button, then a map type chip, each a real touch. */
    private fun chooseMapType(mode: MapMode) {
        composeRule.onNodeWithContentDescription("Layers:", substring = true).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(mapModeChipTag(mode)).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun inDrawer(text: String) = composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TOOLS_DRAWER_SHEET_TAG)))

    private fun row() = composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.REFERENCE_FS_BAND))

    private fun rowState() = row().fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

    private fun rowDisabled() = row().fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)

    private fun bandVisibleOnMap() = renderMode?.layers?.stateOf(MapLayerIds.REFERENCE_FS_BAND)?.visible

    /** A finger is not a point: the row's left end, middle and right end (over its switch), each a real touch. */
    private val touchPoints = listOf(0.05f, 0.5f, 0.95f)

    @Test
    fun `on Trail the row shows Trails on one line, on by default, enabled`() {
        setScreen()
        composeRule.openToolsMapLayers()
        inDrawer("Trails").performScrollTo().assertIsDisplayed()
        assertEquals(false, rowDisabled())
        assertEquals(ToggleableState.On, rowState())
        assertEquals(true, bandVisibleOnMap())
    }

    @Test
    fun `on Trail a real touch anywhere across the row switches the band off and on, in the state the map is handed and in the store`() {
        setScreen()
        composeRule.openToolsMapLayers()
        row().performScrollTo()
        val expected = mutableListOf<String>()
        touchPoints.forEachIndexed { i, fraction ->
            row().performTouchInput { click(Offset(width * fraction, centerY)) }
            composeRule.waitForIdle()
            val on = i % 2 == 1
            expected += "visible ${MapLayerIds.REFERENCE_FS_BAND} $on"
            assertEquals("touch at $fraction", if (on) ToggleableState.On else ToggleableState.Off, rowState())
            assertEquals("touch at $fraction", on, bandVisibleOnMap())
        }
        assertEquals(expected, preferences.writes)
    }

    @Test
    fun `on Topo the row is still listed, grey, disabled to TalkBack, shows its stored state, and real touches do nothing, and back on Trail it works`() {
        setScreen(stored = MapLayerPreferences.NONE.copy(visibility = mapOf(MapLayerIds.REFERENCE_FS_BAND to false)))
        chooseMapType(MapMode.TOPOGRAPHIC)
        assertEquals(Basemap.OPEN_TOPO_MAP, renderMode?.basemap)
        composeRule.openToolsMapLayers()

        inDrawer("Trails").performScrollTo().assertIsDisplayed()
        assertTrue("disabled, as TalkBack reads it", rowDisabled())
        assertEquals("its stored off", ToggleableState.Off, rowState())
        val writesBefore = preferences.writes.toList()
        touchPoints.forEach { fraction ->
            row().performTouchInput { click(Offset(width * fraction, centerY)) }
            composeRule.waitForIdle()
            assertEquals("touch at $fraction changed nothing", ToggleableState.Off, rowState())
        }
        assertEquals("nothing stored", writesBefore, preferences.writes)

        composeRule.closeTools()
        chooseMapType(MapMode.TRAIL)
        composeRule.openTools()
        assertEquals("normal again", false, rowDisabled())
        row().performScrollTo().performTouchInput { click(Offset(width * 0.5f, centerY)) }
        composeRule.waitForIdle()
        assertEquals(ToggleableState.On, rowState())
        assertEquals(true, bandVisibleOnMap())
        assertEquals("visible ${MapLayerIds.REFERENCE_FS_BAND} true", preferences.writes.last())
    }

    @Test
    fun `on OSM a stored on shows on, grey`() {
        setScreen()
        chooseMapType(MapMode.OPENSTREETMAP)
        composeRule.openToolsMapLayers()
        assertTrue(rowDisabled())
        assertEquals(ToggleableState.On, rowState())
    }

    @Test
    fun `a stored off is restored - the map is handed the band hidden and the row shows off`() {
        setScreen(stored = MapLayerPreferences.NONE.copy(visibility = mapOf(MapLayerIds.REFERENCE_FS_BAND to false)))
        assertEquals(false, bandVisibleOnMap())
        composeRule.openToolsMapLayers()
        row().performScrollTo()
        assertEquals(ToggleableState.Off, rowState())
    }

    @Test
    fun `the row sits after the record overlays, below Journal Entries`() {
        setScreen()
        composeRule.openToolsMapLayers()
        val journal = inDrawer("Journal Entries").performScrollTo().fetchSemanticsNode().boundsInRoot
        val trails = inDrawer("Trails").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertEquals(true, trails.top > journal.top)
    }
}
