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
import com.zynergylabs.forager.app.ui.map.MapChromeContentColor
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

    @Test
    fun `after a failed sightings fetch the notice says so under the search bar at the map chrome's fill`() {
        searchThatFails()

        assertTrue("after a failed sightings fetch there is no search-failed notice", shown(SEARCH_FAILED_NOTICE_TAG))
        assertTrue(
            "the notice does not say \"$SEARCH_FAILED_NOTICE_TEXT\"",
            composeRule.onAllNodesWithText(SEARCH_FAILED_NOTICE_TEXT).fetchSemanticsNodes().isNotEmpty(),
        )
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        assertTrue("the notice ${notice.d()} is under the search bar ${bar.d()}", notice.top >= bar.bottom - 1f)
        val fill = composeRule.onNodeWithTag(SEARCH_FAILED_NOTICE_TAG, useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals("the notice's fill is at the map chrome's alpha", MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
        assertTrue(
            "the old full-tab text still shows",
            composeRule.onAllNodesWithText("Couldn't load sightings for the map.").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `the notice's text is opaque and the notice clears the compass strip`() {
        searchThatFails()
        val text = composeRule.onNode(hasText(SEARCH_FAILED_NOTICE_TEXT), useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContentColor]
        assertEquals("the notice's text is not opaque", 1f, text.alpha, 0.01f)
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val strip = bounds("compass-elevation-strip")
        assertTrue("the notice ${notice.d()} overlaps the compass strip ${strip.d()}", !notice.overlaps(strip))
    }

    @Test
    fun `real touches across Try again each run the search again`() {
        searchThatFails()
        // The fetch keeps failing, so the notice comes back after each touch and the next point can be touched.
        val points = listOf(0.15f to 0.25f, 0.85f to 0.25f, 0.5f to 0.5f, 0.15f to 0.75f, 0.85f to 0.75f)
        for ((fx, fy) in points) {
            assertTrue("before the touch at ($fx, $fy) there is no Try again", shown(SEARCH_FAILED_TRY_AGAIN_TAG))
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            assertEquals("a real touch at ($fx, $fy) of Try again did not run the search again", before + 1, rig.sightingsFetches.size)
        }
        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertEquals("Try again's search hands the map its observations", listOf(RealSearchScreenRig.sightingAt(region)), rig.mapSightings)
        assertTrue("the notice stays after the search succeeds", !shown(SEARCH_FAILED_NOTICE_TAG))
    }

    @Test
    fun `a long-press on the map just below the notice reaches the map`() {
        searchThatFails()
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val below = 40f
        for (fx in listOf(0.3f, 0.5f)) {
            val at = Offset(notice.left + notice.width * fx, notice.bottom + below)
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("a long-press at $at, just below the notice ${notice.d()}, did not reach the map", before + 1, rig.mapLongPresses)
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
