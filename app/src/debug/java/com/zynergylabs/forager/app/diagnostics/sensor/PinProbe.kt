package com.zynergylabs.forager.app.diagnostics.sensor

/** Part 3's three ways of getting RAW from the main wide sensor only, in the dispatch's order. */
internal enum class PinAttempt(val id: String) {
    /** Logical camera 0 with the RAW output's physical camera set to the main wide. */
    PHYSICAL_STREAM("physical_stream"),

    /** The main wide physical camera opened directly by its own ID. */
    PHYSICAL_DIRECT("physical_direct"),

    /** Logical camera 0 at zoom 1.0 with no physical stream: the control. */
    LOGICAL_CONTROL("logical_control"),
}

internal sealed interface PinSurvey {
    data class Ready(val logical: LensFacts, val physicals: List<LensFacts>, val mainWideId: String, val reason: String) : PinSurvey
    data class Unsupported(val reason: String, val logical: LensFacts?, val physicals: List<LensFacts>) : PinSurvey
}

internal sealed interface PinBorrow {
    data class Borrowed(val detail: List<String>) : PinBorrow
    data class Failed(val reason: String, val detail: List<String>) : PinBorrow
}

/** One RAW frame of an attempt. Nothing is saved; the image is read for its size and closed. */
internal data class PinFrame(
    val index: Int,
    /** `single` or `burst`. */
    val kind: String,
    val sensorTimestampNs: Long?,
    /** The logical result's `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID`; null when not reported (a physical camera opened alone has none). */
    val activePhysicalId: String?,
    /** The physical cameras the result carries per-physical results for. */
    val physicalResultIds: List<String>,
    /** The camera this RAW frame is attributed to: the physical stream's camera, else the active physical camera, else the camera opened. */
    val attributedTo: String?,
    val rawSize: String?,
    /** The colour filter arrangement of [attributedTo], from its characteristics. */
    val cfa: Int?,
    val error: String?,
)

internal data class PinAttemptResult(
    val attempt: PinAttempt,
    val openedId: String?,
    val physicalStreamId: String?,
    /** Whether the session configured; null when it was never tried (the open was refused, say). */
    val configured: Boolean?,
    /** What `isSessionConfigurationSupported` answered, or why it was not asked. */
    val sessionQuery: String?,
    val rawSize: String?,
    val frames: List<PinFrame>,
    val error: String?,
    val notes: List<String>,
)

internal data class PinHandback(val platformClosed: Boolean, val cameraxBack: Boolean, val detail: String?)

/** The platform side of Part 3. The real one is [PlatformPinCamera]; the tests drive a fake. Main thread. */
internal interface PinCamera {
    /** Reads characteristics only: the logical camera, its physical cameras, and which is the main wide. Touches no camera. */
    suspend fun survey(): PinSurvey

    /** CameraX lets the camera go. */
    suspend fun borrow(): PinBorrow

    /** One attempt, which opens and closes its own device. */
    suspend fun attempt(attempt: PinAttempt, survey: PinSurvey.Ready, burstFrames: Int): PinAttemptResult

    /** Every platform device closed, then CameraX given the camera back. */
    suspend fun handBack(): PinHandback
}

/**
 * Part 3's order and its invariant: whatever happens from the borrow on, the camera is handed back in a `finally` that
 * cancellation cannot skip, and a hand-back that throws is reported as not handed back, never as success.
 */
internal class PinProbe(
    private val camera: PinCamera,
    private val log: (String) -> Unit,
) {
    /** True when the camera was confirmed handed back. */
    suspend fun runAll(burstFrames: Int = BURST_FRAMES): Boolean = TODO("built in the next commit")

    companion object {
        /** "Take one RAW frame and a short burst." */
        const val BURST_FRAMES = 4

        /**
         * One attempt in one word, for the planner's table: `main_wide` when every frame that arrived is attributed to
         * [mainWideId]; `other_sensor` when any is attributed elsewhere; `unattributed` when frames arrived but none says
         * where from; `no_frames` when the session ran but no frame arrived; `failed` when it ended in an error with no frame.
         */
        fun verdict(result: PinAttemptResult, mainWideId: String?): String = TODO("built in the next commit")
    }
}
