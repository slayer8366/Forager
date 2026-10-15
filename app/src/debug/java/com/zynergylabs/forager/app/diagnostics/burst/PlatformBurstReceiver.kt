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
 * The fast-burst test's trigger (dispatch 2026-10-15-09): a broadcast the debug build alone receives, sent over USB with
 * the camera dialog open, the same shape as piece 1's [BurstProbeReceiver]. Declared in `src/debug/AndroidManifest.xml`,
 * so a release build has neither this class nor its entry.
 *
 * ```
 * adb shell am broadcast -n com.zynergylabs.forager.app/.diagnostics.burst.PlatformBurstReceiver -a com.zynergylabs.forager.app.debug.PLATFORM_BURST
 * adb logcat -v raw -s PlatformBurst
 * ```
 *
 * Exported, because the shell is another app, and guarded by `android.permission.DUMP`, which the shell holds and an
 * installed app cannot be granted. It returns at once and runs the probe on [PlatformBurstLauncher]'s own scope.
 *
 * Do not trigger this and piece 1's probe together: both borrow the same camera, and neither knows of the other.
 */
class PlatformBurstReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            Log.w(PlatformBurstLines.TAG, "Ignored a broadcast with action ${intent.action}; the probe starts only on $ACTION.")
            return
        }
        PlatformBurstLauncher.start(context.applicationContext)
    }

    companion object {
        const val ACTION = "com.zynergylabs.forager.app.debug.PLATFORM_BURST"
    }
}

/** Runs one probe at a time on the main thread; a trigger that arrives while one runs is logged and dropped, not queued. */
internal object PlatformBurstLauncher {
    private val running = AtomicBoolean(false)
    private val sessions = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context) {
        if (!running.compareAndSet(false, true)) {
            Log.i(PlatformBurstLines.TAG, PlatformBurstLines.busyLine(sessions.get()))
            return
        }
        val session = sessions.incrementAndGet()
        val folder = PlatformBurstFiles.sessionFolder(System.currentTimeMillis(), session)
        scope.launch {
            val camera = PlatformBurstCameraImpl(context, folder)
            try {
                PlatformBurstProbe(camera, session) { line -> Log.i(PlatformBurstLines.TAG, line) }.runAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // runAll hands back in its own finally; this catches only what escapes it, and says so.
                Log.w(PlatformBurstLines.TAG, PlatformBurstLines.endLine(session, completed = false, reason = "the probe failed outside its steps: $failure", swap = null), failure)
            } finally {
                camera.close()
                running.set(false)
            }
        }
    }
}
