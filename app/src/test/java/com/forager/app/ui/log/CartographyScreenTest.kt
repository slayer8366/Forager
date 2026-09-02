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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                basemap = com.forager.app.ui.map.Basemap.DEFAULT,
                night = false,
                getMapData = { _, _ -> emptyCartographyMapData },
                onOpenEntry = { id ->
                    uiState = uiState.copy(editingEntry = uiState.entries.firstOrNull { it.id == id } ?: uiState.draftEntries.firstOrNull { it.id == id })
                },
                onStartEntry = { date ->
                    val started = CartographyEntry.draft(id = "new-entry", date = date, updatedAtEpochMillis = 0L)
                    uiState = uiState.copy(editingEntry = started, draftEntries = uiState.draftEntries + started)
                },
                onCloseEntry = { uiState = uiState.copy(editingEntry = null) },
                onTextChanged = { text -> uiState.editingEntry?.let { current -> uiState = uiState.copy(editingEntry = current.copy(text = text)) } },
                onTagsChanged = { tags -> uiState.editingEntry?.let { current -> uiState = uiState.copy(editingEntry = current.copy(tags = tags)) } },
                onSetFindDecision = { _, _ -> },
                onSetTrackDecision = { _, _ -> },
                onSetWaypointDecision = { _, _ -> },
                onSetOfflineRegionDecision = { _, _ -> },
                onToggleKeptPhoto = {},
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
}
