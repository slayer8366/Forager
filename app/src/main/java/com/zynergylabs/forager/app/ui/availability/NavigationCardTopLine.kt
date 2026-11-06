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
 * space apart ([TopLineHalves]), on one baseline, so it reads "2.2 mi by trail". With no kind ("Arrived", the dash) the word stands
 * in the distance's own place, in the left half, the right half empty (the owner: "In the left half").
 *
 * Where they can't share the line the kind takes the owner's steps ([KindPlace], decided in [kindPlace]): stacked "by" over
 * "trail", "trail" alone, then its own line under the distance, above the turn line. The kind keeps its type in every step, the
 * stacked words too ("Keep the text size the same size"). It carries [NAVIGATION_HUD_DISTANCE_KIND_TAG] in every step; stacked,
 * the tag is on the stack, which reads "by trail" as one text.
 *
 * Where a figure is wider than its half (a narrow card, large text), it starts at the left part's start rather than run off it,
 * and ends as near the middle as it can. That, how the stacked words sit, and where the kind goes on its own line, are this
 * build's reading, not yet ruled: see the dispatch's completion report.
 */
@Composable
internal fun CardTopLine(readout: NavigationHudReadout, fit: NavigationInfoFit, color: Color, modifier: Modifier = Modifier) {
    val gapPx = with(LocalDensity.current) { Spacing.xs.roundToPx() }
    val kind = readout.distanceKindText
    val place = if (kind == null) KindPlace.NONE else fit.kindPlace
    Layout(
        modifier = modifier.fillMaxWidth(),
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
        val halves = TopLineHalves(width, gapPx)
        // As near the middle as it fits: ends at the middle, or starts at the left part's start where it is wider than its half.
        val figureX = maxOf(0, halves.figureEndPx - figure.width)
        if (kindPlaceable == null || place == KindPlace.NONE) {
            layout(width, figure.height) { figure.place(figureX, 0) }
        } else if (place == KindPlace.OWN_LINE) {
            // Its own line has the left part's whole width: it starts at the middle, or as near it as it fits.
            val kindX = maxOf(0, minOf(halves.kindStartPx, width - kindPlaceable.width))
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
                kindPlaceable.place(halves.kindStartPx, kindY)
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

/** Dispatch 2026-11-05-10: each of the stacked kind's two words, "by" and "trail", so a test can read where each sits. */
internal const val NAVIGATION_HUD_DISTANCE_KIND_WORD_TAG = "navigation-hud-distance-kind-word"
