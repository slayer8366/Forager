package com.zynergylabs.forager.app.diagnostics.sensor

/**
 * The reach probe's log lines (dispatch 2026-10-16-01), under [SensorWatchLines.TAG] with the sensor watch's other lines,
 * one JSON object per line. Kinds: `reach_start`, `reach_camera` (one per camera ID), `reach_plan`, `reach_borrow`,
 * `reach_frame`, `reach_attempt`, `reach_handback`, `reach_row` (one per table row), `reach_table` (the plain per-phone
 * table in one line), `reach_end`. A reading not made is `null`, never 0.
 */
internal object ReachLines {
    fun startLine(burstFrames: Int, attemptTimeoutMillis: Long): String = TODO("stub")
    fun cameraLine(entry: CameraEntry, role: String?): String = TODO("stub")
    fun planLine(plan: List<PlannedReach>): String = TODO("stub")
    fun borrowLine(borrow: PinBorrow): String = TODO("stub")
    fun frameLine(attempt: ReachAttempt, frame: ReachFrame): String = TODO("stub")
    fun attemptLine(result: ReachResult, verdict: String): String = TODO("stub")
    fun skippedLine(skip: PlannedReach.Skip): String = TODO("stub")
    fun handbackLine(handback: PinHandback): String = TODO("stub")
    fun rowLine(row: ReachRow): String = TODO("stub")
    fun tableLine(device: String?, table: String): String = TODO("stub")
    fun endLine(completed: Boolean, reason: String?, verdicts: Map<String, String>): String = TODO("stub")
}
