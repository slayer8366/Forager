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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import org.robolectric.shadows.ShadowToast
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import com.zynergylabs.forager.app.ui.format.displayDate

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

    /** RECORD -751: how many new finds the screen started (onStartEntry), whatever started them. */
    private var startedEntries = 0

    /**
     * The device's live fix as `AvailabilityCompactScaffold` hands it to [JournalTab]
     * (`uiState.liveFix`), held in state so a test can deliver a new fix while the picker is open —
     * picker-fixes dispatch, F1. `null` for every test that doesn't set it, as before.
     */
    private val deviceLocation = mutableStateOf<LatLng?>(null)

    private fun setScreen(
        initial: MushroomLogUiState,
        pendingDestination: PendingJournalDestination? = null,
        mapSlot: MapSlot = StubPickerMapSlot,
    ) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initial) }
            var pending by remember { mutableStateOf(pendingDestination) }
            JournalTab(
                uiState = uiState,
                onOpenCameraForLogEntry = {},
                onOpenCameraForAlbum = {},
                onOpenCameraForCartographyEntry = {},
                mapSlot = mapSlot,
                pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                deviceLocation = deviceLocation.value,
                basemap = Basemap.DEFAULT,
                onOpenEntry = { id -> uiState = uiState.copy(editingEntry = uiState.entries.first { it.id == id }) },
                onCloseEntry = { uiState = uiState.copy(editingEntry = null) },
                onStartEntry = { location, date ->
                    startedEntries++
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
                // Journal Stage 2b: Cartography's own new-entity navigation — this file tests the
                // relocated Finds section's navigation state instead (see setScreen's own
                // Records/Finds tap below), so these are inert fixtures, not exercised by any test.
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
                getCartographyEntryMapData = { _, _ -> EMPTY_CARTOGRAPHY_MAP_DATA },
                getCartographyEntryOfflineRegion = { _, _ -> null },
                getCartographyEntryCurrentLocation = { com.zynergylabs.forager.app.domain.LocationResult.LocationUnavailable },
                // Journal restructure Stage 1's Records tab — this file tests the relocated Finds
                // section's navigation state, so these (besides waypoints/tracks below, unused by
                // Finds) are inert fixtures, not exercised by any test.
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
                pendingDestination = pending,
                onPendingDestinationConsumed = { pending = null },
            )
        }
        // Journal Stage 2b: finds relocated from Cartography into Records' fourth Finds submenu —
        // every test below exercises find-editing navigation, so land there once, here, rather than
        // repeating this tap in each test. Skipped when a pendingDestination was supplied: that
        // routing-fix test (see "the map plus icon bar" below) asserts the tab lands there on its
        // own, with no manual tap standing in for what a real one-shot request already did.
        if (pendingDestination == null) {
            composeRule.onNodeWithText("Records").performClick()
            // J1 S3: the Finds filter chip replaced the "Logged Finds" sub-tab.
            composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).performClick()
        }
    }

    @Test
    fun `opening an existing entry from the gallery shows its report, not the edit form`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))

        composeRule.onNodeWithContentDescription(findBlankTitleLabel(existingEntry)).performClick()

        // The report's own overflow menu is present; the edit form's own "Photos" section is not.
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        composeRule.onNodeWithText("Photos").assertDoesNotExist()
    }

    @Test
    fun `choosing Edit entry from the report switches to the edit form`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        composeRule.onNodeWithContentDescription(findBlankTitleLabel(existingEntry)).performClick()

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
        composeRule.onNodeWithContentDescription(findBlankTitleLabel(existingEntry)).performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithContentDescription("Back to your log").performClick()

        composeRule.onNodeWithTag(FINDS_FAB_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
    }

    /** Save is the one exit that returns to the report rather than the gallery — see [MushroomLogViewModel.onSaveEntry]'s own doc comment. */
    @Test
    fun `tapping Save on the edit form commits and returns to the report`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        composeRule.onNodeWithContentDescription(findBlankTitleLabel(existingEntry)).performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    /** Cancel on a brand-new entry discards it outright — see [MushroomLogViewModel.onCancelEditing]'s own doc comment. */
    @Test
    fun `tapping Cancel on a brand-new entry's edit form discards it and returns to the gallery`() {
        setScreen(MushroomLogUiState())
        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag(FINDS_FAB_TAG).assertIsDisplayed()
        // No tile and no day heading for the discarded entry (data part D, RECORD -702 and -703: a tile shows a name, the time
        // found, or nothing with a "Find, <date>" label; "Find on" no longer appears on a tile, so its absence would prove nothing).
        composeRule.onAllNodesWithContentDescription("Find, ${displayDate(LocalDate.now())}").assertCountEquals(0)
        composeRule.onAllNodesWithTag(findsDayHeaderTag(LocalDate.now())).assertCountEquals(0)
    }

    /**
     * Workstream L4: the gallery's "+" no longer collects a location first — it opens the edit
     * form directly, with no picker step in between at all. [startedEntryAt] proves the callback
     * itself was invoked with `null`, not just that some form appeared.
     */
    @Test
    fun `starting a brand-new entry from the gallery's New find button goes straight to the edit form with no location`() {
        setScreen(MushroomLogUiState())

        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()

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
        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()

        composeRule.onNodeWithText("Add Location").performClick()
        composeRule.onNodeWithTag("picker-map").assertIsDisplayed()

        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Found at 45.5000, -122.5000").assertExists()
    }

    /**
     * Moved here from `LogPanelTest`, deleted with the wide tree's `LogPanel` (dispatch 2026-09-28-245): "Change
     * Location" on an entry that already has a location opens the centre-pin picker and updates the location on
     * confirm. The phone's `JournalTab` renders the same [LogEntryDetailScreen], and none of this file's other
     * tests opened the picker from an already-located entry.
     */
    @Test
    fun `Change Location on an already-located entry opens the centre-pin picker and updates the entry's location on confirm`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        composeRule.onNodeWithContentDescription(findBlankTitleLabel(existingEntry)).performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        composeRule.onNodeWithText("Change Location").performClick()
        composeRule.onNodeWithTag("picker-map").assertIsDisplayed()

        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Found at 45.5000, -122.5000").assertExists()
    }

    /**
     * Picker-fixes dispatch, F1, the owner's report: "when choosing a location to save to the find
     * log" the picker snapped back to the device after every pan. A live fix arrives about once a
     * second, each a new picker region (`findLocationPickerRegion`), and before this fix each one
     * reset the pin and re-centred the map. The owner's words, "Follow until you touch it": after
     * the first pan, a new fix moves neither the pin, nor what OK saves, nor the region the map is
     * handed. The pan is a real drag on [PanRecordingMapSlot]; the new fix goes through
     * [JournalTab]'s own `deviceLocation` parameter, as the scaffold passes `liveFix`.
     */
    @Test
    fun `after a pan, a new device fix moves neither the find picker's pin, nor the map's region, nor what OK saves`() {
        val map = PanRecordingMapSlot(panTo = PANNED_LOCATION)
        deviceLocation.value = FIRST_FIX
        setScreen(MushroomLogUiState(), mapSlot = map.slot)
        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()
        composeRule.onNodeWithText("Add Location").performClick()
        composeRule.onNodeWithText(pinText(FIRST_FIX)).assertExists()

        composeRule.onNodeWithTag(PAN_RECORDING_MAP_TAG).performTouchInput { swipe(center, center - Offset(120f, 60f), 300) }
        composeRule.onNodeWithText(pinText(PANNED_LOCATION)).assertExists()
        val regionsAtPan = map.regions.toList()

        composeRule.runOnIdle { deviceLocation.value = SECOND_FIX }
        composeRule.waitForIdle()

        // The snap-back itself, named: the pin showing the new fix.
        composeRule.onNodeWithText(pinText(SECOND_FIX)).assertDoesNotExist()
        composeRule.onNodeWithText(pinText(PANNED_LOCATION)).assertExists()
        assertEquals("no new region may reach the map after the pan", regionsAtPan, map.regions.toList())
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(foundAtText(PANNED_LOCATION)).assertExists()
    }

    /**
     * F1's other half: until the first pan the picker follows the device, so a picker opened before
     * any fix (on the search region) moves to the first fix when it arrives, pin and map both. This
     * is today's behaviour kept, not a change; it guards against a fix that freezes the picker from
     * the start.
     */
    @Test
    fun `before any pan, the first device fix arriving after the find picker opened moves it there`() {
        val map = PanRecordingMapSlot(panTo = PANNED_LOCATION)
        setScreen(MushroomLogUiState(), mapSlot = map.slot)
        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()
        composeRule.onNodeWithText("Add Location").performClick()
        composeRule.onNodeWithText("Pin at: 45.3260, -122.6340").assertExists()

        composeRule.runOnIdle { deviceLocation.value = FIRST_FIX }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(pinText(FIRST_FIX)).assertExists()
        assertEquals(Region(FIRST_FIX.lat, FIRST_FIX.lng, FIND_PICKER_DEVICE_RADIUS_KM), map.regions.last())
    }

    /**
     * F1 where the picker opened before any fix and the user panned before one arrived: the first
     * fix changes the picker region's centre and its radius at once (the search region's 15 km to
     * the device's 1 km). Neither may reach the map or the pin: the user has touched it.
     */
    @Test
    fun `a pan made before the first device fix is kept when that fix arrives`() {
        val map = PanRecordingMapSlot(panTo = PANNED_LOCATION)
        setScreen(MushroomLogUiState(), mapSlot = map.slot)
        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()
        composeRule.onNodeWithText("Add Location").performClick()
        composeRule.onNodeWithTag(PAN_RECORDING_MAP_TAG).performTouchInput { swipe(center, center - Offset(120f, 60f), 300) }
        composeRule.onNodeWithText(pinText(PANNED_LOCATION)).assertExists()
        val regionsAtPan = map.regions.toList()

        composeRule.runOnIdle { deviceLocation.value = FIRST_FIX }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(pinText(FIRST_FIX)).assertDoesNotExist()
        composeRule.onNodeWithText(pinText(PANNED_LOCATION)).assertExists()
        assertEquals("no new region may reach the map after the pan", regionsAtPan, map.regions.toList())
    }

    /**
     * Stage 2d's routing fix: the map "+" icon bar's "Log a find" flow used to switch only
     * `AvailabilityScreen`'s own outer tab, leaving this tab's own [selectedTopTab] (defaults to
     * Cartography) untouched — landing on Cartography instead of the find form a device report
     * found. [pendingDestination] is the fix: with it set (as `AvailabilityScreen`'s own
     * `onLogFindHere` now does) and [MushroomLogUiState.editingEntry] already populated (as
     * `onStartLogEntry` — `MushroomLogViewModel.onStartNewEntry` — already did, coordinate
     * included), this tab must land directly on the edit form showing that coordinate, with no
     * manual tap standing in for the one-shot request.
     */
    @Test
    fun `the map plus icon bar's pending destination lands directly on the edit form with the picked coordinate, not Cartography`() {
        val newFind = MushroomLogEntry.draft(id = "new-find", location = LatLng(45.5, -122.5), date = LocalDate.of(2026, 8, 1))
        setScreen(
            MushroomLogUiState(editingEntry = newFind),
            pendingDestination = PendingJournalDestination.EDIT_NEW_FIND,
        )

        // The edit form itself, with the picked coordinate — not Entries' own top level. (J2 T1:
        // "Entries" also labels the Entries | Records switch now, which is always on screen, so the
        // absence check is on Entries' own top-level tag rather than that text.)
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Found at 45.5000, -122.5000").assertIsDisplayed()
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertDoesNotExist()
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
        val galleryPhoto = com.zynergylabs.forager.app.domain.model.GalleryPhoto(
            photo = com.zynergylabs.forager.app.domain.model.LogPhoto(id = "gallery-1", relativePath = "photos/gallery-1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )
        setScreen(MushroomLogUiState(galleryPhotos = listOf(galleryPhoto)))
        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()

        composeRule.onNodeWithText("From Album").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }
        // Entry-photo-acquisition dispatch, Item 2: the same shared picker gained Camera/Import
        // buttons for both its callers — see PullPhotoPickerScreen's own doc comment. Presence only,
        // same established limit as every other Camera/Import button in this codebase (a real tap
        // launches a system Activity Robolectric cannot drive).
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Log photo").performClick()

        // Back on the edit form (not stuck in the picker), now showing the pulled-in photo.
        composeRule.onNodeWithText("From Album").assertIsDisplayed()
        // Dispatch 2026-09-28-317: the edit form's own DecodedPhoto is newly composed, and its decode
        // runs on Dispatchers.IO off the test clock. The wait above covered only the picker's decode,
        // so this node was read unwaited (docs/audits/2026-09-30-ci-flake-diagnosis.md section 8:
        // CI's "'Log photo' ... is not displayed!", with 0 such nodes at that moment). The assertion
        // below is unchanged; it now runs once the pulled-in photo has decoded.
        composeRule.waitUntil(
            conditionDescription = "the edit form's pulled-in photo ('Log photo') to finish decoding",
            timeoutMillis = 5_000,
        ) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }
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

        composeRule.onNodeWithContentDescription(findBlankTitleLabel(existingEntry)).assertIsDisplayed()
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

    // ── RECORD -751: "New find" replaces the "+" tile ──

    private fun boundsOf(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun touchAt(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp) {
        val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onRoot().performTouchInput { click(p) }
        composeRule.waitForIdle()
    }

    /**
     * The owner: "can we use a New Find button instead of the + tile". Five real touches across the button's own bounds (a
     * finger is not a point) each start a new find and open its form, as the tile did; Cancel brings the grid back between.
     */
    @Test
    fun `real touches across the New find button each start a new find`() {
        setScreen(MushroomLogUiState(entries = listOf(existingEntry)))
        val fab = boundsOf(FINDS_FAB_TAG)
        composeRule.onNodeWithText("New find").assertIsDisplayed()
        val inset = 6.dp
        val points = listOf(
            (fab.left + fab.right) / 2 to (fab.top + fab.bottom) / 2,
            fab.left + inset to fab.top + inset,
            fab.right - inset to fab.top + inset,
            fab.left + inset to fab.bottom - inset,
            fab.right - inset to fab.bottom - inset,
        )
        points.forEachIndexed { i, (x, y) ->
            touchAt(x, y)
            assertEquals("the touch at (${x.value}, ${y.value}) started a new find", i + 1, startedEntries)
            composeRule.onNodeWithText("Photos").assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(FINDS_FAB_TAG).assertIsDisplayed()
        }
    }

    /** No "+" tile: the grid's first item is the newest day's heading, and nothing in it reads "New log entry". */
    @Test
    fun `the Finds grid has no plus tile and starts at its first date heading`() {
        val older = existingEntry.copy(id = "older", foundOn = LocalDate.of(2026, 7, 2))
        setScreen(MushroomLogUiState(entries = listOf(older, existingEntry)))
        composeRule.onAllNodesWithContentDescription("New log entry").assertCountEquals(0)
        val first = composeRule.onNodeWithTag(FINDS_GRID_TAG).onChildAt(0).fetchSemanticsNode()
        val firstTag = first.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
        assertEquals("the grid's first item is the newest day's heading", findsDayHeaderTag(LocalDate.of(2026, 8, 1)), firstTag)
        assertTrue("positive control: the older day's heading is in the grid too", composeRule.onAllNodesWithTag(findsDayHeaderTag(LocalDate.of(2026, 7, 2))).fetchSemanticsNodes().isNotEmpty())
    }

    /** The button does not cover the last row: scrolled to its end, the grid's last tile ends above the button's top. */
    @Test
    fun `scrolled to its end, the Finds grid's last tile ends above the New find button`() {
        val finds = (1..12).map { n -> existingEntry.copy(id = "find-$n", foundOn = LocalDate.of(2026, 8, n)) }
        setScreen(MushroomLogUiState(entries = finds))
        repeat(8) {
            composeRule.onNodeWithTag(FINDS_GRID_TAG).performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        val fab = boundsOf(FINDS_FAB_TAG)
        // The oldest find is the grid's last tile (newest day first).
        val lastTile = composeRule.onNodeWithContentDescription(findBlankTitleLabel(finds.first())).getUnclippedBoundsInRoot()
        val grid = boundsOf(FINDS_GRID_TAG)
        println("MEASURED -751: grid $grid, last tile $lastTile, button $fab")
        assertTrue("positive control: the grid scrolled (the last tile is inside it)", lastTile.bottom <= grid.bottom)
        assertTrue("the last tile's bottom ${lastTile.bottom} is above the button's top ${fab.top}", lastTile.bottom <= fab.top)
    }

}

private val PICKED_LOCATION = LatLng(45.5, -122.5)

// Picker-fixes dispatch, F1: two device fixes and the point a pan settles on, far enough apart
// that each prints differently at four decimals.
private val FIRST_FIX = LatLng(45.6, -122.7)
private val SECOND_FIX = LatLng(45.61, -122.71)
private val PANNED_LOCATION = LatLng(45.7, -122.9)

private fun pinText(at: LatLng) = "Pin at: ${"%.4f".format(at.lat)}, ${"%.4f".format(at.lng)}"

private fun foundAtText(at: LatLng) = "Found at ${"%.4f".format(at.lat)}, ${"%.4f".format(at.lng)}"

private val EMPTY_CARTOGRAPHY_MAP_DATA = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

private val StubPickerMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("picker-map")) {
        Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
    }
}
