package com.zynergylabs.forager.app.ui.availability

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.RestoreMode
import com.zynergylabs.forager.app.ui.backup.BackupRestoreOverlay
import com.zynergylabs.forager.app.ui.backup.BackupViewModel
import com.zynergylabs.forager.app.ui.backup.FakeBackupFiles
import com.zynergylabs.forager.app.ui.backup.FakeJournalBackup
import com.zynergylabs.forager.app.ui.backup.FakeSchedulePreferences
import com.zynergylabs.forager.app.ui.backup.FakeScheduler
import com.zynergylabs.forager.app.ui.backup.RESTORE_ICON_TAG
import com.zynergylabs.forager.app.ui.backup.RESTORE_PAGE_TAG
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * After a restore, the loading page's Done takes the person to the Maps tab, the app's home map, with no visible
 * restart (owner, "6 B"; the tap animation "1 A"). The real screen and the real page and ViewModel together, the way
 * `MainActivity` puts them: the ViewModel's state feeds both the page and the screen's `returnToMapRequest`.
 *
 * **The claim under test is where the tap lands, not what the animation's frames look like:** the Maps tab is
 * selected at the tap, with the clock stopped mid-animation, and stays selected after the page has gone.
 */
abstract class RestoreReturnsToMapTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()
    private val backup = FakeJournalBackup()
    private val files = FakeBackupFiles()
    private lateinit var vm: BackupViewModel

    private fun setScreen() {
        vm = BackupViewModel(backup, FakeSchedulePreferences(), FakeScheduler(), files, ErrorLog { _, _, _ -> }, Dispatchers.Unconfined)
        composeRule.setContent {
            val ui by vm.uiState.collectAsState()
            val current = ui // read here, in composition, so a change recomposes this and the SideEffect runs again
            SideEffect {
                state.backup = vm.controls(current)
                state.returnToMapRequest = current.returnToMapRequest
            }
            MapChromeTestScreen(state, map, roles, overlay = { BackupRestoreOverlay(vm.controls(current)) })
        }
        composeRule.waitForIdle()
    }

    private fun restoreAndReachDone() {
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        vm.onRestoreFileChosen("content://docs/b.zip")
        vm.onRestoreConfirmed(RestoreMode.REPLACE)
        composeRule.waitForIdle()
    }

    private fun goToJournalAndOpenTools() {
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").assertIsSelected()
    }

    @Test
    fun `tapping Done from another tab lands on Maps at the tap, with the clock stopped mid-animation`() {
        setScreen()
        goToJournalAndOpenTools()
        restoreAndReachDone()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(RESTORE_ICON_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(60)

        composeRule.onNodeWithText("Maps").assertIsSelected()
        composeRule.onNodeWithTag(RESTORE_PAGE_TAG).assertIsDisplayed() // still leaving: the tab did not wait for the frames

        composeRule.mainClock.advanceTimeBy(400)

        assertEquals("the page is gone", 0, composeRule.onAllNodesWithTag(RESTORE_PAGE_TAG).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Maps").assertIsSelected()
    }

    @Test
    fun `Done also closes the Tools drawer it was raised from`() {
        setScreen()
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        restoreAndReachDone()

        composeRule.onNodeWithTag(RESTORE_ICON_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Settings").assertIsNotDisplayed()
        composeRule.onNodeWithText("Maps").assertIsSelected()
    }

    @Test
    fun `with the system's animations off, Done goes straight to the map`() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        setScreen()
        goToJournalAndOpenTools()
        restoreAndReachDone()

        composeRule.onNodeWithTag(RESTORE_ICON_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag(RESTORE_PAGE_TAG).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Maps").assertIsSelected()
    }

    @Test
    fun `the restored data is reloaded before Done is offered`() {
        var reloaded = false
        vm = BackupViewModel(backup, FakeSchedulePreferences(), FakeScheduler(), files, ErrorLog { _, _, _ -> }, Dispatchers.Unconfined, reloadAfterRestore = { reloaded = true })
        files.contents["content://docs/b.zip"] = "PK".toByteArray()
        vm.onRestoreFileChosen("content://docs/b.zip")
        vm.onRestoreConfirmed(RestoreMode.MERGE)

        assertTrue(reloaded)
        assertEquals(com.zynergylabs.forager.app.ui.backup.RestorePage.DONE, vm.uiState.value.restorePage)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RestoreReturnsToMapPortraitTest : RestoreReturnsToMapTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RestoreReturnsToMapShortLandscapeTest : RestoreReturnsToMapTests()


