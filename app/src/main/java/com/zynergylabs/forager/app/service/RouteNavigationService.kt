package com.zynergylabs.forager.app.service

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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.MainActivity
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.routenav.ROUTE_NAVIGATION_CHANNEL_NAME
import com.zynergylabs.forager.app.domain.routenav.ROUTE_NAVIGATION_NOTIFICATION_ARRIVED
import com.zynergylabs.forager.app.domain.routenav.ROUTE_NAVIGATION_NOTIFICATION_STOP
import com.zynergylabs.forager.app.domain.routenav.RouteNavigationTarget
import com.zynergylabs.forager.app.domain.routenav.RouteNavigationWatchState
import com.zynergylabs.forager.app.domain.routenav.routeNavigationNotificationTitle
import com.zynergylabs.forager.app.domain.routenav.routeNavigationTarget
import com.zynergylabs.forager.app.domain.routenav.routeNextStopText
import com.zynergylabs.forager.app.location.hasLocationPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Route navigation's background location service (dispatch 2026-11-03-03, A8; the owner: "Use the same method as off track
 * notifications"). Navigate starts it, with or without a recording, while the app is on screen; it feeds every raw fix to the
 * container's [com.zynergylabs.forager.app.domain.routenav.RouteNavigationWatch], so a stop is reached and buzzes with the screen
 * off, and it shows its own ongoing notification: "Navigating <route>", "To <stop> · <i> of <n>", then "Arrived" after the last
 * stop, which stays until navigation is ended (A9).
 *
 * **Its own service, not a new action on TrackRecordingService** (proposal accepted, A8): that service's lifetime is a
 * recording's, its `onDestroy` ends every recording watch, and its notification's button stops the recording. Navigation would
 * have threaded conditions through all three. Accepted cost: while recording and navigating together, two location
 * subscriptions are open, until battery data says otherwise.
 *
 * **It never ends a navigation itself.** [ACTION_STOP] (the notification's "Stop navigating") ends the watch and stops the service;
 * the Maps tab's ViewModel sees the watch end and ends the navigation exactly as the X does (A9), remembering the next stop. A
 * watch that ends any other way stops this service too, so it never holds GPS with nothing to watch.
 *
 * Not sticky: a service the system restarts after the process died would have no watch to feed. The navigation is kept, and the
 * app picks it up again when it is opened (A4), which starts this service again.
 */
class RouteNavigationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var fixesJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createRouteNavigationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> {
                (application as ForagerApplication).container.routeNavigationWatch.end()
                stopSelfNow()
            }
            else -> {
                Log.w(TAG, "Started with no action (${intent?.action}); nothing to watch, stopping.")
                stopSelfNow()
            }
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (fixesJob != null) return
        val container = (application as ForagerApplication).container
        val watch = container.routeNavigationWatch
        if (!hasLocationPermission(this)) {
            Log.w(TAG, "Refusing to start route navigation's location service: no location permission.")
            stopSelfNow()
            return
        }
        if (!startInForeground(watch.state.value)) {
            stopSelfNow()
            return
        }
        fixesJob = scope.launch {
            launch {
                container.locationTracker.fixes.collect { fix ->
                    if (fix is LocationFix.Update) {
                        try {
                            watch.onFix(fix, System.currentTimeMillis())
                        } catch (e: Exception) {
                            Log.w(TAG, "The route navigation watch threw on a fix; that fix is lost, the next one is judged.", e)
                        }
                    }
                }
            }
            watch.state.distinctUntilChangedByShown().collect { state ->
                if (state.navigation == null) {
                    stopSelfNow()
                } else {
                    postRouteNavigationNotification(this@RouteNavigationService, buildNotification(state))
                }
            }
        }
    }

    private fun stopSelfNow() {
        fixesJob?.cancel()
        fixesJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(state: RouteNavigationWatchState): Boolean = try {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "The system refused to start route navigation in the foreground; stopping.", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "The system refused to start route navigation in the foreground (not allowed from the background); stopping.", e)
        false
    }

    private fun buildNotification(state: RouteNavigationWatchState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, RouteNavigationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(state.tripName?.let(::routeNavigationNotificationTitle))
            .setContentText(routeNavigationNotificationText(state))
            .setSmallIcon(R.drawable.ic_track_recording)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_track_recording, ROUTE_NAVIGATION_NOTIFICATION_STOP, stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.zynergylabs.forager.app.service.action.START_ROUTE_NAVIGATION"
        const val ACTION_STOP = "com.zynergylabs.forager.app.service.action.STOP_ROUTE_NAVIGATION"
        internal const val CHANNEL_ID = "route_navigation"
        internal const val NOTIFICATION_ID = 1005
        private const val REQUEST_CODE_OPEN_APP = 10
        private const val REQUEST_CODE_STOP = 11
        private const val TAG = "RouteNavigationService"
    }
}

/** The notification's text for [state]: the next stop, "Arrived" once every stop is reached, nothing with no navigation. */
internal fun routeNavigationNotificationText(state: RouteNavigationWatchState): String? {
    val navigation = state.navigation ?: return null
    val route = state.route ?: return null
    return when (val target = routeNavigationTarget(route, navigation.nextStopIndex)) {
        is RouteNavigationTarget.ToStop -> routeNextStopText(target.stop.name, target.index, target.stopCount)
        RouteNavigationTarget.Finished -> ROUTE_NAVIGATION_NOTIFICATION_ARRIVED
        is RouteNavigationTarget.OutOfRange -> null
    }
}

/** Only what the notification shows: a new fix that moves nothing posts nothing. */
private fun kotlinx.coroutines.flow.Flow<RouteNavigationWatchState>.distinctUntilChangedByShown() =
    distinctUntilChanged { a, b -> a.navigation == b.navigation && a.tripName == b.tripName }

/** Posts [notification] when POST_NOTIFICATIONS allows, as the off-track alert checks it; the foreground notification already showing stays either way. */
private fun postRouteNavigationNotification(context: Context, notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    NotificationManagerCompat.from(context).notify(RouteNavigationService.NOTIFICATION_ID, notification)
}

/** The ongoing notification's channel: low importance, no sound and no vibration of its own (the stop's buzz is its own). */
internal fun createRouteNavigationChannel(context: Context) {
    val channel = NotificationChannel(RouteNavigationService.CHANNEL_ID, ROUTE_NAVIGATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
        enableVibration(false)
        setSound(null, null)
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
