package com.zynergylabs.forager.app.data.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.zynergylabs.forager.app.MainActivity
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.domain.BackupNotifier
import com.zynergylabs.forager.app.domain.ScheduledBackupNotice

/** The "Backups" channel's id. */
internal const val BACKUP_CHANNEL_ID = "backups"

/** Tags the one-time job "Try again" enqueues. */
internal const val BACKUP_RETRY_WORK_TAG = "journal-backup-retry"

/** The extra a notification's tap carries so the app opens at the Backup section (in Tools, then Settings). */
const val EXTRA_OPEN_BACKUP_SECTION = "com.zynergylabs.forager.app.OPEN_BACKUP_SECTION"

private const val DID_NOT_FINISH_NOTIFICATION_ID = 1101
private const val SAVED_WITH_SKIPPED_NOTIFICATION_ID = 1102

/** Whether [intent] asks the app to open at the Backup section. */
fun opensBackupSection(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_OPEN_BACKUP_SECTION, false) == true

/**
 * Posts a [ScheduledBackupNotice] in the **"Backups"** channel (owner, "1 A": the channel's user-visible name is exactly
 * that, from `strings.xml` like the app's other channels). The two notifications are the approved texts, nothing else;
 * the failed one has a "Try again" action; tapping either opens the app at the Backup section.
 *
 * **Importance is DEFAULT**, which nobody ruled: a failed backup should show in the shade and make a sound, but it is not
 * an emergency (the off-track alert is HIGH) and not a silent status (recording is LOW). Posting is best-effort in the
 * way the app's other alerts are: without POST_NOTIFICATIONS (API 33+) or with notifications switched off, nothing is
 * posted and [notify] says so, so the caller can keep the notice for the app instead.
 */
class AndroidBackupNotifier(context: Context) : BackupNotifier {
    private val context = context.applicationContext

    init {
        createBackupNotificationChannel(this.context)
    }

    override fun notify(notice: ScheduledBackupNotice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        createBackupNotificationChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_BACKUP_SECTION, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, BACKUP_CHANNEL_ID)
            .setContentTitle(notice.text)
            .setSmallIcon(R.drawable.ic_track_recording)
            .setContentIntent(open)
            .setAutoCancel(true)
        val id = when (notice) {
            ScheduledBackupNotice.DidNotFinish -> {
                val retry = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, BackupRetryReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(0, context.getString(R.string.backup_notification_try_again), retry)
                DID_NOT_FINISH_NOTIFICATION_ID
            }
            is ScheduledBackupNotice.SavedWithSkippedPhotos -> SAVED_WITH_SKIPPED_NOTIFICATION_ID
        }
        manager.notify(id, builder.build())
        return true
    }
}

internal fun createBackupNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            BACKUP_CHANNEL_ID,
            context.getString(R.string.backup_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
}

/**
 * The "Try again" action on the "didn't finish" notification: one backup to the same folder, run by the same worker the
 * schedule uses, as a one-time job that is marked as a retry so it runs whether or not the schedule is still on (a folder
 * whose permission is gone fails as any run does, with the same notification, which opens the Backup section). The
 * notification is dismissed.
 */
class BackupRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context.applicationContext).enqueue(
            OneTimeWorkRequest.Builder(ScheduledBackupWorker::class.java)
                .addTag(BACKUP_RETRY_WORK_TAG)
                .setInputData(Data.Builder().putBoolean(BACKUP_RETRY_INPUT_KEY, true).build())
                .build(),
        )
        NotificationManagerCompat.from(context).cancel(DID_NOT_FINISH_NOTIFICATION_ID)
    }
}
