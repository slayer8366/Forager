package com.zynergylabs.forager.app.diagnostics.sensor

/*
 * The sensor watch's pure parts (dispatch 2026-10-15-12, RECORD -1077): what one capture result says about the sensor
 * behind it, the switch detector and its rate limit, the watch summary, the zoom clamp and step list, and the choice of
 * the main wide physical camera. Debug builds only. No Android type appears here, so all of it is tested on the JVM.
 */

/** One capture result of the dialog's camera, reduced to what the watch logs. A reading not reported is null, never 0. */
internal data class FrameReading(
    /** When the result reached the app, on the elapsed-realtime clock: the rate limit's clock. */
    val arrivalNanos: Long,
    val sensorTimestampNs: Long?,
    /** A still capture's result (capture intent STILL_CAPTURE or ZERO_SHUTTER_LAG), not the viewfinder's. */
    val still: Boolean,
    val captureIntent: Int?,
    /** `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID`: which physical camera produced this frame. */
    val activePhysicalId: String?,
    val zoomRatio: Float?,
    val focusDistanceDiopters: Float?,
    val afState: Int?,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val aperture: Float?,
    /** A result key the camera offers whose name says it is a brightness estimate, if any; its units are the camera's own. */
    val brightnessKey: String?,
    val brightnessValue: Double?,
)

/** The active physical camera changed between two results. [before] is the last result that named a physical camera. */
internal data class SwitchEvent(val from: String, val to: String, val before: FrameReading, val after: FrameReading) {
    /** What differs between the two results: `zoom`, `focus`, `exposure` (exposure time or ISO). */
    val changed: List<String>
        get() = buildList {
            if (before.zoomRatio != after.zoomRatio) add("zoom")
            if (before.focusDistanceDiopters != after.focusDistanceDiopters) add("focus")
            if (before.exposureTimeNs != after.exposureTimeNs || before.iso != after.iso) add("exposure")
        }
}

/** What the watch does with one result: log its frame line (or not, by the rate limit), and log a switch (or not). */
internal data class Observation(val logFrame: Boolean, val switch: SwitchEvent?)

/** The watch's totals, logged when it stops. */
internal data class WatchSummary(
    val framesSeen: Int,
    val previewFramesLogged: Int,
    val previewFramesSkipped: Int,
    val stills: Int,
    val switches: Int,
    /** Frames per physical camera, in the order each was first seen. */
    val framesByPhysicalId: Map<String, Int>,
    val framesWithoutId: Int,
    val firstPhysicalId: String?,
    val lastPhysicalId: String?,
    val zoomMin: Float?,
    val zoomMax: Float?,
    /** First result to last, by arrival. */
    val spanMs: Double?,
)

/**
 * Decides, per result, whether its frame line is logged and whether the active physical camera switched.
 *
 * - Preview frames are rate-limited to [MAX_PREVIEW_LINES_PER_SECOND] lines a second: a frame is logged when at least
 *   [minIntervalNanos] have passed (by arrival) since the last preview frame logged.
 * - Every still is logged, outside the limit (dispatch: "plus one for every still capture").
 * - A switch is reported for every change of active physical camera, outside the limit, whether or not that frame's own
 *   line is logged. A result that names no physical camera neither causes a switch nor resets the comparison.
 *
 * Called from one callback thread and read at stop from another, so every method is synchronized.
 */
internal class SwitchDetector(private val minIntervalNanos: Long = MIN_INTERVAL_NANOS) {
    private var lastWithId: FrameReading? = null
    private var lastLoggedPreviewNanos: Long? = null
    private var framesSeen = 0
    private var previewLogged = 0
    private var previewSkipped = 0
    private var stills = 0
    private var switches = 0
    private val byId = LinkedHashMap<String, Int>()
    private var withoutId = 0
    private var firstId: String? = null
    private var zoomMin: Float? = null
    private var zoomMax: Float? = null
    private var firstArrival: Long? = null
    private var lastArrival: Long? = null

    @Synchronized
    fun observe(reading: FrameReading): Observation {
        framesSeen++
        if (firstArrival == null) firstArrival = reading.arrivalNanos
        lastArrival = reading.arrivalNanos
        reading.zoomRatio?.let { z ->
            zoomMin = zoomMin?.let { minOf(it, z) } ?: z
            zoomMax = zoomMax?.let { maxOf(it, z) } ?: z
        }

        val logFrame = if (reading.still) {
            stills++
            true
        } else {
            val last = lastLoggedPreviewNanos
            if (last == null || reading.arrivalNanos - last >= minIntervalNanos) {
                lastLoggedPreviewNanos = reading.arrivalNanos
                previewLogged++
                true
            } else {
                previewSkipped++
                false
            }
        }

        val id = reading.activePhysicalId
        var switch: SwitchEvent? = null
        if (id == null) {
            withoutId++
        } else {
            byId[id] = (byId[id] ?: 0) + 1
            if (firstId == null) firstId = id
            val previous = lastWithId
            val previousId = previous?.activePhysicalId
            if (previous != null && previousId != null && previousId != id) {
                switches++
                switch = SwitchEvent(from = previousId, to = id, before = previous, after = reading)
            }
            lastWithId = reading
        }
        return Observation(logFrame = logFrame, switch = switch)
    }

    @Synchronized
    fun summary(): WatchSummary {
        val first = firstArrival
        val last = lastArrival
        return WatchSummary(
            framesSeen = framesSeen,
            previewFramesLogged = previewLogged,
            previewFramesSkipped = previewSkipped,
            stills = stills,
            switches = switches,
            framesByPhysicalId = LinkedHashMap(byId),
            framesWithoutId = withoutId,
            firstPhysicalId = firstId,
            lastPhysicalId = lastWithId?.activePhysicalId,
            zoomMin = zoomMin,
            zoomMax = zoomMax,
            spanMs = if (first != null && last != null) (last - first) / 1_000_000.0 else null,
        )
    }

    companion object {
        const val MAX_PREVIEW_LINES_PER_SECOND = 5
        const val MIN_INTERVAL_NANOS = 1_000_000_000L / MAX_PREVIEW_LINES_PER_SECOND
    }
}

/** The zoom range CameraX reports for the dialog's camera. */
internal data class ZoomRange(val min: Float, val max: Float)

internal sealed interface ZoomClamp {
    /** [applied] is [requested] brought inside the range; [clamped] says whether that changed it. */
    data class Valid(val requested: Float, val applied: Float, val clamped: Boolean) : ZoomClamp

    /** Not a ratio at all (not a finite number above 0); nothing is applied. */
    data class Rejected(val requested: Float, val reason: String) : ZoomClamp
}

/** One step of the list; a step outside the range is skipped and logged as such, never moved to the nearest end. */
internal data class ZoomStep(val index: Int, val requested: Float, val inRange: Boolean)

internal object ZoomPlan {
    /** The dispatch's list: "0.6, 1, 1.5, 2, 2.9, 3, 3.1, 5, 9.9, 10, 10.1, 20, 30, 100, as far as the range allows". */
    val DEFAULT_STEPS: List<Float> = listOf(0.6f, 1f, 1.5f, 2f, 2.9f, 3f, 3.1f, 5f, 9.9f, 10f, 10.1f, 20f, 30f, 100f)

    /** "holding each step for a set time (default 3 s)". */
    const val DEFAULT_STEP_HOLD_MILLIS = 3_000L

    /** A single set holds this long, then the zoom goes back to 1.0, so no command can leave the camera zoomed. */
    const val DEFAULT_SET_HOLD_MILLIS = 15_000L

    const val MIN_HOLD_MILLIS = 500L
    const val MAX_HOLD_MILLIS = 120_000L

    fun clamp(requested: Float, range: ZoomRange): ZoomClamp {
        if (!requested.isFinite() || requested <= 0f) {
            return ZoomClamp.Rejected(requested, "a zoom ratio is a finite number above 0; $requested is not")
        }
        val applied = requested.coerceIn(range.min, range.max)
        return ZoomClamp.Valid(requested = requested, applied = applied, clamped = applied != requested)
    }

    fun steps(requested: List<Float>, range: ZoomRange): List<ZoomStep> =
        requested.mapIndexed { index, ratio -> ZoomStep(index, ratio, ratio.isFinite() && ratio >= range.min && ratio <= range.max) }

    /** The hold asked for, kept between [MIN_HOLD_MILLIS] and [MAX_HOLD_MILLIS]; [default] when none was asked for. */
    fun hold(requested: Long?, default: Long): Long = (requested ?: default).coerceIn(MIN_HOLD_MILLIS, MAX_HOLD_MILLIS)
}

/** What the characteristics say about one camera, logical or physical. [error] when they could not be read. */
internal data class LensFacts(
    val cameraId: String,
    /** Whether `getCameraIdList` lists it: false for a physical camera usable only through its logical camera. */
    val inCameraIdList: Boolean?,
    val focalLengths: List<Float>,
    /** Width and height in millimetres. */
    val physicalSizeMm: List<Float>?,
    val cfa: Int?,
    val pixelArray: String?,
    val rawSizes: List<String>,
    val capabilities: List<Int>,
    val error: String? = null,
)

internal sealed interface MainWideChoice {
    data class Chosen(val cameraId: String, val reason: String) : MainWideChoice
    data class NotFound(val reason: String) : MainWideChoice
}

/**
 * Which physical camera is the main wide, read from characteristics, never hard-coded (dispatch, Part 3).
 *
 * The platform documents that a logical camera's static sensor and lens metadata "describe the default active physical
 * camera" (CameraMetadata, REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA; READ). So the main wide is the one
 * physical camera whose focal length, sensor physical size and colour filter arrangement are the logical camera's own.
 * On both test phones that is physical camera 5 (the dumpsys captures, READ); none other matches on any of the three.
 * No match, or more than one, is reported as not found with every candidate's facts, never guessed.
 */
internal object MainWideChooser {
    fun choose(logical: LensFacts, physicals: List<LensFacts>): MainWideChoice {
        val focal = logical.focalLengths.firstOrNull()
        val size = logical.physicalSizeMm
        if (logical.error != null || focal == null || size == null || size.size != 2 || logical.cfa == null) {
            return MainWideChoice.NotFound(
                "the logical camera ${logical.cameraId}'s own facts are incomplete (focal ${logical.focalLengths}, size $size, " +
                    "CFA ${logical.cfa}, error ${logical.error}), so nothing can be matched to them",
            )
        }
        val matches = physicals.filter { p ->
            val pSize = p.physicalSizeMm.orEmpty()
            p.error == null &&
                p.focalLengths.any { close(it, focal) } &&
                pSize.size == 2 && close(pSize[0], size[0]) && close(pSize[1], size[1]) &&
                p.cfa == logical.cfa
        }
        val candidates = physicals.joinToString("; ") { p ->
            "${p.cameraId}: focal ${p.focalLengths}, size ${p.physicalSizeMm}, CFA ${p.cfa}" + (p.error?.let { ", unreadable: $it" } ?: "")
        }
        val wanted = "focal $focal mm, size ${size[0]}x${size[1]} mm, CFA ${logical.cfa}"
        return when (matches.size) {
            1 -> MainWideChoice.Chosen(
                matches.single().cameraId,
                "the one physical camera with the logical camera's own $wanted (the default active physical camera); candidates: $candidates",
            )
            0 -> MainWideChoice.NotFound("no physical camera has the logical camera's $wanted; candidates: $candidates")
            else -> MainWideChoice.NotFound(
                "${matches.size} physical cameras (${matches.joinToString { it.cameraId }}) have the logical camera's $wanted; none is chosen",
            )
        }
    }

    /** Within 0.01: the characteristics are floats from one table, so they match exactly in practice; this only absorbs rounding. */
    private fun close(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) < 0.01f
}
