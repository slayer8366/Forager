package com.zynergylabs.forager.app

import android.app.Application
import android.os.Build
import android.util.Log
import com.zynergylabs.forager.app.crash.CrashUncaughtExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ForagerApplication : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * Process-lifetime work that belongs to no screen. Created here rather than in [AppContainer]
     * because the first thing that needed it, the capture sweep below, is a startup concern of the
     * process, not a dependency any screen asks for. [SupervisorJob] so one failed job does not
     * cancel the scope for the next.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val startedAt = System.currentTimeMillis()
        container = AppContainer(this)
        installCrashHandler()
        sweepOrphanedCaptures(startedAt)
    }

    /**
     * See [CrashUncaughtExceptionHandler]'s own doc comment for why this exists. Captures the
     * platform's current default handler before replacing it, so this one can chain to it after
     * writing a trace — process-death behavior is unchanged from before this method existed.
     */
    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashUncaughtExceptionHandler(container.crashFileStore, Build.VERSION.SDK_INT, previousHandler),
        )
    }

    /**
     * [CameraCaptureFiles.sweepOrphans], off the main thread. **`onCreate` runs on the main
     * thread**; a directory walk and a batch of deletes do not belong on it, and this is dispatched
     * explicitly rather than assumed to be elsewhere — a draft of this change said the sweep "runs
     * off-main" as though `onCreate` did, which it does not (reviewer's correction, 2026-09-14).
     * Logged at INFO when it deletes anything, because a count here is the only evidence the
     * pre-existing leak left anything behind on a given install.
     */
    private fun sweepOrphanedCaptures(processStartedAtMillis: Long) {
        applicationScope.launch {
            val deleted = container.cameraCaptureFiles.sweepOrphans(processStartedAtMillis)
            if (deleted > 0) Log.i(TAG, "Deleted $deleted orphaned capture file(s) left by an earlier process.")
        }
    }

    private companion object {
        const val TAG = "ForagerApplication"
    }
}
