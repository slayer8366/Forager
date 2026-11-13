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
import kotlinx.coroutines.CompletableDeferred
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
 * fullscreen (C6). The cluster is dragged to its top before the notice shows, which gives its own limit; when the notice shows
 * the cluster is held below it. The limit being above the notice's bottom is what makes the hold a hold.
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

    /** The cluster's top once dragged up with no notice showing: its own limit. The drag is made before the notice shows. */
    private fun clusterLimit(): Float {
        dragClusterToTop()
        return bounds(MAP_ICON_CLUSTER_TAG).top
    }

    private fun assertHeldBelowThePlacedNotice(limit: Float, where: String) {
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val held = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("$where: positive control: the cluster's own limit $limit is above the notice's bottom ${notice.bottom}", limit < notice.bottom - 1f)
        assertTrue("$where: the cluster ${held.d()} is not held below the notice ${notice.d()}", held.top >= notice.bottom - 1f)
    }

    /**
     * Amendment R: out of fullscreen the failure is in the search bar, so a placed notice below the navigation card is only in
     * fullscreen (RA2, the owner: "Under the card in fullscreen too"). Was: "navigating in portrait, a cluster dragged to the top
     * is held below the notice under the navigation card", out of fullscreen.
     */
    @Test
    fun `fullscreen while navigating in portrait, a cluster dragged to the top is held below the notice under the navigation card`() {
        val rig = RealSearchScreenRig(
            composeRule,
            recording = AvailabilityScreenRecording(isRecording = true),
            navigation = AvailabilityScreenNavigation(isReturning = true),
        )
        rig.setScreen()
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", !shown(SEARCH_ENTRY_BAR_TAG))
        val limit = clusterLimit()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.sightingsGate = null
        gate.complete(Unit)
        rig.settle()
        assertTrue("positive control: the notice shows", shown(SEARCH_FAILED_NOTICE_TAG))
        assertHeldBelowThePlacedNotice(limit, "fullscreen while navigating")
    }

    @Test
    fun `in fullscreen, a cluster dragged to the top is held below the notice at the top of the map`() {
        val rig = RealSearchScreenRig(composeRule)
        rig.setScreen()
        // The search's fetch is held, so fullscreen is entered and the cluster dragged with no notice, and the failure comes after.
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", !shown(SEARCH_ENTRY_BAR_TAG))
        val limit = clusterLimit()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.sightingsGate = null
        gate.complete(Unit)
        rig.settle()
        assertTrue("positive control: the notice shows", shown(SEARCH_FAILED_NOTICE_TAG))
        assertHeldBelowThePlacedNotice(limit, "fullscreen")
    }
}
