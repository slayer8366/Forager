package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.FIND_OVER_VIEW_TAG
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_OPEN_FIND_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Item 8 of dispatch 2026-09-29-57 (amendments -256 and -262, "Remember and reopen"): a find opened from the Maps tab
 * with "Open in Journal" comes back to the Maps tab, its bubble open (and its fan's keys handed to the map), when it is
 * left with Back, through the real screen, the real bottom navigation and the real Back dispatcher. Every touch is a real
 * one; a glyph tap goes through the stub map's `renderMode.onFeatureTap`, as `SightingsMap`'s does. The map's own
 * reopening of the fan (a real `MapView`, its style load and camera folds) is device-only; what is asserted here is that the
 * keys reach the map.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenReturnToMapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(
        listOf(
            StubGlyph(MapLayerIds.FINDS, "find-1", 60.dp, 420.dp, LatLng(45.51, -122.61)),
            StubGlyph(MapLayerIds.PHOTOS, "ph-1", 60.dp, 500.dp, LatLng(45.53, -122.63)),
        ),
    )
    private var log by mutableStateOf(MushroomLogUiState(entries = listOf(BUBBLE_FIND), galleryPhotos = listOf(BUBBLE_PHOTO)))

    private fun setScreen() {
        // The Maps tab's saved records, as the ViewModel loads them at start: the find is drawn, so its bubble can come back.
        val viewModel = mapLayersViewModel(
            getMapRecords = {
                com.zynergylabs.forager.app.domain.MapRecords(
                    findMarkers = listOf(com.zynergylabs.forager.app.domain.model.RecordPoint("find-1", LatLng(45.51, -122.61))),
                    photoMarkers = emptyList(),
                    trackPolylines = emptyList(),
                    offlineRegionCircles = emptyList(),
                    failures = emptyList(),
                )
            },
        )
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = com.zynergylabs.forager.app.domain.AbsentForecastCellStore,
                logUiState = log,
                onOpenLogEntry = { id -> log = log.copy(editingEntry = log.entries.firstOrNull { it.id == id }) },
                onCloseLogEntry = { log = log.copy(editingEntry = null) },
                // The ViewModel's own moves: a delete drops the find and closes it, an edit swaps in a draft row, a save commits it back.
                onDeleteLogEntry = { id -> log = log.copy(entries = log.entries.filterNot { it.id == id }, editingEntry = null) },
                onStartEditingLogEntry = { log = log.copy(editingEntry = BUBBLE_FIND.copy(id = "draft-1", isDraft = true)) },
                onSaveLogEntry = { log = log.copy(editingEntry = BUBBLE_FIND) },
            )
        }
        composeRule.waitForIdle()
    }

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun touchNav(label: String) {
        composeRule.onNodeWithText(label).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun bubbleCount() = composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().size

    /** Taps the find's glyph and then the bubble's "Open in Journal"; the find is then open over the Journal. */
    private fun openFindInJournal() {
        touchCentreOf(glyphTag("find-1"))
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()
        touchCentreOf(MAP_BUBBLE_OPEN_FIND_TAG)
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertIsDisplayed()
        assertEquals("the find is open", "find-1", log.editingEntry?.id)
    }

    private fun assertOnMapsWithFindBubble() {
        composeRule.onAllNodesWithTag(FIND_OVER_VIEW_TAG).assertCountIs(0)
        assertNull("the find is closed", log.editingEntry)
        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_BUBBLE_OPEN_FIND_TAG).assertIsDisplayed()
    }

    @Test
    fun `Open in Journal then the real Back arrives on the Maps tab with that find's bubble open`() {
        setScreen()

        openFindInJournal()
        back()

        assertOnMapsWithFindBubble()
    }

    @Test
    fun `the find page's own back arrow arrives on the Maps tab with the bubble too`() {
        setScreen()

        openFindInJournal()
        composeRule.onNodeWithContentDescription("Back to your log").performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertOnMapsWithFindBubble()
    }

    @Test
    fun `from a fan member the keys of the open fan reach the new map and the bubble is open`() {
        setScreen()
        // The map publishes the open fan's members' keys as SightingsMap does while a fan is up.
        val fanKeys = listOf(FanKey(MapLayerIds.FINDS, "find-1"), FanKey(MapLayerIds.PHOTOS, "ph-1"))
        val memory = map.renderMode!!.returnMemory
        assertNotNull("the compact Maps tab hands its map the return memory", memory)
        memory!!.openFanKeys = fanKeys

        openFindInJournal()
        back()

        assertOnMapsWithFindBubble()
        assertEquals("the fan's keys wait for the new map", fanKeys, map.renderMode!!.returnMemory!!.pendingFanKeys)
    }

    @Test
    fun `the Journal tab is left showing what it showed before the find opened over it`() {
        setScreen()
        touchNav("Journal")
        touchCentreOf("journal-switch-records")
        composeRule.onNodeWithTag("records-chip-waypoints").performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("records-chip-waypoints").assertIsSelected()
        touchNav("Maps")

        openFindInJournal()
        back()
        assertOnMapsWithFindBubble()
        touchNav("Journal")

        composeRule.onNodeWithTag("records-chip-waypoints").assertIsSelected()
    }

    @Test
    fun `Back after switching to another tab does not return to the map`() {
        setScreen()

        openFindInJournal()
        touchNav("Seasonal")
        touchNav("Journal")
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertIsDisplayed()
        back()

        composeRule.onAllNodesWithTag(FIND_OVER_VIEW_TAG).assertCountIs(0)
        assertNull(log.editingEntry)
        assertEquals("no map bubble: Back did what it did before", 0, bubbleCount())
        composeRule.onNodeWithTag("journal-switch-records").assertIsDisplayed()
    }

    @Test
    fun `editing the find and saving it forgets the origin, so Back stays on the Journal`() {
        setScreen()

        openFindInJournal()
        composeRule.onNodeWithContentDescription("Entry options").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit entry").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("saved back onto the find's own id, in its report", "find-1", log.editingEntry?.id)
        composeRule.onNodeWithContentDescription("Back to your log").performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertNull(log.editingEntry)
        assertEquals("Back stayed on the Journal", 0, bubbleCount())
        composeRule.onNodeWithTag("journal-switch-records").assertIsDisplayed()
    }

    @Test
    fun `a find deleted from its page returns to the map without its bubble`() {
        setScreen()

        openFindInJournal()
        composeRule.onNodeWithContentDescription("Entry options").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete entry").performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(FIND_OVER_VIEW_TAG).assertCountIs(0)
        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        assertEquals("no bubble for a deleted find", 0, bubbleCount())
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountIs(expected: Int) {
    assertEquals(expected, fetchSemanticsNodes().size)
}
