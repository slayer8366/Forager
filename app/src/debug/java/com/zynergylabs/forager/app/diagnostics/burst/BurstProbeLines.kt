package com.zynergylabs.forager.app.diagnostics.burst

import java.util.Locale

/**
 * The probe's log lines and its run summary (dispatch 2026-10-15-06). Every line is one JSON object under the tag [TAG],
 * so `adb logcat -v raw -s BurstProbe` prints exactly the probe's lines and nothing else, one object per line. Each line
 * has a `kind` (`start`, `frame`, `run`, `handback`, `end`, `busy`) and the `session` it belongs to, so two triggers in
 * one process can be told apart.
 *
 * A reading that was not made is written as `null`, never as 0: a table built from these lines must be able to tell
 * "the camera reported zero" from "nothing was read" (CLAUDE.md, errors and failure paths).
 */
internal object BurstProbeLines {
    const val TAG = "BurstProbe"

    fun frameLine(session: Int, run: ProbeRun, frame: ProbeFrame): String = JsonLine()
        .string("kind", "frame")
        .int("session", session)
        .string("run", run.id)
        .int("index", frame.index)
        .millis("submitMs", frame.submitMs)
        .millis("startedMs", frame.startedMs)
        .millis("rawArrivalMs", frame.rawArrivalMs)
        .millis("jpegArrivalMs", frame.jpegArrivalMs)
        .long("sensorTimestampNs", frame.sensorTimestampNs)
        .long("exposureTimeNs", frame.exposureTimeNs)
        .int("iso", frame.iso)
        .int("postRawBoost", frame.postRawBoost)
        .string("aeState", frame.aeState?.let { aeStateName(it) })
        .string("awbState", frame.awbState?.let { awbStateName(it) })
        .string("afState", frame.afState?.let { afStateName(it) })
        .float("focusDistanceDiopters", frame.focusDistanceDiopters)
        .floats("blackLevel", frame.blackLevel)
        .int("whiteLevel", frame.whiteLevel)
        .millis("rawCopyMs", frame.rawCopyMs)
        .decimal("sharpness", frame.sharpness, SHARPNESS_DECIMALS)
        .strings("notes", frame.notes)
        .string("error", frame.error)
        .build()

    fun runLine(
        session: Int,
        run: ProbeRun,
        outcome: RunOutcomeKind,
        reason: String?,
        lock: LockOutcome?,
        summary: RunSummary?,
        facts: ProbeRunFacts?,
        before: MemoryReading,
        after: MemoryReading,
    ): String = JsonLine()
        .string("kind", "run")
        .int("session", session)
        .string("run", run.id)
        .string("output", run.output.label)
        .bool("locked", run.locked)
        .string("outcome", outcome.label)
        .string("reason", reason)
        .raw("lock", lock?.let { lockObject(it) })
        .int("framesExpected", summary?.framesExpected)
        .int("framesArrived", summary?.framesArrived)
        .millisList("intervalsMs", summary?.intervalsMs)
        .millis("meanIntervalMs", summary?.meanIntervalMs)
        .millis("minIntervalMs", summary?.minIntervalMs)
        .millis("maxIntervalMs", summary?.maxIntervalMs)
        .millis("spreadMs", summary?.spreadMs)
        .millis("framesPerSecond", summary?.framesPerSecond)
        .millis("firstSubmitToLastArrivalMs", summary?.firstSubmitToLastArrivalMs)
        .int("framesWithResult", summary?.framesWithResult)
        .int("aeLockedFrames", summary?.aeLockedFrames)
        .int("awbLockedFrames", summary?.awbLockedFrames)
        .bool("exposureHeld", summary?.exposureHeld)
        .bool("isoHeld", summary?.isoHeld)
        .bool("focusHeld", summary?.focusHeld)
        .float("focusMinDiopters", summary?.focusMinDiopters)
        .float("focusMaxDiopters", summary?.focusMaxDiopters)
        .bool("lockHeld", summary?.lockHeld)
        .string("rawSize", facts?.rawSize)
        .string("jpegSize", facts?.jpegSize)
        .long("minFrameDurationNs", facts?.minFrameDurationNs)
        .long("stallDurationNs", facts?.stallDurationNs)
        .long("maxMemoryBytes", before.maxMemoryBytes)
        .long("heapUsedBeforeBytes", before.usedBytes)
        .long("heapUsedAfterBytes", after.usedBytes)
        .long("heapUsedPeakBytes", facts?.heapUsedPeakBytes)
        .string("timestampSource", facts?.timestampSource)
        .int("pipelineMaxDepth", facts?.pipelineMaxDepth)
        .strings("notes", facts?.notes.orEmpty())
        .build()

    fun handbackLine(session: Int, run: ProbeRun, release: LockRelease?, handback: Handback): String = JsonLine()
        .string("kind", "handback")
        .int("session", session)
        .string("run", run.id)
        .bool("lockReleased", release?.cleared)
        .string("lockDetail", release?.detail)
        .bool("probeUnbound", handback.probeUnbound)
        .string("detail", handback.detail)
        .build()

    fun startLine(session: Int, runs: List<ProbeRun>, framesPerRun: Int, lockTimeoutMillis: Long, frameTimeoutMillis: Long): String = JsonLine()
        .string("kind", "start")
        .int("session", session)
        .strings("runs", runs.map { it.id })
        .int("framesPerRun", framesPerRun)
        .long("lockTimeoutMs", lockTimeoutMillis)
        .long("frameTimeoutMs", frameTimeoutMillis)
        .build()

    fun endLine(session: Int, completed: Boolean, reason: String?): String = JsonLine()
        .string("kind", "end")
        .int("session", session)
        .bool("completed", completed)
        .string("reason", reason)
        .build()

    /** A trigger that arrived while a probe was already running; it is not queued. */
    fun busyLine(runningSession: Int): String = JsonLine()
        .string("kind", "busy")
        .int("session", runningSession)
        .string("reason", "a probe is already running; this trigger was ignored")
        .build()

    /**
     * The run summary. Frames count as arrived when they carry no error and a sensor timestamp; the rest are left out of
     * every figure. Intervals are between consecutive exposures in sensor-timestamp order, which is the sensor's own
     * spacing whatever order the library delivered the images in. Held means equal on every arrived frame; when an
     * arrived frame has no reading, held is unknown (null). The lock held when every arrived frame's result reads AE and
     * AWB `LOCKED` and the exposure and ISO did not move; an unlocked run has no lock to hold (null).
     */
    fun summarize(run: ProbeRun, framesExpected: Int, frames: List<ProbeFrame>): RunSummary {
        val arrived = frames.filter { it.error == null && it.sensorTimestampNs != null }.sortedBy { it.sensorTimestampNs }
        val intervals = arrived.zipWithNext { a, b -> (b.sensorTimestampNs!! - a.sensorTimestampNs!!) / 1_000_000.0 }
        val mean = intervals.takeIf { it.isNotEmpty() }?.average()
        val withResult = arrived.filter { it.resultMatched }
        val firstSubmit = frames.mapNotNull { it.submitMs }.minOrNull()
        val lastArrival = arrived.flatMap { listOfNotNull(it.rawArrivalMs, it.jpegArrivalMs) }.maxOrNull()
        val exposureHeld = held(arrived) { it.exposureTimeNs }
        val isoHeld = held(arrived) { it.iso }
        val focus = arrived.map { it.focusDistanceDiopters }
        val focusKnown = focus.isNotEmpty() && focus.all { it != null }
        val aeLocked = withResult.count { it.aeState == AE_LOCKED }
        val awbLocked = withResult.count { it.awbState == AWB_LOCKED }
        val lockHeld = when {
            !run.locked -> null
            arrived.isEmpty() || withResult.size < arrived.size -> null
            else -> aeLocked == arrived.size && awbLocked == arrived.size && exposureHeld != false && isoHeld != false
        }
        return RunSummary(
            framesExpected = framesExpected,
            framesArrived = arrived.size,
            intervalsMs = intervals,
            meanIntervalMs = mean,
            minIntervalMs = intervals.minOrNull(),
            maxIntervalMs = intervals.maxOrNull(),
            spreadMs = if (intervals.isEmpty()) null else intervals.max() - intervals.min(),
            framesPerSecond = mean?.takeIf { it > 0.0 }?.let { 1000.0 / it },
            firstSubmitToLastArrivalMs = if (firstSubmit != null && lastArrival != null) lastArrival - firstSubmit else null,
            framesWithResult = withResult.size,
            aeLockedFrames = aeLocked,
            awbLockedFrames = awbLocked,
            exposureHeld = exposureHeld,
            isoHeld = isoHeld,
            focusHeld = if (focusKnown) focus.distinct().size == 1 else null,
            focusMinDiopters = if (focusKnown) focus.minOf { it!! } else null,
            focusMaxDiopters = if (focusKnown) focus.maxOf { it!! } else null,
            lockHeld = lockHeld,
        )
    }

    /** Equal on every frame (true), not (false), or unknown because a frame has no reading or there are none (null). */
    private fun <T> held(frames: List<ProbeFrame>, reading: (ProbeFrame) -> T?): Boolean? {
        val values = frames.map(reading)
        if (values.isEmpty() || values.any { it == null }) return null
        return values.distinct().size == 1
    }

    private fun lockObject(lock: LockOutcome): String = when (lock) {
        is LockOutcome.Reached -> JsonLine()
            .bool("reached", true)
            .millis("waitMs", lock.waitMs)
            .string("aeState", aeStateName(lock.aeState))
            .string("awbState", awbStateName(lock.awbState))
            .string("reason", null)
            .string("lastAeState", null)
            .string("lastAwbState", null)
            .build()
        is LockOutcome.NotReached -> JsonLine()
            .bool("reached", false)
            .millis("waitMs", null)
            .string("aeState", null)
            .string("awbState", null)
            .string("reason", lock.reason)
            .string("lastAeState", lock.lastAeState?.let { aeStateName(it) })
            .string("lastAwbState", lock.lastAwbState?.let { awbStateName(it) })
            .build()
    }

    // camera2's CaptureResult state constants, by value (CONTROL_AE_STATE_*, CONTROL_AWB_STATE_*, CONTROL_AF_STATE_*).
    // Written out here rather than read from android.hardware.camera2 so this file stays testable on the JVM.
    private const val AE_LOCKED = 3
    private const val AWB_LOCKED = 3
    private val AE_STATES = listOf("INACTIVE", "SEARCHING", "CONVERGED", "LOCKED", "FLASH_REQUIRED", "PRECAPTURE")
    private val AWB_STATES = listOf("INACTIVE", "SEARCHING", "CONVERGED", "LOCKED")
    private val AF_STATES = listOf(
        "INACTIVE", "PASSIVE_SCAN", "PASSIVE_FOCUSED", "ACTIVE_SCAN", "FOCUSED_LOCKED", "NOT_FOCUSED_LOCKED", "PASSIVE_UNFOCUSED",
    )

    fun aeStateName(state: Int): String = AE_STATES.getOrNull(state) ?: "UNKNOWN_$state"
    fun awbStateName(state: Int): String = AWB_STATES.getOrNull(state) ?: "UNKNOWN_$state"
    fun afStateName(state: Int): String = AF_STATES.getOrNull(state) ?: "UNKNOWN_$state"

    private const val MILLIS_DECIMALS = 3
    private const val SHARPNESS_DECIMALS = 6

    /** One JSON object, keys in the order added. Only what these lines need: no nesting beyond [raw]. */
    private class JsonLine {
        private val parts = mutableListOf<String>()

        private fun put(key: String, value: String): JsonLine = apply { parts += "${quote(key)}:$value" }

        fun string(key: String, value: String?) = put(key, value?.let { quote(it) } ?: "null")
        fun int(key: String, value: Int?) = put(key, value?.toString() ?: "null")
        fun long(key: String, value: Long?) = put(key, value?.toString() ?: "null")
        fun bool(key: String, value: Boolean?) = put(key, value?.toString() ?: "null")
        fun float(key: String, value: Float?) = put(key, value?.let { floatText(it) } ?: "null")
        fun millis(key: String, value: Double?) = decimal(key, value, MILLIS_DECIMALS)
        fun decimal(key: String, value: Double?, decimals: Int) = put(key, value?.let { decimalText(it, decimals) } ?: "null")
        fun floats(key: String, values: List<Float>?) = put(key, values?.joinToString(",", "[", "]") { floatText(it) } ?: "null")
        fun millisList(key: String, values: List<Double>?) =
            put(key, values?.joinToString(",", "[", "]") { decimalText(it, MILLIS_DECIMALS) } ?: "null")
        fun strings(key: String, values: List<String>) = put(key, values.joinToString(",", "[", "]") { quote(it) })
        fun raw(key: String, json: String?) = put(key, json ?: "null")
        fun build(): String = parts.joinToString(",", "{", "}")

        /** A non-finite number has no JSON form; it is written as null rather than as text a parser would reject. */
        private fun decimalText(value: Double, decimals: Int): String =
            if (value.isFinite()) String.format(Locale.ROOT, "%.${decimals}f", value) else "null"

        private fun floatText(value: Float): String = if (value.isFinite()) value.toString() else "null"

        private fun quote(text: String): String {
            val out = StringBuilder(text.length + 2).append('"')
            for (c in text) {
                when {
                    c == '"' -> out.append("\\\"")
                    c == '\\' -> out.append("\\\\")
                    c == '\n' -> out.append("\\n")
                    c == '\r' -> out.append("\\r")
                    c == '\t' -> out.append("\\t")
                    c < ' ' -> out.append(String.format(Locale.ROOT, "\\u%04x", c.code))
                    else -> out.append(c)
                }
            }
            return out.append('"').toString()
        }
    }
}
