package com.zynergylabs.forager.app.ui.backup

import androidx.lifecycle.ViewModel
import com.zynergylabs.forager.app.domain.BackupFiles
import com.zynergylabs.forager.app.domain.BackupFrequency
import com.zynergylabs.forager.app.domain.BackupSchedulePreferences
import com.zynergylabs.forager.app.domain.BackupScheduleSettings
import com.zynergylabs.forager.app.domain.BackupScheduler
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.JournalBackup
import com.zynergylabs.forager.app.domain.RestoreMode
import com.zynergylabs.forager.app.domain.ScheduledBackupNotice
import com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy
import com.zynergylabs.forager.app.domain.UnreadablePhotosException
import com.zynergylabs.forager.app.domain.BackupException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every string the Backup section shows the user beyond its labels: the owner's approved copy, and only that.
 * A sealed class rather than an enum because two messages carry a count.
 */
sealed class BackupMessage(val text: String) {
    object BACKUP_SAVED : BackupMessage("Backup saved.")
    object BACKUP_FAILED : BackupMessage("Couldn't save the backup.")
    object AUTOMATIC_NEEDS_FOLDER : BackupMessage("Automatic backup is off until you choose a folder.")
    object RESTORE_COMPLETE : BackupMessage("Restore complete.")
    object RESTORE_FAILED : BackupMessage("Couldn't restore that backup.")
    object RESTORE_BLOCKED_WHILE_RECORDING : BackupMessage("Stop recording before restoring a backup.")

    /** The backup saved, with [count] unreadable photos left out. "1 photo ... was" for one, "N photos ... were" for more. */
    data class SavedWithoutPhotos(val count: Int) : BackupMessage(
        if (count == 1) "Backup saved, but 1 photo couldn't be found and was left out."
        else "Backup saved, but $count photos couldn't be found and were left out.",
    )
}

/** A question the Backup section is waiting on: a dialog with buttons. */
sealed interface BackupPrompt {
    /** [count] photos could not be read; the run paused before writing anything into [uri]. */
    data class UnreadablePhotos(val count: Int, val uri: String) : BackupPrompt {
        val text: String get() = if (count == 1) "1 photo couldn't be backed up." else "$count photos couldn't be backed up."
    }

    /** The file the person picked already has contents: ask before anything is written to it (owner, "3 A"). */
    data class ReplaceExisting(val uri: String) : BackupPrompt {
        companion object {
            const val TEXT = "Replace the existing backup file?"
        }
    }

    /** The write into a file failed and the file this run created was removed. */
    object WriteFailed : BackupPrompt {
        const val TEXT = "Couldn't finish the backup. The incomplete file was removed."
    }
}

/** Where the restore's loading page is: not shown, loading the reloaded journal, done and waiting for the tap, or leaving (the tap's animation). */
enum class RestorePage { NONE, LOADING, DONE, LEAVING }

/** What the Backup section draws. [pendingRestoreUri] is set while the "Restore this backup?" prompt is up. */
data class BackupUiState(
    val schedule: BackupScheduleSettings = BackupScheduleSettings(),
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val pendingRestoreUri: String? = null,
    val prompt: BackupPrompt? = null,
    val restorePage: RestorePage = RestorePage.NONE,
    /** One-shot: the section should open the Save picker again (a failed write removed its file). Cleared by [BackupControls.onCreateFileRequestHandled]. */
    val createFileRequested: Boolean = false,
    /** Counts up each time the person taps Done; the screen goes to the Maps tab when it changes. */
    val returnToMapRequest: Int = 0,
    /** A scheduled-backup notice that could not be shown as a notification, to be shown in the app once at launch. */
    val launchNotice: ScheduledBackupNotice? = null,
    /** One-shot: the section should ask for the notification permission, which is only ever the first time scheduled backups are turned on (owner 3.4). Cleared by [BackupControls.onNotificationPermissionRequestHandled]. */
    val askNotificationPermission: Boolean = false,
)

/** The Backup section's state and callbacks, as [com.zynergylabs.forager.app.ui.availability.SettingsContent] takes them. */
data class BackupControls(
    val state: BackupUiState = BackupUiState(),
    val onBackUpNow: (uri: String) -> Unit = {},
    val onAutomaticChanged: (Boolean) -> Unit = {},
    val onFrequencyChanged: (BackupFrequency) -> Unit = {},
    val onFolderChosen: (uri: String) -> Unit = {},
    /** Asked before the file picker opens: false (with the reason shown) when a restore must not start now. */
    val onRestoreRequested: () -> Boolean = { true },
    val onRestoreFileChosen: (uri: String) -> Unit = {},
    val onRestoreConfirmed: (RestoreMode) -> Unit = {},
    val onRestoreCancelled: () -> Unit = {},
    val onPhotosTryAgain: () -> Unit = {},
    val onPhotosContinue: () -> Unit = {},
    val onPhotosCancel: () -> Unit = {},
    val onWriteFailedTryAgain: () -> Unit = {},
    val onWriteFailedCancel: () -> Unit = {},
    val onCreateFileRequestHandled: () -> Unit = {},
    val onRestoreDoneTapped: () -> Unit = {},
    val onRestorePageLeft: () -> Unit = {},
    val onReplaceExistingConfirmed: () -> Unit = {},
    val onReplaceExistingCancelled: () -> Unit = {},
    /** The screen has shown [BackupUiState.launchNotice]; it is forgotten so it shows once. */
    val onLaunchNoticeShown: () -> Unit = {},
    /** The section has acted on [BackupUiState.askNotificationPermission]. */
    val onNotificationPermissionRequestHandled: () -> Unit = {},
)

/**
 * The Backup section's logic: the manual backup, the automatic one's settings, and the restore prompt.
 *
 * - **What the user is told** is only ever a [BackupMessage] (the approved copy). The reason for a failure goes to
 *   [errorLog] and nowhere else on screen.
 * - **The automatic backup is off until the user turns it on, and can only be on with a folder** (owner, "5 C"):
 *   turning it on with no folder leaves it off and says [BackupMessage.AUTOMATIC_NEEDS_FOLDER].
 * - **A restore asks first.** Choosing a file only opens the prompt; nothing is restored until Replace or Merge.
 * - Its own scope on [ioDispatcher] (cancelled in [onCleared]) rather than `viewModelScope`, so a plain JVM test can
 *   run it on `Dispatchers.Unconfined` without an Android main looper.
 */
class BackupViewModel(
    private val backup: JournalBackup,
    private val preferences: BackupSchedulePreferences,
    private val scheduler: BackupScheduler,
    private val files: BackupFiles,
    private val errorLog: ErrorLog,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Whether a track is recording now; a restore is blocked while one is (owner, "5 A"). */
    private val isRecording: () -> Boolean = { false },
    /**
     * Called after a restore that worked; returns when every screen has read the restored journal again. The app's
     * screens read Room once and hold what they read (there is no Flow anywhere in `data/local`), so without this a
     * restore would change the database and leave every open screen showing the old data. `MainActivity` passes it.
     */
    private val reloadAfterRestore: suspend () -> Unit = {},
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    init {
        scope.launch {
            preferences.get().fold(
                onSuccess = { saved -> _uiState.update { it.copy(schedule = saved) } },
                onFailure = { errorLog.w(TAG, "could not read the saved backup schedule; the section starts from the off default", it) },
            )
            // A scheduled-backup notice that could not be a notification (owner, "1 A"): offered once, at launch.
            preferences.pendingNotice().fold(
                onSuccess = { notice -> if (notice != null) _uiState.update { it.copy(launchNotice = notice) } },
                onFailure = { errorLog.w(TAG, "could not read the scheduled-backup notice kept for launch: ${it.message}", it) },
            )
        }
    }

    /** The section's controls for [state], the state the screen collected; the callbacks act on the live state. */
    fun controls(state: BackupUiState): BackupControls = BackupControls(
        state = state,
        onBackUpNow = ::onBackUpNow,
        onAutomaticChanged = ::onAutomaticChanged,
        onFrequencyChanged = ::onFrequencyChanged,
        onFolderChosen = ::onFolderChosen,
        onRestoreRequested = ::onRestoreRequested,
        onRestoreFileChosen = ::onRestoreFileChosen,
        onRestoreConfirmed = ::onRestoreConfirmed,
        onRestoreCancelled = ::onRestoreCancelled,
        onPhotosTryAgain = ::onPhotosTryAgain,
        onPhotosContinue = ::onPhotosContinue,
        onPhotosCancel = ::onPhotosCancel,
        onWriteFailedTryAgain = ::onWriteFailedTryAgain,
        onWriteFailedCancel = ::onWriteFailedCancel,
        onCreateFileRequestHandled = ::onCreateFileRequestHandled,
        onRestoreDoneTapped = ::onRestoreDoneTapped,
        onRestorePageLeft = ::onRestorePageLeft,
        onReplaceExistingConfirmed = ::onReplaceExistingConfirmed,
        onReplaceExistingCancelled = ::onReplaceExistingCancelled,
        onLaunchNoticeShown = ::onLaunchNoticeShown,
        onNotificationPermissionRequestHandled = ::onNotificationPermissionRequestHandled,
    )

    /**
     * The Save picker returned [uri]. **If that file already has contents, ask first** ("Replace the existing backup
     * file?", owner "3 A"), before anything is opened or written, since opening it truncates it. "Has contents" is the
     * provider's own size above 0 (`OpenableColumns.SIZE`); a size the provider will not give is asked about too, and
     * logged: the safe way round, since a skipped question destroys an older backup and an asked one costs a tap.
     */
    fun onBackUpNow(uri: String) {
        val size = try {
            files.sizeOf(uri)
        } catch (e: Exception) {
            errorLog.w(TAG, "could not read the size of the backup file $uri: ${e.message}", e)
            null
        }
        if (size == null) errorLog.w(TAG, "the size of $uri is not known; asking before writing to it", BackupException("size unknown for $uri"))
        if (size == null || size > 0L) {
            _uiState.update { it.copy(prompt = BackupPrompt.ReplaceExisting(uri), message = null) }
            return
        }
        runBackUp(uri, UnreadablePhotoPolicy.ASK)
    }

    fun onReplaceExistingConfirmed() {
        val prompt = _uiState.value.prompt as? BackupPrompt.ReplaceExisting ?: return
        runBackUp(prompt.uri, UnreadablePhotoPolicy.ASK)
    }

    fun onReplaceExistingCancelled() {
        _uiState.update { if (it.prompt is BackupPrompt.ReplaceExisting) it.copy(prompt = null) else it }
    }

    fun onLaunchNoticeShown() {
        _uiState.update { it.copy(launchNotice = null) }
        scope.launch {
            preferences.setPendingNotice(null).onFailure { errorLog.w(TAG, "could not forget the notice that was shown at launch: ${it.message}", it) }
        }
    }

    /**
     * Writes a backup into the file at [uri], which the Save picker has already created. [policy] is ASK on the first
     * run: unreadable photos are found before anything is written and raise the prompt (owner, "3 A"). A failure once the
     * file exists deletes it, and only it, and asks (owner, "7 A"). A file that could not even be opened created nothing,
     * so it is the older, plainer "Couldn't save the backup."
     */
    private fun runBackUp(uri: String, policy: UnreadablePhotoPolicy) {
        _uiState.update { it.copy(busy = true, message = null, prompt = null) }
        scope.launch {
            val sink = try {
                files.openForWrite(uri)
            } catch (e: Exception) {
                errorLog.w(TAG, "backup failed: the file could not be opened: ${e.message}", e)
                _uiState.update { it.copy(busy = false, message = BackupMessage.BACKUP_FAILED) }
                return@launch
            }
            val written = try {
                sink.use { backup.backUp(it, policy) }
            } catch (e: Exception) {
                Result.failure(e)
            }
            written.fold(
                onSuccess = { report ->
                    val message = if (report.photoFilesMissing > 0) BackupMessage.SavedWithoutPhotos(report.photoFilesMissing) else BackupMessage.BACKUP_SAVED
                    _uiState.update { it.copy(busy = false, message = message) }
                },
                onFailure = { error ->
                    if (error is UnreadablePhotosException) {
                        _uiState.update { it.copy(busy = false, prompt = BackupPrompt.UnreadablePhotos(error.count, uri)) }
                    } else {
                        errorLog.w(TAG, "backup failed: ${error.message}", error)
                        removeCreatedFile(uri)
                        _uiState.update { it.copy(busy = false, prompt = BackupPrompt.WriteFailed) }
                    }
                },
            )
        }
    }

    /** Deletes the file this run created. A delete that does not work is logged at WARN and nothing more is said. */
    private fun removeCreatedFile(uri: String) {
        val removed = try {
            files.delete(uri)
        } catch (e: Exception) {
            errorLog.w(TAG, "could not delete the incomplete backup file $uri: ${e.message}", e)
            return
        }
        if (!removed) errorLog.w(TAG, "could not delete the incomplete backup file $uri", BackupException("delete reported false for $uri"))
    }

    fun onPhotosTryAgain() {
        val prompt = _uiState.value.prompt as? BackupPrompt.UnreadablePhotos ?: return
        runBackUp(prompt.uri, UnreadablePhotoPolicy.ASK)
    }

    fun onPhotosContinue() {
        val prompt = _uiState.value.prompt as? BackupPrompt.UnreadablePhotos ?: return
        runBackUp(prompt.uri, UnreadablePhotoPolicy.SKIP)
    }

    fun onPhotosCancel() {
        val prompt = _uiState.value.prompt as? BackupPrompt.UnreadablePhotos ?: return
        removeCreatedFile(prompt.uri)
        _uiState.update { it.copy(prompt = null) }
    }

    fun onWriteFailedTryAgain() {
        _uiState.update { it.copy(prompt = null, createFileRequested = true) }
    }

    fun onWriteFailedCancel() {
        _uiState.update { it.copy(prompt = null) }
    }

    fun onCreateFileRequestHandled() {
        _uiState.update { it.copy(createFileRequested = false) }
    }

    fun onAutomaticChanged(enabled: Boolean) {
        val current = _uiState.value.schedule
        if (enabled && current.folderUri == null) {
            _uiState.update { it.copy(message = BackupMessage.AUTOMATIC_NEEDS_FOLDER) }
            return
        }
        changeSchedule(current.copy(enabled = enabled), afterApplied = if (enabled) ::askNotificationPermissionOnce else null)
    }

    fun onNotificationPermissionRequestHandled() {
        _uiState.update { it.copy(askNotificationPermission = false) }
    }

    /**
     * Owner 3.4: the notification permission is asked only the first time scheduled backups are turned on. Whether it has been
     * asked is remembered before the request is raised, so a declined answer is never asked about again (the in-app notice at the
     * next launch covers it). If that cannot be read or written, it is not asked: asking twice is the thing ruled out.
     */
    private suspend fun askNotificationPermissionOnce() {
        val asked = preferences.notificationPermissionAsked().getOrElse {
            errorLog.w(TAG, "could not read whether the notification permission was asked; not asking: ${it.message}", it)
            return
        }
        if (asked) return
        preferences.setNotificationPermissionAsked().onFailure {
            errorLog.w(TAG, "could not remember that the notification permission was asked; not asking: ${it.message}", it)
            return
        }
        _uiState.update { it.copy(askNotificationPermission = true) }
    }

    fun onFrequencyChanged(frequency: BackupFrequency) = changeSchedule(_uiState.value.schedule.copy(frequency = frequency))

    fun onFolderChosen(uri: String) {
        try {
            files.keepAccessToFolder(uri)
        } catch (e: Exception) {
            errorLog.w(TAG, "could not keep access to the chosen backup folder: ${e.message}", e)
            return
        }
        changeSchedule(_uiState.value.schedule.copy(folderUri = uri))
    }

    /** Asked before the file picker opens. False, with the reason shown, while a track is recording: nothing is staged or touched (owner, "5 A"). */
    fun onRestoreRequested(): Boolean {
        if (isRecording()) {
            _uiState.update { it.copy(message = BackupMessage.RESTORE_BLOCKED_WHILE_RECORDING) }
            return false
        }
        _uiState.update { it.copy(message = null) }
        return true
    }

    /** The Done icon was tapped: the Maps tab is asked for at once (under the page, which then animates away), and the page is LEAVING. */
    fun onRestoreDoneTapped() {
        _uiState.update {
            if (it.restorePage != RestorePage.DONE) it else it.copy(restorePage = RestorePage.LEAVING, returnToMapRequest = it.returnToMapRequest + 1)
        }
    }

    /** The page's exit animation has ended. */
    fun onRestorePageLeft() {
        _uiState.update { if (it.restorePage == RestorePage.LEAVING) it.copy(restorePage = RestorePage.NONE) else it }
    }

    fun onRestoreFileChosen(uri: String) {
        _uiState.update { it.copy(pendingRestoreUri = uri, message = null) }
    }

    fun onRestoreCancelled() {
        _uiState.update { it.copy(pendingRestoreUri = null) }
    }

    fun onRestoreConfirmed(mode: RestoreMode) {
        val uri = _uiState.value.pendingRestoreUri ?: return
        if (isRecording()) {
            // A recording began while the prompt was up: nothing is staged, nothing is touched.
            _uiState.update { it.copy(pendingRestoreUri = null, message = BackupMessage.RESTORE_BLOCKED_WHILE_RECORDING) }
            return
        }
        _uiState.update { it.copy(pendingRestoreUri = null, busy = true, message = null) }
        scope.launch {
            val restored = try {
                files.openForRead(uri).use { source -> backup.restore(source, mode) }
            } catch (e: Exception) {
                Result.failure(e)
            }
            restored.onFailure { errorLog.w(TAG, "restore failed: ${it.message}", it) }
            if (restored.isFailure) {
                _uiState.update { it.copy(busy = false, message = BackupMessage.RESTORE_FAILED) }
                return@launch
            }
            // The restore has committed. The loading page covers the screens while every one of them reads again.
            _uiState.update { it.copy(busy = false, message = BackupMessage.RESTORE_COMPLETE, restorePage = RestorePage.LOADING) }
            try {
                reloadAfterRestore()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorLog.w(TAG, "the restored journal was written, but a screen could not read it again: ${e.message}", e)
            }
            _uiState.update { it.copy(restorePage = RestorePage.DONE) }
        }
    }

    /** Saves [next], then shows it and hands it to the scheduler; a save that fails is logged and changes nothing. */
    private fun changeSchedule(next: BackupScheduleSettings, afterApplied: (suspend () -> Unit)? = null) {
        _uiState.update { it.copy(message = null) }
        scope.launch {
            preferences.save(next).fold(
                onSuccess = {
                    _uiState.update { it.copy(schedule = next) }
                    scheduler.apply(next)
                    afterApplied?.invoke()
                },
                onFailure = { errorLog.w(TAG, "could not save the backup schedule; it is unchanged: ${it.message}", it) },
            )
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "BackupViewModel"
    }
}
