package com.zynergylabs.forager.app.diagnostics.sensor

/**
 * The sensor watch's log lines (dispatch 2026-10-15-12). One JSON object per line under [TAG], so
 * `adb logcat -v raw -s SensorWatch` prints exactly these lines. Each has a `kind`. A reading not made is `null`, never 0.
 */
internal object SensorWatchLines {
    const val TAG = "SensorWatch"

    fun watchLine(state: String, reason: String?, cameraId: String?, dialog: List<String>, notes: List<String>): String = TODO("built in the next commit")

    fun frameLine(reading: FrameReading): String = TODO("built in the next commit")

    fun switchLine(event: SwitchEvent): String = TODO("built in the next commit")

    fun summaryLine(summary: WatchSummary, reason: String, notes: List<String>): String = TODO("built in the next commit")

    fun zoomLine(
        mode: String,
        index: Int?,
        requested: Float?,
        applied: Float?,
        clamped: Boolean?,
        range: ZoomRange?,
        outcome: String,
        reason: String?,
        settled: FrameReading?,
        settledAgeMs: Double?,
        switchesDuringHold: Int?,
    ): String = TODO("built in the next commit")

    fun zoomEndLine(mode: String, returnedToOne: Boolean, reason: String?): String = TODO("built in the next commit")

    fun busyLine(asked: String, reason: String): String = TODO("built in the next commit")

    fun pinStartLine(attempts: List<PinAttempt>, burstFrames: Int): String = TODO("built in the next commit")

    fun lensLine(role: String, facts: LensFacts): String = TODO("built in the next commit")

    fun choiceLine(mainWideId: String?, reason: String): String = TODO("built in the next commit")

    fun pinBorrowLine(borrow: PinBorrow): String = TODO("built in the next commit")

    fun pinFrameLine(attempt: PinAttempt, frame: PinFrame): String = TODO("built in the next commit")

    fun pinAttemptLine(result: PinAttemptResult, verdict: String): String = TODO("built in the next commit")

    fun pinSkippedLine(attempt: PinAttempt, reason: String): String = TODO("built in the next commit")

    fun pinHandbackLine(handback: PinHandback): String = TODO("built in the next commit")

    fun pinEndLine(completed: Boolean, reason: String?, verdicts: Map<String, String>): String = TODO("built in the next commit")

    /** Exposure value at ISO 100, from the result's aperture, exposure time and ISO: log2(N²/t) − log2(ISO/100). */
    fun ev100(aperture: Float?, exposureTimeNs: Long?, iso: Int?): Double? = TODO("built in the next commit")

    fun captureIntentName(intent: Int): String = TODO("built in the next commit")

    fun cfaName(cfa: Int): String = TODO("built in the next commit")
}
