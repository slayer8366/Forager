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
internal enum class ReachStage(val id: String) { CHARACTERISTICS("characteristics"), OPEN("open"), QUERY("query"), CONFIGURE("configure"), CAPTURE("capture") }

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

    fun of(result: ReachResult): String = TODO("stub")
}

internal object ReachPlan {
    /** The operating limit on RAW size, the fast-burst probe's: the binned ~12 MP, never a full-resolution mode. */
    const val RAW_PIXEL_LIMIT = 16_000_000L

    fun plan(survey: ReachSurvey, rawPixelLimit: Long = RAW_PIXEL_LIMIT): List<PlannedReach> = TODO("stub")

    /** The largest `WxH` in [sizes] at or under [limit] pixels; null when none is. */
    fun largestWithin(sizes: List<String>, limit: Long = RAW_PIXEL_LIMIT): String? = TODO("stub")
}

internal object ReachRoles {
    /** Each camera ID's role, from characteristics only. */
    fun assign(survey: ReachSurvey): Map<String, String> = TODO("stub")
}

internal object ReachSummary {
    fun burstRate(frames: List<ReachFrame>): BurstRate? = TODO("stub")

    /** One row per back camera and one per pair, in the order the cameras were found. */
    fun rows(survey: ReachSurvey, roles: Map<String, String>, outcomes: List<ReachOutcome>): List<ReachRow> = TODO("stub")

    fun table(device: String?, rows: List<ReachRow>): String = TODO("stub")
}
