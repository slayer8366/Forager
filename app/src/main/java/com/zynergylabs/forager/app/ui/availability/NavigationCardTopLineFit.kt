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
 * Where the longest distance doesn't fit left of the middle, the owner: "Shift the middle a little (Recommended)" (the option
 * read: "the meeting point moves right just enough (about 4 to 20 dp) and stays put for the whole walk"). See [TopLineMeeting].
 *
 * Where the kind still doesn't fit right of the meeting point, the owner: "Option 1 but stack by on top of trail first, then just
 * shorten it to trail, and then drop it down". So, in order:
 * 1. [KindPlace.BESIDE]: the whole kind right of the meeting point, beside the distance;
 * 2. [KindPlace.STACKED]: "by" over "trail" (the owner: "On the distance's line (Recommended)");
 * 3. [KindPlace.SHORT]: "trail" alone;
 * 4. [KindPlace.OWN_LINE]: the kind on its own line just under the distance, above the turn line (the owner: "At the middle
 *    (Recommended)": it starts at the meeting point, or as near it as fits).
 * "straight" is one word, so it cannot stack or shorten and goes from 1 to 4 (the planner's reading, told to the owner).
 * [KindPlace.SHORT] is kept in the owner's order but is never reached: the stack is as wide as its wider word, "trail", which is
 * exactly what "trail" alone needs, so wherever "trail" would fit the stack fits first (accepted by the planner, 2026-11-06).
 * No text is shrunk, rescaled or fitted ("Keep the text size the same size"): each step changes where the kind goes, never its size.
 *
 * The step is chosen for the worst case the card could show on this walk, so it doesn't change while walking (RECORD -2181,
 * Phase 1's option the owner chose: "It's decided for the longest distance the card could show, so it doesn't jump between lines
 * as the number changes while walking"):
 * - for the widest figure the card could show for this kind in this unit ([longestFigures]), not the figure on screen;
 * - and on the left part as drawn, one width for the whole walk since Amendment 1, G3 and H1 ([topLinePlace]). Until then it was
 *   decided for the narrowest the left part could be across every heading and turn word (the planner's call of 2026-11-06), since
 *   the left part then followed the turn row, which changed as the walker turned.
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
 * "123.4 mi") is measured as itself where the fit is decided ([topLinePlace]), so it can never outgrow its step.
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
 * Where the figure ends and the kind starts on a left part [widthPx] wide. The owner: "Meet at the middle (Recommended)": the
 * figure ends half a word's space ([gapPx], Spacing.xs) left of the middle and the kind starts as far right of it, so "2.2 mi by
 * trail" reads as one phrase. Then "Shift the middle a little (Recommended)": where [longestFigurePx] doesn't fit left of that
 * point, the meeting point moves right just far enough for it, so the widest figure the walk could show ends there too and the
 * point does not move as the number changes. It never moves past the left part's own right edge (a figure that wide leaves the
 * kind no room beside it, so the kind takes its own line). `null` [longestFigurePx], with no kind: the middle, unmoved. A word with
 * no kind is not placed on it: [CardTopLine] centres it (Amendment 1, B3 and B4).
 */
internal class TopLineMeeting(widthPx: Int, gapPx: Int, longestFigurePx: Int?) {
    private val middleEndPx = widthPx / 2 - gapPx / 2
    val figureEndPx: Int = minOf(widthPx, maxOf(middleEndPx, longestFigurePx ?: 0))
    val kindStartPx: Int = figureEndPx + gapPx
    /** How far right of the middle the meeting point moved: 0 where the longest figure fits left of it. */
    val shiftPx: Int = figureEndPx - middleEndPx
    /** What the kind has beside the figure, from where it starts to the left part's right edge. */
    val rightRoomPx: Int = widthPx - kindStartPx
}

/**
 * Amendment 1, H15 (the owner: "Centre them together"): where the distance's window ends on a left part [widthPx] wide, beside or
 * stacked. The window is the widest distance exactly ([longestFigurePx]); it, the word gap ([gapPx]) and the kind as drawn
 * ([kindPx]: the whole kind, or the stack) are one block centred in the left part, never starting left of its left edge. The
 * distance ends here and the kind starts [gapPx] after it, so neither moves as the distance counts down.
 */
internal fun topLineWindowEndPx(widthPx: Int, gapPx: Int, longestFigurePx: Int, kindPx: Int): Int =
    maxOf(0, (widthPx - (longestFigurePx + gapPx + kindPx)) / 2) + longestFigurePx

/** The measured widths the top line is decided on, in pixels. */
internal data class TopLineWidths(
    /** The left part's width the step is decided for: as drawn, one width for the walk ([topLinePlace]). */
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
    val room = TopLineMeeting(w.leftPartPx, w.gapPx, w.figurePx).rightRoomPx
    return when {
        kind <= room -> KindPlace.BESIDE
        w.stackedPx != null && w.stackedPx <= room -> KindPlace.STACKED
        w.shortPx != null && w.shortPx <= room -> KindPlace.SHORT
        else -> KindPlace.OWN_LINE
    }
}

/**
 * What the top line is drawn with: the kind's step, and the widest figure the meeting point is set for ([TopLineMeeting]), `null`
 * with no kind. [decidedForPx] is the left part's width the step was decided for.
 */
internal data class TopLinePlace(val kind: KindPlace, val longestFigurePx: Int?, val decidedForPx: Int? = null) {
    companion object {
        val NONE = TopLinePlace(KindPlace.NONE, null)
    }
}

/**
 * The left part's width in [totalPx] (the two parts and the divider): what is left once the right part keeps its width. Where the
 * fit overflows, the left part stays whole and the right part takes what is left (RECORD -941). Moved here unchanged from
 * [InfoParts]'s measure block, so the top line is decided on the width the left part is drawn at. Since Amendment 1, H2 and H5, the
 * right part always keeps its width (the status words live there, and in daytime it keeps the evening line's width), so there is no
 * longer a case without it.
 */
internal fun leftPartWidthPx(totalPx: Int, dividerPx: Int, fit: NavigationInfoFit): Int {
    val rightWidth = if (fit.overflows) minOf(fit.rightPx, maxOf(0, totalPx - dividerPx - fit.leftPx)) else fit.rightPx
    return maxOf(0, totalPx - dividerPx - rightWidth)
}

/**
 * Where [kindText] goes on the top line: for the widest figure this kind can show in [unit] ([longestFigures]) or [shownFigure]
 * where it is wider, on the left part as drawn ([leftPartWidthPx]). [figurePx] measures in the figure's type, [kindPx] in the kind's.
 *
 * Was decided on the narrowest the left part could be across headings and figures (the planner's call D1, 2026-11-06), because the
 * left part then took the turn row's width where room was short, and the turn row changed as the walker turned. Since Amendment 1,
 * G3 and H1, the turn line is one width in every state and the left part is sized for the widest figure the walk could show
 * (`navigationInfoFit`), so the width drawn is already the same for the whole walk.
 */
internal fun topLinePlace(
    kindText: String?,
    shownFigure: String,
    unit: DistanceUnit,
    fit: NavigationInfoFit,
    totalPx: Int,
    dividerPx: Int,
    gapPx: Int,
    figurePx: (String) -> Int,
    kindPx: (String) -> Int,
): TopLinePlace {
    val kind = kindText ?: return TopLinePlace.NONE
    val longest = (distanceKindOf(kind)?.let { longestFigures(it, unit) }.orEmpty() + shownFigure).maxOf(figurePx)
    val kindWidth = kindPx(kind)
    val leftPart = leftPartWidthPx(totalPx, dividerPx, fit)
    val place = kindPlace(
        TopLineWidths(
            leftPartPx = leftPart,
            gapPx = gapPx,
            figurePx = longest,
            kindPx = kindWidth,
            stackedPx = kindStackOf(kind)?.let { (top, bottom) -> maxOf(kindPx(top), kindPx(bottom)) },
            shortPx = kindShortOf(kind)?.let(kindPx),
        ),
    )
    return TopLinePlace(place, longest, leftPart)
}

/**
 * G3: the widest figure the card could show in [unit] on this walk, either kind ([longestFigures]), by [widthOf]: what the left part
 * is sized for, so it keeps one width as the number changes and as the kind does.
 */
internal fun longestCardFigurePx(unit: DistanceUnit, widthOf: (String) -> Int): Int =
    DistanceKind.entries.flatMap { longestFigures(it, unit) }.maxOf(widthOf)

/** The widest a turn's distance reads in [unit] (dispatch 2026-11-06-01's Phase 1: "88.8 mi", "88.8 km"), for [nextTurnReservePx]. */
internal fun longestTurnDistance(unit: DistanceUnit): String = longestFigures(DistanceKind.BY_TRAIL, unit).last()
