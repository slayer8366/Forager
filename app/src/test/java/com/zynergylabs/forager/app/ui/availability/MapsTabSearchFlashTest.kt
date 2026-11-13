package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01, Amendment Q, QC4 (from the S22 check: for about one frame "Request failed. Check your connection and
 * try again." was drawn above "Trying again…"). The cause: a retry of both halves starts a new search, which clears the map's
 * failure; the ranked list fails again at once offline and puts its line back; the map's refetch starts only on the next
 * composition, and in between the one-line fold (`mapsTabSearchNoticeState`) was off, since it asked for the map's failure only.
 * Here the map's refetch is held, so that window lasts and can be checked. Seen failing first on the probe branch mtsf-q-probe
 * (run 43372930723): `QC4: the list's failure line shows above "Trying again…"`. Fullscreen while navigating, portrait.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchFlashTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(
        composeRule,
        recording = AvailabilityScreenRecording(isRecording = true),
        navigation = AvailabilityScreenNavigation(isReturning = true),
    )

    private fun textShown(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `fullscreen while navigating, a retry of both halves never shows the list's failure line above Trying again`() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: the notice shows", composeRule.onAllNodes(hasTestTag(SEARCH_FAILED_NOTICE_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
            assertTrue("positive control: the notice reads Trying again…", textShown(SEARCH_RETRYING_NOTICE_TEXT))
            assertTrue("positive control: the ranked list failed again", rig.viewModel.uiState.value.errorMessage == LIST_SEARCH_FAILED_MESSAGE)
            assertTrue("QC4: the list's failure line shows above \"Trying again…\"", !textShown(LIST_SEARCH_FAILED_MESSAGE))
        } finally {
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
    }
}
