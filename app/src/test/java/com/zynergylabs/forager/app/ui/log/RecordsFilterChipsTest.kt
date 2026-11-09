package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.zynergylabs.forager.app.ui.availability.offlineRegionRowLineTag
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * Journal redesign J1, S3 (`prompts/preserved/2026-09-27-16.md`; plan J4): Records' four-tab
 * `SecondaryTabRow` becomes one scrolling row of filter chips, All · Finds · Tracks · Waypoints ·
 * Offline maps, each with a count, All the default.
 *
 * Driven through the real [JournalTab], with its callbacks wired to local state the way
 * [JournalTabTest] does. Back goes through the host Activity's real `OnBackPressedDispatcher`, the
 * only thing that can settle which `BackHandler` takes a press. Chip selection that stands for "a
 * finger chose this chip" is a coordinate touch at several points across the chip's own bounds
 * (CLAUDE.md, "A semantic `performClick` asserts wiring, not routing"); a semantic click is used only
 * where a test is getting somewhere, not testing the chip (opening Records, the "New find" button, the "+" tile until RECORD -751).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class RecordsFilterChipsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var offlineMapsOpenedCount = 0
    private var tracksOpenedCount = 0
    private var incidentalExitCount = 0
    private val deletedWaypointIds = mutableListOf<String>()
    private val deletedRegionIds = mutableListOf<Long>()

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    private fun setScreen(
        initial: MushroomLogUiState = MushroomLogUiState(entries = listOf(CHIPS_FIND)),
        pendingDestination: PendingJournalDestination? = null,
        openRecords: Boolean = true,
        waypoints: List<Waypoint> = CHIPS_WAYPOINTS,
        tracks: List<Track> = CHIPS_TRACKS,
        regions: List<OfflineRegionSummary> = CHIPS_REGIONS,
    ) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initial) }
            var pending by remember { mutableStateOf(pendingDestination) }
            JournalTab(
                uiState = uiState,
                onOpenCameraForLogEntry = {},
                onOpenCameraForAlbum = {},
                onOpenCameraForCartographyEntry = {},
                mapSlot = CHIPS_STUB_MAP,
                pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                basemap = Basemap.DEFAULT,
                onOpenEntry = { id -> uiState = uiState.copy(editingEntry = uiState.entries.first { it.id == id }) },
                onCloseEntry = { uiState = uiState.copy(editingEntry = null) },
                onStartEntry = { location, date ->
                    uiState = uiState.copy(editingEntry = MushroomLogEntry.draft(id = "new-entry", location = location, date = date))
                },
                onEntryChanged = { updated -> uiState = uiState.copy(editingEntry = updated) },
                onStartEditingEntry = {},
                onSaveEntry = {},
                onCancelEditing = { uiState = uiState.copy(editingEntry = null) },
                onLeaveEditingIncidentally = {
                    incidentalExitCount++
                    uiState = uiState.copy(editingEntry = null)
                },
                onAddPhoto = {},
                onRemovePhoto = {},
                onPullPhoto = {},
                onDeleteEntry = {},
                onSaveErrorDismissed = {},
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
                getCartographyEntryMapData = { _, _ -> CHIPS_EMPTY_MAP_DATA },
                getCartographyEntryOfflineRegion = { _, _ -> null },
                getCartographyEntryCurrentLocation = { LocationResult.LocationUnavailable },
                availabilityUiState = AvailabilityUiState(offlineRegions = regions),
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { 0L },
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = { offlineMapsOpenedCount++ },
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = { id -> deletedRegionIds += id },
                tracks = tracks,
                onTracksOpened = { tracksOpenedCount++ },
                waypoints = waypoints,
                waypointsErrorMessage = null,
                onDeleteWaypoint = { id -> deletedWaypointIds += id },
                pendingDestination = pending,
                onPendingDestinationConsumed = { pending = null },
            )
        }
        composeRule.waitForIdle()
        if (openRecords) {
            composeRule.onNodeWithText("Records").performClick()
            composeRule.waitForIdle()
        }
    }

    private fun chip(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** One real touch at [fraction] of the chip's own width and height, after scrolling it into view. */
    private fun touchChip(tag: String, fraction: Offset) {
        chip(tag).performScrollTo()
        composeRule.waitForIdle()
        chip(tag).performTouchInput { click(Offset(width * fraction.x, height * fraction.y)) }
        composeRule.waitForIdle()
    }

    /** Selects [tag] by a real touch at each sample point in turn, going back to All between samples so each touch has to do the selecting. */
    private fun touchChipAcrossItsBounds(tag: String, afterEachTouch: () -> Unit = {}) {
        for (point in TOUCH_SAMPLES) {
            touchChip(ALL_CHIP, Offset(0.5f, 0.5f))
            chip(ALL_CHIP).assertIsSelected()
            touchChip(tag, point)
            chip(tag).assertIsSelected()
            chip(ALL_CHIP).assertIsNotSelected()
            afterEachTouch()
        }
    }

    // ── The row ──

    /** RECORD -868: Records opens on Finds. Rewritten for it: this said "All selected", and asserted All selected and the other four not. */
    @Test
    fun `the chip row shows All, Finds, Tracks, Waypoints and Offline maps in that order, with counts matching the data, Finds selected`() {
        setScreen()

        val expected = listOf(
            Triple(ALL_CHIP, "All", "7"),
            Triple(FINDS_CHIP, "Finds", "1"),
            Triple(TRACKS_CHIP, "Tracks", "1"),
            Triple(WAYPOINTS_CHIP, "Waypoints", "2"),
            Triple(OFFLINE_MAPS_CHIP, "Offline maps", "3"),
        )
        for ((tag, label, count) in expected) {
            chip(tag).assert(hasText(label)).assert(hasText(count))
        }
        chip(FINDS_CHIP).assertIsSelected()
        for ((tag, _, _) in expected.filter { it.first != FINDS_CHIP }) chip(tag).assertIsNotSelected()

        val lefts = expected.map { (tag, _, _) -> chip(tag).getUnclippedBoundsInRoot().left.value }
        assertEquals("chips are laid out left to right in display order", lefts.sorted(), lefts)
        // The old sub-tab labels are gone.
        composeRule.onNodeWithText("Waypoint Markers").assertDoesNotExist()
        composeRule.onNodeWithText("Logged Finds").assertDoesNotExist()
    }

    // ── Each single-type chip shows what its sub-tab showed ──

    @Test
    fun `touching Waypoints shows the waypoint list`() {
        setScreen()
        touchChipAcrossItsBounds(WAYPOINTS_CHIP) {
            // J4: the waypoint rows' delete is their swipe row now (the trash icon is gone).
            composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "w1")).assertExists()
            composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "w2")).assertExists()
        }
    }

    @Test
    fun `touching Tracks shows the recorded-track list and fires the tracks-opened callback`() {
        setScreen()
        val before = tracksOpenedCount
        touchChipAcrossItsBounds(TRACKS_CHIP) {
            composeRule.onNodeWithTag("share-track-t1").assertExists()
        }
        assertEquals("one tracks-opened call per entry into Tracks", before + TOUCH_SAMPLES.size, tracksOpenedCount)
    }

    @Test
    fun `touching Offline maps shows the offline-maps panel and fires the offline-maps-opened callback`() {
        setScreen()
        val before = offlineMapsOpenedCount
        touchChipAcrossItsBounds(OFFLINE_MAPS_CHIP) {
            composeRule.onNodeWithText("Downloaded Maps").assertExists()
            composeRule.onNodeWithText("Ridge").assertExists()
        }
        assertEquals("one offline-maps-opened call per entry into Offline maps", before + TOUCH_SAMPLES.size, offlineMapsOpenedCount)
    }

    @Test
    fun `touching Finds shows the finds gallery`() {
        setScreen()
        touchChipAcrossItsBounds(FINDS_CHIP) {
            composeRule.onNodeWithTag(FINDS_FAB_TAG).assertExists()
            // The gallery's tile for the one find, and its day heading (data part D, RECORD -702).
            composeRule.onNodeWithContentDescription(findBlankTitleLabel(CHIPS_FIND)).assertExists()
            composeRule.onNodeWithTag(findsDayHeaderTag(CHIPS_FIND.foundOn)).assertExists()
        }
    }

    // ── Back ──

    /**
     * RECORD -871 (the owner: "Back to Finds, then out (Recommended)"). Rewritten for it: this was "Back from a
     * single-type chip returns to All, and Back from All steps to Cartography", looping over Finds, Tracks, Waypoints
     * and Offline maps and asserting All selected after each Back. Now it loops over All, Tracks, Waypoints and
     * Offline maps and asserts Finds selected after each Back; the last Back, from Finds, steps to Entries as the one
     * from All did.
     */
    @Test
    fun `Back from any other chip returns to Finds, and Back from Finds steps to Cartography`() {
        setScreen()
        for (tag in listOf(ALL_CHIP, TRACKS_CHIP, WAYPOINTS_CHIP, OFFLINE_MAPS_CHIP)) {
            touchChip(tag, Offset(0.5f, 0.5f))
            chip(tag).assertIsSelected()

            pressBack()

            chip(FINDS_CHIP).assertIsSelected()
            chip(tag).assertIsNotSelected()
            composeRule.onNodeWithText("Records").assertIsSelected()
        }

        pressBack()

        // J2 T1: the Entries | Records switch replaced the tab row ("Cartography" reads "Entries").
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.CARTOGRAPHY)).assertIsSelected()
    }

    // ── The Finds editing flow, unchanged ──

    @Test
    fun `EDIT_NEW_FIND lands on Finds editing with the Finds chip selected`() {
        val newFind = MushroomLogEntry.draft(id = "new-find", location = LatLng(45.5, -122.5), date = LocalDate.of(2026, 8, 1))
        setScreen(
            MushroomLogUiState(entries = listOf(CHIPS_FIND), editingEntry = newFind),
            pendingDestination = PendingJournalDestination.EDIT_NEW_FIND,
            openRecords = false,
        )

        composeRule.onNodeWithText("Records").assertIsSelected()
        chip(FINDS_CHIP).assertIsSelected()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Found at 45.5000, -122.5000").assertIsDisplayed()
    }

    @Test
    fun `leaving the Finds chip while editing is an incidental exit`() {
        setScreen()
        touchChip(FINDS_CHIP, Offset(0.5f, 0.5f))
        composeRule.onNodeWithTag(FINDS_FAB_TAG).performClick()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        assertEquals(0, incidentalExitCount)

        touchChip(WAYPOINTS_CHIP, Offset(0.5f, 0.5f))

        assertEquals("leaving Finds mid-edit calls the incidental exit once", 1, incidentalExitCount)
        chip(WAYPOINTS_CHIP).assertIsSelected()
        composeRule.onNodeWithText("Photos").assertDoesNotExist()
    }

    @Test
    fun `leaving the Finds chip with nothing being edited is not an incidental exit`() {
        setScreen()
        touchChip(FINDS_CHIP, Offset(0.5f, 0.5f))
        touchChip(WAYPOINTS_CHIP, Offset(0.5f, 0.5f))
        assertTrue(incidentalExitCount == 0)
    }

    // ── S4: the All logbook (journal redesign J1, continuation prompts/preserved/2026-09-27-17.md) ──
    //
    // Owner's answers: each type's own row as its chip shows it, with a type badge; tapping a find
    // selects the Finds chip and opens its report there; committed finds only. The day grouping those
    // answers also set (finds first within a day, days newest first) became grouping by kind with
    // RECORD -868.
    // Times are built at local hours in the JVM's default zone, the zone the screen groups by, so the
    // day a record falls on does not depend on where the suite runs.

    private fun localMillis(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val d1 = LocalDate.of(2026, 9, 26)
    private val d2 = LocalDate.of(2026, 9, 24)
    private val d3 = LocalDate.of(2026, 9, 20)

    private val logFind1 = MushroomLogEntry.draft(id = "F1", location = null, date = d1).copy(isDraft = false)
    private val logFind2 = MushroomLogEntry.draft(id = "F2", location = null, date = d1).copy(isDraft = false)
    private val logFind3 = MushroomLogEntry.draft(id = "F3", location = null, date = d3).copy(isDraft = false)
    private val logDraft = MushroomLogEntry.draft(id = "D1", location = null, date = d1)

    private fun logWaypoints() = listOf(
        Waypoint(id = "W1", lat = 45.5, lng = -122.6, altitude = null, name = "Morning pin", note = "", createdAtEpochMillis = localMillis(d1, 9)),
        Waypoint(id = "W2", lat = 45.6, lng = -122.7, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = localMillis(d2, 10)),
    )

    private fun logTracks() = listOf(
        Track(id = "T1", name = null, startedAtEpochMillis = localMillis(d1, 15), endedAtEpochMillis = localMillis(d1, 16), points = emptyList()),
    )

    private fun logRegions() = listOf(
        OfflineRegionSummary(id = 7L, name = "Noon region", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 10, sizeBytes = 1000L, createdAtEpochMillis = localMillis(d1, 12)),
    )

    /** The logbook data, on All: Records opens on Finds since RECORD -868, so All is touched first. */
    private fun setLogbookScreen() {
        setScreen(
            initial = MushroomLogUiState(entries = listOf(logFind1, logFind2, logFind3), draftEntries = listOf(logDraft)),
            waypoints = logWaypoints(),
            tracks = logTracks(),
            regions = logRegions(),
        )
        touchChip(ALL_CHIP, Offset(0.5f, 0.5f))
    }

    private fun rowTag(type: String, id: String) = "records-logbook-row-$type-$id"
    private fun badgeTag(type: String, id: String) = "records-badge-$type-$id"
    private fun sectionTag(type: String) = "records-logbook-section-$type"

    /**
     * RECORD -868 (the owner: "In All, separate by category and not by time/day entered, with Finds at the top";
     * "Finds, Tracks, Waypoints, Offline maps (Recommended)"). Rewritten for it: this was "All shows every committed
     * record grouped by day, days newest first, finds first in their day, then timed records newest first"; its
     * reading order ran by day under three day headers, and it asserted each day header's record count ("5 records",
     * "1 record", "1 record"). The reading order now runs by kind under four section headers, each asserted whole with
     * its count; the committed-finds-only and chip-count assertions are unchanged. The fixture's finds have no time,
     * so F1 and F2 (same day) keep their input order, and F3 is an older day.
     */
    @Test
    fun `All shows every committed record grouped by kind, Finds, Tracks, Waypoints, Offline maps, each under a header with its count, newest first`() {
        setLogbookScreen()
        chip(ALL_CHIP).assertIsSelected()

        // Reading order: top first, then left (two finds share a row, as in the Finds gallery grid).
        val expected = listOf(
            sectionTag("finds"),
            rowTag("finds", "F1"),
            rowTag("finds", "F2"),
            rowTag("finds", "F3"),
            sectionTag("tracks"),
            rowTag("tracks", "T1"),
            sectionTag("waypoints"),
            rowTag("waypoints", "W1"),
            rowTag("waypoints", "W2"),
            sectionTag("offline-maps"),
            rowTag("offline-maps", "7"),
        )
        val positions = expected.map { tag ->
            val b = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            tag to (b.top.value to b.left.value)
        }
        val sorted = positions.sortedWith(compareBy({ it.second.first }, { it.second.second })).map { it.first }
        assertEquals("the logbook's reading order", expected, sorted)

        // Section headers name the kind as its chip does and carry the section's count.
        composeRule.onNodeWithTag(sectionTag("finds")).assert(hasText("Finds · 3"))
        composeRule.onNodeWithTag(sectionTag("tracks")).assert(hasText("Tracks · 1"))
        composeRule.onNodeWithTag(sectionTag("waypoints")).assert(hasText("Waypoints · 2"))
        composeRule.onNodeWithTag(sectionTag("offline-maps")).assert(hasText("Offline maps · 1"))
        // Committed finds only: the draft is neither listed nor counted.
        composeRule.onNodeWithTag(rowTag("finds", "D1")).assertDoesNotExist()
        chip(ALL_CHIP).assert(hasText("7"))
        chip(FINDS_CHIP).assert(hasText("3"))
    }

    @Test
    fun `every All row carries its type badge, and each row is its chip's own row with its own controls`() {
        setLogbookScreen()

        for ((type, id) in listOf("finds" to "F1", "finds" to "F2", "finds" to "F3", "tracks" to "T1", "offline-maps" to "7", "waypoints" to "W1", "waypoints" to "W2")) {
            composeRule.onNodeWithTag(badgeTag(type, id), useUnmergedTree = true).assertExists()
        }
        // The same rows the single-type chips show: find tiles, the track row's share action, the
        // waypoint row's Directions and its swipe-to-delete row (J4: the trash icon is gone), the
        // region row and its swipe-to-delete row (J4: its "Delete" button is gone).
        // Each find row is a find tile: an unnamed find with no time shows no title and says "Find, <date>" to a screen reader
        // (data part D, RECORD -702 and -703; it read "Find on <d1>" before, counted twice for d1's two finds).
        for (id in listOf("F1", "F2")) {
            composeRule.onNode(hasContentDescription("Find, ${displayDate(d1)}") and hasAnyAncestor(hasTestTag(rowTag("finds", id))), useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithTag("share-track-T1").assertExists()
        composeRule.onNodeWithContentDescription("Directions to Morning pin").assertExists()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "W2")).assertExists()
        composeRule.onNodeWithText("Noon region").assertExists()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.OFFLINE_MAPS, "7")).assertExists()
    }

    /**
     * Rewritten by journal redesign J4 (D2 for the waypoint, D3 for the region): a waypoint or region
     * in All is deleted by swiping its row, which asks for the pending delete at once, with no
     * confirmation dialog (the Undo snackbar replaces it; [JournalPendingDeleteTest] covers the
     * snackbar and the deferred delete). Was: "deleting from All goes through the same confirmation
     * dialogs as the chips".
     */
    @Test
    fun `deleting a waypoint or a region from All is a swipe, with no dialog`() {
        setLogbookScreen()

        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "W2")).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete \"Creek pin\"?").assertDoesNotExist()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf("W2"), deletedWaypointIds)

        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.OFFLINE_MAPS, "7")).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete \"Noon region\"?").assertDoesNotExist()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf(7L), deletedRegionIds)
    }

    /**
     * RECORD -870 and -871: the region row is one plain line, "name · radius · size · downloaded …", read here part by
     * part as the row draws it (the dots between parts are hidden from the semantics tree). The centre's coordinates,
     * the tile count and the zoom-readiness paragraph it used to print are on the region's own page
     * ([RecordDetailsSheetTest]'s region cases), not in the row. Checked in All and in the Offline maps chip, the
     * row's two homes. Noon region: 5 km is 3 mi; 1000 bytes is "0.0 MB"; its time is after this screen's clock (0),
     * which reads "just now".
     */
    @Test
    fun `an offline region row is one plain line of name, radius, size and when, with no centre, tiles or zoom paragraph, in All and in its chip`() {
        setLogbookScreen()
        assertNoonRegionLine("All")

        touchChip(OFFLINE_MAPS_CHIP, Offset(0.5f, 0.5f))
        assertNoonRegionLine("the Offline maps chip")
    }

    private fun assertNoonRegionLine(where: String) {
        val line = composeRule.onNodeWithTag(offlineRegionRowLineTag(7L), useUnmergedTree = true)
        line.performScrollTo()
        val parts = line.onChildren().fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        }
        assertEquals("the row's line in $where", listOf("Noon region", "3 mi radius", "0.0 MB", "downloaded just now"), parts)
        for (gone in listOf("Ready to zoom", "tiles", "45.5000")) {
            assertEquals(
                "no \"$gone\" in $where's row",
                0,
                composeRule.onAllNodes(
                    hasText(gone, substring = true) and hasAnyAncestor(hasTestTag(swipeToDeleteTag(RecordType.OFFLINE_MAPS, "7"))),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size,
            )
        }
    }

    /**
     * RECORD -874 (the owner: "Find > All > Finds > out (Recommended)"). Rewritten for it a second time. Under -871
     * this was "touching a find in All selects the Finds chip and opens its report, and Back goes to the Finds gallery,
     * then out of Records": it asserted the Finds chip selected with the report open, the F2 tile gone, the Finds
     * gallery after one Back and Entries after two. Now the report opens over Records with All still selected (the F2
     * tile stays composed under it, so its "gone" assertion became "the find over the view is up"); the first Back
     * shows All with the F2 tile, the second Finds, the third Entries.
     */
    @Test
    fun `touching a find in All opens its report over All, and Back goes to All, then Finds, then out of Records`() {
        setLogbookScreen()

        for (point in TOUCH_SAMPLES) {
            if (point != TOUCH_SAMPLES.first()) {
                composeRule.onNodeWithText("Records").performClick()
                composeRule.waitForIdle()
                touchChip(ALL_CHIP, Offset(0.5f, 0.5f))
            }
            chip(ALL_CHIP).assertIsSelected()
            composeRule.onNodeWithTag(rowTag("finds", "F2")).performScrollTo()
            composeRule.onNodeWithTag(rowTag("finds", "F2")).performTouchInput { click(Offset(width * point.x, height * point.y)) }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertExists()
            composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
            chip(ALL_CHIP).assertIsSelected()

            pressBack()
            composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
            chip(ALL_CHIP).assertIsSelected()
            composeRule.onNodeWithTag(rowTag("finds", "F2")).assertIsDisplayed()

            pressBack()
            chip(FINDS_CHIP).assertIsSelected()
            composeRule.onNodeWithTag(FINDS_FAB_TAG).assertIsDisplayed()

            pressBack()
            composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.CARTOGRAPHY)).assertIsSelected()
        }
    }
}

private const val ALL_CHIP = "records-chip-all"
private const val FINDS_CHIP = "records-chip-finds"
private const val TRACKS_CHIP = "records-chip-tracks"
private const val WAYPOINTS_CHIP = "records-chip-waypoints"
private const val OFFLINE_MAPS_CHIP = "records-chip-offline-maps"

/** Three touches spread across a chip: near its start edge, its centre, near its end edge, at differing heights. */
private val TOUCH_SAMPLES = listOf(Offset(0.12f, 0.3f), Offset(0.5f, 0.5f), Offset(0.88f, 0.7f))

private val CHIPS_FIND = MushroomLogEntry.draft(id = "find-1", location = null, date = LocalDate.of(2026, 9, 20)).copy(isDraft = false)

private val CHIPS_WAYPOINTS = listOf(
    Waypoint(id = "w1", lat = 45.5, lng = -122.6, altitude = null, name = "Alpha", note = "", createdAtEpochMillis = 1_758_000_000_000L),
    Waypoint(id = "w2", lat = 45.6, lng = -122.7, altitude = null, name = "Bravo", note = "", createdAtEpochMillis = 1_758_100_000_000L),
)

private val CHIPS_TRACKS = listOf(
    Track(id = "t1", name = null, startedAtEpochMillis = 1_758_200_000_000L, endedAtEpochMillis = 1_758_203_600_000L, points = emptyList()),
)

private val CHIPS_REGIONS = listOf(
    OfflineRegionSummary(id = 1L, name = "Ridge", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 10, sizeBytes = 1000L, createdAtEpochMillis = 1_758_300_000_000L),
    OfflineRegionSummary(id = 2L, name = "Creek", region = Region(45.4, -122.5, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 10, sizeBytes = 1000L, createdAtEpochMillis = 1_758_400_000_000L),
    OfflineRegionSummary(id = 3L, name = "Saddle", region = Region(45.3, -122.4, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 10, sizeBytes = 1000L, createdAtEpochMillis = 1_758_500_000_000L),
)

private val CHIPS_EMPTY_MAP_DATA = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

private val CHIPS_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
