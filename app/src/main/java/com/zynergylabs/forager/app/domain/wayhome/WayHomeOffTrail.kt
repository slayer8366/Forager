package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.OFF_TRACK_HOLD_MILLIS
import com.zynergylabs.forager.app.domain.OFF_TRACK_LINE_METERS
import com.zynergylabs.forager.app.domain.OFF_TRACK_MIN_READINGS
import com.zynergylabs.forager.app.domain.OFF_TRACK_REARM_MILLIS
import com.zynergylabs.forager.app.domain.isNetworkProviderFix
import com.zynergylabs.forager.app.domain.mayAct
import com.zynergylabs.forager.app.domain.metersToPolyline
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint

/** What the quiet off-trail state shows (plan step 7 PR 3; RECORD -1631). */
enum class OffTrailState {
    /** On the trail line, or on the walker's own track from the trailhead to the start: the HUD's usual line. */
    ON,

    /** Off the trail for [OFF_TRACK_HOLD_MILLIS]: the trail line dims and the HUD reads the off-trail notice. Quiet. */
    OFF,

    /** Back on for [OFF_TRACK_REARM_MILLIS] after [OFF]: "Back on trail", for [BACK_ON_TRAIL_SHOW_MILLIS]. */
    BACK_ON,
}

/**
 * How long "Back on trail" shows (RECORD -1631, the owner: "Back on trail" for about 5 s). **Provisional**, as -1631
 * calls every step 7 timing; no existing figure was a display duration (dispatch 2026-10-26-13 Amendment 1, RECORD
 * -1676). Timed on the fixes' own clock: a walker whose fixes stop sees the HUD's stale-fix line in its place anyway.
 */
const val BACK_ON_TRAIL_SHOW_MILLIS = 5_000L

// FAIL-FIRST STUB: removed in the next commit.
private val STUB = true

/**
 * The quiet off-trail state (dispatch 2026-10-26-13, plan step 7 PR 3; RECORD -1631, -1676; the step 7 report,
 * `docs/audits/2026-10-25-04-off-trail-and-wrong-turn-report.md`, sections 3.2, 3.3 and 5). Pure: its time is the
 * fixes' own, as [com.zynergylabs.forager.app.domain.OffTrackJudge]'s is, so a replay runs it as the phone did. No
 * alert, sound or vibration comes from here; that is the far-from-both alert's, in `ReturnWatch`.
 *
 * ## The rule, with the off-track alert's figures and no new one but [BACK_ON_TRAIL_SHOW_MILLIS]
 *
 * - **On** is within [OFF_TRACK_LINE_METERS] plus the reading's accuracy of any of the [next] call's `onLines`. The way
 *   home passes its trail part and its track from the trailhead to the start, so walking your own track back to the
 *   car never reads "off trail".
 * - **Off** is further than that from every one of them for [OFF_TRACK_HOLD_MILLIS], over at least
 *   [OFF_TRACK_MIN_READINGS] counting readings, none back on in between. At Return too: a walker who is off the trail
 *   when they tap it gets the state after the same hold (the report's 8.4, one rule).
 * - **Back on** is within the line for [OFF_TRACK_REARM_MILLIS] after being off: [OffTrailState.BACK_ON], which turns
 *   to [OffTrailState.ON] on the first counting reading [BACK_ON_TRAIL_SHOW_MILLIS] after it began.
 * - **Only GPS readings count,** as for the off-track alert: a network reading, or one whose provider is unknown, is
 *   skipped and the state stays as it was.
 * - **No lines decides nothing:** the state stays as it was.
 *
 * Takes plain lines rather than a [WayHome], so the way to a waypoint can be given the same state later (step 6 PR 4).
 */
class WayHomeOffTrail {
    private var offSinceMillis: Long? = null
    private var offReadings = 0
    private var onSinceMillis: Long? = null
    private var backOnAtMillis: Long? = null

    var state: OffTrailState = OffTrailState.ON
        private set

    /** One reading, judged against [onLines], each in walking order. [provider] is the live reading's, as [com.zynergylabs.forager.app.domain.OffTrackJudge.next] takes it. */
    fun next(reading: TrackPoint, provider: FixProvider?, onLines: List<List<LatLng>>): OffTrailState {
        if (STUB) return state
        val counts = provider?.mayAct ?: !reading.isNetworkProviderFix()
        val lines = onLines.filter { it.isNotEmpty() }
        if (!counts || lines.isEmpty()) return state
        val t = reading.timestampEpochMillis
        if (state == OffTrailState.BACK_ON && t - (backOnAtMillis ?: t) >= BACK_ON_TRAIL_SHOW_MILLIS) {
            state = OffTrailState.ON
            backOnAtMillis = null
        }
        val limit = OFF_TRACK_LINE_METERS + (reading.accuracyMeters?.toDouble() ?: 0.0)
        val here = LatLng(reading.lat, reading.lng)
        if (lines.all { metersToPolyline(here, it) > limit }) {
            onSinceMillis = null
            val since = offSinceMillis ?: t.also { offSinceMillis = it; offReadings = 0 }
            offReadings++
            if (state != OffTrailState.OFF && t - since >= OFF_TRACK_HOLD_MILLIS && offReadings >= OFF_TRACK_MIN_READINGS) {
                state = OffTrailState.OFF
                backOnAtMillis = null
            }
        } else {
            offSinceMillis = null
            offReadings = 0
            if (state == OffTrailState.OFF) {
                val since = onSinceMillis ?: t.also { onSinceMillis = it }
                if (t - since >= OFF_TRACK_REARM_MILLIS) {
                    state = OffTrailState.BACK_ON
                    backOnAtMillis = t
                    onSinceMillis = null
                }
            }
        }
        return state
    }

    /** A new return, or none: back to [OffTrailState.ON] with nothing held. */
    fun reset() {
        offSinceMillis = null
        offReadings = 0
        onSinceMillis = null
        backOnAtMillis = null
        state = OffTrailState.ON
    }
}
