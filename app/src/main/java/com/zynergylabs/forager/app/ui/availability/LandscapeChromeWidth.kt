package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.log.ScreenEdge

/**
 * How wide the compass strip and the navigation display may be in the short-landscape Map tab without reaching the
 * search bar (dispatch 2026-09-28-685, Amendment 1, RECORD -694; the owner: "Stay beside the search bar, '…'
 * (Recommended)"). On the S22 at font scale 2.0 the strip (content-width) and the display (full width up to
 * `LANDSCAPE_HUD_MAX_WIDTH` (removed 2026-10-20, RECORD -1431)) grew from the rail's side across the centre line and over the bar, and nothing bounded
 * them by it. Now they stay in the room between the rail-side controls edge and the bar's end; text that does not fit
 * there ends in "…" on one line, so nothing gets taller and the central third's clearance is unchanged.
 *
 * The bar's end is worked out the way the scaffold places the bar (`AvailabilityCompactScaffold`'s
 * `landscapeSearchWidth`): from the punch-hole-side controls edge, `min(LANDSCAPE_SEARCH_MAX_WIDTH, half the window
 * minus that edge's inset minus LANDSCAPE_SEARCH_CENTRE_GAP)`. Recomputed from the same constants rather than read from
 * the bar's measured size, because reading a measured position back through state for placement caused a bisected
 * regression on this screen (`AvailabilityCompactMapUi`'s note on `onGloballyPositioned`).
 *
 * The rejected alternative, placing both below the bar's measured height, would push the display into the window's
 * central third, whose 1.5 dp clearance on the S22 is the owner's to change.
 */
internal fun railSideRoomBesideSearchBar(windowWidth: Dp, searchSideInset: Dp, railSideInset: Dp): Dp {
    val barWidth = minOf(LANDSCAPE_SEARCH_MAX_WIDTH, windowWidth / 2 - searchSideInset - LANDSCAPE_SEARCH_CENTRE_GAP).coerceAtLeast(0.dp)
    return (windowWidth - railSideInset - searchSideInset - barWidth).coerceAtLeast(0.dp)
}

/**
 * Caps the width of what follows at [railSideRoomBesideSearchBar]. Goes **after** `padding(controlsPadding)` on a child
 * of the map's full-size Box, so the width it is offered is the window less both insets, from which the window is
 * recovered. With the search bar on the same side as the rail (no such phone is known), or no rail, [railEdge] or
 * [searchEdge] tell it so and it changes nothing.
 */
internal fun Modifier.besideLandscapeSearchBar(
    railEdge: ScreenEdge?,
    searchEdge: ScreenEdge?,
    controlsPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): Modifier {
    if (railEdge == null || searchEdge == null || railEdge == searchEdge) return this
    val left = controlsPadding.calculateLeftPadding(layoutDirection)
    val right = controlsPadding.calculateRightPadding(layoutDirection)
    val (searchSideInset, railSideInset) = if (searchEdge == ScreenEdge.Left) left to right else right to left
    return layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val window = constraints.maxWidth.toDp() + searchSideInset + railSideInset
        val room = railSideRoomBesideSearchBar(window, searchSideInset, railSideInset).roundToPx()
        val maxWidth = minOf(constraints.maxWidth, room)
        val placeable = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, maxWidth), maxWidth = maxWidth))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * RECORD -729 (dispatch 2026-09-28-729), as revised by RECORD -732. The owner, -729: "Landscape mode strip can extend to meet
 * the search bar. The search bar height can change to meet the height of the strip. The two can meet at direct center, and
 * they can be split by a simple vertical line between the two". At -729's build the centre left the strip narrower than the
 * room it already had, so the heading still dropped; asked, the owner, -732: "Join moves to fit the strip (Recommended)".
 *
 * So in a short landscape window, not navigating, the strip is as wide as its readouts need whole (heading, altitude and
 * coordinates, with its needle, padding and three-dot button: [LandscapeStripNeed]), and the search bar takes the rest, never
 * narrower than its own floor ([rememberLandscapeBarFloorPx]: its field's resting text whole, and Clear). Where both cannot
 * fit, the bar keeps its floor and the strip narrows, and readoutsFitBeside decides what it drops; the strip never narrows
 * below the coordinates alone (they never drop), and there the bar gives way. While navigating the display keeps
 * [besideLandscapeSearchBar].
 *
 * Goes **after** `padding(controlsPadding)` on a child aligned to the rail-side top corner, so the width it is offered is the
 * room between the two controls edges. Worked out from text measurements, never read back from a layout. The strip's
 * resulting width (measured on it) is what the scaffold gives the bar as its rail-side padding, so the two meet on the same
 * pixel whatever the rounding. With no rail, or the bar on the rail's side, it changes nothing.
 */
internal fun Modifier.landscapeStripFit(
    railEdge: ScreenEdge?,
    searchEdge: ScreenEdge?,
    need: LandscapeStripNeed,
    barFloorPx: Int,
): Modifier {
    if (railEdge == null || searchEdge == null || railEdge == searchEdge) return this
    return layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val width = landscapeStripWidthPx(constraints.maxWidth, need, barFloorPx)
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * RECORD -732: the strip's width in a room of [roomPx] (the window less both controls insets): what its readouts need whole,
 * else what the bar's floor leaves, but never less than the coordinates alone need; never more than the room.
 */
internal fun landscapeStripWidthPx(roomPx: Int, need: LandscapeStripNeed, barFloorPx: Int): Int =
    minOf(need.fullPx, maxOf(need.coordinatesOnlyPx, roomPx - barFloorPx)).coerceIn(0, maxOf(roomPx, 0))

/** RECORD -732: what the landscape strip needs, in pixels: everything whole, and the coordinates alone. */
internal data class LandscapeStripNeed(val fullPx: Int, val coordinatesOnlyPx: Int)

/** RECORD -729, -732: the 1 dp line where the search bar and the strip meet in a short landscape window. */
internal const val LANDSCAPE_BAR_STRIP_LINE_TAG = "landscape-bar-strip-line"
