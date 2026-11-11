package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Dispatch 2026-11-11-01, F1 (Sorrel's review): the search-failed notice while navigating, portrait. Read from code
 * (CompactMapTopStrip.kt): the compass strip's measured height is set to 0 while navigating, so the notice, placed below the
 * strip by that height, would sit under the navigation card. Through the real screen and ViewModel, navigating and recording,
 * a failed search, then real touches at screen coordinates across "Try again". Where the notice sits while navigating is the
 * owner's: this test only says whether it can be seen and touched where it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchNavigatingTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(
        composeRule,
        recording = AvailabilityScreenRecording(isRecording = true),
        navigation = AvailabilityScreenNavigation(isReturning = true),
    )

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    @Test
    fun `while navigating in portrait the notice is uncovered and real touches across Try again run the search again`() {
        rig.setScreen()
        assertTrue("positive control: navigating, the compass strip with the navigation info shows", shown(STRIP_TAG))
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)
        assertTrue("positive control: the search-failed notice is composed", shown(SEARCH_FAILED_NOTICE_TAG))

        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val tryAgain = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        val strip = bounds(STRIP_TAG)
        val where = "notice ${notice.d()}, Try again ${tryAgain.d()}, navigating strip and card ${strip.d()}, bar ${bounds(SEARCH_ENTRY_BAR_TAG).d()}"
        val points = listOf(0.15f to 0.25f, 0.85f to 0.25f, 0.5f to 0.5f, 0.15f to 0.75f, 0.85f to 0.75f)
        val missed = mutableListOf<String>()
        for ((fx, fy) in points) {
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            if (rig.sightingsFetches.size != before + 1) missed += "($fx, $fy)"
        }
        assertEquals("while navigating, real touches on Try again that did not run the search ($where)", emptyList<String>(), missed)
        assertTrue("while navigating, the notice is covered by the navigating strip and card ($where)", notice.top >= strip.bottom - 1f || notice.bottom <= strip.top + 1f)
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}
