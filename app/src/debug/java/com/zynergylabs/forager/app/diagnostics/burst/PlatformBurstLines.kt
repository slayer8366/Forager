package com.zynergylabs.forager.app.diagnostics.burst

/*
 * STUB (dispatch 2026-10-15-09, tests first): the signatures the tests compile against, with no behaviour, so each test
 * fails on CI for its own stated reason before the real lines, summaries and file names are written.
 */
internal object PlatformBurstLines {
    const val TAG = "PlatformBurst"
    const val DNG_WRITTEN = "written"

    fun startLine(session: Int, runs: List<PlatformRun>, framesPerRun: Int): String = "{}"
    fun borrowLine(session: Int, outcome: BorrowOutcome): String = "{}"
    fun convergeLine(session: Int, outcome: ConvergeOutcome): String = "{}"
    fun frameLine(session: Int, run: PlatformRun, frame: PlatformFrame): String = "{}"
    fun runLine(session: Int, run: PlatformRun, outcome: RunOutcomeKind, reason: String?, summary: PlatformRunSummary?, facts: PlatformRunFacts?): String = "{}"
    fun releaseLine(session: Int, release: LockRelease): String = "{}"
    fun handbackLine(session: Int, handback: PlatformHandback): String = "{}"
    fun endLine(session: Int, completed: Boolean, reason: String?, swap: SwapSummary?): String = "{}"
    fun busyLine(runningSession: Int): String = "{}"

    fun summarize(framesExpected: Int, frames: List<PlatformFrame>, submitMs: Double?): PlatformRunSummary =
        PlatformRunSummary(framesExpected, 0, emptyList(), null, null, null, null, null, null, null, 0, 0)

    fun swapSummary(borrow: SwapTiming?, firstRawArrivalMs: Double?, handback: PlatformHandback?): SwapSummary =
        SwapSummary(null, null, null, null, null)
}

internal data class SwapSummary(
    val triggerToPlatformOpenMs: Double?,
    val platformOpenToFirstRawMs: Double?,
    val triggerToFirstRawMs: Double?,
    val viewfinderNotLiveMs: Double?,
    val handbackToViewfinderLiveMs: Double?,
)

internal object PlatformBurstFiles {
    const val ROOT = "raw-burst-test"
    fun sessionFolder(startedAtEpochMillis: Long, session: Int): String = ""
    fun runFolder(sessionFolder: String, run: PlatformRun): String = ""
    fun frameFile(runFolder: String, index: Int): String = ""
}

internal fun rawCornerToCentre(samples: ShortArray, width: Int, height: Int, black: List<Float>): Double? = null
