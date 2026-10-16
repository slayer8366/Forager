package com.zynergylabs.forager.app.diagnostics.sensor

import com.zynergylabs.forager.app.diagnostics.sensor.SensorWatchLines.JsonLine

/**
 * The reach probe's log lines (dispatch 2026-10-16-01), under [SensorWatchLines.TAG] with the sensor watch's other lines,
 * one JSON object per line. Kinds: `reach_start`, `reach_camera` (one per camera ID), `reach_plan`, `reach_borrow`,
 * `reach_frame`, `reach_attempt` (one per planned step, made or not), `reach_handback`, `reach_row` (one per table row),
 * `reach_table` (the plain per-phone table in one line: print it with `jq -r .table`), `reach_end`. A reading not made
 * is `null`, never 0. A separate file from the pin probe's lines rather than an edit to them; the JSON writer is theirs.
 */
internal object ReachLines {
    fun startLine(burstFrames: Int, attemptTimeoutMillis: Long): String = JsonLine()
        .string("kind", "reach_start")
        .int("burstFrames", burstFrames)
        .long("attemptTimeoutMs", attemptTimeoutMillis)
        .build()

    fun cameraLine(entry: CameraEntry, role: String?): String = JsonLine()
        .string("kind", "reach_camera")
        .string("cameraId", entry.cameraId)
        .bool("inCameraIdList", entry.inCameraIdList)
        .string("role", role)
        .string("facing", entry.facing)
        .floats("focalLengths", entry.focalLengths)
        .floats("physicalSizeMm", entry.physicalSizeMm)
        .string("cfa", entry.cfa?.let { SensorWatchLines.cfaName(it) })
        .string("pixelArray", entry.pixelArray)
        .strings("rawSizes", entry.rawSizes)
        .ints("capabilities", entry.capabilities)
        .strings("physicalIds", entry.physicalIds)
        .strings("logicalIds", entry.logicalIds)
        .string("error", entry.error)
        .build()

    fun planLine(plan: List<PlannedReach>): String = JsonLine()
        .string("kind", "reach_plan")
        .strings("run", plan.filterIsInstance<PlannedReach.Run>().map { it.label })
        .strings("notAttempted", plan.filterIsInstance<PlannedReach.Skip>().map { it.label })
        .build()

    fun borrowLine(borrow: PinBorrow): String {
        val (outcome, reason, detail) = when (borrow) {
            is PinBorrow.Borrowed -> Triple("borrowed", null, borrow.detail)
            is PinBorrow.Failed -> Triple("failed", borrow.reason, borrow.detail)
        }
        return JsonLine()
            .string("kind", "reach_borrow")
            .string("outcome", outcome)
            .string("reason", reason)
            .strings("detail", detail)
            .build()
    }

    fun frameLine(attempt: ReachAttempt, frame: ReachFrame): String = JsonLine()
        .string("kind", "reach_frame")
        .string("label", attempt.label)
        .int("index", frame.index)
        .string("capture", frame.kind)
        .string("streamCameraId", frame.streamCameraId)
        .long("sensorTimestampNs", frame.sensorTimestampNs)
        .string("activePhysicalId", frame.activePhysicalId)
        .strings("physicalResultIds", frame.physicalResultIds)
        .string("rawSize", frame.rawSize)
        .string("error", frame.error)
        .build()

    fun attemptLine(result: ReachResult, verdict: String): String {
        val arrived = result.frames.filter { it.error == null && it.sensorTimestampNs != null }
        val rate = ReachSummary.burstRate(result.frames)
        return JsonLine()
            .string("kind", "reach_attempt")
            .string("label", result.attempt.label)
            .string("method", result.attempt.method)
            .string("verdict", verdict)
            .string("openedId", result.openedId)
            .strings("streamIds", result.streamIds)
            .string("rawSize", result.rawSize)
            .bool("opened", result.opened)
            .string("sessionQuery", result.sessionQuery)
            .bool("configured", result.configured)
            .int("singleArrived", arrived.count { it.kind == "single" })
            .int("burstAsked", result.burstAsked)
            .int("burstArrived", arrived.count { it.kind == "burst" })
            .decimal("burstFps", rate?.framesPerSecond, 3)
            .decimal("burstSpanMs", rate?.spanMs, 3)
            .strings("activePhysicalIds", arrived.mapNotNull { it.activePhysicalId }.distinct())
            .string("stoppedAt", result.stoppedAt?.id)
            .bool("timedOut", result.timedOut)
            .string("error", result.error)
            .strings("notes", result.notes)
            .build()
    }

    /** A step not made: the attempt line's keys, with the reason as its error and every reading null. */
    fun skippedLine(skip: PlannedReach.Skip): String = JsonLine()
        .string("kind", "reach_attempt")
        .string("label", skip.label)
        .string("method", skip.method)
        .string("verdict", ReachVerdict.NOT_ATTEMPTED)
        .string("openedId", null)
        .strings("streamIds", emptyList())
        .string("rawSize", null)
        .bool("opened", null)
        .string("sessionQuery", null)
        .bool("configured", null)
        .int("singleArrived", 0)
        .int("burstAsked", 0)
        .int("burstArrived", 0)
        .decimal("burstFps", null, 3)
        .decimal("burstSpanMs", null, 3)
        .strings("activePhysicalIds", emptyList())
        .string("stoppedAt", null)
        .bool("timedOut", false)
        .string("error", skip.reason)
        .strings("notes", emptyList())
        .build()

    fun handbackLine(handback: PinHandback): String = JsonLine()
        .string("kind", "reach_handback")
        .bool("platformClosed", handback.platformClosed)
        .bool("cameraxBack", handback.cameraxBack)
        .string("detail", handback.detail)
        .build()

    fun rowLine(row: ReachRow): String = JsonLine()
        .string("kind", "reach_row")
        .string("cameraId", row.cameraId)
        .string("role", row.role)
        .strings("reachableRaw", row.reachableRaw)
        .texts("outcomes", row.outcomes.toMap())
        .string("rawSize", row.rawSize)
        .decimal("burstFps", row.burstFps, 3)
        .build()

    fun tableLine(device: String?, table: String): String = JsonLine()
        .string("kind", "reach_table")
        .string("device", device)
        .string("table", table)
        .build()

    fun endLine(completed: Boolean, reason: String?, verdicts: Map<String, String>): String = JsonLine()
        .string("kind", "reach_end")
        .bool("completed", completed)
        .string("reason", reason)
        .texts("verdicts", verdicts)
        .build()
}
