package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.theme.mapChromeContentColor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/*
 * Dispatch 2026-11-11-01 (known bug 30, audit finding H1): a failed or loading sightings search no longer replaces the Maps tab.
 * The owner's rulings, verbatim (the dispatch's Amendments, C1 to C7): "Under the search bar (Recommended)"; "Thin line on the
 * search bar (Recommended)"; "One line (Recommended)"; "At once (Recommended)"; "\"Search failed. Check your connection.\"
 * (Recommended)"; "Top of the map (Recommended)"; "Notice stays, no auto retry (Recommended)". This file holds what the Maps tab
 * draws for them; ScaffoldMapTabCall places it, and AvailabilityScreen watches the signal.
 */

/** The notice's words (C5, the owner: "\"Search failed. Check your connection.\" (Recommended)"). */
internal const val SEARCH_FAILED_NOTICE_TEXT = "Search failed. Check your connection."

/** The notice's words while a failed search runs again (E4; "The words are the owner's to change"). */
internal const val SEARCH_RETRYING_NOTICE_TEXT = "Trying again…"

/** The notice's control (C1: "\"Try again\" at its right end"). */
internal const val SEARCH_FAILED_TRY_AGAIN_TEXT = "Try again"

/** [SearchFailedNotice], for tests. */
internal const val SEARCH_FAILED_NOTICE_TAG = "search-failed-notice"

/** [SearchFailedNotice]'s "Try again", for tests. */
internal const val SEARCH_FAILED_TRY_AGAIN_TAG = "search-failed-try-again"

/** [SearchBarLoadingLine]'s moving line, for tests. */
internal const val SEARCH_BAR_LOADING_LINE_TAG = "search-bar-loading-line"

/** How thick [SearchBarLoadingLine] is: "a thin moving line" (C2). */
internal val SEARCH_BAR_LOADING_LINE_HEIGHT: Dp = 2.dp

/** Whether the map's sightings fetch failed for the search showing: what the notice, "Try again" and the retry on signal follow. */
internal fun mapsSearchFailed(uiState: AvailabilityUiState): Boolean = uiState.sightingsErrorMessage != null

/**
 * The state [SearchNotice] reads on the Maps tab. While the map's sightings have failed, the ranked list's own failure line is left
 * out, so a search whose two halves both failed shows one line, the [SearchFailedNotice] (C3, the owner: "One line
 * (Recommended)"). Only that failure is folded: anything else the banner says (a mistyped coordinate, a species lookup, the trips,
 * location permission) shows as it did. When only the ranked list failed, nothing changes (the dispatch's D3).
 */
internal fun mapsTabSearchNoticeState(uiState: AvailabilityUiState): AvailabilityUiState =
    if (mapsSearchFailed(uiState) && uiState.errorMessage == LIST_SEARCH_FAILED_MESSAGE) uiState.copy(errorMessage = null) else uiState

/**
 * Whether the search-failed notice shows: the map's half has failed, or a failed search is running again (E4, the owner: "Notice
 * stays, shows it's trying (Recommended)"; its option read "Retry starts > the notice stays where it is and reads 'Trying
 * again…' > it goes only when the search works, or returns to 'Search failed' if it doesn't. Nothing jumps").
 */
internal fun mapsSearchNoticeShows(uiState: AvailabilityUiState): Boolean = mapsSearchFailed(uiState) || uiState.isRetryingSearch

/** Whether a line shows under the Maps tab's search bar: [SearchNotice]'s, the search-failed notice, or both. */
internal fun mapsTabNoticeShows(uiState: AvailabilityUiState): Boolean =
    searchNoticeMessage(mapsTabSearchNoticeState(uiState)) != null || mapsSearchNoticeShows(uiState)

/**
 * What "Try again" calls: [AvailabilityScreenSearchInputs.onRetryFailedSearch], provided by [AvailabilityScreen] so the notice, deep
 * in the Maps tab's call, is reached without a parameter through the frame (the pattern of `LocalDownloadMapsForTrip`).
 */
internal val LocalRetryFailedSearch = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The one-line notice for a failed search (C1): the existing search-error banner's spot and look ([SearchNotice]: the error
 * container at the map chrome's 80% fill, its text opaque), with "Try again" at its right end. Over the map only. A [Surface],
 * so it takes every touch inside its own bounds and none outside them.
 */
@Composable
internal fun SearchFailedNotice(retrying: Boolean, onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    val noticeColor = mapChromeFill(MaterialTheme.colorScheme.errorContainer, true)
    val noticeContentColor = contentColorFor(MaterialTheme.colorScheme.errorContainer)
    Surface(
        color = noticeColor,
        contentColor = noticeContentColor,
        modifier = modifier
            .absolutePadding(left = LocalSearchNoticeInset.current.left, right = LocalSearchNoticeInset.current.right)
            .testTag(SEARCH_FAILED_NOTICE_TAG)
            .mapChromeContainerColor(noticeColor),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // E4: both lines are always laid out in one space, the one not showing drawn at no alpha and left out of semantics, so
            // the space is the wider of the two and nothing moves when the words change (CLAUDE.md, "Changing info gets its own
            // window"; no text is shrunk). "Try again" keeps its space while the retry runs, drawn at no alpha and not clickable.
            Box(modifier = Modifier.weight(1f).padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm)) {
                NoticeLine(SEARCH_FAILED_NOTICE_TEXT, shown = !retrying)
                NoticeLine(SEARCH_RETRYING_NOTICE_TEXT, shown = retrying)
            }
            TextButton(
                onClick = onTryAgain,
                enabled = !retrying,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = if (retrying) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier.testTag(SEARCH_FAILED_TRY_AGAIN_TAG),
            ) {
                Text(SEARCH_FAILED_TRY_AGAIN_TEXT)
            }
        }
    }
}

/** One of the notice's two lines, laid out whether it shows or not (see [SearchFailedNotice]). */
@Composable
private fun NoticeLine(text: String, shown: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = if (shown) Modifier.mapChromeContentColor(LocalContentColor.current) else Modifier.alpha(0f).clearAndSetSemantics {},
    )
}

/**
 * The notice while fullscreen hides the search bar (C6, the owner: "Top of the map (Recommended)"; its option read "the same
 * one-line notice shows at the top of the map, where the bar would be"): at the map's top, below the compass strip where the
 * strip is there ([top]). [onHeightMeasured] reports its height with [top], so the icon cluster can be kept below it, as it is
 * below the notice under the bar. Composed only while it shows, so it takes no touch otherwise.
 */
@Composable
internal fun FullscreenSearchFailedNotice(
    top: Dp,
    retrying: Boolean,
    onTryAgain: () -> Unit,
    onHeightMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.onSizeChanged { onHeightMeasured(it.height) }.padding(top = top)) {
        SearchFailedNotice(retrying = retrying, onTryAgain = onTryAgain)
    }
}

/**
 * The thin moving line along the search bar's bottom edge while the map's sightings load (C2, the owner: "Thin line on the
 * search bar (Recommended)"; its option read "Nothing in the bar moves"). Drawn over the bar, clipped to its [shape], with no
 * track, so the bar keeps its own fill and nothing in it moves. Takes no touches: the bar under it keeps them.
 */
@Composable
internal fun SearchBarLoadingLine(shape: Shape, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(shape)) {
        // Tagged on a Box of its own: the indicator's own semantics bounds are padded taller than it draws (Material3's
        // accessibility padding), so a test reading the indicator would measure 12 dp, not the line.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(SEARCH_BAR_LOADING_LINE_HEIGHT)
                .testTag(SEARCH_BAR_LOADING_LINE_TAG),
        ) {
            LinearProgressIndicator(
                color = mapChromeContentColor(LocalForagerDarkTheme.current),
                trackColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The retry by itself (C4, the owner: "At once (Recommended)"; E2 "Only with Forager on screen (Recommended)"; E3 "3 tries, then
 * wait for me (Recommended)"; C7 "Notice stays, no auto retry (Recommended)"): each return of [networkValidated]
 * ([signalReturns]), each change in the search's state and Forager coming on screen are handed to [autoRetry], which says when
 * to run [onRetry]. [failed]: the map's half of the search has failed; [running]: a fetch for it runs, or a retry is starting.
 * On screen means resumed ([Lifecycle.State.RESUMED]), read from this composition's lifecycle owner. Collected for as long as the
 * screen is composed.
 */
@Composable
internal fun RetryFailedSearchOnSignalReturn(
    networkValidated: Flow<Boolean>,
    failed: Boolean,
    running: Boolean,
    autoRetry: SearchAutoRetry,
    onRetry: () -> Unit,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val onScreen = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val latest = remember { LatestSignal() }
    val currentFailed by rememberUpdatedState(failed)
    val currentRunning by rememberUpdatedState(running)
    val currentOnScreen by rememberUpdatedState(onScreen)
    val retry by rememberUpdatedState(onRetry)
    LaunchedEffect(networkValidated, autoRetry) {
        signalReturns(networkValidated.onEach { latest.validated = it }).collect {
            if (autoRetry.onSignalReturn(currentFailed, currentRunning, currentOnScreen)) retry()
        }
    }
    LaunchedEffect(failed, running, onScreen, autoRetry) {
        if (autoRetry.onStateChanged(failed, running, onScreen, latest.validated)) retry()
    }
}

/** The signal's last value, as [RetryFailedSearchOnSignalReturn] last saw it; no signal until the first value comes. */
private class LatestSignal {
    var validated: Boolean = false
}
