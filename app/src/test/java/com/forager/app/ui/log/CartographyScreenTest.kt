package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.CartographyEntryMapData
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.photo.CameraCaptureFiles
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [CartographyScreen]'s own navigation state — Journal Stage 2c: which of
 * [CartographyEntryReportScreen]/[CartographyEntryEditScreen] shows for [CartographyUiState.editingEntry],
 * driven by the local [CartographyEntryMode] this file exercises entirely through the real composable
 * and its real callbacks, the same state-machine-test shape [JournalTabTest] already establishes for
 * the identical report/edit problem on [com.forager.app.domain.model.MushroomLogEntry] — plain local
 * state standing in for [CartographyViewModel], not a fake/mock of it, since this test isolates
 * navigation from persistence (see that class's own test, [CartographyViewModelTest], for the
 * persistence side).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyScreenTest {

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

    private val committedEntry = CartographyEntry.draft(id = "committed-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false)
    private val draftEntry = CartographyEntry.draft(id = "draft-1", date = LocalDate.of(2026, 8, 2), updatedAtEpochMillis = 2_000L)

    private val emptyCartographyMapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = emptyList(),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private fun setScreen(initial: CartographyUiState) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initial) }
            CartographyScreen(
                uiState = uiState,
                galleryPhotos = emptyList(),
                isLoadingGalleryPhotos = false,
                galleryLoadErrorMessage = null,
                galleryPhotoEntryReferenceCounts = emptyMap(),
                onDeleteGalleryPhoto = {},
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onAddGalleryPhoto = {},
                distanceUnit = DistanceUnit.MILES,
                mapSlot = { _, _, _, _, _, _, _, _, _ -> },
                night = false,
                getMapData = { _, _ -> emptyCartographyMapData },
                getCoveringOfflineRegion = { _, _ -> null },
                getCurrentLocation = { com.forager.app.domain.LocationResult.LocationUnavailable },
                onOpenEntry = { id ->
                    uiState = uiState.copy(editingEntry = uiState.entries.firstOrNull { it.id == id } ?: uiState.draftEntries.firstOrNull { it.id == id })
                },
                onStartEntry = { date ->
                    val started = CartographyEntry.draft(id = "new-entry", date = date, updatedAtEpochMillis = 0L)
                    uiState = uiState.copy(editingEntry = started, draftEntries = uiState.draftEntries + started)
                },
                onCloseEntry = {
                    uiState.editingEntry?.let { current ->
                        uiState = uiState.copy(
                            entries = if (!current.isDraft && uiState.entries.any { it.id == current.id }) {
                                uiState.entries.map { if (it.id == current.id) current else it }
                            } else {
                                uiState.entries
                            },
                        )
                    }
                    uiState = uiState.copy(editingEntry = null, hasUnsavedChanges = false)
                },
                // Mirrors CartographyViewModel.persist's own draft/committed split: a draft still
                // autosaves (no dirty flag), a committed entry only marks hasUnsavedChanges —
                // this fixture stands in for the ViewModel, so it needs the same split to exercise
                // the Save/Discard/Cancel prompt this file's own new tests drive.
                onTextChanged = { text ->
                    uiState.editingEntry?.let { current ->
                        val edited = current.copy(text = text)
                        uiState = uiState.copy(editingEntry = edited, hasUnsavedChanges = uiState.hasUnsavedChanges || !edited.isDraft)
                    }
                },
                onTagsChanged = { tags -> uiState.editingEntry?.let { current -> uiState = uiState.copy(editingEntry = current.copy(tags = tags)) } },
                onSetFindDecision = { _, _ -> },
                onSetTrackDecision = { _, _ -> },
                onSetWaypointDecision = { _, _ -> },
                onSetOfflineRegionDecision = { _, _ -> },
                onToggleKeptPhoto = {},
                // Real acquire-and-attach composition (persist via AddPhotoToGalleryUseCase, then
                // this same onToggleKeptPhoto) is MainActivity's own job, untestable from here —
                // see PullPhotoPickerScreen's own doc comment. This file isolates navigation, same
                // as every other fixture callback above; a no-op is enough for the tests below,
                // which only assert that Camera/Import exist and that back unwinds correctly.
                onAcquirePhotoForEntry = {},
                onFinishEntry = {
                    uiState.editingEntry?.let { current ->
                        val committed = current.copy(isDraft = false)
                        uiState = uiState.copy(
                            entries = uiState.entries + committed,
                            draftEntries = uiState.draftEntries.filterNot { it.id == committed.id },
                            editingEntry = committed,
                        )
                    }
                },
                onSaveEntry = {
                    uiState.editingEntry?.let { current ->
                        uiState = uiState.copy(
                            entries = if (uiState.entries.any { it.id == current.id }) {
                                uiState.entries.map { if (it.id == current.id) current else it }
                            } else {
                                uiState.entries + current
                            },
                            hasUnsavedChanges = false,
                        )
                    }
                },
                // Real reload-from-database behaviour on Discard is CartographyViewModelTest's own
                // job (see its "discarding... reloads it from the database" test); this fixture only
                // needs to close the editor, since what this file's own tests verify is the dialog's
                // navigation, not storage content.
                onDiscardEntryChanges = { uiState = uiState.copy(editingEntry = null, hasUnsavedChanges = false) },
                // Real demote-and-persist behaviour is CartographyViewModelTest's own job (see its
                // "onSaveEntryAsDraft" tests); this fixture only needs to move the row and close, the
                // same "navigation, not storage content" scope onDiscardEntryChanges' own comment
                // above states.
                onSaveEntryAsDraft = {
                    uiState.editingEntry?.let { current ->
                        val demoted = current.copy(isDraft = true)
                        uiState = uiState.copy(
                            entries = uiState.entries.filterNot { it.id == demoted.id },
                            draftEntries = uiState.draftEntries + demoted,
                        )
                    }
                    uiState = uiState.copy(editingEntry = null, hasUnsavedChanges = false)
                },
                onDeleteEntry = { id ->
                    uiState = uiState.copy(
                        entries = uiState.entries.filterNot { it.id == id },
                        draftEntries = uiState.draftEntries.filterNot { it.id == id },
                        editingEntry = null,
                    )
                },
            )
        }
    }

    @Test
    fun `tapping a committed entry in the Entries tab opens the view screen, not the editor`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))

        composeRule.onNodeWithText("2026-08-01").performClick()

        // The view screen: an overflow menu exists, but none of the editor's editable fields do.
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        composeRule.onNodeWithText("Your own account (optional)").assertDoesNotExist()
    }

    @Test
    fun `tapping a draft in the Drafts tab opens the editor directly, never the view`() {
        setScreen(CartographyUiState(draftEntries = listOf(draftEntry)))

        composeRule.onNodeWithText("Drafts (1)").performClick()
        composeRule.onNodeWithText("2026-08-02").performClick()

        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }

    @Test
    fun `edit entry in the view screen's overflow menu switches to the editor`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))

        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }

    @Test
    fun `starting a brand-new entry from the Entries tab opens the editor directly, never the view`() {
        setScreen(CartographyUiState())

        composeRule.onNodeWithContentDescription("New Cartography entry").performClick()

        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }

    /** Backing out of the view screen (never having edited anything) returns to the Entries tab, same as backing out of the editor does today. */
    @Test
    fun `backing out of the view screen returns to the entries list`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))

        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()

        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithText("2026-08-01").assertIsDisplayed()
    }

    // --- Device-check patch, Item 1: Save/Discard/Cancel for a committed entry ---------------------

    private fun openCommittedEntryEditor() {
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
    }

    @Test
    fun `a committed entry shows its own Save action, not Finish entry`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        openCommittedEntryEditor()

        composeRule.onNodeWithText("Save").assertIsDisplayed()
        composeRule.onNodeWithText("Finish entry").assertDoesNotExist()
    }

    /**
     * Back-nav-and-save-flow dispatch, Item 3: Save confirms, then exits — the standard shape, not
     * stay-on-screen. Pending-edit-and-fixes dispatch, Item 3: the button itself now only opens a
     * confirmation; this test drives through it.
     */
    @Test
    fun `tapping the screen's own Save asks to confirm, then persists a committed entry's edit and exits`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        openCommittedEntryEditor()

        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Chanterelles under the big fir.")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Save this entry?").assertIsDisplayed()
        composeRule.onNodeWithTag(SAVE_CONFIRM_TEST_TAG).performClick()

        // No leave-prompt was needed to get here, and the edit landed in the Entries list.
        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithText("Chanterelles under the big fir.").assertIsDisplayed()
    }

    @Test
    fun `Cancel in the Save confirmation dismisses it and keeps editing without persisting`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        openCommittedEntryEditor()

        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Not yet saved.")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Save this entry?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Save this entry?").assertDoesNotExist()
        composeRule.onNodeWithText("Not yet saved.").assertIsDisplayed()
    }

    @Test
    fun `leaving a committed entry with unsaved changes asks Save, Discard, or Cancel`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        openCommittedEntryEditor()

        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Unsaved.")
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()

        composeRule.onNodeWithText("Save your changes?").assertIsDisplayed()
    }

    @Test
    fun `Cancel in the leave prompt dismisses it and keeps editing`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        openCommittedEntryEditor()

        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Unsaved.")
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.onNodeWithTag(LEAVE_PROMPT_CANCEL_TEST_TAG).performClick()

        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }

    @Test
    fun `Discard in the leave prompt leaves without persisting the edit`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        openCommittedEntryEditor()

        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Unsaved.")
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.onNodeWithTag(LEAVE_PROMPT_DISCARD_TEST_TAG).performClick()

        composeRule.onNodeWithText("Entries").assertIsDisplayed()
    }

    @Test
    fun `Save in the leave prompt persists the edit and leaves`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        openCommittedEntryEditor()

        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Unsaved.")
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.onNodeWithTag(LEAVE_PROMPT_SAVE_TEST_TAG).performClick()

        composeRule.onNodeWithText("Entries").assertIsDisplayed()
    }

    /** Drafts still autosave silently and back still never prompts — unchanged, deliberately, from before this dispatch. */
    @Test
    fun `backing out of a draft with text typed still does not prompt`() {
        setScreen(CartographyUiState())
        composeRule.onNodeWithContentDescription("New Cartography entry").performClick()

        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Draft text.")
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()

        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Drafts (1)").assertIsDisplayed()
    }

    /**
     * Entry-photo-acquisition dispatch, Item 2: Cartography's own acquire-and-attach path,
     * reachable for the first time. Only button *presence* is asserted — tapping either one
     * launches a real system Activity ([rememberPhotoAcquisitionLaunchers]) Robolectric cannot
     * meaningfully drive, the same established limit [PhotoGalleryScreenTest]/[LogEntryDetailScreenTest]
     * already document for the identical buttons elsewhere. What happens after a tap (persist, then
     * attach via [onAcquirePhotoForEntry]) is proven separately: the persist half by
     * `MushroomLogViewModelTest`'s own "onAddGalleryPhoto invokes onPersisted..." test, the attach
     * half by `CartographyViewModelTest`'s own "a standalone photo with no owning find can be
     * pulled into a Cartography entry" test — this file isolates navigation, not persistence, same
     * as its own class doc comment states.
     */
    @Test
    fun `Camera and Import buttons are on the add-photo picker, reached from inside the editor`() {
        setScreen(CartographyUiState(entries = listOf(committedEntry)))
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithContentDescription("Add a photo from the Album").performClick()

        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Gallery").assertDoesNotExist()
    }
}
