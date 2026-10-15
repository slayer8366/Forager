package com.zynergylabs.forager.app.diagnostics.burst

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The platform burst probe's log lines, its run summary and its swap summary (dispatch 2026-10-15-09). Every line is one
 * JSON object under the tag [TAG], so `adb logcat -v raw -s PlatformBurst` prints exactly the probe's lines, one object
 * per line. Each line has a `kind` (`start`, `borrow`, `converge`, `frame`, `run`, `release`, `handback`, `end`, `busy`)
 * and the `session` it belongs to.
 *
 * A reading that was not made is written as `null`, never as 0 (CLAUDE.md, errors and failure paths). Processing modes
 * are written by name (camera2's constant names), so a table needs no lookup; a value camera2 does not define is written
 * `UNKNOWN_<n>`, not dropped.
 *
 * A separate file from piece 1's [BurstProbeLines] rather than an edit to it: the dispatch keeps piece 1's behaviour
 * untouched, and its JSON helper is private to it.
 */
internal object PlatformBurstLines {
    const val TAG = "PlatformBurst"

    fun startLine(session: Int, runs: List<PlatformRun>, framesPerRun: Int): String = JsonLine()
        .string("kind", "start")
        .int("session", session)
        .strings("runs", runs.map { it.id })
        .int("framesPerRun", framesPerRun)
        .build()

    fun borrowLine(session: Int, outcome: BorrowOutcome): String {
        val t = outcome.timing
        val (label, reason) = when (outcome) {
            is BorrowOutcome.Borrowed -> "borrowed" to null
            is BorrowOutcome.Unsupported -> "unsupported" to outcome.reason
            is BorrowOutcome.Failed -> "failed" to outcome.reason
        }
        return JsonLine()
            .string("kind", "borrow")
            .int("session", session)
            .string("outcome", label)
            .string("reason", reason)
            .string("cameraId", t.cameraId)
            .millis("cameraxClosedMs", t.cameraxClosedMs)
            .millis("viewfinderIdleMs", t.viewfinderIdleMs)
            .millis("platformOpenedMs", t.platformOpenedMs)
            .millis("sessionConfiguredMs", t.sessionConfiguredMs)
            .string("rawSize", t.rawSize)
            .string("previewSize", t.previewSize)
            .string("viewfinder", t.viewfinder)
            .strings("cameraStates", t.cameraStates)
            .strings("notes", t.notes)
            .build()
    }

    fun convergeLine(session: Int, outcome: ConvergeOutcome): String = when (outcome) {
        is ConvergeOutcome.Locked -> JsonLine()
            .string("kind", "converge")
            .int("session", session)
            .bool("locked", true)
            .millis("convergedMs", outcome.convergedMs)
            .millis("lockedMs", outcome.lockedMs)
            .long("exposureTimeNs", outcome.exposureTimeNs)
            .int("iso", outcome.iso)
            .long("frameDurationNs", outcome.frameDurationNs)
            .string("reason", null)
            .string("lastAeState", null)
            .string("lastAwbState", null)
            .build()
        is ConvergeOutcome.NotReached -> JsonLine()
            .string("kind", "converge")
            .int("session", session)
            .bool("locked", false)
            .millis("convergedMs", null)
            .millis("lockedMs", null)
            .long("exposureTimeNs", null)
            .int("iso", null)
            .long("frameDurationNs", null)
            .string("reason", outcome.reason)
            .string("lastAeState", outcome.lastAeState?.let { BurstProbeLines.aeStateName(it) })
            .string("lastAwbState", outcome.lastAwbState?.let { BurstProbeLines.awbStateName(it) })
            .build()
    }

    fun frameLine(session: Int, run: PlatformRun, frame: PlatformFrame): String = JsonLine()
        .string("kind", "frame")
        .int("session", session)
        .string("run", run.id)
        .int("index", frame.index)
        .millis("arrivalMs", frame.arrivalMs)
        .long("sensorTimestampNs", frame.sensorTimestampNs)
        .bool("resultMatched", frame.resultMatched)
        .long("exposureTimeNs", frame.exposureTimeNs)
        .int("iso", frame.iso)
        .long("frameDurationNs", frame.frameDurationNs)
        .int("postRawBoost", frame.postRawBoost)
        .string("noiseReduction", frame.noiseReductionMode?.let { name(NOISE_REDUCTION_MODES, it) })
        .string("hotPixel", frame.hotPixelMode?.let { name(HOT_PIXEL_MODES, it) })
        .string("edge", frame.edgeMode?.let { name(EDGE_MODES, it) })
        .string("shading", frame.shadingMode?.let { name(SHADING_MODES, it) })
        .string("tonemap", frame.tonemapMode?.let { name(TONEMAP_MODES, it) })
        .bool("blackLevelLock", frame.blackLevelLock)
        .string("lensShadingMapMode", frame.lensShadingMapMode?.let { name(LENS_SHADING_MAP_MODES, it) })
        .floats("blackLevel", frame.blackLevel)
        .int("whiteLevel", frame.whiteLevel)
        .millis("copyMs", frame.copyMs)
        .decimal("cornerToCentre", frame.cornerToCentre, RATIO_DECIMALS)
        .string("dngFile", frame.dngFile)
        .string("dngOutcome", frame.dngOutcome)
        .strings("notes", frame.notes)
        .string("error", frame.error)
        .build()

    /** A run that captured, with its summary and facts. */
    fun runLine(session: Int, run: PlatformRun, outcome: RunOutcomeKind, reason: String?, summary: PlatformRunSummary?, facts: PlatformRunFacts?): String =
        JsonLine()
            .string("kind", "run")
            .int("session", session)
            .string("run", run.id)
            .string("outcome", outcome.label)
            .string("reason", reason)
            .string("requestMethod", facts?.requestMethod)
            .strings("requested", facts?.requested.orEmpty())
            .int("framesExpected", summary?.framesExpected)
            .int("framesArrived", summary?.framesArrived)
            .millisList("intervalsMs", summary?.intervalsMs)
            .millis("meanIntervalMs", summary?.meanIntervalMs)
            .millis("minIntervalMs", summary?.minIntervalMs)
            .millis("maxIntervalMs", summary?.maxIntervalMs)
            .millis("spreadMs", summary?.spreadMs)
            .millis("framesPerSecond", summary?.framesPerSecond)
            .millis("firstToLastMs", summary?.firstToLastMs)
            .millis("submitToLastArrivalMs", summary?.submitToLastArrivalMs)
            .int("framesWithResult", summary?.framesWithResult)
            .int("framesSaved", summary?.framesSaved)
            .string("rawSize", facts?.rawSize)
            .long("minFrameDurationNs", facts?.minFrameDurationNs)
            .long("stallDurationNs", facts?.stallDurationNs)
            .long("maxMemoryBytes", facts?.maxMemoryBytes)
            .long("heapBeforeBytes", facts?.heapBeforeBytes)
            .long("heapPeakBytes", facts?.heapPeakBytes)
            .long("heapAfterBytes", facts?.heapAfterBytes)
            .millis("submitMs", facts?.submitMs)
            .millis("saveMs", facts?.saveMs)
            .string("folder", facts?.folder)
            .strings("notes", facts?.notes.orEmpty())
            .build()

    fun releaseLine(session: Int, release: LockRelease): String = JsonLine()
        .string("kind", "release")
        .int("session", session)
        .bool("lockReleased", release.cleared)
        .string("detail", release.detail)
        .build()

    fun handbackLine(session: Int, handback: PlatformHandback): String = JsonLine()
        .string("kind", "handback")
        .int("session", session)
        .bool("handedBack", handback.platformClosed && handback.probeUnbound)
        .bool("platformClosed", handback.platformClosed)
        .bool("probeUnbound", handback.probeUnbound)
        .millis("handbackStartMs", handback.handbackStartMs)
        .millis("platformClosedMs", handback.platformClosedMs)
        .millis("cameraxOpenMs", handback.cameraxOpenMs)
        .millis("viewfinderLiveMs", handback.viewfinderLiveMs)
        .millis("handbackToCameraOpenMs", difference(handback.handbackStartMs, handback.cameraxOpenMs))
        .millis("handbackToViewfinderLiveMs", difference(handback.handbackStartMs, handback.viewfinderLiveMs))
        .strings("cameraStates", handback.cameraStates)
        .string("detail", handback.detail)
        .build()

    fun endLine(session: Int, completed: Boolean, reason: String?, swap: SwapSummary?): String = JsonLine()
        .string("kind", "end")
        .int("session", session)
        .bool("completed", completed)
        .string("reason", reason)
        .millis("triggerToPlatformOpenMs", swap?.triggerToPlatformOpenMs)
        .millis("platformOpenToFirstRawMs", swap?.platformOpenToFirstRawMs)
        .millis("triggerToFirstRawMs", swap?.triggerToFirstRawMs)
        .millis("viewfinderNotLiveMs", swap?.viewfinderNotLiveMs)
        .millis("handbackToViewfinderLiveMs", swap?.handbackToViewfinderLiveMs)
        .build()

    /** A trigger that arrived while a probe was already running; it is not queued. */
    fun busyLine(runningSession: Int): String = JsonLine()
        .string("kind", "busy")
        .int("session", runningSession)
        .string("reason", "a platform burst probe is already running; this trigger was ignored")
        .build()

    /**
     * The run summary. Frames count as arrived when they carry no error and a sensor timestamp; the rest are left out of
     * every figure. Intervals are between consecutive exposures in sensor-timestamp order: the sensor's own spacing.
     */
    fun summarize(framesExpected: Int, frames: List<PlatformFrame>, submitMs: Double?): PlatformRunSummary {
        val arrived = frames.filter { it.error == null && it.sensorTimestampNs != null }.sortedBy { it.sensorTimestampNs }
        val intervals = arrived.zipWithNext { a, b -> (b.sensorTimestampNs!! - a.sensorTimestampNs!!) / 1_000_000.0 }
        val mean = intervals.takeIf { it.isNotEmpty() }?.average()
        val lastArrival = arrived.mapNotNull { it.arrivalMs }.maxOrNull()
        return PlatformRunSummary(
            framesExpected = framesExpected,
            framesArrived = arrived.size,
            intervalsMs = intervals,
            meanIntervalMs = mean,
            minIntervalMs = intervals.minOrNull(),
            maxIntervalMs = intervals.maxOrNull(),
            spreadMs = if (intervals.isEmpty()) null else intervals.max() - intervals.min(),
            framesPerSecond = mean?.takeIf { it > 0.0 }?.let { 1000.0 / it },
            firstToLastMs = if (arrived.isEmpty()) null else (arrived.last().sensorTimestampNs!! - arrived.first().sensorTimestampNs!!) / 1_000_000.0,
            submitToLastArrivalMs = difference(submitMs, lastArrival),
            framesWithResult = arrived.count { it.resultMatched },
            framesSaved = frames.count { it.dngOutcome == DNG_WRITTEN },
        )
    }

    /**
     * The figures the owner sees (dispatch, device check): the swap pause, and how long the viewfinder was not live. Each
     * is null when either end of it was not seen.
     */
    fun swapSummary(borrow: SwapTiming?, firstRawArrivalMs: Double?, handback: PlatformHandback?): SwapSummary = SwapSummary(
        triggerToPlatformOpenMs = borrow?.platformOpenedMs,
        platformOpenToFirstRawMs = difference(borrow?.platformOpenedMs, firstRawArrivalMs),
        triggerToFirstRawMs = firstRawArrivalMs,
        viewfinderNotLiveMs = difference(borrow?.viewfinderIdleMs, handback?.viewfinderLiveMs),
        handbackToViewfinderLiveMs = difference(handback?.handbackStartMs, handback?.viewfinderLiveMs),
    )

    private fun difference(from: Double?, to: Double?): Double? = if (from != null && to != null) to - from else null

    /** What a frame's [PlatformFrame.dngOutcome] says when its DNG was written and passed the writer's check. */
    const val DNG_WRITTEN = "written"

    // camera2's mode constants, by value. Written out here rather than read from android.hardware.camera2 so this file
    // stays testable on the JVM; each list is the constant names in value order.
    private val NOISE_REDUCTION_MODES = listOf("OFF", "FAST", "HIGH_QUALITY", "MINIMAL", "ZERO_SHUTTER_LAG")
    private val HOT_PIXEL_MODES = listOf("OFF", "FAST", "HIGH_QUALITY")
    private val EDGE_MODES = listOf("OFF", "FAST", "HIGH_QUALITY", "ZERO_SHUTTER_LAG")
    private val SHADING_MODES = listOf("OFF", "FAST", "HIGH_QUALITY")
    private val TONEMAP_MODES = listOf("CONTRAST_CURVE", "FAST", "HIGH_QUALITY", "GAMMA_VALUE", "PRESET_CURVE")
    private val LENS_SHADING_MAP_MODES = listOf("OFF", "ON")

    private fun name(names: List<String>, value: Int): String = names.getOrNull(value) ?: "UNKNOWN_$value"

    private const val MILLIS_DECIMALS = 3
    private const val RATIO_DECIMALS = 4

    /** One JSON object, keys in the order added. */
    private class JsonLine {
        private val parts = mutableListOf<String>()

        private fun put(key: String, value: String): JsonLine = apply { parts += "${quote(key)}:$value" }

        fun string(key: String, value: String?) = put(key, value?.let { quote(it) } ?: "null")
        fun int(key: String, value: Int?) = put(key, value?.toString() ?: "null")
        fun long(key: String, value: Long?) = put(key, value?.toString() ?: "null")
        fun bool(key: String, value: Boolean?) = put(key, value?.toString() ?: "null")
        fun millis(key: String, value: Double?) = decimal(key, value, MILLIS_DECIMALS)
        fun decimal(key: String, value: Double?, decimals: Int) = put(key, value?.let { decimalText(it, decimals) } ?: "null")
        fun floats(key: String, values: List<Float>?) = put(key, values?.joinToString(",", "[", "]") { if (it.isFinite()) it.toString() else "null" } ?: "null")
        fun millisList(key: String, values: List<Double>?) =
            put(key, values?.joinToString(",", "[", "]") { decimalText(it, MILLIS_DECIMALS) } ?: "null")
        fun strings(key: String, values: List<String>) = put(key, values.joinToString(",", "[", "]") { quote(it) })
        fun build(): String = parts.joinToString(",", "{", "}")

        private fun decimalText(value: Double, decimals: Int): String =
            if (value.isFinite()) String.format(Locale.ROOT, "%.${decimals}f", value) else "null"

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

/** The end line's figures; see [PlatformBurstLines.swapSummary]. */
internal data class SwapSummary(
    val triggerToPlatformOpenMs: Double?,
    val platformOpenToFirstRawMs: Double?,
    val triggerToFirstRawMs: Double?,
    val viewfinderNotLiveMs: Double?,
    val handbackToViewfinderLiveMs: Double?,
)

/**
 * Where the test frames are saved (dispatch: "the app's own storage, not the Gallery or the journal, under a folder the
 * planner can pull with `run-as`"; -1046 Q1: the two test phones only, never GitHub, never a release). Paths are relative
 * to the app's files directory:
 *
 * `raw-burst-test/<UTC start, yyyyMMdd-HHmmssZ>-s<session>/<run id>/frame-01.dng` … `frame-08.dng`
 *
 * The start time keeps two processes' session 1 apart; the session keeps two triggers in one second apart.
 */
internal object PlatformBurstFiles {
    const val ROOT = "raw-burst-test"

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    fun sessionFolder(startedAtEpochMillis: Long, session: Int): String =
        "$ROOT/${STAMP.format(Instant.ofEpochMilli(startedAtEpochMillis))}-s$session"

    fun runFolder(sessionFolder: String, run: PlatformRun): String = "$sessionFolder/${run.id}"

    /** [index] is the frame's 0-based index in its run; files count from 1, as a person reads them. */
    fun frameFile(runFolder: String, index: Int): String = String.format(Locale.ROOT, "%s/frame-%02d.dng", runFolder, index + 1)
}

/**
 * How vignetted a RAW frame is: the mean of its four corner blocks over the mean of its centre block, black level
 * subtracted. A RAW the camera has already shading-corrected reads near 1; an uncorrected one well below 1 (a flat,
 * evenly lit scene is what makes the figure mean that; on a textured scene it also carries the scene). It is how the
 * shading-OFF run answers "whether the RAW changes" in the log, beside the DNGs measured offline.
 *
 * Blocks are square, a sixteenth of the shorter side, and start on even rows and columns so each holds whole 2x2 colour
 * cells and every channel equally; [black] is the 2x2 black-level repeat, row-major. Null when the frame is too small,
 * the sample count does not match, the black level is not four values, or the centre is at or below black.
 */
internal fun rawCornerToCentre(samples: ShortArray, width: Int, height: Int, black: List<Float>): Double? {
    if (width <= 0 || height <= 0 || black.size != 4 || samples.size.toLong() != width.toLong() * height) return null
    val block = (minOf(width, height) / 16) and 1.inv()
    if (block < 2) return null
    fun even(v: Int) = v and 1.inv()
    fun mean(x0: Int, y0: Int): Double {
        var sum = 0.0
        for (y in y0 until y0 + block) {
            for (x in x0 until x0 + block) {
                sum += (samples[y * width + x].toInt() and 0xFFFF) - black[(y and 1) * 2 + (x and 1)]
            }
        }
        return sum / (block * block)
    }
    val right = even(width - block)
    val bottom = even(height - block)
    val centre = mean(even((width - block) / 2), even((height - block) / 2))
    if (centre <= 0.0) return null
    val corners = (mean(0, 0) + mean(right, 0) + mean(0, bottom) + mean(right, bottom)) / 4
    return corners / centre
}
