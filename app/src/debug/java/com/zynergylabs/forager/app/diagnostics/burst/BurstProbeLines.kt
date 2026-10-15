package com.zynergylabs.forager.app.diagnostics.burst

/** STUB (tests-first commit): not built yet. */
internal object BurstProbeLines {
    const val TAG = "BurstProbe"

    fun frameLine(session: Int, run: ProbeRun, frame: ProbeFrame): String = "{}"

    fun runLine(
        session: Int,
        run: ProbeRun,
        outcome: RunOutcomeKind,
        reason: String?,
        lock: LockOutcome?,
        summary: RunSummary?,
        facts: ProbeRunFacts?,
        before: MemoryReading,
        after: MemoryReading,
    ): String = "{}"

    fun handbackLine(session: Int, run: ProbeRun, release: LockRelease?, handback: Handback): String = "{}"

    fun startLine(session: Int, runs: List<ProbeRun>, framesPerRun: Int, lockTimeoutMillis: Long, frameTimeoutMillis: Long): String = "{}"

    fun endLine(session: Int, completed: Boolean, reason: String?): String = "{}"

    fun summarize(run: ProbeRun, framesExpected: Int, frames: List<ProbeFrame>): RunSummary =
        RunSummary(framesExpected, 0, emptyList(), null, null, null, null, null, null, 0, 0, 0, null, null, null, null, null, null)
}
