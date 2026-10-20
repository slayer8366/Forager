package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 1, item 7 (dispatch 2026-09-28-652; scout Q5; the owner, RECORD -651: "Fix it in this sweep": "Once it starts
 * closing, taps go beneath it and its buttons stop responding").
 *
 * The search dropdown's dismiss scrim goes the moment it starts closing, while the panel is still shrinking; before the fix a tap
 * on the shrinking panel landed on its buttons. Here the panel is opened, closed with Back, and caught mid-close with the clock
 * stopped; a real touch on its Search button must reach the map beneath and not the button. The positive
 * control is the same touch on the open panel, which must reach the button, so the mid-close result is not just a miss.
 *
 * Dispatch 2026-09-28-697 moved "Use current location", the button this test first touched (at the panel's top), to the
 * panel's bottom row and renamed it Search (RECORD -700: the same path). Touching Search there, the mid-close test also asserts the touch point is still inside
 * the shrinking panel when it lands: a point the panel had already shrunk away from would reach the map whatever the fix did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SearchDropdownClosingTapThroughTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private var mapTaps = 0
    private var searches = 0

    private val mapSlot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
        Box(
            modifier
                .testTag(LAYOUT_FIXES_MAP_TAG)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    mapTaps++
                    onTap()
                },
        )
    }

    @After
    fun clockBack() {
        composeRule.mainClock.autoAdvance = true
    }

    private fun setScreen() {
        composeRule.setContent { ForagerTheme { Screen() } }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun openDropdown() {
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun buttonCentre(): Offset {
        val b = composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG).fetchSemanticsNode().boundsInRoot
        return b.center
    }

    private fun touch(at: Offset) = composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }

    private fun dropdownShown() = composeRule.onAllNodes(hasTestTag(SEARCH_DROPDOWN_TAG)).fetchSemanticsNodes().size

    @Test
    fun `a touch on the open panel's button reaches the button (the positive control)`() {
        setScreen()
        openDropdown()
        val before = mapTaps
        touch(buttonCentre())
        composeRule.waitForIdle()
        assertEquals("the open panel's button took the touch", 1, searches)
        assertEquals("and the map did not", before, mapTaps)
    }

    @Test
    fun `mid-close, a touch on the shrinking panel's button reaches the map and not the button`() {
        setScreen()
        openDropdown()
        val at = buttonCentre()
        val panelBefore: DpRect = with(composeRule.density) {
            composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).fetchSemanticsNode().boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) }
        }

        composeRule.mainClock.autoAdvance = false
        // With the clock stopped, a state write outside composition reaches the next frame only once it is applied (as the fan's
        // tests do it): Back, then the apply, then frames.
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("the panel is still on screen, closing", 1, dropdownShown())
        assertEquals("the dismiss scrim is already gone", 0, composeRule.onAllNodes(hasTestTag(SEARCH_DROPDOWN_SCRIM_TAG)).fetchSemanticsNodes().size)

        val panelNow = composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the touch point $at is still inside the closing panel $panelNow", panelNow.contains(at))

        val before = mapTaps
        touch(at)
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("the closing panel's button did not fire", 0, searches)
        assertEquals("the touch reached the map beneath (panel was ${panelBefore.describe()})", before + 1, mapTaps)
        assertEquals("and it was taken mid-close", 1, dropdownShown())

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue("closed in the end", dropdownShown() == 0)
    }

    @Composable
    private fun Screen() {
        AvailabilityScreen(
            uiState = LAYOUT_FIXES_FIX_STATE,
            searchInputs = AvailabilityScreenSearchInputs(
                onUseCurrentLocation = { searches++ },
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
            ),
            offlineMaps = AvailabilityScreenOfflineMaps(
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
            ),
            settings = AvailabilityScreenSettings(
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
            ),
            mapSlot = mapSlot,
        )
    }
}
