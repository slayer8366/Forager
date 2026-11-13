package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
 * Dispatch 2026-11-11-01, Amendment T, TB4 (the owner: "One line, in the bar"; option text "As you ruled: that case also reads
 * \"Search failed.\" in the bar, and \"Try again\" is made to run the list's half again. No banner."). Out of fullscreen, the
 * ranked list alone fails and the map's half loads. Through the real screen and ViewModel: "Try again" by a real touch fetches
 * the list again and not the map's sightings; "Trying again…" while it runs; "Search failed." if it fails again; the resting
 * line and Clear when it works. Before this amendment "Try again" did nothing there (`retryFailedSearch` returned at its first
 * line); seen failing first on the probe branch (see the report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchListOnlyTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun textShown(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun listOnlyFailure() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: the ranked list failed", LIST_SEARCH_FAILED_MESSAGE, rig.viewModel.uiState.value.errorMessage)
        assertTrue("positive control: the map's half loaded", rig.viewModel.uiState.value.sightingsErrorMessage == null && rig.mapSightings.isNotEmpty())
        assertTrue("a list-only failure does not read \"$SEARCH_BAR_FAILED_TEXT\" in the bar", textShown(SEARCH_BAR_FAILED_TEXT))
        assertTrue("a list-only failure still shows the banner's line", !textShown(LIST_SEARCH_FAILED_MESSAGE))
    }

    @Test
    fun `a real touch on Try again runs the list's half again, Trying again while it runs, Search failed when it fails`() {
        listOnlyFailure()
        val lists = rig.availabilityFetches.size
        val maps = rig.sightingsFetches.size
        val gate = CompletableDeferred<Unit>()
        rig.availabilityGate = gate
        try {
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
            assertEquals("Try again did not run the list's half again", lists + 1, rig.availabilityFetches.size)
            assertEquals("Try again ran the map's half, which had loaded", maps, rig.sightingsFetches.size)
            assertTrue("while the list runs again the bar does not read \"$SEARCH_RETRYING_NOTICE_TEXT\"", textShown(SEARCH_RETRYING_NOTICE_TEXT))
        } finally {
            rig.availabilityGate = null
            gate.complete(Unit)
            rig.settle()
        }
        assertTrue("after the list failed again the bar does not read \"$SEARCH_BAR_FAILED_TEXT\"", textShown(SEARCH_BAR_FAILED_TEXT))
        assertEquals("the map's observations left the map", listOf(RealSearchScreenRig.sightingAt(com.zynergylabs.forager.app.domain.model.Region(45.3736, -121.6959, 8))), rig.mapSightings)
    }

    @Test
    fun `when the list works again the resting line and Clear come back`() {
        listOnlyFailure()
        rig.availabilityFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        val summary = activeSearchSummary(rig.viewModel.uiState.value, rig.viewModel.uiState.value.distanceUnit)
        assertTrue("the bar still reads \"$SEARCH_BAR_FAILED_TEXT\" after the list worked", !textShown(SEARCH_BAR_FAILED_TEXT))
        assertTrue("the resting line <$summary> did not come back", textShown(summary))
        assertTrue("Clear did not come back", shown(SEARCH_BAR_CLEAR_TAG))
    }
}
