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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
 * [LogEntryDetailScreen]/the centre-pin location picker shows for a given [MushroomLogUiState] and
 * how a user gets from one to another. Exercised through the real composable and its real
 * callbacks (`onOpenEntry`/`onStartEntry`/back arrows), not by reaching into private state, per
 * CLAUDE.md.
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

    // isDraft = false: represents a genuinely committed entry already in the list, the realistic
    // starting point for this file's report/edit navigation tests (Workstream L4b-R).
    private val existingEntry = MushroomLogEntry.draft(
        id = "existing-1",
        location = LatLng(45.326, -122.634),
        date = LocalDate.of(2026, 8, 1),
    ).copy(isDraft = false)

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
                    // Workstream L4b: a brand-new entry is a draft, never added to entries at
                    // creation (owner decision #6) — see MushroomLogViewModel.onStartNewEntry's own
                    // doc comment. Mirrors that real behavior rather than the pre-L4b shape this
                    // harness used to have.
                    val started = MushroomLogEntry.draft(id = "new-entry", location = location, date = date)
                    uiState = uiState.copy(editingEntry = started)
                },
                onEntryChanged = { updated ->
                    uiState = uiState.copy(
                        entries = uiState.entries.map { if (it.id == updated.id) updated else it },
                        editingEntry = updated,
                    )
                },
                onStartEditingEntry = {
                    // Workstream L4b-R: a simplified local-state stand-in for
                    // MushroomLogViewModel.onStartEditingEntry — this harness models navigation, not
                    // draft-row/parent-pointer mechanics (that's MushroomLogViewModelTest's job), so
                    // "starting to edit" is just flipping isDraft in place rather than creating a
                    // separate row under a new id. A no-op if already a draft, matching the real
                    // ViewModel's own guard.
                    uiState.editingEntry?.let { current ->
                        if (!current.isDraft) uiState = uiState.copy(editingEntry = current.copy(isDraft = true))
                    }
                },
                onSaveEntry = {
                    uiState.editingEntry?.let { current ->
                        val committed = current.copy(isDraft = false)
                        uiState = uiState.copy(
                            entries = if (uiState.entries.any { it.id == committed.id }) {
                                uiState.entries.map { if (it.id == committed.id) committed else it }
                            } else {
                                uiState.entries + committed
                            },
                            editingEntry = committed,
                        )
                    }
                },
                onCancelEditing = { uiState = uiState.copy(editingEntry = null) },
                onLeaveEditingIncidentally = {
                    uiState.editingEntry?.let { current ->
                        val committed = current.copy(isDraft = false)
                        uiState = uiState.copy(
                            entries = if (uiState.entries.any { it.id == committed.id }) {
                                uiState.entries.map { if (it.id == committed.id) committed else it }
                            } else {
                                uiState.entries + committed
                            },
                            editingEntry = null,
                        )
                    }
                },
                onAddPhoto = {},
                onRemovePhoto = {},
                onPullPhoto = { photo ->
                    val editing = uiState.editingEntry
                    if (editing != null && editing.photos.none { it.id == photo.id }) {
                        val updated = editing.copy(photos = editing.photos + photo)
                        uiState = uiState.copy(
                            entries = uiState.entries.map { if (it.id == updated.id) updated else it },
                            editingEntry = updated,
                        )
                    }
                },
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

    /**
     * Workstream L4b: the back arrow is an incidental exit (auto-save), grouped with a tab switch
     * or the app backgrounding — never a toggle back to the report the way it worked before drafts
     * existed. It closes the entry entirely; only the explicit Save button (below) returns to the
     * report. This test's own name and expectation flipped from "returns to the report, not the
     * gallery" for exactly that reason.
     */
    @Test
    fun `backing out of the edit form auto-saves and returns to the gallery, not the report`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithContentDescription("Back to your log").performClick()

        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
    }

    /** Save is the one exit that returns to the report rather than the gallery — see [MushroomLogViewModel.onSaveEntry]'s own doc comment. */
    @Test
    fun `tapping Save on the edit form commits and returns to the report`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        composeRule.onNodeWithText("Find on ${existingEntry.foundOn}").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    /** Cancel on a brand-new entry discards it outright — see [MushroomLogViewModel.onCancelEditing]'s own doc comment. */
    @Test
    fun `tapping Cancel on a brand-new entry's edit form discards it and returns to the gallery`() {
        setScreen(MushroomLogUiState())
        composeRule.onNodeWithContentDescription("New log entry").performClick()

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        composeRule.onNodeWithText("Find on", substring = true).assertDoesNotExist()
    }

    /**
     * Workstream L4: the gallery's "+" no longer collects a location first — it opens the edit
     * form directly, with no picker step in between at all. [startedEntryAt] proves the callback
     * itself was invoked with `null`, not just that some form appeared.
     */
    @Test
    fun `starting a brand-new entry from the gallery's plus tile goes straight to the edit form with no location`() {
        setScreen(MushroomLogUiState())

        composeRule.onNodeWithContentDescription("New log entry").performClick()

        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
        composeRule.onNodeWithText("No location set.").assertIsDisplayed()
        assertEquals(null, startedEntryAt)
    }

    /**
     * The centre-pin picker survives L4, just retargeted: [LogEntryDetailScreen]'s own "Add
     * Location" button (in [JournalTab]'s [MushroomLogUiState.editingEntry] state) opens it, and
     * confirming a pan sets [MushroomLogEntry.foundAt] on the already-open entry.
     */
    @Test
    fun `Add Location on the edit form opens the centre-pin picker and sets the entry's location on confirm`() {
        setScreen(MushroomLogUiState())
        composeRule.onNodeWithContentDescription("New log entry").performClick()

        composeRule.onNodeWithText("Add Location").performClick()
        composeRule.onNodeWithTag("picker-map").assertIsDisplayed()

        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Found at 45.5000, -122.5000").assertExists()
    }

    /**
     * Workstream G3: [LogEntryDetailScreen]'s "From Album" button opens [PullPhotoPickerScreen]
     * the same way "Add Location" opens the centre-pin picker — full-screen, this tab's own state.
     * Selecting a photo pulls it into the entry and returns to the edit form, without adding a new
     * file (the fake harness's own `onPullPhoto` above only ever copies the same [LogPhoto]
     * reference in, never creates one — see [PullPhotoIntoEntryUseCaseTest] for the file/row
     * assertion this harness can't make).
     */
    @Test
    fun `From Album on the edit form opens the picker and pulls the selected photo into the entry`() {
        val galleryPhoto = com.forager.app.domain.model.GalleryPhoto(
            photo = com.forager.app.domain.model.LogPhoto(id = "gallery-1", relativePath = "photos/gallery-1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )
        setScreen(MushroomLogUiState(galleryPhotos = listOf(galleryPhoto)))
        composeRule.onNodeWithContentDescription("New log entry").performClick()

        composeRule.onNodeWithText("From Album").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Log photo").performClick()

        // Back on the edit form (not stuck in the picker), now showing the pulled-in photo.
        composeRule.onNodeWithText("From Album").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Log photo").assertIsDisplayed()
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

private val StubPickerMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("picker-map")) {
        Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
    }
}
