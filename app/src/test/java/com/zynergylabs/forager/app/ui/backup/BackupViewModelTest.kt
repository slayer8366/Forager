package com.zynergylabs.forager.app.ui.backup

import com.zynergylabs.forager.app.domain.BackupFrequency
import com.zynergylabs.forager.app.domain.BackupScheduleSettings
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.RestoreMode
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Backup section's rules, on the ViewModel the screen drives (the screen's own wiring is in
 * `BackupSettingsScreenTest`). Every message asserted is compared to the approved copy as a literal string, so
 * a reworded message fails here and is not silently accepted.
 */
class BackupViewModelTest {

    private val backup = FakeJournalBackup()
    private val prefs = FakeSchedulePreferences()
    private val scheduler = FakeScheduler()
    private val files = FakeBackupFiles()
    private val logged = mutableListOf<String>()
    private var reloads = 0
    private var recording = false
    private var reloadGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private fun viewModel() = BackupViewModel(
        backup = backup,
        preferences = prefs,
        scheduler = scheduler,
        files = files,
        errorLog = ErrorLog { _, message, error -> logged += "$message :: ${error.message}" },
        ioDispatcher = Dispatchers.Unconfined,
        isRecording = { recording },
        reloadAfterRestore = { reloads++; reloadGate?.await() },
    )

    private fun BackupViewModel.state() = uiState.value

    @Test
    fun `the automatic backup is off by default, with no folder, and the messages are the approved words`() {
        val vm = viewModel()
        assertFalse(vm.state().schedule.enabled)
        assertNull(vm.state().schedule.folderUri)
        assertEquals("Backup saved.", BackupMessage.BACKUP_SAVED.text)
        assertEquals("Couldn't save the backup.", BackupMessage.BACKUP_FAILED.text)
        assertEquals("Automatic backup is off until you choose a folder.", BackupMessage.AUTOMATIC_NEEDS_FOLDER.text)
        assertEquals("Restore complete.", BackupMessage.RESTORE_COMPLETE.text)
        assertEquals("Couldn't restore that backup.", BackupMessage.RESTORE_FAILED.text)
    }

    @Test
    fun `turning the automatic backup on with no folder leaves it off and says so`() {
        val vm = viewModel()

        vm.controls(vm.state()).onAutomaticChanged(true)

        assertFalse(vm.state().schedule.enabled)
        assertEquals(BackupMessage.AUTOMATIC_NEEDS_FOLDER, vm.state().message)
        assertFalse("nothing was saved as on", prefs.stored.enabled)
        assertTrue("and nothing was scheduled", scheduler.applied.none { it.enabled })
    }

    @Test
    fun `with a folder chosen the automatic backup turns on, is saved, and is scheduled at its frequency`() {
        val vm = viewModel()
        vm.controls(vm.state()).onFolderChosen("content://tree/backups")

        vm.controls(vm.state()).onAutomaticChanged(true)

        assertEquals(listOf("content://tree/backups"), files.keptFolders)
        assertTrue(vm.state().schedule.enabled)
        assertNull(vm.state().message)
        assertEquals(BackupScheduleSettings(enabled = true, frequency = BackupFrequency.WEEKLY, folderUri = "content://tree/backups"), prefs.stored)
        assertEquals(prefs.stored, scheduler.applied.last())
    }

    @Test
    fun `changing how often re-schedules an enabled backup, and turning it off cancels the schedule`() {
        val vm = viewModel()
        vm.controls(vm.state()).onFolderChosen("content://tree/backups")
        vm.controls(vm.state()).onAutomaticChanged(true)

        vm.controls(vm.state()).onFrequencyChanged(BackupFrequency.MONTHLY)
        assertEquals(BackupFrequency.MONTHLY, scheduler.applied.last().frequency)
        assertTrue(scheduler.applied.last().enabled)

        vm.controls(vm.state()).onAutomaticChanged(false)
        assertFalse(scheduler.applied.last().enabled)
        assertFalse(prefs.stored.enabled)
        assertEquals("the choices are kept for next time", BackupFrequency.MONTHLY, prefs.stored.frequency)
    }

    @Test
    fun `a saved schedule is read back when the screen starts`() {
        prefs.stored = BackupScheduleSettings(enabled = true, frequency = BackupFrequency.DAILY, folderUri = "content://tree/backups")

        val vm = viewModel()

        assertEquals(prefs.stored, vm.state().schedule)
    }

    @Test
    fun `an unreadable saved schedule is logged, and the section starts from the off default rather than guessing`() {
        prefs.failGet = true

        val vm = viewModel()

        assertFalse(vm.state().schedule.enabled)
        assertTrue("the failure was logged: $logged", logged.any { "unreadable" in it })
    }

    @Test
    fun `Back up now writes the backup to the chosen file and says Backup saved`() {
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/forager-backup.zip")

        assertEquals(1, backup.backUps)
        assertTrue(FakeJournalBackup.BACKUP_BYTES.contentEquals(files.written.getValue("content://docs/forager-backup.zip").toByteArray()))
        assertEquals(BackupMessage.BACKUP_SAVED, vm.state().message)
        assertFalse(vm.state().busy)
    }

    @Test
    fun `a backup that fails after its file exists removes the file and asks, rather than saying Couldn't save`() {
        backup.failBackUp = true
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        assertEquals(BackupPrompt.WriteFailed, vm.state().prompt)
        assertEquals(listOf("content://docs/x.zip"), files.deleted)
        assertTrue(logged.any { "backup failed" in it })
    }

    @Test
    fun `a file that cannot be opened is a failed backup, not a crash and not a success`() {
        files.failOpen = true
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        assertEquals(BackupMessage.BACKUP_FAILED, vm.state().message)
        assertEquals("the backup was never attempted", 0, backup.backUps)
    }

    @Test
    fun `choosing a file to restore asks first and restores nothing until Replace or Merge is chosen`() {
        files.contents["content://docs/b.zip"] = "PK-bytes".toByteArray()
        val vm = viewModel()

        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")

        assertEquals("content://docs/b.zip", vm.state().pendingRestoreUri)
        assertTrue(backup.restored.isEmpty())
    }

    @Test
    fun `Replace and Merge each restore the chosen file in their mode and say Restore complete`() {
        for (mode in RestoreMode.entries) {
            backup.restored.clear()
            files.contents["content://docs/b.zip"] = "PK-$mode".toByteArray()
            val vm = viewModel()
            vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")

            vm.controls(vm.state()).onRestoreConfirmed(mode)

            assertEquals(1, backup.restored.size)
            assertEquals(mode, backup.restored.single().first)
            assertEquals("PK-$mode", String(backup.restored.single().second))
            assertEquals(BackupMessage.RESTORE_COMPLETE, vm.state().message)
            assertNull("the prompt is closed", vm.state().pendingRestoreUri)
        }
    }

    @Test
    fun `Cancel closes the prompt and restores nothing`() {
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")
        assertEquals("the prompt was up", "content://docs/b.zip", vm.state().pendingRestoreUri)

        vm.controls(vm.state()).onRestoreCancelled()

        assertNull(vm.state().pendingRestoreUri)
        assertTrue(backup.restored.isEmpty())
        assertNull(vm.state().message)
    }

    @Test
    fun `a restore that fails says Couldn't restore that backup and logs why`() {
        backup.failRestore = true
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")

        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.MERGE)

        assertEquals(BackupMessage.RESTORE_FAILED, vm.state().message)
        assertTrue(logged.any { "restore failed" in it })
    }

    @Test
    fun `a chosen restore file that cannot be read is a failed restore before any prompt result`() {
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")
        files.failOpen = true

        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.REPLACE)

        assertEquals(BackupMessage.RESTORE_FAILED, vm.state().message)
        assertTrue(backup.restored.isEmpty())
    }

    // ---- item 3: unreadable photos pause the backup -------------------------------------------

    @Test
    fun `unreadable photos pause the backup before anything is written, and the prompt says how many`() {
        backup.unreadablePhotos = 3
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        assertEquals(BackupPrompt.UnreadablePhotos(3, "content://docs/x.zip"), vm.state().prompt)
        assertEquals("3 photos couldn't be backed up.", (vm.state().prompt as BackupPrompt.UnreadablePhotos).text)
        assertNull("not shown as a saved or failed backup", vm.state().message)
        assertEquals("nothing was written before the person chose", 0, files.written.getValue("content://docs/x.zip").size())
        assertTrue("and nothing was deleted", files.deleted.isEmpty())
    }

    @Test
    fun `one unreadable photo is worded in the singular`() {
        backup.unreadablePhotos = 1
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        assertEquals("1 photo couldn't be backed up.", (vm.state().prompt as BackupPrompt.UnreadablePhotos).text)
    }

    @Test
    fun `Continue without files saves the backup without them and says how many were left out`() {
        backup.unreadablePhotos = 2
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        vm.controls(vm.state()).onPhotosContinue()

        assertEquals(listOf(com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy.ASK, com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy.SKIP), backup.policies)
        assertTrue(FakeJournalBackup.BACKUP_BYTES.contentEquals(files.written.getValue("content://docs/x.zip").toByteArray()))
        assertEquals(BackupMessage.SavedWithoutPhotos(2), vm.state().message)
        assertEquals("Backup saved, but 2 photos couldn't be found and were left out.", vm.state().message!!.text)
        assertNull(vm.state().prompt)
    }

    @Test
    fun `one photo left out is worded in the singular, verb and all`() {
        assertEquals("Backup saved, but 1 photo couldn't be found and was left out.", BackupMessage.SavedWithoutPhotos(1).text)
    }

    @Test
    fun `Try again reads the photos again, and saves the backup if they can be read now`() {
        backup.unreadablePhotos = 2
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")
        backup.unreadablePhotos = 0

        vm.controls(vm.state()).onPhotosTryAgain()

        assertEquals(BackupMessage.BACKUP_SAVED, vm.state().message)
        assertNull(vm.state().prompt)
        assertTrue("it asked again, it did not skip", backup.policies.all { it == com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy.ASK })
    }

    @Test
    fun `Try again with the photos still unreadable asks again`() {
        backup.unreadablePhotos = 2
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        vm.controls(vm.state()).onPhotosTryAgain()

        assertEquals(2, (vm.state().prompt as BackupPrompt.UnreadablePhotos).count)
        assertEquals(2, backup.backUps)
    }

    @Test
    fun `Cancel saves nothing and deletes the file this run created, and only that file`() {
        backup.unreadablePhotos = 2
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        vm.controls(vm.state()).onPhotosCancel()

        assertEquals(listOf("content://docs/x.zip"), files.deleted)
        assertNull(vm.state().prompt)
        assertNull("cancelled is not a failure and not a save", vm.state().message)
    }

    // ---- item 7: a failed write removes its own file ------------------------------------------

    @Test
    fun `a write that fails after the file exists deletes that file and asks, with the owner's words`() {
        backup.failWrite = true
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        assertEquals(listOf("content://docs/x.zip"), files.deleted)
        assertEquals(BackupPrompt.WriteFailed, vm.state().prompt)
        assertEquals("Couldn't finish the backup. The incomplete file was removed.", BackupPrompt.WriteFailed.TEXT)
        assertNull("the older message is not also shown", vm.state().message)
        assertTrue("the reason is logged: $logged", logged.any { "the write failed" in it })
    }

    @Test
    fun `Try again after a failed write asks the screen to open the Save picker again`() {
        backup.failWrite = true
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        vm.controls(vm.state()).onWriteFailedTryAgain()

        assertNull(vm.state().prompt)
        assertTrue(vm.state().createFileRequested)
        vm.controls(vm.state()).onCreateFileRequestHandled()
        assertFalse(vm.state().createFileRequested)
    }

    @Test
    fun `Cancel after a failed write closes the prompt and does not ask for a file`() {
        backup.failWrite = true
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        vm.controls(vm.state()).onWriteFailedCancel()

        assertNull(vm.state().prompt)
        assertFalse(vm.state().createFileRequested)
    }

    @Test
    fun `a delete that fails is logged at warn and the person is told nothing extra`() {
        backup.failWrite = true
        files.failDelete = true
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/x.zip")

        assertEquals("still the same prompt", BackupPrompt.WriteFailed, vm.state().prompt)
        assertTrue("the failed delete is logged: $logged", logged.any { "delete" in it && "x.zip" in it })
        assertNull(vm.state().message)
    }

    // ---- item 5: no restore while recording ---------------------------------------------------

    @Test
    fun `a restore cannot be started while a track is recording, and nothing is touched`() {
        recording = true
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()

        val allowed = vm.controls(vm.state()).onRestoreRequested()

        assertFalse(allowed)
        assertEquals(BackupMessage.RESTORE_BLOCKED_WHILE_RECORDING, vm.state().message)
        assertEquals("Stop recording before restoring a backup.", vm.state().message!!.text)
        assertNull(vm.state().pendingRestoreUri)
        assertTrue(backup.restored.isEmpty())
    }

    @Test
    fun `with no recording a restore may start`() {
        val vm = viewModel()

        assertTrue(vm.controls(vm.state()).onRestoreRequested())
        assertNull(vm.state().message)
    }

    @Test
    fun `a recording that starts while the prompt is up still blocks Replace and Merge, and restores nothing`() {
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")
        recording = true

        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.REPLACE)

        assertTrue(backup.restored.isEmpty())
        assertEquals(BackupMessage.RESTORE_BLOCKED_WHILE_RECORDING, vm.state().message)
        assertNull(vm.state().pendingRestoreUri)
    }

    // ---- item 6: the loading page ----------------------------------------------------------------

    @Test
    fun `after a restore the page is Loading while the screens reload, then Done`() {
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        reloadGate = kotlinx.coroutines.CompletableDeferred()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")

        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.REPLACE)

        assertEquals("the reload has begun and is held", 1, reloads)
        assertEquals(RestorePage.LOADING, vm.state().restorePage)

        reloadGate!!.complete(Unit)

        assertEquals(RestorePage.DONE, vm.state().restorePage)
    }

    @Test
    fun `a failed restore shows no loading page and reloads nothing`() {
        backup.failRestore = true
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")

        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.MERGE)

        assertEquals(RestorePage.NONE, vm.state().restorePage)
        assertEquals(0, reloads)
    }

    @Test
    fun `tapping Done asks the screen for the Maps tab at once, and the page leaves after its animation`() {
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")
        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.REPLACE)
        assertEquals(RestorePage.DONE, vm.state().restorePage)
        assertEquals(0, vm.state().returnToMapRequest)

        vm.controls(vm.state()).onRestoreDoneTapped()

        assertEquals("the Maps tab is requested at the tap, not after the animation", 1, vm.state().returnToMapRequest)
        assertEquals(RestorePage.LEAVING, vm.state().restorePage)

        vm.controls(vm.state()).onRestorePageLeft()

        assertEquals(RestorePage.NONE, vm.state().restorePage)
    }

    @Test
    fun `Done is not offered before the reload has finished`() {
        reloadGate = kotlinx.coroutines.CompletableDeferred()
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val vm = viewModel()
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")
        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.REPLACE)

        vm.controls(vm.state()).onRestoreDoneTapped()

        assertEquals("a tap while loading does nothing", RestorePage.LOADING, vm.state().restorePage)
        assertEquals(0, vm.state().returnToMapRequest)
    }

    // ---- item 1: a notice waiting for the next launch ----------------------------------------------

    @Test
    fun `a scheduled-backup notice that could not be a notification is offered once at launch, and forgotten after`() {
        prefs.pending = com.zynergylabs.forager.app.domain.ScheduledBackupNotice.SavedWithSkippedPhotos(2)

        val vm = viewModel()

        assertEquals(com.zynergylabs.forager.app.domain.ScheduledBackupNotice.SavedWithSkippedPhotos(2), vm.state().launchNotice)
        assertEquals("Scheduled backup saved. 2 photos couldn't be backed up.", vm.state().launchNotice!!.text)

        vm.controls(vm.state()).onLaunchNoticeShown()

        assertNull(vm.state().launchNotice)
        assertNull("gone from the store, so the next launch does not show it again", prefs.pending)
        assertNull(viewModel().state().launchNotice)
    }

    @Test
    fun `with no notice waiting nothing is offered`() {
        assertNull(viewModel().state().launchNotice)
    }

    // ---- item 3: asking before a file with contents is replaced ---------------------------------

    @Test
    fun `a file that already has contents is asked about first, and nothing is opened or written`() {
        files.sizes["content://docs/old.zip"] = 4096L
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/old.zip")

        assertEquals(BackupPrompt.ReplaceExisting("content://docs/old.zip"), vm.state().prompt)
        assertEquals("Replace the existing backup file?", BackupPrompt.ReplaceExisting.TEXT)
        assertTrue("the file was not even opened for writing", files.written.isEmpty())
        assertEquals(0, backup.backUps)
        assertTrue(files.deleted.isEmpty())
    }

    @Test
    fun `Replace writes the backup into the file`() {
        files.sizes["content://docs/old.zip"] = 4096L
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/old.zip")

        vm.controls(vm.state()).onReplaceExistingConfirmed()

        assertTrue(FakeJournalBackup.BACKUP_BYTES.contentEquals(files.written.getValue("content://docs/old.zip").toByteArray()))
        assertEquals(BackupMessage.BACKUP_SAVED, vm.state().message)
        assertNull(vm.state().prompt)
    }

    @Test
    fun `Cancel writes nothing, deletes nothing, and returns to the Backup section`() {
        files.sizes["content://docs/old.zip"] = 4096L
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/old.zip")

        vm.controls(vm.state()).onReplaceExistingCancelled()

        assertNull(vm.state().prompt)
        assertTrue(files.written.isEmpty())
        assertTrue("the file that was there is left alone", files.deleted.isEmpty())
        assertEquals(0, backup.backUps)
        assertFalse(vm.state().busy)
    }

    @Test
    fun `a new empty file is not asked about`() {
        files.sizes["content://docs/new.zip"] = 0L
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/new.zip")

        assertNull(vm.state().prompt)
        assertEquals(BackupMessage.BACKUP_SAVED, vm.state().message)
    }

    @Test
    fun `a size the provider does not give is asked about, the safe way round, and the reason is logged`() {
        files.sizeUnreadable += "content://docs/mystery.zip"
        val vm = viewModel()

        vm.controls(vm.state()).onBackUpNow("content://docs/mystery.zip")

        assertEquals(BackupPrompt.ReplaceExisting("content://docs/mystery.zip"), vm.state().prompt)
        assertTrue(files.written.isEmpty())
        assertTrue("logged: $logged", logged.any { "size" in it && "mystery.zip" in it })
    }

    @Test
    fun `once Replace is confirmed, a Try again after unreadable photos does not ask again`() {
        files.sizes["content://docs/old.zip"] = 4096L
        backup.unreadablePhotos = 2
        val vm = viewModel()
        vm.controls(vm.state()).onBackUpNow("content://docs/old.zip")
        vm.controls(vm.state()).onReplaceExistingConfirmed()
        assertEquals(BackupPrompt.UnreadablePhotos(2, "content://docs/old.zip"), vm.state().prompt)

        vm.controls(vm.state()).onPhotosTryAgain()

        assertTrue("asked about the photos again, not about the file", vm.state().prompt is BackupPrompt.UnreadablePhotos)
    }

    // ---- dispatch 2026-09-28-182, item 4: the notification permission is asked once (owner 3.4) ----

    @Test
    fun `turning scheduled backups on for the first time asks for the notification permission, and off and on again does not`() {
        val vm = viewModel()
        vm.controls(vm.state()).onFolderChosen("content://tree/backups")

        vm.controls(vm.state()).onAutomaticChanged(true)

        assertTrue("asked the first time", vm.state().askNotificationPermission)
        assertTrue("and remembered that it was", prefs.notificationAsked)
        vm.controls(vm.state()).onNotificationPermissionRequestHandled()
        assertFalse("the one-shot is cleared once acted on", vm.state().askNotificationPermission)

        vm.controls(vm.state()).onAutomaticChanged(false)
        vm.controls(vm.state()).onAutomaticChanged(true)

        assertFalse("not asked again, whatever the answer was", vm.state().askNotificationPermission)
        assertTrue("and the schedule is on", vm.state().schedule.enabled)
    }

    @Test
    fun `having been asked in an earlier session, turning scheduled backups on asks for nothing`() {
        prefs.notificationAsked = true
        val vm = viewModel()
        vm.controls(vm.state()).onFolderChosen("content://tree/backups")

        vm.controls(vm.state()).onAutomaticChanged(true)

        assertFalse(vm.state().askNotificationPermission)
        assertTrue(vm.state().schedule.enabled)
    }

    @Test
    fun `turning it on with no folder does not use up the one ask`() {
        val vm = viewModel()

        vm.controls(vm.state()).onAutomaticChanged(true)

        assertFalse(vm.state().askNotificationPermission)
        assertFalse("still unspent", prefs.notificationAsked)
    }

    /**
     * Dispatch 2026-09-28-658 (D1): the screen ending mid-backup cancels the run, and a cancel is not a failed write.
     * The ViewModel is held in a store, as `MainActivity` holds it, so `clear()` reaches the real `onCleared` that
     * cancels its scope. With the cancel caught as a failure (the bug), this sees the file removed, the write-failed
     * prompt and "backup failed" in the log.
     */
    @Test
    fun `a backup cancelled by the screen ending is not a failure and keeps the file`() {
        backup.hold = kotlinx.coroutines.CompletableDeferred()
        val store = androidx.lifecycle.ViewModelStore()
        val vm = heldIn(store)
        vm.controls(vm.state()).onBackUpNow("content://docs/new.zip")
        assertTrue("precondition: the backup is under way", vm.state().busy)
        assertEquals("precondition: it has begun writing", 1, backup.backUps)

        store.clear()

        assertTrue("the file is not removed as a failed write: ${files.deleted}", files.deleted.isEmpty())
        assertNull("no write-failed prompt", vm.state().prompt)
        assertNull("no message", vm.state().message)
        assertTrue("nothing logged as a failed backup: $logged", logged.none { "backup failed" in it })
    }

    /** Dispatch 2026-09-28-658 (D1), the restore side: a cancel is never "Couldn't restore that backup." */
    @Test
    fun `a restore cancelled by the screen ending is not shown as failed`() {
        backup.hold = kotlinx.coroutines.CompletableDeferred()
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        val store = androidx.lifecycle.ViewModelStore()
        val vm = heldIn(store)
        vm.controls(vm.state()).onRestoreFileChosen("content://docs/b.zip")
        vm.controls(vm.state()).onRestoreConfirmed(RestoreMode.MERGE)
        assertEquals("precondition: the restore has begun", 1, backup.restored.size)
        assertTrue("precondition: and is under way", vm.state().busy)

        store.clear()

        assertTrue("never shown as failed: ${vm.state().message}", vm.state().message != BackupMessage.RESTORE_FAILED)
        assertTrue("nothing logged as a failed restore: $logged", logged.none { "restore failed" in it })
    }

    private fun heldIn(store: androidx.lifecycle.ViewModelStore): BackupViewModel =
        androidx.lifecycle.ViewModelProvider(
            store,
            viewModelFactory { initializer { viewModel() } },
        )[BackupViewModel::class.java]
}
