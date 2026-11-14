package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
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

    private val rig = RealSearchScreenRig(composeRule, countsLongPresses = true)

    private val region = Region(45.3736, -121.6959, 8)

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // Unmerged since Amendment R: out of fullscreen the failure's words are the search field's placeholder, inside the field.
    private fun textShown(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun search() = rig.searchCoordinates("45.3736", "-121.6959")

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    private fun assertSameRect(message: String, expected: Rect, actual: Rect) =
        assertTrue("$message: ${expected.d()} then ${actual.d()}", listOf(expected.left - actual.left, expected.top - actual.top, expected.right - actual.right, expected.bottom - actual.bottom).all { kotlin.math.abs(it) <= 0.5f })

    private fun enterFullscreen() {
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", !shown(SEARCH_ENTRY_BAR_TAG))
    }

    /**
     * E4 (the owner: "Notice stays, shows it's trying (Recommended)"): a retry held running, then failing, then succeeding. What
     * carries the failure ([holderTag]: the search bar out of fullscreen since Amendment R, the placed notice in fullscreen) keeps
     * its place and size and reads "Trying again…" while it runs; the icon cluster does not move; the notice reads [failedText]
     * again when it fails, and the failure goes when it works.
     */
    private fun assertRetryKeepsTheFailure(where: String, holderTag: String, failedText: String) {
        val holder = bounds(holderTag)
        val cluster = bounds(MAP_ICON_CLUSTER_TAG)
        val button = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
            assertTrue("$where: positive control: the retry is running", rig.viewModel.uiState.value.isLoadingSightings)
            assertTrue("$where: what carries the failure went away during the retry", shown(holderTag))
            assertTrue("$where: it does not read \"$SEARCH_RETRYING_NOTICE_TEXT\" during the retry", textShown(SEARCH_RETRYING_NOTICE_TEXT))
            assertTrue("$where: it still reads \"$failedText\" during the retry", !textShown(failedText))
            // G2 (the owner: "Shown, greyed out"): Try again stays, disabled, in the disabled look. I4 (Sorrel's second review): a
            // touch-and-count check was here and could not fail (the error is already cleared during a retry, so the retry
            // returns at its first line either way); removed. That it does nothing rests on its being disabled, asserted here.
            assertTrue("$where: Try again is not shown during the retry", shown(SEARCH_FAILED_TRY_AGAIN_TAG))
            composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true).assertIsNotEnabled()
            assertSameRect("$where: Try again moved or changed size when the retry started", button, bounds(SEARCH_FAILED_TRY_AGAIN_TAG))
            assertSameRect("$where: what carries the failure moved or changed size when the retry started", holder, bounds(holderTag))
            assertSameRect("$where: the icon cluster moved when the retry started", cluster, bounds(MAP_ICON_CLUSTER_TAG))
        } finally {
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
        assertTrue("$where: after the retry failed it does not read \"$failedText\"", textShown(failedText))
        assertSameRect("$where: what carries the failure moved or changed size when the retry failed", holder, bounds(holderTag))
        assertSameRect("$where: the icon cluster moved when the retry failed", cluster, bounds(MAP_ICON_CLUSTER_TAG))
        composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true).assertIsEnabled()
        assertSameRect("$where: Try again moved or changed size when the retry failed", button, bounds(SEARCH_FAILED_TRY_AGAIN_TAG))
        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertTrue("$where: the failure stays after the retry works", !textShown(failedText))
    }

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

        // Amendment R: out of fullscreen the one line is the bar's (was: the floating notice shown).
        assertTrue("both failed and the bar does not read \"$SEARCH_BAR_FAILED_TEXT\"", textShown(SEARCH_BAR_FAILED_TEXT))
        assertTrue("both failed and the list's own failure line shows beside the notice", !textShown(LIST_SEARCH_FAILED_MESSAGE))

        rig.availabilityFailure = null
        rig.sightingsFailure = null
        val lists = rig.availabilityFetches.size
        val maps = rig.sightingsFetches.size
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))

        assertEquals("Try again did not run the ranked list again", lists + 1, rig.availabilityFetches.size)
        assertEquals("Try again did not run the map's sightings again", maps + 1, rig.sightingsFetches.size)
        assertTrue("the failure stays in the bar after both succeed", !textShown(SEARCH_BAR_FAILED_TEXT))
        assertEquals("the map is handed the search's observations", listOf(RealSearchScreenRig.sightingAt(region)), rig.mapSightings)
    }

    @Test
    fun `when only the list fails and the map's sightings load the bar reads Search failed and no banner shows`() {
        // Amendment T, TB4 (the owner: "One line, in the bar"): out of fullscreen a list-only failure is the bar's one line, no
        // banner. Was: "when only the list fails and the map's sightings load the Maps tab shows today's banner" (D3).
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        search()
        assertEquals("positive control: the ranked list failed", LIST_SEARCH_FAILED_MESSAGE, rig.viewModel.uiState.value.errorMessage)

        assertTrue("only the list failed and its line still shows in the banner", !textShown(LIST_SEARCH_FAILED_MESSAGE))
        assertTrue("only the list failed and a search-failed notice floats", !shown(SEARCH_FAILED_NOTICE_TAG))
        assertTrue("only the list failed and the bar does not read \"$SEARCH_BAR_FAILED_TEXT\"", textShown(SEARCH_BAR_FAILED_TEXT))
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
        assertTrue("the failure stays in the bar after the search succeeds", !textShown(SEARCH_BAR_FAILED_TEXT))
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
        assertTrue("the failure went from the bar by itself", textShown(SEARCH_BAR_FAILED_TEXT))
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

        // Amendment Q, QC1 (the owner: "3 tries, then wait for me"; "Growing: 3, then 10"): the failed first try is followed by a
        // timed one 3 s later, and the returns held back run the third when their interval ends; then the three are spent. Was:
        // one more run when the interval ended and none after, before a failed try was followed by a timed one.
        signal(true)
        pass(13_000)
        assertEquals("within 13 s the failed search did not use its three automatic tries", 4, rig.sightingsFetches.size)
        pass(3 * SIGNAL_RETURN_MIN_INTERVAL_MS)
        assertEquals("after its three automatic tries the search ran again by itself", 4, rig.sightingsFetches.size)
    }

    @Test
    fun `while fullscreen hides the search bar the notice shows at the top of the map and Try again works`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        // Amendment R: out of fullscreen the failure is in the bar; the fullscreen notice goes where the bar was (was: measured
        // against the notice under the bar).
        val underBar = bounds(SEARCH_ENTRY_BAR_TAG).let { androidx.compose.ui.geometry.Rect(it.left, it.bottom, it.right, it.bottom) }

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

    @Test
    fun `during a retry the notice keeps its place and size and reads Trying again, and the cluster does not move`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        assertRetryKeepsTheFailure("in the bar", SEARCH_ENTRY_BAR_TAG, SEARCH_BAR_FAILED_TEXT)
    }

    @Test
    fun `during a retry in fullscreen the notice keeps its place and size and reads Trying again`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        enterFullscreen()
        assertRetryKeepsTheFailure("fullscreen", SEARCH_FAILED_NOTICE_TAG, SEARCH_FAILED_NOTICE_TEXT)
    }

    @Test
    fun `in fullscreen the notice is at the top of the map, below the compass strip, inside the screen`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        enterFullscreen()
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val map = bounds(LAYOUT_FIXES_MAP_TAG)
        val strip = bounds("compass-elevation-strip")
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue("the fullscreen notice ${notice.d()} is not just below the compass strip ${strip.d()}", notice.top >= strip.bottom - 1f && notice.top <= strip.bottom + 1f)
        assertTrue("the fullscreen notice ${notice.d()} is not on the map ${map.d()}", notice.top >= map.top - 1f && notice.bottom <= map.bottom + 1f)
        assertTrue("the fullscreen notice ${notice.d()} is not inside the screen ${root.d()}", notice.left >= root.left - 1f && notice.right <= root.right + 1f && notice.top >= root.top - 1f && notice.bottom <= root.bottom + 1f)
    }

    @Test
    fun `a long-press on the map just below the fullscreen notice reaches the map`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        enterFullscreen()
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        for (fx in listOf(0.3f, 0.5f)) {
            val at = Offset(notice.left + notice.width * fx, notice.bottom + 40f)
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("a long-press at $at, just below the fullscreen notice ${notice.d()}, did not reach the map", before + 1, rig.mapLongPresses)
        }
    }

    /**
     * Amendment R: out of fullscreen the failure is in the bar, so the cluster has nothing to keep below. A cluster dragged to its
     * top limit stays there while the search has failed. Was: "a cluster dragged to its top limit is held below the notice while
     * it shows, and returns when it goes" (the notice under the bar); the hold below a placed notice is now tested in fullscreen
     * (MapsTabSearchClusterTest).
     */
    @Test
    fun `with the failure in the bar, a cluster dragged to its top limit stays there`() {
        rig.setScreen()
        val handle = composeRule.onNodeWithTag("map-icon-bar-minimize-handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 0.dp, (-800).dp)
        val limit = bounds(MAP_ICON_CLUSTER_TAG).top

        rig.sightingsFailure = IOException("no signal (test)")
        search()
        assertTrue("positive control: the bar reads \"$SEARCH_BAR_FAILED_TEXT\"", textShown(SEARCH_BAR_FAILED_TEXT))
        assertEquals("the cluster moved for a failure that is in the bar", limit, bounds(MAP_ICON_CLUSTER_TAG).top, 1f)
        assertTrue("the cluster ${bounds(MAP_ICON_CLUSTER_TAG).d()} overlaps the bar ${bounds(SEARCH_ENTRY_BAR_TAG).d()}", !bounds(MAP_ICON_CLUSTER_TAG).overlaps(bounds(SEARCH_ENTRY_BAR_TAG)))
    }

    @Test
    fun `when both failed and a mistyped coordinate replaces the list's failure line, Try again still runs both`() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        // A mistyped coordinate, by real touches: its line replaces the list's failure line in the banner.
        rig.tapBar()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).performTextReplacement("999")
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LONGITUDE_TAG).performTextReplacement("-121.6959")
        rig.touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG))
        val message = rig.viewModel.uiState.value.errorMessage
        assertTrue("positive control: the list's failure line was replaced (now <$message>)", message != null && message != LIST_SEARCH_FAILED_MESSAGE)
        if (rig.dropdownShown()) composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        rig.settle()

        rig.availabilityFailure = null
        rig.sightingsFailure = null
        val lists = rig.availabilityFetches.size
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertEquals("after another line replaced the list's failure line, Try again did not run the ranked list again", lists + 1, rig.availabilityFetches.size)
    }
}
