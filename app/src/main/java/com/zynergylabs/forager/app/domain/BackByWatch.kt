package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delivers a recording's "Back by" alert (dispatch 2026-09-28-645, plan task T15; Amendments 1 and
 * 2, RECORD -646 and -647). See [BackByChoice] for the owner's path.
 *
 * ## Who drives it
 *
 * Held by `AppContainer` for the life of the process and driven by `TrackRecordingService`, the way
 * [SundownWatch] and [ReturnWatch] are, so it fires with the app swiped away and needs **no
 * exact-alarm permission**: [begin] when a recording starts, [onFix] for every raw fix, [tick] on the
 * service's own 15 s timer, [end] when the recording stops or the service is destroyed. Since dispatch
 * 2026-09-28-796 (RECORD -797) also on a GPS fix once the time has come ([onFix] returns `true`), and on
 * an inexact allow-while-idle wake-up alarm at the time ([onAlarm]): the timer alone stalled with the
 * screen off and Back by never posted on the L3 walk. The screen
 * calls [set] and [clear] (through [BackByControl]); the alert's two buttons reach the service, which
 * calls [imBack] and [later]. Nothing else drives it.
 *
 * ## What a tick does
 *
 * With a time set: if the walker has arrived, the reminder ends; else, at or past the time and not
 * yet alerted for it, one alert ([AlertKind.BACK_BY], overriding silence like the sunset alert).
 * Every tick republishes [shown] with the clock, so the strip's last-hour window opens on time.
 *
 * ## How it ends: only by the walker's choice (Amendment 1)
 *
 * "I'm back" on the alert ([imBack]), "Clear" in the menu ([clear]), stopping the recording ([end]),
 * or **arrival after Return**: the sundown alerts' rule exactly ([SundownWatch]'s arrival check) —
 * Return tapped, a fresh GPS fix ([fixFreshness] not LOST, provider GPS), within [hasArrived] of the
 * start (the origin waypoint, or the track's first point before there is one). No "back at the car"
 * is inferred without Return: at the start of a recording the walker is already at the start, and a
 * loop past the car would cancel a safety reminder by mistake (the owner: no inferred rule). Unlike
 * the sundown watch, arrival is checked whether or not the sundown alerts are switched on: it belongs
 * to this reminder, not to that setting.
 *
 * ## A set before the service has begun
 *
 * The service begins a moment after Record. A Back by set inside that moment is kept if [begin] then
 * names the same track, as [ReturnWatch] keeps an early Return; another track drops it. Once begun
 * for one track, calls naming another are refused.
 *
 * ## What it keeps
 *
 * The time, the time it last alerted for, and the newest GPS fix, in memory, for one recording. Not in
 * DataStore: a process death ends the recording (a sticky restart arrives with no intent and records
 * nothing; `EndAbandonedTracksUseCase`), so a stored time would have nothing to drive it. Reported
 * to the owner as a gap shared with the sundown alerts (the -645 verify report, item 6).
 *
 * ## Threads
 *
 * As [SundownWatch]: fields under [lock], ticks serialised by [tickMutex], delivery outside [lock].
 */
class BackByWatch(
    private val alertDelivery: AlertDelivery,
    private val clock: CurrentTimeProvider,
    /** The track through the read seam ([TrackRepository.getById]), for its first point. */
    private val readTrack: suspend (String) -> Result<Track?>,
    /** A waypoint by id ([WaypointRepository.getById]), for the track's origin. */
    private val readWaypoint: suspend (String) -> Result<Waypoint?>,
    /** Whether the walker is heading back on this track ([ReturnWatch]'s state). */
    private val isReturning: (String) -> Boolean,
    private val errorLog: ErrorLog,
    /**
     * Where each set, evaluation, alert and end is written (dispatch 2026-09-28-796): `FileBackByRecord` in
     * the app. Written outside [lock]. Never positions.
     */
    private val record: BackByRecord = NoBackByRecord,
    /**
     * The wake-up at the set time (dispatch 2026-09-28-796; the owner, RECORD -797): asked for on every
     * set and "+30 min", cancelled whenever the reminder ends. Called outside [lock].
     */
    private val alarms: WakeUpAlarms = NoWakeUpAlarms,
) : BackByControl {
    private val lock = Any()
    private val tickMutex = Mutex()

    // Read and written only while holding [lock].
    private var trackId: String? = null
    private var begun = false
    private var backByAt: Long? = null
    private var alertedFor: Long? = null
    private var newestGpsFix: TrackPoint? = null

    private val _shown = MutableStateFlow<BackByShown?>(null)

    /** The back-by time for the recording being watched, or `null` when none is set. */
    override val shown: StateFlow<BackByShown?> = _shown.asStateFlow()

    /** The service has started recording [trackId]. A time set early for this same track is kept; anything else is dropped. */
    fun begin(trackId: String) {
        val kept = synchronized(lock) {
            if (this.trackId != trackId) forget()
            this.trackId = trackId
            begun = true
            publish(clock.nowEpochMillis())
            backByAt
        }
        record.write(BackByRecordEvent.Started(trackId, kept))
    }

    /**
     * The recording has ended. With a [trackId], only if it is the one watched; with `null`, whatever
     * is begun: the service's `onDestroy`.
     */
    fun end(trackId: String?) {
        val ended = synchronized(lock) {
            if (trackId == null && !begun) return
            if (trackId != null && trackId != this.trackId) return
            val endedId = this.trackId.takeIf { backByAt != null }
            forget()
            endedId
        } ?: return
        record.write(BackByRecordEvent.Ended(ended, if (trackId == null) BackByEndReason.SERVICE_DESTROYED else BackByEndReason.STOP))
        cancelAlarm(ended)
    }

    /**
     * One raw fix from the service's collector. Only a GPS fix can decide arrival ([FixProvider.mayAct]).
     *
     * Returns `true` when the time has come and its alert has not been given: the service then evaluates
     * at once, without waiting for its timer (dispatch 2026-09-28-796; the owner, RECORD -797). A fix is
     * proof the service is running; the timer alone counts only time the processor is awake, which with
     * the screen off it often is not, and on the L3 walk Back by never posted.
     */
    fun onFix(fix: TrackPoint, provider: FixProvider): Boolean = synchronized(lock) {
        if (trackId == null) return@synchronized false
        if (provider.mayAct) newestGpsFix = fix
        val at = backByAt ?: return@synchronized false
        clock.nowEpochMillis() >= at && alertedFor != at
    }

    /**
     * The wake-up alarm arrived (dispatch 2026-09-28-796): recorded, then one evaluation, as the timer's.
     * With nothing watched (a process started fresh for an alarm left from an ended one) it records that
     * and does nothing else.
     */
    suspend fun onAlarm() {
        val watched = synchronized(lock) { trackId?.takeIf { backByAt != null } }
        record.write(BackByRecordEvent.AlarmDelivered(watched))
        if (watched != null) tick(EvaluationTrigger.ALARM)
    }

    private fun armAlarm(trackId: String, at: Long) {
        val accepted = try {
            alarms.schedule(WakeUpAlarm.BACK_BY, at)
        } catch (e: Exception) {
            errorLog.w(TAG, "The back-by wake-up could not be scheduled; the timer and fixes still evaluate it.", e)
            false
        }
        record.write(BackByRecordEvent.AlarmScheduled(trackId, at, accepted))
    }

    private fun cancelAlarm(trackId: String) {
        try {
            alarms.cancel(WakeUpAlarm.BACK_BY)
        } catch (e: Exception) {
            errorLog.w(TAG, "The back-by wake-up could not be cancelled; it will find nothing set.", e)
        }
        record.write(BackByRecordEvent.AlarmCancelled(trackId))
    }

    override fun set(trackId: String, atEpochMillis: Long): Boolean {
        var watched: String? = null
        val accepted = synchronized(lock) {
            if (this.trackId != trackId) {
                if (begun) {
                    watched = this.trackId
                    return@synchronized false
                }
                forget()
                this.trackId = trackId
            }
            backByAt = atEpochMillis
            alertedFor = null
            publish(clock.nowEpochMillis())
            true
        }
        record.write(BackByRecordEvent.Set(trackId, atEpochMillis, accepted, watched))
        if (accepted) armAlarm(trackId, atEpochMillis)
        return accepted
    }

    override fun clear(trackId: String) = endBy(trackId, BackByEndReason.CLEAR)

    /** A choice from the menu with no recording on the screen (dispatch 2026-09-28-796): nothing is set, and that is recorded. */
    override fun setWithNoRecording() {
        record.write(BackByRecordEvent.SetWithNoRecording)
    }

    /** "I'm back" on the alert: the reminder ends; the recording carries on. */
    fun imBack(trackId: String) = endBy(trackId, BackByEndReason.IM_BACK)

    private fun endBy(trackId: String, reason: BackByEndReason) {
        val wasSet = synchronized(lock) {
            if (this.trackId != trackId) return
            val was = backByAt != null
            endReminder()
            was
        }
        if (wasSet) {
            record.write(BackByRecordEvent.Ended(trackId, reason))
            cancelAlarm(trackId)
        }
    }

    /** "+30 min" on the alert: the time moves to now plus [BACK_BY_LATER_MILLIS], and alerts again then. */
    fun later(trackId: String) {
        val at = synchronized(lock) {
            if (this.trackId != trackId || backByAt == null) return
            val now = clock.nowEpochMillis()
            backByAt = now + BACK_BY_LATER_MILLIS
            alertedFor = null
            publish(now)
            backByAt
        } ?: return
        record.write(BackByRecordEvent.Later(trackId, at))
        armAlarm(trackId, at)
    }

    /** One evaluation. See the class header. */
    suspend fun tick(trigger: EvaluationTrigger = EvaluationTrigger.TIMER) = tickMutex.withLock {
        val (id, at, alerted, gpsFix) = synchronized(lock) {
            val id = trackId ?: return@withLock
            val at = backByAt ?: return@withLock
            Snapshot(id, at, alertedFor, newestGpsFix)
        }
        val now = clock.nowEpochMillis()

        if (hasArrivedAfterReturn(id, gpsFix, now)) {
            val ended = synchronized(lock) { (trackId == id && backByAt == at).also { if (it) endReminder() } }
            if (ended) {
                record.write(BackByRecordEvent.Ended(id, BackByEndReason.ARRIVAL))
                cancelAlarm(id)
            }
            return@withLock
        }
        record.write(BackByRecordEvent.Evaluated(id, at, due = now >= at, trigger = trigger))

        val fire = now >= at && alerted != at
        val stillSet = synchronized(lock) {
            if (trackId != id || backByAt != at) return@synchronized false
            if (fire) alertedFor = at
            publish(now)
            true
        }
        if (!fire || !stillSet) return@withLock
        val outcome = alertDelivery.deliverReporting(
            Alert(AlertKind.BACK_BY, overridesSilence = true, backBy = BackByAlertDetail(trackId = id, backByAtEpochMillis = at)),
        )
        record.write(BackByRecordEvent.Fired(id, at, outcome))
        // A buzz Android drops is recorded as skipped, with its reason, as the sundown alerts record theirs (dispatch
        // 2026-09-28-685, merged with RECORD -687): never "issued".
        if (outcome != null && (!outcome.notificationPosted || !outcome.vibrated)) {
            errorLog.w(
                TAG,
                "The back-by alert was only partly delivered: notification ${outcome.notificationProblem ?: "posted"}, vibration ${outcome.vibrationProblem ?: outcome.vibrationSkipped?.let { "skipped: $it" } ?: "issued"}.",
                IllegalStateException("partial delivery of BACK_BY"),
            )
        }
    }

    /** [SundownWatch]'s arrival rule, without its alerts-enabled gate (see the class header). */
    private suspend fun hasArrivedAfterReturn(id: String, gpsFix: TrackPoint?, now: Long): Boolean {
        if (gpsFix == null || !isReturning(id)) return false
        if (fixFreshness(now - gpsFix.timestampEpochMillis) == FixFreshness.LOST) return false
        val track = readTrack(id).getOrElse { error ->
            errorLog.w(TAG, "Couldn't read track '$id' for arrival; the back-by reminder stays set.", error)
            return false
        } ?: return false
        val origin = track.originWaypointId?.let { originId ->
            readWaypoint(originId).getOrElse { error ->
                errorLog.w(TAG, "Couldn't read the origin waypoint '$originId'; arrival is measured to the track's first point.", error)
                null
            }
        }
        val start = origin?.let { LatLng(it.lat, it.lng) } ?: track.points.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: return false
        return hasArrived(GeoDistance.metersBetween(LatLng(gpsFix.lat, gpsFix.lng), start), gpsFix.accuracyMeters)
    }

    /** Ends the reminder, keeping the recording watched. Called under [lock]. */
    private fun endReminder() {
        backByAt = null
        alertedFor = null
        _shown.value = null
    }

    /** Publishes the time set, if any, with [now]. Called under [lock]. */
    private fun publish(now: Long) {
        val id = trackId
        val at = backByAt
        _shown.value = if (id != null && at != null) BackByShown(id, at, now) else null
    }

    private fun forget() {
        trackId = null
        begun = false
        backByAt = null
        alertedFor = null
        newestGpsFix = null
        _shown.value = null
    }

    private data class Snapshot(val trackId: String, val backByAt: Long, val alertedFor: Long?, val gpsFix: TrackPoint?)

    private companion object {
        const val TAG = "BackByWatch"
    }
}
