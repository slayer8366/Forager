package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What [ReturnWatch] tells a screen. [trackId] is the recording it is for, or `null` when it is
 * for none. A screen copies [isReturning] and [isOffTrack] only when [trackId] is the track it is
 * itself recording; see `TrackRecordingViewModel`.
 *
 * [isBegun] is **the live answer to "is a recording running"** (dispatch 2026-09-28-400, Amendment
 * 3): true only between the recording service's `begin` and `end`. An open track row is not that
 * answer, because a killed process leaves the same row. A screen opened while this is true takes
 * the recording up, in [mode], the mode the service is recording in. A [trackId] with [isBegun]
 * false is a Return accepted early, before the service has begun, and is not a recording.
 *
 * [returnToStart] is the watch's own measurement, the one the decision was made on. The screen's
 * button shows a distance the ViewModel works out for itself from its own fixes (dispatch
 * 2026-09-28-400, Amendment 2, the planner's ruling on question 1), so nothing under `ui/` reads
 * this field today. It is here because the decision cannot be checked without it.
 *
 * [arrivedAfterReturn] (RECORD -844, -845): the last return on [trackId] ended because the walker
 * reached the start. [isReturning] is false from that moment, so this is
 * what Back by and the sundown alerts read beside it ([ReturnWatch.arrivalJudgedFor]) to go on
 * judging arrival exactly as they did while the return ran. Cleared by the next Return and by the end
 * of the recording; the walker closing the HUD afterwards leaves it set, since the return had already
 * ended by arrival, not by the walker.
 */
data class ReturnWatchState(
    val trackId: String? = null,
    val isBegun: Boolean = false,
    val mode: TrackRecordingMode? = null,
    val isReturning: Boolean = false,
    val isOffTrack: Boolean = false,
    val returnToStart: ReturnToStartInfo? = null,
    val arrivedAfterReturn: Boolean = false,
)

/**
 * Watches a walker on the way back to a recording's start, and delivers the off-track alert.
 *
 * It owns what used to be `TrackRecordingViewModel`'s: the returning flag, the start point and
 * the one [AlertDelivery.deliver] call. The decision is [OffTrackJudge]'s (dispatch 2026-09-28-425,
 * plan task T21): the distance from the path walked out, not, as before, three readings' distance
 * to the start rising. The distance to the start ([ComputeReturnToStartUseCase]) is still measured
 * and published, for nothing but [ReturnWatchState.returnToStart].
 *
 * ## Why it exists (dispatch 2026-09-28-400, Amendment 2; the owner chose "Option B")
 *
 * While all of that was the ViewModel's, it died with the Activity. A recording swiped away from
 * recents went on being recorded by `TrackRecordingService`, and nothing would say "Off track"
 * again for it (see [AlertDelivery]'s header, which named the hole). This object is held by
 * `AppContainer`, for the life of the process, and driven by the service, which is the thing that
 * keeps the process alive and has the continuous stream of fixes. The ViewModel calls it for
 * Return and copies its state. Clearing the ViewModel no longer touches the decision.
 *
 * ## Who calls what
 *
 * - **The recording service** calls [begin] when a recording starts, [onFix] for **every raw fix**
 *   its collector receives, [onKeptPoint] for every point its sampler keeps for the track, and
 *   [end] when the recording stops or the service is destroyed. Nothing else begins it, and nothing
 *   else feeds it.
 * - **The ViewModel** calls [startReturn] and [stopReturn] from the screen's Return control, and
 *   [setStartPoint] whenever the start point it shows changes. It collects [state].
 *
 * **Two streams, two uses.** The kept points ([onKeptPoint]) are the track as it is stored, and the
 * ones kept before Return, behind the start point, are the path a return is measured against: the
 * track as it stood when Return was tapped, with its not-yet-written tail, so not read back from the
 * store, which lags by up to 30 s. The raw fixes ([onFix]), about one a second, are the readings the
 * path is measured from; [OffTrackJudge] skips the network ones itself.
 *
 * ## A return asked for before the service has begun
 *
 * The service begins a moment after the Record tap: the ViewModel creates the track row,
 * `MainActivity` sends the start, the service handles it. A Return tapped inside that moment must
 * not be lost (Amendment 2, ruling 3). So [startReturn] and [setStartPoint] are accepted for a
 * track before [begin], and kept if [begin] then names the same track. If it names another, they
 * are dropped. Once begun for one track, calls that name another are refused.
 *
 * ## Two callers at once
 *
 * The service's collector runs on a background thread and the ViewModel on the main thread, so
 * unlike the ViewModel's version this has two writers. Everything the object holds is read and
 * written inside one lock, the judge's reading included, so two fixes arriving together cannot
 * both decide to alert. [state] is a
 * `StateFlow`, which is safe to read from any thread. [AlertDelivery.deliver] is called after the
 * lock is released: it posts a notification and vibrates, and nothing here should wait on that.
 *
 * ## What it keeps
 *
 * The kept points of the recording it is begun for, which are the stored track: a few thousand at
 * the four-hour cap. A return snapshots them; a new recording, or the end of this one, drops them.
 *
 * ## Left able to take the sundown alerts, which are not built
 *
 * The two sundown alerts (plan task T2) need the same things this has: a holder that outlives the
 * Activity, the service's lifetime, and [AlertDelivery]. Nothing for them is built here.
 */
class ReturnWatch(
    private val computeReturnToStart: ComputeReturnToStartUseCase,
    private val alertDelivery: AlertDelivery,
    /**
     * Settings' "Off-track reminder" checkbox (dispatch 2026-09-28-626; Amendment 1, RECORD -627):
     * off, a decision to alert is not delivered. Read at the moment of the decision, so
     * a change mid-walk applies to the next stray. Only the delivery is gated: the judge still runs
     * and [ReturnWatchState.isOffTrack] still colours the Return button. Defaults to on, the
     * setting's own default.
     */
    private val isReminderOn: () -> Boolean = { true },
) {
    private val _state = MutableStateFlow(ReturnWatchState())
    val state: StateFlow<ReturnWatchState> = _state.asStateFlow()

    private val lock = Any()

    // Everything below is read and written only while holding [lock].
    private var trackId: String? = null
    private var begun = false
    private var mode: TrackRecordingMode? = null
    private var returning = false
    private var offTrack = false
    private var start: TrackPoint? = null
    private var lastInfo: ReturnToStartInfo? = null

    // RECORD -845, "Must leave first": whether a GPS fix has put the walker outside the arrival circle
    // since Return was tapped. Arrival counts only once this is true.
    private var leftArrivalCircle = false

    // RECORD -844: see [ReturnWatchState.arrivedAfterReturn].
    private var arrivedAfterReturn = false

    // The kept points of the track the watch is for, oldest first: the stored track, tail included.
    private val keptPoints = mutableListOf<TrackPoint>()

    // The kept points as they stood when Return was tapped: the path the return is measured against.
    private var pathAtReturn: List<TrackPoint> = emptyList()

    // The return's judge, made at its first measurable fix with the start point in front of the
    // path: the start can reach the watch a moment after Return does. Null whenever no return is
    // under way, so a new return starts with a fresh judge, armed.
    private var judge: OffTrackJudge? = null

    /** How many kept points the path a return is measured against has. For tests. */
    internal val pathPointCount: Int get() = synchronized(lock) { pathAtReturn.size }

    /**
     * The service has started recording [trackId], in [mode]. Anything accepted early for this
     * same track is kept; anything held for another track is dropped.
     */
    fun begin(trackId: String, mode: TrackRecordingMode) = synchronized(lock) {
        if (this.trackId != trackId) forget()
        this.trackId = trackId
        this.mode = mode
        begun = true
        publish()
    }

    /**
     * The recording has ended. With a [trackId], ends only if the watch is for that track, so a
     * late stop for an old recording cannot end a new one. With `null`, ends whatever is begun:
     * the service's `onDestroy`, which has no track id left to name.
     */
    fun end(trackId: String?) {
        synchronized(lock) {
            if (trackId == null && !begun) return
            if (trackId != null && trackId != this.trackId) return
            forget()
            this.trackId = null
            mode = null
            begun = false
            publish()
        }
    }

    /**
     * The walker is now heading back to the start of [trackId]: the only state the off-track
     * check runs in. Returns `false`, changing nothing, if the watch is begun for another track.
     * A new return snapshots the kept points as its path and starts with a fresh judge, armed.
     */
    fun startReturn(trackId: String): Boolean = synchronized(lock) {
        if (!holdFor(trackId)) return@synchronized false
        pathAtReturn = keptPoints.toList()
        judge = null
        returning = true
        offTrack = false
        leftArrivalCircle = false
        arrivedAfterReturn = false
        publish()
        true
    }

    /** The return is over, the recording is not. Drops the path and the judge. Ignored for a track the watch is not for. */
    fun stopReturn(trackId: String) {
        synchronized(lock) {
            if (trackId != this.trackId) return
            pathAtReturn = emptyList()
            judge = null
            returning = false
            offTrack = false
            leftArrivalCircle = false
            publish()
        }
    }

    /**
     * Where [trackId] started, as the screen shows it: the origin waypoint once it exists, the
     * first breadcrumb before that. The watch keeps the last one it was given, so it still has it
     * after the ViewModel that gave it is gone. Ignored if the watch is begun for another track.
     */
    fun setStartPoint(trackId: String, start: TrackPoint) = synchronized(lock) {
        if (!holdFor(trackId)) return@synchronized
        this.start = start
    }

    /**
     * One point the service's sampler kept for the track: what is stored, in the order it is
     * stored. Only while begun, as [onFix] is.
     */
    fun onKeptPoint(point: TrackPoint) = synchronized(lock) {
        if (!begun) return@synchronized
        keptPoints += point
    }

    /**
     * One raw fix from the service's collector. Not a reading until the service has begun the
     * watch, and not measurable until there is a start point.
     *
     * Every measurable fix updates [ReturnWatchState.returnToStart]. While returning it is also
     * [OffTrackJudge]'s reading, against the start and the path as they stood at Return, and an
     * [Alert] is delivered on the reading the judge says goes off: once per stray (the owner's
     * choice), no repeat while it lasts.
     *
     * [provider] is where the platform said [current] came from (dispatch 2026-09-28-527), passed beside
     * it and never stored.
     *
     * A GPS fix at the start, once the walker has been away from it since Return, ends the return
     * instead ([hasArrivedAfterLeaving], RECORD -844, -845): that fix is not judged, and none after it
     * is, until the next Return.
     */
    fun onFix(current: TrackPoint, provider: FixProvider) {
        val alert = synchronized(lock) {
            if (!begun) return
            val start = start ?: return
            val info = computeReturnToStart(current, start)
            lastInfo = info
            var shouldAlert = false
            if (returning && hasArrivedAfterLeaving(current, provider, start)) {
                endReturnOnArrival()
            } else if (returning) {
                val verdict = (judge ?: OffTrackJudge(listOf(start) + pathAtReturn).also { judge = it }).next(current, provider)
                offTrack = verdict.isOffTrack
                shouldAlert = verdict.alert
            }
            publish()
            shouldAlert
        }
        if (alert && !isReminderOn()) return
        if (alert) {
            // overridesSilence = false: owner ruling, 2026-09-11, reversing the original. Straying
            // is often deliberate, so off-track respects a phone the user silenced. See
            // AlertDelivery's own doc comment.
            alertDelivery.deliverReporting(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false))
        }
    }

    /**
     * Whether Back by and the sundown alerts should judge arrival for [trackId] (RECORD -844, -845): while
     * a return on it is under way, as before, and also once that return has ended by arrival. Both read
     * this one answer (`AppContainer`), so their arrival behaviour is what it was when a return never
     * ended on its own.
     */
    fun arrivalJudgedFor(trackId: String): Boolean =
        state.value.let { it.trackId == trackId && (it.isReturning || it.arrivedAfterReturn) }

    /**
     * RECORD -844 and -845: the walker is back at the start. The same test Back by and the sundown alerts
     * use ([hasArrived]: within the larger of 15 m and twice the fix's accuracy, straight line), on a GPS
     * fix only, as theirs is ([FixProvider.mayAct]); and only once a GPS fix has put the walker outside
     * that circle since Return was tapped ("Must leave first"), so a Return tapped at the start does not
     * end at once. Updates [leftArrivalCircle]. Call with [lock] held, while returning.
     */
    private fun hasArrivedAfterLeaving(current: TrackPoint, provider: FixProvider, start: TrackPoint): Boolean {
        if (!provider.mayAct) return false
        val inside = hasArrived(GeoDistance.metersBetween(LatLng(current.lat, current.lng), LatLng(start.lat, start.lng)), current.accuracyMeters)
        if (!inside) {
            leftArrivalCircle = true
            return false
        }
        return leftArrivalCircle
    }

    /**
     * Ends the return because the walker arrived (RECORD -844): off-track stops watching, the path and the
     * judge go, and the recording carries on. Call with [lock] held.
     */
    private fun endReturnOnArrival() {
        pathAtReturn = emptyList()
        judge = null
        returning = false
        offTrack = false
        leftArrivalCircle = false
        arrivedAfterReturn = true
    }

    /** Makes the watch be for [trackId] if it may be: already is, or not begun yet. Call with [lock] held. */
    private fun holdFor(trackId: String): Boolean {
        if (this.trackId == trackId) return true
        if (begun) return false
        forget()
        this.trackId = trackId
        return true
    }

    /** Drops everything held about a track. Call with [lock] held. */
    private fun forget() {
        returning = false
        offTrack = false
        leftArrivalCircle = false
        arrivedAfterReturn = false
        start = null
        lastInfo = null
        keptPoints.clear()
        pathAtReturn = emptyList()
        judge = null
    }

    /** Call with [lock] held, so the published states are in the order the changes happened. */
    private fun publish() {
        _state.value = ReturnWatchState(
            trackId = trackId,
            isBegun = begun,
            mode = mode,
            isReturning = returning,
            isOffTrack = offTrack,
            returnToStart = lastInfo,
            arrivedAfterReturn = arrivedAfterReturn,
        )
    }
}
