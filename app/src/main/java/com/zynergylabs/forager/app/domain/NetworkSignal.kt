package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone has a working internet connection, as the platform judges it: the default network carries the
 * "validated" capability (Android checked it reaches the internet). Dispatch 2026-10-22-01 Amendment 2 (RECORD -1556):
 * "No signal" is read from this alone, never from map tile errors. An interface this project owns (CLAUDE.md: wrap
 * external integrations), so the rule built on it ([noSignalWithHysteresis]) is tested without Android.
 *
 * [validated] emits the current state first, then each change. The real one is `AndroidNetworkSignal`.
 */
interface NetworkSignal {
    val validated: Flow<Boolean>
}

/**
 * How long the phone must be without a validated network before an OpenStreetMap or Topographical map is drawn as
 * Forager Trail Map from the downloaded areas. An operating limit, the planner's call (RECORD -1556: "switching after
 * about 5 s without signal"), not a measured figure: long enough that a network hand-over does not reload the map.
 */
const val NO_SIGNAL_SWITCH_DELAY_MS: Long = 5_000

/**
 * How long a validated network must last before the map goes back to the chosen online map. Longer than
 * [NO_SIGNAL_SWITCH_DELAY_MS] on purpose (RECORD -1556: "back after about 30 s with it"), so a signal that comes and
 * goes at the edge of coverage does not swap the map back and forth: each swap is a full style reload.
 */
const val SIGNAL_RETURN_SWITCH_DELAY_MS: Long = 30_000

/**
 * `true` while there is "no signal" for the map's purposes: [validated] has been `false` for [lossDelayMs], and has not
 * since been `true` for [returnDelayMs]. A change that does not last its delay is dropped, so the output only moves
 * after the input has held still.
 *
 * The first value is taken as it is, with no delay: it is the state the app starts in, not a change, so a map opened
 * with no signal is drawn from the downloads at once rather than trying the online map for five seconds and then
 * reloading. Callback driven, with one pending timer at most; no polling.
 */
fun noSignalWithHysteresis(
    validated: Flow<Boolean>,
    lossDelayMs: Long = NO_SIGNAL_SWITCH_DELAY_MS,
    returnDelayMs: Long = SIGNAL_RETURN_SWITCH_DELAY_MS,
): Flow<Boolean> = channelFlow {
    var current: Boolean? = null
    validated.distinctUntilChanged().collectLatest { ok ->
        val wanted = !ok
        val now = current
        if (now == null) {
            current = wanted
            send(wanted)
        } else if (wanted != now) {
            delay(if (wanted) lossDelayMs else returnDelayMs)
            current = wanted
            send(wanted)
        }
    }
}.distinctUntilChanged()
