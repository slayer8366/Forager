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
import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.mapLayerSwitchTag
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
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
 * Step 8b on the Maps tab (dispatch 2026-10-26-15 Amendment 2; the owner's wording, RECORD -1680): the Layers sheet's
 * "Official trails" row, through the real screen and the real [AvailabilityViewModel]. The map slot stands in for
 * `SightingsMap` and reports, as it does after each style load ([MapRenderMode.onReferenceTrailsShown]), whether its
 * loaded style draws the band: `true` for Trail Map's street-v3, `false` for today's v2, Topographical and
 * OpenStreetMap (`ReferenceTrailsVisibilityTest` covers that report against the style fixtures). Every touch is a real
 * one at screen coordinates.
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

    /** A map whose loaded style [drawsBand] or not, reported as `SightingsMap` reports it. */
    private fun slot(drawsBand: Boolean): MapSlot = { _, _, mode, _, _, _, _, _, modifier ->
        renderMode = mode
        LaunchedEffect(Unit) { mode.onReferenceTrailsShown(drawsBand) }
        Box(modifier.testTag("map-slot"))
    }

    private fun setScreen(drawsBand: Boolean, stored: MapLayerPreferences = MapLayerPreferences.NONE) {
        preferences = InMemoryLayerPreferences(stored)
        val viewModel = mapLayersViewModel(layerPreferences = preferences)
        composeRule.setContent { MapLayersTestScreen(viewModel, slot(drawsBand), com.zynergylabs.forager.app.domain.AbsentForecastCellStore) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
    }

    private fun inSheet(text: String) = composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(MAP_LAYERS_SHEET_TAG)))

    private fun inSheetCount(text: String) =
        composeRule.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(MAP_LAYERS_SHEET_TAG))).fetchSemanticsNodes().size

    private fun row() = composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.REFERENCE_FS_BAND))

    private fun rowState() = row().fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

    private fun bandVisibleOnMap() = renderMode?.layers?.stateOf(MapLayerIds.REFERENCE_FS_BAND)?.visible

    @Test
    fun `on today's style the sheet has no Official trails row, and the eight overlays are as they were`() {
        setScreen(drawsBand = false)
        assertEquals(0, inSheetCount("Official trails"))
        assertEquals(0, inSheetCount("Forest Service and BLM"))
        listOf("Finds", "Photos", "Waypoints", "Planned trips", "Recording trail", "Tracks", "Offline maps", "Journal entries").forEach {
            inSheet(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `with the band loaded the row shows, with its second line, on by default`() {
        setScreen(drawsBand = true)
        inSheet("Official trails").performScrollTo().assertIsDisplayed()
        inSheet("Forest Service and BLM").assertIsDisplayed()
        assertEquals(ToggleableState.On, rowState())
        assertEquals(true, bandVisibleOnMap())
    }

    @Test
    fun `a real touch anywhere across the row switches the band off and on, in the state the map is handed and in the store`() {
        setScreen(drawsBand = true)
        row().performScrollTo()
        val expected = mutableListOf<String>()
        // A finger is not a point: the row's left end, middle and right end (over its switch), each a real touch.
        listOf(0.05f, 0.5f, 0.95f).forEachIndexed { i, fraction ->
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
    fun `a stored off is restored - the map is handed the band hidden and the row shows off`() {
        setScreen(drawsBand = true, stored = MapLayerPreferences.NONE.copy(visibility = mapOf(MapLayerIds.REFERENCE_FS_BAND to false)))
        assertEquals(false, bandVisibleOnMap())
        row().performScrollTo()
        assertEquals(ToggleableState.Off, rowState())
    }

    @Test
    fun `the row sits after the record overlays, below Journal entries`() {
        setScreen(drawsBand = true)
        val journal = inSheet("Journal entries").performScrollTo().fetchSemanticsNode().boundsInRoot
        val official = inSheet("Official trails").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertEquals(true, official.top > journal.top)
    }
}
