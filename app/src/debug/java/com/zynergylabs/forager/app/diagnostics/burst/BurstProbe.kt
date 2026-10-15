package com.zynergylabs.forager.app.diagnostics.burst

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * RAW stage 3, piece 1 (dispatch 2026-10-15-06, RECORD -1048): the burst-rate and lock probe's run order and its one
 * invariant. Debug builds only. It measures; it does not build the burst function the engine will use (piece 2, -1044's
 * "do one at a time").
 *
 * ## The invariant: the camera goes back exactly as it was, on every path
 *
 * Each run borrows the camera dialog's camera ([BurstProbeCamera.bind]), optionally locks AE and AWB, captures, and then
 * in a `finally` that cancellation cannot skip releases the lock (when it asked for one) and hands the camera back
 * ([BurstProbeCamera.release]). The probe never touches the dialog's own use cases, so once they are active again the
 * user's chosen format is the one they were bound with (see `CameraXBurstProbeCamera` for what CameraX does, read from
 * the 1.6.2 artifacts).
 *
 * A run that fails is logged with its reason and the next run goes on, because each run restores before the next
 * starts. A hand-back that cannot be confirmed (the probe's use cases still read as bound, or the lock not read back as
 * cleared) stops the probe: borrowing the camera again from a state the probe cannot vouch for is how a probe leaves the
 * camera dead or in the wrong format, which the dispatch names as an abort condition.
 */
internal class BurstProbe(
    private val camera: BurstProbeCamera,
    private val session: Int,
    private val log: (String) -> Unit,
) {
    /** Runs [runs] in order. True when every run handed the camera back; false when the probe stopped early. */
    suspend fun runAll(runs: List<ProbeRun> = ProbeRun.entries, framesPerRun: Int = FRAMES_PER_RUN): Boolean {
        log(BurstProbeLines.startLine(session, runs, framesPerRun, LOCK_TIMEOUT_MILLIS, FRAME_TIMEOUT_MILLIS))
        for (run in runs) {
            if (!runOne(run, framesPerRun)) {
                val reason = "the camera could not be confirmed handed back after ${run.id}; the remaining runs were not attempted"
                log(BurstProbeLines.endLine(session, completed = false, reason = reason))
                return false
            }
        }
        log(BurstProbeLines.endLine(session, completed = true, reason = null))
        return true
    }

    /** What one run recorded, filled in as it goes, so the `finally` that logs it sees however far it got. */
    private class RunRecord {
        var outcome = RunOutcomeKind.FAILED
        var reason: String? = "the run ended before it recorded an outcome"
        var lockAsked = false
        var lock: LockOutcome? = null
        var capture: BurstCapture? = null
    }

    /** One run. Returns whether the camera was confirmed handed back (and, for a locked run, the lock released). */
    private suspend fun runOne(run: ProbeRun, framesPerRun: Int): Boolean {
        val before = camera.memory()
        val record = RunRecord()
        var handedBack = false
        try {
            attempt(run, framesPerRun, record)
        } catch (cancelled: CancellationException) {
            record.outcome = RunOutcomeKind.FAILED
            record.reason = "the probe was cancelled during this run"
            throw cancelled
        } catch (failure: Exception) {
            record.outcome = RunOutcomeKind.FAILED
            record.reason = "the run failed: $failure"
        } finally {
            handedBack = withContext(NonCancellable) { restore(run, framesPerRun, record, before) }
        }
        return handedBack
    }

    /** Bind, lock when the run is locked, capture. Each early return leaves its outcome and reason in [record]. */
    private suspend fun attempt(run: ProbeRun, framesPerRun: Int, record: RunRecord) {
        when (val bind = camera.bind(run.output)) {
            is BindOutcome.Unsupported -> {
                record.outcome = RunOutcomeKind.UNSUPPORTED
                record.reason = bind.reason
                return
            }
            is BindOutcome.Failed -> {
                record.outcome = RunOutcomeKind.FAILED
                record.reason = bind.reason
                return
            }
            is BindOutcome.Bound -> Unit
        }
        if (run.locked) {
            // Asked before the call, not after it returns: the options are set inside lock(), so a lock() that throws
            // or is cancelled part-way may already have set them, and the release must still run.
            record.lockAsked = true
            val lock = camera.lock(LOCK_TIMEOUT_MILLIS)
            record.lock = lock
            if (lock is LockOutcome.NotReached) {
                record.outcome = RunOutcomeKind.FAILED
                record.reason = "AE/AWB lock not reached: ${lock.reason}"
                return
            }
        }
        val burst = camera.capture(run.output, framesPerRun, FRAME_TIMEOUT_MILLIS)
        record.capture = burst
        burst.frames.forEach { frame -> log(BurstProbeLines.frameLine(session, run, frame)) }
        val missing = framesPerRun - burst.frames.count { it.error == null && it.sensorTimestampNs != null }
        if (missing == 0) {
            record.outcome = RunOutcomeKind.COMPLETE
            record.reason = null
        } else {
            record.outcome = RunOutcomeKind.PARTIAL
            record.reason = "$missing of $framesPerRun frames did not arrive; each frame line says why"
        }
    }

    /**
     * The `finally` half: release the lock if one was asked for, hand the camera back whatever happened, and log the run
     * and the hand-back. A release or hand-back that throws is caught here and reported as not confirmed, so one failing
     * cannot skip the other.
     */
    private suspend fun restore(run: ProbeRun, framesPerRun: Int, record: RunRecord, before: MemoryReading): Boolean {
        val release = if (record.lockAsked) {
            try {
                camera.unlock()
            } catch (failure: Exception) {
                LockRelease(cleared = false, detail = "releasing the lock threw: $failure")
            }
        } else {
            null
        }
        val handback = try {
            camera.release()
        } catch (failure: Exception) {
            Handback(probeUnbound = false, detail = "handing the camera back threw: $failure")
        }
        val after = camera.memory()
        val capture = record.capture
        val summary = capture?.let { BurstProbeLines.summarize(run, framesPerRun, it.frames) }
        log(BurstProbeLines.runLine(session, run, record.outcome, record.reason, record.lock, summary, capture?.facts, before, after))
        log(BurstProbeLines.handbackLine(session, run, release, handback))
        return handback.probeUnbound && (release == null || release.cleared)
    }

    companion object {
        /** The dispatch's 8 back-to-back captures per run. */
        const val FRAMES_PER_RUN = 8

        /**
         * How long the lock may take to read `LOCKED`. The lock is a request setting, and a result reflects it after at
         * most the camera's pipeline depth (`REQUEST_PIPELINE_MAX_DEPTH`, logged per run; at most 8 on a conforming
         * device). At 15 preview frames a second, a dim scene, 3 s is 45 results: over five times that depth. A lock
         * that has not landed by then is reported as not reached rather than waited on.
         */
        const val LOCK_TIMEOUT_MILLIS = 3_000L

        /**
         * How long the probe waits per frame for the whole burst: 8 frames get 32 s from the first submit. The stage 2
         * logs show 0.3 to 0.5 s a frame (research audit §3.2); a frame that has not arrived by then is logged as never
         * arrived, never padded.
         */
        const val FRAME_TIMEOUT_MILLIS = 4_000L
    }
}
