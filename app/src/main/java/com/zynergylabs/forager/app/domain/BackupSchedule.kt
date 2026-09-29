package com.zynergylabs.forager.app.domain

import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/** How often the automatic backup runs (owner, "2 C": daily, weekly or monthly). */
enum class BackupFrequency { DAILY, WEEKLY, MONTHLY }

/**
 * The automatic backup's settings (owner, "5 C scheduled set to off by default, must be turned on by user").
 * [enabled] is false until the user turns it on, and it can only be true with a [folderUri]. [frequency] has no
 * ruled default, so it starts at [BackupFrequency.WEEKLY]; the user changes it before or after turning the switch on.
 */
data class BackupScheduleSettings(
    val enabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val folderUri: String? = null,
)

/** A file [createInFolder] made: where it is, and the stream to write it. */
class BackupTarget(val uri: String, val stream: OutputStream)

/** DataStore-backed in production, one flat file (CLAUDE.md: DataStore for flat settings). A failed read is a [Result.failure], never a default. */
interface BackupSchedulePreferences {
    suspend fun get(): Result<BackupScheduleSettings>

    suspend fun save(settings: BackupScheduleSettings): Result<Unit>

    /** A scheduled-backup notice that could not be shown as a notification, waiting to be shown in the app once; `null` when there is none. */
    suspend fun pendingNotice(): Result<ScheduledBackupNotice?>

    suspend fun setPendingNotice(notice: ScheduledBackupNotice?): Result<Unit>

    /**
     * The URIs of the backup files the scheduled job itself created and still keeps, oldest first (dispatch 2026-09-28-182,
     * item 3). This record is the only thing pruning ever deletes from: a file the job did not create is never in it.
     */
    suspend fun scheduledBackupFiles(): Result<List<String>>

    suspend fun setScheduledBackupFiles(uris: List<String>): Result<Unit>

    /** Whether the notification permission has already been asked for on turning scheduled backups on (owner 3.4: it is asked once). */
    suspend fun notificationPermissionAsked(): Result<Boolean>

    suspend fun setNotificationPermissionAsked(): Result<Unit>
}

/** Makes the operating system's job list match [settings]: periodic work when enabled, none otherwise. WorkManager in production. */
interface BackupScheduler {
    fun apply(settings: BackupScheduleSettings)
}

/**
 * The files a backup is written to and a restore reads from, addressed by the URI string the Storage Access
 * Framework gave the user. Behind an interface so the domain and the tests never touch `ContentResolver`.
 */
interface BackupFiles {
    fun openForWrite(uri: String): OutputStream

    fun openForRead(uri: String): InputStream

    /** A new file called [displayName] in the folder [folderUri]; the provider may rename it if the name is taken (nothing is overwritten). */
    fun createInFolder(folderUri: String, displayName: String): BackupTarget

    /**
     * Deletes the file at [uri] and reports whether it is gone. **Only ever called with a file this app's own backup made:**
     * the one a run created, when that run's write failed or the person cancelled (owner, "7 A"), or an older scheduled backup
     * the scheduled job recorded and now prunes past the newest 5 (owner 3.3). Never a manual backup, never a listing of the folder.
     */
    fun delete(uri: String): Boolean

    /**
     * How many bytes the file at [uri] holds now, from the provider's own size column (`OpenableColumns.SIZE`), or `null`
     * when the provider does not say. Asked of the file the Save picker returned, before anything is written to it.
     */
    fun sizeOf(uri: String): Long?

    /** Keeps read and write access to [folderUri] across restarts (a persisted URI permission). */
    fun keepAccessToFolder(folderUri: String)
}

/** `forager-backup-<date>.zip`, the default file name (approved copy), the date being [epochMillis] in [zone]. */
fun backupFileName(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "forager-backup-${Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()}.zip"

/** How many of the scheduled job's own backups are kept (owner 3.3: "keep the newest 5"). */
const val KEEP_NEWEST_SCHEDULED_BACKUPS = 5

/**
 * One scheduled run: reads the settings, and writes one new file to the chosen folder. A run with the setting off, no
 * folder, or an unreadable folder is a [Result.failure], so the caller (the worker) reports it rather than counting it
 * a success. A [retry] (the notification's Try again) does not need the setting on: it is one backup to the saved
 * folder, asked for by the person just now (dispatch 2026-09-28-182, item 7); it still needs a folder.
 *
 * **Keeping the newest [keepNewest]** (owner 3.3): after a successful run the new file's URI is added to the record the
 * preferences keep, the record is cut to its newest [keepNewest], and the ones cut off are deleted. Only URIs in that record
 * are ever deleted, so a manual backup or any other file in the folder is safe by construction, not by a name pattern.
 * The record is written **before** anything is deleted: a crash between the two leaves an unrecorded file, which is never
 * deleted (the safe side), where the other order could leave a recorded file that is already gone. A record that cannot
 * be read or written means nothing is deleted, and it is logged. A delete that fails is logged at WARN and never fails
 * the backup; that file is left out of the record (it is not retried: a file the person removed by hand would otherwise
 * stay in the record and be retried on every run, for ever).
 */
class RunScheduledBackupUseCase(
    private val backup: JournalBackup,
    private val preferences: BackupSchedulePreferences,
    private val files: BackupFiles,
    private val clock: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val errorLog: ErrorLog = ErrorLog { _, _, _ -> },
    private val keepNewest: Int = KEEP_NEWEST_SCHEDULED_BACKUPS,
) {
    suspend operator fun invoke(retry: Boolean = false): Result<BackupReport> {
        val settings = preferences.get().getOrElse { return Result.failure(it) }
        if (!settings.enabled && !retry) return Result.failure(BackupException("the scheduled backup ran while the setting is off; nothing was written"))
        val folder = settings.folderUri ?: return Result.failure(BackupException("the scheduled backup ran with no folder chosen; nothing was written"))
        val name = backupFileName(clock.nowEpochMillis(), zone)
        val target = try {
            files.createInFolder(folder, name)
        } catch (e: Exception) {
            return Result.failure(BackupException("could not create $name in the backup folder: ${e.message}", e))
        }
        // No screen to ask on: unreadable photos are skipped and counted in the report (owner, "yes that sounds good").
        val written = try {
            target.stream.use { backup.backUp(it, UnreadablePhotoPolicy.SKIP) }
        } catch (e: Exception) {
            Result.failure(e)
        }
        // A run that failed leaves nothing behind: the file it created is removed, and only that file (owner, "7 A").
        if (written.isFailure) removeIncomplete(target.uri) else keepNewestOnly(target.uri)
        return written
    }

    private suspend fun keepNewestOnly(newUri: String) {
        val recorded = preferences.scheduledBackupFiles().getOrElse {
            errorLog.w(TAG, "could not read the record of scheduled backup files; nothing was pruned and $newUri is not recorded: ${it.message}", it)
            return
        }
        val all = recorded + newUri
        val excess = all.size - keepNewest
        if (excess <= 0) {
            preferences.setScheduledBackupFiles(all).onFailure { errorLog.w(TAG, "could not write the record of scheduled backup files: ${it.message}", it) }
            return
        }
        val stale = all.take(excess)
        preferences.setScheduledBackupFiles(all.drop(excess)).onFailure {
            errorLog.w(TAG, "could not write the record of scheduled backup files; nothing was deleted: ${it.message}", it)
            return
        }
        for (uri in stale) {
            val removed = try {
                files.delete(uri)
            } catch (e: Exception) {
                errorLog.w(TAG, "could not delete the old scheduled backup $uri: ${e.message}", e)
                continue
            }
            if (!removed) errorLog.w(TAG, "could not delete the old scheduled backup $uri", BackupException("delete reported false for $uri"))
        }
    }

    private fun removeIncomplete(uri: String) {
        val removed = try {
            files.delete(uri)
        } catch (e: Exception) {
            errorLog.w(TAG, "could not delete the incomplete backup file $uri: ${e.message}", e)
            return
        }
        if (!removed) errorLog.w(TAG, "could not delete the incomplete backup file $uri", BackupException("delete reported false for $uri"))
    }

    private companion object {
        const val TAG = "RunScheduledBackup"
    }
}
