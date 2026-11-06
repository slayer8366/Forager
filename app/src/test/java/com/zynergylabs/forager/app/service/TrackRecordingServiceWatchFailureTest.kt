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
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dispatch 2026-09-28-658, scout item R1: **a watch that throws on a fix does not end the
 * recording.** The real [TrackRecordingService], the real container and the platform's own
 * location path, with one change: the service is handed a [RecordingWatches] whose one named call
 * throws on every fix. Twenty fixes go in; the reading is the twenty points stored in the track,
 * the service's batch, which only arrive if the collector kept running past the first throw.
 *
 * One test per guarded call, because each guard is its own line in the service and each must bite
 * on its own: with any one of them removed, its test stores no point at all (the first fix's throw
 * cancels the collector before the sampler keeps anything), and fails with the count.
 *
 * Every test stops what it started inside its own body, as `TrackRecordingServiceSwipeAwayTest`'s
 * header records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceWatchFailureTest {

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
    fun `a return watch that throws on every fix leaves every point saved`() =
        recordsThroughAThrowing(Throwing.RETURN_ON_FIX)

    @Test
    fun `a sundown watch that throws on every fix leaves every point saved`() =
        recordsThroughAThrowing(Throwing.SUNDOWN_ON_FIX)

    @Test
    fun `a return watch that throws on every kept point leaves every point saved`() =
        recordsThroughAThrowing(Throwing.RETURN_ON_KEPT_POINT)

    /**
     * Dispatch 2026-09-28-645 (RECORD -674 and -687): the back-by watch's fix call is guarded the same
     * way. Without its guard the first fix's throw cancels the collector and no point is stored.
     */
    @Test
    fun `a back-by watch that throws on every fix leaves every point saved`() =
        recordsThroughAThrowing(Throwing.BACK_BY_ON_FIX)

    private fun recordsThroughAThrowing(which: Throwing) {
        var controller: ServiceController<TrackRecordingService>? = null
        val watches = ThrowingWatches(container, which)
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get().watchesFor = { watches }
            controller.startCommand(0, 1)
            assertEquals("precondition: the service is listening", 1, awaitListenerCount(1))

            repeat(FLUSH_BATCH_SIZE) { index -> simulateFix(index + 1) }

            assertEquals(
                "every fix's point is stored although ${which.name} threw on each",
                FLUSH_BATCH_SIZE,
                awaitPointCount(trackId, FLUSH_BATCH_SIZE),
            )
            // The throwing call was reached on every fix (or every kept point), so the count above
            // is the guard at work, not a call that never ran.
            assertEquals("${which.name} was called and threw for each of the twenty", FLUSH_BATCH_SIZE, watches.throws.get())
            assertTrue("the other calls still ran", watches.passedThrough.get() > 0)
        } finally {
            controller?.let { end(it) }
        }
    }

    private enum class Throwing { RETURN_ON_FIX, SUNDOWN_ON_FIX, RETURN_ON_KEPT_POINT, BACK_BY_ON_FIX }

    /** The container's watches, except that [which] throws every time it is called. */
    private class ThrowingWatches(container: AppContainer, private val which: Throwing) : RecordingWatches(container) {
        val throws = AtomicInteger(0)
        val passedThrough = AtomicInteger(0)

        override fun returnOnFix(point: TrackPoint, provider: FixProvider) {
            if (which == Throwing.RETURN_ON_FIX) fail()
            passedThrough.incrementAndGet()
            super.returnOnFix(point, provider)
        }

        override fun sundownOnFix(point: TrackPoint, provider: FixProvider): Boolean {
            if (which == Throwing.SUNDOWN_ON_FIX) fail()
            passedThrough.incrementAndGet()
            return super.sundownOnFix(point, provider)
        }

        override fun returnOnKeptPoint(point: TrackPoint) {
            if (which == Throwing.RETURN_ON_KEPT_POINT) fail()
            passedThrough.incrementAndGet()
            super.returnOnKeptPoint(point)
        }

        override fun backByOnFix(point: TrackPoint, provider: FixProvider): Boolean {
            if (which == Throwing.BACK_BY_ON_FIX) fail()
            passedThrough.incrementAndGet()
            return super.backByOnFix(point, provider)
        }

        private fun fail(): Nothing {
            throws.incrementAndGet()
            throw IllegalStateException("A watch failed on purpose (test).")
        }
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    /** As `TrackRecordingServiceSwipeAwayTest.simulateFix`: 10 s and 0.001 degrees apart, so HIGH_ACCURACY keeps every one. */
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

    /** Every stored point, through the unfiltered read. */
    private fun pointCount(trackId: String): Int = runBlocking { container.trackRepository.getFullRecord(trackId) }.getOrThrow().size

    private fun awaitPointCount(trackId: String, atLeast: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = pointCount(trackId)
        while (count < atLeast && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = pointCount(trackId)
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
        const val FIRST_FIX_EPOCH_MILLIS = 1_790_000_000_000L
    }
}
