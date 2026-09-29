package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.BackupFrequency
import com.zynergylabs.forager.app.domain.BackupScheduleSettings
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.RestoreMode
import com.zynergylabs.forager.app.domain.backupFileName
import com.zynergylabs.forager.app.ui.backup.BACKUP_AUTOMATIC_SWITCH_TAG
import com.zynergylabs.forager.app.ui.backup.BACKUP_MESSAGE_TAG
import com.zynergylabs.forager.app.ui.backup.BackupViewModel
import com.zynergylabs.forager.app.ui.backup.FakeBackupFiles
import com.zynergylabs.forager.app.ui.backup.FakeJournalBackup
import com.zynergylabs.forager.app.ui.backup.FakeSchedulePreferences
import com.zynergylabs.forager.app.ui.backup.FakeScheduler
import com.zynergylabs.forager.app.ui.backup.RESTORE_PROMPT_TAG
import com.zynergylabs.forager.app.ui.backup.backupFrequencyTag
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Tools, then Settings, then the Backup section, on the real [AvailabilityScreen] with the real
 * [BackupViewModel] (its collaborators are the fakes in `ui/backup`): what the user sees and what a touch does.
 *
 * The system pickers (create a file, choose a folder, open a file) cannot be driven under Robolectric, so the
 * Activity Result registry is a scripted one: it records the contract and its input, and answers the launch with
 * the URI the test chose, the way the system would. That reaches the real callback the button reaches; what the
 * picker itself looks like is a device item.
 */
abstract class BackupSettingsScreenTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val backup = FakeJournalBackup()
    private val prefs = FakeSchedulePreferences()
    private val scheduler = FakeScheduler()
    private val files = FakeBackupFiles()
    private val logged = mutableListOf<String>()
    private var recording = false

    private val launched = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()
    private val answers = mutableMapOf<Class<*>, Any?>()
    private val registryOwner = object : ActivityResultRegistryOwner {
        @Suppress("UNCHECKED_CAST")
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launched += contract to input
                if (answers.containsKey(contract.javaClass)) answers[contract.javaClass]?.let { dispatchResult(requestCode, it as O) }
            }
        }
    }

    private fun setScreen() {
        val vm = BackupViewModel(backup, prefs, scheduler, files, ErrorLog { _, m, e -> logged += "$m :: ${e.message}" }, Dispatchers.Unconfined, isRecording = { recording })
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                val state by vm.uiState.collectAsState()
                AvailabilityScreen(
                    uiState = AvailabilityUiState(),
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                    mapSlot = { _, _, _, _, _, _, _, _, modifier -> androidx.compose.foundation.layout.Box(modifier) },
                    backup = vm.controls(state),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    private fun tap(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `Settings has a Backup section with exactly the approved labels`() {
        setScreen()

        for (label in listOf("Backup", "Back up now", "Automatic backup", "How often", "Daily", "Weekly", "Monthly", "Backup folder", "Choose folder", "Restore from backup")) {
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `Back up now offers the default file name, writes the backup where the user chose, and says Backup saved`() {
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()

        tap("Back up now")

        val (contract, input) = launched.single()
        assertTrue(contract is ActivityResultContracts.CreateDocument)
        assertEquals(backupFileName(System.currentTimeMillis()), input)
        assertTrue(FakeJournalBackup.BACKUP_BYTES.contentEquals(files.written.getValue("content://docs/chosen.zip").toByteArray()))
        composeRule.onNodeWithText("Backup saved.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a backup whose file cannot be opened shows Couldn't save the backup and not Backup saved`() {
        files.failOpen = true
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()

        tap("Back up now")

        composeRule.onNodeWithText("Couldn't save the backup.").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Backup saved.").fetchSemanticsNodes().size)
    }

    @Test
    fun `the Automatic backup switch is off, and turning it on with no folder leaves it off and says why`() {
        setScreen()
        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performScrollTo().assertIsOff()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).assertIsOff()
        composeRule.onNodeWithText("Automatic backup is off until you choose a folder.").performScrollTo().assertIsDisplayed()
        assertTrue(scheduler.applied.none { it.enabled })
    }

    @Test
    fun `Choose folder opens the folder picker, and with a folder chosen the switch turns on and schedules the backup`() {
        answers[ActivityResultContracts.OpenDocumentTree::class.java] = Uri.parse("content://tree/Backups")
        setScreen()

        tap("Choose folder")
        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(launched.first().first is ActivityResultContracts.OpenDocumentTree)
        assertEquals(listOf("content://tree/Backups"), files.keptFolders)
        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).assertIsOn()
        assertEquals(BackupScheduleSettings(true, BackupFrequency.WEEKLY, "content://tree/Backups"), scheduler.applied.last())
    }

    @Test
    fun `How often changes the scheduled frequency`() {
        prefs.stored = BackupScheduleSettings(enabled = true, frequency = BackupFrequency.WEEKLY, folderUri = "content://tree/Backups")
        setScreen()

        tap("Monthly")

        assertEquals(BackupFrequency.MONTHLY, scheduler.applied.last().frequency)
        composeRule.onNodeWithTag(backupFrequencyTag(BackupFrequency.MONTHLY)).assertIsSelected()
    }

    @Test
    fun `Restore from backup opens the file picker, then asks with the approved title, body and buttons`() {
        files.contents["content://docs/b.zip"] = "PK-bytes".toByteArray()
        answers[ActivityResultContracts.OpenDocument::class.java] = Uri.parse("content://docs/b.zip")
        setScreen()

        tap("Restore from backup")

        assertTrue(launched.single().first is ActivityResultContracts.OpenDocument)
        composeRule.onNodeWithText("Restore this backup?").assertIsDisplayed()
        composeRule.onNodeWithText("Select Replace if you want to delete the journal data on this device, and move the backup into its place.").assertIsDisplayed()
        composeRule.onNodeWithText("Select Merge if you want to keep the journal data on this device, and restore the rest of the backup, skipping any duplicates.").assertIsDisplayed()
        composeRule.onNodeWithText("Select Cancel to go back.").assertIsDisplayed()
        for (button in listOf("Replace", "Merge", "Cancel")) composeRule.onNodeWithText(button).assertIsDisplayed()
        assertTrue("nothing is restored before a choice", backup.restored.isEmpty())
    }

    @Test
    fun `Cancel on the prompt restores nothing and closes it`() {
        files.contents["content://docs/b.zip"] = "PK-bytes".toByteArray()
        answers[ActivityResultContracts.OpenDocument::class.java] = Uri.parse("content://docs/b.zip")
        setScreen()
        tap("Restore from backup")

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithText("Restore this backup?").fetchSemanticsNodes().size)
        assertTrue(backup.restored.isEmpty())
    }

    private fun restoreWith(button: String, mode: RestoreMode) {
        files.contents["content://docs/b.zip"] = "PK-$button".toByteArray()
        answers[ActivityResultContracts.OpenDocument::class.java] = Uri.parse("content://docs/b.zip")
        setScreen()
        tap("Restore from backup")

        composeRule.onNodeWithText(button).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(mode), backup.restored.map { it.first })
        assertEquals("PK-$button", String(backup.restored.single().second))
        composeRule.onNodeWithText("Restore complete.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Replace restores the chosen file in Replace mode and shows Restore complete`() = restoreWith("Replace", RestoreMode.REPLACE)

    @Test
    fun `Merge restores the chosen file in Merge mode and shows Restore complete`() = restoreWith("Merge", RestoreMode.MERGE)

    @Test
    fun `a restore that fails shows Couldn't restore that backup`() {
        backup.failRestore = true
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        answers[ActivityResultContracts.OpenDocument::class.java] = Uri.parse("content://docs/b.zip")
        setScreen()
        tap("Restore from backup")

        composeRule.onNodeWithText("Merge").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Couldn't restore that backup.").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Restore complete.").fetchSemanticsNodes().size)
    }

    // ---- unreadable photos, a failed write, no restore while recording, the default frequency ----

    @Test
    fun `unreadable photos pause the backup with the owner's words and three buttons`() {
        backup.unreadablePhotos = 2
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()

        tap("Back up now")

        composeRule.onNodeWithText("2 photos couldn't be backed up.").assertIsDisplayed()
        for (button in listOf("Try again", "Continue without file(s)", "Cancel")) composeRule.onNodeWithText(button).assertIsDisplayed()
        assertEquals("nothing was written yet", 0, files.written.getValue("content://docs/chosen.zip").size())
    }

    @Test
    fun `Continue without files saves the backup and says how many photos were left out`() {
        backup.unreadablePhotos = 2
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()
        tap("Back up now")

        composeRule.onNodeWithText("Continue without file(s)").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Backup saved, but 2 photos couldn't be found and were left out.").performScrollTo().assertIsDisplayed()
        assertTrue(FakeJournalBackup.BACKUP_BYTES.contentEquals(files.written.getValue("content://docs/chosen.zip").toByteArray()))
    }

    @Test
    fun `Cancel on the unreadable photos prompt deletes the file this run created`() {
        backup.unreadablePhotos = 1
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()
        tap("Back up now")
        composeRule.onNodeWithText("1 photo couldn't be backed up.").assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("content://docs/chosen.zip"), files.deleted)
        assertEquals(0, composeRule.onAllNodesWithText("1 photo couldn't be backed up.").fetchSemanticsNodes().size)
    }

    @Test
    fun `a failed write shows the owner's message with Try again and Cancel, and Try again opens the Save picker again`() {
        backup.failWrite = true
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()

        tap("Back up now")

        composeRule.onNodeWithText("Couldn't finish the backup. The incomplete file was removed.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        assertEquals(listOf("content://docs/chosen.zip"), files.deleted)
        assertEquals(1, launched.size)

        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitForIdle()

        assertEquals("the Save picker was opened a second time", 2, launched.count { it.first is ActivityResultContracts.CreateDocument })
    }

    @Test
    fun `while a track is recording, Restore from backup says so and opens no picker`() {
        recording = true
        setScreen()

        tap("Restore from backup")

        composeRule.onNodeWithText("Stop recording before restoring a backup.").performScrollTo().assertIsDisplayed()
        assertTrue("no file picker was launched", launched.none { it.first is ActivityResultContracts.OpenDocument })
        assertTrue(backup.restored.isEmpty())
    }

    @Test
    fun `turning the automatic backup on leaves Weekly selected, the default the owner ruled`() {
        answers[ActivityResultContracts.OpenDocumentTree::class.java] = Uri.parse("content://tree/Backups")
        setScreen()
        composeRule.onNodeWithTag(backupFrequencyTag(BackupFrequency.WEEKLY)).performScrollTo().assertIsSelected()

        tap("Choose folder")
        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).assertIsOn()
        composeRule.onNodeWithTag(backupFrequencyTag(BackupFrequency.WEEKLY)).assertIsSelected()
        assertEquals(BackupFrequency.WEEKLY, scheduler.applied.last().frequency)
    }

    // ---- dispatch 2026-09-28-153 -------------------------------------------------------------------

    @Test
    fun `a file that already has contents is asked about before anything is written, with the owner's words`() {
        files.sizes["content://docs/chosen.zip"] = 4096L
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()

        tap("Back up now")

        composeRule.onNodeWithText("Replace the existing backup file?").assertIsDisplayed()
        composeRule.onNodeWithText("Replace").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        assertTrue("nothing written yet", files.written.isEmpty())
    }

    @Test
    fun `Replace on that question writes the backup, and Cancel writes nothing`() {
        files.sizes["content://docs/chosen.zip"] = 4096L
        answers[ActivityResultContracts.CreateDocument::class.java] = Uri.parse("content://docs/chosen.zip")
        setScreen()
        tap("Back up now")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertTrue(files.written.isEmpty())
        assertEquals(0, composeRule.onAllNodesWithText("Replace the existing backup file?").fetchSemanticsNodes().size)

        tap("Back up now")
        composeRule.onNodeWithText("Replace").performClick()
        composeRule.waitForIdle()

        assertTrue(FakeJournalBackup.BACKUP_BYTES.contentEquals(files.written.getValue("content://docs/chosen.zip").toByteArray()))
        composeRule.onNodeWithText("Backup saved.").performScrollTo().assertIsDisplayed()
    }

    private fun turnScheduleOn() {
        answers[ActivityResultContracts.OpenDocumentTree::class.java] = Uri.parse("content://tree/Backups")
        setScreen()
        tap("Choose folder")
        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun notificationPermissionAsks() = launched.count { (it.first as? ActivityResultContracts.RequestPermission) != null && it.second == "android.permission.POST_NOTIFICATIONS" }

    @Test
    fun `turning scheduled backups on asks for the notification permission`() {
        answers[ActivityResultContracts.RequestPermission::class.java] = false

        turnScheduleOn()

        assertEquals(1, notificationPermissionAsks())
    }

    @Test
    fun `declining the permission does not stop the schedule`() {
        answers[ActivityResultContracts.RequestPermission::class.java] = false

        turnScheduleOn()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).assertIsOn()
        assertTrue("the schedule is on and applied", scheduler.applied.last().enabled)
    }

    @Test
    fun `with the permission already granted it is not asked for`() {
        androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>().let {
            org.robolectric.Shadows.shadowOf(it).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        turnScheduleOn()

        assertEquals(0, notificationPermissionAsks())
    }

    @Test
    fun `turning it on with no folder asks for nothing`() {
        setScreen()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(0, notificationPermissionAsks())
    }

    @Test
    fun `turning it off asks for nothing more`() {
        answers[ActivityResultContracts.RequestPermission::class.java] = false
        turnScheduleOn()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).assertIsOff()
        assertEquals("still the one ask, from turning it on", 1, notificationPermissionAsks())
    }

    @Test
    fun `turning it on, off and on again asks for the notification permission once`() {
        answers[ActivityResultContracts.RequestPermission::class.java] = false
        turnScheduleOn()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).assertIsOn()
        assertEquals("one ask in all, not one per turn-on", 1, notificationPermissionAsks())
    }

    @Test
    fun `having been asked before, turning it on asks for nothing`() {
        prefs.notificationAsked = true

        turnScheduleOn()

        composeRule.onNodeWithTag(BACKUP_AUTOMATIC_SWITCH_TAG).assertIsOn()
        assertEquals(0, notificationPermissionAsks())
    }

    @Test
    fun `a notice kept for launch is shown once, in the app, in the approved words`() {
        prefs.pending = com.zynergylabs.forager.app.domain.ScheduledBackupNotice.DidNotFinish
        setScreen()

        composeRule.onNodeWithText("Scheduled backup didn't finish").assertIsDisplayed()
        assertEquals("forgotten once shown", null, prefs.pending)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class BackupSettingsScreenPortraitTest : BackupSettingsScreenTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class BackupSettingsScreenShortLandscapeTest : BackupSettingsScreenTests()
