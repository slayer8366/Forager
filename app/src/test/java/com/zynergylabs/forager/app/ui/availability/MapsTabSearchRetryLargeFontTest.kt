package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Dispatch 2026-11-11-01, F4 (E4, the owner: "Notice stays, shows it's trying (Recommended)"; "Nothing jumps"): at a large font
 * the failure line wraps and "Trying again…" does not, so the notice keeps its size through a retry only because both lines are
 * laid out in one space. At the default font the two lines take the same height and a size check cannot tell; this is the case
 * that can.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
class MapsTabSearchRetryLargeFontTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    @Test
    fun `at a large font the notice keeps its size when the retry starts and when it fails`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
            assertTrue("positive control: the notice reads \"$SEARCH_RETRYING_NOTICE_TEXT\"", composeRule.onAllNodesWithText(SEARCH_RETRYING_NOTICE_TEXT).fetchSemanticsNodes().isNotEmpty())
            val during = bounds(SEARCH_FAILED_NOTICE_TAG)
            assertTrue("large font: the notice moved or changed size when the retry started: ${notice.d()} then ${during.d()}", (notice.height - during.height).let { kotlin.math.abs(it) <= 0.5f } && kotlin.math.abs(notice.top - during.top) <= 0.5f)
        } finally {
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
        val after = bounds(SEARCH_FAILED_NOTICE_TAG)
        assertTrue("large font: the notice changed size when the retry failed: ${notice.d()} then ${after.d()}", kotlin.math.abs(notice.height - after.height) <= 0.5f)
        assertTrue("positive control: the notice is shown", composeRule.onAllNodes(hasTestTag(SEARCH_FAILED_NOTICE_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
