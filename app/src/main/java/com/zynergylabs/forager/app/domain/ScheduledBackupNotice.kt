package com.zynergylabs.forager.app.domain

/**
 * What a scheduled backup tells the person when it needs to (owner, "6 option A" and the follow-up "1 A"): a
 * notification, or, when notifications are not allowed, the same words in the app once at the next launch. **The words are
 * the owner's approved texts and nothing else.**
 */
sealed class ScheduledBackupNotice {
    abstract val text: String

    /** The run failed and left nothing behind. Offered with "Try again". */
    object DidNotFinish : ScheduledBackupNotice() {
        override val text: String = "Scheduled backup didn't finish"
    }

    /** The backup was saved, with [count] unreadable photos left out. "1 photo" for one. */
    data class SavedWithSkippedPhotos(val count: Int) : ScheduledBackupNotice() {
        override val text: String =
            if (count == 1) "Scheduled backup saved. 1 photo couldn't be backed up." else "Scheduled backup saved. $count photos couldn't be backed up."
    }
}

/** Shows a [ScheduledBackupNotice] as a notification. Returns whether it was shown: `false` when notifications are not allowed. */
interface BackupNotifier {
    fun notify(notice: ScheduledBackupNotice): Boolean
}

/**
 * Reports a scheduled run's outcome (dispatch 2026-09-28-153, item 1): nothing for a clean run; [ScheduledBackupNotice.DidNotFinish]
 * for a failure; [ScheduledBackupNotice.SavedWithSkippedPhotos] for a run that left photos out. If the notification cannot be
 * shown, the notice is kept, and shown in the app once at the next launch.
 */
class ScheduledBackupReporter(
    private val notifier: BackupNotifier,
    private val preferences: BackupSchedulePreferences,
    private val errorLog: ErrorLog,
) {
    suspend fun report(result: Result<BackupReport>) {
        val notice = result.fold(
            onSuccess = { report -> if (report.photoFilesMissing > 0) ScheduledBackupNotice.SavedWithSkippedPhotos(report.photoFilesMissing) else null },
            onFailure = { ScheduledBackupNotice.DidNotFinish },
        ) ?: return
        val shown = try {
            notifier.notify(notice)
        } catch (e: Exception) {
            errorLog.w(TAG, "could not post the scheduled-backup notification: ${e.message}", e)
            false
        }
        if (!shown) {
            preferences.setPendingNotice(notice).onFailure {
                errorLog.w(TAG, "could not keep the scheduled-backup notice for the next launch: ${it.message}", it)
            }
        }
    }

    private companion object {
        const val TAG = "ScheduledBackupReporter"
    }
}
