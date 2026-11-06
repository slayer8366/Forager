package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.model.DistanceUnit

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): where the kind word ("by trail", "straight") sits on the navigation card's left part. The
 * owner: "For navigation cards have the 2.2 mi on the left half, and then move the "by trail" from the bottom to the top right half,
 * on the same line as 2.2 mi", "And do the same for landscape", "Keep the text size the same size"; then "Meet at the middle
 * (Recommended)": the distance ends at the left part's middle and the kind starts there, on one baseline. The pure half: every
 * choice is a function of measured widths, so a wrong step is a pinned-literal failure (`NavigationCardTopLineFitTest`).
 *
 * Where the two can't share the line at today's sizes, the owner: "Option 1 but stack by on top of trail first, then just shorten
 * it to trail, and then drop it down". So, in order:
 * 1. [KindPlace.BESIDE]: the whole kind in the right half, beside the distance;
 * 2. [KindPlace.STACKED]: "by" over "trail" in the right half;
 * 3. [KindPlace.SHORT]: "trail" alone;
 * 4. [KindPlace.OWN_LINE]: the kind on its own line just under the distance, above the turn line.
 * "straight" is one word, so it cannot stack or shorten and goes from 1 to 4 (the planner's reading, told to the owner).
 * No text is shrunk, rescaled or fitted ("Keep the text size the same size"): each step changes where the kind goes, never its size.
 *
 * The step is chosen for the widest figure the card could show for this kind in this unit ([longestFigures]), not the figure on
 * screen, so it does not change while the number counts down on a walk (RECORD -2181: "Each step is chosen for the longest
 * distance the card could show, so the layout doesn't jump while walking").
 */
internal enum class KindPlace {
    /** No kind to place: "Arrived", the dash, "within 16 ft". The word stands where the figure stands, in the left half. */
    NONE,
    BESIDE,
    STACKED,
    SHORT,
    OWN_LINE,
}

/** The two words "by trail" stacks into, and the one it shortens to (the owner: "stack by on top of trail", "shorten it to trail"). */
internal const val KIND_STACK_TOP = "by"
internal const val KIND_SHORT = "trail"

/** The kind's stacked and short forms, `null` where it has none ("straight" is one word). */
internal fun kindStackOf(kind: String): Pair<String, String>? = if (kind == DistanceKind.BY_TRAIL.words) KIND_STACK_TOP to KIND_SHORT else null
internal fun kindShortOf(kind: String): String? = if (kind == DistanceKind.BY_TRAIL.words) KIND_SHORT else null

/**
 * The widest figures [kind] can show in [unit], in the card's tabular figures, where every digit is as wide as any other, so one
 * string stands for every figure of its shape. Read from `DistanceUnit.kt`'s formatters:
 * - the way along trails is [com.zynergylabs.forager.app.domain.model.formatDistanceMeters]: feet below a quarter mile (at most
 *   four digits: 0.2499 mi rounds to "1320 ft"), metres below a kilometre (999.6 m rounds to "1000 m"), tenths above;
 * - the straight line is [com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy] outside the fix's circle: the same,
 *   with "≈ " before the feet or metres wherever the fix's accuracy rounds them (at most four digits again: "≈ 1500 ft").
 * Tenths are counted to two whole digits ("88.8 mi"), a walk's distances. A figure wider still (a trip in another area,
 * "123.4 mi") is measured as itself where the fit is decided ([TopLineWidths.figurePx]), so it can never outgrow its step.
 */
internal fun longestFigures(kind: DistanceKind, unit: DistanceUnit): List<String> {
    val about = if (kind == DistanceKind.STRAIGHT) "≈ " else ""
    return when (unit) {
        DistanceUnit.MILES -> listOf("${about}8888 ft", "88.8 mi")
        DistanceUnit.KILOMETERS -> listOf("${about}8888 m", "88.8 km")
    }
}

/** The kind [text] names, `null` for any other text. */
internal fun distanceKindOf(text: String?): DistanceKind? = DistanceKind.entries.firstOrNull { it.words == text }

/**
 * The left part's two halves either side of the gap between the figure and the kind: the figure ends at [figureEndPx], the kind
 * starts at [kindStartPx]. The gap is a word's space, centred on the middle, so "2.2 mi by trail" reads as one phrase. Shared by
 * the fit and the drawing, so the room one counts is the room the other draws in.
 */
internal class TopLineHalves(widthPx: Int, gapPx: Int) {
    private val middle = widthPx / 2
    val figureEndPx = middle - gapPx / 2
    val kindStartPx = middle + (gapPx - gapPx / 2)
    val leftRoomPx = figureEndPx
    val rightRoomPx = widthPx - kindStartPx
}

/** The measured widths the top line is decided on, in pixels. */
internal data class TopLineWidths(
    /** The left part's width as [InfoParts] lays it out ([leftPartWidthPx]). */
    val leftPartPx: Int,
    val gapPx: Int,
    /** The widest of [longestFigures] and the figure on screen. */
    val figurePx: Int,
    /** The whole kind; `null` with none. */
    val kindPx: Int?,
    /** "by" over "trail": the wider of the two; `null` where the kind cannot stack. */
    val stackedPx: Int? = null,
    /** "trail"; `null` where the kind cannot shorten. */
    val shortPx: Int? = null,
)

internal fun kindPlace(w: TopLineWidths): KindPlace {
    val kind = w.kindPx ?: return KindPlace.NONE
    val halves = TopLineHalves(w.leftPartPx, w.gapPx)
    // The kind's steps make the kind narrower; none of them makes room for a figure wider than its half.
    if (w.figurePx > halves.leftRoomPx) return KindPlace.OWN_LINE
    val room = halves.rightRoomPx
    return when {
        kind <= room -> KindPlace.BESIDE
        w.stackedPx != null && w.stackedPx <= room -> KindPlace.STACKED
        w.shortPx != null && w.shortPx <= room -> KindPlace.SHORT
        else -> KindPlace.OWN_LINE
    }
}

/**
 * The left part's width in [totalPx] (the two parts and the divider): what is left once the right part keeps its width. Where the
 * fit overflows, the left part stays whole and the right part takes what is left (RECORD -941). Moved here unchanged from
 * [InfoParts]'s measure block, so the top line is decided on the width the left part is drawn at.
 */
internal fun leftPartWidthPx(totalPx: Int, dividerPx: Int, fit: NavigationInfoFit, reserveRight: Boolean): Int {
    val wantRight = if (reserveRight) fit.rightPx else 0
    val rightWidth = if (fit.overflows) minOf(wantRight, maxOf(0, totalPx - dividerPx - fit.leftPx)) else wantRight
    return maxOf(0, totalPx - dividerPx - rightWidth)
}
