package com.zynergylabs.forager.app.diagnostics.burst

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The probe's trigger (dispatch 2026-10-15-06): a broadcast the debug build alone receives, sent over USB with the camera
 * dialog open. Declared in `src/debug/AndroidManifest.xml`, so a release build has neither this class nor its entry.
 *
 * ```
 * adb shell am broadcast -n com.zynergylabs.forager.app/.diagnostics.burst.BurstProbeReceiver -a com.zynergylabs.forager.app.debug.BURST_PROBE
 * adb logcat -v raw -s BurstProbe
 * ```
 *
 * The receiver is exported, because the shell is another app, and guarded by `android.permission.DUMP`, which the shell
 * holds and an installed app cannot be granted, so nothing else on the phone can start the camera through it.
 *
 * It returns at once: the probe runs for tens of seconds, longer than a receiver may hold the main thread, so it is
 * started on [BurstProbeLauncher]'s own scope and reports only through the log.
 */
class BurstProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            Log.w(BurstProbeLines.TAG, "Ignored a broadcast with action ${intent.action}; the probe starts only on $ACTION.")
            return
        }
        BurstProbeLauncher.start(context.applicationContext)
    }

    companion object {
        const val ACTION = "com.zynergylabs.forager.app.debug.BURST_PROBE"
    }
}

/** Runs one probe at a time on the main thread; a trigger that arrives while one runs is logged and dropped, not queued. */
internal object BurstProbeLauncher {
    private val running = AtomicBoolean(false)
    private val sessions = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context) {
        if (!running.compareAndSet(false, true)) {
            Log.i(BurstProbeLines.TAG, BurstProbeLines.busyLine(sessions.get()))
            return
        }
        val session = sessions.incrementAndGet()
        scope.launch {
            val camera = CameraXBurstProbeCamera(context)
            try {
                BurstProbe(camera, session) { line -> Log.i(BurstProbeLines.TAG, line) }.runAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(BurstProbeLines.TAG, BurstProbeLines.endLine(session, completed = false, reason = "the probe failed outside a run: $failure"), failure)
            } finally {
                camera.close()
                running.set(false)
            }
        }
    }
}
