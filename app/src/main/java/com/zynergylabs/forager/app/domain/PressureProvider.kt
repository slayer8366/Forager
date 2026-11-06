package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * Owned abstraction over the phone's barometer (RECORD -864, the owner: "Use the barometer (Recommended)"; CLAUDE.md:
 * external integrations behind an interface this project owns). Domain code and the recording service depend on this,
 * never on `android.hardware.SensorManager`, so the height rule is testable headless and a test can hand the service a
 * phone with no barometer.
 */
interface PressureProvider {
    /**
     * One [PressureReading] per sensor sample while collected, about once a second. Emits `null` once and completes
     * when the phone has no barometer: an explicit "unsupported", as [CompassProvider.heading] does for a missing
     * compass, never a stream that silently never emits.
     */
    val readings: Flow<PressureReading?>
}

/**
 * A pressure in hPa and when the sensor took it, on the elapsed-realtime clock in nanoseconds (the sensor's own
 * clock, which keeps counting while the phone sleeps), never wall time.
 */
data class PressureReading(val hPa: Float, val elapsedRealtimeNanos: Long)

/**
 * The pressure to store on a point made at [nowElapsedRealtimeNanos]: the latest reading's, if it is at most
 * [PRESSURE_MAX_AGE_NANOS] old; otherwise `null`, not a stale value carried onto a point it does not describe.
 */
fun pressureForPoint(latest: PressureReading?, nowElapsedRealtimeNanos: Long): Float? {
    val reading = latest ?: return null
    val age = nowElapsedRealtimeNanos - reading.elapsedRealtimeNanos
    return reading.hPa.takeIf { age in 0..PRESSURE_MAX_AGE_NANOS }
}

/** A reading older than 2 s is not put on a point. The sensor is asked for one a second. */
const val PRESSURE_MAX_AGE_NANOS: Long = 2_000_000_000L

/** The rate the barometer is asked for: one sample a second (a starting value, RECORD -867). */
const val PRESSURE_SAMPLING_PERIOD_MICROS: Int = 1_000_000

/** How long the sensor may batch samples before handing them over, so the processor is not woken for each. */
const val PRESSURE_MAX_REPORT_LATENCY_MICROS: Int = 5_000_000
