package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECORD -728, through the real [AvailabilityScreen] on the Maps tab with a fix (the strip showing its readout).
 *
 * The seam: the owner, "There is a slightly hang down of chrome from the search menu that extends into the strip zone, making
 * it look taller than it is." Measured at the build before the fix (semantic bounds, 384 dp portrait): the bar 0 to 45 dp, its
 * divider (its visible bottom edge) 40 to 41 dp, the strip from 45 dp; 4 dp of the bar's fill, its bottom padding, hung below
 * the divider against the strip. Now the divider is the bar's last 1 dp and the strip starts at it: no overlap, no gap.
 *
 * The text: the owner, "Go up to 14 sp". The strip's readout line is 14 sp (STRIP_READOUT_FONT_SIZE), read from each text's own
 * laid-out style, and the strip stays at least 36 dp tall (its three-dot button's square).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SearchBarStripSeamTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val mapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG)) }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun setScreen() {
        composeRule.setContent { Screen() }
        composeRule.waitForIdle()
    }

    /**
     * Dispatch 2026-10-12-06 (RECORD -934: the tuck supersedes RECORD -728's "no hang-down"): over the map the bar is the rounded
     * pill with no divider, and the strip is tucked under it. Was: "the strip starts exactly at the search bar's divider, its
     * visible bottom, with no fill below the divider" (strip.top == divider.bottom; the divider the bar's last line). Now the
     * strip's own bounds, where its readouts and touches are, start at the pill's bottom, no gap and no overlap; only its fill
     * reaches up behind the pill's rounded lower corners, which `MapChromeColourPixelsTests` reads as one 0.8 layer. No divider
     * is drawn over the map, and the pill is 40 dp (RECORD -943; was "keeps its 45 dp").
     */
    @Test
    fun `the strip's bounds start at the pill's bottom, no divider is drawn over the map, and the pill is 40 dp`() {
        setScreen()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        println("MEASURED bar ${bar.top}..${bar.bottom}, strip ${strip.top}..${strip.bottom}")

        assertTrue("no divider over the map", composeRule.onAllNodesWithTag(SEARCH_ENTRY_BAR_DIVIDER_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertEquals("the strip's bounds start at the pill's bottom: no gap, no overlap", bar.bottom.value, strip.top.value, 0.5f)
        // RECORD -943 (the owner: "The search panel has a very thick margin and it can afford to thin out"): the Maps tab's pill
        // is 40 dp here, at 384 dp and font 1.0 (3 dp above its field, 4 dp and a 1 dp spacer below), the planner's floor. Was 45.
        assertEquals("the pill is 40 dp", 40f, (bar.bottom - bar.top).value, 0.5f)
        assertTrue("and no less than the planner's 40 dp floor", (bar.bottom - bar.top).value >= 40f - 0.5f)
    }

    /**
     * Dispatch 2026-10-12-08 (RECORD -951, item 8: "Enforce the 40 dp pill floor, with a test at font 0.85"): RECORD -943's floor
     * held in code. At font 0.85 the field, twice a line, is shorter, and the pill would be about 37 dp without the floor; with it,
     * 40, and the strip still starts at the pill's bottom.
     */
    @Test
    @Config(fontScale = 0.85f)
    fun `at font 0,85 the pill keeps its 40 dp floor and the strip still starts at its bottom`() {
        setScreen()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        println("MEASURED font 0.85: bar ${bar.top}..${bar.bottom} (${(bar.bottom - bar.top).value} dp), strip ${strip.top}..${strip.bottom}")
        assertEquals("positive control: font 0.85", 0.85f, composeRule.density.fontScale, 0.001f)
        assertTrue("the pill is at least 40 dp: ${(bar.bottom - bar.top).value}", (bar.bottom - bar.top).value >= 40f - 0.5f)
        assertEquals("the strip's bounds start at the pill's bottom: no gap, no overlap", bar.bottom.value, strip.top.value, 0.5f)
    }

    private fun fontSizeOf(node: SemanticsNodeInteraction): Float {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.fontSize.value
    }

    @Test
    fun `the strip's readout is 14 sp and the strip is at least 36 dp tall`() {
        setScreen()
        listOf(COMPASS_STRIP_HEADING_TAG, COMPASS_STRIP_ELEVATION_TAG, COMPASS_STRIP_COORDINATES_TAG).forEach { tag ->
            assertEquals("$tag is 14 sp", 14.sp.value, fontSizeOf(composeRule.onNodeWithTag(tag, useUnmergedTree = true)), 0.01f)
        }
        val dot = composeRule.onAllNodes(hasText("·") and hasAnyAncestor(hasTestTag(STRIP_TAG)), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("positive control: the strip draws its dots", dot.isNotEmpty())
        val strip = bounds(STRIP_TAG)
        assertTrue("the strip is at least 36 dp tall (${strip.bottom - strip.top})", (strip.bottom - strip.top).value >= 36f - 0.5f)
    }

    @Composable
    private fun Screen() {
        AvailabilityScreen(
            uiState = LAYOUT_FIXES_FIX_STATE,
            searchInputs = AvailabilityScreenSearchInputs(
                onUseCurrentLocation = {},
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

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}
