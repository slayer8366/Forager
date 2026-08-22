package com.forager.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.forager.app.AppContainer
import com.forager.app.ForagerApplication
import com.forager.app.MainActivity
import com.forager.app.R
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationSampler
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode
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
 * The foreground service that records a track: collects [com.forager.app.domain.LocationTracker]
 * fixes, runs each through [LocationSampler] to decide what actually gets kept, and batches
 * accepted points into [com.forager.app.domain.RecordTrackPointsUseCase] rather than writing one
 * row per fix — see that use case's owning [com.forager.app.domain.TrackRepository] doc comment for
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
 * `docs/plans/forager-navigator-plan.md` §7).
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
                val mode = TrackRecordingMode.entries.firstOrNull { it.name == modeName } ?: TrackRecordingMode.BALANCED
                if (trackId != null && recordingJob == null) {
                    // Defence in depth against the confirmed FGS-location-type crash: MainActivity
                    // already gates on this before ever sending ACTION_START (see its own
                    // hasLocationPermission()), but this service must never crash regardless of how
                    // it gets told to start — see startForegroundWithLocationType()'s doc comment.
                    if (hasLocationPermission()) {
                        startRecording(trackId, mode)
                    } else {
                        Log.w(TAG, "Refusing to start recording for track '$trackId': no location permission.")
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        recordingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRecording(trackId: String, mode: TrackRecordingMode) {
        currentTrackId = trackId
        startForegroundWithLocationType()

        val container = (application as ForagerApplication).container
        val sampler = LocationSampler(mode)
        var lastAccepted: TrackPoint? = null

        recordingJob = scope.launch {
            launch {
                container.locationTracker.fixes.collect { fix ->
                    when (fix) {
                        is LocationFix.Update -> {
                            val candidate = TrackPoint(
                                lat = fix.lat,
                                lng = fix.lng,
                                altitude = fix.altitude,
                                accuracyMeters = fix.accuracyMeters,
                                timestampEpochMillis = fix.timestampEpochMillis,
                            )
                            if (sampler.shouldAccept(lastAccepted, candidate)) {
                                lastAccepted = candidate
                                val shouldFlush = bufferMutex.withLock {
                                    pendingPoints += candidate
                                    pendingPoints.size >= FLUSH_BATCH_SIZE
                                }
                                if (shouldFlush) flushPendingPoints(trackId, container)
                            }
                        }
                        LocationFix.PermissionDenied -> stopRecording()
                    }
                }
            }
            launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MILLIS)
                    flushPendingPoints(trackId, container)
                }
            }
        }
    }

    private fun stopRecording() {
        val trackId = currentTrackId
        currentTrackId = null
        recordingJob?.cancel()
        recordingJob = null
        if (trackId != null) {
            val container = (application as ForagerApplication).container
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

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.track_recording_notification_title))
            .setContentText(getString(R.string.track_recording_notification_text))
            .setSmallIcon(R.drawable.ic_track_recording)
            .setContentIntent(openAppIntent)
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
     */
    private fun startForegroundWithLocationType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Same check, same two permissions, as
     * [com.forager.app.location.AndroidLocationProvider.hasLocationPermission] — not shared code
     * across a service/domain-layer boundary that owns neither Context nor Manifest, matching that
     * class's own doc comment on why (see also `MainActivity`'s own copy, and
     * `com.forager.app.ui.map.SightingsMap.kt`'s).
     */
    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_START = "com.forager.app.service.action.START_RECORDING"
        const val ACTION_STOP = "com.forager.app.service.action.STOP_RECORDING"
        const val EXTRA_TRACK_ID = "com.forager.app.service.extra.TRACK_ID"
        const val EXTRA_MODE = "com.forager.app.service.extra.MODE"

        private const val TAG = "TrackRecordingService"
        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 1001

        // Whichever comes first flushes the buffer: this many points accepted, or this much time
        // elapsed — the same "don't let irreplaceable field data sit unwritten for too long if the
        // service is killed" reasoning as ForagerDatabase's doc comment on why tracks get a real
        // migration in the first place, just applied to the in-memory buffer ahead of it.
        private const val FLUSH_BATCH_SIZE = 20
        private const val FLUSH_INTERVAL_MILLIS = 30_000L
    }
}
