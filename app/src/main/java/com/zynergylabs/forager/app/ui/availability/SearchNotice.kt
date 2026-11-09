package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySearchUi.kt (dispatch 2026-10-19-17, UI split Phase 1, PR 10a).

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.mapChromeContentColor
import com.zynergylabs.forager.app.ui.theme.Spacing


/**
 * Failures that come from the drawer's controls or the app bar's search field, surfaced outside
 * both.
 *
 * The coordinate-validation and search-failure messages used to be rendered inside the ranked
 * list, which was the default tab. The Map tab is the default now and the controls that raise
 * these live behind a drawer that closes on search, so without this strip both messages could be
 * raised and never seen (CLAUDE.md: failures are reported, not swallowed). The taxon-search error
 * joined this strip rather than staying inline in the old `AvailabilitySearchTopBar`: that bar is a fixed
 * two-row sibling above the weighted tab content (see its own doc comment), so an inline error
 * line there would grow the app bar's height exactly when the map's share of the screen is being
 * protected — this strip already exists and already scrolls with nothing beneath it.
 */
@Composable
internal fun SearchNotice(
    uiState: AvailabilityUiState,
    /** Whether a map is drawn beneath the banner: the Maps tab's own, inside its map's Box. Map chrome at 80%. */
    overMap: Boolean = false,
) {
    val message = searchNoticeMessage(uiState) ?: return

    // The fill and its content colour, pinned to the fill's own role (`contentColorFor` matches a
    // colour-scheme role exactly; see `MapLayersSheet`). The text keeps its own explicit colour.
    val noticeColor = mapChromeFill(MaterialTheme.colorScheme.errorContainer, overMap)
    val noticeContentColor = contentColorFor(MaterialTheme.colorScheme.errorContainer)
    Surface(
        color = noticeColor,
        contentColor = noticeContentColor,
        modifier = Modifier
            .absolutePadding(left = LocalSearchNoticeInset.current.left, right = LocalSearchNoticeInset.current.right)
            .testTag(SEARCH_NOTICE_TAG)
            .mapChromeContainerColor(noticeColor),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .mapChromeContentColor(LocalContentColor.current)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

/**
 * Room [SearchNotice] leaves at its left and right ends. The landscape L's map tab provides the L's side and width here (owner's
 * ruling (b), continuation 2026-09-28-172): the notice and the L must not overlap, and a composition local reaches the notice inside
 * the search slot without widening the slot's signature. [None] everywhere else, so every other notice is as it was.
 */
internal data class SearchNoticeInset(val left: Dp = 0.dp, val right: Dp = 0.dp) {
    companion object {
        val None = SearchNoticeInset()
    }
}

internal val LocalSearchNoticeInset = compositionLocalOf { SearchNoticeInset.None }

/**
 * The text [SearchNotice] shows, or `null` when it shows nothing. One definition for the banner and for the Maps tab's layout, which
 * places the banner below the compass strip and keeps the icon cluster below it only while it shows (dispatch 2026-09-28-104, item 2).
 */
internal fun searchNoticeMessage(uiState: AvailabilityUiState): String? =
    uiState.errorMessage
        ?: uiState.taxonSearchErrorMessage
        ?: uiState.plannedTripsErrorMessage
        ?: if (uiState.locationPermissionDenied) {
            // RECORD -700, the owner: "Option 1, but only refer to "Set on map" since that is right there."
            LOCATION_PERMISSION_DENIED_MESSAGE
        } else {
            null
        }

/** The banner when location permission is denied; the owner's wording, RECORD -700. */
internal const val LOCATION_PERMISSION_DENIED_MESSAGE = "Location permission was denied. Tap Set on map to choose a place."

/** [SearchNotice]'s banner, for tests. */
internal const val SEARCH_NOTICE_TAG = "search-notice"
