package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.alert.OFF_TRACK_NOTIFICATION_ID
import com.zynergylabs.forager.app.domain.ReturnWatchState
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import org.robolectric.shadows.ShadowLog

/**
 * The real [TrackRecordingService] and the real [AppContainer], around the moment the app is
 * swiped away from recents (dispatch 2026-09-28-400; Part 1 pinned the fault here, Amendment 2
 * moved the off-track decision into `ReturnWatch`, which this service begins, feeds and ends).
 *
 * The first two tests are the fixed behaviour: **the off-track alert is delivered with no
 * ViewModel in existence,** through the service's own `onStartCommand`, read as the notification
 * the real `AndroidAlertDelivery` posts. The container's delivery cannot be swapped for a fake, so
 * the notification is the reading.
 *
 * **The reopened screen (Amendment 3, Part 3a).** Part 2 left two tests here pinning a fault. They
 * are replaced: a ViewModel built while the service records takes the recording up, Stop on it
 * ends the recording, and Record on it cannot start a second one.
 *
 * ## What is real here, and the three steps that are not
 *
 * Real: the ViewModel's own callbacks, the service through its own `onStartCommand`, the tracker
 * over Robolectric's `LocationManager`, the Room database and the watch behind [AppContainer], and
 * the alert delivery.
 *
 * Not reached, and said plainly:
 * 1. **The swipe from recents.** It is stood in for by `ViewModelStore.clear()` with the service
 *    left running. On the S22 the real swipe was checked on 2026-10-02 (the report's "Phone
 *    check"): the process, the service and its notification survived.
 * 2. **`MainActivity`'s `LaunchedEffect(trackUiState.activeTrack)`,** which is what turns a new
 *    `activeTrack` into `ACTION_START` and a cleared one into `ACTION_STOP`. No test in this
 *    repository composes `MainActivity`. [startIntent] and [stopIntent] are built here from the
 *    same three constants that effect uses, as `TrackRecordingServiceTest.startIntent` does.
 * 3. **The process being killed.** Only the null intent a sticky restart delivers is reachable.
 *
 * ## Ending what was started
 *
 * `TrackRecordingServiceTest`'s header records what an unstopped recording does to the next test
 * class's sandbox. Every test here that starts the service stops it through `ACTION_STOP` inside
 * its own body, waits for the stop to finish, destroys the controller and drains, in a `finally`.
 * Every ViewModel is stopped there too, which is `CLAUDE.md`'s poll-loop rule applied here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceSwipeAwayTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager
    private val createdViewModels = mutableListOf<TrackRecordingViewModel>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            // Without it AndroidAlertDelivery posts nothing on API 33 and above, and the notification is this class's reading of a delivery.
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        // One provider, so each collector of the tracker's stream is exactly one registered listener
        // and the listener count below reads as "how many things are listening for fixes".
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    /** Built the way `MainActivity`'s factory builds it, and held in a store so `clear()` reaches `onCleared`. */
    private fun viewModelIn(store: ViewModelStore): TrackRecordingViewModel =
        ViewModelProvider(
            store,
            viewModelFactory {
                initializer {
                    TrackRecordingViewModel(
                        container.trackRepository,
                        container.startTrackUseCase,
                        container.getWaypointsUseCase,
                        container.createWaypointUseCase,
                        container.deleteWaypointUseCase,
                        container.computeReturnToStartUseCase,
                        container.locationTracker,
                        container.getTracksUseCase,
                        container.returnWatch,
                        container.alertAudibility,
                        deleteTrack = container.deleteTrackUseCase,
                        getTrackOriginWaypoint = container.getTrackOriginWaypointUseCase,
                        alreadyRecordingMessage = ALREADY_RECORDING,
                        abandonedTrackSweepOnce = container.abandonedTrackSweepOnce,
                    ).also(createdViewModels::add)
                }
            },
        )[TrackRecordingViewModel::class.java]

    /** The intent `MainActivity.kt:413-427` sends when `activeTrack` becomes non-null. HIGH_ACCURACY is what the record button passes (`MainActivity.kt:602`). */
    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    /** The intent `MainActivity.kt:434-436` sends when `activeTrack` becomes null after a start. */
    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    /**
     * Plan task T1's check, and the replacement for Part 1's fault: **the alert is delivered with no
     * screen alive, through the service's own entry point.** No ViewModel is constructed in this
     * test at all. The track row is made by the use case the ViewModel would have called, and the
     * start point and the return are given to the watch as the ViewModel would have given them
     * before it was swiped away. Then the service is the only thing running: it began the watch on
     * `ACTION_START`, and each fix the platform delivers to it goes to the watch.
     *
     * Three fixes, each about 111 m further north of the start. The reading is the off-track
     * notification itself, id 1002, posted by the real `AndroidAlertDelivery`.
     *
     * Fails with the service's one feed call removed: "expected the off-track notification".
     */
    @Test
    fun `with no ViewModel in existence, a returning recording posts the off-track notification when the walker moves away`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            val service = controller.create().get()
            controller.startCommand(0, 1)
            assertNotNull("precondition: the service is in the foreground, recording", shadowOf(service).lastForegroundNotification)
            assertEquals(
                "the service began the watch for its track, in the mode it was started in",
                ReturnWatchState(trackId = trackId, isBegun = true, mode = TrackRecordingMode.HIGH_ACCURACY),
                container.returnWatch.state.value,
            )
            assertEquals("precondition: the service's listener is the only one; no ViewModel exists", 1, awaitListenerCount(1))

            container.returnWatch.setStartPoint(trackId, TrackPoint(45.000, -122.0, null, null, FIRST_FIX_EPOCH_MILLIS))
            assertTrue(container.returnWatch.startReturn(trackId))
            assertNull("precondition: no off-track notification yet", offTrackNotification())

            simulateFix(1)
            simulateFix(2)
            simulateFix(3)

            assertNotNull(
                "expected the off-track notification after three readings moving away from the start, with no ViewModel alive",
                awaitOffTrackNotification(),
            )
            assertTrue(container.returnWatch.state.value.isOffTrack)

            controller.withIntent(stopIntent()).startCommand(0, 2)
            assertNotNull(awaitEndedAt(trackId))
            assertTrue(awaitStoppedBySelf(service))
            assertEquals("stopping the recording ended the watch", ReturnWatchState(), container.returnWatch.state.value)
        } finally {
            endEverything(controller)
        }
    }

    /**
     * The dispatch's first path with the real ViewModel in it, as far as a headless test goes:
     * `Recording, returning > the app is swiped away > the walker moves away > the alert arrives`.
     * Record and Return are the ViewModel's own callbacks. The origin waypoint is seeded from a
     * real fix and handed to the watch by the ViewModel. Then the ViewModel's store is cleared and
     * the walk away is delivered to the one listener that is left, the service's.
     */
    @Test
    fun `Record, Return, the ViewModel cleared, then a walk away from the start - the off-track notification is posted`() {
        val store = ViewModelStore()
        val vm = viewModelIn(store)
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
            val trackId = awaitActiveTrackId(vm)
            assertNotNull(trackId)
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId!!))
            val service = controller.create().get()
            controller.startCommand(0, 1)
            assertNotNull(shadowOf(service).lastForegroundNotification)
            assertEquals(2, awaitListenerCount(2))

            simulateFix(0) // 45.000, accuracy 5 m: the first gated fix, so the origin waypoint
            assertTrue("precondition: the origin waypoint exists, so the watch has its start point", awaitOrigin(vm))
            vm.startReturn()
            assertTrue(vm.uiState.value.isReturning)

            store.clear() // the swipe-away
            assertEquals("only the service is listening now", 1, awaitListenerCount(1))
            assertNull("precondition: no off-track notification yet", offTrackNotification())

            simulateFix(1)
            simulateFix(2)
            simulateFix(3)

            assertNotNull(
                "expected the off-track notification: the return was started on the screen, the screen is gone, and the walker moved away",
                awaitOffTrackNotification(),
            )
        } finally {
            endEverything(controller)
        }
    }

    /**
     * The other side of the two tests above, so their notification is not one that would have been
     * posted anyway: the same service, the same start point, more of the same walk away, and no
     * return under way. Twenty fixes, because twenty kept points is the write a test can wait for;
     * once they are stored, every one of them has been through the collector and so through the
     * watch.
     */
    @Test
    fun `with no return under way, the same walk away posts no off-track notification`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get()
            controller.startCommand(0, 1)
            assertEquals(1, awaitListenerCount(1))
            container.returnWatch.setStartPoint(trackId, TrackPoint(45.000, -122.0, null, null, FIRST_FIX_EPOCH_MILLIS))

            repeat(FLUSH_BATCH_SIZE) { index -> simulateFix(index + 1) }

            assertEquals("precondition: all twenty fixes went through the service's collector", FLUSH_BATCH_SIZE, awaitPointCount(trackId, FLUSH_BATCH_SIZE))
            assertNotNull("the watch measured them", container.returnWatch.state.value.returnToStart)
            assertFalse(container.returnWatch.state.value.isOffTrack)
            assertNull("outbound travel is never off track", offTrackNotification())
        } finally {
            endEverything(controller)
        }
    }

    /**
     * Dispatch 2026-09-28-425: a return is measured against the track as it stood when Return was
     * tapped, and the watch has that track because the service hands it each point its sampler
     * keeps. Twenty fixes through the real service's collector, stored as twenty points, then
     * Return: the path the return is measured against has all twenty. Fails with the service's
     * kept-point call removed (a path of 0).
     */
    @Test
    fun `the points the service keeps before Return are the path the return is measured against`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get()
            controller.startCommand(0, 1)
            assertEquals(1, awaitListenerCount(1))
            container.returnWatch.setStartPoint(trackId, TrackPoint(45.000, -122.0, null, null, FIRST_FIX_EPOCH_MILLIS))

            repeat(FLUSH_BATCH_SIZE) { index -> simulateFix(index + 1) }
            assertEquals("precondition: twenty points kept and stored", FLUSH_BATCH_SIZE, awaitPointCount(trackId, FLUSH_BATCH_SIZE))

            assertTrue(container.returnWatch.startReturn(trackId))
            assertEquals(FLUSH_BATCH_SIZE, container.returnWatch.pathPointCount)
        } finally {
            endEverything(controller)
        }
    }

    /**
     * Amendment 3, step 3: a start for the track already being recorded is not a fault. A screen
     * that takes up a running recording is a new Activity, and its effect sends `ACTION_START`
     * again for that same track. The service says nothing and changes nothing. A start for a
     * **different** track is still dropped with the warning (the second-recording test below).
     */
    @Test
    fun `a repeated start for the track already being recorded says nothing and changes nothing`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            val service = controller.create().get()
            controller.startCommand(0, 1)
            assertEquals(1, awaitListenerCount(1))
            val before = container.returnWatch.state.value

            ShadowLog.clear()
            controller.withIntent(startIntent(trackId)).startCommand(0, 2)

            assertEquals(
                "no warning for a start that names the track already being recorded",
                emptyList<String>(),
                ShadowLog.getLogsForTag("TrackRecordingService").filter { it.type >= android.util.Log.WARN }.map { it.msg },
            )
            assertEquals("still one collection of fixes", 1, awaitListenerCount(1))
            assertEquals("the watch is as it was", before, container.returnWatch.state.value)
            assertFalse(shadowOf(service).isStoppedBySelf)
        } finally {
            endEverything(controller)
        }
    }

    /**
     * The owner's main path with the real service and container:
     * `Recording > swipe the app away > open Forager again > the screen shows the recording still
     * running`, and `Stop on that screen ends the recording`.
     *
     * **This replaces Part 2's first `PART 3 FAULT` pin here.** The assertion that turned:
     * `isRecording` on the reopened ViewModel was false and is true.
     *
     * The reopened Activity's effect sends `ACTION_START` again for the track it took up
     * (`hasStartedRecordingOnce` starts false in a new Activity). That intent is sent here by hand,
     * as the effect would, and the service says nothing for it (step 3). Then Stop: the screen's
     * callback, and the `ACTION_STOP` the effect sends for it.
     */
    @Test
    fun `the service goes on recording after the ViewModel is cleared, a reopened ViewModel takes the recording up, and its Stop ends it`() {
        val firstStore = ViewModelStore()
        val first = viewModelIn(firstStore)
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            first.startRecording(TrackRecordingMode.HIGH_ACCURACY)
            val trackId = awaitActiveTrackId(first)
            assertNotNull("the ViewModel must have created a track before there is anything to swipe away from", trackId)
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId!!))
            val service = controller.create().get()
            controller.startCommand(0, 1)
            assertNotNull("precondition: the service is in the foreground, recording", shadowOf(service).lastForegroundNotification)
            assertEquals("precondition: the service and the screen's ViewModel are both listening for fixes", 2, awaitListenerCount(2))

            // The swipe-away, as far as either class can tell: the Activity's store is cleared, the service is not told anything.
            firstStore.clear()
            assertEquals("one listener is left, the service's", 1, awaitListenerCount(1))
            assertFalse("the service has not stopped itself", shadowOf(service).isStoppedBySelf)
            assertNull("the track is still open in storage", trackRow(trackId)?.endedAtEpochMillis)

            // Forager is opened again: a new Activity, so a new ViewModel.
            val reopened = viewModelIn(ViewModelStore())

            assertEquals("the reopened screen took up the recording the service is making", trackId, awaitActiveTrackId(reopened))
            assertTrue("so the record button offers Stop", reopened.uiState.value.isRecording)
            assertEquals(TrackRecordingMode.HIGH_ACCURACY, reopened.uiState.value.activeTrack?.mode)
            assertEquals("and it is listening for fixes again, beside the service", 2, awaitListenerCount(2))

            // What MainActivity's effect sends for the reopened screen's activeTrack: a start for the track already recording.
            ShadowLog.clear()
            controller.withIntent(startIntent(trackId)).startCommand(0, 2)
            assertEquals(
                "the repeated start is not a fault and is not logged as one",
                emptyList<String>(),
                ShadowLog.getLogsForTag("TrackRecordingService").filter { it.type >= android.util.Log.WARN }.map { it.msg },
            )

            // Stop on the reopened screen, then the ACTION_STOP the effect sends for it.
            reopened.stopRecording()
            controller.withIntent(stopIntent()).startCommand(0, 3)

            assertNotNull("Stop on the reopened screen ended the recording", awaitEndedAt(trackId))
            assertTrue(awaitStoppedBySelf(service))
            assertFalse(reopened.uiState.value.isRecording)
            assertEquals("one track, ended; no second row was made anywhere on the way", listOf(trackId), allTrackIds())
        } finally {
            endEverything(controller)
        }
    }

    /**
     * The owner's path 3 with the real service and container: Record pressed while another
     * recording is running. The reopened ViewModel is asked to start before its take-up has
     * finished (the row is still being read), which is the one moment Record could be pressed in
     * that state. Nothing is started, no track row is made, and the screen carries the owner's
     * words.
     *
     * **This replaces Part 2's second `PART 3 FAULT` pin here** (two open rows, points in the wrong
     * one). The assertion that turned: a second track id then, one row now.
     */
    @Test
    fun `Record on a reopened screen while the service is recording starts nothing, makes no second track, and says so`() {
        val firstStore = ViewModelStore()
        val first = viewModelIn(firstStore)
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            first.startRecording(TrackRecordingMode.HIGH_ACCURACY)
            val firstTrackId = awaitActiveTrackId(first)
            assertNotNull(firstTrackId)
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(firstTrackId!!))
            controller.create().get()
            controller.startCommand(0, 1)
            assertEquals(2, awaitListenerCount(2))
            firstStore.clear()
            assertEquals(1, awaitListenerCount(1))

            val reopened = viewModelIn(ViewModelStore())
            assertFalse("precondition: the take-up has not finished, so this screen has no recording yet", reopened.uiState.value.isRecording)

            reopened.startRecording(TrackRecordingMode.HIGH_ACCURACY) // the record button, MainActivity.kt's onToggleRecording

            assertEquals(ALREADY_RECORDING, reopened.uiState.value.startRecordingErrorMessage)
            assertEquals("the screen then takes up the recording that is running, the first one", firstTrackId, awaitActiveTrackId(reopened))
            assertEquals("no second track row was made", listOf(firstTrackId), allTrackIds())
        } finally {
            endEverything(controller)
        }
    }

    /**
     * Amendment 2's ruling 8, kept: a start for a **different** track while one is being recorded
     * is dropped with a warning that names both, and every point still goes to the track being
     * recorded. Since step 4 the app itself can no longer make that second track; the second row
     * here is made by hand, to show what the service does if such a start ever reaches it.
     *
     * Twenty fixes, because twenty kept points is the service's batch, the one write a test can
     * wait for. That the first track then holds twenty is the positive half: the fixes did flow,
     * so the second track's zero is a reading and not an empty stream.
     */
    @Test
    fun `a start for a different track while one is being recorded is dropped with a warning, and every point still goes to the first`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val firstTrackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            val secondTrackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(firstTrackId))
            val service = controller.create().get()
            controller.startCommand(0, 1)
            assertNotNull("precondition: the service is recording the first track", shadowOf(service).lastForegroundNotification)
            assertEquals(1, awaitListenerCount(1))

            ShadowLog.clear()
            controller.withIntent(startIntent(secondTrackId)).startCommand(0, 2)

            assertEquals(
                "the dropped start is logged, naming the track it was for and the track being recorded",
                listOf("Ignoring a start for track '$secondTrackId': already recording track '$firstTrackId'."),
                ShadowLog.getLogsForTag("TrackRecordingService").filter { it.type == android.util.Log.WARN }.map { it.msg },
            )
            assertEquals("the watch is still for the first track", firstTrackId, container.returnWatch.state.value.trackId)
            assertEquals("the service did not start a second collection", 1, awaitListenerCount(1))

            repeat(FLUSH_BATCH_SIZE) { index -> simulateFix(index) }

            assertEquals("the points went to the first track", FLUSH_BATCH_SIZE, awaitPointCount(firstTrackId, FLUSH_BATCH_SIZE))
            assertEquals("and none to the track whose start was dropped", 0, pointCount(secondTrackId))

            controller.withIntent(stopIntent()).startCommand(0, 3)
            assertNotNull("the stop ended the first track, the only one the service knows", awaitEndedAt(firstTrackId))
            assertTrue(awaitStoppedBySelf(service))
        } finally {
            endEverything(controller)
        }
    }

    /**
     * Dispatch premise 6, the part a headless test reaches: `START_STICKY` asks the system to
     * recreate the service after the process is killed, and what it then delivers is a null
     * intent. `onStartCommand` starts nothing for it: no foreground notification, no listener, and
     * it asks to be sticky again.
     *
     * The positive half of these two readings is in the tests above, where `ACTION_START` on this
     * same class does produce a foreground notification and a listener.
     *
     * Not reached: whether and when the system restarts the service, and what Android does with a
     * restarted service that never calls `startForeground`. Out of scope to fix (the dispatch).
     */
    @Test
    fun `today - a null intent, which is what a sticky restart delivers after the process is killed, starts nothing`() {
        val controller = Robolectric.buildService(TrackRecordingService::class.java)
        val service = controller.create().get()
        try {
            val result = service.onStartCommand(null, 0, 1)
            idleAndSettle()

            assertEquals(Service.START_STICKY, result)
            assertNull("nothing was promoted to the foreground", shadowOf(service).lastForegroundNotification)
            assertEquals("nothing is listening for fixes", 0, shadowLocationManager.requestLocationUpdateListeners.size)
            assertEquals("the watch was not begun", ReturnWatchState(), container.returnWatch.state.value)
            assertFalse("and the service did not stop itself either: it sits there doing nothing", shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
            drainBeforeTeardown()
        }
    }

    /**
     * One GPS fix, [index] steps north of 45.000, delivered through the platform's own listener
     * path. Wall time and elapsed time both move 10 s per fix: the tracker asks the platform for at
     * most one fix a second, and HIGH_ACCURACY's sampler wants 5 s and 5 m between kept points.
     */
    private fun simulateFix(index: Int) {
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 45.000 + index * 0.001
                longitude = -122.0
                accuracy = 5f
                time = FIRST_FIX_EPOCH_MILLIS + index * 10_000L
                elapsedRealtimeNanos = (index + 1) * 10_000_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun offTrackNotification() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(OFF_TRACK_NOTIFICATION_ID)

    /** The watch is fed on the service's own background thread, so the notification is not there the instant the fix is simulated. Polls; `null` on timeout, so a missing delivery fails and does not hang. */
    private fun awaitOffTrackNotification(timeoutMillis: Long = 10_000L): android.app.Notification? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            offTrackNotification()?.let { return it }
            idleAndSettle()
        }
        return null
    }

    private fun awaitOrigin(viewModel: TrackRecordingViewModel, timeoutMillis: Long = 10_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (viewModel.uiState.value.originWaypoint != null) return true
            idleAndSettle()
        }
        return false
    }

    private fun allTrackIds(): List<String> = runBlocking { container.trackRepository.getAll() }.getOrThrow().map { it.id }

    private fun trackRow(trackId: String) = runBlocking { container.trackRepository.getById(trackId) }.getOrThrow()

    /** Every stored point, kept or not: the unfiltered read, so the read seam's network-fix filter cannot hide a write. */
    private fun pointCount(trackId: String): Int = runBlocking { container.trackRepository.getFullRecord(trackId) }.getOrThrow().size

    /** Polls until the track holds at least [atLeast] points, and returns the count it last read, so a shortfall fails with the number. */
    private fun awaitPointCount(trackId: String, atLeast: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = pointCount(trackId)
        while (count < atLeast && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = pointCount(trackId)
        }
        return count
    }

    /** Polls until exactly [expected] listeners are registered, and returns the count it last read. */
    private fun awaitListenerCount(expected: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = shadowLocationManager.requestLocationUpdateListeners.size
        while (count != expected && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = shadowLocationManager.requestLocationUpdateListeners.size
        }
        return count
    }

    private fun awaitActiveTrackId(viewModel: TrackRecordingViewModel, timeoutMillis: Long = 10_000L): String? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            viewModel.uiState.value.activeTrack?.let { return it.trackId }
            idleAndSettle()
        }
        return null
    }

    /** `init`'s `loadTracks` is a database read; this waits for it, so a "not recording" read after it is not just "not loaded yet". */
    private fun awaitTrackListed(viewModel: TrackRecordingViewModel, trackId: String, timeoutMillis: Long = 10_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (viewModel.uiState.value.tracks.any { it.id == trackId }) return true
            idleAndSettle()
        }
        return false
    }

    private fun awaitEndedAt(trackId: String, timeoutMillis: Long = 10_000L): Long? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            trackRow(trackId)?.endedAtEpochMillis?.let { return it }
            Thread.sleep(25L)
        }
        return null
    }

    private fun awaitStoppedBySelf(service: TrackRecordingService, timeoutMillis: Long = 5_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (shadowOf(service).isStoppedBySelf) return true
            Thread.sleep(25L)
        }
        return false
    }

    /**
     * Stops every ViewModel and the service, whatever state the body left them in, and waits for
     * the service's stop to finish before the controller is destroyed. See this class's header.
     */
    private fun endEverything(controller: ServiceController<TrackRecordingService>?) {
        createdViewModels.forEach { it.stopRecording() }
        if (controller != null) {
            val service = controller.get()
            if (!shadowOf(service).isStoppedBySelf) {
                controller.withIntent(stopIntent()).startCommand(0, 99)
                awaitStoppedBySelf(service)
            }
            controller.destroy()
        }
        idleAndSettle()
        drainBeforeTeardown()
    }

    private fun idleAndSettle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** As `TrackRecordingServiceTest.drainBeforeTeardown`, for the reason recorded there. */
    private fun drainBeforeTeardown() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(250L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        /** Mirrors `TrackRecordingService`'s own private constant of the same name. */
        const val FLUSH_BATCH_SIZE = 20
        const val FIRST_FIX_EPOCH_MILLIS = 1_790_000_000_000L

        /** The owner's sentence, as `MainActivity` reads it from `strings.xml` and passes it in. */
        const val ALREADY_RECORDING = "Forager is already recording a track. Stop it before starting another."
    }
}
