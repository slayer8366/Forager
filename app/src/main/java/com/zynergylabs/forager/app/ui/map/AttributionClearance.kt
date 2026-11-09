package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil

/**
 * MapLibre's attribution button ("i") moves clear of the landscape L (dispatch 2026-09-29-57, amendment
 * -262, item 1, the owner's "Move the 'i'"). The L keeps its bottom limit at the nav inset (RECORD.md:4879,
 * landscape-L P7, unchanged); the button moves, through its margins (`attributionMarginsPx`).
 *
 * What this needs and does not have: the button is MapLibre's own view, so its rectangle is worked out
 * here from the margins the app hands MapLibre, not read back. Its size is [ATTRIBUTION_BUTTON_DP], an
 * assumption (the S22-A run record measured the button at 59 px square at 450 dpi, 21 dp; rounded up),
 * so the real position and a real tap on it are device-only.
 */

/** The button's assumed side, in dp: measured 21 dp on the S22 at 450 dpi (59 px), rounded up. */
internal const val ATTRIBUTION_BUTTON_DP = 24f

/** The gap kept between the button and what it clears, in dp (the L's own gap to the search notice, `Spacing.sm`). */
internal const val ATTRIBUTION_CLEAR_GAP_DP = 8f

/**
 * The end inset (the right in a left-to-right layout, the left in a right-to-left one) the button needs
 * so that its rectangle does not intersect [keepClear], the L's measured bounds in the map's own pixels.
 * [endInsetPx] itself when there is nothing to clear ([keepClear] `null`), when the map has no size yet,
 * or when the button at [endInsetPx] and [bottomInsetPx] already clears it; otherwise the smallest larger
 * inset that puts the button [gapPx] inboard of it. [defaults] is MapLibre's own margins `[left, top, right, bottom]`
 * in px, which the insets are added to, as `attributionMarginsPx` does.
 */
internal fun attributionEndInsetClearOf(
    keepClear: Rect?,
    mapWidthPx: Int,
    mapHeightPx: Int,
    defaults: IntArray,
    bottomInsetPx: Int,
    endInsetPx: Int,
    isRtl: Boolean,
    buttonPx: Int,
    gapPx: Int,
): Int {
    if (keepClear == null || mapWidthPx <= 0 || mapHeightPx <= 0) return endInsetPx
    // The button's rectangle as MapLibre lays it out (gravity bottom-end, margins = its own defaults plus the insets).
    val bottom = (mapHeightPx - defaults[3] - bottomInsetPx).toFloat()
    val top = bottom - buttonPx
    val left: Float
    val right: Float
    if (isRtl) {
        left = (defaults[0] + endInsetPx).toFloat()
        right = left + buttonPx
    } else {
        right = (mapWidthPx - defaults[2] - endInsetPx).toFloat()
        left = right - buttonPx
    }
    if (!Rect(left, top, right, bottom).overlaps(keepClear)) return endInsetPx
    // Inboard of the L: the button's inner edge a gap short of the L's inner edge.
    val needed = if (isRtl) {
        ceil(keepClear.right + gapPx - defaults[0]).toInt()
    } else {
        ceil(mapWidthPx - defaults[2] - (keepClear.left - gapPx)).toInt()
    }
    return maxOf(endInsetPx, needed)
}
