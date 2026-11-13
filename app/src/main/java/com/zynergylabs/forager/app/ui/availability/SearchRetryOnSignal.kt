package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

// Moved unchanged from MapsSearchFailure.kt (dispatch 2026-11-11-01, Amendment U, UB6: that file had grown to 419 lines).

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
    failureSerial: Int,
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
    // QC1: keyed on the failures' count too, so a failed automatic try is seen even when the state reads as it did before it.
    // A timed try waits in here; any change of state (a tap on "Try again", a success, a return of signal running a try) ends
    // this effect and so drops the wait, and Forager leaving the screen ends it and starts it again on coming back.
    LaunchedEffect(failed, running, onScreen, failureSerial, autoRetry) {
        if (autoRetry.onStateChanged(failed, running, onScreen, latest.validated)) {
            retry()
            return@LaunchedEffect
        }
        val wait = autoRetry.timedTryDelayMs() ?: return@LaunchedEffect
        delay(wait)
        if (autoRetry.onTimedTryDue(currentFailed, currentRunning, currentOnScreen, latest.validated)) retry()
    }
}

/** The signal's last value, as [RetryFailedSearchOnSignalReturn] last saw it; no signal until the first value comes. */
private class LatestSignal {
    var validated: Boolean = false
}
