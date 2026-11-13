package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Spacing

// Moved unchanged from MapsSearchFailure.kt (dispatch 2026-11-11-01, Amendment U, UB6: that file had grown to 419 lines): the
// failure as the Maps tab's search bar shows it out of fullscreen (Amendments R, S and T).

/**
 * Dispatch 2026-11-11-01, Amendment R and S: the bar's line while the search has failed (RA3's option text: "\"Search failed.\"
 * sits where the October text is, with a \"Try again\" button at the bar's right"), in place of the whole resting line (RA4, the
 * owner: "The whole line").
 */
internal const val SEARCH_BAR_FAILED_TEXT = "Search failed."

/**
 * What the Maps tab's search bar shows for a failed search out of fullscreen: [line] in place of the resting line, and "Try
 * again" in place of Clear, greyed and doing nothing while [retrying] (G2, the owner: "Shown, greyed out"; SA1: while a try runs).
 */
internal data class SearchBarFailure(val retrying: Boolean, val onTryAgain: () -> Unit) {
    /**
     * Amendment T, TC1 and TC2 (the owner: "Shorter word when tight (Recommended)"; "Button only when nothing fits
     * (Recommended)"): the bar's line, longest first; the bar shows the first that fits whole, or none.
     */
    val lines: List<String> get() = if (retrying) {
        listOf(SEARCH_RETRYING_NOTICE_TEXT, SEARCH_RETRYING_SHORT_TEXT)
    } else {
        listOf(SEARCH_BAR_FAILED_TEXT, SEARCH_BAR_FAILED_SHORT_TEXT)
    }
}

/**
 * Amendment T, TC1: the bar's line for [failure] in [roomPx] of text room at the current font: "Search failed." if it fits whole,
 * else "Failed.", else no words ("Try again" alone). [widthOf] measures a line, unwrapped, in the field's text style. TC2 (the
 * planner's reading): the same while a try runs, "Trying again…", "Trying…", then none. Text size never changes.
 */
internal fun searchBarFailureLine(failure: SearchBarFailure, roomPx: Int, widthOf: (String) -> Int): String =
    failure.lines.firstOrNull { widthOf(it) <= roomPx } ?: ""

/** Amendment T, TB1: the shorter word when "Search failed." does not fit (option text: "the bar reads \"Failed.\""). */
internal const val SEARCH_BAR_FAILED_SHORT_TEXT = "Failed."

/** Amendment T, TC2 (the planner's reading): the shorter word while a try runs. */
internal const val SEARCH_RETRYING_SHORT_TEXT = "Trying…"

/**
 * The bar's failure, for the Maps tab out of fullscreen: while the map's half has failed, or a failed search runs again; and,
 * since Amendment T (TB4, the owner: "One line, in the bar"), while the ranked list alone has failed or runs again.
 */
internal fun searchBarFailure(uiState: AvailabilityUiState, onTryAgain: () -> Unit): SearchBarFailure? =
    if (mapsSearchNoticeShows(uiState) || listOnlySearchFailed(uiState) || uiState.isRetryingList) {
        SearchBarFailure(retrying = uiState.isRetryingSearch || uiState.isRetryingList, onTryAgain = onTryAgain)
    } else {
        null
    }

/**
 * Amendment T, TC1: the bar's line for [failure], measured against [maxWidth], the field's whole width beside "Try again", less
 * the field's own side padding ([fieldSidePadding] each side). Measured in the field's text style ([LocalTextStyle]).
 */
@Composable
internal fun rememberSearchBarFailureLine(failure: SearchBarFailure, maxWidth: Dp, fieldSidePadding: Dp): String {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current
    val density = LocalDensity.current
    val room = with(density) { (maxWidth - fieldSidePadding * 2).roundToPx() }
    return searchBarFailureLine(failure, room) { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
}

/**
 * "Try again" in the bar, at Clear's size and place (SB1, the owner: "\"Try again\" takes Clear's place (Recommended)"): the field's
 * height tall, the word and its padding wide, no 48 dp minimum touch height, as Clear (RECORD -728). Disabled, in the button's own
 * disabled colour, while a try runs (G2).
 */
@Composable
internal fun TryAgainInBar(failure: SearchBarFailure, height: Dp, contentColor: Color) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        TextButton(
            onClick = failure.onTryAgain,
            enabled = !failure.retrying,
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            contentPadding = PaddingValues(horizontal = Spacing.md),
            modifier = Modifier.padding(end = Spacing.xs).height(height).widthIn(min = 48.dp).testTag(SEARCH_FAILED_TRY_AGAIN_TAG),
        ) {
            Text(SEARCH_FAILED_TRY_AGAIN_TEXT)
        }
    }
}
