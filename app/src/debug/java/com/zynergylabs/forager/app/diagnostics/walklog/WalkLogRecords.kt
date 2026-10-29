package com.zynergylabs.forager.app.diagnostics.walklog

/**
 * What the walk logger writes, as plain values (dispatch 2026-09-28-532). The Android callbacks
 * (`Location`, `GnssStatus`, `GnssMeasurementsEvent`, `SensorEvent`) are copied into these by
 * [AndroidWalkLogPlatform] on arrival, so that everything from here to the file is plain Kotlin
 * and is tested on the JVM with made-up values. A value the platform does not report travels as
 * `null` and is written as `none`, never as a zero that would read as a measurement.
 */

/** One location fix as the platform delivered it. [listener] is which of the logger's own listeners received it. */
data class FixRecord(
    val listener: String,
    val provider: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val speedAccuracyMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val altitudeMeters: Double?,
    val timeEpochMillis: Long,
    val elapsedRealtimeNanos: Long?,
)

/** One satellite in one `GnssStatus` callback. */
data class SatelliteRecord(
    val constellation: String,
    val svid: Int,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val carrierFrequencyHz: Float?,
)

/** The receiver clock that accompanies one `GnssMeasurementsEvent`. */
data class GnssClockRecord(
    val timeNanos: Long,
    val fullBiasNanos: Long?,
    val biasNanos: Double?,
    val biasUncertaintyNanos: Double?,
    val driftNanosPerSecond: Double?,
    val hardwareClockDiscontinuityCount: Int,
    val elapsedRealtimeNanos: Long?,
)

/** One satellite's raw measurement in one `GnssMeasurementsEvent`. */
data class GnssMeasurementRecord(
    val constellation: String,
    val svid: Int,
    val cn0DbHz: Double,
    val state: Int,
    val receivedSvTimeNanos: Long,
    val receivedSvTimeUncertaintyNanos: Long,
    val timeOffsetNanos: Double,
    val pseudorangeRateMetersPerSecond: Double,
    val pseudorangeRateUncertaintyMetersPerSecond: Double,
    val accumulatedDeltaRangeState: Int,
    val accumulatedDeltaRangeMeters: Double,
    val accumulatedDeltaRangeUncertaintyMeters: Double,
    val carrierFrequencyHz: Float?,
    val multipathIndicator: Int,
    val codeType: String?,
)

/** One motion, step or pressure sensor reading. [kind] is one of [WalkLogSensorKind]'s names. */
data class SensorRecord(
    val kind: WalkLogSensorKind,
    val eventTimestampNanos: Long,
    val values: FloatArray,
    val accuracy: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is SensorRecord && kind == other.kind && eventTimestampNanos == other.eventTimestampNanos &&
            values.contentEquals(other.values) && accuracy == other.accuracy

    override fun hashCode(): Int = ((kind.hashCode() * 31 + eventTimestampNanos.hashCode()) * 31 + values.contentHashCode()) * 31 + accuracy
}

/** One battery reading; [levelPercent] null when the platform gives no level. */
data class BatteryRecord(
    val levelPercent: Int?,
    val charging: Boolean?,
    val plugged: String,
)

/** A sensor as the platform describes it, for the header: what it reports is possible, not what is safe (CLAUDE.md). */
data class SensorInfo(
    val type: Int,
    val stringType: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val maximumRange: Float,
    val resolution: Float,
    val powerMilliamps: Float,
    val minDelayMicros: Int,
    val maxDelayMicros: Int,
    val isWakeUp: Boolean,
)

/** What the header says about the phone, the app and its network at the start. */
data class HeaderFacts(
    val manufacturer: String,
    val model: String,
    val device: String,
    val buildId: String,
    val fingerprint: String,
    val sdkInt: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    /** `TelephonyManager.getSimState()` as a name, e.g. `READY`, `ABSENT`. */
    val simState: String,
    /** The data network type, e.g. `LTE`, `NR`; null when it could not be read (see [dataNetworkTypeUnsupportedReason]). */
    val dataNetworkType: String?,
    val dataNetworkTypeUnsupportedReason: String?,
    /** The active network's transports, e.g. `wifi`, `cellular`; empty when there is no active network; null when it could not be read. */
    val activeTransports: List<String>?,
    val gnssHardwareModelName: String?,
    val gnssYearOfHardware: Int?,
    val sensors: List<SensorInfo>,
)

/**
 * `GnssCapabilities` by name, in insertion order. A flag the running API level does not have is
 * present with the value `unsupported-below-api-N`, never left out. Values are booleans, the
 * supported/unsupported/unknown names of an int capability, or a list (the signal types) written
 * without spaces.
 */
data class GnssCapabilitiesRecord(val flags: Map<String, Any>)

/** The sensors the logger asks for, with the rates the owner approved (RECORD -559, choice 4). [rateHz] null means "as delivered". */
enum class WalkLogSensorKind(val androidType: Int, val label: String, val rateHz: Int?, val needsActivityRecognition: Boolean = false) {
    ACCELEROMETER(1, "accelerometer", 25),
    MAGNETOMETER(2, "magnetometer", 25),
    GYROSCOPE(4, "gyroscope", 25),
    PRESSURE(6, "pressure", null),
    ROTATION_VECTOR(11, "rotation_vector", 10),
    GAME_ROTATION_VECTOR(15, "game_rotation_vector", 10),
    STEP_DETECTOR(18, "step_detector", null, needsActivityRecognition = true),
    STEP_COUNTER(19, "step_counter", null, needsActivityRecognition = true),
}
