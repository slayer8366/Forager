package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.photo.CameraCaptureFiles
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/**
 * [LogPanel]'s list view — the medium/expanded window drawer's counterpart to [JournalTab]'s
 * gallery, reached the same way [AvailabilityScreenWaypointFlowTest] reaches the drawer's other
 * sections. Only the list state (not the detail/edit form, already covered by
 * [LogEntryReportScreenTest]/[MushroomLogNotObservedRenderingTest]) is exercised here, through the
 * real [MushroomLogUiState] and the real [LogEntryListScreen] it renders — not a hand-built stand-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogPanelTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val existingEntry = MushroomLogEntry.draft(
        id = "existing-1",
        location = LatLng(45.326, -122.634),
        date = LocalDate.of(2026, 8, 1),
    )

    private fun setScreen(uiState: MushroomLogUiState) {
        composeRule.setContent {
            LogPanel(
                uiState = uiState,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onOpenEntry = {},
                onCloseEntry = {},
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onDeleteEntry = {},
                onBackToSearch = {},
            )
        }
    }

    @Test
    fun `loadErrorMessage shows in the drawer's Log list in place of the entries`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry), loadErrorMessage = "Couldn't load your log entries."))

        composeRule.onNodeWithText("Couldn't load your log entries.").assertIsDisplayed()
        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").assertDoesNotExist()
    }

    @Test
    fun `no load error text shows in the drawer's Log list when loadErrorMessage is null`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry), loadErrorMessage = null))

        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't load your log entries.").assertDoesNotExist()
    }
}
