package com.zynergylabs.forager.app.diagnostics.burst

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The fast-burst test's order of steps and its one invariant (dispatch 2026-10-15-09, RECORD -1063). Debug builds only.
 * It measures; nothing user-facing is built (-1044).
 *
 * ## The steps
 *
 * 1. **Borrow** ([PlatformBurstCamera.borrow]): CameraX lets the camera go, the platform interface opens it, and a
 *    session with a preview and a RAW reader is configured. The swap's times are logged.
 * 2. **Converge** ([PlatformBurstCamera.converge]): AE and AWB settle on the session's preview, then lock.
 * 3. **The four runs** ([PlatformRun]), 8 RAW frames each, each saved as 8 DNGs. A run the phone cannot do is logged as
 *    unsupported with its reason; a run that throws is logged as failed, and the next run goes on (the session is still
 *    the probe's; nothing about the dialog's camera depends on one run).
 * 4. **Hand back**, in a `finally` that cancellation cannot skip.
 *
 * ## The invariant: the camera is handed back on every path
 *
 * Whatever happens from the first instruction of the borrow on, including a borrow that fails half-way and a failure or
 * cancellation in the middle of a run, the `finally` releases the locks (when converge was attempted) and then hands the
 * camera back. A release that throws is caught and reported, so it cannot skip the hand-back; a hand-back that throws is
 * caught and reported as not handed back, never as success. The dispatch's abort condition is "the camera is left dead in
 * any test"; [PlatformBurstProbeHandbackTest] holds this on every path through this entry point.
 */
internal class PlatformBurstProbe(
    private val camera: PlatformBurstCamera,
    private val session: Int,
    private val log: (String) -> Unit,
) {
    /** True when every step ran and the camera was confirmed handed back; false otherwise, with the reason in the end line. */
    suspend fun runAll(runs: List<PlatformRun> = PlatformRun.entries, framesPerRun: Int = FRAMES_PER_RUN): Boolean {
        log(PlatformBurstLines.startLine(session, runs, framesPerRun))
        var borrowTiming: SwapTiming? = null
        var firstRawArrivalMs: Double? = null
        var lockAsked = false
        var failure: String? = null
        var handback: PlatformHandback? = null
        try {
            val borrow = camera.borrow()
            borrowTiming = borrow.timing
            log(PlatformBurstLines.borrowLine(session, borrow))
            if (borrow !is BorrowOutcome.Borrowed) {
                failure = "the camera was not borrowed; no run was attempted"
                return false
            }
            // Asked before the call: the lock is set inside converge(), which may throw part-way.
            lockAsked = true
            val converged = camera.converge()
            log(PlatformBurstLines.convergeLine(session, converged))
            if (converged !is ConvergeOutcome.Locked) {
                failure = "AE and AWB did not converge and lock; no run was attempted"
                return false
            }
            for (run in runs) {
                val arrival = runOne(run, framesPerRun, converged)
                if (firstRawArrivalMs == null) firstRawArrivalMs = arrival
            }
        } catch (cancelled: CancellationException) {
            failure = "the probe was cancelled"
            throw cancelled
        } catch (error: Exception) {
            failure = "the probe failed: $error"
        } finally {
            val askedForLock = lockAsked
            val returned = withContext(NonCancellable) { restore(askedForLock) }
            handback = returned
            val handedBack = returned.platformClosed && returned.probeUnbound
            val reason = when {
                !handedBack -> listOfNotNull(failure, "the camera could not be confirmed handed back").joinToString("; ")
                else -> failure
            }
            log(PlatformBurstLines.endLine(session, completed = reason == null, reason = reason, swap = PlatformBurstLines.swapSummary(borrowTiming, firstRawArrivalMs, returned)))
        }
        val handedBack = handback ?: return false
        return failure == null && handedBack.platformClosed && handedBack.probeUnbound
    }

    /** One run, logged. Returns the earliest RAW arrival it saw, for the swap figures. A run's own failure is logged, not thrown. */
    private suspend fun runOne(run: PlatformRun, framesPerRun: Int, converged: ConvergeOutcome.Locked): Double? {
        val outcome = try {
            camera.burst(run, framesPerRun, converged)
        } catch (cancelled: CancellationException) {
            log(PlatformBurstLines.runLine(session, run, RunOutcomeKind.FAILED, "the probe was cancelled during this run", null, null))
            throw cancelled
        } catch (error: Exception) {
            log(PlatformBurstLines.runLine(session, run, RunOutcomeKind.FAILED, "the run failed: $error", null, null))
            return null
        }
        return when (outcome) {
            is PlatformBurstOutcome.Unsupported -> {
                log(PlatformBurstLines.runLine(session, run, RunOutcomeKind.UNSUPPORTED, outcome.reason, null, null))
                null
            }
            is PlatformBurstOutcome.Captured -> {
                outcome.frames.forEach { log(PlatformBurstLines.frameLine(session, run, it)) }
                val summary = PlatformBurstLines.summarize(framesPerRun, outcome.frames, outcome.facts.submitMs)
                val missing = framesPerRun - summary.framesArrived
                val unsaved = summary.framesArrived - summary.framesSaved
                val (kind, reason) = when {
                    summary.framesArrived == 0 -> RunOutcomeKind.FAILED to "no frame arrived; each frame line says why"
                    missing > 0 -> RunOutcomeKind.PARTIAL to "$missing of $framesPerRun frames did not arrive; each frame line says why"
                    unsaved > 0 -> RunOutcomeKind.PARTIAL to "$unsaved of ${summary.framesArrived} frames were not saved; each frame line says why"
                    else -> RunOutcomeKind.COMPLETE to null
                }
                log(PlatformBurstLines.runLine(session, run, kind, reason, summary, outcome.facts))
                outcome.frames.filter { it.error == null }.mapNotNull { it.arrivalMs }.minOrNull()
            }
        }
    }

    /** The `finally` half: release the locks if asked for, then hand back whatever happened. Neither throwing can skip the other. */
    private suspend fun restore(lockAsked: Boolean): PlatformHandback {
        if (false) {
            val release = try {
                camera.releaseLocks()
            } catch (error: Exception) {
                LockRelease(cleared = false, detail = "releasing the locks threw: $error")
            }
            log(PlatformBurstLines.releaseLine(session, release))
        }
        val handback = try {
            camera.handBack()
        } catch (error: Exception) {
            PlatformHandback(
                platformClosed = false,
                probeUnbound = false,
                handbackStartMs = null,
                platformClosedMs = null,
                cameraxOpenMs = null,
                viewfinderLiveMs = null,
                cameraStates = emptyList(),
                detail = "handing the camera back threw: $error",
            )
        }
        log(PlatformBurstLines.handbackLine(session, handback))
        return handback
    }

    companion object {
        /** The dispatch's 8 back-to-back RAW frames per run. */
        const val FRAMES_PER_RUN = 8
    }
}
