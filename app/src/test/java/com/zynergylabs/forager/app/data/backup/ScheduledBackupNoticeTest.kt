package com.zynergylabs.forager.app.data.backup

import com.zynergylabs.forager.app.domain.BackupException
import com.zynergylabs.forager.app.domain.BackupReport
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.ScheduledBackupNotice
import com.zynergylabs.forager.app.domain.ScheduledBackupReporter
import com.zynergylabs.forager.app.ui.backup.FakeBackupNotifier
import com.zynergylabs.forager.app.ui.backup.FakeSchedulePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a scheduled run tells the person (dispatch 2026-09-28-153, item 1; owner "1 A"): the approved words, a
 * notification when one can be shown, and otherwise the same notice kept for the app's next launch.
 */
class ScheduledBackupNoticeTest {

    private val notifier = FakeBackupNotifier()
    private val prefs = FakeSchedulePreferences()
    private val logged = mutableListOf<String>()
    private val reporter = ScheduledBackupReporter(notifier, prefs, ErrorLog { _, m, e -> logged += "$m :: ${e.message}" })

    private fun ok(left: Int = 0) = Result.success(BackupReport(photoFiles = 4, photoFilesMissing = left, archiveBytes = 1L))

    @Test
    fun `the words are exactly the approved texts, with 1 photo in the singular`() {
        assertEquals("Scheduled backup didn't finish", ScheduledBackupNotice.DidNotFinish.text)
        assertEquals("Scheduled backup saved. 1 photo couldn't be backed up.", ScheduledBackupNotice.SavedWithSkippedPhotos(1).text)
        assertEquals("Scheduled backup saved. 2 photos couldn't be backed up.", ScheduledBackupNotice.SavedWithSkippedPhotos(2).text)
    }

    @Test
    fun `a clean run says nothing`() = runBlocking {
        reporter.report(ok())

        assertTrue(notifier.notices.isEmpty())
        assertNull(prefs.pending)
    }

    @Test
    fun `a failed run posts the did-not-finish notice`() = runBlocking {
        reporter.report(Result.failure(BackupException("fake: it failed")))

        assertEquals(listOf<ScheduledBackupNotice>(ScheduledBackupNotice.DidNotFinish), notifier.notices)
    }

    @Test
    fun `a run that left photos out posts how many`() = runBlocking {
        reporter.report(ok(left = 2))

        assertEquals(listOf<ScheduledBackupNotice>(ScheduledBackupNotice.SavedWithSkippedPhotos(2)), notifier.notices)
    }

    @Test
    fun `a notice that was shown as a notification is not kept for the app`() = runBlocking {
        reporter.report(Result.failure(BackupException("fake")))

        assertNull(prefs.pending)
    }

    @Test
    fun `a notice that could not be shown as a notification is kept, to be shown in the app once at launch`() = runBlocking {
        notifier.shows = false

        reporter.report(ok(left = 3))

        assertEquals(ScheduledBackupNotice.SavedWithSkippedPhotos(3), prefs.pending)
    }

    @Test
    fun `a notice that cannot be kept either is logged, not lost silently`() = runBlocking {
        notifier.shows = false
        val failingPrefs = object : com.zynergylabs.forager.app.domain.BackupSchedulePreferences by prefs {
            override suspend fun setPendingNotice(notice: ScheduledBackupNotice?) = Result.failure<Unit>(java.io.IOException("disk full"))
        }

        ScheduledBackupReporter(notifier, failingPrefs, ErrorLog { _, m, e -> logged += "$m :: ${e.message}" }).report(Result.failure(BackupException("fake")))

        assertTrue("logged: $logged", logged.any { "disk full" in it })
    }
}
