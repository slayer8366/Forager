package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The platform side of the reach probe. The real one is [PlatformReachCamera]; the tests drive a fake. Main thread. */
internal interface ReachCamera {
    /** Reads characteristics only: every listed camera and every physical camera. Touches no camera. */
    suspend fun survey(): ReachSurvey

    /** CameraX lets the dialog's camera go. */
    suspend fun borrow(): PinBorrow

    /** One attempt, which opens and closes its own device. */
    suspend fun attempt(attempt: ReachAttempt, burstFrames: Int): ReachResult

    /** Every platform device closed, then CameraX given the camera back. */
    suspend fun handBack(): PinHandback
}

/**
 * The reach probe's order and its invariant (dispatch 2026-10-16-01): survey, plan, borrow, every planned attempt in
 * turn, hand back, summary. A refused, failed, thrown or hung attempt is a finding: it is logged with its verdict and
 * the next attempt still runs. Each attempt is cut off at [attemptTimeoutMillis]; the hand-back runs in a `finally`
 * that cancellation cannot skip and is itself bounded by [handBackTimeoutMillis], and one that throws or overruns is
 * reported as not handed back, never as success.
 */
internal class ReachProbe(
    private val camera: ReachCamera,
    private val attemptTimeoutMillis: Long = ATTEMPT_TIMEOUT_MILLIS,
    private val handBackTimeoutMillis: Long = HANDBACK_TIMEOUT_MILLIS,
    private val log: (String) -> Unit,
) {
    /** True when every planned attempt was made (whatever its verdict) and the camera was confirmed handed back. */
    suspend fun runAll(burstFrames: Int = BURST_FRAMES): Boolean {
        log(ReachLines.startLine(burstFrames, attemptTimeoutMillis))
        val survey = try {
            camera.survey()
        } catch (cancelled: CancellationException) {
            log(ReachLines.endLine(false, "the probe was cancelled during the survey; nothing was borrowed", emptyMap()))
            throw cancelled
        } catch (failure: Exception) {
            log(ReachLines.endLine(false, "the survey failed, so nothing was borrowed: $failure", emptyMap()))
            return false
        }
        val roles = ReachRoles.assign(survey)
        survey.cameras.forEach { log(ReachLines.cameraLine(it, roles[it.cameraId])) }
        val plan = ReachPlan.plan(survey)
        log(ReachLines.planLine(plan))
        if (plan.none { it is PlannedReach.Run }) {
            val why = if (survey.cameras.none { it.facing == "BACK" }) "this phone describes no back camera" else "no attempt could be planned"
            plan.filterIsInstance<PlannedReach.Skip>().forEach { log(ReachLines.skippedLine(it)) }
            log(ReachLines.endLine(false, "$why, so nothing was borrowed", plan.associate { it.label to ReachVerdict.NOT_ATTEMPTED }))
            return false
        }

        val outcomes = mutableListOf<ReachOutcome>()
        var failure: String? = null
        var handback: PinHandback? = null
        try {
            val borrow = camera.borrow()
            log(ReachLines.borrowLine(borrow))
            if (borrow is PinBorrow.Failed) {
                failure = "the camera was not borrowed: ${borrow.reason}"
                return false
            }
            for (step in plan) {
                outcomes += when (step) {
                    is PlannedReach.Skip -> {
                        log(ReachLines.skippedLine(step))
                        ReachOutcome(step, ReachVerdict.NOT_ATTEMPTED, null)
                    }
                    is PlannedReach.Run -> runOne(step, burstFrames)
                }
            }
        } catch (cancelled: CancellationException) {
            failure = "the probe was cancelled"
            throw cancelled
        } catch (error: Exception) {
            failure = "the probe failed: $error"
        } finally {
            val returned = withContext(NonCancellable) { boundedHandBack() }
            handback = returned
            log(ReachLines.handbackLine(returned))
            val rows = ReachSummary.rows(survey, roles, outcomes)
            rows.forEach { log(ReachLines.rowLine(it)) }
            log(ReachLines.tableLine(survey.device, ReachSummary.table(survey.device, rows)))
            val handedBack = returned.platformClosed && returned.cameraxBack
            val reason = listOfNotNull(failure, if (!handedBack) "the camera could not be confirmed handed back" else null)
                .joinToString("; ").ifEmpty { null }
            log(ReachLines.endLine(completed = reason == null, reason = reason, verdicts = outcomes.associate { it.planned.label to it.verdict }))
        }
        val returned = handback ?: return false
        return failure == null && returned.platformClosed && returned.cameraxBack
    }

    /**
     * One attempt, cut off at [attemptTimeoutMillis], and logged frame by frame and as a whole. A throw or a timeout is
     * this attempt's verdict, never the run's end; only a cancellation from outside ends the run (and the `finally`
     * above still hands back).
     */
    private suspend fun runOne(step: PlannedReach.Run, burstFrames: Int): ReachOutcome {
        val attempt = step.attempt
        val result = try {
            withTimeout(attemptTimeoutMillis) { camera.attempt(attempt, burstFrames) }
        } catch (timeout: TimeoutCancellationException) {
            empty(attempt, "the attempt did not finish within $attemptTimeoutMillis ms and was cut off", timedOut = true)
        } catch (cancelled: CancellationException) {
            log(ReachLines.attemptLine(empty(attempt, "the probe was cancelled during this attempt"), ReachVerdict.FAILED))
            throw cancelled
        } catch (error: IllegalArgumentException) {
            empty(attempt, "the attempt failed: $error")
        }
        result.frames.forEach { log(ReachLines.frameLine(attempt, it)) }
        val verdict = ReachVerdict.of(result)
        log(ReachLines.attemptLine(result, verdict))
        return ReachOutcome(step, verdict, result)
    }

    /** The hand-back, bounded; one that throws or overruns is not handed back. */
    private suspend fun boundedHandBack(): PinHandback = try {
        withTimeout(handBackTimeoutMillis) { camera.handBack() }
    } catch (timeout: TimeoutCancellationException) {
        PinHandback(platformClosed = false, cameraxBack = false, detail = "the hand-back did not finish within $handBackTimeoutMillis ms: the dialog's camera may be dead")
    } catch (error: Exception) {
        PinHandback(platformClosed = false, cameraxBack = false, detail = "handing the camera back threw: $error")
    }

    /**
     * A result for an attempt that returned nothing of its own. A throw is `failed`, a timeout `timed_out`; where either
     * stopped is not known, so it is marked `unknown` and the error says what happened.
     */
    private fun empty(attempt: ReachAttempt, error: String, timedOut: Boolean = false): ReachResult {
        val (opened, streams) = when (attempt) {
            is ReachAttempt.ThroughLogical -> attempt.logicalId to listOf(attempt.physicalId)
            is ReachAttempt.Direct -> attempt.cameraId to listOf(attempt.cameraId)
            is ReachAttempt.TwoStreams -> attempt.logicalId to listOf(attempt.first, attempt.second)
        }
        return ReachResult(
            attempt = attempt, openedId = opened, streamIds = streams, rawSize = null, opened = false, sessionQuery = null,
            configured = null, frames = emptyList(), burstAsked = 0, stoppedAt = ReachStage.UNKNOWN, error = error,
            notes = emptyList(), timedOut = timedOut,
        )
    }

    companion object {
        /** "1 single frame, and an 8-frame burst." */
        const val BURST_FRAMES = 8

        /**
         * One attempt's ceiling. The platform attempt bounds each of its own waits (open 5 s, configure 5 s, exposure
         * 3 s, the single frame 3 s, the burst 10 s, the close 3 s: 29 s in all); this is above that sum, so it fires
         * only when something the attempt did not bound has hung.
         */
        const val ATTEMPT_TIMEOUT_MILLIS = 45_000L

        /** The hand-back's ceiling, above its own bounded waits (pending opens 5 s, closes 3 s each, CameraX OPEN 5 s, the viewfinder 5 s). */
        const val HANDBACK_TIMEOUT_MILLIS = 30_000L
    }
}
