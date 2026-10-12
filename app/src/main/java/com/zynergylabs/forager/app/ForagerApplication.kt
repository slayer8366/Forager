package com.zynergylabs.forager.app

import android.app.Application
import android.util.Log
import com.zynergylabs.forager.app.alert.cancelRecordingAlerts
import com.zynergylabs.forager.app.crash.installCrashCapture
import com.zynergylabs.forager.app.data.backup.ScheduledBackupDependencies
import com.zynergylabs.forager.app.data.backup.ScheduledBackupDependenciesProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.RunScheduledBackupUseCase
import com.zynergylabs.forager.app.domain.ScheduledBackupReporter
import com.zynergylabs.forager.app.diagnostics.DebugDiagnostics
import com.zynergylabs.forager.app.export.TrackGpxExporter
import com.zynergylabs.forager.app.map.initializeMapLibre
import com.zynergylabs.forager.app.map.installMapHttpClient
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
        // The start time goes to the container for the abandoned-track sweep's cut-off (dispatch
        // 2026-09-28-400, Amendment 3, Part 3b). Taken above, before anything else runs. The sweep
        // itself is not launched here: nothing in onCreate queries the database. See
        // AbandonedTrackSweepOnce for what launches it and why.
        container = AppContainer(this, processStartedAtEpochMillis = startedAt)
        installCrashHandler()
        initializeMapLibreAtStart()
        installMapHttpClientAtStart()
        sweepOrphanedCaptures(startedAt)
        deleteStaleGpxExports()
        clearStaleRecordingAlerts()
    }

    /**
     * A process killed mid-recording never runs the service's `onDestroy`, so its sundown, back-by or
     * off-track alert can stay in the shade (RECORD -800: the owner, on clearing on kill, "Yes, add it now
     * (Recommended)"). A new process has no recording, so any such alert here is left from one that has
     * ended. Chosen over the service's next start, which a killed recording may never get: every way back
     * into the app (the screen, a sticky restart, an alarm, a notification action) starts the process.
     */
    private fun clearStaleRecordingAlerts() {
        try {
            cancelRecordingAlerts(this)
        } catch (e: Exception) {
            Log.w(TAG, "Alerts left from an ended recording could not be taken down at start.", e)
        }
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
     * Gives MapLibre the client that identifies the app to tile servers (dispatch 2026-09-28-325),
     * after [initializeMapLibreAtStart] because installing it loads a class that needs MapLibre's
     * application context. A failure is logged here, not swallowed, and leaves MapLibre on its
     * default client (the library's own User-Agent), so the map still works.
     */
    private fun installMapHttpClientAtStart() {
        try {
            installMapHttpClient()
        } catch (e: Exception) {
            Log.w(TAG, "The map's HTTP client was not installed; tiles go out with MapLibre's default User-Agent.", e)
        } catch (e: LinkageError) {
            Log.w(TAG, "The map's HTTP client was not installed; tiles go out with MapLibre's default User-Agent.", e)
        }
    }

    /**
     * Crash capture, debug builds only (dispatch 2026-10-11, RECORD -830): [installCrashCapture] has a
     * debug version in `src/debug` that installs `CrashUncaughtExceptionHandler`, chained to the
     * platform's own, and a release version in `src/release` that does nothing, because nothing in a
     * release build can read a crash file (the Crash Logs row is debug-only too; Play Console reports
     * crashes). A build-type source set rather than a `BuildConfig.DEBUG` branch, as `DebugDiagnostics`
     * is, so the capturing classes are not in the release APK at all.
     */
    private fun installCrashHandler() {
        installCrashCapture(container.crashFileStore)
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

    /**
     * [TrackGpxExporter.deleteStaleExports] at start (F5, dispatch 2026-09-28-216; owner, "3 A"), so an
     * export more than an hour old leaves the cache even when no later export runs. Off the main thread
     * for the reason [sweepOrphanedCaptures] gives, and logged at INFO when it deletes anything.
     */
    private fun deleteStaleGpxExports() {
        applicationScope.launch {
            val deleted = TrackGpxExporter.forContext(this@ForagerApplication).deleteStaleExports()
            if (deleted > 0) Log.i(TAG, "Deleted $deleted GPX export(s) more than an hour old from the cache.")
        }
    }

    private companion object {
        const val TAG = "ForagerApplication"
    }
}
