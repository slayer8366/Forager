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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.shadows.ShadowToast

/**
 * [MushroomLogUiState.saveErrorMessage]'s Toast on [LogPanel] — the drawer-hosted counterpart to
 * [JournalTabTest]'s identical coverage of [JournalTab]. Both entry points wire the same
 * `LaunchedEffect` independently (see [LogPanel]'s own doc comment), so both get the render-wiring
 * check; the clearing logic itself is [MushroomLogViewModelTest]'s to prove.
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

    private fun setScreen(initial: MushroomLogUiState) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initial) }
            LogPanel(
                uiState = uiState,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                mapSlot = StubPickerMapSlot,
                region = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                basemap = Basemap.DEFAULT,
                onOpenEntryForEditing = { id ->
                    // See JournalTabTest's identical stand-in for the full reasoning — this file's
                    // own tests set editingEntry directly rather than through this callback, so the
                    // "start editing" half never actually fires, but LogPanel still requires the
                    // parameter. Combines what onOpenEntry+onStartEditingEntry used to do separately
                    // (Workstream L4c: LogPanel now takes the one atomic ViewModel operation).
                    val opened = uiState.entries.first { it.id == id }
                    uiState = uiState.copy(editingEntry = if (opened.isDraft) opened else opened.copy(isDraft = true))
                },
                onCloseEntry = { uiState = uiState.copy(editingEntry = null) },
                onEntryChanged = { updated ->
                    uiState = uiState.copy(
                        entries = uiState.entries.map { if (it.id == updated.id) updated else it },
                        editingEntry = updated,
                    )
                },
                onSaveEntry = {
                    uiState.editingEntry?.let { current ->
                        val committed = current.copy(isDraft = false)
                        uiState = uiState.copy(
                            entries = uiState.entries.map { if (it.id == committed.id) committed else it },
                            editingEntry = committed,
                        )
                    }
                },
                onCancelEditing = { uiState = uiState.copy(editingEntry = null) },
                onLeaveEditingIncidentally = {
                    uiState.editingEntry?.let { current ->
                        val committed = current.copy(isDraft = false)
                        uiState = uiState.copy(
                            entries = uiState.entries.map { if (it.id == committed.id) committed else it },
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
                onBackToSearch = {},
                onSaveErrorDismissed = { uiState = uiState.copy(saveErrorMessage = null) },
                // Journal Stage 2b: Cartography's own new-entity navigation — this file tests the
                // relocated Finds section, so these are inert fixtures, not exercised by any test.
                cartographyUiState = CartographyUiState(),
                onOpenCartographyEntry = {},
                onStartCartographyEntry = {},
                onCloseCartographyEntry = {},
                onCartographyTextChanged = {},
                onCartographyTagsChanged = {},
                onSetFindDecision = { _, _ -> },
                onSetTrackDecision = { _, _ -> },
                onSetWaypointDecision = { _, _ -> },
                onSetOfflineRegionDecision = { _, _ -> },
                onToggleKeptPhoto = {},
                onFinishCartographyEntry = {},
                onDeleteCartographyEntry = {},
                // Journal restructure Stage 1's Records tab — this file tests the relocated Finds
                // section's saveErrorMessage Toast, so these are inert fixtures, not exercised below.
                availabilityUiState = com.forager.app.ui.availability.AvailabilityUiState(),
                distanceUnit = com.forager.app.domain.model.DistanceUnit.MILES,
                currentTime = com.forager.app.domain.CurrentTimeProvider { 0L },
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                tracks = emptyList(),
                onTracksOpened = {},
                waypoints = emptyList(),
                waypointsErrorMessage = null,
                onDeleteWaypoint = {},
            )
        }
        // Journal Stage 2b: finds relocated from Cartography into Records' fourth Finds submenu —
        // every test below exercises find-editing state, so land there once, here.
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("Finds").performClick()
    }

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

    /**
     * Workstream L4: this panel gained the identical Add-Location wiring [JournalTabTest] already
     * covers for the compact tab, since both render the same [LogEntryDetailScreen]. An entry only
     * ever arrives here already created (this window class has no "+" of its own), so the fixture
     * entry below already has a location — this proves "Change Location" (not "Add Location")
     * shows for that case, and that confirming a pan updates it.
     */
    @Test
    fun `Change Location on an already-located entry opens the centre-pin picker and updates the entry's location on confirm`() {
        val existingEntry = MushroomLogEntry.draft(
            id = "existing-1",
            location = LatLng(45.0, -122.0),
            date = LocalDate.of(2026, 8, 1),
        )
        setScreen(MushroomLogUiState(entries = listOf(existingEntry), editingEntry = existingEntry))

        composeRule.onNodeWithText("Change Location").performClick()
        composeRule.onNodeWithTag("picker-map").assertIsDisplayed()

        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Found at 45.5000, -122.5000").assertExists()
    }
}

private val PICKED_LOCATION = LatLng(45.5, -122.5)

private val StubPickerMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("picker-map")) {
        Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
    }
}
