package com.zynergylabs.forager.app.diagnostics.walklog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walk logger's line format (dispatch 2026-09-28-532, Evidence: "each event type written as one
 * line with its fields, given fake platform events"). Every expected line is written out in full,
 * so the format a reader on the laptop depends on is pinned here, not inferred from the code.
 * Coordinates are made up (the dispatch: no positions in the repository).
 */
class WalkLogFormatTest {

    @Test
    fun `a fix is one line with every field, and a value the platform did not report is none`() {
        val fix = FixRecord(
            listener = "passive",
            provider = "gps",
            latitude = 10.5,
            longitude = -20.25,
            accuracyMeters = 3.79f,
            verticalAccuracyMeters = null,
            speedMetersPerSecond = 1.25f,
            speedAccuracyMetersPerSecond = 0.5f,
            bearingDegrees = 90.0f,
            bearingAccuracyDegrees = null,
            altitudeMeters = 100.5,
            timeEpochMillis = 1_700_000_000_000L,
            elapsedRealtimeNanos = 999L,
        )

        assertEquals(
            "1000 FIX listener=passive provider=gps lat=10.5 lon=-20.25 acc=3.79 vacc=none speed=1.25 speedAcc=0.5 " +
                "bearing=90.0 bearingAcc=none alt=100.5 time=1700000000000 ern=999",
            WalkLogFormat.fix(1000L, fix),
        )
    }

    @Test
    fun `a fix with no provider says so rather than guessing one`() {
        val fix = FixRecord("network", null, 1.0, 2.0, null, null, null, null, null, null, null, 5L, null)

        assertEquals(
            "7 FIX listener=network provider=none lat=1.0 lon=2.0 acc=none vacc=none speed=none speedAcc=none " +
                "bearing=none bearingAcc=none alt=none time=5 ern=none",
            WalkLogFormat.fix(7L, fix),
        )
    }

    @Test
    fun `a satellite status is a count line then one line per satellite, carrier frequency in whole hertz`() {
        val lines = WalkLogFormat.gnssStatus(
            2000L,
            listOf(
                SatelliteRecord("GPS", 12, 33.5f, 45.0f, 120.0f, usedInFix = true, carrierFrequencyHz = 1_575_420_032f),
                SatelliteRecord("GALILEO", 3, 20.25f, 10.0f, 300.5f, usedInFix = false, carrierFrequencyHz = null),
            ),
        )

        assertEquals(
            listOf(
                "2000 GNSS_STATUS n=2",
                "2000 GNSS_SAT c=GPS svid=12 cn0=33.5 el=45.0 az=120.0 used=1 cf=1575420032",
                "2000 GNSS_SAT c=GALILEO svid=3 cn0=20.25 el=10.0 az=300.5 used=0 cf=none",
            ),
            lines,
        )
    }

    @Test
    fun `an empty satellite status is still written, as zero satellites`() {
        assertEquals(listOf("5 GNSS_STATUS n=0"), WalkLogFormat.gnssStatus(5L, emptyList()))
    }

    @Test
    fun `raw measurements are a clock line then one line per measurement, with the carrier phase state`() {
        val clock = GnssClockRecord(
            timeNanos = 123_456L,
            fullBiasNanos = -1_000_000_000L,
            biasNanos = 0.5,
            biasUncertaintyNanos = null,
            driftNanosPerSecond = 1.5,
            hardwareClockDiscontinuityCount = 2,
            elapsedRealtimeNanos = 3000L,
        )
        val measurement = GnssMeasurementRecord(
            constellation = "GPS",
            svid = 12,
            cn0DbHz = 33.5,
            state = 16_431,
            receivedSvTimeNanos = 99_000L,
            receivedSvTimeUncertaintyNanos = 10L,
            timeOffsetNanos = 0.0,
            pseudorangeRateMetersPerSecond = -150.25,
            pseudorangeRateUncertaintyMetersPerSecond = 0.125,
            accumulatedDeltaRangeState = 0,
            accumulatedDeltaRangeMeters = 0.0,
            accumulatedDeltaRangeUncertaintyMeters = 0.0,
            carrierFrequencyHz = 1_176_450_048f,
            multipathIndicator = 0,
            codeType = "Q",
        )

        assertEquals(
            listOf(
                "3000 GNSS_CLOCK timeNanos=123456 fullBias=-1000000000 bias=0.5 biasUnc=none drift=1.5 hwDisc=2 ern=3000 n=1",
                "3000 GNSS_MEAS c=GPS svid=12 cn0=33.5 state=16431 rxSvTime=99000 rxSvTimeUnc=10 toff=0.0 prr=-150.25 " +
                    "prrUnc=0.125 adrState=0 adr=0.0 adrUnc=0.0 cf=1176450048 mp=0 code=Q",
            ),
            WalkLogFormat.gnssMeasurements(3000L, clock, listOf(measurement)),
        )
    }

    @Test
    fun `the measurements status is written by name`() {
        assertEquals("1 GNSS_MEAS_STATUS status=NOT_SUPPORTED", WalkLogFormat.gnssMeasurementsStatus(1L, 0))
        assertEquals("1 GNSS_MEAS_STATUS status=READY", WalkLogFormat.gnssMeasurementsStatus(1L, 1))
        assertEquals("1 GNSS_MEAS_STATUS status=LOCATION_DISABLED", WalkLogFormat.gnssMeasurementsStatus(1L, 2))
        assertEquals("1 GNSS_MEAS_STATUS status=NOT_ALLOWED", WalkLogFormat.gnssMeasurementsStatus(1L, 3))
        assertEquals("1 GNSS_MEAS_STATUS status=UNKNOWN(9)", WalkLogFormat.gnssMeasurementsStatus(1L, 9))
    }

    @Test
    fun `a sensor reading is one line with the sensor's own timestamp and every value`() {
        assertEquals(
            "4000 SENSOR type=accelerometer ts=3999 v=0.125,9.75,-0.5 acc=3",
            WalkLogFormat.sensor(4000L, SensorRecord(WalkLogSensorKind.ACCELEROMETER, 3999L, floatArrayOf(0.125f, 9.75f, -0.5f), 3)),
        )
        assertEquals(
            "4001 SENSOR type=step_counter ts=4000 v=1234.0 acc=0",
            WalkLogFormat.sensor(4001L, SensorRecord(WalkLogSensorKind.STEP_COUNTER, 4000L, floatArrayOf(1234f), 0)),
        )
    }

    @Test
    fun `a sensor accuracy change is its own line`() {
        assertEquals(
            "5 SENSOR_ACCURACY type=magnetometer accuracy=1",
            WalkLogFormat.sensorAccuracy(5L, WalkLogSensorKind.MAGNETOMETER, 1),
        )
    }

    @Test
    fun `a battery line carries the phone's clock, the level, charging and the free space`() {
        assertEquals(
            "60000 BATTERY wall=1700000060000 level=85 charging=false plugged=none free=5000000000",
            WalkLogFormat.battery(60_000L, 1_700_000_060_000L, BatteryRecord(85, false, "none"), 5_000_000_000L),
        )
        assertEquals(
            "60000 BATTERY wall=1700000060000 level=none charging=none plugged=none free=1",
            WalkLogFormat.battery(60_000L, 1_700_000_060_000L, null, 1L),
        )
    }

    @Test
    fun `unsupported, stopped and end lines`() {
        assertEquals("8 UNSUPPORTED what=sensor:pressure reason=absent", WalkLogFormat.unsupported(8L, "sensor:pressure", "absent"))
        assertEquals(
            "9 STOPPED reason=storage-low free=100 min=209715200",
            WalkLogFormat.stopped(9L, "storage-low", "free=100 min=209715200"),
        )
        assertEquals("10 END reason=recording-stopped", WalkLogFormat.end(10L, "recording-stopped"))
    }

    @Test
    fun `the header names the phone, the app, the network state, every sensor and the GNSS capabilities`() {
        val header = WalkLogFormat.header(
            startWallClockMillis = 1_700_000_000_000L,
            startNanos = 5000L,
            facts = headerFacts(),
            capabilities = GnssCapabilitiesRecord(linkedMapOf("hasMeasurements" to true, "hasAccumulatedDeltaRange" to false)),
        )

        assertEquals("# walklog version=1", header.first())
        val expected = listOf(
            "# start wall=2023-11-14T22:13:20Z wallMillis=1700000000000 elapsedNanos=5000",
            "# device manufacturer=samsung model=SM-X000 device=testdevice build=TEST.1 fingerprint=test/fp sdk=36",
            "# app versionName=1.0.0 versionCode=42",
            "# network sim=READY dataNetworkType=LTE transports=cellular",
            "# gnss hardware=\"Test GNSS\" year=2021",
            "# gnss-capabilities hasMeasurements=true hasAccumulatedDeltaRange=false",
            "# sensor type=1 stringType=android.sensor.accelerometer name=\"Test Accel\" vendor=\"Test Vendor\" version=2 " +
                "range=78.4 resolution=0.0024 powerMa=0.15 minDelayUs=2000 maxDelayUs=200000 wakeUp=false",
            "# rates accelerometer=25Hz magnetometer=25Hz gyroscope=25Hz pressure=as-delivered rotation_vector=10Hz " +
                "game_rotation_vector=10Hz step_detector=as-delivered step_counter=as-delivered",
        )
        expected.forEach { line -> assertTrue("header lacks:\n$line\nheader was:\n${header.joinToString("\n")}", line in header) }
        assertTrue("every header line starts with '# '", header.all { it.startsWith("# ") })
    }

    @Test
    fun `a header with nothing readable says unsupported for each, never leaves it out`() {
        val header = WalkLogFormat.header(
            startWallClockMillis = 0L,
            startNanos = 0L,
            facts = headerFacts().copy(
                dataNetworkType = null,
                dataNetworkTypeUnsupportedReason = "permission-not-granted",
                activeTransports = null,
                gnssHardwareModelName = null,
                gnssYearOfHardware = null,
                sensors = emptyList(),
            ),
            capabilities = null,
        )

        val expected = listOf(
            "# network sim=READY dataNetworkType=unsupported:permission-not-granted transports=unsupported",
            "# gnss hardware=none year=none",
            "# gnss-capabilities unsupported reason=api-below-30",
            "# sensors none",
        )
        expected.forEach { line -> assertTrue("header lacks:\n$line\nheader was:\n${header.joinToString("\n")}", line in header) }
    }

    @Test
    fun `no active network is written as none, distinct from unreadable`() {
        val header = WalkLogFormat.header(0L, 0L, headerFacts().copy(activeTransports = emptyList()), null)

        assertTrue(header.joinToString("\n"), "# network sim=READY dataNetworkType=LTE transports=none" in header)
    }

    private fun headerFacts() = HeaderFacts(
        manufacturer = "samsung",
        model = "SM-X000",
        device = "testdevice",
        buildId = "TEST.1",
        fingerprint = "test/fp",
        sdkInt = 36,
        appVersionName = "1.0.0",
        appVersionCode = 42L,
        simState = "READY",
        dataNetworkType = "LTE",
        dataNetworkTypeUnsupportedReason = null,
        activeTransports = listOf("cellular"),
        gnssHardwareModelName = "Test GNSS",
        gnssYearOfHardware = 2021,
        sensors = listOf(
            SensorInfo(
                type = 1,
                stringType = "android.sensor.accelerometer",
                name = "Test Accel",
                vendor = "Test Vendor",
                version = 2,
                maximumRange = 78.4f,
                resolution = 0.0024f,
                powerMilliamps = 0.15f,
                minDelayMicros = 2000,
                maxDelayMicros = 200_000,
                isWakeUp = false,
            ),
        ),
    )
}
