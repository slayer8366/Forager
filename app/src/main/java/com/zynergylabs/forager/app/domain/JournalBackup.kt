package com.zynergylabs.forager.app.domain

import java.io.InputStream
import java.io.OutputStream

/**
 * What a restore does with the journal data already on the phone (owner, "1 C ask to replace or
 * merge"; the prompt's own copy is the definition the user reads).
 *
 * - [REPLACE] deletes the phone's journal data and puts the backup's in its place.
 * - [MERGE] keeps the phone's journal data and adds the backup's records the phone lacks, skipping any
 *   record whose id is already there, so the phone's copy wins. The rule for the rows that hang off a
 *   record is on [com.zynergylabs.forager.app.data.backup.RoomJournalBackup].
 */
enum class RestoreMode { REPLACE, MERGE }

/** What a finished backup wrote. [photoFilesMissing] counts `log_photos` rows whose file could not be read and was left out (only ever above 0 when the run was told to skip them). */
data class BackupReport(val photoFiles: Int, val photoFilesMissing: Int, val archiveBytes: Long)

/**
 * What a finished restore changed: [rowsInserted] across the journal tables, [recordsSkipped] (Merge: a record
 * whose id was already on the phone), [rowsDropped] (Merge: a dependent row whose record is missing, so it
 * would have dangled) and [photoFilesAdded].
 */
data class RestoreReport(
    val mode: RestoreMode,
    val rowsInserted: Int,
    val recordsSkipped: Int,
    val rowsDropped: Int,
    val photoFilesAdded: Int,
)

/** Why a backup or restore failed, in words for the log. The user is only ever shown the approved message, never this text. */
open class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * What a backup does about photo files it cannot read (owner, "3 A"): [ASK] stops before writing anything and fails
 * with [UnreadablePhotosException] so the person can choose; [SKIP] leaves them out, saves the backup, and reports how
 * many in [BackupReport.photoFilesMissing]. A scheduled run has no screen to ask on, so it always skips.
 */
enum class UnreadablePhotoPolicy { ASK, SKIP }

/** A backup found [count] photo files it could not read and was told to ask. Nothing was written to the sink. */
class UnreadablePhotosException(val count: Int) : BackupException("$count photo file(s) could not be read")

/**
 * The journal backup, behind an interface this project owns (CLAUDE.md, Architecture): the domain and the
 * screens depend on this, not on Room, SQLite or zip. Both operations report a failure as a
 * [Result.failure] carrying a [BackupException]; a failed restore leaves the phone exactly as it was.
 *
 * The streams are the caller's (a Storage Access Framework document, a folder file); this closes neither.
 */
interface JournalBackup {
    suspend fun backUp(sink: OutputStream, unreadablePhotos: UnreadablePhotoPolicy = UnreadablePhotoPolicy.ASK): Result<BackupReport>

    suspend fun restore(source: InputStream, mode: RestoreMode): Result<RestoreReport>
}
