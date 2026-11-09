package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/*
 * A map bubble kept above the route card (dispatch 2026-11-06-05, PR #507; RECORD -2187). The owner, to a waypoint's bubble drawn
 * under the expanded route card: "Kept above the card" (the bubble stays above the card's top edge, the way it stays below the top
 * strip), and "Make the change this release actually".
 *
 * The strip is kept clear by the bubble's top limit (`minY`, compassStripClearance's measurement); the card is kept clear the same way
 * from below: its top is the bottom of the box the bubble is clamped into (`bubblePlacement`'s `maxHeight`), so the same flip and the
 * same clamp decide where the bubble goes. Not a new placement rule. Where the anchor is itself under the card, the clamp does what it
 * does at the strip when "Flip below" does not fit: the card sits at the limit and its tail runs on to the anchor, under the card.
 *
 * The strip is full width, so its limit needs no sideways test. The card is not: it applies only where the bubble's columns meet the
 * card's ([left]..[right]), so a bubble beside the card is not pushed up for chrome it does not touch. What is passed is the part of
 * the card that is drawn (the whole card at rest, peek or expanded; its 24 dp strip once tucked; RouteCardAttributionCover.kt), so a
 * tucked card limits only a bubble that would overlap its strip. Kept out of MapBubbles.kt (.claude/skills/modular-code/SKILL.md).
 */

/** A lower limit for a bubble, in the bubble layer's own px: the box ends at [top] for a bubble whose columns meet [left]..[right]. */
data class BubbleFloor(val top: Int, val left: Int, val right: Int)

/** The [BubbleFloor] of chrome at [coverInRoot], for a bubble layer whose top-left is at ([boxLeftInRootPx], [boxTopInRootPx]) in root. */
fun bubbleFloorOf(coverInRoot: Rect?, boxLeftInRootPx: Float, boxTopInRootPx: Float): BubbleFloor? {
    if (coverInRoot == null || coverInRoot.isEmpty) return null
    return BubbleFloor(
        top = (coverInRoot.top - boxTopInRootPx).roundToInt(),
        left = (coverInRoot.left - boxLeftInRootPx).roundToInt(),
        right = (coverInRoot.right - boxLeftInRootPx).roundToInt(),
    )
}

/** The bottom of the box a bubble at [x], [width] px wide, is clamped into: [maxHeight], or [floor]'s top where the two overlap sideways. */
internal fun bubbleBottomLimit(maxHeight: Int, floor: BubbleFloor?, x: Int, width: Int): Int {
    if (floor == null || x >= floor.right || x + width <= floor.left) return maxHeight
    return minOf(maxHeight, floor.top)
}
