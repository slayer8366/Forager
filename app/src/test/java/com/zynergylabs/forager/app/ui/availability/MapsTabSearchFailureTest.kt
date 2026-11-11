package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zynergylabs.forager.app.domain.model.Region
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

    private val rig = RealSearchScreenRig(composeRule)

    private val region = Region(45.3736, -121.6959, 8)

    private val tabBarTag get() = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG
    private val tabBarName get() = if (landscape) "the rail" else "the tab bar"

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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
