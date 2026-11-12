package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
 * Dispatch 2026-11-11-01, I1 (Sorrel's second review): "Trying again…" must not stick. A search A whose sightings fetch is
 * held, then a search B whose fetch succeeds, then A's fetch fails late (the race is audit M13, left as it is: the failure shows
 * over B's good map). "Try again" then has nothing to fetch, because B's sightings are loaded. Before the fix the notice read
 * "Trying again…" for good, with the button greyed (against E4, the owner: "Notice stays, shows it's trying (Recommended)").
 * Through the real screen and ViewModel ([RealSearchScreenRig]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchStaleFailureTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private val regionB = Region(44.0521, -123.0868, 8)

    private fun textShown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `after an older search fails late over a newer one's map, Try again does not leave the notice reading Trying again`() {
        rig.setScreen()
        val gateA = CompletableDeferred<Unit>()
        rig.sightingsGate = gateA
        rig.searchCoordinates("45.3736", "-121.6959")
        assertTrue("positive control: search A's sightings fetch is held", rig.viewModel.uiState.value.isLoadingSightings)

        rig.sightingsGate = null
        rig.searchCoordinates("44.0521", "-123.0868")
        assertEquals("positive control: search B's observations are on the map", listOf(RealSearchScreenRig.sightingAt(regionB)), rig.mapSightings)

        rig.sightingsFailure = IOException("no signal (test)")
        gateA.complete(Unit)
        rig.settle()
        assertTrue("positive control (M13, left as it is): A's late failure shows its notice", textShown(SEARCH_FAILED_NOTICE_TEXT))

        val fetches = rig.sightingsFetches.size
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        rig.settle()

        assertTrue(
            "after Try again over a loaded map (fetches ${rig.sightingsFetches.size - fetches}), the notice reads \"$SEARCH_RETRYING_NOTICE_TEXT\" and stays",
            !textShown(SEARCH_RETRYING_NOTICE_TEXT),
        )
        // What Try again does here is not pinned (the planner's question, I1); only that it does not stick. If the notice still
        // shows, its button is usable again.
        if (composeRule.onAllNodes(hasTestTag(SEARCH_FAILED_TRY_AGAIN_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true).assertIsEnabled()
        }
    }

    // ── K1 (the owner: "Fetch again (Recommended)"), K2 (the planner's) ──

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Search A held, search B loaded, A fails late over B's map (M13, not changed). Leaves the failure switch on. */
    private fun lateFailureOverLoadedMap() {
        val gateA = CompletableDeferred<Unit>()
        rig.sightingsGate = gateA
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.sightingsGate = null
        rig.searchCoordinates("44.0521", "-123.0868")
        assertEquals("positive control: search B's observations are on the map", listOf(RealSearchScreenRig.sightingAt(regionB)), rig.mapSightings)
        // M3: the showing search's own query, as its fetch asked it: what a retry must fetch again.
        showingQuery = rig.sightingsFetches.last()
        assertEquals("positive control: the last fetch was search B's", regionB, showingQuery.first)
        rig.sightingsFailure = IOException("no signal (test)")
        gateA.complete(Unit)
        rig.settle()
        assertTrue("positive control (M13, not changed): A's late failure shows its notice", textShown(SEARCH_FAILED_NOTICE_TEXT))
    }

    private lateinit var showingQuery: Triple<Region, Int, com.zynergylabs.forager.app.domain.model.TaxonFilter>

    /**
     * A retry of B's sightings, held running: one more fetch, for B; "Trying again…"; the map there. Then [succeed] or not, and
     * the notice goes or reads "Search failed" again, the map there throughout.
     */
    private fun assertOneFetchForTheShowingSearch(where: String, startRetry: () -> Unit, succeed: Boolean) {
        val before = rig.sightingsFetches.size
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            startRetry()
            assertEquals("$where: the showing search's sightings were not fetched again exactly once", before + 1, rig.sightingsFetches.size)
            assertEquals("$where: the fetch was not for the showing search", showingQuery, rig.sightingsFetches.last())
            assertEquals("$where: the showing search's observations left the map while the fetch runs", listOf(RealSearchScreenRig.sightingAt(regionB)), rig.mapSightings)
            assertTrue("$where: the notice does not read \"$SEARCH_RETRYING_NOTICE_TEXT\" while the fetch runs", textShown(SEARCH_RETRYING_NOTICE_TEXT))
            assertTrue("$where: the map is gone while the fetch runs", shown(LAYOUT_FIXES_MAP_TAG))
            if (succeed) rig.sightingsFailure = null
        } finally {
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
        assertEquals("$where: more than one fetch", before + 1, rig.sightingsFetches.size)
        assertTrue("$where: the map is gone after the fetch", shown(LAYOUT_FIXES_MAP_TAG))
        if (succeed) {
            assertTrue("$where: the notice stays after the fetch worked", !shown(SEARCH_FAILED_NOTICE_TAG))
            assertEquals("$where: the map is not handed B's observations", listOf(RealSearchScreenRig.sightingAt(regionB)), rig.mapSightings)
        } else {
            assertTrue("$where: after the fetch failed the notice does not read \"$SEARCH_FAILED_NOTICE_TEXT\"", textShown(SEARCH_FAILED_NOTICE_TEXT))
            assertEquals("$where: the showing search's observations left the map after the fetch failed", listOf(RealSearchScreenRig.sightingAt(regionB)), rig.mapSightings)
        }
    }

    @Test
    fun `over a loaded map, a real touch on Try again fetches the showing search once, and the notice goes when it works`() {
        rig.setScreen()
        lateFailureOverLoadedMap()
        assertOneFetchForTheShowingSearch("Try again, works", { rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true)) }, succeed = true)
    }

    @Test
    fun `over a loaded map, a real touch on Try again fetches once, and Search failed comes back when it fails`() {
        rig.setScreen()
        lateFailureOverLoadedMap()
        assertOneFetchForTheShowingSearch("Try again, fails", { rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true)) }, succeed = false)
    }

    @Test
    fun `over a loaded map, a return of signal fetches the showing search once, and the notice goes when it works`() {
        rig.setScreen()
        rig.networkValidated.value = false
        rig.settle()
        lateFailureOverLoadedMap()
        assertOneFetchForTheShowingSearch("signal returns", { rig.networkValidated.value = true; rig.settle() }, succeed = true)
    }

    @Test
    fun `over a loaded map, a return of signal fetches the showing search once, and Search failed comes back when it fails`() {
        rig.setScreen()
        rig.networkValidated.value = false
        rig.settle()
        lateFailureOverLoadedMap()
        assertOneFetchForTheShowingSearch("signal returns, fails", { rig.networkValidated.value = true; rig.settle() }, succeed = false)
    }
}
