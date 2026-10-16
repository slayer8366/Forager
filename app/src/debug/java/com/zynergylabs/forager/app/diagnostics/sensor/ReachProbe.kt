package com.zynergylabs.forager.app.diagnostics.sensor

/** The platform side of the reach probe. The real one is [PlatformReachCamera]; the tests drive a fake. Main thread. */
internal interface ReachCamera {
    /** Reads characteristics only: every listed camera and every physical camera. Touches no camera. */
    suspend fun survey(): ReachSurvey

    /** CameraX lets the dialog's camera go. */
    suspend fun borrow(): PinBorrow

    /** One attempt, which opens and closes its own device. */
    suspend fun attempt(attempt: ReachAttempt, burstFrames: Int): ReachResult

    /** Every platform device closed, then CameraX given the camera back. */
    suspend fun handBack(): PinHandback
}

/**
 * The reach probe's order and its invariant (dispatch 2026-10-16-01): survey, plan, borrow, every planned attempt in
 * turn, hand back, summary. A refused, failed, thrown or hung attempt is a finding: it is logged with its verdict and
 * the next attempt still runs. Each attempt is cut off at [attemptTimeoutMillis]; the hand-back runs in a `finally`
 * that cancellation cannot skip and is itself bounded by [handBackTimeoutMillis], and one that throws or overruns is
 * reported as not handed back, never as success.
 */
internal class ReachProbe(
    private val camera: ReachCamera,
    private val attemptTimeoutMillis: Long = ATTEMPT_TIMEOUT_MILLIS,
    private val handBackTimeoutMillis: Long = HANDBACK_TIMEOUT_MILLIS,
    private val log: (String) -> Unit,
) {
    /** True when every planned attempt was made (whatever its verdict) and the camera was confirmed handed back. */
    suspend fun runAll(burstFrames: Int = BURST_FRAMES): Boolean = TODO("stub")

    companion object {
        /** "1 single frame, and an 8-frame burst." */
        const val BURST_FRAMES = 8

        /**
         * One attempt's ceiling. The platform attempt bounds each of its own waits (open 5 s, configure 5 s, exposure
         * 3 s, the single frame 3 s, the burst 10 s, the close 3 s: 29 s in all); this is above that sum, so it fires
         * only when something the attempt did not bound has hung.
         */
        const val ATTEMPT_TIMEOUT_MILLIS = 45_000L

        /** The hand-back's ceiling, above its own bounded waits (pending opens 5 s, closes 3 s each, CameraX OPEN 5 s, the viewfinder 5 s). */
        const val HANDBACK_TIMEOUT_MILLIS = 30_000L
    }
}
