package com.zynergylabs.forager.app.ui.log

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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LogPhoto
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.PAN_RECORDING_MAP_TAG
import com.zynergylabs.forager.app.ui.map.PanRecordingMapSlot
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

    /** The device's live fix, as `AvailabilityScreen` passes `liveFix` to [LogPanel]; held in state for F1's tests (see [JournalTabTest]'s own). */
    private val deviceLocation = mutableStateOf<LatLng?>(null)

    private fun setScreen(
        initial: MushroomLogUiState,
        mapSlot: MapSlot = StubPickerMapSlot,
        galleryPhotos: List<GalleryPhoto> = emptyList(),
    ) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initial) }
            LogPanel(
                uiState = uiState,
                galleryPhotos = galleryPhotos,
                onOpenCameraForLogEntry = {},
                onOpenCameraForAlbum = {},
                onOpenCameraForCartographyEntry = {},
                mapSlot = mapSlot,
                region = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                deviceLocation = deviceLocation.value,
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
                // J6a: LogPanel is JournalTab under a header, and JournalTab draws an open find in its report
                // unless told it is being edited. These tests set `editingEntry` directly, the state the old
                // LogPanel (which had no report step) meant by "open", so they say it is being edited.
                findEntryModeState = remember { mutableStateOf(JournalEntryMode.EDIT) },
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
                getCartographyEntryMapData = { _, _ ->
                    com.zynergylabs.forager.app.domain.CartographyEntryMapData(
                        trackPolylines = emptyList(),
                        findMarkers = emptyList(),
                        waypointMarkers = emptyList(),
                        photoMarkers = emptyList(),
                        offlineRegionCircles = emptyList(),
                    )
                },
                getCartographyEntryOfflineRegion = { _, _ -> null },
                getCartographyEntryCurrentLocation = { com.zynergylabs.forager.app.domain.LocationResult.LocationUnavailable },
                // Journal restructure Stage 1's Records tab — this file tests the relocated Finds
                // section's saveErrorMessage Toast, so these are inert fixtures, not exercised below.
                availabilityUiState = com.zynergylabs.forager.app.ui.availability.AvailabilityUiState(),
                distanceUnit = com.zynergylabs.forager.app.domain.model.DistanceUnit.MILES,
                currentTime = com.zynergylabs.forager.app.domain.CurrentTimeProvider { 0L },
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
        // J1 S3: the Finds filter chip replaced the "Logged Finds" sub-tab (LogPanel shares RecordsTab).
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).performClick()
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

    /** Picker-fixes dispatch, F1, in the wide tree: the same find picker, the same rule — see [JournalTabTest]'s identical test. */
    @Test
    fun `after a pan, a new device fix moves neither the find picker's pin, nor the map's region, nor what OK saves`() {
        val map = PanRecordingMapSlot(panTo = PANNED_LOCATION)
        deviceLocation.value = FIRST_FIX
        setScreen(MushroomLogUiState(entries = listOf(locatedEntry), editingEntry = locatedEntry), mapSlot = map.slot)
        composeRule.onNodeWithText("Change Location").performClick()
        composeRule.onNodeWithText(pinText(FIRST_FIX)).assertExists()

        composeRule.onNodeWithTag(PAN_RECORDING_MAP_TAG).performTouchInput { swipe(center, center - Offset(120f, 60f), 300) }
        composeRule.onNodeWithText(pinText(PANNED_LOCATION)).assertExists()
        val regionsAtPan = map.regions.toList()

        composeRule.runOnIdle { deviceLocation.value = SECOND_FIX }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(pinText(SECOND_FIX)).assertDoesNotExist()
        composeRule.onNodeWithText(pinText(PANNED_LOCATION)).assertExists()
        assertEquals("no new region may reach the map after the pan", regionsAtPan, map.regions.toList())
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(foundAtText(PANNED_LOCATION)).assertExists()
    }

    /** F1's other half in the wide tree: before any pan, the first fix moves the picker — see [JournalTabTest]'s identical test. */
    @Test
    fun `before any pan, the first device fix arriving after the find picker opened moves it there`() {
        val map = PanRecordingMapSlot(panTo = PANNED_LOCATION)
        setScreen(MushroomLogUiState(entries = listOf(locatedEntry), editingEntry = locatedEntry), mapSlot = map.slot)
        composeRule.onNodeWithText("Change Location").performClick()
        composeRule.onNodeWithText("Pin at: 45.3260, -122.6340").assertExists()

        composeRule.runOnIdle { deviceLocation.value = FIRST_FIX }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(pinText(FIRST_FIX)).assertExists()
        assertEquals(Region(FIRST_FIX.lat, FIRST_FIX.lng, FIND_PICKER_DEVICE_RADIUS_KM), map.regions.last())
    }

    /**
     * Picker-fixes dispatch F5, in the wide tree (owner: "Remove everywhere now"): `LogPanel`'s
     * Journal album, reached through `CartographyScreen`'s Entries toolbar, shows no corner trash
     * button. (When this was written it had no long-press Delete either and the wide tree's photos were
     * deleted from the drawer's Photo Gallery screen; J6a removed that screen and gave the album the long-press
     * Delete, which `WideJournalTest` covers.)
     */
    @Test
    fun `the wide tree's Journal album photo has no corner delete control`() {
        val photo = GalleryPhoto(
            photo = LogPhoto(id = "photo-wide", relativePath = "photos/none-wide.jpg", createdAtEpochMillis = 1_758_300_000_000L),
            referencingEntryIds = emptyList(),
        )
        setScreen(MushroomLogUiState(), galleryPhotos = listOf(photo))
        // setScreen lands on Records' Finds chip for this file's find tests; the album is Cartography's.
        // J6a header ruling: the "Cartography | Records" tab row is the phone's Entries | Records switch.
        composeRule.onNodeWithText("Entries").performClick()
        composeRule.onNodeWithTag(ENTRIES_VIEW_ALBUM_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(albumPhotoTestTag(photo.photo.id)).assertExists()
        composeRule.onAllNodesWithContentDescription("Delete this photo").assertCountEquals(0)
    }

    private val locatedEntry =MushroomLogEntry.draft(id = "existing-1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
}

private val PICKED_LOCATION = LatLng(45.5, -122.5)

// Picker-fixes dispatch, F1 — see JournalTabTest's identical values.
private val FIRST_FIX = LatLng(45.6, -122.7)
private val SECOND_FIX = LatLng(45.61, -122.71)
private val PANNED_LOCATION = LatLng(45.7, -122.9)

private fun pinText(at: LatLng) = "Pin at: ${"%.4f".format(at.lat)}, ${"%.4f".format(at.lng)}"

private fun foundAtText(at: LatLng) = "Found at ${"%.4f".format(at.lat)}, ${"%.4f".format(at.lng)}"

private val StubPickerMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("picker-map")) {
        Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
    }
}
