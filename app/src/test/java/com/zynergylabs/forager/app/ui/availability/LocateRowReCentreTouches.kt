package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** The Maps tab icon bar's locate row, by its content description. */
internal const val LOCATE_ROW = "Center on my location"

/** The icon bar's minimise handle ("Hide map controls", `MapIconBarHandles.kt`). */
internal const val LOCATE_ROW_MINIMIZE_HANDLE_TAG = "map-icon-bar-minimize-handle"

/**
 * Dispatch 2026-11-06-04, Amendment 2 (RECORD -2186): where real touches on the locate row are the row's own. In the landscape L the
 * minimise handle's tap box (20 dp wide, one row tall, centred on the locate row: owner's ruling (c), continuation 2026-09-28-172) is
 * composed after the bar, straddles the bar's outer edge and wins its outer sliver (12 dp of the 48 dp row, from the code: the handle
 * box from the cluster's edge, the bar 8 dp in). A touch there hides the controls; it is the handle's, and was before this amendment.
 * So the five samples, as fractions of the row [row]: its centre; 8% in from its top, its bottom and its inboard side; and, on the
 * outer side, 2 dp inboard of the handle's box [handle] where it overlaps the row, 8% in where it does not.
 *
 * Found on run 42344323056: the first version of `LocateRingReCenterTest` touched 8% in from both sides, and the outer one (3.8 dp in,
 * under the handle's box) did not reach the row.
 */
internal fun locateRowTouchFractions(row: DpRect, handle: DpRect?): List<Pair<Float, Float>> {
    val width = (row.right - row.left).value
    val overlaps = handle != null && handle.left < row.right && row.left < handle.right && handle.top < row.bottom && row.top < handle.bottom
    val handleOnLeft = handle != null && (handle.left + handle.right) / 2 < (row.left + row.right) / 2
    val (outer, inner) = when {
        !overlaps -> 0.08f to 0.92f
        handleOnLeft -> ((handle!!.right - row.left).value + 2f) / width to 0.92f
        else -> ((handle!!.left - row.left).value - 2f) / width to 0.08f
    }
    return listOf(0.5f to 0.5f, outer to 0.5f, inner to 0.5f, 0.5f to 0.08f, 0.5f to 0.92f)
}

/**
 * Real touches at [locateRowTouchFractions] on the locate row, each bringing the navigation view back as Re-center does (its
 * `restoreRequestId`, [restoreId], which only Re-center and locate move). [before] runs before each touch (the caller's check that
 * the ring and no pill are there), [after] after it (the caller's check of what follows, then its move away, so the next touch starts
 * from the same state).
 */
internal fun RouteRule.assertLocateRowReCentres(
    where: String,
    restoreId: () -> Int,
    settle: () -> Unit,
    before: (String) -> Unit = {},
    after: (String) -> Unit,
) {
    val row = onNodeWithContentDescription(LOCATE_ROW).getUnclippedBoundsInRoot()
    val scale = this.density.density
    val handle = onAllNodesWithTag(LOCATE_ROW_MINIMIZE_HANDLE_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let {
        DpRect((it.left / scale).dp, (it.top / scale).dp, (it.right / scale).dp, (it.bottom / scale).dp)
    }
    val samples = locateRowTouchFractions(row, handle)
    println("LOCATE $where: row ${row.left.value}..${row.right.value} x ${row.top.value}..${row.bottom.value}, handle ${handle?.let { "${it.left.value}..${it.right.value} x ${it.top.value}..${it.bottom.value}" } ?: "none"}, samples $samples")
    samples.forEach { (fx, fy) ->
        assertTrue("$where: the sample ($fx, $fy) lies inside the locate row", fx in 0f..1f && fy in 0f..1f)
        val at = "$where, a touch at ($fx, $fy)"
        before(at)
        val was = restoreId()
        onNodeWithContentDescription(LOCATE_ROW).performTouchInput { click(Offset(width * fx, height * fy)) }
        settle()
        assertEquals("$at on the locate row brings the navigation view back", was + 1, restoreId())
        after(at)
    }
}
