package com.zynergylabs.forager.app.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zynergylabs.forager.app.domain.BackupFrequency
import com.zynergylabs.forager.app.domain.BackupScheduleSettings
import com.zynergylabs.forager.app.domain.BackupScheduler
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.RunScheduledBackupUseCase
import com.zynergylabs.forager.app.domain.ScheduledBackupReporter
import java.util.concurrent.TimeUnit

/** The input a Try again job carries: run one backup to the saved folder whether or not the schedule is on (dispatch 2026-09-28-182, item 7). */
internal const val BACKUP_RETRY_INPUT_KEY = "retry"

/** What the worker needs. */
interface ScheduledBackupDependencies {
    val runScheduledBackup: RunScheduledBackupUseCase
    val reporter: ScheduledBackupReporter
    val errorLog: ErrorLog
}

/** Implemented by the application, so the worker keeps the two-argument constructor WorkManager instantiates by reflection. */
interface ScheduledBackupDependenciesProvider {
    val scheduledBackupDependencies: ScheduledBackupDependencies
}

/**
 * The scheduled backup, as WorkManager runs it: one [RunScheduledBackupUseCase]. A run that fails is reported as
 * [Result.failure] and logged, never as a success; a periodic job runs again at its next period regardless.
 */
class ScheduledBackupWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val dependencies: ScheduledBackupDependencies? = null,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = dependencies ?: (applicationContext as? ScheduledBackupDependenciesProvider)?.scheduledBackupDependencies
        if (deps == null) {
            Log.w(TAG, "The scheduled backup ran in an application that does not provide its dependencies; nothing was backed up.")
            return Result.failure()
        }
        val run = deps.runScheduledBackup(retry = inputData.getBoolean(BACKUP_RETRY_INPUT_KEY, false))
        // Tell the person: a notification, or the same words in the app at the next launch (owner, "1 A").
        deps.reporter.report(run)
        return run.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                deps.errorLog.w(TAG, "the scheduled backup failed: ${error.message}", error)
                Result.failure()
            },
        )
    }

    private companion object {
        const val TAG = "ScheduledBackupWorker"
    }
}

/**
 * Makes WorkManager's job match the settings: one unique periodic job while the backup is on and has a folder,
 * at the chosen frequency (daily is 1 day, weekly 7, monthly 30), and none otherwise. A change of frequency
 * updates the one job in place. No constraints are added (none is ruled).
 *
 * **The first run waits one whole interval** (owner 3.2): the request has that as its initial delay. Without it WorkManager
 * runs a periodic job's first run at once, so turning the switch on, or off and on, made a backup every time. `apply` is
 * called only when the person changes a setting, never at launch (`BackupViewModel.changeSchedule`), so this delay is
 * only ever a fresh start of the schedule; a change of frequency updates the pending job in place.
 */
class WorkManagerBackupScheduler(private val workManager: WorkManager) : BackupScheduler {
    override fun apply(settings: BackupScheduleSettings) {
        if (!settings.enabled || settings.folderUri == null) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val days = when (settings.frequency) {
            BackupFrequency.DAILY -> 1L
            BackupFrequency.WEEKLY -> 7L
            BackupFrequency.MONTHLY -> 30L
        }
        val request = PeriodicWorkRequest.Builder(ScheduledBackupWorker::class.java, days, TimeUnit.DAYS)
            .setInitialDelay(days, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "journal-backup"
    }
}
