package com.zynergylabs.forager.app.ui.backup

import com.zynergylabs.forager.app.domain.BackupException
import com.zynergylabs.forager.app.domain.BackupFiles
import com.zynergylabs.forager.app.domain.BackupReport
import com.zynergylabs.forager.app.domain.BackupSchedulePreferences
import com.zynergylabs.forager.app.domain.BackupTarget
import com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy
import com.zynergylabs.forager.app.domain.UnreadablePhotosException
import com.zynergylabs.forager.app.domain.BackupScheduleSettings
import com.zynergylabs.forager.app.domain.BackupScheduler
import com.zynergylabs.forager.app.domain.JournalBackup
import com.zynergylabs.forager.app.domain.RestoreMode
import com.zynergylabs.forager.app.domain.RestoreReport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A [JournalBackup] that writes fixed bytes on a backup and records what a restore was given; either can be made to
 * fail. [unreadablePhotos] makes it find that many unreadable photos: with [UnreadablePhotoPolicy.ASK] it fails
 * before writing anything (as the real one does), with SKIP it saves and reports them. [failWrite] writes a few bytes
 * and then fails, the way a write to a full or vanished file does.
 */
internal class FakeJournalBackup : JournalBackup {
    var failBackUp = false
    var failRestore = false
    var failWrite = false
    var unreadablePhotos = 0
    val restored = mutableListOf<Pair<RestoreMode, ByteArray>>()
    val policies = mutableListOf<UnreadablePhotoPolicy>()
    var backUps = 0

    /** When set, a backup writes a few bytes and then waits on it, and a restore waits on it after reading: a run still going when the screen ends. */
    var hold: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun backUp(sink: OutputStream, unreadablePhotos: UnreadablePhotoPolicy): Result<BackupReport> {
        backUps++
        policies += unreadablePhotos
        hold?.let {
            sink.write(BACKUP_BYTES, 0, 3)
            it.await()
        }
        if (failBackUp) return Result.failure(BackupException("fake: backup failed"))
        if (this.unreadablePhotos > 0 && unreadablePhotos == UnreadablePhotoPolicy.ASK) return Result.failure(UnreadablePhotosException(this.unreadablePhotos))
        if (failWrite) {
            sink.write(BACKUP_BYTES, 0, 3)
            return Result.failure(java.io.IOException("fake: the write failed"))
        }
        sink.write(BACKUP_BYTES)
        val left = if (unreadablePhotos == UnreadablePhotoPolicy.SKIP) this.unreadablePhotos else 0
        return Result.success(BackupReport(photoFiles = 0, photoFilesMissing = left, archiveBytes = BACKUP_BYTES.size.toLong()))
    }

    override suspend fun restore(source: InputStream, mode: RestoreMode): Result<RestoreReport> {
        restored += mode to source.readBytes()
        hold?.await()
        if (failRestore) return Result.failure(BackupException("fake: restore failed"))
        return Result.success(RestoreReport(mode, rowsInserted = 1, recordsSkipped = 0, rowsDropped = 0, photoFilesAdded = 0))
    }

    companion object {
        val BACKUP_BYTES = "PK-fake-backup".toByteArray()
    }
}

internal class FakeSchedulePreferences(var stored: BackupScheduleSettings = BackupScheduleSettings()) : BackupSchedulePreferences {
    var failGet = false
    var pending: com.zynergylabs.forager.app.domain.ScheduledBackupNotice? = null

    override suspend fun pendingNotice(): Result<com.zynergylabs.forager.app.domain.ScheduledBackupNotice?> = Result.success(pending)

    override suspend fun setPendingNotice(notice: com.zynergylabs.forager.app.domain.ScheduledBackupNotice?): Result<Unit> {
        pending = notice
        return Result.success(Unit)
    }

    /** The scheduled job's own files as recorded, oldest first. */
    var scheduledFiles: List<String> = emptyList()
    var failFilesGet = false
    var failFilesSet = false
    var notificationAsked = false

    override suspend fun scheduledBackupFiles(): Result<List<String>> =
        if (failFilesGet) Result.failure(IllegalStateException("fake: the record is unreadable")) else Result.success(scheduledFiles)

    override suspend fun setScheduledBackupFiles(uris: List<String>): Result<Unit> {
        if (failFilesSet) return Result.failure(IllegalStateException("fake: the record cannot be written"))
        scheduledFiles = uris
        return Result.success(Unit)
    }

    override suspend fun notificationPermissionAsked(): Result<Boolean> = Result.success(notificationAsked)

    override suspend fun setNotificationPermissionAsked(): Result<Unit> {
        notificationAsked = true
        return Result.success(Unit)
    }

    override suspend fun get(): Result<BackupScheduleSettings> =
        if (failGet) Result.failure(IllegalStateException("fake: unreadable")) else Result.success(stored)

    override suspend fun save(settings: BackupScheduleSettings): Result<Unit> {
        stored = settings
        return Result.success(Unit)
    }
}

internal class FakeScheduler : BackupScheduler {
    val applied = mutableListOf<BackupScheduleSettings>()
    override fun apply(settings: BackupScheduleSettings) {
        applied += settings
    }
}

/**
 * In-memory files by URI. [delete] is recorded in [deleted] and can be made to fail with [failDelete]; the real
 * interface has it only for a run to remove the one file it created, and the tests assert that is all it is used for.
 */
internal class FakeBackupFiles : BackupFiles {
    val written = linkedMapOf<String, ByteArrayOutputStream>()
    val contents = mutableMapOf<String, ByteArray>()
    val created = mutableListOf<Pair<String, String>>()
    val keptFolders = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var failOpen = false
    var failFolder = false
    var failDelete = false

    /** What [sizeOf] answers by URI; a URI not listed is a new, empty file (0). */
    val sizes = mutableMapOf<String, Long>()

    /** URIs whose provider does not say how big the file is. */
    val sizeUnreadable = mutableSetOf<String>()

    override fun sizeOf(uri: String): Long? = if (uri in sizeUnreadable) null else sizes[uri] ?: 0L

    override fun openForWrite(uri: String): OutputStream {
        if (failOpen) throw java.io.IOException("fake: cannot open $uri")
        return ByteArrayOutputStream().also { written[uri] = it }
    }

    override fun openForRead(uri: String): InputStream {
        if (failOpen) throw java.io.IOException("fake: cannot open $uri")
        return ByteArrayInputStream(contents.getValue(uri))
    }

    /** Every [createInFolder] call, failed or not. */
    var folderAttempts = 0

    override fun createInFolder(folderUri: String, displayName: String): BackupTarget {
        folderAttempts++
        if (failFolder) throw java.io.IOException("fake: folder unreadable")
        created += folderUri to displayName
        val uri = "$folderUri/$displayName#${created.size}"
        return BackupTarget(uri, ByteArrayOutputStream().also { written[uri] = it })
    }

    override fun delete(uri: String): Boolean {
        deleted += uri
        if (failDelete) return false
        written.remove(uri)
        return true
    }

    override fun keepAccessToFolder(folderUri: String) {
        keptFolders += folderUri
    }
}

/** A [com.zynergylabs.forager.app.domain.BackupNotifier] that records what it was asked to show, and can say it could not show it. */
internal class FakeBackupNotifier(var shows: Boolean = true) : com.zynergylabs.forager.app.domain.BackupNotifier {
    val notices = mutableListOf<com.zynergylabs.forager.app.domain.ScheduledBackupNotice>()

    override fun notify(notice: com.zynergylabs.forager.app.domain.ScheduledBackupNotice): Boolean {
        notices += notice
        return shows
    }
}
