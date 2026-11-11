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
 * Dispatch 2026-11-11-01, H2 (a probe, not for merge): the search-error banner that was there before this dispatch
 * ([SearchNotice]), while navigating in portrait. Read from code: it sits under the bar, where the navigation card hangs, so it
 * would be covered. Not moved (H2: the owner's to rule); this says what a test shows. Only the ranked list fails, so the banner
 * shows its own line (D3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchBannerNavigatingTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(
        composeRule,
        recording = AvailabilityScreenRecording(isRecording = true),
        navigation = AvailabilityScreenNavigation(isReturning = true),
    )

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    @Test
    fun `while navigating in portrait the search-error banner is not covered by the navigation card`() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: the list failed", LIST_SEARCH_FAILED_MESSAGE, rig.viewModel.uiState.value.errorMessage)
        assertTrue("positive control: the banner is composed", composeRule.onAllNodes(hasTestTag(SEARCH_NOTICE_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val banner = bounds(SEARCH_NOTICE_TAG)
        val card = bounds("compass-elevation-strip")
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        assertTrue("while navigating, the banner ${banner.d()} is covered by the navigation card ${card.d()} (bar ${bar.d()})", !banner.overlaps(card))
    }
}
