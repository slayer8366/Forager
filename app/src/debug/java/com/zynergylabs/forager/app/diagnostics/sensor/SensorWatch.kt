package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
 *
 * The results arrive on the camera's callback thread; [start] and [stop] run on the main thread. The detector is
 * synchronized, and [latest] and the detector reference are volatile, so a result that lands as the watch stops is either
 * counted or dropped whole.
 */
internal class SensorWatch(
    private val camera: DialogCamera,
    private val log: (String) -> Unit,
    private val clock: () -> Long,
) {
    @Volatile private var detector: SwitchDetector? = null
    @Volatile private var lastReading: FrameReading? = null

    /** True from a successful [start] until [stop]. */
    val running: Boolean get() = detector != null

    /** Set when the watch was turned on by its own broadcast, so a zoom run that also needed it does not turn it off. */
    var heldByUser: Boolean = false

    /** The last result seen, logged or not. */
    val latest: FrameReading? get() = lastReading

    /** Switches seen since [start]; 0 when not running. */
    fun switchCount(): Int = detector?.summary()?.switches ?: 0

    /** True when the watch is on after the call (already on counts). Logs a `watch` line either way. */
    suspend fun start(): Boolean {
        if (running) {
            log(SensorWatchLines.watchLine("already_on", null, null, emptyList(), emptyList()))
            return true
        }
        val attached = try {
            camera.attach()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            DialogAttach.Unavailable("finding the dialog's camera threw: $failure")
        }
        when (attached) {
            is DialogAttach.Unavailable -> {
                log(SensorWatchLines.watchLine("unavailable", attached.reason, null, emptyList(), emptyList()))
                return false
            }
            is DialogAttach.Attached -> {
                val fresh = SwitchDetector()
                lastReading = null
                detector = fresh
                try {
                    camera.startResults { reading -> onReading(fresh, reading) }
                } catch (failure: Exception) {
                    detector = null
                    val notes = try {
                        camera.stopResults()
                    } catch (stopFailure: Exception) {
                        listOf("stopping the results after the failed start threw: $stopFailure")
                    }
                    log(SensorWatchLines.watchLine("unavailable", "the results callback could not be added: $failure", attached.cameraId, attached.dialog, attached.notes + notes))
                    return false
                }
                log(SensorWatchLines.watchLine("on", null, attached.cameraId, attached.dialog, attached.notes))
                return true
            }
        }
    }

    /** Stops the results and logs the summary and a `watch` off line. Safe when not running (logs that). */
    fun stop(reason: String) {
        val stopping = detector
        if (stopping == null) {
            log(SensorWatchLines.watchLine("already_off", reason, null, emptyList(), emptyList()))
            return
        }
        detector = null
        heldByUser = false
        val notes = try {
            camera.stopResults()
        } catch (failure: Exception) {
            listOf("stopping the results threw: $failure; the callback may still be registered")
        }
        log(SensorWatchLines.summaryLine(stopping.summary(), reason, notes))
        log(SensorWatchLines.watchLine("off", reason, null, emptyList(), notes))
    }

    /** On the callback thread. A result for a watch that has since stopped (or restarted) is dropped. */
    private fun onReading(owner: SwitchDetector, reading: FrameReading) {
        if (detector !== owner) return
        lastReading = reading
        val observation = owner.observe(reading)
        observation.switch?.let { log(SensorWatchLines.switchLine(it)) }
        if (observation.logFrame) log(SensorWatchLines.frameLine(reading))
    }
}

/**
 * Part 2, the zoom stepper, on the dialog's camera through CameraX's own `setZoomRatio`. Every path, a failure or a
 * cancellation included, ends with the zoom set back to 1.0 in a `finally` cancellation cannot skip, and a watch it
 * turned on for itself is turned off again.
 *
 * A single set is held for a time and then returned to 1.0 as well (default [ZoomPlan.DEFAULT_SET_HOLD_MILLIS]): the
 * dispatch names a camera "left ... zoomed" as an abort condition, so no command leaves the zoom where it put it.
 */
internal class ZoomRunner(
    private val camera: DialogCamera,
    private val watch: SensorWatch,
    private val log: (String) -> Unit,
    private val clock: () -> Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** Sets [ratio] (clamped to the range and logged as clamped), holds it [holdMillis], then returns to 1.0. */
    suspend fun set(ratio: Float, holdMillis: Long): Boolean = withZoom(MODE_SET) { range ->
        when (val clamp = ZoomPlan.clamp(ratio, range)) {
            is ZoomClamp.Rejected -> {
                log(SensorWatchLines.zoomLine(MODE_SET, null, ratio, null, null, range, "rejected", clamp.reason, null, null, null))
                false
            }
            is ZoomClamp.Valid -> {
                val outcome = camera.setZoom(clamp.applied)
                val settled = watch.latest
                log(
                    SensorWatchLines.zoomLine(
                        MODE_SET, null, clamp.requested, clamp.applied, clamp.clamped, range, outcome.label(), outcome.reason(),
                        settled, settled?.let { ageMs(it) }, null,
                    ),
                )
                if (outcome is ZoomOutcome.Applied) sleep(holdMillis)
                outcome is ZoomOutcome.Applied
            }
        }
    }

    /** Steps through [ratios] inside the range, holding each [holdMillis], logging the settled sensor; then 1.0. */
    suspend fun steps(ratios: List<Float> = ZoomPlan.DEFAULT_STEPS, holdMillis: Long = ZoomPlan.DEFAULT_STEP_HOLD_MILLIS): Boolean =
        withZoom(MODE_STEPS) { range ->
            for (step in ZoomPlan.steps(ratios, range)) {
                if (!step.inRange) {
                    log(
                        SensorWatchLines.zoomLine(
                            MODE_STEP, step.index, step.requested, null, null, range, "skipped",
                            "outside the reported range ${range.min} to ${range.max}", null, null, null,
                        ),
                    )
                    continue
                }
                val switchesBefore = watch.switchCount()
                val outcome = camera.setZoom(step.requested)
                if (outcome is ZoomOutcome.Applied) sleep(holdMillis)
                val settled = if (outcome is ZoomOutcome.Applied) watch.latest else null
                log(
                    SensorWatchLines.zoomLine(
                        MODE_STEP, step.index, step.requested, if (outcome is ZoomOutcome.Applied) step.requested else null, false, range,
                        outcome.label(), outcome.reason(), settled, settled?.let { ageMs(it) },
                        if (outcome is ZoomOutcome.Applied) watch.switchCount() - switchesBefore else null,
                    ),
                )
            }
            true
        }

    /**
     * Finds the dialog's zoom, turns the watch on if it is off, runs [body], and in a `finally` cancellation cannot skip
     * sets the zoom back to 1.0 (or the nearest the range allows), turns off a watch it turned on, and logs `zoom_end`.
     * Returns true only when [body] finished, said it succeeded, and the zoom was confirmed back.
     */
    private suspend fun withZoom(mode: String, body: suspend (ZoomRange) -> Boolean): Boolean {
        val access = try {
            camera.findZoom()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ZoomAccess.Unavailable("finding the dialog's zoom threw: $failure")
        }
        val range = when (access) {
            is ZoomAccess.Unavailable -> {
                log(SensorWatchLines.zoomEndLine(mode, returnedToOne = false, reason = access.reason))
                return false
            }
            is ZoomAccess.Available -> access.range
        }
        var startedWatch = false
        var succeeded = false
        var failure: String? = null
        try {
            startedWatch = !watch.running && watch.start()
            succeeded = body(range)
        } catch (cancelled: CancellationException) {
            failure = "the zoom $mode was cancelled"
            throw cancelled
        } catch (error: Exception) {
            failure = "the zoom $mode failed: $error"
        } finally {
            withContext(NonCancellable) {
                val one = when (val home = ZoomPlan.clamp(1f, range)) {
                    is ZoomClamp.Valid -> home.applied
                    is ZoomClamp.Rejected -> 1f
                }
                val back = try {
                    ZoomOutcome.Applied
                } catch (error: Exception) {
                    ZoomOutcome.Failed("setting the zoom back threw: $error")
                }
                val settled = watch.latest
                log(
                    SensorWatchLines.zoomLine(
                        MODE_RESTORE, null, 1f, if (back is ZoomOutcome.Applied) one else null, one != 1f, range,
                        back.label(), back.reason(), settled, settled?.let { ageMs(it) }, null,
                    ),
                )
                if (startedWatch && !watch.heldByUser) watch.stop("the zoom $mode ended")
                val returned = back is ZoomOutcome.Applied
                val reason = listOfNotNull(failure, (back as? ZoomOutcome.Failed)?.let { "the return to 1.0 failed: ${it.reason}" })
                    .joinToString("; ").ifEmpty { null }
                log(SensorWatchLines.zoomEndLine(mode, returnedToOne = returned, reason = reason))
                succeeded = succeeded && returned && failure == null
            }
        }
        return succeeded
    }

    private fun ageMs(reading: FrameReading): Double = (clock() - reading.arrivalNanos) / 1_000_000.0

    private fun ZoomOutcome.label(): String = if (this is ZoomOutcome.Applied) "applied" else "failed"
    private fun ZoomOutcome.reason(): String? = (this as? ZoomOutcome.Failed)?.reason

    private companion object {
        const val MODE_SET = "set"
        const val MODE_STEPS = "steps"
        const val MODE_STEP = "step"
        const val MODE_RESTORE = "restore"
    }
}
