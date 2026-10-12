package com.zynergylabs.forager.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.MainActivity
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.alert.BACK_BY_NOTIFICATION_ID
import com.zynergylabs.forager.app.alert.cancelRecordingAlerts
import com.zynergylabs.forager.app.domain.EvaluationTrigger
import com.zynergylabs.forager.app.diagnostics.WalkLogger
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationSampler
import com.zynergylabs.forager.app.domain.RecordingHalt
import com.zynergylabs.forager.app.domain.RecordingHaltReason
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.toTrackPoint
import com.zynergylabs.forager.app.domain.PressureReading
import com.zynergylabs.forager.app.domain.pressureForPoint
import com.zynergylabs.forager.app.location.hasLocationPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The foreground service that records a track: collects [com.zynergylabs.forager.app.domain.LocationTracker]
 * fixes, runs each through [LocationSampler] to decide what actually gets kept, and batches
 * accepted points into [com.zynergylabs.forager.app.domain.RecordTrackPointsUseCase] rather than writing one
 * row per fix — see that use case's owning [com.zynergylabs.forager.app.domain.TrackRepository] doc comment for
 * why a multi-hour recording needs batched writes at all.
 *
 * A foreground service with a persistent notification, not [android.app.job.JobScheduler] or
 * `WorkManager` (not currently a project dependency — see `docs/plans/mushroom-log-phase2-inaturalist-upload.md`
 * for the same check made independently for a different feature): this is continuous, user-initiated,
 * indefinite-duration work the user can see is running, which is exactly what a foreground service
 * is for and what the other two are not built for.
 *
 * Controlled by [ACTION_START]/[ACTION_STOP] intents rather than binding — Phase 1a builds this
 * service and its domain logic only; the UI that starts/stops it and observes live progress is
 * Phase 1c, once the map layer it would show breadcrumbs on is on the new renderer (see
 * `docs/navigation/forager-navigator-plan.md` §7).
 *
 * ## It also drives the return watch (dispatch 2026-09-28-400, Amendment 2)
 *
 * The off-track decision lives in [com.zynergylabs.forager.app.domain.ReturnWatch], held by the app's
 * container so that it outlives the Activity. This service is what makes it run: it begins the
 * watch when a recording starts, hands it **every raw fix** its collector receives (before the
 * sampler decides what to keep, because the decision's window is counted in raw fixes), and ends
 * it when the recording stops or the service is destroyed. Nothing else begins or feeds it. That
 * is why the alert still arrives after the app is swiped away from recents: this service is the
 * part that is still running.
 *
 * ## And the sundown watch (dispatch 2026-09-28-516)
 *
 * The same way, for [com.zynergylabs.forager.app.domain.SundownWatch]: begun with the recording,
 * handed every raw fix, evaluated on its own 15 s timer and once at the first fix, ended with the
 * recording or the service. The three sundown alerts arrive with the app swiped away for the same
 * reason the off-track alert does.
 *
 * ## And the back-by watch (dispatch 2026-09-28-645, plan task T15)
 *
 * The same way again, for [com.zynergylabs.forager.app.domain.BackByWatch]: begun with the
 * recording, handed every raw fix, evaluated on its own 15 s timer, ended with the recording or the
 * service. The back-by alert's two buttons, "I'm back" and "+30 min", are [ACTION_BACK_BY_IM_BACK]
 * and [ACTION_BACK_BY_LATER] sent to this service, as its own "Stop recording" is: this service is
 * what is still running when the app has been swiped away. No exact alarm: the timer is this
 * service's, which runs exactly as long as the recording does, the only time Back by exists.
 *
 * ## And the walk logger, in debug builds (dispatch 2026-09-28-532)
 *
 * Told when a recording starts and stops, and when the service is destroyed; it reads the phone and
 * acts on nothing. A no-op in release builds (`src/release`), and in debug builds only while the
 * Diagnostics screen's "Walk logger" switch is on.
 */
class TrackRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordingJob: Job? = null

    private val bufferMutex = Mutex()
    private val pendingPoints = mutableListOf<TrackPoint>()

    // The service only ever records one track at a time; recordingJob's presence alongside this id
    // is what stopRecording/onDestroy key off, rather than trusting the caller to pass the right id
    // back on ACTION_STOP.
    @Volatile private var currentTrackId: String? = null

    /** The barometer's latest reading while a recording runs (RECORD -867); `null` with no barometer or none yet. */
    @Volatile private var latestPressure: PressureReading? = null

    /**
     * Where each fix goes besides the sampler: the container's watches. A test replaces it, before
     * the start command, to hand the service a watch that throws (dispatch 2026-09-28-658, R1).
     */
    internal var watchesFor: (AppContainer) -> RecordingWatches = ::RecordingWatches

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                val modeName = intent.getStringExtra(EXTRA_MODE)
                val mode = TrackRecordingMode.entries.firstOrNull { it.name == modeName } ?: run {
                    // Dispatch 2026-09-28-658 (R2): the fallback is logged, so a renamed or missing
                    // mode recording at Balanced's spacing leaves a trace.
                    Log.w(TAG, "Unknown recording mode '$modeName' for track '$trackId'; recording at ${TrackRecordingMode.BALANCED.name}.")
                    TrackRecordingMode.BALANCED
                }
                if (trackId != null && recordingJob == null) {
                    // Defence in depth against the confirmed FGS-location-type crash: MainActivity
                    // already gates on this before ever sending ACTION_START (see its own
                    // hasLocationPermission), but this service must never crash regardless of how
                    // it gets told to start — see startForegroundWithLocationType()'s doc comment.
                    if (hasLocationPermission(this)) {
                        startRecording(trackId, mode)
                    } else {
                        Log.w(TAG, "Refusing to start recording for track '$trackId': no location permission.")
                        // RECORD -660 (item 4): the screen is told, and the track it made is ended.
                        halt(trackId, RecordingHaltReason.NO_LOCATION_PERMISSION)
                    }
                } else if (trackId != null && trackId != currentTrackId) {
                    // One recording at a time. A start for another track while one is running is
                    // dropped, and said so, because the track that start was for then gets no
                    // points (dispatch 2026-09-28-400, Amendment 2, ruling 8).
                    //
                    // A start that names the track already being recorded is not that, and says
                    // nothing (Amendment 3, step 3): a screen reopened after a swipe-away takes the
                    // recording up, and its Activity, being new, sends the start again for it.
                    Log.w(TAG, "Ignoring a start for track '$trackId': already recording track '$currentTrackId'.")
                }
            }
            ACTION_STOP -> stopRecording()
            ACTION_BACK_BY_IM_BACK, ACTION_BACK_BY_LATER -> onBackByAction(intent.action!!, intent.getStringExtra(EXTRA_TRACK_ID))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Whatever recording this service had begun the watch for is over with the service.
        (application as ForagerApplication).container.returnWatch.end(null)
        (application as ForagerApplication).container.sundownWatch.end(null)
        (application as ForagerApplication).container.backByWatch.end(null)
        // A recording that ends without a Stop leaves no alert behind either (RECORD -800).
        cancelRecordingAlerts(this)
        WalkLogger.of(this).onRecordingStopped()
        recordingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRecording(trackId: String, mode: TrackRecordingMode) {
        currentTrackId = trackId
        if (!startForegroundWithLocationType(trackId)) {
            // Refused (dispatch 2026-09-28-658, R7): logged inside, and the service stops, as the
            // permission branch above does. No watch was begun. RECORD -660 (item 4): the screen
            // is told, so it shows not recording, and the track it made is ended.
            currentTrackId = null
            halt(trackId, RecordingHaltReason.FOREGROUND_REFUSED)
            return
        }

        val container = (application as ForagerApplication).container
        val watches = watchesFor(container)
        container.returnWatch.begin(trackId, mode)
        container.sundownWatch.begin(trackId)
        container.backByWatch.begin(trackId)
        WalkLogger.of(this).onRecordingStarted(trackId)
        val sampler = LocationSampler(mode)
        var lastAccepted: TrackPoint? = null

        latestPressure = null
        recordingJob = scope.launch {
            // RECORD -864, -867: the barometer, only while recording. Each kept point carries the latest reading if
            // it is fresh (pressureForPoint); with no barometer the walk's heights are read from smoothed GPS.
            launch {
                container.pressureProvider.readings.collect { reading ->
                    if (reading == null) Log.i(TAG, "No barometer on this phone; track '$trackId' reads its heights from smoothed GPS.")
                    latestPressure = reading
                }
            }
            // Dispatch 2026-09-28-626: read Settings' "Off-track reminder" so the off-track rule's
            // synchronous read holds the stored value, even for a recording the system restarted
            // with no screen to have read it.
            launch {
                container.offTrackReminderPreferences.getEnabled().onFailure { error ->
                    Log.w(TAG, "Couldn't read whether the off-track reminder is on; the alert stays on, the default.", error)
                }
            }
            // Dispatch 2026-09-28-767: read the Battery saver switch, so a recording the system
            // restarted with no screen still asks at the stored interval; the tracker follows it.
            launch {
                container.batterySaverPreferences.getEnabled().onFailure { error ->
                    Log.w(TAG, "Couldn't read whether Battery saver is on; asking for a position every second, the default.", error)
                }
            }
            launch {
                container.locationTracker.fixes.collect { fix ->
                    when (fix) {
                        is LocationFix.Update -> {
                            val candidate = fix.toTrackPoint().copy(pressureHpa = pressureForPoint(latestPressure, SystemClock.elapsedRealtimeNanos()))
                            // Dispatch 2026-09-28-658 (R1): each watch call is guarded, as the
                            // sundown tick is. A watch that throws on this fix loses this fix; the
                            // point is still sampled and saved, and the recording goes on.
                            guardWatch("The return watch", candidate) { watches.returnOnFix(candidate, fix.provider) }
                            // Dispatch 2026-09-28-516: every raw fix to the sundown watch too, and
                            // an evaluation at once on the first, so a recording started past the
                            // leave-by time does not wait for the timer.
                            if (guardWatch("The sundown watch", candidate) { watches.sundownOnFix(candidate, fix.provider) } == true) {
                                launch { tickSundown(container, EvaluationTrigger.FIX) }
                            }
                            // Dispatch 2026-09-28-645 (RECORD -674, -687): every raw fix to the
                            // back-by watch, guarded as the two above are, so a back-by watch that
                            // throws loses this fix and nothing else.
                            // Dispatch 2026-09-28-796 (RECORD -797): and evaluated at once when its time has come, not
                            // only on the timer, which stalls while the processor sleeps.
                            if (guardWatch("The back-by watch", candidate) { watches.backByOnFix(candidate, fix.provider) } == true) {
                                launch { tickBackBy(container, EvaluationTrigger.FIX) }
                            }
                            if (sampler.shouldAccept(lastAccepted, candidate)) {
                                lastAccepted = candidate
                                // Dispatch 2026-09-28-425: the kept point to the watch too, which
                                // measures a return against the track as it stood at Return.
                                guardWatch("The return watch's kept point", candidate) { watches.returnOnKeptPoint(candidate) }
                                val shouldFlush = bufferMutex.withLock {
                                    pendingPoints += candidate
                                    pendingPoints.size >= FLUSH_BATCH_SIZE
                                }
                                if (shouldFlush) flushPendingPoints(trackId, container)
                            }
                        }
                        LocationFix.PermissionDenied -> {
                            // RECORD -660 (item 4): the screen is told why; stopRecording ends the track.
                            (application as ForagerApplication).container.recordingHalts.report(RecordingHalt(trackId, RecordingHaltReason.NO_LOCATION_PERMISSION))
                            stopRecording()
                        }
                    }
                }
            }
            launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MILLIS)
                    flushPendingPoints(trackId, container)
                }
            }
            launch {
                // Once at the start too: with no live reading, the last known position is all
                // there is, and a recording already past its leave-by time should hear so at once.
                tickSundown(container)
                while (isActive) {
                    delay(SUNDOWN_TICK_MILLIS)
                    tickSundown(container)
                }
            }
            launch {
                // Dispatch 2026-09-28-645: Back by on its own loop, so a failure in one evaluation
                // cannot hold up the other. Once at the start too, for a time set a moment before
                // the service began (BackByWatch keeps it).
                tickBackBy(container)
                while (isActive) {
                    delay(BACK_BY_TICK_MILLIS)
                    tickBackBy(container)
                }
            }
        }
    }

    /** One back-by evaluation; anything it throws is logged and dropped, as [tickSundown] does. */
    private suspend fun tickBackBy(container: AppContainer, trigger: EvaluationTrigger = EvaluationTrigger.TIMER) {
        try {
            container.backByWatch.tick(trigger)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "A back-by evaluation failed; the next tick tries again.", e)
        }
    }

    /**
     * "I'm back" or "+30 min" from the back-by alert (dispatch 2026-09-28-645). The alert is taken
     * down either way: answered, or left from a recording that has ended. Acted on only for the
     * recording this service is running, which the intent names; with no recording running the
     * service was started only to hear this, and stops again, as a Stop with nothing recording does.
     */
    private fun onBackByAction(action: String, trackId: String?) {
        NotificationManagerCompat.from(this).cancel(BACK_BY_NOTIFICATION_ID)
        val current = currentTrackId
        if (current == null) {
            Log.w(TAG, "A back-by action arrived with no recording running; nothing to change.")
            if (recordingJob == null) stopSelf()
            return
        }
        if (trackId != current) {
            Log.w(TAG, "A back-by action for track '$trackId' arrived while recording '$current'; ignored.")
            return
        }
        val watch = (application as ForagerApplication).container.backByWatch
        when (action) {
            ACTION_BACK_BY_IM_BACK -> watch.imBack(current)
            ACTION_BACK_BY_LATER -> watch.later(current)
        }
    }

    /**
     * A recording this service will not run (RECORD -660, item 4): reported to the screen through
     * [com.zynergylabs.forager.app.domain.RecordingHalts], and the track the screen created for it
     * ended, as a stop ends one, so it is not left open. Then the service stops itself, after the
     * end is written, as [stopRecording] does: stopping first would cancel the write with the scope.
     */
    private fun halt(trackId: String, reason: RecordingHaltReason) {
        val container = (application as ForagerApplication).container
        container.recordingHalts.report(RecordingHalt(trackId, reason))
        scope.launch {
            container.endTrackUseCase(trackId).onFailure { error ->
                Log.w(TAG, "Couldn't mark track '$trackId' ended after it could not be recorded.", error)
            }
            stopSelf()
        }
    }

    /**
     * One watch call for one fix (dispatch 2026-09-28-658, R1). Anything it throws is logged and
     * dropped, for the reason [tickSundown] gives: the collector runs as a child of the recording,
     * and an exception escaping it would cancel the recording with it, so no later point would be
     * saved. Returns the call's result, or `null` when it threw.
     */
    private inline fun <T> guardWatch(what: String, fix: TrackPoint, call: () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed on the fix at ${fix.timestampEpochMillis}; the point is still recorded and the next fix tries again.", e)
            null
        }

    /**
     * One sundown evaluation. Anything it throws is logged and dropped: it runs as a child of the
     * recording, and an exception escaping it would cancel the recording with it.
     */
    private suspend fun tickSundown(container: AppContainer, trigger: EvaluationTrigger = EvaluationTrigger.TIMER) {
        try {
            container.sundownWatch.tick(trigger)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "A sundown evaluation failed; the next tick tries again.", e)
        }
    }

    private fun stopRecording() {
        val trackId = currentTrackId
        currentTrackId = null
        recordingJob?.cancel()
        recordingJob = null
        if (trackId != null) {
            val container = (application as ForagerApplication).container
            container.returnWatch.end(trackId)
            container.sundownWatch.end(trackId)
            container.backByWatch.end(trackId)
            // The reminder ends with the recording (the owner: "Cancel automatically"), so an alert
            // still in the shade has nothing left to answer; the sundown alert goes with it (RECORD -800).
            cancelRecordingAlerts(this)
            WalkLogger.of(this).onRecordingStopped()
            scope.launch {
                flushPendingPoints(trackId, container)
                container.endTrackUseCase(trackId).onFailure { error ->
                    Log.w(TAG, "Couldn't mark track '$trackId' ended.", error)
                }
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    private suspend fun flushPendingPoints(trackId: String, container: AppContainer) {
        val toWrite = bufferMutex.withLock {
            val copy = pendingPoints.toList()
            pendingPoints.clear()
            copy
        }
        if (toWrite.isEmpty()) return
        container.recordTrackPointsUseCase(trackId, toWrite).onFailure { error ->
            Log.w(TAG, "Couldn't persist ${toWrite.size} point(s) for track '$trackId'.", error)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.track_recording_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    /**
     * The ongoing notification, with a **Stop recording** action beside the tap-to-open intent.
     *
     * The action exists because tapping the notification body was, until it was added, the only
     * affordance a recording had once the Activity went away — and it opens an app that cannot see
     * this service. [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.isRecording] is derived purely
     * from [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.activeTrack], an in-memory field that
     * `TrackRecordingViewModel`'s `init` does not repopulate from storage, so an Activity destroyed
     * mid-recording comes back reporting "not recording" while this service is still running and
     * still holding GPS. With no action here, nothing short of force-stopping the app could end
     * that recording. The shade is therefore the one control that must work regardless of what the
     * UI believes, and it addresses this service directly ([PendingIntent.getService]) rather than
     * routing through `MainActivity` — the Activity is exactly the thing that may not exist.
     *
     * [ACTION_STOP] is the same action `MainActivity`'s `LaunchedEffect(trackUiState.activeTrack)`
     * sends, so a stop from here takes the identical path: [stopRecording] flushes the buffer, ends
     * the track through `endTrackUseCase`, and calls `stopSelf()`. No new stop path was added.
     *
     * [PendingIntent.FLAG_IMMUTABLE] on both, as the platform requires from API 31 and this file
     * already did for the content intent. Distinct request codes so the two are never conflated.
     */
    private fun buildNotification(): Notification {
        // SINGLE_TOP and CLEAR_TOP, as the backup notification's tap sets (AndroidBackupNotifier):
        // MainActivity is a standard activity, so a plain start from here made a second copy on
        // top of the one already in the task, and Back from it landed on the older copy. Seen on
        // the S22 (dispatch 2026-09-28-415). With these the existing screen comes to the front
        // and gets the intent through onNewIntent, which ignores one with no extra. With no task
        // alive the tap still opens the app as before. The manifest's launch mode is left alone:
        // changing it would change every way the app is started.
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, TrackRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.track_recording_notification_title))
            .setContentText(getString(R.string.track_recording_notification_text))
            .setSmallIcon(R.drawable.ic_track_recording)
            .setContentIntent(openAppIntent)
            .addAction(
                R.drawable.ic_track_recording,
                getString(R.string.track_recording_notification_stop_action),
                stopIntent,
            )
            .setOngoing(true)
            .build()
    }

    /**
     * `Service.startForeground(Int, Notification)` is `final` — cannot be overridden, unlike the
     * one-shot [android.location.LocationManager]/[android.hardware.SensorManager] wrapping this
     * project does elsewhere. This calls the type-carrying overload directly instead.
     *
     * As of `targetSdk` 34+, starting a `FOREGROUND_SERVICE_TYPE_LOCATION` service without either
     * runtime location permission granted throws a `SecurityException` here — a confirmed crash
     * (captured stack trace: `startForegroundWithLocationType` -> `startRecording` ->
     * `onStartCommand`, an uncaught `RuntimeException: Unable to start service` on the main
     * thread). [onStartCommand]'s [hasLocationPermission] check is what prevents this method from
     * ever being reached without the permission it needs.
     *
     * **Other refusals** (dispatch 2026-09-28-658, R7). The platform can still refuse with the
     * permission held: from Android 12 a foreground start the system does not allow at that moment
     * throws `ForegroundServiceStartNotAllowedException` (an [IllegalStateException]), and from
     * Android 14 a location-type start from the background without a while-in-use exemption throws
     * a [SecurityException]. Either is caught, logged with the track it was for, and reported as
     * `false`, so [startRecording] stops the service as the permission branch does, rather than the
     * service crashing. Returns `true` once the service is in the foreground.
     */
    private fun startForegroundWithLocationType(trackId: String): Boolean {
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The system refused to start recording track '$trackId' in the foreground; stopping.", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "The system refused to start recording track '$trackId' in the foreground (not allowed from the background); stopping.", e)
            false
        }
    }

    companion object {
        const val ACTION_START = "com.zynergylabs.forager.app.service.action.START_RECORDING"
        const val ACTION_STOP = "com.zynergylabs.forager.app.service.action.STOP_RECORDING"
        const val EXTRA_TRACK_ID = "com.zynergylabs.forager.app.service.extra.TRACK_ID"
        const val EXTRA_MODE = "com.zynergylabs.forager.app.service.extra.MODE"

        /** The back-by alert's "I'm back" (dispatch 2026-09-28-645), with [EXTRA_TRACK_ID]. */
        const val ACTION_BACK_BY_IM_BACK = "com.zynergylabs.forager.app.service.action.BACK_BY_IM_BACK"

        /** The back-by alert's "+30 min", with [EXTRA_TRACK_ID]. */
        const val ACTION_BACK_BY_LATER = "com.zynergylabs.forager.app.service.action.BACK_BY_LATER"

        private const val TAG = "TrackRecordingService"
        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 1001

        // Distinct per PendingIntent so neither can be handed the other's Intent by the platform's
        // (requestCode, filterEquals(Intent)) identity rule. They already differ by target type
        // (activity vs service); the codes make that independent of it.
        private const val REQUEST_CODE_OPEN_APP = 0
        private const val REQUEST_CODE_STOP = 1

        // Whichever comes first flushes the buffer: this many points accepted, or this much time
        // elapsed — the same "don't let irreplaceable field data sit unwritten for too long if the
        // service is killed" reasoning as ForagerDatabase's doc comment on why tracks get a real
        // migration in the first place, just applied to the in-memory buffer ahead of it.
        private const val FLUSH_BATCH_SIZE = 20
        private const val FLUSH_INTERVAL_MILLIS = 30_000L

        // How often the sundown alerts are re-evaluated (dispatch 2026-09-28-516): the screen's
        // own re-read of the track runs at the same 15 s, and the alerts are minute-scale moments.
        private const val SUNDOWN_TICK_MILLIS = 15_000L

        // Back by's (dispatch 2026-09-28-645): a minute-precision reminder fires at most 15 s late.
        private const val BACK_BY_TICK_MILLIS = 15_000L
    }
}
