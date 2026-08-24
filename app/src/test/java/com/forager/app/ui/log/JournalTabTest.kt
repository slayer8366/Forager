package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.robolectric.shadows.ShadowToast
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Region
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [JournalTab]'s own navigation state — which of [LogGalleryScreen]/[LogEntryReportScreen]/
 * [LogEntryDetailScreen]/[LogEntryLocationPicker] shows for a given [MushroomLogUiState] and how a
 * user gets from one to another. Exercised through the real composable and its real callbacks
 * (`onOpenEntry`/`onStartEntry`/back arrows), not by reaching into private state, per CLAUDE.md.
 *
 * A real, non-fake [MushroomLogViewModel] isn't used here — this is deliberately a state-machine
 * test of [JournalTab] itself, wiring callbacks to plain local state the same way
 * [AvailabilityScreenTripPlanningFlowTest] wires a real ViewModel for its own screen: the two are
 * complementary, not redundant — this one is fast and isolates the navigation logic from
 * persistence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JournalTabTest {

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

    private var startedEntryAt: LatLng? = null

    private fun setScreen(initial: MushroomLogUiState) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initial) }
            JournalTab(
                uiState = uiState,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                mapSlot = StubPickerMapSlot,
                pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                basemap = Basemap.DEFAULT,
                onOpenEntry = { id -> uiState = uiState.copy(editingEntry = uiState.entries.first { it.id == id }) },
                onCloseEntry = { uiState = uiState.copy(editingEntry = null) },
                onStartEntry = { location, date ->
                    startedEntryAt = location
                    val started = MushroomLogEntry.draft(id = "new-entry", location = location, date = date)
                    uiState = uiState.copy(entries = uiState.entries + started, editingEntry = started)
                },
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onDeleteEntry = { id -> uiState = uiState.copy(entries = uiState.entries.filterNot { it.id == id }, editingEntry = null) },
                onSaveErrorDismissed = { uiState = uiState.copy(saveErrorMessage = null) },
            )
        }
    }

    @Test
    fun `opening an existing entry from the gallery shows its report, not the edit form`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))

        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").performClick()

        // The report's own overflow menu is present; the edit form's own "Photos" section is not.
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        composeRule.onNodeWithText("Photos").assertDoesNotExist()
    }

    @Test
    fun `choosing Edit entry from the report switches to the edit form`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").performClick()

        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
    }

    @Test
    fun `backing out of the edit form returns to the report, not the gallery`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithContentDescription("Back to your log").performClick()

        // The report's own marker is back — not the gallery (which has no "Entry options" menu at
        // all) and not the edit form (which the previous test already showed doesn't have it either).
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    @Test
    fun `starting a brand-new entry from the gallery's plus tile goes straight to the edit form`() {
        setScreen(MushroomLogUiState())

        composeRule.onNodeWithContentDescription("New log entry").performClick()
        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
        assertEquals(LatLng(45.5, -122.5), startedEntryAt)
    }

    /**
     * [MushroomLogUiState.loadErrorMessage]'s neutral, non-belief-changing empty state — see
     * [LogGalleryScreen]'s own doc comment on the parameter. Driven through [JournalTab] with a
     * real [MushroomLogUiState] rather than calling [LogGalleryScreen] directly, so this exercises
     * the same threading [MainActivity] relies on.
     */
    @Test
    fun `a set loadErrorMessage shows the unavailable text when there are no entries to show`() {
        setScreen(MushroomLogUiState(loadErrorMessage = "Log entries unavailable."))

        composeRule.onNodeWithText("Log entries unavailable.").assertIsDisplayed()
    }

    @Test
    fun `with loadErrorMessage unset, no unavailable text appears`() {
        setScreen(MushroomLogUiState())

        composeRule.onNodeWithText("Log entries unavailable.").assertDoesNotExist()
    }

    /** The "not belief-changing" half of the doc comment: a failed refresh never hides entries already on screen. */
    @Test
    fun `a set loadErrorMessage does not hide entries already showing`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry), loadErrorMessage = "Log entries unavailable."))

        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").assertIsDisplayed()
        composeRule.onNodeWithText("Log entries unavailable.").assertDoesNotExist()
    }

    /**
     * [MushroomLogUiState.saveErrorMessage]'s Toast — PR #32's `startRecordingErrorMessage`
     * shape (`AvailabilityScreen.kt`'s `CompactMapTab`), reused here. The clearing half of
     * "dismiss or next successful save, whichever first" is [MushroomLogViewModelTest]'s to prove
     * (it owns [MushroomLogViewModel.onSaveErrorDismissed]); this only proves the render wiring:
     * the message reaches a Toast when set, and none shows when it's null.
     */
    @Test
    fun `a set saveErrorMessage shows a Toast with that text`() {
        setScreen(MushroomLogUiState(saveErrorMessage = "Couldn't save your changes."))

        composeRule.waitForIdle()

        assertEquals("Couldn't save your changes.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `with saveErrorMessage unset, no Toast shows`() {
        setScreen(MushroomLogUiState())

        composeRule.waitForIdle()

        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }
}

private val PICKED_LOCATION = LatLng(45.5, -122.5)

private val StubPickerMapSlot: MapSlot = { _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("picker-map")) {
        Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
    }
}
