package com.zynergylabs.forager.app.diagnostics.walklog

/**
 * Everything the walk logger needs from the phone, behind an interface this project owns
 * (CLAUDE.md, Architecture), so [WalkLogSession] is tested on the JVM with a fake that offers or
 * withholds each source. The real one is [AndroidWalkLogPlatform].
 *
 * Every `register*` reports whether the platform accepted it. A refusal is written to the file as
 * unsupported, never dropped.
 */
interface WalkLogPlatform {

    /** The since-boot clock every event line is keyed on: unaffected by the phone's clock being set by hand (RECORD -540). */
    fun elapsedRealtimeNanos(): Long

    /** The phone's own clock, written in the header and with each battery line, so a wrong clock shows in the file. */
    fun wallClockMillis(): Long

    fun headerFacts(): HeaderFacts

    /** Null when this API level has no `GnssCapabilities` (below 30); the header then says so. */
    fun gnssCapabilities(): GnssCapabilitiesRecord?

    fun hasPermission(permission: String): Boolean

    /** Free bytes where the log file is written. */
    fun freeBytes(): Long

    /** Null when the platform gave no battery reading. */
    fun battery(): BatteryRecord?

    fun registerFixes(sink: WalkLogEvents): Boolean

    fun registerGnssStatus(sink: WalkLogEvents): Boolean

    fun registerGnssMeasurements(sink: WalkLogEvents): Boolean

    /** [kind]'s own rate; false when the sensor is absent or the registration was refused. */
    fun registerSensor(kind: WalkLogSensorKind, sink: WalkLogEvents): Boolean

    /** Calls [tick] once a minute, on the same thread the events arrive on, until [unregisterAll]. */
    fun everyMinute(tick: () -> Unit)

    /** Undoes every registration and the minute timer. Safe to call twice. */
    fun unregisterAll()
}

/** Where the platform delivers what it senses. One thread at a time (the logger's own thread on a phone). */
interface WalkLogEvents {
    fun onFix(fix: FixRecord)
    fun onGnssStatus(satellites: List<SatelliteRecord>)
    fun onGnssMeasurements(clock: GnssClockRecord, measurements: List<GnssMeasurementRecord>)

    /** `GnssMeasurementsEvent.Callback.onStatusChanged`'s value. */
    fun onGnssMeasurementsStatus(status: Int)
    fun onSensor(reading: SensorRecord)
    fun onSensorAccuracy(kind: WalkLogSensorKind, accuracy: Int)
}
