package com.forager.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * Owned abstraction over the device compass. Domain and UI code depend on this interface, never on
 * android.hardware.SensorManager directly, so the compass+elevation strip can be exercised without
 * a real sensor (CLAUDE.md: isolate hardware/integration layers behind a driver interface this
 * project owns).
 */
interface CompassProvider {
    /**
     * One [CompassReading] per sensor update while collected — the magnetic heading **and** the
     * sensor's own statement of how far to trust it, in a single value (compass-reliability
     * dispatch, owner decision). Not two flows: two flows can be read at different moments and
     * pair a heading with the wrong uncertainty, which is the defect this exists to remove,
     * reintroduced — the same shape as `liveFix` replacing separate position and accuracy fields.
     *
     * `null` when this device has no usable rotation sensor (neither a rotation-vector sensor nor
     * an accelerometer+magnetometer pair) — an explicit "unsupported" per CLAUDE.md, never a
     * fabricated or stale last-known value.
     */
    val heading: Flow<CompassReading?>
}

/**
 * A heading and how far the sensor says to trust it, emitted together.
 *
 * [timestampMillis] is the sensor's own monotonic clock (elapsed realtime), not wall time — it is
 * what [CompassTrustJudge] measures its clear-hold against, so it must be monotonic and it must be
 * the reading's own, never "now" at the consumer.
 */
data class CompassReading(
    /** Degrees clockwise from magnetic north, `[0, 360)`. */
    val magneticHeadingDegrees: Float,
    val uncertainty: HeadingUncertainty,
    val timestampMillis: Long,
)

/**
 * The platform gives two signals about how trustworthy a heading is, and this app reads both
 * (compass-reliability dispatch). Which one a reading carries is recorded on it, so a test — or a
 * field log — can see which path decided.
 */
sealed interface HeadingUncertainty {
    /**
     * The rotation-vector sensor's own estimated heading accuracy (`values[4]`, radians on the
     * platform, converted to degrees here). Preferred: a number comparable to what the needle
     * claims, not a bucket. Only ever emitted for a value the provider judged supplied and
     * plausible — see `AndroidCompassProvider` for what is treated as "not supplied".
     */
    data class Estimated(val degrees: Float) : HeadingUncertainty

    /**
     * The sensor's accuracy status level — the fallback when [Estimated] is not available. On the
     * rotation-vector path this is that sensor's own status (the only one registered there); on
     * the accelerometer+magnetometer fallback path it is the magnetometer's, never the
     * accelerometer's (owner decision: the magnetometer is the sensor being distorted).
     */
    data class Status(val level: CompassStatus) : HeadingUncertainty
}

/** `SensorManager.SENSOR_STATUS_*`, as an owned type so the domain never names the platform constants. */
enum class CompassStatus {
    /** `SENSOR_STATUS_UNRELIABLE` (0): values cannot be trusted. */
    UNRELIABLE,

    /** `SENSOR_STATUS_ACCURACY_LOW` (1): calibration with the environment is needed. */
    LOW,

    /** `SENSOR_STATUS_ACCURACY_MEDIUM` (2): average; may improve. */
    MEDIUM,

    /** `SENSOR_STATUS_ACCURACY_HIGH` (3): maximum accuracy. */
    HIGH,
}
