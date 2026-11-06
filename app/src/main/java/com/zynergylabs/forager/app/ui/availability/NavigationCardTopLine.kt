package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.zynergylabs.forager.app.ui.motion.WordSwap
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the left part's top line, the distance and what it measures. The owner: "For navigation
 * cards have the 2.2 mi on the left half, and then move the "by trail" from the bottom to the top right half, on the same line as
 * 2.2 mi", and "Meet at the middle (Recommended)": the distance ends at the left part's middle and the kind starts there, a word's
 * space apart, on one baseline, so it reads "2.2 mi by trail". Where the longest distance the walk could show doesn't fit left of
 * the middle, "Shift the middle a little (Recommended)": the meeting point moves right just enough for it ([TopLineMeeting]), so it
 * stays put while the number changes.
 *
 * Where the kind doesn't fit right of the meeting point it takes the owner's steps ([KindPlace], decided in [topLinePlace]):
 * - stacked "by" over "trail", "On the distance's line (Recommended)": both words start at the meeting point, "trail" on the
 *   distance's baseline and "by" just above it;
 * - "trail" alone, on the same baseline (in the owner's order, though never reached: see [KindPlace]);
 * - its own line under the distance, above the turn line, "At the middle (Recommended)": starting at the meeting point, or as near
 *   it as fits.
 * The kind keeps its type in every step, the stacked words too ("Keep the text size the same size"). It carries
 * [NAVIGATION_HUD_DISTANCE_KIND_TAG] in every step; stacked, the tag is on the stack, which reads "by trail" as one text.
 *
 * With no kind ("Arrived", the dash) the word stands in the distance's own place, "In the left half": it ends at the middle, which
 * does not move, since there is no kind's longest figure to move it for. Where the word is wider than its half, "Left, crossing as
 * little (Recommended)": it starts at the left part's left edge and crosses the middle only as far as it must.
 */
@Composable
internal fun CardTopLine(readout: NavigationHudReadout, fit: NavigationInfoFit, color: Color, modifier: Modifier = Modifier) {
    val gapPx = with(LocalDensity.current) { Spacing.xs.roundToPx() }
    val kind = readout.distanceKindText
    val place = if (kind == null) KindPlace.NONE else fit.topLine.kind
    val longestFigurePx = if (kind == null) null else fit.topLine.longestFigurePx
    Layout(
        // The tag is on the whole line, the left part's full width, so a test reads the left part's own bounds.
        modifier = modifier.fillMaxWidth().testTag(NAVIGATION_HUD_TOP_LINE_TAG),
        contents = listOf(
            {
                WordSwap(text = columnFigure(readout, fit.figureShort)) { shown ->
                    Text(
                        text = shown,
                        style = cardFigureStyle(),
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        // The route message's own tag is on its line when it moves there; the dash here is the left part's.
                        modifier = if (readout.distanceText == ROUTE_UNAVAILABLE_TEXT) Modifier else Modifier.testTag(NAVIGATION_HUD_DISTANCE_TAG),
                    )
                }
            },
            { if (kind != null) KindWords(kind, place, color) },
        ),
    ) { (figureParts, kindParts), constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val figure = figureParts.single().measure(loose)
        val kindPlaceable = kindParts.singleOrNull()?.measure(loose)
        val meeting = TopLineMeeting(width, gapPx, longestFigurePx)
        // Ends at the meeting point, or, a word with no kind wider than its half, starts at the left part's left edge.
        val figureX = maxOf(0, meeting.figureEndPx - figure.width)
        if (kindPlaceable == null || place == KindPlace.NONE) {
            layout(width, figure.height) { figure.place(figureX, 0) }
        } else if (place == KindPlace.OWN_LINE) {
            // Its own line has the left part's whole width: it starts where it would beside the distance, or as near it as fits.
            val kindX = maxOf(0, minOf(meeting.kindStartPx, width - kindPlaceable.width))
            layout(width, figure.height + kindPlaceable.height) {
                figure.place(figureX, 0)
                kindPlaceable.place(kindX, figure.height)
            }
        } else {
            // One baseline: the kind's last line (its only line, or "trail" under "by") on the figure's baseline.
            val figureBaseline = figure.baselineOr(FirstBaseline)
            val kindBaseline = kindPlaceable.baselineOr(LastBaseline)
            val line = maxOf(figureBaseline, kindBaseline)
            val figureY = line - figureBaseline
            val kindY = line - kindBaseline
            layout(width, maxOf(figureY + figure.height, kindY + kindPlaceable.height)) {
                figure.place(figureX, figureY)
                kindPlaceable.place(meeting.kindStartPx, kindY)
            }
        }
    }
}

/**
 * A placeable's baseline. Every text reports one, and [WordSwap] passes its text's on, so a missing one is a bug: it is logged and
 * the placeable's bottom stands in, so the line is still drawn.
 */
private fun androidx.compose.ui.layout.Placeable.baselineOr(line: AlignmentLine): Int {
    val baseline = get(line)
    if (baseline != AlignmentLine.Unspecified) return baseline
    android.util.Log.w(TOP_LINE_LOG_TAG, "the top line's text reports no baseline ($line); its bottom stands in")
    return height
}

private const val TOP_LINE_LOG_TAG = "NavigationCard"

/** The kind word in [place]'s form, in the status line's type (dispatch -677; RECORD -713), whatever its form. */
@Composable
private fun KindWords(kind: String, place: KindPlace, color: Color) {
    val style = MaterialTheme.typography.labelMedium
    val stack = kindStackOf(kind)
    if (place == KindPlace.STACKED && stack != null) {
        Column(
            modifier = Modifier.semantics { text = AnnotatedString(kind) }.testTag(NAVIGATION_HUD_DISTANCE_KIND_TAG),
            horizontalAlignment = Alignment.Start,
        ) {
            for (word in listOf(stack.first, stack.second)) {
                Text(text = word, style = style, color = color, maxLines = 1, softWrap = false, modifier = Modifier.testTag(NAVIGATION_HUD_DISTANCE_KIND_WORD_TAG))
            }
        }
        return
    }
    val shown = if (place == KindPlace.SHORT) kindShortOf(kind) ?: kind else kind
    WordSwap(text = shown) { words ->
        Text(
            text = words,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(NAVIGATION_HUD_DISTANCE_KIND_TAG),
        )
    }
}

/** Dispatch 2026-11-05-10: the top line, as wide as the left part. */
internal const val NAVIGATION_HUD_TOP_LINE_TAG = "navigation-hud-top-line"

/** Dispatch 2026-11-05-10: each of the stacked kind's two words, "by" and "trail", so a test can read where each sits. */
internal const val NAVIGATION_HUD_DISTANCE_KIND_WORD_TAG = "navigation-hud-distance-kind-word"
