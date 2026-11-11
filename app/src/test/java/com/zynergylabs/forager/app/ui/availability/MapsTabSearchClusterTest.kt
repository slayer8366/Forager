package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01, I2 (Sorrel's second review): the icon cluster's top limit follows the placed search-failed notice,
 * where it is placed rather than in the search bar's column: while navigating in portrait (below the navigation card, G1) and in
 * fullscreen (C6). The cluster is dragged to its top with the notice showing, so it is held below the notice; then the search
 * works, the notice goes, and the cluster is where it was dragged to: above the notice's bottom, which is what makes the hold
 * a hold and not the cluster's own limit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchClusterTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun dragClusterToTop() {
        val handle = composeRule.onNodeWithTag("map-icon-bar-minimize-handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 0.dp, (-800).dp)
    }

    private fun assertHeldBelowThePlacedNotice(rig: RealSearchScreenRig, where: String) {
        dragClusterToTop()
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val held = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("$where: the cluster ${held.d()} is not held below the notice ${notice.d()}", held.top >= notice.bottom - 1f)

        rig.sightingsFailure = null
        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertTrue("$where: positive control: the notice went when the search worked", !shown(SEARCH_FAILED_NOTICE_TAG))
        val free = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("$where: positive control: dragged to the top with no notice, the cluster ${free.d()} rises above the notice's bottom ${notice.bottom}", free.top < notice.bottom - 1f)
    }

    @Test
    fun `navigating in portrait, a cluster dragged to the top is held below the notice under the navigation card`() {
        val rig = RealSearchScreenRig(
            composeRule,
            recording = AvailabilityScreenRecording(isRecording = true),
            navigation = AvailabilityScreenNavigation(isReturning = true),
        )
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertHeldBelowThePlacedNotice(rig, "navigating")
    }

    @Test
    fun `in fullscreen, a cluster dragged to the top is held below the notice at the top of the map`() {
        val rig = RealSearchScreenRig(composeRule)
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", !shown(SEARCH_ENTRY_BAR_TAG))
        assertHeldBelowThePlacedNotice(rig, "fullscreen")
    }
}
