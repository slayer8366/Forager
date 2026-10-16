package com.zynergylabs.forager.app.diagnostics.sensor

/**
 * A phone shaped like the S26 as its characteristics read (the pin probe's `lens` lines, RECORD -1079, and the dumpsys
 * capture of 2026-10-15, both READ): listed IDs 0, 1, 2 and 3; logical back camera 0 made of physical 2, 5, 6 and 7;
 * 2 is also listed; 1 and 3 face front. Focal lengths, sensor sizes, colour layouts and RAW sizes are the logged ones.
 */
internal object ReachFixtures {
    private fun entry(
        id: String,
        listed: Boolean,
        facing: String,
        focal: Float,
        size: List<Float>,
        cfa: Int,
        raw: List<String>,
        physicalIds: List<String> = emptyList(),
        logicalIds: List<String> = emptyList(),
    ) = CameraEntry(
        cameraId = id, inCameraIdList = listed, facing = facing, focalLengths = listOf(focal), physicalSizeMm = size, cfa = cfa,
        pixelArray = raw.firstOrNull(), rawSizes = raw, capabilities = listOf(0, 3), physicalIds = physicalIds, logicalIds = logicalIds,
    )

    val s26 = ReachSurvey(
        device = "samsung SM-S948U",
        cameraIdList = listOf("0", "1", "2", "3"),
        cameras = listOf(
            entry("0", true, "BACK", 6.5f, listOf(9.792f, 7.344f), 2, listOf("4080x3060"), physicalIds = listOf("2", "5", "6", "7")),
            entry("1", true, "FRONT", 3.3f, listOf(5.6f, 4.2f), 1, listOf("4000x3000")),
            entry("2", true, "BACK", 2.2f, listOf(5.712f, 4.284f), 1, listOf("4080x3060"), logicalIds = listOf("0")),
            entry("3", true, "FRONT", 2.9f, listOf(4.0f, 3.0f), 1, emptyList()),
            entry("5", false, "BACK", 6.5f, listOf(9.792f, 7.344f), 2, listOf("4080x3060"), logicalIds = listOf("0")),
            entry("6", false, "BACK", 7.0f, listOf(3.648f, 2.736f), 2, listOf("3648x2736"), logicalIds = listOf("0")),
            entry("7", false, "BACK", 18.6f, listOf(5.712f, 4.284f), 1, listOf("4080x3060"), logicalIds = listOf("0")),
        ),
    )

    /** The plan [s26] must produce, in order. */
    val s26Labels = listOf(
        "direct_0",
        "through_logical_0_2", "direct_2",
        "through_logical_0_5", "direct_5",
        "through_logical_0_6", "direct_6",
        "through_logical_0_7", "direct_7",
        "pair_0_2_5", "pair_0_2_6", "pair_0_2_7", "pair_0_5_6", "pair_0_5_7", "pair_0_6_7",
    )

    /** Eight frames 33.333 ms apart (30 a second), as the fast-burst probe measured on both phones (RECORD -1069, -1074). */
    fun burst(stream: String, size: String = "4080x3060", count: Int = 8, startNs: Long = 1_000_000_000L): List<ReachFrame> =
        (0 until count).map { i ->
            ReachFrame(1 + i, "burst", stream, startNs + i * 33_333_333L, stream, listOf(stream), size, null)
        }

    fun single(stream: String, size: String = "4080x3060"): ReachFrame =
        ReachFrame(0, "single", stream, 500_000_000L, stream, listOf(stream), size, null)

    /** A whole attempt that reached RAW: one single and an eight-frame burst on each of its streams. */
    fun reached(attempt: ReachAttempt, burstFrames: Int = 8): ReachResult {
        val (opened, streams) = when (attempt) {
            is ReachAttempt.ThroughLogical -> attempt.logicalId to listOf(attempt.physicalId)
            is ReachAttempt.Direct -> attempt.cameraId to listOf(attempt.cameraId)
            is ReachAttempt.TwoStreams -> attempt.logicalId to listOf(attempt.first, attempt.second)
        }
        val pair = attempt is ReachAttempt.TwoStreams
        val frames = streams.map { single(it) } + if (pair) emptyList() else burst(streams.single(), count = burstFrames)
        return ReachResult(
            attempt = attempt, openedId = opened, streamIds = streams, rawSize = "4080x3060", opened = true, sessionQuery = "supported",
            configured = true, frames = frames, burstAsked = if (pair) 0 else burstFrames, stoppedAt = null, error = null, notes = emptyList(),
        )
    }

    /** An open refused as camera 5's was on the S26 (RECORD -1079). */
    fun refused(attempt: ReachAttempt, openedId: String): ReachResult = ReachResult(
        attempt = attempt, openedId = openedId, streamIds = listOf(openedId), rawSize = "4080x3060", opened = false, sessionQuery = null,
        configured = null, frames = emptyList(), burstAsked = 8, stoppedAt = ReachStage.OPEN,
        error = "camera $openedId was not opened: openCamera threw CameraAccessException reason 2: CAMERA_DISCONNECTED (2): No camera device with ID \"$openedId\" available",
        notes = emptyList(),
    )
}
