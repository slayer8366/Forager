package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.motion.WordSwap

/**
 * The card's right part, dispatch 2026-11-05-10, Amendment 1 (RECORD -2181): the evening lines, and under them the status space.
 * - H2, the owner, to "With the north arrow and heading gone from the card, where should "Approaching" (and the other status words,
 *   like "Off trail") go?": "Under the evening line (Recommended)", the option reading "Up one line, into the empty space under "Dark
 *   since…", left of the X. The card is two lines tall, and the divider runs its full height."
 * - The planner's readings, open to the owner: the status words are centred in their own space, the right part's second line, with
 *   or without the evening line (in daytime the first line is held empty, [eveningHeld]); where the evening part is more than one
 *   line, the status goes under the whole of it and the card grows, as it does today (Q2).
 * The route message and Try again keep their own line under the parts ([CardRouteLines]).
 */
@Composable
internal fun RightPart(
    readout: NavigationHudReadout,
    sundownLine: String?,
    backByLine: String?,
    sundownVisibility: MutableTransitionState<Boolean>,
    backByVisibility: MutableTransitionState<Boolean>,
    eveningBreaks: Boolean,
    /** Whether an evening line is shown or still leaving; without one, its first line is held empty. */
    eveningHeld: Boolean,
    /** H8: the status words are here; `false` where they take their own line under the parts, and the right part holds the evening only. */
    statusInRight: Boolean,
    onStatusHeight: (Float) -> Unit,
) {
    val eveningStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (eveningHeld) {
            EveningStack(sundownLine, backByLine, sundownVisibility, backByVisibility, eveningBreaks)
        } else if (statusInRight) {
            val lineHeight = with(LocalDensity.current) { rememberTextMeasurer().measure("Sunset", eveningStyle, maxLines = 1).size.height.toDp() }
            Spacer(Modifier.height(lineHeight))
        }
        if (statusInRight) {
            StatusSpace(readout, Modifier.onGloballyPositioned { onStatusHeight(it.size.height.toFloat()) })
        } else {
            SideEffect { onStatusHeight(0f) }
        }
    }
}

/**
 * The status space: the status words, then the compass notice on its own line, each centred and wrapping in the right part's width.
 * - The status words are the readout's ([NavigationHudReadout.statusForms]), longest first: the first form that fits on one line,
 *   as today; a status that may be dropped ("Approaching", the straight-line note) still is where none fits (T11, RECORD -766); one
 *   that may not keeps its shortest form and wraps. H4, the owner: "Wrap the text to "Trail routes not" and "downloaded" goes on the
 *   next line"; the planner's reading: "Any other status words too wide for the space wrap the same way; no new short forms are
 *   invented. The space grows taller, never wider." Natural centred wrapping, no hand-made breaks (the planner's reading of Q8).
 * - What the compass strip already says about the position (no fix, approximate, last seen) is left out, so it shows once, on the
 *   strip (RECORD -951).
 * - H3, the owner, to "With the heading off the card, where do these go?": "In the status space (Recommended)", the option reading
 *   "They show where "Approaching" goes, under the evening line. The compass strip stays exactly as it is outside navigation." With
 *   other status words as well, both show, the notice on its own line under them (the planner's reading of Q6).
 */
@Composable
internal fun StatusSpace(
    readout: NavigationHudReadout,
    modifier: Modifier = Modifier,
    /** H10: the status words' own line under the parts (H8) holds one of them, the more urgent ([oneStatusLine]). */
    oneLine: Boolean = false,
) {
    val statusStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val widthPx = constraints.maxWidth
        val statusFit = statusLineFit(readout, kindInStatus = false, maxPx = widthPx, leadSeparatorPx = 0) {
            measurer.measure(it, statusStyle, maxLines = 1, softWrap = false).size.width
        }
        val words = if (readout.statusIsPositionStatus) "" else statusFit.status
        val (status, notice) = if (oneLine) oneStatusLine(words, readout.compassNoticeText, readout.statusIsFixAge) else words to readout.compassNoticeText
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (status.isEmpty()) {
                // Nothing to say: no line (RECORD -935, "only when it has something to say"). An empty, zero-height node keeps the
                // status's tag, reading "", so "the status line is empty" stays a statement a test can check.
                Text(text = "", style = statusStyle, modifier = Modifier.height(0.dp).testTag(NAVIGATION_HUD_STATUS_TAG))
            } else {
                WordSwap(text = status, modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { shown ->
                    Text(
                        text = shown,
                        style = statusStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag(NAVIGATION_HUD_STATUS_TAG),
                    )
                }
            }
            if (notice != null) {
                WordSwap(text = notice, modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { shown ->
                    Text(
                        text = shown,
                        style = statusStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag(NAVIGATION_HUD_COMPASS_NOTICE_TAG),
                    )
                }
            }
        }
    }
}

/** H3: the compass notice in the card's status space, "Compass calibrating…" or "Compass unavailable · north up". */
internal const val NAVIGATION_HUD_COMPASS_NOTICE_TAG = "navigation-hud-compass-notice"
