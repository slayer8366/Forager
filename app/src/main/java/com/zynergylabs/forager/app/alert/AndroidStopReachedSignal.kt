package com.zynergylabs.forager.app.alert

import android.content.Context
import com.zynergylabs.forager.app.domain.routenav.StopReachedSignal

/**
 * The short buzz at a route's stop (dispatch 2026-11-03-03, A4: "the phone gives one short vibration"), issued as the off-track
 * alert's vibration is ([vibrateForAlert], the owner's "Use the same method as off track notifications", A7), with notification
 * usage so it obeys silent mode (A8, `overridesSilence = false`). Vibration only: nothing is posted, since the navigation's own
 * ongoing notification and the screen carry the words.
 */
class AndroidStopReachedSignal(context: Context) : StopReachedSignal {
    private val appContext = context.applicationContext

    override fun signal() = vibrateForAlert(appContext, overridesSilence = false, pattern = ROUTE_STOP_VIBRATION_PATTERN_MILLIS)
}

/** One short buzz: off-track's pulse, once (A4: "one short vibration"). */
internal val ROUTE_STOP_VIBRATION_PATTERN_MILLIS = longArrayOf(0L, 250L)
