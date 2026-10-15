package com.zynergylabs.forager.app.diagnostics.burst

/*
 * STUB (dispatch 2026-10-15-09, tests first): runs nothing and hands nothing back, so the hand-back tests fail on CI for
 * their stated reason before the real order of steps is written.
 */
internal class PlatformBurstProbe(
    private val camera: PlatformBurstCamera,
    private val session: Int,
    private val log: (String) -> Unit,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun runAll(runs: List<PlatformRun> = PlatformRun.entries, framesPerRun: Int = FRAMES_PER_RUN): Boolean = true

    companion object {
        const val FRAMES_PER_RUN = 8
    }
}
