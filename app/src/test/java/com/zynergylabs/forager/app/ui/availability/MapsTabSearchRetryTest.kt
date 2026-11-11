package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import com.zynergylabs.forager.app.domain.model.Region
import java.io.IOException
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01, Step 2: what runs a failed search again, through the real screen and the real ViewModel
 * ([RealSearchScreenRig]), with the raw signal the screen is handed ([RealSearchScreenRig.networkValidated]) driven by the test.
 * The owner's rulings: C3 "One line (Recommended)" (both halves fail: one notice, "Try again" runs both); C4 "At once
 * (Recommended)" (signal returns: the search runs again straight away); C6 "Top of the map (Recommended)" (the bar hidden by
 * fullscreen); C7 "Notice stays, no auto retry (Recommended)" (a failure with good signal). D1: a signal that flaps must not
 * produce a burst of searches. D3: only the ranked list failing shows today's banner, as today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchRetryTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private val region = Region(45.3736, -121.6959, 8)

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textShown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun search() = rig.searchCoordinates("45.3736", "-121.6959")

    /** The raw signal changes, and the screen and the ViewModel are let run, without moving the clock. */
    private fun signal(validated: Boolean) {
        rig.networkValidated.value = validated
        composeRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    /** [ms] of time passes, on Compose's clock and the main looper's. */
    private fun pass(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        composeRule.waitForIdle()
    }

    @Test
    fun `when both the list and the map fail the Maps tab shows one line and Try again runs both`() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        val state = rig.viewModel.uiState.value
        assertEquals("positive control: the ranked list failed", LIST_SEARCH_FAILED_MESSAGE, state.errorMessage)
        assertTrue("positive control: the map's sightings failed", state.sightingsErrorMessage != null)

        assertTrue("both failed and there is no search-failed notice", shown(SEARCH_FAILED_NOTICE_TAG))
        assertTrue("both failed and the list's own failure line shows beside the notice", !textShown(LIST_SEARCH_FAILED_MESSAGE))

        rig.availabilityFailure = null
        rig.sightingsFailure = null
        val lists = rig.availabilityFetches.size
        val maps = rig.sightingsFetches.size
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))

        assertEquals("Try again did not run the ranked list again", lists + 1, rig.availabilityFetches.size)
        assertEquals("Try again did not run the map's sightings again", maps + 1, rig.sightingsFetches.size)
        assertTrue("the notice stays after both succeed", !shown(SEARCH_FAILED_NOTICE_TAG))
        assertEquals("the map is handed the search's observations", listOf(RealSearchScreenRig.sightingAt(region)), rig.mapSightings)
    }

    @Test
    fun `when only the list fails and the map's sightings load the Maps tab shows today's banner`() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        search()
        assertEquals("positive control: the ranked list failed", LIST_SEARCH_FAILED_MESSAGE, rig.viewModel.uiState.value.errorMessage)

        assertTrue("only the list failed and its banner is gone from the Maps tab", textShown(LIST_SEARCH_FAILED_MESSAGE))
        assertTrue("only the list failed and the search-failed notice shows", !shown(SEARCH_FAILED_NOTICE_TAG))
    }

    @Test
    fun `when signal returns a failed search runs again at once`() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)
        rig.sightingsFailure = null

        signal(true)

        assertEquals("the search did not run again when signal returned", 2, rig.sightingsFetches.size)
        rig.settle()
        assertTrue("the notice stays after the search succeeds", !shown(SEARCH_FAILED_NOTICE_TAG))
        assertEquals("the map is handed the search's observations", listOf(RealSearchScreenRig.sightingAt(region)), rig.mapSightings)
    }

    @Test
    fun `a failure with good signal keeps its notice and does not run again by itself`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("server error (test)")
        search()
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)

        pass(60_000)

        assertEquals("a failure with good signal ran again by itself", 1, rig.sightingsFetches.size)
        assertTrue("the notice went away by itself", shown(SEARCH_FAILED_NOTICE_TAG))
    }

    @Test
    fun `a signal that flaps runs the failed search again once at once and at most once more in the next ten seconds`() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)

        // Five returns inside one second, every retry failing again.
        repeat(5) {
            signal(true)
            pass(100)
            signal(false)
            pass(100)
        }
        assertEquals("five returns in a second ran the search more than once", 2, rig.sightingsFetches.size)

        signal(true)
        pass(SIGNAL_RETURN_MIN_INTERVAL_MS)
        assertEquals("the returns held back did not run the search once when the interval ended", 3, rig.sightingsFetches.size)
        pass(3 * SIGNAL_RETURN_MIN_INTERVAL_MS)
        assertEquals("with no new return the search ran again", 3, rig.sightingsFetches.size)
    }

    @Test
    fun `while fullscreen hides the search bar the notice shows at the top of the map and Try again works`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        val underBar = composeRule.onNodeWithTag(SEARCH_FAILED_NOTICE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", !shown(SEARCH_ENTRY_BAR_TAG))

        assertTrue("fullscreen hid the search-failed notice", shown(SEARCH_FAILED_NOTICE_TAG))
        val atTop = composeRule.onNodeWithTag(SEARCH_FAILED_NOTICE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the notice $atTop did not move up to where the bar was (under it, it was at $underBar)", atTop.top < underBar.top)

        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertEquals("Try again in fullscreen did not run the search again", 2, rig.sightingsFetches.size)
        assertEquals("the map is handed the search's observations", listOf(RealSearchScreenRig.sightingAt(region)), rig.mapSightings)
    }
}
