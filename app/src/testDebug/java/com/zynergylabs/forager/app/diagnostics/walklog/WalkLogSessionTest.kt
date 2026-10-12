package com.zynergylabs.forager.app.diagnostics.walklog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One walk log from start to stop, against a fake phone (dispatch 2026-09-28-532, Evidence):
 * every event the phone delivers becomes its line; anything it does not offer is written as
 * unsupported, never left out; running out of storage stops the log and says so. The service-level
 * half (the log follows the recording, and storage-low never stops the recording) is
 * `WalkLoggerServiceTest`. Coordinates are made up.
 *
 * Under Robolectric only because the code logs its stops through `android.util.Log`, which a plain
 * JVM test here cannot call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WalkLogSessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val platform = FakePlatform()

    private fun newSession(): Pair<WalkLogSession, File> {
        val file = File(tempFolder.root, "walklog-test.txt")
        val writer = WalkLogWriter(file, freeBytes = { platform.free }, nowNanos = { platform.nanos })
        return WalkLogSession(platform, writer) to file
    }

    @Test
    fun `start writes the header first, then asks the phone for every source`() {
        val (session, file) = newSession()

        session.start()

        assertTrue(session.isActive)
        val lines = file.readLines()
        assertEquals("# walklog version=1", lines.first())
        assertTrue("the header names the phone", lines.any { it.startsWith("# device ") && "model=SM-FAKE" in it })
        assertTrue("fixes", platform.fixesRegistered)
        assertTrue("satellite status", platform.statusRegistered)
        assertTrue("raw measurements", platform.measurementsRegistered)
        assertEquals("every sensor the phone offers", WalkLogSensorKind.entries.toSet(), platform.sensorsRegistered)
        assertTrue("the minute timer", platform.tick != null)
        assertFalse("a phone that offers everything has no unsupported line", lines.any { " UNSUPPORTED " in it })
        session.stop()
    }

    /**
     * The walk logger reports itself logging as soon as its session is active, and `WalkLoggerServiceTest` read the file
     * at that moment: on CI (run 38115289698) it found the file empty, its header still in the writer's buffer
     * (`NoSuchElementException` at `WalkLoggerServiceTest.kt:148`). The header is now written and flushed before the
     * session marks itself active. Read here at the first call the session makes once active, with the fake clock
     * standing still, so no time-based flush can have written it.
     */
    @Test
    fun `once the session is active its header is already on disk`() {
        val (session, file) = newSession()
        var seen: Pair<Boolean, List<String>>? = null
        platform.onRegisterFixes = { seen = session.isActive to file.readLines() }

        session.start()

        val (active, lines) = seen!!
        assertTrue("precondition: active when it asks for fixes", active)
        assertEquals("the header is on disk the moment the session is active: $lines", "# walklog version=1", lines.firstOrNull())
        session.stop()
    }

    @Test
    fun `each delivered event is one line, keyed on the since-boot clock`() {
        val (session, file) = newSession()
        session.start()

        platform.nanos = 7_000L
        session.onFix(FixRecord("passive", "gps", 1.5, 2.5, 3.79f, null, null, null, null, null, null, 5L, 6_999L))
        platform.nanos = 7_001L
        session.onGnssStatus(listOf(SatelliteRecord("GPS", 12, 30f, 40f, 50f, true, null)))
        platform.nanos = 7_002L
        session.onGnssMeasurements(
            GnssClockRecord(1L, null, null, null, null, 0, null),
            listOf(GnssMeasurementRecord("GPS", 12, 30.0, 1, 2L, 3L, 0.0, 4.0, 5.0, 16, 6.0, 7.0, null, 0, null)),
        )
        platform.nanos = 7_003L
        session.onSensor(SensorRecord(WalkLogSensorKind.PRESSURE, 7_000L, floatArrayOf(1013.25f), 3))
        platform.nanos = 7_004L
        session.onSensorAccuracy(WalkLogSensorKind.MAGNETOMETER, 2)
        platform.nanos = 7_005L
        session.onGnssMeasurementsStatus(1)
        session.stop()

        val events = file.readLines().filterNot { it.startsWith("#") }
        assertTrue(events.joinToString("\n"), events.any { it.startsWith("7000 FIX listener=passive provider=gps lat=1.5 lon=2.5 acc=3.79 ") })
        assertTrue(events.joinToString("\n"), "7001 GNSS_STATUS n=1" in events)
        assertTrue(events.joinToString("\n"), events.any { it.startsWith("7001 GNSS_SAT c=GPS svid=12 ") })
        assertTrue(events.joinToString("\n"), events.any { it.startsWith("7002 GNSS_CLOCK timeNanos=1 ") })
        assertTrue(events.joinToString("\n"), events.any { it.startsWith("7002 GNSS_MEAS c=GPS svid=12 ") && " adrState=16 " in it })
        assertTrue(events.joinToString("\n"), "7003 SENSOR type=pressure ts=7000 v=1013.25 acc=3" in events)
        assertTrue(events.joinToString("\n"), "7004 SENSOR_ACCURACY type=magnetometer accuracy=2" in events)
        assertTrue(events.joinToString("\n"), "7005 GNSS_MEAS_STATUS status=READY" in events)
    }

    @Test
    fun `a minute tick writes the battery line`() {
        val (session, file) = newSession()
        session.start()

        platform.nanos = 60_000_000_000L
        platform.tick!!.invoke()
        session.stop()

        assertTrue(
            file.readLines().joinToString("\n"),
            "60000000000 BATTERY wall=1700000060000 level=80 charging=false plugged=none free=10737418240" in file.readLines(),
        )
    }

    @Test
    fun `what the phone does not offer is written as unsupported, one line each, never omitted`() {
        platform.absentSensors += WalkLogSensorKind.PRESSURE
        platform.absentSensors += WalkLogSensorKind.GAME_ROTATION_VECTOR
        platform.statusAccepted = false
        platform.measurementsAccepted = false
        platform.capabilities = null
        val (session, file) = newSession()

        session.start()
        session.stop()

        val lines = file.readLines()
        val unsupported = lines.filter { " UNSUPPORTED " in it }.map { it.substringAfter(" UNSUPPORTED ") }
        assertEquals(
            lines.joinToString("\n"),
            setOf(
                "what=sensor:pressure reason=absent-or-refused",
                "what=sensor:game_rotation_vector reason=absent-or-refused",
                "what=gnss-status reason=register-refused",
                "what=gnss-measurements reason=register-refused",
            ),
            unsupported.toSet(),
        )
        assertEquals("one line each", 4, unsupported.size)
        assertTrue("and the capabilities say so in the header", "# gnss-capabilities unsupported reason=api-below-30" in lines)
    }

    @Test
    fun `without activity recognition the step sensors are not asked for, and are written as unsupported for that reason`() {
        platform.activityRecognitionGranted = false
        val (session, file) = newSession()

        session.start()
        session.stop()

        assertFalse(WalkLogSensorKind.STEP_DETECTOR in platform.sensorsRegistered)
        assertFalse(WalkLogSensorKind.STEP_COUNTER in platform.sensorsRegistered)
        val unsupported = file.readLines().filter { " UNSUPPORTED " in it }.map { it.substringAfter(" UNSUPPORTED ") }
        assertEquals(
            setOf(
                "what=sensor:step_detector reason=permission-not-granted",
                "what=sensor:step_counter reason=permission-not-granted",
            ),
            unsupported.toSet(),
        )
    }

    @Test
    fun `a phone that reports raw measurements not supported is told once, however often it says so`() {
        val (session, file) = newSession()
        session.start()

        session.onGnssMeasurementsStatus(0)
        session.onGnssMeasurementsStatus(0)
        session.onGnssMeasurementsStatus(0)
        session.stop()

        val unsupported = file.readLines().filter { " UNSUPPORTED what=gnss-measurements " in it }
        assertEquals(file.readLines().joinToString("\n"), 1, unsupported.size)
        assertTrue(unsupported.single().endsWith("reason=platform-status-not-supported"))
    }

    @Test
    fun `stop ends the file with an end line, lets go of every source, and later events are not written`() {
        val (session, file) = newSession()
        session.start()

        platform.nanos = 9_000L
        session.stop()
        session.onSensor(SensorRecord(WalkLogSensorKind.ACCELEROMETER, 9_001L, floatArrayOf(1f, 2f, 3f), 3))

        assertFalse(session.isActive)
        assertTrue("every registration undone", platform.unregistered)
        assertEquals("9000 END reason=recording-stopped", file.readLines().last())
    }

    @Test
    fun `storage falling below 200 MB at a minute tick stops the log, lets go of every source, and says so`() {
        val (session, file) = newSession()
        session.start()

        platform.free = 1_000L
        platform.nanos = 60_000_000_000L
        platform.tick!!.invoke()

        assertFalse("the log has stopped", session.isActive)
        assertTrue("every registration undone, so nothing keeps the phone busy for a log that is not written", platform.unregistered)
        assertEquals("60000000000 STOPPED reason=storage-low free=1000 min=209715200", file.readLines().last())
        session.stop()
        assertEquals("a stop after a storage stop adds nothing", "60000000000 STOPPED reason=storage-low free=1000 min=209715200", file.readLines().last())
    }

    @Test
    fun `below 200 MB at the start, no source is asked for and the file says why`() {
        platform.free = 1_000L
        val (session, file) = newSession()

        session.start()

        assertFalse(session.isActive)
        assertFalse(platform.fixesRegistered)
        assertTrue(platform.sensorsRegistered.isEmpty())
        assertTrue(file.readLines().single().endsWith(" STOPPED reason=storage-low free=1000 min=209715200"))
    }

    /** A phone that offers everything unless told otherwise. */
    private class FakePlatform : WalkLogPlatform {
        var nanos = 1_000L
        var free = 10L * 1024 * 1024 * 1024
        val absentSensors = mutableSetOf<WalkLogSensorKind>()
        var statusAccepted = true
        var measurementsAccepted = true
        var activityRecognitionGranted = true
        var capabilities: GnssCapabilitiesRecord? = GnssCapabilitiesRecord(linkedMapOf("hasMeasurements" to true))

        var fixesRegistered = false
        var statusRegistered = false
        var measurementsRegistered = false
        val sensorsRegistered = mutableSetOf<WalkLogSensorKind>()
        var tick: (() -> Unit)? = null
        var unregistered = false

        override fun elapsedRealtimeNanos() = nanos
        override fun wallClockMillis() = 1_700_000_000_000L + nanos / 1_000_000L
        override fun headerFacts() = HeaderFacts(
            manufacturer = "fake", model = "SM-FAKE", device = "fake", buildId = "FAKE.1", fingerprint = "fake/fp", sdkInt = 36,
            appVersionName = "1.0.0", appVersionCode = 1L, simState = "ABSENT", dataNetworkType = null,
            dataNetworkTypeUnsupportedReason = "permission-not-granted", activeTransports = emptyList(),
            gnssHardwareModelName = null, gnssYearOfHardware = null, sensors = emptyList(),
        )
        override fun gnssCapabilities() = capabilities
        override fun hasPermission(permission: String) =
            permission != android.Manifest.permission.ACTIVITY_RECOGNITION || activityRecognitionGranted
        override fun freeBytes() = free
        override fun battery() = BatteryRecord(80, false, "none")
        /** Called as the session asks for fixes: the first thing it does once it is active. */
        var onRegisterFixes: (() -> Unit)? = null
        override fun registerFixes(sink: WalkLogEvents): Boolean { onRegisterFixes?.invoke(); fixesRegistered = true; return true }
        override fun registerGnssStatus(sink: WalkLogEvents): Boolean { statusRegistered = statusAccepted; return statusAccepted }
        override fun registerGnssMeasurements(sink: WalkLogEvents): Boolean { measurementsRegistered = measurementsAccepted; return measurementsAccepted }
        override fun registerSensor(kind: WalkLogSensorKind, sink: WalkLogEvents): Boolean {
            if (kind in absentSensors) return false
            sensorsRegistered += kind
            return true
        }
        override fun everyMinute(tick: () -> Unit) { this.tick = tick }
        override fun unregisterAll() { unregistered = true }
    }
}
