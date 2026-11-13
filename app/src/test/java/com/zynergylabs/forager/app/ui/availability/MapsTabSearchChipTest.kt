package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01, F6 (Sorrel's review): the map's chips do not overlap "Try again". The taxon chip ("View on Map" from
 * a List-tab row) hangs under the compass strip, where the notice also sits. Through the real screen and ViewModel: a search,
 * a real touch on its List row, then the same search again failing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchChipTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    @Test
    fun `the taxon chip does not overlap Try again, and a real touch on Try again runs the search again`() {
        rig.setScreen()
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.touch(composeRule.onNode(hasText("List") and hasClickAction() and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG))))
        rig.touch(composeRule.onNodeWithText("artist's bracket", substring = true))
        assertTrue("positive control: View on Map put the taxon chip on the map", shown(CHIP_TAG))

        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        // Amendment R: out of fullscreen the failure and its "Try again" are in the bar (was: the notice under the bar).
        assertTrue("positive control: Try again shows", shown(SEARCH_FAILED_TRY_AGAIN_TAG))
        assertTrue("positive control: the chip still shows", shown(CHIP_TAG))

        val chip = bounds(CHIP_TAG)
        val tryAgain = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        val notice = bounds(SEARCH_ENTRY_BAR_TAG)
        val before = rig.sightingsFetches.size
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        val ran = rig.sightingsFetches.size == before + 1
        assertTrue(
            "the taxon chip ${chip.d()} overlaps Try again ${tryAgain.d()} (bar ${notice.d()}); a touch on Try again ran the search: $ran",
            !chip.overlaps(tryAgain),
        )
        assertEquals("a real touch on Try again did not run the search again", before + 1, rig.sightingsFetches.size)
    }

    private companion object {
        const val CHIP_TAG = "map-taxon-filter-chip"
    }
}
