package com.zynergylabs.forager.app.ui.availability

/**
 * Dispatch 2026-11-06-01, Amendment 1, J3 step 3 and J4 (RECORD -2183). The owner, J3: "Own line, card moves up", the step reading
 * "landscape at font 2.0 with an evening line: its own line, and the card moves up just enough to clear the walker's dot; the evening
 * line stays on the card"; J4: "Follow the largest rule" (fonts 1.5 and 1.8 in landscape the same), and "Round and outline" (the card,
 * once moved up off the screen's bottom edge, gets rounded bottom corners and the outline all round).
 *
 * Where the landscape navigation card sits, as a pure decision:
 * - [NavigationCardLift.OnBottomEdge]: where it is today (RECORD -953), whenever this is not J3 step 3's case, and in that case where
 *   the card with its evening line already clears the walker's dot (and Reset north) from the bottom edge. "Just enough to clear" is
 *   read as no move at all where nothing needs clearing: measured, at font 1.5 the card with its evening line sits 4.3 dp (640 and
 *   780 x 360) and 13.3 dp (823 x 384) above the dot's top from the bottom edge (probe run 42402017914).
 * - [NavigationCardLift.Up]: its bottom at the dot's top, the least move that clears the dot from below, so its evening line stays.
 * - [NavigationCardLift.NoRoom]: it would need to move, but moved up its top would pass the compass strip's bottom, or it would cover
 *   the L where the L shares its columns. Then RECORD -955 holds as before: the card stays on the bottom edge and the evening line
 *   leaves it. The host logs the reason when it fires (CLAUDE.md: no unlogged fallback).
 *
 * Every input is fixed while walking (the card's height with its evening line is built from parts that line does not change, as
 * RECORD -955's own decision is), so the card does not move up and down as it is drawn.
 */
internal sealed interface NavigationCardLift {
    data object OnBottomEdge : NavigationCardLift

    /** Moved up by [px] from the bottom edge. */
    data class Up(val px: Float) : NavigationCardLift

    data class NoRoom(val reason: String) : NavigationCardLift
}

internal fun navigationCardLift(
    /** J3 step 3 and J4: landscape at font 1.5 or more, with an evening line the card has to show and not stepping aside (D42). */
    liftCase: Boolean,
    /** The card's height with its evening line, as RECORD -955 reckons it; NaN before it is measured or with no evening line. */
    heightWithEveningPx: Float,
    /** The card's bottom, in the root, on the screen's bottom edge. */
    bottomEdgePx: Float,
    /** RECORD -955's limit for the card's top on the bottom edge: the dot's bottom, or Reset north's where the L shares its columns. */
    topLimitPx: Float?,
    /** The walker's dot's top, in the root. */
    dotTopPx: Float?,
    /** The compass strip's bottom, in the root; `null` where it is not known. */
    stripBottomPx: Float?,
    /** The L's top and bottom, in the root, where it shares the card's columns; `null` otherwise. */
    lTopPx: Float?,
    lBottomPx: Float?,
): NavigationCardLift {
    // Tests first (stub): the card never moves. The forward commit decides.
    return NavigationCardLift.OnBottomEdge
}

/** RECORD -955: the walker's dot's centre, in the root, where the navigation view puts it; `null` before the map is measured. */
internal fun walkerDotCentreInRootPx(cluster: MapIconClusterState): Float? {
    val mapHeight = cluster.mapContentBoxHeightPx
    if (mapHeight <= 0f) return null
    return cluster.mapContentBoxTopInRootPx + (mapHeight + mapHeight * com.zynergylabs.forager.app.ui.map.NAVIGATION_VIEW_TOP_PADDING_FRACTION.toFloat()) / 2f
}
