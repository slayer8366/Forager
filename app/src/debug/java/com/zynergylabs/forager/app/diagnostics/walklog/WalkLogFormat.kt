package com.zynergylabs.forager.app.diagnostics.walklog

import java.time.Instant
import kotlin.math.roundToLong

/**
 * The walk log's text format (dispatch 2026-09-28-532, verify item 4): plain text, one line per
 * event, readable on the laptop without the app.
 *
 * - Header lines start with `# ` and come first: the phone, the app, the network at the start, every
 *   sensor as the phone describes it, the GNSS hardware and capabilities, and the rates asked for.
 * - Every other line is `<since-boot nanoseconds> <TYPE> key=value ...`. The since-boot clock
 *   (`SystemClock.elapsedRealtimeNanos`) keys every line because it cannot be set by hand: with the
 *   phone's clock 4 h ahead (RECORD -540) a wall-clock key would misplace the whole walk. The phone's
 *   clock is in the header and on each battery line, so a wrong clock shows in the file.
 * - A value the platform did not report is `none`. Something the phone does not offer at all is an
 *   `UNSUPPORTED` line, never a missing one (CLAUDE.md: an unsupported capability says so).
 * - A satellite status and a raw-measurement event are several satellites each: one count or clock
 *   line, then one line per satellite, all with the same key.
 *
 * Numbers are written with Kotlin's own `toString`, which does not follow the phone's locale, so a
 * decimal point is always a point. Carrier frequencies are whole hertz.
 */
object WalkLogFormat {

    const val VERSION = 1

    fun fix(atNanos: Long, fix: FixRecord): String = line(
        atNanos, "FIX",
        "listener" to fix.listener,
        "provider" to fix.provider,
        "lat" to fix.latitude,
        "lon" to fix.longitude,
        "acc" to fix.accuracyMeters,
        "vacc" to fix.verticalAccuracyMeters,
        "speed" to fix.speedMetersPerSecond,
        "speedAcc" to fix.speedAccuracyMetersPerSecond,
        "bearing" to fix.bearingDegrees,
        "bearingAcc" to fix.bearingAccuracyDegrees,
        "alt" to fix.altitudeMeters,
        "time" to fix.timeEpochMillis,
        "ern" to fix.elapsedRealtimeNanos,
    )

    fun gnssStatus(atNanos: Long, satellites: List<SatelliteRecord>): List<String> =
        listOf(line(atNanos, "GNSS_STATUS", "n" to satellites.size)) + satellites.map { sat ->
            line(
                atNanos, "GNSS_SAT",
                "c" to sat.constellation,
                "svid" to sat.svid,
                "cn0" to sat.cn0DbHz,
                "el" to sat.elevationDegrees,
                "az" to sat.azimuthDegrees,
                "used" to if (sat.usedInFix) 1 else 0,
                "cf" to sat.carrierFrequencyHz?.let(::wholeHertz),
            )
        }

    fun gnssMeasurements(atNanos: Long, clock: GnssClockRecord, measurements: List<GnssMeasurementRecord>): List<String> =
        listOf(
            line(
                atNanos, "GNSS_CLOCK",
                "timeNanos" to clock.timeNanos,
                "fullBias" to clock.fullBiasNanos,
                "bias" to clock.biasNanos,
                "biasUnc" to clock.biasUncertaintyNanos,
                "drift" to clock.driftNanosPerSecond,
                "hwDisc" to clock.hardwareClockDiscontinuityCount,
                "ern" to clock.elapsedRealtimeNanos,
                "n" to measurements.size,
            ),
        ) + measurements.map { m ->
            line(
                atNanos, "GNSS_MEAS",
                "c" to m.constellation,
                "svid" to m.svid,
                "cn0" to m.cn0DbHz,
                "state" to m.state,
                "rxSvTime" to m.receivedSvTimeNanos,
                "rxSvTimeUnc" to m.receivedSvTimeUncertaintyNanos,
                "toff" to m.timeOffsetNanos,
                "prr" to m.pseudorangeRateMetersPerSecond,
                "prrUnc" to m.pseudorangeRateUncertaintyMetersPerSecond,
                "adrState" to m.accumulatedDeltaRangeState,
                "adr" to m.accumulatedDeltaRangeMeters,
                "adrUnc" to m.accumulatedDeltaRangeUncertaintyMeters,
                "cf" to m.carrierFrequencyHz?.let(::wholeHertz),
                "mp" to m.multipathIndicator,
                "code" to m.codeType,
            )
        }

    /** `GnssMeasurementsEvent.Callback`'s status values, by name; an unknown one keeps its number. */
    fun gnssMeasurementsStatus(atNanos: Long, status: Int): String =
        line(atNanos, "GNSS_MEAS_STATUS", "status" to measurementsStatusName(status))

    fun measurementsStatusName(status: Int): String = when (status) {
        0 -> "NOT_SUPPORTED"
        1 -> "READY"
        2 -> "LOCATION_DISABLED"
        3 -> "NOT_ALLOWED"
        else -> "UNKNOWN($status)"
    }

    fun sensor(atNanos: Long, reading: SensorRecord): String = line(
        atNanos, "SENSOR",
        "type" to reading.kind.label,
        "ts" to reading.eventTimestampNanos,
        "v" to reading.values.joinToString(","),
        "acc" to reading.accuracy,
    )

    fun sensorAccuracy(atNanos: Long, kind: WalkLogSensorKind, accuracy: Int): String =
        line(atNanos, "SENSOR_ACCURACY", "type" to kind.label, "accuracy" to accuracy)

    fun battery(atNanos: Long, wallClockMillis: Long, battery: BatteryRecord?, freeBytes: Long): String = line(
        atNanos, "BATTERY",
        "wall" to wallClockMillis,
        "level" to battery?.levelPercent,
        "charging" to battery?.charging,
        "plugged" to (battery?.plugged ?: NONE),
        "free" to freeBytes,
    )

    fun unsupported(atNanos: Long, what: String, reason: String): String =
        line(atNanos, "UNSUPPORTED", "what" to what, "reason" to reason)

    /** [detail] is written as given, after the reason: `key=value` pairs the caller formats. */
    fun stopped(atNanos: Long, reason: String, detail: String): String = "$atNanos STOPPED reason=$reason $detail"

    fun end(atNanos: Long, reason: String): String = line(atNanos, "END", "reason" to reason)

    fun header(startWallClockMillis: Long, startNanos: Long, facts: HeaderFacts, capabilities: GnssCapabilitiesRecord?): List<String> {
        val lines = mutableListOf<String>()
        lines += "# walklog version=$VERSION"
        // Worded so the legend never reads as an event line: " UNSUPPORTED " with spaces is what marks one.
        lines += "# format: <since-boot ns> <TYPE> key=value ...; none: the phone did not report that value; " +
            "UNSUPPORTED: the phone does not offer it"
        lines += "# start wall=${Instant.ofEpochMilli(startWallClockMillis)} wallMillis=$startWallClockMillis elapsedNanos=$startNanos"
        lines += "# device manufacturer=${facts.manufacturer} model=${facts.model} device=${facts.device} " +
            "build=${facts.buildId} fingerprint=${facts.fingerprint} sdk=${facts.sdkInt}"
        lines += "# app versionName=${facts.appVersionName} versionCode=${facts.appVersionCode}"
        val dataNetworkType = facts.dataNetworkType ?: "unsupported:${facts.dataNetworkTypeUnsupportedReason ?: "unknown"}"
        val transports = when {
            facts.activeTransports == null -> "unsupported"
            facts.activeTransports.isEmpty() -> NONE
            else -> facts.activeTransports.joinToString(",")
        }
        lines += "# network sim=${facts.simState} dataNetworkType=$dataNetworkType transports=$transports"
        lines += "# gnss hardware=${facts.gnssHardwareModelName?.let(::quoted) ?: NONE} year=${facts.gnssYearOfHardware ?: NONE}"
        lines += if (capabilities == null) {
            "# gnss-capabilities unsupported reason=api-below-30"
        } else {
            "# gnss-capabilities " + capabilities.flags.entries.joinToString(" ") { (name, value) -> "$name=$value" }
        }
        if (facts.sensors.isEmpty()) {
            lines += "# sensors none"
        } else {
            facts.sensors.forEach { s ->
                lines += "# sensor type=${s.type} stringType=${s.stringType} name=${quoted(s.name)} vendor=${quoted(s.vendor)} " +
                    "version=${s.version} range=${s.maximumRange} resolution=${s.resolution} powerMa=${s.powerMilliamps} " +
                    "minDelayUs=${s.minDelayMicros} maxDelayUs=${s.maxDelayMicros} wakeUp=${s.isWakeUp}"
            }
        }
        lines += "# rates " + WalkLogSensorKind.entries.joinToString(" ") { kind ->
            "${kind.label}=${kind.rateHz?.let { "${it}Hz" } ?: "as-delivered"}"
        }
        return lines
    }

    private const val NONE = "none"

    private fun line(atNanos: Long, type: String, vararg fields: Pair<String, Any?>): String = buildString {
        append(atNanos).append(' ').append(type)
        fields.forEach { (key, value) -> append(' ').append(key).append('=').append(value ?: NONE) }
    }

    private fun wholeHertz(hz: Float): Long = hz.toDouble().roundToLong()

    /** Sensor and GNSS names carry spaces; quoted so the line still splits on spaces outside them. */
    private fun quoted(text: String): String = "\"" + text.replace("\"", "'") + "\""
}
