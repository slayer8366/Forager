package com.zynergylabs.forager.app.diagnostics.sensor

/*
 * The reach probe's domain (dispatch 2026-10-16-01, RECORD -1082): which cameras the phone describes, which RAW attempts
 * to make on each back camera, what each attempt's outcome is called, and the per-phone summary. No Android types, so
 * all of it runs headless in the unit tests; the platform side is [PlatformReachCamera].
 */

/** What the characteristics say about one camera ID, listed or physical. [error] when they could not be read. */
internal data class CameraEntry(
    val cameraId: String,
    /** Whether `getCameraIdList` lists it. */
    val inCameraIdList: Boolean,
    /** `BACK`, `FRONT`, `EXTERNAL` or `UNKNOWN_<n>`; null when not read. */
    val facing: String?,
    val focalLengths: List<Float>,
    /** Width and height in millimetres. */
    val physicalSizeMm: List<Float>?,
    val cfa: Int?,
    val pixelArray: String?,
    /** Every RAW_SENSOR output size, `WxH`, as the stream configuration map lists them. */
    val rawSizes: List<String>,
    val capabilities: List<Int>,
    /** Its physical cameras, when it is a logical multi-camera; empty otherwise. */
    val physicalIds: List<String>,
    /** The listed logical cameras that name it as one of their physical cameras. */
    val logicalIds: List<String>,
    val error: String? = null,
)

internal data class ReachSurvey(
    /** The phone's own words for itself (manufacturer and model), for the table's heading. */
    val device: String?,
    val cameraIdList: List<String>,
    /** Every listed ID, then every physical ID not listed, in the order found. */
    val cameras: List<CameraEntry>,
    val notes: List<String> = emptyList(),
)

/** One way of getting RAW from one camera (dispatch, "What to build", 2). */
internal sealed interface ReachAttempt {
    /** Stable, for the log and the table: `through_logical_<L>_<P>`, `direct_<id>`, `pair_<L>_<a>_<b>`. */
    val label: String

    /** `through_logical_<L>`, `direct` or `pair`: how the camera was reached, for the summary. */
    val method: String

    /** (a) Logical camera [logicalId] opened, a RAW stream bound to its physical camera [physicalId]. */
    data class ThroughLogical(val logicalId: String, val physicalId: String) : ReachAttempt {
        override val label get() = "through_logical_${logicalId}_$physicalId"
        override val method get() = "through_logical_$logicalId"
    }

    /** (b) Camera [cameraId] opened by its own ID, a RAW stream on it. */
    data class Direct(val cameraId: String) : ReachAttempt {
        override val label get() = "direct_$cameraId"
        override val method get() = "direct"
    }

    /** (c) Logical camera [logicalId] opened, two RAW streams of [rawSize] bound to [first] and [second] at once. */
    data class TwoStreams(val logicalId: String, val first: String, val second: String, val rawSize: String) : ReachAttempt {
        override val label get() = "pair_${logicalId}_${first}_$second"
        override val method get() = "pair"
    }
}

/** One planned step: an attempt to make, or one not made with its reason. */
internal sealed interface PlannedReach {
    val label: String
    /** The camera the step is about (for a pair, the logical camera). */
    val cameraId: String

    data class Run(val attempt: ReachAttempt) : PlannedReach {
        override val label get() = attempt.label
        override val cameraId get() = when (attempt) {
            is ReachAttempt.ThroughLogical -> attempt.physicalId
            is ReachAttempt.Direct -> attempt.cameraId
            is ReachAttempt.TwoStreams -> attempt.logicalId
        }
    }

    data class Skip(override val label: String, override val cameraId: String, val method: String, val reason: String) : PlannedReach
}

/** One RAW frame of an attempt. Nothing is saved; the image is read for its size and timestamp and closed. */
internal data class ReachFrame(
    val index: Int,
    /** `single` or `burst`. */
    val kind: String,
    /** The camera the RAW stream is bound to: the physical camera of a physical stream, else the camera opened. */
    val streamCameraId: String,
    val sensorTimestampNs: Long?,
    /** The result's `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID`; null when not reported. */
    val activePhysicalId: String?,
    /** The physical cameras the result carries per-physical results for. */
    val physicalResultIds: List<String>,
    val rawSize: String?,
    val error: String?,
)

/** Where an attempt stopped, when it stopped early. */
internal enum class ReachStage(val id: String) {
    CHARACTERISTICS("characteristics"), OPEN("open"), QUERY("query"), CONFIGURE("configure"), CAPTURE("capture"),

    /** The attempt threw or was cut off without saying where. */
    UNKNOWN("unknown"),
}

internal data class ReachResult(
    val attempt: ReachAttempt,
    val openedId: String,
    /** The cameras the RAW streams are bound to, in stream order. */
    val streamIds: List<String>,
    /** The RAW size asked for; null when the camera offers none within the operating limit. */
    val rawSize: String?,
    /** Whether the open succeeded. */
    val opened: Boolean,
    /** What `isSessionConfigurationSupported` answered (`supported`, `not supported`), or why it was not asked. */
    val sessionQuery: String?,
    /** Whether the session configured; null when it was never tried. */
    val configured: Boolean?,
    val frames: List<ReachFrame>,
    /** The burst's length as asked; 0 for a pair, which takes one frame only. */
    val burstAsked: Int,
    /** Set when the attempt stopped early: where, and [error] says why. */
    val stoppedAt: ReachStage?,
    val error: String?,
    val notes: List<String>,
    /** True when the probe cut the attempt off at its timeout. */
    val timedOut: Boolean = false,
)

/** A burst's measured rate, from sensor timestamps. */
internal data class BurstRate(val frames: Int, val spanMs: Double, val framesPerSecond: Double)

/** One planned step and how it ended; [result] is null for a step not made. */
internal data class ReachOutcome(val planned: PlannedReach, val verdict: String, val result: ReachResult?)

/** One row of the per-phone table. */
internal data class ReachRow(
    val cameraId: String,
    val role: String,
    /** Each method's verdict for this camera, in the order made, e.g. `through_logical_0` to `reachable_raw`. */
    val outcomes: List<Pair<String, String>>,
    /** The methods whose verdict was `reachable_raw`. */
    val reachableRaw: List<String>,
    val rawSize: String?,
    val burstFps: Double?,
)

internal object ReachVerdict {
    const val REACHABLE_RAW = "reachable_raw"
    const val REACHABLE_NO_RAW = "reachable_no_raw"
    const val REFUSED = "refused"
    const val UNSUPPORTED = "unsupported"

    /** Beyond the dispatch's four, so nothing is squeezed into one of them: the probe cut the attempt off. */
    const val TIMED_OUT = "timed_out"

    /** Beyond the dispatch's four: the attempt failed before it reached the camera (characteristics unreadable, say). */
    const val FAILED = "failed"

    /** A planned step not made, with its reason. */
    const val NOT_ATTEMPTED = "not_attempted"

    /**
     * An attempt's outcome in one word. Read in this order: cut off; failed before the camera was asked; the open
     * refused; the session query's no; the session refused; then, with a session running, whether RAW arrived on every
     * stream asked for. Nothing is inferred from a missing reading: no RAW frame is `reachable_no_raw`, never RAW.
     */
    fun of(result: ReachResult): String {
        if (result.timedOut) return TIMED_OUT
        if (result.stoppedAt == ReachStage.CHARACTERISTICS || result.stoppedAt == ReachStage.UNKNOWN) return FAILED
        if (!result.opened) return REFUSED
        if (result.sessionQuery == "not supported") return UNSUPPORTED
        if (result.configured != true) return REFUSED
        if (result.rawSize == null) return REACHABLE_NO_RAW
        val arrivedOn = result.frames.filter { it.error == null && it.sensorTimestampNs != null }.map { it.streamCameraId }.toSet()
        return if (result.streamIds.isNotEmpty() && result.streamIds.all { it in arrivedOn }) REACHABLE_RAW else REACHABLE_NO_RAW
    }
}

internal object ReachPlan {
    /** The operating limit on RAW size, the fast-burst probe's: the binned ~12 MP, never a full-resolution mode. */
    const val RAW_PIXEL_LIMIT = 16_000_000L

    /**
     * For each back camera in the order found: (a) through each listed logical camera that names it, (b) a direct open,
     * or the reason none is made when the ID is not listed; then (c) every pair of a listed logical camera's back
     * physical cameras. A pair is made only when both offer one RAW size within the limit: the platform's multi-camera
     * guarantee is that a logical camera's RAW stream "can be replaced with two streams of identical type and size"
     * from two of its physical cameras (CameraDevice's documentation, READ for the pin probe), and no wider pair is
     * guaranteed. Front cameras are enumerated and not attempted (dispatch: "for each back camera").
     */
    fun plan(survey: ReachSurvey, rawPixelLimit: Long = RAW_PIXEL_LIMIT): List<PlannedReach> {
        val byId = survey.cameras.associateBy { it.cameraId }
        val listedLogicals = survey.cameras.filter { it.inCameraIdList && it.physicalIds.isNotEmpty() }.map { it.cameraId }.toSet()
        val steps = mutableListOf<PlannedReach>()
        for (camera in survey.cameras) {
            if (camera.facing == null) {
                steps += PlannedReach.Skip(
                    "unread_${camera.cameraId}", camera.cameraId, "any",
                    "camera ${camera.cameraId}'s facing could not be read (error ${camera.error}), so it is not known to be a back camera; not attempted",
                )
                continue
            }
            if (camera.facing != "BACK") continue
            for (logical in camera.logicalIds.filter { it in listedLogicals }) {
                steps += PlannedReach.Run(ReachAttempt.ThroughLogical(logical, camera.cameraId))
            }
            steps += if (true) {
                PlannedReach.Run(ReachAttempt.Direct(camera.cameraId))
            } else {
                PlannedReach.Skip(
                    "direct_${camera.cameraId}", camera.cameraId, "direct",
                    "camera ${camera.cameraId} is not in getCameraIdList ${survey.cameraIdList}; not opened directly",
                )
            }
        }
        for (logicalId in listedLogicals.sortedBy { id -> survey.cameras.indexOfFirst { it.cameraId == id } }) {
            val backs = byId.getValue(logicalId).physicalIds.mapNotNull { byId[it] }.filter { it.facing == "BACK" }
            for (i in backs.indices) for (j in i + 1 until backs.size) {
                val a = backs[i]
                val b = backs[j]
                val label = "pair_${logicalId}_${a.cameraId}_${b.cameraId}"
                val shared = largestWithin(a.rawSizes.filter { it in b.rawSizes }, rawPixelLimit)
                steps += if (shared != null) {
                    PlannedReach.Run(ReachAttempt.TwoStreams(logicalId, a.cameraId, b.cameraId, shared))
                } else {
                    PlannedReach.Skip(
                        label, logicalId, "pair",
                        "cameras ${a.cameraId} ${a.rawSizes} and ${b.cameraId} ${b.rawSizes} share no RAW size at or under " +
                            "${rawPixelLimit / 1_000_000} MP; the platform guarantees a pair of physical streams of identical type and size only",
                    )
                }
            }
        }
        return steps
    }

    /** The largest `WxH` in [sizes] at or under [limit] pixels; null when none is. A size that does not parse is never chosen. */
    fun largestWithin(sizes: List<String>, limit: Long = RAW_PIXEL_LIMIT): String? =
        sizes.mapNotNull { text -> pixels(text)?.let { text to it } }.filter { it.second <= limit }.maxByOrNull { it.second }?.first

    /** Width times height of a `WxH`; null when it does not parse. */
    fun pixels(size: String): Long? {
        val parts = size.split('x')
        if (parts.size != 2) return null
        val w = parts[0].toLongOrNull() ?: return null
        val h = parts[1].toLongOrNull() ?: return null
        return if (w > 0 && h > 0) w * h else null
    }
}

internal object ReachRoles {
    /**
     * Each camera ID's role, from characteristics only: `logical` for a camera with physical cameras, `front`, the main
     * wide ([MainWideChooser] on the first listed back logical camera), and every other back camera by its field of
     * view against the main wide's: the ratio of focal length over sensor diagonal, written as a zoom factor. Under
     * 0.85 is `ultra_wide_<f>x`, over 1.15 `telephoto_<f>x`, between `wide_<f>x`. With no main wide, or a camera whose
     * focal length or size is unread, it is only `back`; `unknown` when its facing is unread.
     */
    fun assign(survey: ReachSurvey): Map<String, String> {
        val byId = survey.cameras.associateBy { it.cameraId }
        val mainId = survey.cameras.firstOrNull { it.inCameraIdList && it.facing == "BACK" && it.physicalIds.isNotEmpty() }?.let { logical ->
            val physicals = logical.physicalIds.mapNotNull { byId[it] }.map { it.toLensFacts() }
            (MainWideChooser.choose(logical.toLensFacts(), physicals) as? MainWideChoice.Chosen)?.cameraId
        }
        val mainView = mainId?.let { byId[it] }?.let { view(it) }
        return survey.cameras.associate { camera ->
            camera.cameraId to when {
                camera.physicalIds.isNotEmpty() -> "logical"
                camera.facing == null -> "unknown"
                camera.facing == "FRONT" -> "front"
                camera.facing != "BACK" -> camera.facing?.lowercase() ?: "unknown"
                camera.cameraId == mainId -> "main_wide"
                else -> {
                    val own = view(camera)
                    if (own == null || mainView == null) {
                        "back"
                    } else {
                        val factor = own / mainView
                        val text = String.format(java.util.Locale.ROOT, "%.1fx", factor)
                        when {
                            factor < 0.85 -> "ultra_wide_$text"
                            factor > 1.15 -> "telephoto_$text"
                            else -> "wide_$text"
                        }
                    }
                }
            }
        }
    }

    /** Focal length over sensor diagonal: proportional to the zoom factor a lens gives. */
    private fun view(camera: CameraEntry): Double? {
        val focal = camera.focalLengths.firstOrNull() ?: return null
        val size = camera.physicalSizeMm?.takeIf { it.size == 2 } ?: return null
        val diagonal = kotlin.math.sqrt(size[0].toDouble() * size[0] + size[1].toDouble() * size[1])
        return if (diagonal > 0 && focal > 0f) focal / diagonal else null
    }

    private fun CameraEntry.toLensFacts() =
        LensFacts(cameraId, inCameraIdList, focalLengths, physicalSizeMm, cfa, pixelArray, rawSizes, capabilities, error)
}

internal object ReachSummary {
    /** The burst frames that arrived, by sensor timestamp: (n - 1) over first to last. Null for fewer than two. */
    fun burstRate(frames: List<ReachFrame>): BurstRate? {
        val times = frames.filter { it.kind == "burst" && it.error == null }.mapNotNull { it.sensorTimestampNs }.sorted()
        if (times.size < 2) return null
        val spanNs = times.last() - times.first()
        if (spanNs <= 0L) return null
        return BurstRate(times.size, spanNs / 1_000_000.0, (times.size - 1) * 1_000_000_000.0 / spanNs)
    }

    /** One row per back camera and one per pair, in the order the plan made them. */
    fun rows(survey: ReachSurvey, roles: Map<String, String>, outcomes: List<ReachOutcome>): List<ReachRow> {
        val rows = mutableListOf<ReachRow>()
        val singles = outcomes.filter { it.planned.method() != "pair" }.groupBy { it.planned.cameraId }
        for (camera in survey.cameras) {
            val mine = singles[camera.cameraId] ?: continue
            rows += row(camera.cameraId, roles[camera.cameraId] ?: "unknown", mine)
        }
        for (pair in outcomes.filter { it.planned.method() == "pair" }) {
            val (logical, ids) = pairIds(pair.planned)
            rows += row(ids, "pair_on_$logical", listOf(pair))
        }
        return rows
    }

    private fun row(cameraId: String, role: String, outcomes: List<ReachOutcome>): ReachRow {
        val reached = outcomes.filter { it.verdict == ReachVerdict.REACHABLE_RAW && it.result != null }
        return ReachRow(
            cameraId = cameraId,
            role = role,
            outcomes = outcomes.map { it.planned.method() to it.verdict },
            reachableRaw = reached.map { it.planned.method() },
            rawSize = reached.firstNotNullOfOrNull { it.result?.rawSize },
            burstFps = reached.mapNotNull { o -> o.result?.let { burstRate(it.frames)?.framesPerSecond } }.maxOrNull(),
        )
    }

    private fun PlannedReach.method(): String = when (this) {
        is PlannedReach.Run -> attempt.method
        is PlannedReach.Skip -> method
    }

    /** A pair's logical camera and its `a+b`, from the attempt or, for a pair not made, from its label. */
    private fun pairIds(planned: PlannedReach): kotlin.Pair<String, String> = when (planned) {
        is PlannedReach.Run -> (planned.attempt as ReachAttempt.TwoStreams).let { it.logicalId to "${it.first}+${it.second}" }
        is PlannedReach.Skip -> planned.label.removePrefix("pair_").split('_').let { parts ->
            planned.cameraId to parts.drop(1).joinToString("+")
        }
    }

    /**
     * The per-phone table, one row a line under a heading: camera, role, reachable how, RAW size, frame rate. "Reachable
     * how" lists the methods that gave RAW, with every other outcome in brackets; `none` when nothing gave RAW.
     */
    fun table(device: String?, rows: List<ReachRow>): String {
        val lines = mutableListOf(device ?: "this phone (its model was not read)", "camera | role | reachable how | RAW size | frame rate")
        for (row in rows) {
            val others = row.outcomes.filter { it.second != ReachVerdict.REACHABLE_RAW }.joinToString("; ") { "${it.first}: ${it.second}" }
            val how = when {
                row.reachableRaw.isEmpty() -> "none ($others)"
                others.isEmpty() -> row.reachableRaw.joinToString(", ")
                else -> "${row.reachableRaw.joinToString(", ")} ($others)"
            }
            val rate = row.burstFps?.let { String.format(java.util.Locale.ROOT, "%.1f fps", it) } ?: "-"
            lines += "${row.cameraId} | ${row.role} | $how | ${row.rawSize ?: "-"} | $rate"
        }
        return lines.joinToString("\n")
    }
}
