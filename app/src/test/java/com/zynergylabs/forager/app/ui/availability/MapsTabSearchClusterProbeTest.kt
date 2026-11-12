package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01, M5 (a probe, not for merge): a cluster dragged to its top while a notice already shows. What it does
 * when the notice goes, for the search-failed notice (under the bar, and placed below the card while navigating) and for the
 * search-error banner from before this dispatch, under the bar. It records, it asserts nothing: the values are printed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchClusterProbeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private fun top(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.let { "%.1f..%.1f".format(it.top, it.bottom) }

    private fun clusterTop() = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    private fun drag() {
        val h = composeRule.onNodeWithTag("map-icon-bar-minimize-handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeRule.longPressDrag((h.left + h.right) / 2, (h.top + h.bottom) / 2, 0.dp, (-800).dp)
    }

    private fun failedNoticeCase(rig: RealSearchScreenRig, name: String) {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        val notice = top(SEARCH_FAILED_NOTICE_TAG)
        drag()
        val held = clusterTop()
        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        val after = clusterTop()
        drag()
        val free = clusterTop()
        println("M5 $name: notice $notice; dragged while it shows, cluster top $held; after it goes $after; dragged again with none $free")
    }

    @Test
    fun `the search-failed notice under the bar`() = failedNoticeCase(RealSearchScreenRig(composeRule), "search-failed notice under the bar")

    @Test
    fun `the search-failed notice below the card while navigating`() = failedNoticeCase(
        RealSearchScreenRig(composeRule, recording = AvailabilityScreenRecording(isRecording = true), navigation = AvailabilityScreenNavigation(isReturning = true)),
        "search-failed notice below the navigation card",
    )

    @Test
    fun `the search-error banner under the bar`() {
        val rig = RealSearchScreenRig(composeRule)
        rig.setScreen()
        rig.tapBar()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).performTextReplacement("999")
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LONGITUDE_TAG).performTextReplacement("-121.6959")
        rig.touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG))
        if (rig.dropdownShown()) composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        rig.settle()
        val banner = top(SEARCH_NOTICE_TAG)
        drag()
        val held = clusterTop()
        rig.searchCoordinates("45.3736", "-121.6959")
        val after = clusterTop()
        drag()
        val free = clusterTop()
        println("M5 search-error banner under the bar: banner $banner; dragged while it shows, cluster top $held; after it goes $after; dragged again with none $free")
    }
}
