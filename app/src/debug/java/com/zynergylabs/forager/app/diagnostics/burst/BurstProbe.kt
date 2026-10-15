package com.zynergylabs.forager.app.diagnostics.burst

/** STUB (tests-first commit): runs each step in order and nothing else. No lock release, no hand-back. */
internal class BurstProbe(
    private val camera: BurstProbeCamera,
    private val session: Int,
    private val log: (String) -> Unit,
) {
    suspend fun runAll(runs: List<ProbeRun> = ProbeRun.entries, framesPerRun: Int = FRAMES_PER_RUN): Boolean {
        for (run in runs) {
            try {
                if (camera.bind(run.output) !is BindOutcome.Bound) continue
                if (run.locked && camera.lock(LOCK_TIMEOUT_MILLIS) !is LockOutcome.Reached) continue
                camera.capture(run.output, framesPerRun, FRAME_TIMEOUT_MILLIS)
            } catch (e: Exception) {
                log("{}")
            }
        }
        return true
    }

    companion object {
        const val FRAMES_PER_RUN = 8
        const val LOCK_TIMEOUT_MILLIS = 3_000L
        const val FRAME_TIMEOUT_MILLIS = 4_000L
    }
}
