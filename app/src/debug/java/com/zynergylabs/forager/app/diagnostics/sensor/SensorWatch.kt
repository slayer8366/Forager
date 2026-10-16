package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.delay

/**
 * The camera dialog's own camera, as the watch (Part 1) and the zoom stepper (Part 2) use it. The real one is
 * [CameraXDialogCamera]; the tests drive a fake.
 */
internal interface DialogCamera {
    /** Finds the dialog's camera and what it has bound. Main thread. */
    suspend fun attach(): DialogAttach

    /** Starts delivering every capture result of the dialog's camera, preview and still, to [onReading] on a background thread. */
    fun startResults(onReading: (FrameReading) -> Unit)

    /** Stops the delivery. Returns notes for the summary (results that could not be read, say), never throws them away. */
    fun stopResults(): List<String>

    /** Finds the dialog's camera control and its reported zoom range. Main thread. */
    suspend fun findZoom(): ZoomAccess

    /** CameraX's `setZoomRatio` on the dialog's camera, awaited. Main thread. */
    suspend fun setZoom(ratio: Float): ZoomOutcome
}

internal sealed interface DialogAttach {
    data class Attached(val cameraId: String, val dialog: List<String>, val notes: List<String>) : DialogAttach
    data class Unavailable(val reason: String) : DialogAttach
}

internal sealed interface ZoomAccess {
    data class Available(val range: ZoomRange) : ZoomAccess
    data class Unavailable(val reason: String) : ZoomAccess
}

internal sealed interface ZoomOutcome {
    data object Applied : ZoomOutcome
    data class Failed(val reason: String) : ZoomOutcome
}

/**
 * Part 1, the watch: one JSON line per preview capture result (at most five a second), one per still, and a `switch` line
 * whenever the active physical camera changes. On until turned off, or until its time limit.
 */
internal class SensorWatch(
    private val camera: DialogCamera,
    private val log: (String) -> Unit,
    private val clock: () -> Long,
) {
    /** True from a successful [start] until [stop]. */
    val running: Boolean get() = TODO("built in the next commit")

    /** Set when the watch was turned on by its own broadcast, so a zoom run that also needed it does not turn it off. */
    var heldByUser: Boolean = false

    /** The last result seen, logged or not. */
    val latest: FrameReading? get() = TODO("built in the next commit")

    /** Switches seen since [start]. */
    fun switchCount(): Int = TODO("built in the next commit")

    /** True when the watch is on after the call (already on counts). Logs a `watch` line either way. */
    suspend fun start(): Boolean = TODO("built in the next commit")

    /** Stops the results and logs the summary and a `watch` off line. Safe when not running (logs that). */
    fun stop(reason: String): Unit = TODO("built in the next commit")
}

/**
 * Part 2, the zoom stepper, on the dialog's camera through CameraX's own `setZoomRatio`. Every path, a failure or a
 * cancellation included, ends with the zoom set back to 1.0 in a `finally` cancellation cannot skip, and a watch it
 * turned on for itself is turned off again.
 */
internal class ZoomRunner(
    private val camera: DialogCamera,
    private val watch: SensorWatch,
    private val log: (String) -> Unit,
    private val clock: () -> Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** Sets [ratio] (clamped to the range and logged as clamped), holds it [holdMillis], then returns to 1.0. */
    suspend fun set(ratio: Float, holdMillis: Long): Boolean = TODO("built in the next commit")

    /** Steps through [ratios] inside the range, holding each [holdMillis], logging the settled sensor; then 1.0. */
    suspend fun steps(ratios: List<Float> = ZoomPlan.DEFAULT_STEPS, holdMillis: Long = ZoomPlan.DEFAULT_STEP_HOLD_MILLIS): Boolean =
        TODO("built in the next commit")
}
