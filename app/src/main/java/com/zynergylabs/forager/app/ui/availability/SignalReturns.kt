package com.zynergylabs.forager.app.ui.availability

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The shortest time between two automatic retries of a failed search: an operating limit, not a measured figure (the coder's
 * call, dispatch 2026-11-11-01, for the planner to accept or change). The owner's ruling is "At once (Recommended)" (C4), and the
 * first return of signal is told at once; this only spaces out the ones after it, so a signal that comes and goes at the edge of
 * coverage cannot send a burst of searches (D1). Ten seconds is several times a search's usual length, so a retry still running
 * is not overtaken by the next.
 */
internal const val SIGNAL_RETURN_MIN_INTERVAL_MS: Long = 10_000

/**
 * One value each time [validated] returns to `true` after having been `false`: the phone getting a working connection back.
 * [validated] is [com.zynergylabs.forager.app.domain.NetworkSignal.validated], the raw signal (dispatch 2026-11-11-01, C4 and D1),
 * not the map's "no signal" with its delays (`noSignalWithHysteresis`), so a return is told when the platform reports it.
 *
 * - The first value is the state the screen starts in, not a change: `true` first tells nothing, and `false` first is a loss.
 * - A return is told at once, then nothing for [minIntervalMs]. Returns inside that time are kept as one, told when it ends, so
 *   none is lost and a flapping signal tells at most one per [minIntervalMs] (D1: "a signal that flaps must not produce a burst").
 */
internal fun signalReturns(validated: Flow<Boolean>, minIntervalMs: Long = SIGNAL_RETURN_MIN_INTERVAL_MS): Flow<Unit> = channelFlow {
    val returns = Channel<Unit>(Channel.CONFLATED)
    launch {
        var lost = false
        validated.distinctUntilChanged().collect { ok ->
            if (!ok) {
                lost = true
            } else if (lost) {
                lost = false
                returns.send(Unit)
            }
        }
        returns.close()
    }
    for (signalBack in returns) {
        send(signalBack)
        delay(minIntervalMs)
    }
}
