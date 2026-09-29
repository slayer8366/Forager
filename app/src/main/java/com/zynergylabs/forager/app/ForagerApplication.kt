package com.zynergylabs.forager.app

import android.app.Application
import android.os.Build
import android.util.Log
import com.zynergylabs.forager.app.crash.CrashUncaughtExceptionHandler
import com.zynergylabs.forager.app.data.backup.ScheduledBackupDependencies
import com.zynergylabs.forager.app.data.backup.ScheduledBackupDependenciesProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.RunScheduledBackupUseCase
import com.zynergylabs.forager.app.domain.ScheduledBackupReporter
import com.zynergylabs.forager.app.diagnostics.DebugDiagnostics
import com.zynergylabs.forager.app.map.initializeMapLibre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ForagerApplication : Application(), ScheduledBackupDependenciesProvider {
    lateinit var container: AppContainer
        private set

    /**
     * The debug build's observation surface (StrictMode into a file, the sweep's count); a no-op
     * object in release, by build-type source set rather than by branch — see the two
     * [DebugDiagnostics] classes. Installed first, before [container] is built, so what it observes
     * is the whole process from the first line of this method, not the part after startup.
     */
    lateinit var diagnostics: DebugDiagnostics
        private set

    /**
     * Process-lifetime work that belongs to no screen. Created here rather than in [AppContainer]
     * because the first thing that needed it, the capture sweep below, is a startup concern of the
     * process, not a dependency any screen asks for. [SupervisorJob] so one failed job does not
     * cancel the scope for the next.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What the scheduled backup's worker runs, taken from the container when WorkManager starts it (possibly in a fresh process with no Activity). */
    override val scheduledBackupDependencies: ScheduledBackupDependencies
        get() = object : ScheduledBackupDependencies {
            override val runScheduledBackup: RunScheduledBackupUseCase = container.runScheduledBackupUseCase
            override val reporter: ScheduledBackupReporter = container.scheduledBackupReporter
            override val errorLog: ErrorLog = container.errorLog
        }

    override fun onCreate() {
        super.onCreate()
        val startedAt = System.currentTimeMillis()
        diagnostics = DebugDiagnostics.install(this)
        container = AppContainer(this)
        installCrashHandler()
        initializeMapLibreAtStart()
        sweepOrphanedCaptures(startedAt)
    }

    /**
     * MapLibre's native side must be initialised before anything reads offline regions (the
     * Journal's start-up read runs at ViewModel construction) or composes a map. This is the
     * earliest point every path goes through. [initializeMapLibre] logs a failure itself and
     * retries on the next call, so it is caught here only so a device where it fails still starts;
     * under Robolectric the native library cannot load, so every unit test that boots this class
     * logs that failure once (an earlier attempt at this call was reverted for throwing there).
     */
    private fun initializeMapLibreAtStart() {
        try {
            initializeMapLibre(this)
        } catch (e: Exception) {
            Log.w(TAG, "MapLibre was not initialised at application start; later callers will retry.")
        } catch (e: LinkageError) {
            // The native library did not load (always, under Robolectric). Already logged by the initializer.
            Log.w(TAG, "MapLibre was not initialised at application start; later callers will retry.")
        }
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
            // The same number, somewhere a phone with no logcat can read it (debug builds only).
            diagnostics.recordSweep(deleted)
        }
    }

    private companion object {
        const val TAG = "ForagerApplication"
    }
}
