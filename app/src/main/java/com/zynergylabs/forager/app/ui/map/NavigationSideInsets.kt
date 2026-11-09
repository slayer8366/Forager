package com.zynergylabs.forager.app.ui.map

/**
 * D29 (dispatch 2026-10-30-08, RECORD -1979, -1980; the owner: "Keep the dot clear of the card (Recommended)", whose option read
 * "While the route card is expanded, the navigation view shifts so your dot sits in the open map beside the card, and moves back when
 * the card collapses"): the side padding the navigation view adds, in px, on top of its top padding
 * ([NAVIGATION_VIEW_TOP_PADDING_FRACTION]). The walker sits at the centre of the padded area, so a padding on each side puts them in
 * the middle of what is left between the two. [NONE] everywhere but the landscape navigation view with the route card expanded.
 *
 * J8 (dispatch 2026-11-06-01 Amendment 1, RECORD -2183; the owner: "Map moves dot up (Recommended)", "(a) Landscape only"): and
 * [bottomPx], the bottom padding that keeps the walker's dot just above the card on top of the landscape navigation card where that
 * card would cover it; 0 elsewhere. The centre of the padded area rises by half of it.
 */
data class NavigationSideInsets(val leftPx: Double, val rightPx: Double, val bottomPx: Double = 0.0) {
    companion object {
        val NONE = NavigationSideInsets(0.0, 0.0)
    }
}

/** The navigation view's whole padding (left, top, right, bottom, in px) for a map [mapHeightPx] tall, with [side] added. */
fun navigationViewPaddingPx(mapHeightPx: Int, side: NavigationSideInsets): DoubleArray =
    doubleArrayOf(side.leftPx, navigationViewTopPaddingPx(mapHeightPx), side.rightPx, side.bottomPx)
