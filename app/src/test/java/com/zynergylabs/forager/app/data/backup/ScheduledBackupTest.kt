package com.zynergylabs.forager.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.zynergylabs.forager.app.data.repository.DataStoreBackupSchedulePreferences
import com.zynergylabs.forager.app.domain.BackupFrequency
import com.zynergylabs.forager.app.domain.BackupScheduleSettings
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.RunScheduledBackupUseCase
import com.zynergylabs.forager.app.domain.ScheduledBackupNotice
import com.zynergylabs.forager.app.domain.ScheduledBackupReporter
import com.zynergylabs.forager.app.domain.backupFileName
import com.zynergylabs.forager.app.ui.backup.FakeBackupFiles
import com.zynergylabs.forager.app.ui.backup.FakeJournalBackup
import com.zynergylabs.forager.app.ui.backup.FakeSchedulePreferences
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The automatic backup, below the screen: the preference file, one scheduled run, the WorkManager job and the
 * worker that WorkManager calls. Off by default; one new file per run; nothing is ever deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScheduledBackupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("UTC")
    private val noon = 1_790_000_000_000L // a fixed instant, so the file name is a known date
    private val clock = CurrentTimeProvider { noon }
    private val files = FakeBackupFiles()
    private val logged = mutableListOf<String>()
    private val notifier = com.zynergylabs.forager.app.ui.backup.FakeBackupNotifier()
    private val backup = FakeJournalBackup()
    private val on = BackupScheduleSettings(enabled = true, frequency = BackupFrequency.WEEKLY, folderUri = "content://tree/backups")

    private fun useCase(prefs: BackupScheduleSettings) =
        RunScheduledBackupUseCase(backup, FakeSchedulePreferences(prefs), files, clock, zone, ErrorLog { _, m, e -> logged += "$m :: ${e.message}" })

    // ---- the settings file ---------------------------------------------------------------------

    @Test
    fun `the saved schedule is off, weekly and without a folder until the user sets it`() = runBlocking {
        val prefs = DataStoreBackupSchedulePreferences(context)

        assertEquals(BackupScheduleSettings(enabled = false, frequency = BackupFrequency.WEEKLY, folderUri = null), prefs.get().getOrThrow())
    }

    @Test
    fun `a saved schedule reads back exactly, for every frequency`() = runBlocking {
        val prefs = DataStoreBackupSchedulePreferences(context)
        for (f in BackupFrequency.entries) {
            val settings = BackupScheduleSettings(enabled = true, frequency = f, folderUri = "content://tree/a b")
            prefs.save(settings).getOrThrow()
            assertEquals(settings, prefs.get().getOrThrow())
        }
    }

    // ---- one scheduled run ---------------------------------------------------------------------

    @Test
    fun `the default file name is forager-backup, the date, dot zip`() {
        assertEquals("forager-backup-2026-09-21.zip", backupFileName(noon, zone))
    }

    @Test
    fun `a scheduled run writes one new file to the chosen folder, named by the date`() = runBlocking {
        val result = useCase(on)()

        assertTrue(result.isSuccess)
        assertEquals(listOf("content://tree/backups" to backupFileName(noon, zone)), files.created)
        assertTrue(FakeJournalBackup.BACKUP_BYTES.contentEquals(files.written.values.single().toByteArray()))
    }

    @Test
    fun `two runs make two files and nothing is removed`() = runBlocking {
        useCase(on)()
        useCase(on)()

        assertEquals(2, files.created.size)
        assertEquals("both files are still there", 2, files.written.size)
        assertTrue("and nothing was deleted", files.deleted.isEmpty())
    }

    @Test
    fun `a run with the setting off, or with no folder, writes nothing and reports a failure`() = runBlocking {
        val off = useCase(on.copy(enabled = false))()
        val noFolder = useCase(on.copy(folderUri = null))()

        assertTrue("off: ${off.exceptionOrNull()}", off.exceptionOrNull().let { it is com.zynergylabs.forager.app.domain.BackupException && "off" in it.message!! })
        assertTrue("no folder: ${noFolder.exceptionOrNull()}", noFolder.exceptionOrNull().let { it is com.zynergylabs.forager.app.domain.BackupException && "folder" in it.message!! })
        assertTrue("never a silent success", files.created.isEmpty())
    }

    @Test
    fun `a folder that cannot be written is a failure the caller can see, not a success`() = runBlocking {
        files.failFolder = true

        val result = useCase(on)()

        assertTrue("names the folder: ${result.exceptionOrNull()}", result.exceptionOrNull().let { it is com.zynergylabs.forager.app.domain.BackupException && "folder" in it.message!! })
        assertEquals("nothing was reported as written", 0, backup.backUps)
    }

    @Test
    fun `a scheduled run that meets unreadable photos skips them and saves the backup, reporting how many`() = runBlocking {
        backup.unreadablePhotos = 2

        val result = useCase(on)()

        assertEquals(2, result.getOrThrow().photoFilesMissing)
        assertEquals("it never asks: there is no screen", listOf(com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy.SKIP), backup.policies)
        assertTrue(files.deleted.isEmpty())
        assertEquals(1, files.created.size)
    }

    @Test
    fun `a scheduled run whose write fails deletes the file it created, and only that file`() = runBlocking {
        useCase(on)() // an earlier, good backup already in the folder
        val earlier = files.written.keys.single()
        backup.failWrite = true

        val result = useCase(on)()

        assertTrue(result.isFailure)
        assertEquals("only the file this run created", listOf(files.created.last().let { "${it.first}/${it.second}#2" }), files.deleted)
        assertTrue("the earlier backup is untouched", earlier in files.written.keys)
    }

    @Test
    fun `a delete that fails is logged at warn and the run is still reported as a failure`() = runBlocking {
        backup.failWrite = true
        files.failDelete = true

        val result = useCase(on)()

        assertTrue(result.isFailure)
        assertTrue("logged: $logged", logged.any { "delete" in it })
    }

    // ---- the worker ----------------------------------------------------------------------------

    private fun worker(prefs: BackupScheduleSettings, log: MutableList<String>): ListenableWorker {
        val deps = object : ScheduledBackupDependencies {
            override val runScheduledBackup = useCase(prefs)
            override val reporter = ScheduledBackupReporter(notifier, FakeSchedulePreferences(prefs), ErrorLog { _, _, _ -> })
            override val errorLog = ErrorLog { _, message, error -> log += "$message :: ${error.message}" }
        }
        return TestListenableWorkerBuilder<ScheduledBackupWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: androidx.work.WorkerParameters): ListenableWorker =
                    ScheduledBackupWorker(appContext, workerParameters, deps)
            })
            .build()
    }

    @Test
    fun `the worker runs the backup and reports success`() = runBlocking {
        val result = (worker(on, mutableListOf()) as androidx.work.CoroutineWorker).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, files.created.size)
    }

    @Test
    fun `the worker reports a failed run as a failure and logs why`() = runBlocking {
        files.failFolder = true
        val log = mutableListOf<String>()

        val result = (worker(on, log) as androidx.work.CoroutineWorker).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertTrue("logged: $log", log.any { "folder unreadable" in it })
    }

    // ---- the WorkManager job -------------------------------------------------------------------

    private fun workManager(): WorkManager {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        return WorkManager.getInstance(context)
    }

    /**
     * The job's state once it has settled. Test-mode WorkManager runs a periodic job's first run at once, on a real thread,
     * and the worker now also reports its outcome, so the first read could catch it mid-run (RUNNING). What is asserted is
     * unchanged: this waits (up to 10 s) for the run to end, it does not accept RUNNING.
     */
    private fun infos(wm: WorkManager): List<WorkInfo> {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val read = wm.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_WORK_NAME).get()
            if (read.none { it.state == WorkInfo.State.RUNNING } || System.currentTimeMillis() > deadline) return read
            Thread.sleep(50)
        }
    }

    @Test
    fun `enabling schedules one periodic job at the chosen frequency, and nothing is scheduled while it is off`() {
        val wm = workManager()
        val scheduler = WorkManagerBackupScheduler(wm)
        assertTrue("nothing scheduled by default", infos(wm).isEmpty())

        scheduler.apply(on.copy(frequency = BackupFrequency.DAILY))
        val daily = infos(wm).single()
        assertEquals(WorkInfo.State.ENQUEUED, daily.state)
        assertEquals(TimeUnit.DAYS.toMillis(1), daily.periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(on.copy(frequency = BackupFrequency.MONTHLY))
        val monthly = infos(wm).filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals("still one job, with the new period", 1, monthly.size)
        assertEquals(TimeUnit.DAYS.toMillis(30), monthly.single().periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(on.copy(frequency = BackupFrequency.WEEKLY))
        assertEquals(TimeUnit.DAYS.toMillis(7), infos(wm).single { it.state == WorkInfo.State.ENQUEUED }.periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(on.copy(enabled = false))
        assertTrue("turning it off cancels the job", infos(wm).none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `an enabled setting with no folder is never scheduled`() {
        val wm = workManager()

        WorkManagerBackupScheduler(wm).apply(on.copy(folderUri = null))

        assertNull(infos(wm).firstOrNull { it.state == WorkInfo.State.ENQUEUED })
        assertFalse(infos(wm).any { it.state == WorkInfo.State.ENQUEUED })
    }

    // ---- what a scheduled run tells the person (dispatch 2026-09-28-153, item 1) ----

    @Test
    fun `the worker reports a failed run, so a notification or an in-app notice follows`() = runBlocking {
        files.failFolder = true

        (worker(on, mutableListOf()) as androidx.work.CoroutineWorker).doWork()

        assertEquals(listOf<ScheduledBackupNotice>(ScheduledBackupNotice.DidNotFinish), notifier.notices)
    }

    @Test
    fun `the worker reports nothing for a clean run, and the skipped count for a run that left photos out`() = runBlocking {
        (worker(on, mutableListOf()) as androidx.work.CoroutineWorker).doWork()
        assertEquals("a clean run says nothing", emptyList<ScheduledBackupNotice>(), notifier.notices)

        backup.unreadablePhotos = 3
        (worker(on, mutableListOf()) as androidx.work.CoroutineWorker).doWork()

        assertEquals(listOf<ScheduledBackupNotice>(ScheduledBackupNotice.SavedWithSkippedPhotos(3)), notifier.notices)
    }

    @Test
    fun `a notice waiting to be shown at launch is stored and read back exactly, and clears`() = runBlocking {
        val prefs = DataStoreBackupSchedulePreferences(context)
        assertEquals("none by default", null, prefs.pendingNotice().getOrThrow())

        for (notice in listOf(ScheduledBackupNotice.DidNotFinish, ScheduledBackupNotice.SavedWithSkippedPhotos(1), ScheduledBackupNotice.SavedWithSkippedPhotos(12))) {
            prefs.setPendingNotice(notice).getOrThrow()
            assertEquals(notice, prefs.pendingNotice().getOrThrow())
        }
        prefs.setPendingNotice(null).getOrThrow()
        assertEquals(null, prefs.pendingNotice().getOrThrow())
    }

    // ---- dispatch 2026-09-28-182: the first run waits, the newest 5 are kept, Try again with the schedule off ----

    private fun runner(prefs: FakeSchedulePreferences) =
        RunScheduledBackupUseCase(backup, prefs, files, clock, zone, ErrorLog { _, m, e -> logged += "$m :: ${e.message}" })

    private fun urisMade() = files.created.mapIndexed { i, (folder, name) -> "$folder/$name#${i + 1}" }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(25)
        }
        return true
    }

    /** A test-mode WorkManager whose worker is the real [ScheduledBackupWorker] over this test's fakes, so a run shows up in [backup] and [files]. */
    private fun countingWorkManager(prefs: FakeSchedulePreferences): WorkManager {
        val deps = object : ScheduledBackupDependencies {
            override val runScheduledBackup = runner(prefs)
            override val reporter = ScheduledBackupReporter(notifier, prefs, ErrorLog { _, _, _ -> })
            override val errorLog = ErrorLog { _, message, error -> logged += "$message :: ${error.message}" }
        }
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: androidx.work.WorkerParameters): ListenableWorker? =
                if (workerClassName == ScheduledBackupWorker::class.java.name) ScheduledBackupWorker(appContext, workerParameters, deps) else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setWorkerFactory(factory).build())
        return WorkManager.getInstance(context)
    }

    // -- item 2: the first scheduled backup waits --

    @Test
    fun `turning the schedule on runs nothing at once, and one backup once the interval has passed`() {
        val wm = countingWorkManager(FakeSchedulePreferences(on))

        WorkManagerBackupScheduler(wm).apply(on)

        assertFalse("nothing runs at enqueue (a run was seen: ${backup.backUps})", waitFor(1500) { backup.backUps > 0 })
        val job = wm.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals("the first run is one interval away", TimeUnit.DAYS.toMillis(7), job.initialDelayMillis)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(job.id)

        assertTrue("one run once the interval has passed", waitFor(10_000) { backup.backUps >= 1 })
        Thread.sleep(300)
        assertEquals("and only one", 1, backup.backUps)
        assertEquals(1, files.created.size)
    }

    @Test
    fun `changing how often, and turning it off and on again, run nothing at once either`() {
        val wm = countingWorkManager(FakeSchedulePreferences(on))
        val scheduler = WorkManagerBackupScheduler(wm)

        scheduler.apply(on.copy(frequency = BackupFrequency.DAILY))
        scheduler.apply(on.copy(frequency = BackupFrequency.WEEKLY))
        assertFalse("a frequency change ran a backup", waitFor(1500) { backup.backUps > 0 })

        scheduler.apply(on.copy(enabled = false))
        scheduler.apply(on)
        assertFalse("off and on ran a backup", waitFor(1500) { backup.backUps > 0 })
        assertEquals("one job", 1, wm.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_WORK_NAME).get().count { it.state == WorkInfo.State.ENQUEUED })
    }

    // -- item 3: keep the newest 5 scheduled backups --

    @Test
    fun `the job keeps the newest 5 of its own backups, deleting the oldest beyond that, oldest first`() = runBlocking {
        val prefs = FakeSchedulePreferences(on)
        val run = runner(prefs)

        repeat(7) { assertTrue(run().isSuccess) }

        val made = urisMade()
        assertEquals("the two oldest were deleted, oldest first", made.take(2), files.deleted)
        assertEquals("the newest five are still there", made.drop(2), files.written.keys.toList())
        assertEquals("and are exactly what the record holds, oldest first", made.drop(2), prefs.scheduledFiles)
    }

    @Test
    fun `five backups delete nothing`() = runBlocking {
        val prefs = FakeSchedulePreferences(on)
        val run = runner(prefs)

        repeat(5) { run() }

        assertTrue("five is within the limit: ${files.deleted}", files.deleted.isEmpty())
        assertEquals(urisMade(), prefs.scheduledFiles)
    }

    /** Passes at base (nothing is ever deleted there): it can only fail once pruning exists, so the revert check is what shows it bites. */
    @Test
    fun `a manual backup and any other file in the folder are never deleted, however old`() = runBlocking {
        val manual = "content://tree/backups/forager-backup-manual.zip"
        val other = "content://tree/backups/notes.txt"
        files.written[manual] = java.io.ByteArrayOutputStream()
        files.written[other] = java.io.ByteArrayOutputStream()
        val prefs = FakeSchedulePreferences(on)
        val run = runner(prefs)

        repeat(9) { run() }

        assertTrue("the manual backup and the other file are still there", manual in files.written && other in files.written)
        assertTrue("and were never asked to be deleted: ${files.deleted}", manual !in files.deleted && other !in files.deleted)
        assertEquals("only the job's own were", urisMade().take(4), files.deleted)
    }

    @Test
    fun `a delete that fails is logged at warn, the backup is still a success, and the file is not kept to fail again`() = runBlocking {
        val old = (1..5).map { "content://tree/backups/old-$it.zip" }
        val prefs = FakeSchedulePreferences(on).also { it.scheduledFiles = old }
        files.failDelete = true

        val result = runner(prefs)()

        assertTrue("the backup itself succeeded: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals("the oldest was tried", listOf(old.first()), files.deleted)
        assertTrue("logged, naming it: $logged", logged.any { "delete" in it && "old-1" in it })
        assertEquals("the record is the newest five", old.drop(1) + urisMade(), prefs.scheduledFiles)
    }

    /** Passes at base (a failed run touches nothing there); it guards the new code's failure branch. */
    @Test
    fun `a failed run records nothing and prunes nothing`() = runBlocking {
        val old = (1..5).map { "content://tree/backups/old-$it.zip" }
        val prefs = FakeSchedulePreferences(on).also { it.scheduledFiles = old }
        backup.failWrite = true

        val result = runner(prefs)()

        assertTrue(result.isFailure)
        assertEquals("only the incomplete file this run created was deleted", urisMade(), files.deleted)
        assertEquals("the record is as it was", old, prefs.scheduledFiles)
    }

    @Test
    fun `a record that cannot be read prunes nothing, is logged, and does not overwrite the record`() = runBlocking {
        val old = (1..9).map { "content://tree/backups/old-$it.zip" }
        val prefs = FakeSchedulePreferences(on).also { it.scheduledFiles = old; it.failFilesGet = true }

        val result = runner(prefs)()

        assertTrue("the backup succeeded: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue("nothing was deleted: ${files.deleted}", files.deleted.isEmpty())
        assertTrue("logged: $logged", logged.any { "record" in it })
        assertEquals("the unreadable record was not written over", old, prefs.scheduledFiles)
    }

    @Test
    fun `a record that cannot be written deletes nothing, and is logged`() = runBlocking {
        val old = (1..5).map { "content://tree/backups/old-$it.zip" }
        val prefs = FakeSchedulePreferences(on).also { it.scheduledFiles = old; it.failFilesSet = true }

        val result = runner(prefs)()

        assertTrue(result.isSuccess)
        assertTrue("nothing was deleted, since it could not be recorded: ${files.deleted}", files.deleted.isEmpty())
        assertTrue("logged: $logged", logged.any { "record" in it })
    }

    @Test
    fun `the recorded list is stored and read back exactly, in order, and the permission flag starts unset`() = runBlocking {
        val prefs = DataStoreBackupSchedulePreferences(context)
        assertEquals(emptyList<String>(), prefs.scheduledBackupFiles().getOrThrow())
        assertFalse(prefs.notificationPermissionAsked().getOrThrow())
        val uris = listOf("content://tree/a%20b/x.zip#1", "content://tree/second.zip", "content://com.android.externalstorage.documents/tree/primary%3ABackups/document/primary%3ABackups%2Fforager-backup-2026-09-29%20(1).zip")

        prefs.setScheduledBackupFiles(uris).getOrThrow()
        assertEquals(uris, prefs.scheduledBackupFiles().getOrThrow())
        prefs.setScheduledBackupFiles(emptyList()).getOrThrow()
        assertEquals(emptyList<String>(), prefs.scheduledBackupFiles().getOrThrow())

        prefs.setNotificationPermissionAsked().getOrThrow()
        assertTrue(prefs.notificationPermissionAsked().getOrThrow())
    }

    // -- item 7: Try again with the schedule off --

    @Test
    fun `a retry writes a backup with the setting off, and still refuses with no folder`() = runBlocking {
        val offWithFolder = runner(FakeSchedulePreferences(on.copy(enabled = false)))(retry = true)
        assertTrue("retry with the setting off: ${offWithFolder.exceptionOrNull()}", offWithFolder.isSuccess)
        assertEquals(1, files.created.size)

        val noFolder = runner(FakeSchedulePreferences(on.copy(enabled = false, folderUri = null)))(retry = true)
        assertTrue("no folder is still a failure: $noFolder", noFolder.exceptionOrNull().let { it is com.zynergylabs.forager.app.domain.BackupException && "folder" in it.message!! })
        assertEquals("nothing more was written", 1, files.created.size)
    }

    @Test
    fun `the notification's Try again runs one backup to the saved folder although the schedule has been turned off`() {
        countingWorkManager(FakeSchedulePreferences(on.copy(enabled = false)))

        BackupRetryReceiver().onReceive(context, android.content.Intent())

        assertTrue("a backup was written (created: ${files.created}, logged: $logged)", waitFor(10_000) { files.created.isNotEmpty() })
        Thread.sleep(300)
        assertEquals(listOf("content://tree/backups" to backupFileName(noon, zone)), files.created)
        assertEquals("a clean run says nothing", emptyList<ScheduledBackupNotice>(), notifier.notices)
    }

    @Test
    fun `Try again with the folder's permission gone tries the folder and posts the existing failure`() {
        files.failFolder = true
        countingWorkManager(FakeSchedulePreferences(on.copy(enabled = false)))

        BackupRetryReceiver().onReceive(context, android.content.Intent())

        assertTrue("a notice followed", waitFor(10_000) { notifier.notices.isNotEmpty() })
        assertEquals("the folder was tried (not refused for the setting)", 1, files.folderAttempts)
        assertEquals(listOf<ScheduledBackupNotice>(ScheduledBackupNotice.DidNotFinish), notifier.notices)
    }
}
