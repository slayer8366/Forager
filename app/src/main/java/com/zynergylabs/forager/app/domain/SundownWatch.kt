package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delivers a recording's three sundown alerts: the heads-up, the leave-by and the sunset alert
 * (dispatch 2026-09-28-516, plan tasks T2 and T13, the owner's choices in RECORD -513 to -515).
 *
 * ## Why it lives here, and who drives it
 *
 * The countdown on screen rides `TrackRecordingViewModel`'s scope, which dies when the app is
 * swiped away. This is held by `AppContainer` for the life of the process, as [ReturnWatch] is,
 * and driven by `TrackRecordingService`, which is what keeps the process alive while recording:
 * [begin] when a recording starts, [onFix] for every raw fix its collector receives, [tick] on its
 * own timer (and once at the first position), [end] when the recording stops or the service is
 * destroyed. Nothing else drives it. Since dispatch 2026-09-28-796 (the owner, RECORD -797: "Yes, same
 * fix (Recommended)") also a fix that finds the next alert's due time passed ([onFix] returns `true`),
 * and an inexact allow-while-idle wake-up alarm at that time ([onAlarm]): the 15 s timer counts only
 * time the processor is awake, and on the L3 walk the same timer left Back by unposted.
 *
 * ## What the screen reads from it ([shown], dispatch 2026-09-28-592, Amendment 1, RECORD -593)
 *
 * The sundown line on the map's strip and the navigation display (plan task T3) is published here,
 * as [shown], from the same tick that decides the alerts, so its "start back by" is the leave-by
 * alert's own time ([sundownLeaveByAt]) and never a second computation of it. The service and the
 * screen share this process (the service declares no `android:process`), so the recording ViewModel
 * collects it as it collects [ReturnWatch]'s state. The owner, allowing it: "Yes, allow it
 * (Recommended)". To that end every tick with a position computes and publishes the line, and the
 * two checks that used to end a tick first, the alerts turned off and arrival, now sit just before
 * deciding and delivering: the line stays with the alerts off (the switch "gates the notifications
 * only") and after arrival. **Every alert still fires at the moment it did** (the owner's
 * condition, `SundownWatchAlertMomentsTest`, written and run against `main` first): what the
 * decision remembers (the fired set, the held sunset, the hop band) is still written only when the
 * alerts are on and the walker has not arrived, and arrival is still only noticed with the alerts
 * on. A margin changed in Settings re-derives the line at once ([onMarginChanged]) from the last
 * tick's sunset and walk back, through the same function, and never decides or delivers anything;
 * the next tick reads the new margin for the alerts as before.
 *
 * ## What each tick works out
 *
 * - **Sunset**, from the newest fix of **any** kind, GPS or network. The owner, 2026-10-04:
 *   "Sunset moves about 4 s per km, and GPS-only would mean no sundown alert at all under canopy
 *   or indoors." An owner-approved exception to -510's rule that an approximate position never
 *   decides anything, for the sunset time only. With no live fix yet, the platform's last known
 *   position ([LastKnownLocationSource], -510's, reused) stands in, for the sunset only: a
 *   recording with no live reading at all still gets a sunset time and its alerts (the owner's
 *   condition for merging this, with -510 merged first).
 * - **The walk back**, from [returnWalkingTime] over the stored track (read through the read seam,
 *   so the network-fix exclusion and its counts are the ones the estimate's "at least" rules
 *   expect), its origin waypoint, and the newest **GPS** fix only. GPS is told from network by the
 *   provider the platform reported, passed beside each fix by the service ([FixProvider], dispatch
 *   2026-09-28-527); an unknown provider is not GPS. Until -527 the raw stream carried no provider
 *   and this used the timestamp rule ([isNetworkProviderFix]), which on the S22's walks disagreed with
 *   the provider 4 times in 1,969 fixes. Its freshness is [fixFreshness]
 *   of its age, so a GPS fix older than five minutes withholds the estimate. The hop band is
 *   carried between ticks, as [returnWalkingTime] asks.
 * - **The decision**, [DecideSundownAlertUseCase], with what has already fired.
 *
 * **Failure direction** (recorded in -516's report against RECORD -519, and still the direction): a
 * phone whose live fixes come only from network, or from a provider this app does not recognise, has
 * no GPS fix, so the walk back reads "unknown" and the leave-by time falls back to sunset minus the
 * margin. Safe in direction: no confident figure it does not have. The timestamp rule's own failure,
 * a phone whose GPS stamps milliseconds, no longer reaches this watch (dispatch 2026-09-28-527).
 *
 * **The sunset held.** [ComputeSundownCountdownUseCase] always reports the *next* sunset, so
 * at sunset it reports tomorrow's (and its search turns over a fraction of a second early). The
 * watch keeps the sunset it has been counting toward and refuses a jump to the next day, which is
 * what lets the sunset alert fire at all. A recording started after sunset
 * counts toward tomorrow's and alerts on nothing tonight: the night foray the owner described.
 *
 * ## When nothing more fires
 *
 * Once per recording each. After [end]. With the alerts turned off
 * ([SundownPreferencesRepository.getAlertsEnabled]). And, the owner's option B, once the walker
 * is heading back (Return tapped) and a fresh GPS fix is within [hasArrived]'s reach of the start
 * (the origin waypoint, or the track's first point before there is one): from then on the
 * recording is over as far as these alerts are concerned, even if the walker sets out again.
 * Without Return, only stopping the recording silences them.
 *
 * ## What it keeps, and for how long
 *
 * The fired set, the hop band, the two newest fixes and the arrived flag, for one recording, in
 * memory. Not in DataStore: a process death ends the recording too (a sticky restart arrives with
 * no intent and records nothing), so there is nothing to carry across one.
 *
 * ## Threads
 *
 * [onFix] is called from the service's collector and [tick] from its timer, on different
 * coroutines. The fields are read and written under [lock]; ticks are serialised by [tickMutex],
 * so two cannot decide the same alert. [AlertDelivery] is called outside [lock].
 */
class SundownWatch(
    private val alertDelivery: AlertDelivery,
    private val clock: CurrentTimeProvider,
    private val preferences: SundownPreferencesRepository,
    /** The track through the read seam ([TrackRepository.getById]). */
    private val readTrack: suspend (String) -> Result<Track?>,
    /** A waypoint by id ([WaypointRepository.getById]), for the track's origin. */
    private val readWaypoint: suspend (String) -> Result<Waypoint?>,
    /** Whether the walker is heading back on this track ([ReturnWatch]'s state). */
    private val isReturning: (String) -> Boolean,
    private val errorLog: ErrorLog,
    /**
     * The platform's last known position (dispatch 2026-09-28-510's source, reused, not copied):
     * the sunset position while no live fix has arrived. Never a walk-back position.
     */
    private val lastKnownLocation: LastKnownLocationSource = NoLastKnownLocation,
    private val computeCountdown: ComputeSundownCountdownUseCase = ComputeSundownCountdownUseCase(),
    private val decide: DecideSundownAlertUseCase = DecideSundownAlertUseCase(),
    /** Each evaluation, alert and wake-up (dispatch 2026-09-28-796): `FileSundownRecord` in the app. Written outside [lock]. */
    private val record: SundownRecord = NoSundownRecord,
    /**
     * The wake-up at the next alert's due time (dispatch 2026-09-28-796; the owner, RECORD -797: "Yes, same
     * fix (Recommended)"). Asked for after each decision, cancelled when nothing more is due. Called outside [lock].
     */
    private val alarms: WakeUpAlarms = NoWakeUpAlarms,
) {
    private val lock = Any()
    private val tickMutex = Mutex()

    // Read and written only while holding [lock].
    private var trackId: String? = null
    private var newestFix: TrackPoint? = null
    private var newestGpsFix: TrackPoint? = null
    private var fired: Set<SundownAlert> = emptySet()
    private var hopBand: HopBand = HopBand.NONE
    private var arrived = false

    // The next alert's due time, as the last decision worked it out, and the time the wake-up is set
    // for (dispatch 2026-09-28-796). [nextDueAt] is consumed by the fix that finds it passed.
    private var nextDueAt: Long? = null
    private var alarmAt: Long? = null

    // The sunset this recording counts toward: followed while ahead, held once passed. Null until
    // the first evaluation with a position.
    private var sunsetAt: Long? = null

    // What the last tick published the line from, for [onMarginChanged]. Null until a tick has a sunset.
    private var lineInputs: LineInputs? = null

    private val _shown = MutableStateFlow<SundownShown?>(null)

    /**
     * The sundown line for the recording being watched, or `null` when nothing is. Written under
     * [lock]; a [StateFlow], so safe to read from any thread.
     */
    val shown: StateFlow<SundownShown?> = _shown.asStateFlow()

    /** The service has started recording [trackId]. Everything from any earlier recording is dropped. */
    fun begin(trackId: String) = synchronized(lock) {
        forget()
        this.trackId = trackId
        _shown.value = SundownShown(trackId, SundownLine.FindingPosition)
    }

    /**
     * The darkness margin was changed in Settings to [minutes]. Re-derives the line's start-back
     * time at once from the last tick's sunset and walk back, with [sundownLeaveByAt], the function
     * the alerts decide on. Display only: nothing is decided, delivered or remembered for the alerts,
     * which read the margin themselves at the next tick. A tick already under way when this is called
     * may publish once more with the margin it read; the next tick agrees with this.
     */
    fun onMarginChanged(minutes: Int) = synchronized(lock) {
        val id = trackId ?: return@synchronized
        val inputs = lineInputs ?: return@synchronized
        val countdown = inputs.countdown.copy(turnaroundAtEpochMillis = inputs.countdown.sunsetAtEpochMillis - minutes * 60_000L)
        _shown.value = SundownShown(id, sundownLineFor(countdown, inputs.position, startBackAt(countdown, inputs.walkBack)))
    }

    /**
     * The recording has ended. With a [trackId], only if it is the one being watched, so a late
     * stop for an old recording cannot end a new one. With `null`, whatever is being watched: the
     * service's `onDestroy`.
     */
    fun end(trackId: String?) {
        val cancelFor = synchronized(lock) {
            if (trackId != null && trackId != this.trackId) return
            val armed = this.trackId?.takeIf { alarmAt != null }
            forget()
            armed
        } ?: return
        cancelAlarm(cancelFor)
    }

    /**
     * The wake-up alarm arrived (dispatch 2026-09-28-796): recorded, then one evaluation. With nothing
     * watched it records that and does nothing else.
     */
    suspend fun onAlarm() {
        val watched = synchronized(lock) {
            alarmAt = null
            trackId
        }
        record.write(SundownRecordEvent.AlarmDelivered(watched))
        if (watched != null) tick(EvaluationTrigger.ALARM)
    }

    /**
     * One raw fix from the service's collector. Ignored when nothing is being watched. Returns
     * `true` for the first fix of a recording, so the service can evaluate at once rather than at
     * its next tick: a recording started past the leave-by time alerts as soon as it has a place.
     *
     * [provider] is where the platform said [fix] came from (dispatch 2026-09-28-527), passed beside it
     * and never stored.
     */
    fun onFix(fix: TrackPoint, provider: FixProvider): Boolean = synchronized(lock) {
        if (trackId == null) return@synchronized false
        val first = newestFix == null
        newestFix = fix
        if (provider.mayAct) newestGpsFix = fix
        // Dispatch 2026-09-28-796 (RECORD -797): also `true` once the next alert's due time has passed,
        // so a fix evaluates at once instead of waiting for the timer, which stalls while the processor
        // sleeps. Consumed here, so one fix asks once; the evaluation works the next due time out again.
        val due = nextDueAt?.let { clock.nowEpochMillis() >= it } == true
        if (due) nextDueAt = null
        first || due
    }

    /** One evaluation. Publishes the line, and delivers at most one alert. See the class header for what it works out. */
    suspend fun tick(trigger: EvaluationTrigger = EvaluationTrigger.TIMER) = tickMutex.withLock {
        val (id, anyFix, gpsFix, alreadyFired, band, heldSunset, arrivedBefore) = synchronized(lock) {
            val id = trackId ?: return@withLock
            Snapshot(id, newestFix, newestGpsFix, fired, hopBand, sunsetAt, arrived)
        }
        // With no live reading yet, the last known position gives the sunset, and only the sunset:
        // the walk back reads gpsFix, which a last known position never becomes.
        val sunsetFrom = anyFix ?: lastKnownLocation.lastKnown()?.toTrackPoint() ?: run {
            publish(id, SundownLine.FindingPosition, null)
            return@withLock
        }
        val position = LatLng(sunsetFrom.lat, sunsetFrom.lng)
        record.write(SundownRecordEvent.Evaluated(id, trigger))

        val enabled = preferences.getAlertsEnabled().getOrElse { error ->
            errorLog.w(TAG, "Couldn't read whether the sundown alerts are on; they stay on, the default.", error)
            true
        }

        val now = clock.nowEpochMillis()
        val marginMinutes = preferences.getDarknessMarginMinutes().getOrElse { error ->
            errorLog.w(TAG, "Couldn't read the darkness margin; using the default of $DEFAULT_DARKNESS_MARGIN_MINUTES minutes.", error)
            DEFAULT_DARKNESS_MARGIN_MINUTES
        }
        val marginMillis = marginMinutes * 60_000L
        val computed = computeCountdown(now, position, sunsetFrom.timestampEpochMillis, marginMillis)
        if (computed !is SundownCountdown.Known) {
            publish(id, sundownLineFor(computed, position, null), null)
            return@withLock
        }
        // The countdown always looks for the *next* sunset, so at sunset it reports tomorrow's and
        // "past sunset" never happens; its search also turns to tomorrow a fraction of a second
        // before the sunset it reported a minute earlier. So once this recording has a sunset, a
        // new one is taken only if it is the same evening (the walker's position moves it by
        // seconds); a jump to the next day keeps the one held. Its civil dusk is the held sunset's
        // own (dispatch -592, Amendment 1): the copy used to keep the computed one, tomorrow's.
        val countdown = heldSunset?.takeIf { computed.sunsetAtEpochMillis - it > SAME_SUNSET_WITHIN_MILLIS }
            ?.let { computed.copy(sunsetAtEpochMillis = it, turnaroundAtEpochMillis = it - marginMillis, civilDuskAtEpochMillis = civilDuskAfter(it, position)) }
            ?: computed

        val track = readTrack(id).getOrElse { error ->
            errorLog.w(TAG, "Couldn't read track '$id' for the walk back; it is unknown for this evaluation.", error)
            null
        }
        val origin = track?.originWaypointId?.let { originId ->
            readWaypoint(originId).getOrElse { error ->
                errorLog.w(TAG, "Couldn't read the origin waypoint '$originId'; the walk back is measured to the track's first point.", error)
                null
            }
        }
        val freshness = gpsFix?.let { fixFreshness(now - it.timestampEpochMillis) } ?: FixFreshness.LOST
        val current = gpsFix?.let { LatLng(it.lat, it.lng) }

        // Arrival is noticed only with the alerts on, as it always was: the moments they fire at
        // depend on when it is noticed.
        var arrivedNow = arrivedBefore
        if (enabled && !arrivedBefore && track != null && gpsFix != null && current != null && freshness != FixFreshness.LOST && isReturning(id)) {
            val start = origin?.let { LatLng(it.lat, it.lng) } ?: track.points.firstOrNull()?.let { LatLng(it.lat, it.lng) }
            if (start != null && hasArrived(GeoDistance.metersBetween(current, start), gpsFix.accuracyMeters)) {
                synchronized(lock) { if (trackId == id) arrived = true }
                arrivedNow = true
            }
        }

        val estimate = track?.let { returnWalkingTime(it, origin, current, freshness, band) }
        val walkBack = when (estimate) {
            is ReturnWalkingTime.Estimate -> if (estimate.isAtLeast) WalkBack.AtLeast(estimate.walkingMillis) else WalkBack.About(estimate.walkingMillis)
            is ReturnWalkingTime.Withheld, null -> WalkBack.Unknown
        }
        publish(id, sundownLineFor(countdown, position, startBackAt(countdown, walkBack)), LineInputs(countdown, position, walkBack))

        // The alerts from here on. Off, or arrived: nothing is decided and nothing remembered, so
        // turning them on later finds what it always found.
        if (!enabled || arrivedNow) {
            nothingMoreDue(id)
            return@withLock
        }
        val decision = decide(countdown, walkBack.millisOrNull, alreadyFired)
        armForNext(id, nextSundownAlertDue(decision, countdown))

        val stillThisRecording = synchronized(lock) {
            if (trackId != id) return@synchronized false
            fired = decision.spent
            sunsetAt = countdown.sunsetAtEpochMillis
            if (estimate is ReturnWalkingTime.Estimate) hopBand = estimate.path.hopBand
            true
        }
        val kind = when (decision.fire) {
            SundownAlert.HEADS_UP -> AlertKind.HEADS_UP
            SundownAlert.LEAVE_BY -> AlertKind.LEAVE_BY
            SundownAlert.SUNSET -> AlertKind.SUNSET
            null -> return@withLock
        }
        if (!stillThisRecording) return@withLock
        val outcome = alertDelivery.deliverReporting(
            Alert(kind, overridesSilence = true, sundown = SundownAlertDetail(countdown.sunsetAtEpochMillis, walkBack, decision.leaveByAtEpochMillis ?: countdown.turnaroundAtEpochMillis, decidedAtEpochMillis = now)),
        )
        record.write(SundownRecordEvent.Fired(id, decision.fire!!, outcome))
        if (outcome != null && (!outcome.notificationPosted || !outcome.vibrated)) {
            errorLog.w(
                TAG,
                "The $kind alert was only partly delivered: notification ${outcome.notificationProblem ?: "posted"}, vibration ${outcome.vibrationProblem ?: outcome.vibrationSkipped?.let { "skipped: $it" } ?: "issued"}.",
                IllegalStateException("partial delivery of $kind"),
            )
        }
    }

    /**
     * Sets the wake-up for [next], the next alert not yet given and its due time, or cancels it when there
     * is none (dispatch 2026-09-28-796). The due time moves a little with each walk-back estimate, so the
     * alarm is asked for again only when it moves by more than [ALARM_RESCHEDULE_SLACK_MILLIS]: an alarm
     * a little early evaluates, finds nothing due, and sets the next.
     */
    private fun armForNext(id: String, next: Pair<SundownAlert, Long>?) {
        if (next == null) {
            nothingMoreDue(id)
            return
        }
        val (alert, at) = next
        val schedule = synchronized(lock) {
            if (trackId != id) return
            nextDueAt = at
            val set = alarmAt
            (set == null || kotlin.math.abs(set - at) > ALARM_RESCHEDULE_SLACK_MILLIS).also { if (it) alarmAt = at }
        }
        if (!schedule) return
        val accepted = try {
            alarms.schedule(WakeUpAlarm.SUNDOWN, at)
        } catch (e: Exception) {
            errorLog.w(TAG, "The sundown wake-up could not be scheduled; the timer and fixes still evaluate.", e)
            false
        }
        record.write(SundownRecordEvent.AlarmScheduled(id, alert, at, accepted))
    }

    /** Nothing more is due for [id] (all given, alerts off, or arrived): no next time, and the wake-up cancelled if set. */
    private fun nothingMoreDue(id: String) {
        val wasSet = synchronized(lock) {
            if (trackId != id) return
            nextDueAt = null
            (alarmAt != null).also { alarmAt = null }
        }
        if (wasSet) cancelAlarm(id)
    }

    private fun cancelAlarm(id: String) {
        try {
            alarms.cancel(WakeUpAlarm.SUNDOWN)
        } catch (e: Exception) {
            errorLog.w(TAG, "The sundown wake-up could not be cancelled; it will find nothing due.", e)
        }
        record.write(SundownRecordEvent.AlarmCancelled(id))
    }

    /** Publishes [line] for recording [id], if it is still the one watched. */
    private fun publish(id: String, line: SundownLine, inputs: LineInputs?) = synchronized(lock) {
        if (trackId != id) return@synchronized
        _shown.value = SundownShown(id, line)
        if (inputs != null) lineInputs = inputs
    }

    /**
     * The line's start-back time: the leave-by alert's time when the walk back is known, measured
     * or "at least" (the owner, Amendment 1: "Just "start back by 5:32""), else `null`.
     */
    private fun startBackAt(countdown: SundownCountdown.Known, walkBack: WalkBack): Long? =
        if (walkBack == WalkBack.Unknown) null else sundownLeaveByAt(countdown.turnaroundAtEpochMillis, walkBack.millisOrNull)

    private fun forget() {
        trackId = null
        newestFix = null
        newestGpsFix = null
        fired = emptySet()
        hopBand = HopBand.NONE
        arrived = false
        sunsetAt = null
        lineInputs = null
        nextDueAt = null
        alarmAt = null
        _shown.value = null
    }

    private data class Snapshot(
        val trackId: String,
        val anyFix: TrackPoint?,
        val gpsFix: TrackPoint?,
        val fired: Set<SundownAlert>,
        val hopBand: HopBand,
        val heldSunset: Long?,
        val arrived: Boolean,
    )

    private data class LineInputs(val countdown: SundownCountdown.Known, val position: LatLng, val walkBack: WalkBack)

    private companion object {
        const val TAG = "SundownWatch"

        /** Further than this from the held sunset is another day's, not a moved one: ~24 h apart, against seconds per km. */
        const val SAME_SUNSET_WITHIN_MILLIS = 12L * 60L * 60L * 1_000L

        /** See [armForNext]. */
        const val ALARM_RESCHEDULE_SLACK_MILLIS = 60_000L
    }
}

/**
 * The next sundown alert not yet given, and when it falls due, from [decision] (dispatch 2026-09-28-796):
 * the heads-up [SUNDOWN_HEADS_UP_LEAD_MILLIS] before the leave-by time, the leave-by time, and sunset,
 * the moments [DecideSundownAlertUseCase] decides on. `null` when all three are spent.
 */
internal fun nextSundownAlertDue(decision: SundownAlertDecision, countdown: SundownCountdown.Known): Pair<SundownAlert, Long>? {
    val leaveBy = decision.leaveByAtEpochMillis ?: countdown.turnaroundAtEpochMillis
    return listOf(
        SundownAlert.HEADS_UP to leaveBy - SUNDOWN_HEADS_UP_LEAD_MILLIS,
        SundownAlert.LEAVE_BY to leaveBy,
        SundownAlert.SUNSET to countdown.sunsetAtEpochMillis,
    ).filter { (alert, _) -> alert !in decision.spent }.minByOrNull { it.second }
}

/** The sundown line for recording [trackId] ([SundownWatch.shown]). */
data class SundownShown(val trackId: String, val line: SundownLine)
