package com.zynergylabs.forager.app.diagnostics.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sensor watch's triggers (dispatch 2026-10-15-12): broadcasts the debug build alone receives, sent over USB with the
 * camera dialog open. Declared in `src/debug/AndroidManifest.xml`, exported because the shell is another app, and guarded
 * by `android.permission.DUMP`, which the shell holds and an installed app cannot be granted. Read the lines with
 * `adb logcat -v raw -s SensorWatch`.
 *
 * ```
 * # Part 1: the watch, on and off (on for at most 10 minutes)
 * adb shell am broadcast -n com.zynergylabs.forager.app/.diagnostics.sensor.SensorWatchReceiver -a com.zynergylabs.forager.app.debug.SENSOR_WATCH --ez on true
 * adb shell am broadcast -n com.zynergylabs.forager.app/.diagnostics.sensor.SensorWatchReceiver -a com.zynergylabs.forager.app.debug.SENSOR_WATCH --ez on false
 * # Part 2: one ratio, held 15 s by default (--el hold_ms to change), then back to 1.0
 * adb shell am broadcast -n com.zynergylabs.forager.app/.diagnostics.sensor.SensorWatchReceiver -a com.zynergylabs.forager.app.debug.SENSOR_ZOOM --ef ratio 0.6
 * # Part 2: the step list, 3 s a step by default (--el hold_ms to change), then back to 1.0
 * adb shell am broadcast -n com.zynergylabs.forager.app/.diagnostics.sensor.SensorWatchReceiver -a com.zynergylabs.forager.app.debug.SENSOR_ZOOM_STEPS
 * # Part 3: the three pinning attempts
 * adb shell am broadcast -n com.zynergylabs.forager.app/.diagnostics.sensor.SensorWatchReceiver -a com.zynergylabs.forager.app.debug.SENSOR_PIN
 * ```
 *
 * Each returns at once; the work runs on [SensorWatchController]'s own scope and reports only through the log.
 */
class SensorWatchReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION") // Bundle.get(String) is deprecated for typed reads; here any number type is accepted on purpose.
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val extras = intent.extras
        when (intent.action) {
            ACTION_WATCH -> {
                val on = extras?.get(EXTRA_ON) as? Boolean
                if (on == null) {
                    Log.w(SensorWatchLines.TAG, SensorWatchLines.watchLine("ignored", "the watch needs --ez $EXTRA_ON true or false; nothing was changed", null, emptyList(), emptyList()))
                } else {
                    SensorWatchController.watch(app, on)
                }
            }
            ACTION_ZOOM -> {
                // A missing or non-number ratio reaches the runner as NaN, which it rejects and logs; the zoom still goes to 1.0.
                val ratio = (extras?.get(EXTRA_RATIO) as? Number)?.toFloat() ?: Float.NaN
                SensorWatchController.zoomSet(app, ratio, holdMillis(extras?.get(EXTRA_HOLD)))
            }
            ACTION_ZOOM_STEPS -> SensorWatchController.zoomSteps(app, holdMillis(extras?.get(EXTRA_HOLD)))
            ACTION_PIN -> SensorWatchController.pin(app)
            else -> Log.w(SensorWatchLines.TAG, "Ignored a broadcast with action ${intent.action}.")
        }
    }

    private fun holdMillis(value: Any?): Long? = (value as? Number)?.toLong()

    companion object {
        const val ACTION_WATCH = "com.zynergylabs.forager.app.debug.SENSOR_WATCH"
        const val ACTION_ZOOM = "com.zynergylabs.forager.app.debug.SENSOR_ZOOM"
        const val ACTION_ZOOM_STEPS = "com.zynergylabs.forager.app.debug.SENSOR_ZOOM_STEPS"
        const val ACTION_PIN = "com.zynergylabs.forager.app.debug.SENSOR_PIN"
        const val EXTRA_ON = "on"
        const val EXTRA_RATIO = "ratio"
        const val EXTRA_HOLD = "hold_ms"
    }
}

/**
 * Holds the one watch and runs the zoom and pin commands on the main thread. A new zoom command cancels the running one
 * (whose `finally` returns the zoom to 1.0) before it starts. The pinning probe borrows the camera, so it refuses to start
 * while a zoom runs, and a zoom refuses while it runs; both log a `busy` line. Do not run these alongside the burst
 * probes: those borrow the same camera and none of them knows of the others.
 */
internal object SensorWatchController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var camera: CameraXDialogCamera? = null
    private var sensorWatch: SensorWatch? = null
    private var limitJob: Job? = null
    private var zoomJob: Job? = null
    private var pinJob: Job? = null

    /** The watch turns itself off after this long, so a forgotten one does not run all day. */
    private const val WATCH_LIMIT_MILLIS = 10 * 60 * 1_000L

    private fun log(line: String) {
        Log.i(SensorWatchLines.TAG, line)
    }

    private fun clock(): Long = SystemClock.elapsedRealtimeNanos()

    private fun watchFor(context: Context): SensorWatch {
        sensorWatch?.let { return it }
        val dialogCamera = CameraXDialogCamera(context).also { camera = it }
        return SensorWatch(dialogCamera, ::log, ::clock).also { sensorWatch = it }
    }

    fun watch(context: Context, on: Boolean) {
        scope.launch {
            val watch = watchFor(context)
            if (on) {
                if (watch.start()) {
                    watch.heldByUser = true
                    limitJob?.cancel()
                    limitJob = scope.launch {
                        delay(WATCH_LIMIT_MILLIS)
                        watch.stop("the watch's ${WATCH_LIMIT_MILLIS / 60_000}-minute limit")
                    }
                }
            } else {
                limitJob?.cancel()
                limitJob = null
                watch.stop("turned off by broadcast")
            }
        }
    }

    fun zoomSet(context: Context, ratio: Float, holdMillis: Long?) {
        launchZoom(context, "zoom") { runner -> runner.set(ratio, ZoomPlan.hold(holdMillis, ZoomPlan.DEFAULT_SET_HOLD_MILLIS)) }
    }

    fun zoomSteps(context: Context, holdMillis: Long?) {
        launchZoom(context, "zoom_steps") { runner -> runner.steps(ZoomPlan.DEFAULT_STEPS, ZoomPlan.hold(holdMillis, ZoomPlan.DEFAULT_STEP_HOLD_MILLIS)) }
    }

    private fun launchZoom(context: Context, asked: String, block: suspend (ZoomRunner) -> Boolean) {
        if (pinJob?.isActive == true) {
            log(SensorWatchLines.busyLine(asked, "the pinning probe holds the camera; try again when its pin_end line is logged"))
            return
        }
        val previous = zoomJob
        zoomJob = scope.launch {
            previous?.cancelAndJoin()
            val watch = watchFor(context)
            val dialogCamera = camera ?: return@launch
            try {
                block(ZoomRunner(dialogCamera, watch, ::log, ::clock))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The runner restores in its own finally; this catches only what escapes it, and says so.
                log(SensorWatchLines.zoomEndLine(asked, returnedToOne = false, reason = "the zoom failed outside its own steps: $failure"))
            }
        }
    }

    fun pin(context: Context) {
        if (pinJob?.isActive == true) {
            log(SensorWatchLines.busyLine("pin", "the pinning probe is already running"))
            return
        }
        if (zoomJob?.isActive == true) {
            log(SensorWatchLines.busyLine("pin", "a zoom command is running; try again when its zoom_end line is logged"))
            return
        }
        pinJob = scope.launch {
            val platform = PlatformPinCamera(context)
            try {
                PinProbe(platform, ::log).runAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // runAll hands back in its own finally; this catches only what escapes it, and says so.
                log(SensorWatchLines.pinEndLine(false, "the probe failed outside its steps: $failure", emptyMap()))
            } finally {
                platform.close()
            }
        }
    }
}
