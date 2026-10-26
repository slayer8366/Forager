package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
 * Dispatch 2026-10-25-10 (RECORD -1636): **the way home starts from the walker's newest kept point**, including the
 * points the service holds in memory and has not written yet (Sorrel's bends report: the line started up to about
 * 42 m behind the walker, one flush interval).
 *
 * The real [TrackRecordingService], the real container and repository, and the platform's location path. The kept
 * points reach the way home through the service's own call ([RecordingWatches.wayHomeOnKeptPoint]); the way home reads
 * the stored track through the real `TrackRepository.getById`, as `AppContainer` wires it. The one stand-in is the
 * route search, which records the track it is handed: the line's track part starts from that track's last point
 * (`FindWayHomeUseCase`, `trackPart(stored, from = points.lastIndex, …)`), so the last point is the line's start.
 * Return is the call the recording ViewModel's Return makes, `WayHomeWatch.find(trackId, origin)`.
 *
 * Twenty-three fixes: the first twenty are written (the service's batch of 20), the last three are kept and pending.
 * The precondition reads the store, so the case is the one that fails: three kept points not in the stored track.
 *
 * Every test stops what it started inside its own body, as `TrackRecordingServiceSwipeAwayTest`'s header records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceWayHomeNewestPointTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    @Test
    fun `at Return the way home starts from the newest kept point, written or not`() {
        var controller: ServiceController<TrackRecordingService>? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handed = CompletableDeferred<Track>()
        val watch = WayHomeWatch(
            scope = scope,
            readTrack = container.trackRepository::getById,
            findWayHome = { track: Track, _: Waypoint? ->
                handed.complete(track)
                WayHomeResult.TrackOnly(null)
            },
            isReturning = { true },
            errorLog = ErrorLog { _, _, _ -> },
        )
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get().watchesFor = { ToTheWatch(it, watch) }
            controller.startCommand(0, 1)
            assertEquals("precondition: the service is listening", 1, awaitListenerCount(1))

            repeat(FIXES) { index -> simulateFix(index + 1) }
            assertEquals("precondition: the batch of 20 is written", FLUSH_BATCH_SIZE, awaitStoredCount(trackId, FLUSH_BATCH_SIZE))
            idleAndSettle()
            assertEquals(
                "precondition: the last three kept points are not written yet, so this is the case that can fail",
                FLUSH_BATCH_SIZE,
                storedCount(trackId),
            )

            watch.find(trackId, null)
            val track = runBlocking { withTimeout(10_000L) { handed.await() } }

            assertEquals(
                "the way home is found on every kept point, in order, once each: the twenty written and the three pending",
                (1..FIXES).map { fixLat(it) },
                track.points.map { it.lat },
            )
            assertEquals("the line's first point is the newest kept point", fixLat(FIXES), track.points.last().lat, 0.0)
        } finally {
            controller?.let { end(it) }
            scope.cancel()
        }
    }

    /** The container's watches, with the way home's two calls sent to [watch] instead of the container's. */
    private class ToTheWatch(container: AppContainer, private val watch: WayHomeWatch) : RecordingWatches(container) {
        override fun wayHomeOnKeptPoint(trackId: String, point: TrackPoint) = watch.onKeptPoint(trackId, point)
        override fun wayHomeOnFix(point: TrackPoint, provider: com.zynergylabs.forager.app.domain.FixProvider) = watch.onFix(point, provider)
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    private fun fixLat(index: Int) = 45.000 + index * 0.001

    /** As `TrackRecordingServiceWatchFailureTest.simulateFix`: 10 s and 0.001 degrees apart, so HIGH_ACCURACY keeps every one. */
    private fun simulateFix(index: Int) {
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = fixLat(index)
                longitude = -122.0
                accuracy = 5f
                time = FIRST_FIX_EPOCH_MILLIS + index * 10_000L
                elapsedRealtimeNanos = (index + 1) * 10_000_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun storedCount(trackId: String): Int = runBlocking { container.trackRepository.getById(trackId) }.getOrThrow()!!.points.size

    private fun awaitStoredCount(trackId: String, atLeast: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = storedCount(trackId)
        while (count < atLeast && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = storedCount(trackId)
        }
        return count
    }

    private fun awaitListenerCount(expected: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = shadowLocationManager.requestLocationUpdateListeners.size
        while (count != expected && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = shadowLocationManager.requestLocationUpdateListeners.size
        }
        return count
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
        /** Mirrors `TrackRecordingService`'s own private constant of the same name. */
        const val FLUSH_BATCH_SIZE = 20
        const val FIXES = 23
        const val FIRST_FIX_EPOCH_MILLIS = 1_790_000_000_000L
    }
}
