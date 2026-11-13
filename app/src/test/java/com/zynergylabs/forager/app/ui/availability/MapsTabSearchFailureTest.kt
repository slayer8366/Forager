package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01 (known bug 30, audit finding H1), Step 1: a failed or loading sightings search on the Maps tab.
 * The owner's ruling: "Map stays, auto retry (Recommended)": "search fails > map, tabs and search bar stay"; "Loading > map
 * stays, small progress mark".
 *
 * Through the real screen and the real ViewModel ([RealSearchScreenRig]): the search is run by real touches on the search
 * bar, and the map's sightings fetch is made to fail ([RealSearchScreenRig.sightingsFailure]) or held
 * ([RealSearchScreenRig.sightingsGate]). Each test lists what of the Maps tab's own chrome is missing, so one failure names
 * every missing piece rather than the first.
 *
 * Run at a portrait phone (the bottom bar), a short landscape phone and a landscape tablet (the rail): the app draws the
 * phone's tree at every size (dispatch 2026-09-28-245), so these are the wide layouts there are.
 */
abstract class MapsTabSearchFailureTests(private val landscape: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule, countsLongPresses = true)

    private val region = Region(45.3736, -121.6959, 8)

    private val tabBarTag get() = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG
    private val tabBarName get() = if (landscape) "the rail" else "the tab bar"

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** [tag]'s bounds in the root, in px. */
    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    /** What of the Maps tab's own chrome is not on screen. */
    private fun missingChrome(): List<String> = buildList {
        if (!shown(LAYOUT_FIXES_MAP_TAG)) add("the map")
        if (!shown(SEARCH_ENTRY_BAR_TAG)) add("the search bar")
        if (!shown(tabBarTag)) add(tabBarName)
    }

    /** The screen, then a search by real touches whose sightings fetch fails. */
    private fun searchThatFails() {
        rig.setScreen()
        assertEquals("positive control: before any search the Maps tab has all its chrome", emptyList<String>(), missingChrome())
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: the map's sightings were fetched once", 1, rig.sightingsFetches.size)
        assertEquals(
            "positive control: the fetch failed",
            "Couldn't load sightings for the map.",
            rig.viewModel.uiState.value.sightingsErrorMessage,
        )
    }

    @Test
    fun `after a failed sightings fetch the Maps tab keeps the map, the search bar and the tab bar`() {
        searchThatFails()

        assertEquals("missing from the Maps tab after a failed sightings fetch", emptyList<String>(), missingChrome())
    }

    @Test
    fun `after a failed sightings fetch a real touch on the search bar runs the search again`() {
        searchThatFails()
        rig.sightingsFailure = null

        assertTrue("after a failed sightings fetch there is no search bar to touch", shown(SEARCH_ENTRY_BAR_TAG))
        rig.searchCoordinates("45.3736", "-121.6959")

        assertEquals("the search ran the sightings fetch again", 2, rig.sightingsFetches.size)
        assertEquals("the map is handed the search's observations", listOf(RealSearchScreenRig.sightingAt(region)), rig.mapSightings)
    }

    @Test
    fun `while sightings load the Maps tab keeps the map, the search bar and the tab bar`() {
        rig.setScreen()
        assertEquals("positive control: before any search the Maps tab has all its chrome", emptyList<String>(), missingChrome())
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            rig.searchCoordinates("45.3736", "-121.6959")
            assertTrue("positive control: the map's sightings are loading", rig.viewModel.uiState.value.isLoadingSightings)

            assertEquals("missing from the Maps tab while sightings load", emptyList<String>(), missingChrome())
        } finally {
            gate.complete(Unit)
            rig.settle()
        }
    }

    // ── Step 2 (dispatch 2026-11-11-01, the owner's rulings C1 to C7) ──

    // ── Amendment R and S (the owner: "The try again notice for search should go into the search bar itself, in place of the
    // October text"; "\"Try again\" takes Clear's place (Recommended)"; "The whole line"; "Still \"Search failed.\""). These
    // replace the Step 2 tests of the notice under the bar (named in the report's addendum). ──

    private fun lineNode() = composeRule.onNode(hasText(SEARCH_BAR_FAILED_TEXT), useUnmergedTree = true)

    private fun barLineShown(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `after a failed sightings fetch the search bar reads Search failed with Try again in Clear's place, and no notice floats`() {
        searchThatFails()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        assertTrue("the bar does not read \"$SEARCH_BAR_FAILED_TEXT\"", barLineShown(SEARCH_BAR_FAILED_TEXT))
        val line = lineNode().fetchSemanticsNode().boundsInRoot
        assertTrue("the line ${line.d()} is not in the bar ${bar.d()}", line.top >= bar.top - 1f && line.bottom <= bar.bottom + 1f)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        lineNode().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertTrue("the bar's line is cut short", !layouts.single().isLineEllipsized(0))
        val summary = activeSearchSummary(rig.viewModel.uiState.value, rig.viewModel.uiState.value.distanceUnit)
        assertTrue("the resting line <$summary> still shows beside the failure (RA4: the whole line)", !barLineShown(summary))
        val tryAgain = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        assertTrue("Try again ${tryAgain.d()} is not wholly in the bar ${bar.d()}", tryAgain.left >= bar.left - 1f && tryAgain.right <= bar.right + 1f && tryAgain.top >= bar.top - 1f && tryAgain.bottom <= bar.bottom + 1f)
        assertTrue("Clear shows while the search has failed (SB1)", !shown(SEARCH_BAR_CLEAR_TAG))
        assertTrue("a notice floats under the bar out of fullscreen", !shown(SEARCH_FAILED_NOTICE_TAG))
        val fill = composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG, useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals("the bar's fill is not at the map chrome's alpha", MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
        assertTrue(
            "the old full-tab text still shows",
            composeRule.onAllNodesWithText("Couldn't load sightings for the map.").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `when the search works again the resting line and Clear come back`() {
        searchThatFails()
        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        val summary = activeSearchSummary(rig.viewModel.uiState.value, rig.viewModel.uiState.value.distanceUnit)
        assertTrue("the bar still reads \"$SEARCH_BAR_FAILED_TEXT\" after the search worked", !barLineShown(SEARCH_BAR_FAILED_TEXT))
        assertTrue("the resting line <$summary> did not come back", barLineShown(summary))
        assertTrue("Clear did not come back", shown(SEARCH_BAR_CLEAR_TAG))
        assertTrue("Try again stays after the search worked", !shown(SEARCH_FAILED_TRY_AGAIN_TAG))
    }

    @Test
    fun `real touches across Try again each run the search again`() {
        searchThatFails()
        // The fetch keeps failing, so the failure comes back after each touch and the next point can be touched.
        val points = listOf(0.15f to 0.25f, 0.85f to 0.25f, 0.5f to 0.5f, 0.15f to 0.75f, 0.85f to 0.75f)
        for ((fx, fy) in points) {
            assertTrue("before the touch at ($fx, $fy) there is no Try again", shown(SEARCH_FAILED_TRY_AGAIN_TAG))
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            assertEquals("a real touch at ($fx, $fy) of Try again did not run the search again", before + 1, rig.sightingsFetches.size)
            assertTrue("a touch on Try again opened the search panel", !rig.dropdownShown())
        }
        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertEquals("Try again's search hands the map its observations", listOf(RealSearchScreenRig.sightingAt(region)), rig.mapSightings)
        assertTrue("the bar still reads \"$SEARCH_BAR_FAILED_TEXT\" after the search succeeds", !barLineShown(SEARCH_BAR_FAILED_TEXT))
    }

    @Test
    fun `real touches on the rest of the bar open the search panel and do not run the search`() {
        searchThatFails()
        for (fx in listOf(0.1f, 0.5f, 0.9f)) {
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true), fx, 0.5f)
            assertTrue("a real touch at $fx across the bar's line did not open the search panel", rig.dropdownShown())
            assertEquals("a real touch at $fx across the bar's line ran the search", before, rig.sightingsFetches.size)
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            rig.settle()
            if (rig.dropdownShown()) {
                composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
                rig.settle()
            }
        }
    }

    @Test
    fun `focused after a failure, before typing, the bar still reads Search failed with Try again`() {
        searchThatFails()
        rig.tapBar()
        assertTrue("positive control: the bar's field is focused, its panel open", rig.dropdownShown())
        assertTrue("focused, the bar does not read \"$SEARCH_BAR_FAILED_TEXT\" (SB3)", barLineShown(SEARCH_BAR_FAILED_TEXT))
        assertTrue("focused, Try again is gone (SB3: it stays at the right)", shown(SEARCH_FAILED_TRY_AGAIN_TAG))
    }

    @Test
    fun `with the search failed, a long-press on the map below the bar reaches the map`() {
        searchThatFails()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds("compass-elevation-strip")
        val top = maxOf(bar.bottom, if (strip.left < bar.right && strip.right > bar.left) strip.bottom else bar.bottom)
        val cluster = bounds(MAP_ICON_CLUSTER_TAG)
        val points = listOf(0.3f, 0.5f).map { Offset(bar.left + bar.width * it, top + 40f) }.filter { !cluster.contains(it) }
        assertTrue("positive control: points below the bar", points.isNotEmpty())
        for (at in points) {
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("a long-press at $at, below the bar ${bar.d()}, did not reach the map", before + 1, rig.mapLongPresses)
        }
    }

    @Test
    fun `while sightings load a thin line runs along the search bar's bottom edge and nothing in the bar moves`() {
        rig.setScreen()
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        val summaryWhileLoading: Rect
        try {
            rig.searchCoordinates("45.3736", "-121.6959")
            assertTrue("positive control: the map's sightings are loading", rig.viewModel.uiState.value.isLoadingSightings)

            assertTrue("while sightings load there is no loading line on the search bar", shown(SEARCH_BAR_LOADING_LINE_TAG))
            val line = bounds(SEARCH_BAR_LOADING_LINE_TAG)
            val bar = bounds(SEARCH_ENTRY_BAR_TAG)
            assertEquals("the line ${line.d()} ends on the bar's bottom edge ${bar.d()}", bar.bottom, line.bottom, 1f)
            assertTrue("the line ${line.d()} is within the bar's width ${bar.d()}", line.left >= bar.left - 1f && line.right <= bar.right + 1f)
            val thinPx = with(composeRule.density) { SEARCH_BAR_LOADING_LINE_HEIGHT.toPx() }
            assertEquals("the line is thin", thinPx, line.height, 1f)
            summaryWhileLoading = bounds(ACTIVE_SEARCH_SUMMARY_TAG)
        } finally {
            gate.complete(Unit)
            rig.settle()
        }
        assertTrue("the loading line stays after the sightings arrive", !shown(SEARCH_BAR_LOADING_LINE_TAG))
        assertEquals("the bar's text moved when the sightings arrived", summaryWhileLoading, bounds(ACTIVE_SEARCH_SUMMARY_TAG))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchFailurePortraitTest : MapsTabSearchFailureTests(landscape = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapsTabSearchFailureShortLandscapeTest : MapsTabSearchFailureTests(landscape = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land")
class MapsTabSearchFailureLandscapeTabletTest : MapsTabSearchFailureTests(landscape = true)
