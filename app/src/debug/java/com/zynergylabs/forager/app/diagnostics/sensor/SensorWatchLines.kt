package com.zynergylabs.forager.app.diagnostics.sensor

import com.zynergylabs.forager.app.diagnostics.burst.BurstProbeLines
import java.util.Locale
import kotlin.math.ln

/**
 * The sensor watch's log lines (dispatch 2026-10-15-12). One JSON object per line under [TAG], so
 * `adb logcat -v raw -s SensorWatch` prints exactly these lines. Each has a `kind`:
 *
 * - Part 1: `watch` (on, off, already on or off, unavailable), `frame` (one per preview result at most five a second, one
 *   per still), `switch` (the active physical camera changed), `summary` (when the watch stops).
 * - Part 2: `zoom` (a set, each step, the return to 1.0) and `zoom_end`.
 * - Part 3: `pin_start`, `lens` (the logical camera and each physical one), `choice` (the main wide), `pin_borrow`,
 *   `pin_frame`, `pin_attempt`, `pin_handback`, `pin_end`.
 * - `busy` when a command is refused because another holds the camera.
 *
 * A reading not made is `null`, never 0 (CLAUDE.md, errors and failure paths). Camera2 constants are written by name, so
 * a table needs no lookup; a value camera2 does not define is written `UNKNOWN_<n>`, not dropped. A separate file from
 * the burst probes' lines rather than an edit to them; the AF state names are theirs, reused.
 */
internal object SensorWatchLines {
    const val TAG = "SensorWatch"

    fun watchLine(state: String, reason: String?, cameraId: String?, dialog: List<String>, notes: List<String>): String = JsonLine()
        .string("kind", "watch")
        .string("state", state)
        .string("reason", reason)
        .string("cameraId", cameraId)
        .strings("dialog", dialog)
        .strings("notes", notes)
        .build()

    fun frameLine(reading: FrameReading): String = JsonLine()
        .string("kind", "frame")
        .string("source", if (reading.still) "still" else "preview")
        .string("captureIntent", reading.captureIntent?.let { captureIntentName(it) })
        .string("activePhysicalId", reading.activePhysicalId)
        .float("zoomRatio", reading.zoomRatio)
        .float("focusDistanceDiopters", reading.focusDistanceDiopters)
        .decimal("focusDistanceM", focusMetres(reading.focusDistanceDiopters), 3)
        .string("afState", reading.afState?.let { BurstProbeLines.afStateName(it) })
        .long("exposureTimeNs", reading.exposureTimeNs)
        .int("iso", reading.iso)
        .float("aperture", reading.aperture)
        .decimal("ev100", ev100(reading.aperture, reading.exposureTimeNs, reading.iso), 2)
        .string("brightnessKey", reading.brightnessKey)
        .decimal("brightnessValue", reading.brightnessValue, 3)
        .long("sensorTimestampNs", reading.sensorTimestampNs)
        .build()

    fun switchLine(event: SwitchEvent): String = JsonLine()
        .string("kind", "switch")
        .string("from", event.from)
        .string("to", event.to)
        .strings("changed", event.changed)
        .float("zoomFrom", event.before.zoomRatio)
        .float("zoomTo", event.after.zoomRatio)
        .float("focusFrom", event.before.focusDistanceDiopters)
        .float("focusTo", event.after.focusDistanceDiopters)
        .long("exposureFrom", event.before.exposureTimeNs)
        .long("exposureTo", event.after.exposureTimeNs)
        .int("isoFrom", event.before.iso)
        .int("isoTo", event.after.iso)
        .long("sensorTimestampNs", event.after.sensorTimestampNs)
        .build()

    fun summaryLine(summary: WatchSummary, reason: String, notes: List<String>): String = JsonLine()
        .string("kind", "summary")
        .string("reason", reason)
        .int("framesSeen", summary.framesSeen)
        .int("previewFramesLogged", summary.previewFramesLogged)
        .int("previewFramesSkipped", summary.previewFramesSkipped)
        .int("stills", summary.stills)
        .int("switches", summary.switches)
        .counts("framesByPhysicalId", summary.framesByPhysicalId)
        .int("framesWithoutId", summary.framesWithoutId)
        .string("firstPhysicalId", summary.firstPhysicalId)
        .string("lastPhysicalId", summary.lastPhysicalId)
        .float("zoomMin", summary.zoomMin)
        .float("zoomMax", summary.zoomMax)
        .decimal("spanMs", summary.spanMs, 3)
        .strings("notes", notes)
        .build()

    fun zoomLine(
        mode: String,
        index: Int?,
        requested: Float?,
        applied: Float?,
        clamped: Boolean?,
        range: ZoomRange?,
        outcome: String,
        reason: String?,
        settled: FrameReading?,
        settledAgeMs: Double?,
        switchesDuringHold: Int?,
    ): String = JsonLine()
        .string("kind", "zoom")
        .string("mode", mode)
        .int("index", index)
        .float("requested", requested)
        .float("applied", applied)
        .bool("clamped", clamped)
        .float("rangeMin", range?.min)
        .float("rangeMax", range?.max)
        .string("outcome", outcome)
        .string("reason", reason)
        .string("settledActivePhysicalId", settled?.activePhysicalId)
        .float("settledZoomRatio", settled?.zoomRatio)
        .float("settledFocusDistanceDiopters", settled?.focusDistanceDiopters)
        .long("settledExposureTimeNs", settled?.exposureTimeNs)
        .int("settledIso", settled?.iso)
        .decimal("settledAgeMs", settledAgeMs, 3)
        .int("switchesDuringHold", switchesDuringHold)
        .build()

    fun zoomEndLine(mode: String, returnedToOne: Boolean, reason: String?): String = JsonLine()
        .string("kind", "zoom_end")
        .string("mode", mode)
        .bool("returnedToOne", returnedToOne)
        .string("reason", reason)
        .build()

    fun busyLine(asked: String, reason: String): String = JsonLine()
        .string("kind", "busy")
        .string("asked", asked)
        .string("reason", reason)
        .build()

    fun pinStartLine(attempts: List<PinAttempt>, burstFrames: Int): String = JsonLine()
        .string("kind", "pin_start")
        .strings("attempts", attempts.map { it.id })
        .int("burstFrames", burstFrames)
        .build()

    fun lensLine(role: String, facts: LensFacts): String = JsonLine()
        .string("kind", "lens")
        .string("role", role)
        .string("cameraId", facts.cameraId)
        .bool("inCameraIdList", facts.inCameraIdList)
        .floats("focalLengths", facts.focalLengths)
        .floats("physicalSizeMm", facts.physicalSizeMm)
        .string("cfa", facts.cfa?.let { cfaName(it) })
        .string("pixelArray", facts.pixelArray)
        .strings("rawSizes", facts.rawSizes)
        .ints("capabilities", facts.capabilities)
        .string("error", facts.error)
        .build()

    fun choiceLine(mainWideId: String?, reason: String): String = JsonLine()
        .string("kind", "choice")
        .string("mainWideId", mainWideId)
        .string("reason", reason)
        .build()

    fun pinBorrowLine(borrow: PinBorrow): String {
        val (outcome, reason, detail) = when (borrow) {
            is PinBorrow.Borrowed -> Triple("borrowed", null, borrow.detail)
            is PinBorrow.Failed -> Triple("failed", borrow.reason, borrow.detail)
        }
        return JsonLine()
            .string("kind", "pin_borrow")
            .string("outcome", outcome)
            .string("reason", reason)
            .strings("detail", detail)
            .build()
    }

    fun pinFrameLine(attempt: PinAttempt, frame: PinFrame): String = JsonLine()
        .string("kind", "pin_frame")
        .string("attempt", attempt.id)
        .int("index", frame.index)
        .string("capture", frame.kind)
        .long("sensorTimestampNs", frame.sensorTimestampNs)
        .string("activePhysicalId", frame.activePhysicalId)
        .strings("physicalResultIds", frame.physicalResultIds)
        .string("attributedTo", frame.attributedTo)
        .string("rawSize", frame.rawSize)
        .string("cfa", frame.cfa?.let { cfaName(it) })
        .string("error", frame.error)
        .build()

    fun pinAttemptLine(result: PinAttemptResult, verdict: String): String {
        val arrived = result.frames.filter { it.error == null }
        return JsonLine()
            .string("kind", "pin_attempt")
            .string("attempt", result.attempt.id)
            .string("verdict", verdict)
            .string("openedId", result.openedId)
            .string("physicalStreamId", result.physicalStreamId)
            .bool("configured", result.configured)
            .string("sessionQuery", result.sessionQuery)
            .string("rawSize", result.rawSize)
            .int("framesAsked", result.frames.size)
            .int("framesArrived", arrived.size)
            .strings("attributedTo", arrived.mapNotNull { it.attributedTo }.distinct())
            .string("error", result.error)
            .strings("notes", result.notes)
            .build()
    }

    fun pinSkippedLine(attempt: PinAttempt, reason: String): String = JsonLine()
        .string("kind", "pin_attempt")
        .string("attempt", attempt.id)
        .string("verdict", "skipped")
        .string("openedId", null)
        .string("physicalStreamId", null)
        .bool("configured", null)
        .string("sessionQuery", null)
        .string("rawSize", null)
        .int("framesAsked", 0)
        .int("framesArrived", 0)
        .strings("attributedTo", emptyList())
        .string("error", reason)
        .strings("notes", emptyList())
        .build()

    fun pinHandbackLine(handback: PinHandback): String = JsonLine()
        .string("kind", "pin_handback")
        .bool("platformClosed", handback.platformClosed)
        .bool("cameraxBack", handback.cameraxBack)
        .string("detail", handback.detail)
        .build()

    fun pinEndLine(completed: Boolean, reason: String?, verdicts: Map<String, String>): String = JsonLine()
        .string("kind", "pin_end")
        .bool("completed", completed)
        .string("reason", reason)
        .texts("verdicts", verdicts)
        .build()

    /** Exposure value at ISO 100, from the result's aperture, exposure time and ISO: log2(N²/t) − log2(ISO/100). */
    fun ev100(aperture: Float?, exposureTimeNs: Long?, iso: Int?): Double? {
        if (aperture == null || exposureTimeNs == null || iso == null) return null
        if (!(aperture > 0f) || exposureTimeNs <= 0L || iso <= 0) return null
        val n = aperture.toDouble()
        val seconds = exposureTimeNs / 1_000_000_000.0
        return log2(n * n / seconds) - log2(iso / 100.0)
    }

    fun captureIntentName(intent: Int): String = CAPTURE_INTENTS.getOrNull(intent) ?: "UNKNOWN_$intent"

    fun cfaName(cfa: Int): String = CFAS.getOrNull(cfa) ?: "UNKNOWN_$cfa"

    /** Metres from diopters; null at 0 (focused at infinity, which JSON cannot write) and below. */
    private fun focusMetres(diopters: Float?): Double? = diopters?.takeIf { it > 0f }?.let { 1.0 / it }

    private fun log2(x: Double): Double = ln(x) / ln(2.0)

    /** camera2's `CONTROL_CAPTURE_INTENT_*`, by value. */
    private val CAPTURE_INTENTS = listOf(
        "CUSTOM", "PREVIEW", "STILL_CAPTURE", "VIDEO_RECORD", "VIDEO_SNAPSHOT", "ZERO_SHUTTER_LAG", "MANUAL", "MOTION_TRACKING",
    )

    /** camera2's `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_*`, by value. */
    private val CFAS = listOf("RGGB", "GRBG", "GBRG", "BGGR", "RGB", "MONO", "NIR")

    /** One JSON object, keys in the order added. */
    private class JsonLine {
        private val parts = mutableListOf<String>()

        private fun put(key: String, value: String): JsonLine = apply { parts += "${quote(key)}:$value" }

        fun string(key: String, value: String?) = put(key, value?.let { quote(it) } ?: "null")
        fun int(key: String, value: Int?) = put(key, value?.toString() ?: "null")
        fun long(key: String, value: Long?) = put(key, value?.toString() ?: "null")
        fun bool(key: String, value: Boolean?) = put(key, value?.toString() ?: "null")
        fun float(key: String, value: Float?) = put(key, value?.let { floatText(it) } ?: "null")
        fun decimal(key: String, value: Double?, decimals: Int) = put(key, value?.let { decimalText(it, decimals) } ?: "null")
        fun floats(key: String, values: List<Float>?) = put(key, values?.joinToString(",", "[", "]") { floatText(it) } ?: "null")
        fun ints(key: String, values: List<Int>) = put(key, values.joinToString(",", "[", "]"))
        fun strings(key: String, values: List<String>) = put(key, values.joinToString(",", "[", "]") { quote(it) })
        fun counts(key: String, values: Map<String, Int>) =
            put(key, values.entries.joinToString(",", "{", "}") { (k, v) -> "${quote(k)}:$v" })
        fun texts(key: String, values: Map<String, String>) =
            put(key, values.entries.joinToString(",", "{", "}") { (k, v) -> "${quote(k)}:${quote(v)}" })
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
