package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * Dispatch 2026-11-11-01: what the retry by itself ([RetryFailedSearchOnSignalReturn], [SearchAutoRetry]) is fed from the screen's
 * state. Its own file (Amendment U, UB6: not in AvailabilityScreen.kt, at 770 lines).
 *
 * - [failed]: the map's half has failed, or, since Amendment U (UB1, the owner: "Same as the map's half (Recommended)"; option text
 *   "\"Search failed.\" always behaves the same: when signal returns it tries at once, then 3 seconds, then 10 seconds, three in
 *   all, then waits for \"Try again\""), the ranked list alone has failed. One gate, so one cap of three per failed search,
 *   whichever half failed; [onRetry] ([retryFailedSearch]) runs the list's half for a list-only failure and the map's otherwise.
 * - running: a fetch for the failed search runs, or a retry is starting, of either half. Since Amendment V, the list's own fetch
 *   too ([listStillLoading]).
 * - failures counted: each failed fetch of either half, so a try that fails at once is still seen.
 */
@Composable
internal fun RetryFailedSearchByItself(
    uiState: AvailabilityUiState,
    networkValidated: Flow<Boolean>,
    autoRetry: SearchAutoRetry,
    onRetry: () -> Unit,
) {
    RetryFailedSearchOnSignalReturn(
        networkValidated = networkValidated,
        failed = mapsSearchFailed(uiState) || listOnlySearchFailed(uiState),
        running = uiState.isLoadingSightings || uiState.isRetryingSearch || uiState.isRetryingList || listStillLoading(uiState),
        failureSerial = uiState.sightingsFailureSerial + uiState.listFailureSerial,
        searchSerial = uiState.newSearchSerial,
        autoRetry = autoRetry,
        onRetry = onRetry,
    )
}

/**
 * Amendment V (VA1, VA2; the owner: "3 tries, then wait for me"; "Same as the map's half (Recommended)"): the ranked list's fetch
 * is still running while the map's half has not failed. Counted as running so that:
 * - VA1: when an automatic try runs both halves and the map's comes back first, the gate does not read "nothing failed, nothing
 *   running" and start its count again; the list failing after it gets the 3 s and 10 s tries, within the same three.
 * - VA2: a return of signal while only the list loads is held, and runs once if the list then fails, as F2 does for the map's half.
 * Left out while the map's half has failed, so that case runs as it did before (a return of signal tries the map at once).
 *
 * Amendment W, WA5: `isLoading` is also true while Search (the current location) looks for a fix, before its search starts
 * (`AvailabilityViewModel.useCurrentLocation`, which also clears `errorMessage`). Read from the code, the gate then:
 * - after a search whose map's half failed: this term is false and the map's failure still shows, so a return of signal during
 *   the fix tries the old search's map's half, as before Amendment V;
 * - otherwise (no failure, or the list's alone, whose message the fix clears): nothing failed and this term reads as running, so a
 *   return of signal is held; when the fix ends a new search starts and [SearchAutoRetry.onSearch] drops it, and when the fix
 *   fails ("couldn't find" is not the list's failure) nothing has failed and nothing runs, so the count starts again.
 *
 * Amendment W (WA1): since V this term made a new search read as running from its first moment, so a new search no longer
 * started the count again by reading nothing failed and nothing running; the gate is now told of a new search directly.
 */
internal fun listStillLoading(uiState: AvailabilityUiState): Boolean = uiState.isLoading && !mapsSearchFailed(uiState)

