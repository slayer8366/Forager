package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Journal redesign J5, short windows (`prompts/preserved/2026-09-27-26.md`): L4 (sideways cards in
 * two columns, the owner's rulings 2, "Long-press, like grids", and 3, "Photo, then track, then
 * icon") and L6 (the album in 5 columns), driven through the real
 * [JournalTab] at `w823dp-h384dp-land`, with its Cartography callbacks wired to local state as
 * [JournalEntryCardsTest] does. Portrait is pinned at `w411dp-h891dp`.
 *
 * Every control a finger taps or long-presses is touched with `performTouchInput` at several
 * points across its own bounds. Tags are literals, so these compile against their base.
 *
 * JournalTab is hosted alone, so its content fills the window here; `AvailabilityScreen` caps it
 * at 640 dp beside the rail (B3), which the L6 test reproduces with a 640 dp host. L5 is not built
 * (see the completion report's "Needs a decision").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class JournalShortWindowCardsTest {

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

    private val opened = mutableListOf<String>()
    private val deleteRequests = mutableListOf<String>()
    private var incidentalExits = 0
    private var newFindsStarted = 0

    private fun setScreen(
        entries: List<CartographyEntry>,
        galleryPhotos: List<GalleryPhoto> = emptyList(),
        tracks: List<Track> = emptyList(),
        finds: List<MushroomLogEntry> = emptyList(),
        waypoints: List<Waypoint> = emptyList(),
        hostWidth: androidx.compose.ui.unit.Dp? = null,
    ) {
        composeRule.setContent {
            var cartography by remember { mutableStateOf(CartographyUiState(entries = entries)) }
            var log by remember { mutableStateOf(MushroomLogUiState(entries = finds)) }
            val tab: @androidx.compose.runtime.Composable () -> Unit = {
                JournalTab(
                    uiState = log,
                    onOpenCameraForLogEntry = {},
                    onOpenCameraForAlbum = {},
                    onOpenCameraForCartographyEntry = {},
                    mapSlot = CARDS_STUB_MAP_J5,
                    pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                    basemap = Basemap.DEFAULT,
                    onOpenEntry = {},
                    onCloseEntry = {},
                    onStartEntry = { location, date ->
                        newFindsStarted++
                        log = log.copy(editingEntry = MushroomLogEntry.draft(id = "new-find", location = location, date = date))
                    },
                    onEntryChanged = {},
                    onStartEditingEntry = {},
                    onSaveEntry = {},
                    onCancelEditing = {},
                    onLeaveEditingIncidentally = {
                        incidentalExits++
                        log = log.copy(editingEntry = null)
                    },
                    onAddPhoto = {},
                    onRemovePhoto = {},
                    onPullPhoto = {},
                    onDeleteEntry = {},
                    onSaveErrorDismissed = {},
                    galleryPhotos = galleryPhotos,
                    cartographyUiState = cartography,
                    onOpenCartographyEntry = { id ->
                        opened += id
                        cartography = cartography.copy(editingEntry = cartography.entries.first { it.id == id })
                    },
                    onStartCartographyEntry = {},
                    onCloseCartographyEntry = { cartography = cartography.copy(editingEntry = null) },
                    onCartographyTextChanged = {},
                    onCartographyTagsChanged = {},
                    onSetFindDecision = { _, _ -> },
                    onSetTrackDecision = { _, _ -> },
                    onSetWaypointDecision = { _, _ -> },
                    onSetOfflineRegionDecision = { _, _ -> },
                    onToggleKeptPhoto = {},
                    onFinishCartographyEntry = {},
                    onDeleteCartographyEntry = {},
                    onRequestDeleteCartographyEntry = { id -> deleteRequests += id },
                    getCartographyEntryMapData = { _, _ -> J5_EMPTY_MAP_DATA },
                    getCartographyEntryOfflineRegion = { _, _ -> null },
                    getCartographyEntryCurrentLocation = { LocationResult.LocationUnavailable },
                    availabilityUiState = AvailabilityUiState(),
                    distanceUnit = DistanceUnit.KILOMETERS,
                    currentTime = CurrentTimeProvider { 0L },
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                    tracks = tracks,
                    onTracksOpened = {},
                    waypoints = waypoints,
                    waypointsErrorMessage = null,
                    onDeleteWaypoint = {},
                )
            }
            if (hostWidth != null) {
                Box(modifier = androidx.compose.ui.Modifier.width(hostWidth)) { tab() }
            } else {
                tab()
            }
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)
    private fun bounds(tag: String): DpRect = node(tag).getUnclippedBoundsInRoot()
    private fun unmergedBounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun exists(tag: String, unmerged: Boolean = false): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    private fun textIn(cardTag: String, text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(cardTag)), useUnmergedTree = true)

    private fun menuItems() = composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))

    private fun touchAt(tag: String, fraction: Offset) {
        val r = bounds(tag)
        val x = (r.left + (r.right - r.left) * fraction.x).value
        val y = (r.top + (r.bottom - r.top) * fraction.y).value
        composeRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeRule.waitForIdle()
    }

    private fun longPressAt(tag: String, fraction: Offset) {
        node(tag).performTouchInput { longClick(Offset(width * fraction.x, height * fraction.y)) }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    /** The slot at a card's start (ruling 3) is 72 dp square and sits before the card's text. */
    private fun assertSlotAtStart(cardTag: String, slotTag: String, title: String) {
        val card = bounds(cardTag)
        val slot = unmergedBounds(slotTag)
        assertEquals("the slot is 72 dp wide", 72f, slot.width.value, 0.5f)
        assertEquals("the slot is 72 dp tall", 72f, slot.height.value, 0.5f)
        assertTrue("the slot $slot is at the card's start $card", slot.left - card.left <= 12.dp)
        val titleBounds = textIn(cardTag, title).getUnclippedBoundsInRoot()
        assertTrue("the text ($titleBounds) is to the right of the slot ($slot)", titleBounds.left >= slot.right)
        assertTrue("the card is not taller than its content needs: sideways, not a hero on top ($card)", card.height < 140.dp)
    }

    // ── L4: two columns, the 72 dp slot (ruling 3) ──

    @Test
    fun `L4 entry cards sit in two columns, each with a 72 dp slot on the left and the text on the right`() {
        setScreen(listOf(TEXT_FIND_ENTRY, TEXT_WAYPOINT_ENTRY, TEXT_REGION_ENTRY))
        val a = bounds(card(TEXT_FIND_ENTRY.id))
        val b = bounds(card(TEXT_WAYPOINT_ENTRY.id))
        assertEquals("two cards share a row", a.top.value, b.top.value, 0.5f)
        assertTrue("side by side", b.left >= a.right)
        assertSlotAtStart(card(TEXT_FIND_ENTRY.id), typePanel(TEXT_FIND_ENTRY.id), "Morning walk")
    }

    @Test
    fun `ruling 3 an entry with a hero photo shows that photo in the slot`() {
        setScreen(listOf(HERO_TRACK_ENTRY), galleryPhotos = listOf(galleryPhoto("p1")), tracks = listOf(track("tr1")))
        assertSlotAtStart(card(HERO_TRACK_ENTRY.id), hero(HERO_TRACK_ENTRY.id, "p1"), "Ridge")
        assertFalse("the photo wins over the track", exists(trackThumb(HERO_TRACK_ENTRY.id), unmerged = true))
    }

    @Test
    fun `ruling 3 with no photo, the track thumbnail (every kept track) fills the slot`() {
        setScreen(listOf(TRACK_ONLY_ENTRY), tracks = listOf(track("tr1"), track("tr2")))
        assertSlotAtStart(card(TRACK_ONLY_ENTRY.id), trackThumb(TRACK_ONLY_ENTRY.id), "Two loops")
        assertFalse("no icon panel when a track draws", exists(typePanel(TRACK_ONLY_ENTRY.id), unmerged = true))
    }

    @Test
    fun `ruling 3 with neither, the tinted type-icon panel fills the slot, and the card keeps J3's content`() {
        setScreen(listOf(TEXT_FIND_ENTRY))
        assertSlotAtStart(card(TEXT_FIND_ENTRY.id), typePanel(TEXT_FIND_ENTRY.id), "Morning walk")
        val c = card(TEXT_FIND_ENTRY.id)
        textIn(c, "26").assertIsDisplayed()
        textIn(c, "SAT").assertIsDisplayed()
        textIn(c, "C. formosus").assertIsDisplayed()
        textIn(c, "1 find").assertIsDisplayed()
        textIn(c, "1 waypoint").assertIsDisplayed()
    }

    // ── Ruling 2: long-press, like grids ──

    @Test
    fun `ruling 2 a long-press at several points on a two-column card opens Edit and Delete, and there is no swipe`() {
        setScreen(listOf(TEXT_FIND_ENTRY, TEXT_WAYPOINT_ENTRY))
        assertFalse("no two-stage swipe row in a short window", exists("entries-swipe-${TEXT_WAYPOINT_ENTRY.id}"))
        var opened = 0
        for (fraction in TOUCH_SAMPLES_J5) {
            longPressAt(card(TEXT_WAYPOINT_ENTRY.id), fraction)
            menuItems().assertCountEquals(2)
            node("tile-options-edit").assertIsDisplayed().assert(hasText("Edit"))
            node("tile-options-delete").assertIsDisplayed().assert(hasText("Delete"))
            opened++
            pressBack()
        }
        assertEquals(TOUCH_SAMPLES_J5.size, opened)
        assertTrue("nothing was deleted by opening the menu", deleteRequests.isEmpty())
    }

    @Test
    fun `ruling 2 the menu's Delete asks for the pending delete of that entry`() {
        setScreen(listOf(TEXT_FIND_ENTRY, TEXT_WAYPOINT_ENTRY))
        longPressAt(card(TEXT_WAYPOINT_ENTRY.id), Offset(0.7f, 0.6f))
        node("tile-options-delete").performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(listOf(TEXT_WAYPOINT_ENTRY.id), deleteRequests)
    }

    @Test
    fun `ruling 2 the menu's Edit opens that entry in the editor`() {
        setScreen(listOf(TEXT_FIND_ENTRY, TEXT_WAYPOINT_ENTRY))
        longPressAt(card(TEXT_FIND_ENTRY.id), Offset(0.3f, 0.4f))
        node("tile-options-edit").performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(listOf(TEXT_FIND_ENTRY.id), opened)
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }

    @Test
    fun `ruling 2 a plain tap at several points still opens the entry's report`() {
        setScreen(listOf(TEXT_FIND_ENTRY, TEXT_WAYPOINT_ENTRY))
        for (fraction in TOUCH_SAMPLES_J5) {
            touchAt(card(TEXT_WAYPOINT_ENTRY.id), fraction)
            assertEquals(TEXT_WAYPOINT_ENTRY.id, opened.last())
            assertFalse("the report, not the editor", composeRule.onAllNodes(hasText("Your own account (optional)")).fetchSemanticsNodes().isNotEmpty())
            pressBack()
        }
        assertEquals(TOUCH_SAMPLES_J5.size, opened.size)
    }

    @Test
    fun `ruling 2 a collapsed entry's row takes the long-press menu too`() {
        setScreen(listOf(TEXT_FIND_ENTRY, BARE_ENTRY_J5))
        assertFalse("no two-stage swipe row on a collapsed row in a short window", exists("entries-swipe-${BARE_ENTRY_J5.id}"))
        longPressAt("entry-row-${BARE_ENTRY_J5.id}", Offset(0.6f, 0.5f))
        menuItems().assertCountEquals(2)
        node("tile-options-delete").performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(listOf(BARE_ENTRY_J5.id), deleteRequests)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `portrait is unchanged - one column, the hero on top, the swipe row, no slot`() {
        setScreen(listOf(HERO_TRACK_ENTRY, TEXT_FIND_ENTRY), galleryPhotos = listOf(galleryPhoto("p1")), tracks = listOf(track("tr1")))
        val heroBounds = unmergedBounds(hero(HERO_TRACK_ENTRY.id, "p1"))
        assertEquals("the hero is 140 dp tall on top", 140f, heroBounds.height.value, 0.5f)
        assertTrue("the swipe row is there", exists("entries-swipe-${HERO_TRACK_ENTRY.id}"))
        assertFalse("no type panel in portrait", exists(typePanel(TEXT_FIND_ENTRY.id), unmerged = true))
        val a = bounds(card(HERO_TRACK_ENTRY.id))
        val b = bounds(card(TEXT_FIND_ENTRY.id))
        assertTrue("one column: the second card is below the first", b.top >= a.bottom)
    }

    // ── The L1 row's switch keeps the incidental-exit rule ──

    /**
     * Leaving Records for Entries mid-find-edit is an incidental exit (`JournalTab`'s doc comment). In
     * a short window the switch is the L1 row's; this pins that the rule came with it. Added while
     * building, after RecordsFilterChipsTest caught a stale read of the open find in the same rule.
     */
    @Test
    fun `in a short window, the L1 switch leaving Records mid-find-edit is an incidental exit`() {
        setScreen(entries = emptyList())
        node("journal-switch-records").performClick()
        composeRule.waitForIdle()
        node("records-chip-finds").performClick()
        composeRule.waitForIdle()
        // RECORD -753: in a short window "New find" is the L1 row's, not a floating button.
        composeRule.onNodeWithTag(SHORT_NEW_FIND_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        assertEquals(0, incidentalExits)
        touchAt("journal-switch-entries", Offset(0.5f, 0.5f))
        assertEquals("leaving Records mid-edit by the L1 switch is one incidental exit", 1, incidentalExits)
        node("journal-switch-entries").assertIsSelected()
    }

    // ── RECORD -753 (dispatch 2026-09-28-755, item 2): "New find" in the L1 row, matching Entries ──

    private fun openFinds() {
        node("journal-switch-records").performClick()
        composeRule.waitForIdle()
        node("records-chip-finds").performClick()
        composeRule.waitForIdle()
    }

    /**
     * The owner: "Yes, match Entries (Recommended)". On Finds in a short landscape window "New find" is an icon in the L1 row, at
     * its end as "New entry" is on Entries, and there is no floating button. Real touches at five points across the icon's own
     * bounds each start a new find and open its form; Back returns to the grid between.
     */
    @Test
    fun `753 Finds puts New find in the L1 row, and real touches across it each start a new find`() {
        setScreen(entries = emptyList(), finds = listOf(keptFindEntry("f1")))
        openFinds()
        assertFalse("no floating New find button in a short window", exists(FINDS_FAB_TAG))
        val header = bounds(SHORT_HEADER_TAG)
        val button = bounds(SHORT_NEW_FIND_TAG)
        assertTrue("the button $button is in the L1 row $header", button.top >= header.top && button.bottom <= header.bottom)
        assertTrue("at the row's end, as New entry is: $button in $header", header.right - button.right <= 24.dp)
        node(SHORT_NEW_FIND_TAG).assert(androidx.compose.ui.test.hasContentDescription("New find"))
        val points = listOf(Offset(0.5f, 0.5f), Offset(0.2f, 0.2f), Offset(0.8f, 0.2f), Offset(0.2f, 0.8f), Offset(0.8f, 0.8f))
        points.forEachIndexed { i, p ->
            touchAt(SHORT_NEW_FIND_TAG, p)
            assertEquals("the touch at $p started new find ${i + 1}", i + 1, newFindsStarted)
            composeRule.onNodeWithText("Photos").assertIsDisplayed()
            assertFalse("no New find in the row while a find is open", exists(SHORT_NEW_FIND_TAG))
            pressBack()
            assertTrue("back on the grid, New find is in the row again", exists(SHORT_NEW_FIND_TAG))
        }
    }

    /** As the floating button: Log only, not Drafts; and not on Records' other chips. */
    @Test
    fun `753 New find shows on Finds' Log tab only`() {
        setScreen(entries = emptyList(), finds = listOf(keptFindEntry("f1")))
        node("journal-switch-records").performClick()
        composeRule.waitForIdle()
        // RECORD -868 made Finds the default chip; this asserted "not on Records' default chip", which was All. Now All
        // is touched first and the same absence is asserted there.
        node("records-chip-all").performClick()
        composeRule.waitForIdle()
        assertFalse("not on the All chip", exists(SHORT_NEW_FIND_TAG))
        node("records-chip-finds").performClick()
        composeRule.waitForIdle()
        assertTrue("positive control: on Finds' Log", exists(SHORT_NEW_FIND_TAG))
        composeRule.onNodeWithText("Drafts").performClick()
        composeRule.waitForIdle()
        assertFalse("not on Drafts", exists(SHORT_NEW_FIND_TAG))
        composeRule.onNodeWithText("Log").performClick()
        composeRule.waitForIdle()
        assertTrue("back on Log", exists(SHORT_NEW_FIND_TAG))
    }

    /** Portrait is unchanged: the floating "New find", no L1 row. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `753 portrait keeps the floating New find button`() {
        setScreen(entries = emptyList(), finds = listOf(keptFindEntry("f1")))
        node("journal-switch-records").performClick()
        composeRule.waitForIdle()
        node("records-chip-finds").performClick()
        composeRule.waitForIdle()
        assertTrue("the floating button in portrait", exists(FINDS_FAB_TAG))
        assertFalse("no L1 row button in portrait", exists(SHORT_NEW_FIND_TAG))
    }

    // ── L6: the album in 5 columns ──

    @Test
    fun `L6 the album grid is 5 columns in a short window`() {
        setScreen(entries = emptyList(), galleryPhotos = (1..7).map { galleryPhoto("a$it") }, hostWidth = 640.dp)
        node("entries-view-album").performClick()
        composeRule.waitForIdle()
        val tiles = (1..6).map { bounds("entries-album-photo-a$it") }
        (0 until 5).forEach { assertEquals("the first five share the first row", tiles[0].top.value, tiles[it].top.value, 0.5f) }
        assertTrue("the sixth wraps to a second row", tiles[5].top >= tiles[0].bottom)
        // 640 less 2 x 16 dp padding less 4 x 3 dp gaps, over 5.
        assertEquals("tile width", (640f - 32f - 12f) / 5f, tiles[0].width.value, 1f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `L6 portrait keeps the album at 3 columns`() {
        setScreen(entries = emptyList(), galleryPhotos = (1..4).map { galleryPhoto("a$it") })
        node("entries-view-album").performClick()
        composeRule.waitForIdle()
        val tiles = (1..4).map { bounds("entries-album-photo-a$it") }
        assertEquals(tiles[0].top.value, tiles[2].top.value, 0.5f)
        assertTrue("the fourth wraps", tiles[3].top >= tiles[0].bottom)
    }
}

// ── fixtures (named apart from the other classes' file-private ones) ──

private fun card(id: String) = "entry-card-$id"
private fun hero(entryId: String, photoId: String) = "entry-hero-$entryId-$photoId"
private fun trackThumb(entryId: String) = "entry-track-thumbnail-$entryId"
private fun typePanel(entryId: String) = "entry-type-panel-$entryId"

private val TOUCH_SAMPLES_J5 = listOf(Offset(0.15f, 0.3f), Offset(0.5f, 0.5f), Offset(0.85f, 0.75f))

private fun entryOn(id: String, date: LocalDate): CartographyEntry =
    CartographyEntry.draft(id = id, date = date, updatedAtEpochMillis = 0L).copy(isDraft = false)

private fun keptFind(id: String, identification: String) =
    FindDecision(findId = id, foundOn = LocalDate.of(2026, 9, 26), ownIdentification = identification, hasPhotos = false, kept = true)

private fun keptTrack(id: String) = TrackDecision(trackId = id, name = null, distanceMeters = 1_500.0, durationMillis = 30 * 60_000L, pointCount = 10, kept = true)

/** Saturday 2026-09-26: writing, a kept find and a kept waypoint, no photo, no track. */
private val TEXT_FIND_ENTRY: CartographyEntry = entryOn("text-find", LocalDate.of(2026, 9, 26)).copy(
    text = "Morning walk",
    findDecisions = listOf(keptFind("f1", "C. formosus")),
    waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Pin", lat = 45.0, lng = -122.0, kept = true)),
)

private val TEXT_WAYPOINT_ENTRY: CartographyEntry = entryOn("text-waypoint", LocalDate.of(2026, 9, 25)).copy(
    text = "Pins only",
    waypointDecisions = listOf(WaypointDecision(waypointId = "w2", name = "Pin", lat = 45.0, lng = -122.0, kept = true)),
)

private val TEXT_REGION_ENTRY: CartographyEntry = entryOn("text-region", LocalDate.of(2026, 9, 24)).copy(
    text = "Scouting",
    offlineRegionDecisions = listOf(OfflineRegionDecision(offlineRegionId = 1L, name = "R", lat = 45.0, lng = -122.0, radiusKm = 5, kept = true)),
)

/** A photo and a kept track: the photo wins the slot. */
private val HERO_TRACK_ENTRY: CartographyEntry = entryOn("hero-track", LocalDate.of(2026, 9, 23)).copy(
    text = "Ridge",
    photos = listOf(PhotoAttachment(photoId = "p1", attachedAtEpochMillis = 1_000L)),
    trackDecisions = listOf(keptTrack("tr1")),
)

/** Two kept tracks and no photo: the track thumbnail, every kept track in one box (J4). */
private val TRACK_ONLY_ENTRY: CartographyEntry = entryOn("track-only", LocalDate.of(2026, 9, 22)).copy(
    text = "Two loops",
    trackDecisions = listOf(keptTrack("tr1"), keptTrack("tr2")),
)

/** No text, no photo, no track: J3 collapses it to a short row. */
private val BARE_ENTRY_J5: CartographyEntry = entryOn("bare", LocalDate.of(2026, 9, 14)).copy(
    offlineRegionDecisions = listOf(OfflineRegionDecision(offlineRegionId = 3L, name = "R3", lat = 45.0, lng = -122.0, radiusKm = 5, kept = true)),
)

private fun galleryPhoto(id: String): GalleryPhoto =
    GalleryPhoto(photo = LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = null), referencingEntryIds = emptyList())

private fun track(id: String): Track = Track(
    id = id,
    name = null,
    startedAtEpochMillis = 1_758_200_000_000L,
    endedAtEpochMillis = 1_758_203_600_000L,
    points = List(10) { i ->
        TrackPoint(lat = 45.0 + i * 0.001, lng = -122.0 + i * 0.002, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_758_200_000_000L + i * 1_000L)
    },
)

private val J5_EMPTY_MAP_DATA = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

private val CARDS_STUB_MAP_J5: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

/** A saved find for the RECORD -753 tests. */
private fun keptFindEntry(id: String): MushroomLogEntry =
    MushroomLogEntry.draft(id = id, location = com.zynergylabs.forager.app.domain.model.LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)
