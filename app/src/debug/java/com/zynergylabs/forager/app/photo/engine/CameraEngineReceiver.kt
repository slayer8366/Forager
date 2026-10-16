package com.zynergylabs.forager.app.photo.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The prototype's adb trigger (dispatch 2026-10-16-02). Debug builds only, declared in `src/debug/AndroidManifest.xml`,
 * exported because adb's shell is another app and guarded by DUMP, which the shell holds and an installed app cannot be
 * granted, like the probes' receivers.
 *
 * ```
 * # Choose the engine (takes effect at the next camera open); CameraX is the default.
 * adb shell am broadcast -n com.zynergylabs.forager.app/.photo.engine.CameraEngineReceiver -a com.zynergylabs.forager.app.debug.CAMERA_ENGINE --es engine platform
 * adb shell am broadcast -n com.zynergylabs.forager.app/.photo.engine.CameraEngineReceiver -a com.zynergylabs.forager.app.debug.CAMERA_ENGINE --es engine camerax
 * # Force the fallback at the next open (and turn it off again).
 * adb shell am broadcast -n com.zynergylabs.forager.app/.photo.engine.CameraEngineReceiver -a com.zynergylabs.forager.app.debug.CAMERA_ENGINE --ez force_fallback true
 * # With the camera dialog open on engine A: the 8-frame RAW burst, and the summary line.
 * adb shell am broadcast -n com.zynergylabs.forager.app/.photo.engine.CameraEngineReceiver -a com.zynergylabs.forager.app.debug.CAMERA_ENGINE_BURST
 * adb shell am broadcast -n com.zynergylabs.forager.app/.photo.engine.CameraEngineReceiver -a com.zynergylabs.forager.app.debug.CAMERA_ENGINE_SUMMARY
 * # Read.
 * adb logcat -v raw -s CameraEngineA
 * ```
 */
class CameraEngineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ENGINE -> onEngine(intent)
            ACTION_BURST -> EngineBurstLauncher.start()
            ACTION_SUMMARY -> Log.i(ENGINE_LOG_TAG, ActivePlatformEngine.current?.summary() ?: "engine=switch event=summary reason=no platform engine is open")
            else -> Log.w(ENGINE_LOG_TAG, "engine=switch event=ignored action=${intent.action}")
        }
    }

    private fun onEngine(intent: Intent) {
        if (intent.hasExtra(EXTRA_FORCE_FALLBACK)) {
            CameraEngineSwitch.forceFallback = intent.getBooleanExtra(EXTRA_FORCE_FALLBACK, false)
            Log.i(ENGINE_LOG_TAG, "engine=switch event=force_fallback value=${CameraEngineSwitch.forceFallback} (takes effect at the next camera open)")
        }
        val name = intent.getStringExtra(EXTRA_ENGINE) ?: return
        val choice = CameraEngineChoice.entries.firstOrNull { it.id == name }
        if (choice == null) {
            Log.w(ENGINE_LOG_TAG, "engine=switch event=ignored engine=$name reason=not one of ${CameraEngineChoice.entries.map { it.id }}")
            return
        }
        CameraEngineSwitch.select(choice)
    }

    companion object {
        const val ACTION_ENGINE = "com.zynergylabs.forager.app.debug.CAMERA_ENGINE"
        const val ACTION_BURST = "com.zynergylabs.forager.app.debug.CAMERA_ENGINE_BURST"
        const val ACTION_SUMMARY = "com.zynergylabs.forager.app.debug.CAMERA_ENGINE_SUMMARY"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_FORCE_FALLBACK = "force_fallback"
    }
}

/** The platform engine of the camera dialog that is open now, if it runs engine A; the adb triggers reach it here. */
internal object ActivePlatformEngine {
    @Volatile
    var current: PlatformCameraEngine? = null
}

/** One burst at a time on the main thread; a trigger while one runs is logged and dropped. */
internal object EngineBurstLauncher {
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start() {
        val engine = ActivePlatformEngine.current
        if (engine == null) {
            Log.i(ENGINE_LOG_TAG, "engine=switch event=burst_refused reason=no platform engine is open; choose engine A and open the camera dialog")
            return
        }
        if (!running.compareAndSet(false, true)) {
            Log.i(ENGINE_LOG_TAG, "engine=switch event=burst_refused reason=a burst is already running")
            return
        }
        scope.launch {
            try {
                Log.i(ENGINE_LOG_TAG, "engine=switch event=burst_result ${engine.burst()}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(ENGINE_LOG_TAG, "engine=switch event=burst_failed reason=$failure", failure)
            } finally {
                running.set(false)
            }
        }
    }
}
