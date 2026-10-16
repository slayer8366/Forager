package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    // Before [log], so a trailing log lambda still binds.
    private val attemptTimeoutMillis: Long = ATTEMPT_TIMEOUT_MILLIS,
    private val handBackTimeoutMillis: Long = HANDBACK_TIMEOUT_MILLIS,
    private val log: (String) -> Unit,
) {
    /**
     * Surveys, borrows, makes the three attempts in order, and hands back. True when the borrow succeeded and the camera
     * was confirmed handed back; an attempt that fails is a finding, logged, and does not make the run false.
     */
    suspend fun runAll(burstFrames: Int = BURST_FRAMES): Boolean {
        log(SensorWatchLines.pinStartLine(PinAttempt.entries, burstFrames))
        val survey = try {
            camera.survey()
        } catch (cancelled: CancellationException) {
            log(SensorWatchLines.pinEndLine(false, "the probe was cancelled during the survey; nothing was borrowed", emptyMap()))
            throw cancelled
        } catch (failure: Exception) {
            log(SensorWatchLines.pinEndLine(false, "the survey failed, so nothing was borrowed: $failure", emptyMap()))
            return false
        }
        val (logical, physicals) = when (survey) {
            is PinSurvey.Ready -> survey.logical to survey.physicals
            is PinSurvey.Unsupported -> survey.logical to survey.physicals
        }
        logical?.let { log(SensorWatchLines.lensLine("logical", it)) }
        physicals.forEach { log(SensorWatchLines.lensLine("physical", it)) }
        when (survey) {
            is PinSurvey.Ready -> log(SensorWatchLines.choiceLine(survey.mainWideId, survey.reason))
            is PinSurvey.Unsupported -> log(SensorWatchLines.choiceLine(null, survey.reason))
        }

        val verdicts = LinkedHashMap<String, String>()
        var borrowed = false
        var failure: String? = null
        var handback: PinHandback? = null
        try {
            val borrow = camera.borrow()
            log(SensorWatchLines.pinBorrowLine(borrow))
            if (borrow is PinBorrow.Failed) {
                failure = "the camera was not borrowed: ${borrow.reason}"
                return false
            }
            borrowed = true
            for (attempt in PinAttempt.entries) {
                val ready = survey as? PinSurvey.Ready
                // The control needs no main wide, so it runs even when none was found; the other two cannot.
                val target = ready ?: if (attempt == PinAttempt.LOGICAL_CONTROL) controlOnly(survey) else null
                if (target == null) {
                    val reason = "no main wide physical camera was found: ${(survey as PinSurvey.Unsupported).reason}"
                    log(SensorWatchLines.pinSkippedLine(attempt, reason))
                    verdicts[attempt.id] = "skipped"
                    continue
                }
                verdicts[attempt.id] = runOne(attempt, target, ready?.mainWideId, burstFrames)
            }
        } catch (cancelled: CancellationException) {
            failure = "the probe was cancelled"
            throw cancelled
        } catch (error: Exception) {
            failure = "the probe failed: $error"
        } finally {
            // Bounded (dispatch 2026-10-16-01): a hand-back that hangs is reported as not handed back, never waited on for ever.
            val returned = withContext(NonCancellable) {
                try {
                    withTimeout(handBackTimeoutMillis) { camera.handBack() }
                } catch (timeout: TimeoutCancellationException) {
                    PinHandback(platformClosed = false, cameraxBack = false, detail = "the hand-back did not finish within $handBackTimeoutMillis ms: the dialog's camera may be dead")
                } catch (error: Exception) {
                    PinHandback(platformClosed = false, cameraxBack = false, detail = "handing the camera back threw: $error")
                }
            }
            handback = returned
            log(SensorWatchLines.pinHandbackLine(returned))
            val handedBack = returned.platformClosed && returned.cameraxBack
            val reason = listOfNotNull(failure, if (!handedBack) "the camera could not be confirmed handed back" else null)
                .joinToString("; ").ifEmpty { null }
            log(SensorWatchLines.pinEndLine(completed = reason == null, reason = reason, verdicts = verdicts))
        }
        val returned = handback ?: return false
        return borrowed && failure == null && returned.platformClosed && returned.cameraxBack
    }

    /**
     * One attempt, logged frame by frame and as a whole. Its own failure is logged as its verdict, not thrown; one that
     * does not finish within [attemptTimeoutMillis] is cut off and logged `timed_out` (dispatch 2026-10-16-01: on the S26
     * the probe logged nothing after the refused direct open, RECORD -1079, and two of the platform attempt's waits had
     * no bound), so the next attempt and the hand-back still run.
     */
    private suspend fun runOne(attempt: PinAttempt, survey: PinSurvey.Ready, mainWideId: String?, burstFrames: Int): String {
        val result = try {
            camera.attempt(attempt, survey, burstFrames)
        } catch (timeout: TimeoutCancellationException) {
            val cut = PinAttemptResult(
                attempt, null, null, null, null, null, emptyList(),
                "the attempt did not finish within $attemptTimeoutMillis ms and was cut off", emptyList(),
            )
            log(SensorWatchLines.pinAttemptLine(cut, TIMED_OUT))
            return TIMED_OUT
        } catch (cancelled: CancellationException) {
            val cut = PinAttemptResult(attempt, null, null, null, null, null, emptyList(), "the probe was cancelled during this attempt", emptyList())
            log(SensorWatchLines.pinAttemptLine(cut, "failed"))
            throw cancelled
        } catch (error: Exception) {
            PinAttemptResult(attempt, null, null, null, null, null, emptyList(), "the attempt failed: $error", emptyList())
        }
        result.frames.forEach { log(SensorWatchLines.pinFrameLine(attempt, it)) }
        val verdict = verdict(result, mainWideId)
        log(SensorWatchLines.pinAttemptLine(result, verdict))
        return verdict
    }

    /** The control attempt's survey when no main wide was found: the logical camera alone, with no main wide to compare to. */
    private fun controlOnly(survey: PinSurvey): PinSurvey.Ready? {
        val unsupported = survey as? PinSurvey.Unsupported ?: return null
        val logical = unsupported.logical ?: return null
        return PinSurvey.Ready(logical, unsupported.physicals, mainWideId = "", reason = unsupported.reason)
    }

    companion object {
        /** "Take one RAW frame and a short burst." */
        const val BURST_FRAMES = 4

        /** One attempt's ceiling (dispatch 2026-10-16-01: "a per-attempt timeout, so no attempt can hang the probe"). */
        const val ATTEMPT_TIMEOUT_MILLIS = 45_000L

        /** The hand-back's ceiling, above its own bounded waits. */
        const val HANDBACK_TIMEOUT_MILLIS = 30_000L

        /** The verdict of an attempt the probe cut off. */
        const val TIMED_OUT = "timed_out"

        /**
         * One attempt in one word, for the planner's table: `main_wide` when every frame that arrived is attributed to
         * [mainWideId]; `other_sensor` when any is attributed elsewhere; `unattributed` when frames arrived but none says
         * where from; `no_frames` when the session ran but no frame arrived; `failed` when it ended in an error with no frame.
         */
        fun verdict(result: PinAttemptResult, mainWideId: String?): String {
            val arrived = result.frames.filter { it.error == null }
            if (arrived.isEmpty()) return if (result.error != null && result.frames.isEmpty()) "failed" else "no_frames"
            val attributed = arrived.mapNotNull { it.attributedTo }
            return when {
                attributed.isEmpty() -> "unattributed"
                mainWideId.isNullOrEmpty() -> "no_main_wide_to_compare"
                attributed.any { it != mainWideId } -> "other_sensor"
                attributed.size < arrived.size -> "main_wide_partly_unattributed"
                else -> "main_wide"
            }
        }
    }
}
