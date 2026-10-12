package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.alert.BACK_BY_NOTIFICATION_ID
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import android.app.AlarmManager
import com.zynergylabs.forager.app.alert.AndroidWakeUpAlarms
import com.zynergylabs.forager.app.domain.BACK_BY_LATER_MILLIS
import com.zynergylabs.forager.app.domain.WakeUpAlarm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.robolectric.shadows.ShadowAlarmManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * Back by not firing on the L3 walk (dispatch 2026-09-28-796, RECORD -793 to -795): the cases
 * `TrackRecordingServiceBackByTest` never had. That class sets a time already past before the service
 * starts, so its first evaluation, at start, fires it; nothing reached a **future** time set **after**
 * the service had begun, through the service's own loop, or with a **Return in progress**, as on the
 * walk (set at about 13:10 for 13:55, Return at 13:48, no alert posted: the S22's usagestats has no
 * `back_by_alert` interruption that day).
 *
 * The real [TrackRecordingService] and the real [AppContainer]. The time is set on the container's
 * `BackByWatch`, the one instance `MainActivity` hands the recording ViewModel (`MainActivity.kt:234`,
 * `container` being the application's, `:63`) and the one the service drives; the ViewModel's
 * `setBackBy` makes exactly this call (`TrackRecordingViewModel.kt:314-318`). Return is the call the
 * ViewModel's `startReturn` makes (`TrackRecordingViewModel.kt:822`). The container's clock is the
 * system clock, so these tests wait in real time, for one tick of the 15 s loop at most.
 *
 * Every test stops what it started inside its own body, in a `finally`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceBackByLoopTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    /** (a) A future time, set after the service has begun, is reached through the service's own 15 s loop. */
    @Test
    fun `a future time set after the service has begun fires through the service's own loop`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId) = startedRecording()
            controller = c
            simulateGpsFix(metresNorth = 0.0)
            val at = System.currentTimeMillis() + 2_000L
            assertTrue("the watch takes the time", container.backByWatch.set(trackId, at))
            val notification = awaitBackByNotification(timeoutMillis = LOOP_WAIT_MILLIS)
            assertNotNull("no back-by alert within ${LOOP_WAIT_MILLIS / 1_000} s of a time set 2 s ahead, after the service began", notification)
        } finally {
            controller?.let { end(it) }
        }
    }

    /** (b) As (a), with a Return in progress before the time, and the walker well away from the start, as on the walk. */
    @Test
    fun `with a Return in progress and the walker far from the start, the time still fires through the loop`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId) = startedRecording()
            controller = c
            simulateGpsFix(metresNorth = 0.0)
            simulateGpsFix(metresNorth = 2_000.0)
            assertTrue("Return taken", container.returnWatch.startReturn(trackId))
            val at = System.currentTimeMillis() + 2_000L
            assertTrue("the watch takes the time", container.backByWatch.set(trackId, at))
            val deadline = System.currentTimeMillis() + LOOP_WAIT_MILLIS
            var notification: Notification? = null
            while (notification == null && System.currentTimeMillis() < deadline) {
                simulateGpsFix(metresNorth = 2_000.0)
                notification = awaitBackByNotification(timeoutMillis = 1_000L)
            }
            assertNotNull("no back-by alert within ${LOOP_WAIT_MILLIS / 1_000} s of a time set 2 s ahead, during a Return, 2 km from the start", notification)
        } finally {
            controller?.let { end(it) }
        }
    }

    /**
     * The walk's suspected mechanism, modelled (unconfirmed on the device; see the report): the alert
     * waits only on the service's 15 s `delay`, and a `delay` counts time the CPU is awake. Fixes did
     * reach the service on the walk (the off-track alert at 13:57:48 is driven by them), so a fix
     * after the time is a moment the service is certainly running. This asks whether such a fix
     * brings the alert, or whether it still waits for the timer.
     */
    @Test
    fun `a GPS fix after the time brings the alert without waiting for the 15 s timer`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId) = startedRecording()
            controller = c
            simulateGpsFix(metresNorth = 0.0)
            val at = System.currentTimeMillis() + 500L
            assertTrue("the watch takes the time", container.backByWatch.set(trackId, at))
            while (System.currentTimeMillis() <= at) Thread.sleep(50L)
            val fixAt = System.currentTimeMillis()
            simulateGpsFix(metresNorth = 0.0)
            val notification = awaitBackByNotification(timeoutMillis = FIX_WAIT_MILLIS)
            assertNotNull(
                "no back-by alert within ${FIX_WAIT_MILLIS / 1_000} s of a GPS fix ${fixAt - at} ms past the time: the alert waits for the 15 s timer, not the fix. The record: ${recordLines()}",
                notification,
            )
            assertTrue("woken by the fix: ${recordLines()}", recordLines().any { " by=fix" in it && " due=yes " in it })
        } finally {
            controller?.let { end(it) }
        }
    }

    /**
     * Dispatch 2026-09-28-796, step 3: the app's real record file says what happened, in order, through
     * the real service and container: started, set, evaluated, fired with its delivery, ended by stop.
     */
    @Test
    fun `the back-by record file tells the story set, evaluated, fired, ended, with no positions`() {
        val file = java.io.File(context.filesDir, com.zynergylabs.forager.app.data.diagnostics.BACK_BY_RECORD_FILE_NAME)
        file.delete()
        val (c, trackId) = startedRecording()
        try {
            simulateGpsFix(metresNorth = 0.0)
            assertTrue(container.backByWatch.set(trackId, System.currentTimeMillis() + 2_000L))
            assertNotNull("the alert, for the record to report", awaitBackByNotification(timeoutMillis = LOOP_WAIT_MILLIS))
        } finally {
            end(c)
        }
        val kinds = file.readLines().map { it.substringAfter(' ').substringBefore(' ') }
        val story = kinds.filter { it != "evaluated" }
        assertEquals("the record: ${file.readLines()}", listOf("started", "set", "alarm-scheduled", "fired", "ended", "alarm-cancelled"), story)
        val lines = file.readLines()
        assertTrue("an evaluation before the alert: $lines", kinds.indexOf("evaluated") in 0 until kinds.indexOf("fired"))
        assertTrue("accepted: $lines", lines.single { " set " in it }.endsWith(" accepted"))
        assertTrue("posted: $lines", " notification=posted " in lines.single { " fired " in it })
        assertTrue("ended by the stop: $lines", lines.single { " ended " in it }.endsWith(" reason=stop"))
        assertTrue("every line names this track: $lines", lines.all { "track=$trackId" in it })
        assertTrue("no coordinate in any line: $lines", lines.none { "-122." in it || "lat=" in it || "lng=" in it })
    }

    // ── The wake-up alarm (dispatch 2026-09-28-796; the owner, RECORD -797) ──

    private fun shadowAlarms() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun backByAlarm(): ShadowAlarmManager.ScheduledAlarm? {
        val ours = AndroidWakeUpAlarms.pendingIntentFor(context, WakeUpAlarm.BACK_BY)
        return shadowAlarms().scheduledAlarms.singleOrNull { it.operation == ours }
    }

    /** Set, "+30 min" and Clear, through the real container: an inexact allow-while-idle wake-up at the time, moved, then gone. */
    @Test
    fun `a set time asks for an inexact allow-while-idle wake-up at that time, +30 min moves it, Clear and Stop take it away`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId) = startedRecording()
            controller = c
            val at = System.currentTimeMillis() + 60 * 60_000L
            assertTrue(container.backByWatch.set(trackId, at))
            val alarm = backByAlarm()
            assertNotNull("a wake-up for Back by: ${shadowAlarms().scheduledAlarms.map { it.triggerAtMs }}", alarm)
            assertEquals("wakes the phone", AlarmManager.RTC_WAKEUP, alarm!!.type)
            assertEquals("at the time set", at, alarm.triggerAtMs)
            assertTrue("delivered in Doze too", alarm.isAllowWhileIdle)
            assertTrue("inexact: no exact-alarm permission", alarm.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT)

            val before = System.currentTimeMillis()
            container.backByWatch.later(trackId)
            val moved = backByAlarm()!!.triggerAtMs
            assertTrue("+30 min moves the wake-up to 30 minutes from now: $moved", moved in before + BACK_BY_LATER_MILLIS..System.currentTimeMillis() + BACK_BY_LATER_MILLIS)

            container.backByWatch.clear(trackId)
            assertNull("Clear cancels it", backByAlarm())

            assertTrue(container.backByWatch.set(trackId, at))
            assertNotNull(backByAlarm())
            end(c)
            controller = null
            assertNull("stopping the recording cancels it", backByAlarm())
        } finally {
            controller?.let { end(it) }
        }
    }

    /**
     * The alarm path alone: the time passes with no fix after it and long before the service's next 15 s
     * tick, and the wake-up arrives (fired through its own PendingIntent, to the real receiver). The alert
     * must come from the alarm. On the walk the screen was off from 13:48:37 to 13:57:10, across 13:55.
     */
    @Test
    fun `the wake-up alarm, delivered with no fix and before the timer's next tick, brings the alert`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId) = startedRecording()
            controller = c
            val at = System.currentTimeMillis() + 500L
            assertTrue(container.backByWatch.set(trackId, at))
            val alarm = backByAlarm()
            assertNotNull(alarm)
            while (System.currentTimeMillis() <= at) Thread.sleep(50L)
            alarm!!.operation!!.send()
            val deliveredAt = System.currentTimeMillis()
            val notification = awaitBackByNotification(timeoutMillis = FIX_WAIT_MILLIS)
            assertNotNull(
                "no back-by alert within ${FIX_WAIT_MILLIS / 1_000} s of the wake-up alarm, delivered ${deliveredAt - at} ms past the time",
                notification,
            )
            val lines = java.io.File(context.filesDir, com.zynergylabs.forager.app.data.diagnostics.BACK_BY_RECORD_FILE_NAME).readLines()
            assertTrue("the record says the alarm arrived and woke the evaluation: $lines", lines.any { " alarm-delivered track=$trackId" in it } && lines.any { " by=alarm" in it && " due=yes " in it })
        } finally {
            controller?.let { end(it) }
        }
    }

    private fun recordLines(): List<String> =
        java.io.File(context.filesDir, com.zynergylabs.forager.app.data.diagnostics.BACK_BY_RECORD_FILE_NAME).takeIf { it.exists() }?.readLines().orEmpty()

    private fun startedRecording(): Pair<ServiceController<TrackRecordingService>, String> {
        val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        controller.create().get()
        controller.startCommand(0, 1)
        // The start-of-recording evaluation runs with nothing set; let it pass before setting a time.
        idleAndSettle()
        Thread.sleep(250L)
        idleAndSettle()
        return controller to trackId
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    private var lastFixTime = 0L

    /**
     * One GPS fix [metresNorth] of a fixed start, stamped on a whole second, as the S22's GPS stamps them, and
     * at least a second after the previous one: the shadow drops a fix sooner than the registration's 1 s
     * interval after the last, which is how run 2's first try of the fix test saw no fix at all.
     */
    private fun simulateGpsFix(metresNorth: Double) {
        lastFixTime = maxOf(System.currentTimeMillis() / 1_000L * 1_000L, lastFixTime + 1_000L)
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 45.0 + metresNorth / 111_195.0
                longitude = -122.0
                accuracy = 5f
                time = lastFixTime
                elapsedRealtimeNanos = lastFixTime * 1_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun backByNotification() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(BACK_BY_NOTIFICATION_ID)

    private fun awaitBackByNotification(timeoutMillis: Long): Notification? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            backByNotification()?.let { return it }
            idleAndSettle()
        }
        return backByNotification()
    }

    private fun end(controller: ServiceController<TrackRecordingService>) {
        val service = controller.get()
        if (!shadowOf(service).isStoppedBySelf) {
            controller.withIntent(stopIntent()).startCommand(0, 99)
            val deadline = System.currentTimeMillis() + 5_000L
            while (!shadowOf(service).isStoppedBySelf && System.currentTimeMillis() < deadline) Thread.sleep(25L)
        }
        controller.destroy()
        idleAndSettle()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(250L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idleAndSettle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        /** One 15 s tick of the loop, with room for a slow machine. */
        const val LOOP_WAIT_MILLIS = 25_000L

        /** Well inside one tick: an alert this soon after the fix came from the fix, not the timer. */
        const val FIX_WAIT_MILLIS = 3_000L
    }
}
