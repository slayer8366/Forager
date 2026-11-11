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
            "after Try again with nothing to fetch (fetches ${rig.sightingsFetches.size - fetches}), the notice reads \"$SEARCH_RETRYING_NOTICE_TEXT\" and stays",
            !textShown(SEARCH_RETRYING_NOTICE_TEXT),
        )
        // What Try again does here is not pinned (the planner's question, I1); only that it does not stick. If the notice still
        // shows, its button is usable again.
        if (composeRule.onAllNodes(hasTestTag(SEARCH_FAILED_TRY_AGAIN_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true).assertIsEnabled()
        }
    }
}
