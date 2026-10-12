package com.zynergylabs.forager.app.diagnostics.walklog

import android.Manifest

/**
 * One recording's walk log, from [start] to [stop] (dispatch 2026-09-28-532): writes the header,
 * asks the phone for every source, writes each event the phone delivers as its line, and once a
 * minute writes the battery and checks the free space.
 *
 * **Unsupported is written, never omitted.** A sensor the phone lacks or refuses, a satellite or
 * raw-measurement callback it refuses, step sensors without `ACTIVITY_RECOGNITION`, and raw
 * measurements the phone later reports as not supported each get one `UNSUPPORTED` line. The last
 * one is said once however often the phone repeats it.
 *
 * **Stopping early.** When the writer stops (storage below the floor, or a write error), the session
 * lets go of every source at once, so nothing keeps the phone busy for a log that is not being
 * written, and calls [onStoppedEarly] (the logger lets go of its wake lock there). The recording
 * never hears of it.
 *
 * Every method is called on one thread: the logger's own on a phone, the test's in tests.
 */
class WalkLogSession(
    private val platform: WalkLogPlatform,
    private val writer: WalkLogWriter,
    private val onStoppedEarly: () -> Unit = {},
) : WalkLogEvents {

    /**
     * Read from other threads (the logger's [isActive] callers). Set false only after the file is
     * closed, so "not active" means the end line and every buffered line are on disk.
     */
    @Volatile private var active = false
    private var stopping = false
    private var measurementsUnsupportedSaid = false

    val isActive: Boolean get() = active

    fun start() {
        if (active) return
        if (!writer.open()) return
        val now = platform.elapsedRealtimeNanos()
        // The header is on disk before the session is active (CI run 38115289698): the logger reports itself logging
        // from [isActive], and a reader at that moment found the file empty, the header still in the writer's buffer.
        // A header that cannot be written leaves the session inactive; the writer has written its STOPPED line.
        if (!writer.writeAndFlush(WalkLogFormat.header(platform.wallClockMillis(), now, platform.headerFacts(), platform.gnssCapabilities()))) return
        active = true

        if (!platform.registerFixes(this)) unsupported("fixes", "register-refused")
        if (!platform.registerGnssStatus(this)) unsupported("gnss-status", "register-refused")
        if (!platform.registerGnssMeasurements(this)) {
            unsupported("gnss-measurements", "register-refused")
            measurementsUnsupportedSaid = true
        }
        val activityRecognition = platform.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
        WalkLogSensorKind.entries.forEach { kind ->
            when {
                kind.needsActivityRecognition && !activityRecognition -> unsupported("sensor:${kind.label}", "permission-not-granted")
                !platform.registerSensor(kind, this) -> unsupported("sensor:${kind.label}", "absent-or-refused")
            }
        }
        // A battery line at the start too, so the first minute's drop is measured from the start.
        onMinute()
        if (!active) return
        platform.everyMinute(::onMinute)
    }

    fun stop(reason: String = REASON_RECORDING_STOPPED) {
        if (!active || stopping) return
        stopping = true
        try {
            platform.unregisterAll()
            writer.close(WalkLogFormat.end(platform.elapsedRealtimeNanos(), reason))
        } finally {
            active = false
        }
    }

    override fun onFix(fix: FixRecord) {
        if (active) write(listOf(WalkLogFormat.fix(platform.elapsedRealtimeNanos(), fix)))
    }

    override fun onGnssStatus(satellites: List<SatelliteRecord>) {
        if (active) write(WalkLogFormat.gnssStatus(platform.elapsedRealtimeNanos(), satellites))
    }

    override fun onGnssMeasurements(clock: GnssClockRecord, measurements: List<GnssMeasurementRecord>) {
        if (active) write(WalkLogFormat.gnssMeasurements(platform.elapsedRealtimeNanos(), clock, measurements))
    }

    override fun onGnssMeasurementsStatus(status: Int) {
        if (!active) return
        val now = platform.elapsedRealtimeNanos()
        if (!write(listOf(WalkLogFormat.gnssMeasurementsStatus(now, status)))) return
        if (status == STATUS_NOT_SUPPORTED && !measurementsUnsupportedSaid) {
            measurementsUnsupportedSaid = true
            unsupported("gnss-measurements", "platform-status-not-supported")
        }
    }

    override fun onSensor(reading: SensorRecord) {
        if (active) write(listOf(WalkLogFormat.sensor(platform.elapsedRealtimeNanos(), reading)))
    }

    override fun onSensorAccuracy(kind: WalkLogSensorKind, accuracy: Int) {
        if (active) write(listOf(WalkLogFormat.sensorAccuracy(platform.elapsedRealtimeNanos(), kind, accuracy)))
    }

    private fun onMinute() {
        if (!active) return
        val now = platform.elapsedRealtimeNanos()
        val free = platform.freeBytes()
        if (!write(listOf(WalkLogFormat.battery(now, platform.wallClockMillis(), platform.battery(), free)))) return
        if (!writer.checkSpace()) stoppedEarly()
    }

    private fun unsupported(what: String, reason: String) {
        write(listOf(WalkLogFormat.unsupported(platform.elapsedRealtimeNanos(), what, reason)))
    }

    /** False, and the session stopped, when the writer has stopped. */
    private fun write(lines: List<String>): Boolean {
        if (!active) return false
        if (writer.write(lines)) return true
        stoppedEarly()
        return false
    }

    private fun stoppedEarly() {
        if (!active) return
        active = false
        platform.unregisterAll()
        onStoppedEarly()
    }

    companion object {
        const val REASON_RECORDING_STOPPED = "recording-stopped"

        /** `GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED`. */
        private const val STATUS_NOT_SUPPORTED = 0
    }
}
