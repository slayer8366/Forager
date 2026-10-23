package com.zynergylabs.forager.app.ui.availability

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.needsFetch

/**
 * The line a region's row in Offline maps says about its trail routes, and whether it offers "Try again" (dispatch
 * 2026-10-23-03; the owner's answers to the step path: "Automatic, button as backup (Recommended)", the wording "OK as
 * drafted"). `null` is no line: routes stored (path A, the size already counts them), including a region whose
 * routes lack a square, which is said only when a route reaches that edge, not on the row (the owner, D: "Only when a
 * route reaches that edge (Recommended)").
 *
 * - [fetching]: "Getting trail routes".
 * - No routes for the area, or a reason a retry can't fix: C's line, no button (path C; F uses C's line).
 * - Routes wanted and no signal: "...download next time you're online.", no button (path E, offline).
 * - Routes wanted, and an attempt this session has failed ([attemptFailed]): B's line with **Try again**.
 * - Routes wanted with signal and no attempt yet: no line, for the moment before the automatic fetch starts.
 *
 * Pure, so every state is tested without a screen.
 */
internal fun offlineRouteLine(routeData: RegionRouteData, fetching: Boolean, attemptFailed: Boolean, noSignal: Boolean): OfflineRouteLine? = when {
    fetching -> OfflineRouteLine(ROUTES_GETTING, tryAgain = false)
    routeData is RegionRouteData.Stored -> null
    !routeData.needsFetch() -> OfflineRouteLine(ROUTES_NONE_HERE, tryAgain = false)
    noSignal -> OfflineRouteLine(ROUTES_NEXT_TIME_ONLINE, tryAgain = false)
    attemptFailed -> OfflineRouteLine(ROUTES_DIDNT_DOWNLOAD, tryAgain = true)
    else -> null
}

internal data class OfflineRouteLine(val text: String, val tryAgain: Boolean)

/**
 * What the Offline maps region list asks of the ViewModel about trail routes (dispatch 2026-10-23-03): [onTryAgain]
 * on a region's row, and [onSignalReturned] when signal comes back while the list is on screen (Amendment 1).
 */
data class OfflineRouteActions(val onTryAgain: (Long) -> Unit = {}, val onSignalReturned: () -> Unit = {})

/** [OfflineRegionRow]'s route line and its button, under the region's one line. */
@Composable
internal fun OfflineRouteLineContent(regionId: Long, line: OfflineRouteLine, onTryAgain: () -> Unit) {
    Text(
        line.text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(offlineRouteLineTag(regionId)),
    )
    if (line.tryAgain) {
        OutlinedButton(onClick = onTryAgain, modifier = Modifier.testTag(offlineRouteTryAgainTag(regionId))) { Text(ROUTES_TRY_AGAIN) }
    }
}

internal const val ROUTES_GETTING = "Getting trail routes"
internal const val ROUTES_DIDNT_DOWNLOAD = "Map only. Trail routes didn't download."
internal const val ROUTES_NONE_HERE = "Map only. No trail routes for this area yet."
internal const val ROUTES_NEXT_TIME_ONLINE = "Map only. Trail routes download next time you're online."
internal const val ROUTES_TRY_AGAIN = "Try again"

internal fun offlineRouteLineTag(regionId: Long): String = "offline-region-routes-$regionId"

internal fun offlineRouteTryAgainTag(regionId: Long): String = "offline-region-routes-try-again-$regionId"
