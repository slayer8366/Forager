package com.zynergylabs.forager.app.data.backup

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.zynergylabs.forager.app.MainActivity
import com.zynergylabs.forager.app.domain.ScheduledBackupNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The scheduled backup's notifications (dispatch 2026-09-28-153, item 1; owner "1 A"): a channel named exactly
 * "Backups", the approved texts, "Try again" on the failed one, and a tap that opens the Backup section. Real
 * `NotificationManager` (Robolectric's), asserted on what would be on the phone: the channel's name, each notification's
 * title, its actions and its intents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidBackupNotifierTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    private fun allowNotifications() = shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun shown(): List<Notification> = shadowOf(manager).allNotifications

    private fun title(n: Notification) = n.extras.getString(Notification.EXTRA_TITLE)

    @Test
    fun `the channel is named exactly Backups`() {
        allowNotifications()

        AndroidBackupNotifier(app).notify(ScheduledBackupNotice.DidNotFinish)

        assertEquals("Backups", manager.getNotificationChannel(BACKUP_CHANNEL_ID)!!.name.toString())
    }

    @Test
    fun `the did-not-finish notification has the approved words and one action, Try again`() {
        allowNotifications()

        val shownNow = AndroidBackupNotifier(app).notify(ScheduledBackupNotice.DidNotFinish)

        assertTrue(shownNow)
        val n = shown().single()
        assertEquals("Scheduled backup didn't finish", title(n))
        assertEquals(listOf("Try again"), n.actions.map { it.title.toString() })
        assertEquals(BACKUP_CHANNEL_ID, n.channelId)
    }

    @Test
    fun `Try again is a broadcast to the retry receiver`() {
        allowNotifications()
        AndroidBackupNotifier(app).notify(ScheduledBackupNotice.DidNotFinish)

        val action = shown().single().actions.single()

        val saved = shadowOf(action.actionIntent)
        assertTrue(saved.isBroadcastIntent)
        assertEquals(BackupRetryReceiver::class.java.name, saved.savedIntent.component!!.className)
    }

    @Test
    fun `the saved-with-skipped-photos notification has the approved words, 1 photo in the singular, and no action`() {
        allowNotifications()
        val notifier = AndroidBackupNotifier(app)

        notifier.notify(ScheduledBackupNotice.SavedWithSkippedPhotos(1))
        assertEquals("Scheduled backup saved. 1 photo couldn't be backed up.", title(shown().single()))
        manager.cancelAll()

        notifier.notify(ScheduledBackupNotice.SavedWithSkippedPhotos(5))
        val n = shown().single()
        assertEquals("Scheduled backup saved. 5 photos couldn't be backed up.", title(n))
        assertTrue("no Try again on a backup that saved", n.actions.isNullOrEmpty())
    }

    @Test
    fun `tapping either notification opens the app at the Backup section`() {
        allowNotifications()
        val notifier = AndroidBackupNotifier(app)
        for (notice in listOf(ScheduledBackupNotice.DidNotFinish, ScheduledBackupNotice.SavedWithSkippedPhotos(2))) {
            manager.cancelAll()
            notifier.notify(notice)

            val tap = shadowOf(shown().single().contentIntent)

            assertTrue(tap.isActivityIntent)
            assertEquals(MainActivity::class.java.name, tap.savedIntent.component!!.className)
            assertTrue("the extra says which section: $notice", opensBackupSection(tap.savedIntent))
        }
    }

    @Test
    fun `an intent without the extra does not ask for the Backup section`() {
        assertFalse(opensBackupSection(Intent(app, MainActivity::class.java)))
        assertFalse(opensBackupSection(null))
        assertTrue(opensBackupSection(Intent(app, MainActivity::class.java).putExtra(EXTRA_OPEN_BACKUP_SECTION, true)))
    }

    @Test
    fun `without the notification permission nothing is posted and it says so`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val shownNow = AndroidBackupNotifier(app).notify(ScheduledBackupNotice.DidNotFinish)

        assertFalse(shownNow)
        assertTrue(shown().isEmpty())
    }

    @Test
    fun `with notifications switched off for the app nothing is posted and it says so`() {
        allowNotifications()
        shadowOf(manager).setNotificationsEnabled(false)

        val shownNow = AndroidBackupNotifier(app).notify(ScheduledBackupNotice.DidNotFinish)

        assertFalse(shownNow)
        assertTrue(shown().isEmpty())
    }

    @Test
    fun `the retry receiver runs one backup, by enqueueing one one-time job`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        val wm = WorkManager.getInstance(app)

        BackupRetryReceiver().onReceive(app, Intent(app, BackupRetryReceiver::class.java))

        val infos = wm.getWorkInfosByTag(BACKUP_RETRY_WORK_TAG).get()
        assertEquals(1, infos.size)
        assertTrue("the job is the worker's, once: ${infos.single().state}", infos.single().state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED))
        assertNotNull(infos.single().tags)
        assertEquals("a one-time job, not a period", null, infos.single().periodicityInfo)
        wm.cancelAllWork().result.get() // the job is real work; leave none behind for another test in this sandbox
    }
}
