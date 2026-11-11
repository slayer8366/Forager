package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.longClick
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

    private fun textShown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

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
     * E4 (the owner: "Notice stays, shows it's trying (Recommended)"): a retry held running, then failing, then succeeding. The
     * notice keeps its place and size and reads "Trying again…" while it runs; the icon cluster does not move; the loading line
     * of C2 shows; the notice reads "Search failed" again when it fails, and goes when it works.
     */
    private fun assertRetryKeepsTheNotice(where: String) {
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val cluster = bounds(MAP_ICON_CLUSTER_TAG)
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
            assertTrue("$where: positive control: the retry is running", rig.viewModel.uiState.value.isLoadingSightings)
            assertTrue("$where: the notice went away during the retry", shown(SEARCH_FAILED_NOTICE_TAG))
            assertTrue("$where: the notice does not read \"$SEARCH_RETRYING_NOTICE_TEXT\" during the retry", textShown(SEARCH_RETRYING_NOTICE_TEXT))
            assertTrue("$where: the notice still reads \"$SEARCH_FAILED_NOTICE_TEXT\" during the retry", !textShown(SEARCH_FAILED_NOTICE_TEXT))
            assertTrue("$where: Try again shows during the retry", !shown(SEARCH_FAILED_TRY_AGAIN_TAG))
            assertSameRect("$where: the notice moved or changed size when the retry started", notice, bounds(SEARCH_FAILED_NOTICE_TAG))
            assertSameRect("$where: the icon cluster moved when the retry started", cluster, bounds(MAP_ICON_CLUSTER_TAG))
        } finally {
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
        assertTrue("$where: after the retry failed the notice does not read \"$SEARCH_FAILED_NOTICE_TEXT\"", textShown(SEARCH_FAILED_NOTICE_TEXT))
        assertSameRect("$where: the notice moved or changed size when the retry failed", notice, bounds(SEARCH_FAILED_NOTICE_TAG))
        assertSameRect("$where: the icon cluster moved when the retry failed", cluster, bounds(MAP_ICON_CLUSTER_TAG))
        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertTrue("$where: the notice stays after the retry works", !shown(SEARCH_FAILED_NOTICE_TAG))
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

    @Test
    fun `during a retry the notice keeps its place and size and reads Trying again, and the cluster does not move`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        assertRetryKeepsTheNotice("under the bar")
    }

    @Test
    fun `during a retry in fullscreen the notice keeps its place and size and reads Trying again`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        enterFullscreen()
        assertRetryKeepsTheNotice("fullscreen")
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

    @Test
    fun `a cluster dragged to its top limit is held below the notice while it shows, and returns when it goes`() {
        rig.setScreen()
        val handle = composeRule.onNodeWithTag("map-icon-bar-minimize-handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 0.dp, (-800).dp)
        val limit = bounds(MAP_ICON_CLUSTER_TAG).top

        rig.sightingsFailure = IOException("no signal (test)")
        search()
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val cluster = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("positive control: the cluster's limit $limit is above the notice's bottom ${notice.bottom}", limit < notice.bottom)
        assertTrue("the cluster ${cluster.d()} is not held below the notice ${notice.d()}", cluster.top >= notice.bottom - 1f)

        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertEquals("the cluster did not return to its limit when the notice went", limit, bounds(MAP_ICON_CLUSTER_TAG).top, 1f)
    }
}
