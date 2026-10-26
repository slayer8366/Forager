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
 * Dispatch 2026-10-26-07 (RECORD -1654): **the way home never starts from a network-only position.** The service hands
 * the way home every point its sampler keeps ([RecordingWatches.wayHomeOnKeptPoint]), and the sampler keeps a network
 * fix whose reported accuracy clears the mode's ceiling (`LocationSampler.shouldAccept`). The stored track the way home
 * reads is filtered by the store's read path (`excludeNetworkProviderFixes`); the held points it appends must be too,
 * or a network fix kept since the last write becomes the line's start.
 *
 * As [TrackRecordingServiceWayHomeNewestPointTest]: the real service, container, repository and platform location
 * path, with both providers on. Twenty-three fixes: twenty GPS fixes written (the batch of 20), then GPS fixes 21 and
 * 22 and a network fix 23 kept and pending. The precondition confirms the service handed the network fix to the way
 * home, so the case is the one that can fail.
 *
 * Every test stops what it started inside its own body, as `TrackRecordingServiceSwipeAwayTest`'s header records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceWayHomeNetworkFixTest {

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
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }

    @Test
    fun `at Return a network fix kept since the last write is not the way home's start`() {
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
        val kept = java.util.Collections.synchronizedList(mutableListOf<TrackPoint>())
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get().watchesFor = { ToTheWatch(it, watch, kept) }
            controller.startCommand(0, 1)
            assertEquals("precondition: the service is listening", 1, awaitListenerCount(1))

            repeat(FIXES - 1) { index -> simulateFix(index + 1, LocationManager.GPS_PROVIDER, 0L) }
            simulateFix(FIXES, LocationManager.NETWORK_PROVIDER, NETWORK_MILLIS)
            assertEquals("precondition: the batch of 20 is written", FLUSH_BATCH_SIZE, awaitStoredCount(trackId, FLUSH_BATCH_SIZE))
            idleAndSettle()
            assertEquals(
                "precondition: the last three kept points are not written yet, so this is the case that can fail",
                FLUSH_BATCH_SIZE,
                storedCount(trackId),
            )
            assertTrue(
                "precondition: the service kept the network fix and handed it to the way home",
                kept.any { it.lat == fixLat(FIXES) && it.timestampEpochMillis % 1_000L == NETWORK_MILLIS },
            )

            watch.find(trackId, null)
            val track = runBlocking { withTimeout(10_000L) { handed.await() } }

            assertEquals(
                "the way home is found on the kept GPS points only, in order, once each: the twenty written and the two pending",
                (1 until FIXES).map { fixLat(it) },
                track.points.map { it.lat },
            )
            assertEquals("the line's first point is the newest kept GPS point, not the network fix", fixLat(FIXES - 1), track.points.last().lat, 0.0)
        } finally {
            controller?.let { end(it) }
            scope.cancel()
        }
    }

    /** The container's watches, with the way home's two calls sent to [watch] instead of the container's, and each kept point noted in [kept]. */
    private class ToTheWatch(container: AppContainer, private val watch: WayHomeWatch, private val kept: MutableList<TrackPoint>) : RecordingWatches(container) {
        override fun wayHomeOnKeptPoint(trackId: String, point: TrackPoint) {
            kept += point
            watch.onKeptPoint(trackId, point)
        }
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

    /**
     * 10 s and 0.001 degrees apart, at 5 m accuracy for GPS and 20 m for the network fix (under HIGH_ACCURACY's 30 m
     * ceiling), so HIGH_ACCURACY keeps every one. [millis] is the fix's sub-second part: 0 for GPS, which is
     * second-aligned, and non-zero for the network fix, which is what the store's read path excludes.
     */
    private fun simulateFix(index: Int, provider: String, millis: Long) {
        shadowLocationManager.simulateLocation(
            Location(provider).apply {
                latitude = fixLat(index)
                longitude = -122.0
                accuracy = if (provider == LocationManager.NETWORK_PROVIDER) 20f else 5f
                time = FIRST_FIX_EPOCH_MILLIS + index * 10_000L + millis
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
        const val NETWORK_MILLIS = 123L
        const val FIRST_FIX_EPOCH_MILLIS = 1_790_000_000_000L
    }
}
