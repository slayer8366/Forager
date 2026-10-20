package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The layout limits [CompactMapTab]'s children share, carried as one object so a spacing fix touches this file and the one child
 * that draws it (UI split Phase 2.3, dispatch 2026-10-19-35; survey `docs/audits/2026-10-18-ui-split-survey.md` on branch
 * `ui-split-survey`, section 6.3). The cluster's own bounds and measures are not here: they already live in
 * [MapIconClusterState], which every child reads.
 *
 * **A view over State objects, never a copy** (the pattern of [com.zynergylabs.forager.app.ui.map.SightingsMapState]).
 * [CompactMapTab] still remembers the three measured sizes where it always did, in its map branch, with the same start value and in
 * the same order, and remembers this holder unkeyed straight after them, in the same branch, so the holder lives exactly as long as
 * the States it wraps. Each read here is a getter on that State and each write a setter on it. A value copied in would be stale the
 * moment the State changed: the trap the frame's extraction found (`docs/audits/2026-09-26-split-stage-g-completion-report.md`).
 *
 * **Where the reads happen is unchanged.** The body reads the strip's height where it always did and hands the children plain
 * values; only [CompactMapTopStrip] (which writes all three) and [CompactMapNavigationDisplay] (which reads the landscape two) take
 * the holder, and each reads or writes through it at the same place it used the State before.
 */
internal class CompactMapLayoutState(
    private val compassStripHeightState: MutableIntState,
    private val landscapeStripHeightState: MutableIntState,
    private val landscapeStripWidthState: MutableIntState,
) {
    /**
     * The portrait strip's measured height, written by [CompactMapTopStrip] while not navigating (0 while navigating and before
     * its first layout; 0 in a landscape window). Not the one-line clearance, which is one text line's height.
     */
    var compassStripHeightPx: Int
        get() = compassStripHeightState.intValue
        set(value) { compassStripHeightState.intValue = value }

    /** RECORD -729: the strip's measured height in a landscape window (the search bar is given it); 0 elsewhere. */
    var landscapeStripHeightPx: Int
        get() = landscapeStripHeightState.intValue
        set(value) { landscapeStripHeightState.intValue = value }

    /** RECORD -732: and its measured width (the scaffold gives the bar the rest of the room); 0 elsewhere. */
    var landscapeStripWidthPx: Int
        get() = landscapeStripWidthState.intValue
        set(value) { landscapeStripWidthState.intValue = value }

    // The limits, worked out from the sizes above and from inputs that change every composition. Those inputs are arguments and
    // never stored: the one-line clearance is remembered under the strip's text style and density, and the insets, the search
    // bar's edge and the density come with each composition. [CompactMapTab] calls each of these where it always worked the limit
    // out, so every state read stays in its body and the children are handed values, as before.

    /**
     * RECORD -709 (the owner: "Follow the strip's real height (Recommended)"): what sits below the strip in portrait (the
     * observation bubble's top, the icon bar's drag limit, the taxon and journal chips; the search dropdown, in the
     * scaffold, through searchBarSlot) clears the strip's measured height, not one text line. Back by's three-dot button
     * (dispatch 2026-09-28-645) gave the strip a 36 dp floor, twice that line, and its sundown and Back by lines grow it
     * too. The one-line clearance stays the floor, and is what is used while the strip is not measured: before its first
     * layout, while navigating (the display replaces it), and in a landscape window, where the strip sits in the other
     * corner and is not measured. The comment in [CompactMapTab] that begins 'A real clearance for "below the compass strip,"'
     * (on the one-line clearance measured with `compassStripTextMeasurer`) records a regression from reading a measured height
     * back for the bubble's minY; the strip's measured height was already read in that body for the search notice
     * (searchBarSlot), and the test that caught that regression ("tapping elsewhere on the map dismisses the observation
     * bubble") is @Ignore'd for an unrelated harness reason, so whether the regression returns is a device item.
     */
    fun stripBottomClearance(oneLineClearance: Dp, density: Density): Dp =
        with(density) { compassStripHeightPx.toDp() }.coerceAtLeast(oneLineClearance)

    /** The observation bubble's top: below the search bar and the strip. */
    fun bubbleMinY(topInset: Dp, stripBottomClearance: Dp): Dp = topInset + stripBottomClearance

    /**
     * The icon cluster's top limit, in pixels: in a landscape window the search bar's settled bottom edge (not the animated top
     * inset; see `searchBarBottom` on [CompactMapTab]), otherwise below the search bar and the strip.
     */
    fun clusterTopLimitPx(landscapeCluster: Boolean, searchBarBottom: Dp, topInset: Dp, stripBottomClearance: Dp, density: Density): Float =
        with(density) { (if (landscapeCluster) searchBarBottom else topInset + stripBottomClearance).toPx() }

    /** Owner's ruling (b), continuation 2026-09-28-172: 8 + the L's measured width + 8, as the legend makes room (legendEndPadding). */
    fun noticeInsetDp(clusterWidthPx: Float, density: Density): Dp =
        MAP_ICON_BAR_EDGE_INSET + with(density) { clusterWidthPx.toDp() } + Spacing.sm

    /**
     * Owner's ruling (b), continuation 2026-09-28-172: in the landscape L, a notice on the same side as the L is inset on that
     * side by [noticeInsetDp], as the legend makes room (legendEndPadding). Elsewhere none. Takes the cluster itself, not its side,
     * so the side is read only in a landscape window, as before: a side passed in would be read up front, and the tab's body would
     * then read it in portrait too.
     */
    fun searchNoticeInset(landscapeCluster: Boolean, cluster: MapIconClusterState, punchHoleEdge: ScreenEdge?, insetDp: Dp): SearchNoticeInset {
        val noticeAnchoredLeft = punchHoleEdge == ScreenEdge.Left
        return when {
            !landscapeCluster || cluster.isOnLeftSide != noticeAnchoredLeft -> SearchNoticeInset.None
            cluster.isOnLeftSide -> SearchNoticeInset(left = insetDp)
            else -> SearchNoticeInset(right = insetDp)
        }
    }
}
