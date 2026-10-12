package com.zynergylabs.forager.app.diagnostics.walklog

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.diagnostics.WalkLogger
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.service.TrackRecordingService
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowPowerManager

/**
 * The walk logger through the recording's real entry point, `TrackRecordingService`'s own
 * `onStartCommand` (dispatch 2026-09-28-532; Amendment 3, RECORD -560): it runs only while the
 * Diagnostics switch is on, starts and stops with the recording, holds a wake lock only while it
 * logs (RECORD -559, choice 3), and storage running low stops the log, never the recording.
 *
 * The switch is the app container's own store, the one the Diagnostics panel writes. Each test that
 * starts the service stops it through `ACTION_STOP` and destroys it in a `finally`, as
 * `TrackRecordingServiceSwipeAwayTest` records why. One provider (GPS) is enabled, so the tracker's
 * collection is exactly one registered listener and the logger's passive listener is the second.
 * Coordinates are made up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WalkLoggerServiceTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        // No stand-in for free space: every test but the storage-low one reads the real temporary
        // filesystem, with no walklogs/ folder yet, as on a fresh install (RECORD -569). A 10 GB
        // stand-in here once hid the bug where a missing folder read as 0 bytes free.
        assertTrue("precondition: no walklogs/ folder before the first recording", !walkLogDir().exists())
    }

    @After
    fun tearDown() {
        WalkLogger.freeBytesOverride = null
    }

    private fun walkLoggerSwitch(): WalkLoggerSwitch {
        val switch = container.forecastCellStore as? WalkLoggerSwitch
        assertNotNull("the debug container's diagnostics store is the walk logger's switch", switch)
        return switch!!
    }

    private fun switchOn() = runBlocking { walkLoggerSwitch().setWalkLoggerEnabled(true).getOrThrow() }

    private fun walkLogDir() = File(context.getExternalFilesDir(null) ?: context.filesDir, WalkLogger.DIRECTORY_NAME)

    private fun walkLogFiles(): List<File> = walkLogDir().listFiles()?.filter { it.isFile }.orEmpty()

    private fun newTrack(trackId: String) = runBlocking {
        container.trackRepository.create(
            Track(id = trackId, name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList()),
        ).getOrThrow()
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    @Test
    fun `the switch is off until turned on`() {
        assertEquals(false, runBlocking { walkLoggerSwitch().isWalkLoggerEnabled().getOrThrow() })
    }

    /**
     * The guarantee the existing listener-counting tests rely on (Amendment 3): with the switch off,
     * as it is by default, a recording has one listener, the tracker's, and no log, no wake lock.
     * Watched for a full second after the recording starts, because the logger starts on its own
     * thread and an extra listener would arrive late, not at once.
     */
    @Test
    fun `with the switch off, a recording registers no listener beyond the tracker's and writes no walk log`() {
        val trackId = "walklog-off"
        newTrack(trackId)
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        try {
            controller.create().startCommand(0, 1)
            assertEquals("the tracker's listener arrives", 1, awaitListenerCount(1))

            val deadline = System.currentTimeMillis() + 1_000L
            while (System.currentTimeMillis() < deadline) {
                idleAndSettle()
                assertEquals("never a second listener with the switch off", 1, shadowLocationManager.requestLocationUpdateListeners.size)
            }
            assertFalse(WalkLogger.of(context).isLogging)
            assertTrue("no walk log file: ${walkLogFiles()}", walkLogFiles().isEmpty())
            assertFalse("no wake lock", ShadowPowerManager.getLatestWakeLock()?.isHeld == true)
        } finally {
            end(controller)
        }
    }

    @Test
    fun `switched on, the log starts with the recording, holds a wake lock, and ends with it`() {
        switchOn()
        val trackId = "walklog-on"
        newTrack(trackId)
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        try {
            controller.create().startCommand(0, 1)

            val started = await { WalkLogger.of(context).isLogging }
            assertTrue("the logger starts with the recording; the log reads: ${walkLogFiles().map { it.readText() }}", started)
            val file = walkLogFiles().single()
            assertTrue("named for its start: ${file.name}", file.name.startsWith("walklog-") && file.name.endsWith(".txt"))
            assertEquals("# walklog version=1", file.readLines().first())
            assertEquals("the tracker's listener and the logger's passive one", 2, awaitListenerCount(2))
            val wakeLock = ShadowPowerManager.getLatestWakeLock()
            assertNotNull("a wake lock while logging", wakeLock)
            assertTrue(wakeLock!!.isHeld)
            assertEquals(WalkLogger.WAKE_LOCK_TAG, shadowOf(wakeLock).tag)

            controller.withIntent(stopIntent()).startCommand(0, 2)

            assertTrue("the logger stops with the recording", await { !WalkLogger.of(context).isLogging })
            assertEquals("${file.readLines().takeLast(3)}", "END reason=recording-stopped", file.readLines().last().substringAfter(' '))
            assertFalse("the wake lock is let go", wakeLock.isHeld)
            assertEquals("no listener is left behind", 0, awaitListenerCount(0))
        } finally {
            end(controller)
        }
    }

    @Test
    fun `a fix delivered during a logged recording is written with its provider`() {
        switchOn()
        val trackId = "walklog-fix"
        newTrack(trackId)
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        try {
            controller.create().startCommand(0, 1)
            assertTrue(await { WalkLogger.of(context).isLogging })
            assertEquals(2, awaitListenerCount(2))

            simulateFix(index = 1)
            // Read after the stop, which flushes and closes the file: the writer flushes every 5 s
            // of since-boot time, and Robolectric's clock does not move unless a test moves it.
            controller.withIntent(stopIntent()).startCommand(0, 2)
            assertTrue(await { !WalkLogger.of(context).isLogging })

            val file = walkLogFiles().single()
            assertTrue(
                "a FIX line from the logger's own listener, in:\n${file.readLines().filterNot { it.startsWith("#") }.joinToString("\n")}",
                file.readLines().any { " FIX " in it && "provider=gps" in it && "lat=10.001" in it },
            )
        } finally {
            end(controller)
        }
    }

    @Test
    fun `a service destroyed without a stop ends the log and lets go of the wake lock`() {
        switchOn()
        val trackId = "walklog-destroyed"
        newTrack(trackId)
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        controller.create().startCommand(0, 1)
        assertTrue(await { WalkLogger.of(context).isLogging })
        val file = walkLogFiles().single()

        controller.destroy()
        idleAndSettle()

        assertTrue("the logger stops with the service", await { !WalkLogger.of(context).isLogging })
        assertTrue(file.readLines().last().contains(" END "))
        assertFalse(ShadowPowerManager.getLatestWakeLock()!!.isHeld)
        drain()
    }

    @Test
    fun `storage below 200 MB stops the log, never the recording`() {
        WalkLogger.freeBytesOverride = { 1_000L }
        switchOn()
        val trackId = "walklog-storage-low"
        newTrack(trackId)
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        try {
            controller.create().startCommand(0, 1)

            assertTrue("a walk log file saying why", await { walkLogFiles().singleOrNull()?.readLines()?.lastOrNull()?.contains(" STOPPED reason=storage-low ") == true })
            assertFalse(WalkLogger.of(context).isLogging)
            assertFalse("no wake lock for a log that is not written", ShadowPowerManager.getLatestWakeLock()?.isHeld == true)
            assertEquals("the recording's own listener is still there", 1, awaitListenerCount(1))

            // The recording keeps collecting with the log stopped: twenty fixes, the service's write batch, stored as
            // twenty points before the stop. Waited for, not assumed: the service's collector runs on its own thread,
            // and a stop sent straight after the fixes cancels it before it has taken them. That race failed this test
            // on CI with a track ended and no points (RECORD -850; docs/audits/2026-10-11-main-red-tests.md).
            repeat(FLUSH_BATCH_SIZE) { index -> simulateFix(index = index + 1) }
            assertEquals("the recording kept collecting with the log stopped", FLUSH_BATCH_SIZE, awaitPointCount(trackId, FLUSH_BATCH_SIZE))
            controller.withIntent(stopIntent()).startCommand(0, 2)

            val endedWithItsPoints = await { runBlocking { container.trackRepository.getById(trackId) }.getOrNull()?.let { it.endedAtEpochMillis != null && it.points.size == FLUSH_BATCH_SIZE } == true }
            val track = runBlocking { container.trackRepository.getById(trackId) }.getOrNull()
            assertTrue("the recording ended with its points: ended=${track?.endedAtEpochMillis} points=${track?.points?.size}", endedWithItsPoints)
        } finally {
            end(controller)
        }
    }

    private fun simulateFix(index: Int) {
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 10.000 + index * 0.001
                longitude = 20.0
                accuracy = 5f
                time = 1_700_000_000_000L + index * 10_000L
                elapsedRealtimeNanos = (index + 1) * 10_000_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun pointCount(trackId: String): Int = runBlocking { container.trackRepository.getFullRecord(trackId) }.getOrThrow().size

    /** As `TrackRecordingServiceSwipeAwayTest.awaitPointCount`: polls, and returns the count last read, so a shortfall fails with the number. */
    private fun awaitPointCount(trackId: String, atLeast: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = pointCount(trackId)
        while (count < atLeast && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = pointCount(trackId)
        }
        return count
    }

    private fun await(timeoutMillis: Long = 10_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            idleAndSettle()
        }
        return condition()
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
        drain()
    }

    private fun idleAndSettle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** As `TrackRecordingServiceTest.drainBeforeTeardown`, for the reason recorded there; also lets the logger's own thread finish. */
    private fun drain() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(250L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        /** `TrackRecordingService`'s private write batch, as `TrackRecordingServiceSwipeAwayTest` restates it. */
        const val FLUSH_BATCH_SIZE = 20
    }
}
