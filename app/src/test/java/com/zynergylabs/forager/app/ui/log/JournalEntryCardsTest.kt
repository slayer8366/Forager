package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.domain.model.Region
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

/**
 * Journal redesign J3 (`prompts/preserved/2026-09-27-20.md`; plan J5, J9): the Entries timeline's
 * cards, driven through the real [JournalTab] with its Cartography callbacks wired to local state,
 * the way [JournalEntriesTest] does.
 *
 * Cards are checked by their **rendered text** (the plan's rule): title, day numeral and weekday,
 * species chip text, each stat's text with its unit. Every card or row a finger taps is touched with
 * `performTouchInput` at several points across its own bounds (CLAUDE.md, "A semantic
 * `performClick` asserts wiring, not routing"). Tags are literals, so each step's tests compile
 * against the base they were written before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class JournalEntryCardsTest {

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

    private val openedCartographyIds = mutableListOf<String>()

    private fun setScreen(
        entries: List<CartographyEntry>,
        galleryPhotos: List<GalleryPhoto> = emptyList(),
        tracks: List<Track> = emptyList(),
        logState: MushroomLogUiState = MushroomLogUiState(),
        distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
        getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
        tracksState: State<List<Track>>? = null,
        drafts: List<CartographyEntry> = emptyList(),
    ) {
        composeRule.setContent {
            var cartographyState by remember { mutableStateOf(CartographyUiState(entries = entries, draftEntries = drafts)) }
            JournalTab(
                uiState = logState,
                onOpenCameraForLogEntry = {},
                onOpenCameraForAlbum = {},
                onOpenCameraForCartographyEntry = {},
                mapSlot = CARDS_STUB_MAP,
                pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                basemap = Basemap.DEFAULT,
                onOpenEntry = {},
                onCloseEntry = {},
                onStartEntry = { _, _ -> },
                onEntryChanged = {},
                onStartEditingEntry = {},
                onSaveEntry = {},
                onCancelEditing = {},
                onLeaveEditingIncidentally = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onPullPhoto = {},
                onDeleteEntry = {},
                onSaveErrorDismissed = {},
                galleryPhotos = galleryPhotos,
                cartographyUiState = cartographyState,
                onOpenCartographyEntry = { id ->
                    openedCartographyIds += id
                    cartographyState = cartographyState.copy(editingEntry = cartographyState.entries.first { it.id == id })
                },
                onStartCartographyEntry = {},
                onCloseCartographyEntry = { cartographyState = cartographyState.copy(editingEntry = null) },
                onCartographyTextChanged = {},
                onCartographyTagsChanged = {},
                onSetFindDecision = { _, _ -> },
                onSetTrackDecision = { _, _ -> },
                onSetWaypointDecision = { _, _ -> },
                onSetOfflineRegionDecision = { _, _ -> },
                onToggleKeptPhoto = {},
                onFinishCartographyEntry = {},
                onDeleteCartographyEntry = {},
                getCartographyEntryMapData = { _, _ -> CARDS_EMPTY_MAP_DATA },
                getSavedTrackPaths = getSavedTrackPaths,
                getCartographyEntryOfflineRegion = { _, _ -> null },
                getCartographyEntryCurrentLocation = { LocationResult.LocationUnavailable },
                availabilityUiState = AvailabilityUiState(),
                distanceUnit = distanceUnit,
                currentTime = CurrentTimeProvider { 0L },
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                tracks = tracksState?.value ?: tracks,
                onTracksOpened = {},
                waypoints = emptyList(),
                waypointsErrorMessage = null,
                onDeleteWaypoint = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    /**
     * A node showing exactly [text] inside the card or row tagged [cardTag]. The unmerged tree: a
     * clickable `Card` merges its children's text into its own node, so in the merged tree no child
     * text node has the card as an ancestor.
     */
    private fun textIn(cardTag: String, text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(cardTag)), useUnmergedTree = true)

    /** A node tagged [tag] inside a card: the unmerged tree, since the card merges its children (see [textIn]). */
    private fun inCard(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun scrollListTo(tag: String) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        composeRule.waitForIdle()
    }

    // ── C1: the cards (plan J5) ──

    @Test
    fun `a card shows the day numeral and weekday, the first line as its title, and no ISO date`() {
        setScreen(listOf(FULL_ENTRY))

        node(cardTag(FULL_ENTRY.id)).assertIsDisplayed()
        textIn(cardTag(FULL_ENTRY.id), "26").assertIsDisplayed()
        textIn(cardTag(FULL_ENTRY.id), "SAT").assertIsDisplayed()
        textIn(cardTag(FULL_ENTRY.id), "Chanterelles along the ridge").assertIsDisplayed()
        // The rest of the writing is not part of the title.
        composeRule.onNodeWithText("Chanterelles along the ridge\nWet slope under Doug-fir").assertDoesNotExist()
        composeRule.onNodeWithText("2026-09-26", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a card shows kept finds' identifications as species chips, each species once, withheld finds left out`() {
        setScreen(listOf(FULL_ENTRY))

        composeRule.onAllNodes(hasText("C. formosus") and hasAnyAncestor(hasTestTag(cardTag(FULL_ENTRY.id))), useUnmergedTree = true).assertCountEquals(1)
        textIn(cardTag(FULL_ENTRY.id), "B. edulis").assertIsDisplayed()
        // Withheld: not part of the entry, so no chip.
        composeRule.onNodeWithText("A. muscaria").assertDoesNotExist()
    }

    @Test
    fun `a card's stats row shows each kept type's figure with its unit, in kilometres`() {
        setScreen(listOf(FULL_ENTRY), distanceUnit = DistanceUnit.KILOMETERS)

        val card = cardTag(FULL_ENTRY.id)
        textIn(card, "3 finds").assertIsDisplayed()
        // 4200 m and 2 h 10 min: the app's own track formatter (trackSubtitle), kept tracks only.
        textIn(card, "4.2 km · 2h 10m").assertIsDisplayed()
        textIn(card, "1 waypoint").assertIsDisplayed()
        textIn(card, "2 offline maps").assertIsDisplayed()
        // The old one-number summary is gone.
        composeRule.onNodeWithText("kept item", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a card's track stat follows the user's distance unit`() {
        setScreen(listOf(FULL_ENTRY), distanceUnit = DistanceUnit.MILES)

        // 4200 m = 2.61 mi.
        textIn(cardTag(FULL_ENTRY.id), "2.6 mi · 2h 10m").assertIsDisplayed()
        composeRule.onNodeWithText("4.2 km", substring = true).assertDoesNotExist()
    }

    @Test
    fun `singular stats read 1 find, 1 waypoint, 1 offline map, and a type with nothing kept shows no stat`() {
        setScreen(listOf(SINGLES_ENTRY))

        val card = cardTag(SINGLES_ENTRY.id)
        textIn(card, "1 find").assertIsDisplayed()
        textIn(card, "1 offline map").assertIsDisplayed()
        composeRule.onNodeWithText("waypoint", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(" km", substring = true).assertDoesNotExist()
    }

    @Test
    fun `entries sit under month headers, newest month first`() {
        setScreen(listOf(FULL_ENTRY, AUGUST_ENTRY))

        node(monthTag("2026-09")).assertIsDisplayed()
        node(monthTag("2026-09")).assert(hasText("SEPTEMBER 2026"))
        scrollListTo(monthTag("2026-08"))
        node(monthTag("2026-08")).assertIsDisplayed().assert(hasText("AUGUST 2026"))

        val sep = node(monthTag("2026-09")).getUnclippedBoundsInRoot()
        val aug = node(monthTag("2026-08")).getUnclippedBoundsInRoot()
        val fullCard = node(cardTag(FULL_ENTRY.id)).getUnclippedBoundsInRoot()
        val augCard = node(cardTag(AUGUST_ENTRY.id)).getUnclippedBoundsInRoot()
        assertTrue("September's card comes after its header", fullCard.top >= sep.bottom)
        assertTrue("August's header comes after September's card", aug.top >= fullCard.bottom)
        assertTrue("August's card comes after its header", augCard.top >= aug.bottom)
    }

    @Test
    fun `the month header stays pinned at the top while that month's cards scroll under it`() {
        setScreen(MANY_SEPTEMBER)

        val headerBefore = node(monthTag("2026-09")).getUnclippedBoundsInRoot()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(MANY_SEPTEMBER.size)
        composeRule.waitForIdle()

        // The first card has scrolled away (gone, or moved up under the header); the header has not.
        val firstGone = composeRule.onAllNodes(hasTestTag(cardTag(MANY_SEPTEMBER.first().id))).fetchSemanticsNodes().isEmpty() ||
            node(cardTag(MANY_SEPTEMBER.first().id)).getUnclippedBoundsInRoot().top < headerBefore.top
        assertTrue("the list scrolled", firstGone)
        node(monthTag("2026-09")).assertIsDisplayed()
        val headerAfter = node(monthTag("2026-09")).getUnclippedBoundsInRoot()
        val lastCard = node(cardTag(MANY_SEPTEMBER.last().id)).getUnclippedBoundsInRoot()
        assertTrue("the header has not scrolled up out of place (${headerBefore.top} -> ${headerAfter.top})", headerAfter.top <= headerBefore.top + 1.dp && headerAfter.top >= 0.dp)
        assertTrue("the header sits above the cards now showing", headerAfter.bottom <= lastCard.top)
    }

    @Test
    fun `an entry with no photo, no text and no track collapses to one short row with its day and stats`() {
        setScreen(listOf(FULL_ENTRY, BARE_ENTRY))
        scrollListTo(rowTag(BARE_ENTRY.id))

        node(rowTag(BARE_ENTRY.id)).assertIsDisplayed()
        node(cardTag(BARE_ENTRY.id)).assertDoesNotExist()
        textIn(rowTag(BARE_ENTRY.id), "14").assertIsDisplayed()
        textIn(rowTag(BARE_ENTRY.id), "MON").assertIsDisplayed()
        textIn(rowTag(BARE_ENTRY.id), "1 offline map").assertIsDisplayed()
        val row = node(rowTag(BARE_ENTRY.id)).getUnclippedBoundsInRoot()
        val card = node(cardTag(FULL_ENTRY.id)).getUnclippedBoundsInRoot()
        assertTrue("the short row is one line tall (${row.bottom - row.top})", row.bottom - row.top <= 64.dp)
        assertTrue("and shorter than a full card", (row.bottom - row.top) < (card.bottom - card.top))
        // The full card, beside it, is not collapsed.
        node(rowTag(FULL_ENTRY.id)).assertDoesNotExist()
    }

    @Test
    fun `an entry with nothing kept and nothing written collapses and says so`() {
        setScreen(listOf(EMPTY_ENTRY))

        node(rowTag(EMPTY_ENTRY.id)).assertIsDisplayed()
        textIn(rowTag(EMPTY_ENTRY.id), "Nothing kept").assertIsDisplayed()
    }

    // ── C2: the hero photo (owner: "Direct photos only (Recommended)") ──

    @Test
    fun `the hero is the earliest attached photo still in the gallery, skipping an earlier deleted one, on top of the card`() {
        setScreen(listOf(HERO_ENTRY), galleryPhotos = listOf(galleryPhoto("p-new"), galleryPhoto("p-mid")))

        inCard(heroTag(HERO_ENTRY.id, "p-mid")).assertIsDisplayed()
        inCard(heroTag(HERO_ENTRY.id, "p-deleted")).assertDoesNotExist()
        inCard(heroTag(HERO_ENTRY.id, "p-new")).assertDoesNotExist()
        composeRule.onAllNodes(hasTagPrefix("entry-hero-"), useUnmergedTree = true).assertCountEquals(1)
        val hero = inCard(heroTag(HERO_ENTRY.id, "p-mid")).getUnclippedBoundsInRoot()
        val card = node(cardTag(HERO_ENTRY.id)).getUnclippedBoundsInRoot()
        val title = textIn(cardTag(HERO_ENTRY.id), "Hero walk").getUnclippedBoundsInRoot()
        assertTrue("the hero sits inside the card, at its top ($hero in $card)", hero.top >= card.top && hero.top - card.top <= 1.dp && hero.bottom <= card.bottom)
        assertTrue("and above the card's text", hero.bottom <= title.top)
    }

    @Test
    fun `an entry with no attached photo has no hero, beside one that has`() {
        setScreen(listOf(HERO_ENTRY, FULL_ENTRY), galleryPhotos = listOf(galleryPhoto("p-mid")))

        // Positive control: the entry with a photo does draw one.
        inCard(heroTag(HERO_ENTRY.id, "p-mid")).assertExists()
        composeRule.onAllNodes(hasTagPrefix("entry-hero-${FULL_ENTRY.id}-"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a wordless, trackless entry is a card when its photo exists and collapses when every photo is gone`() {
        setScreen(listOf(PHOTO_ONLY_ENTRY, PHOTO_GONE_ENTRY), galleryPhotos = listOf(galleryPhoto("p-only")))

        node(cardTag(PHOTO_ONLY_ENTRY.id)).assertIsDisplayed()
        inCard(heroTag(PHOTO_ONLY_ENTRY.id, "p-only")).assertIsDisplayed()
        node(rowTag(PHOTO_ONLY_ENTRY.id)).assertDoesNotExist()
        node(rowTag(PHOTO_GONE_ENTRY.id)).assertIsDisplayed()
        composeRule.onAllNodes(hasTagPrefix("entry-hero-${PHOTO_GONE_ENTRY.id}-"), useUnmergedTree = true).assertCountEquals(0)
    }

    // ── C3: track thumbnails (owner: "Rows and Entries cards", "Join in memory (Recommended)") ──

    @Test
    fun `a card whose kept track is in the loaded list with two or more points shows its thumbnail`() {
        setScreen(listOf(TRACK_CARD), tracks = listOf(track("tr-a", points = 3)))

        inCard(entryThumbTag(TRACK_CARD.id)).assertIsDisplayed()
        val thumb = inCard(entryThumbTag(TRACK_CARD.id)).getUnclippedBoundsInRoot()
        val card = node(cardTag(TRACK_CARD.id)).getUnclippedBoundsInRoot()
        assertTrue("the thumbnail sits inside its card", thumb.left >= card.left && thumb.right <= card.right && thumb.top >= card.top && thumb.bottom <= card.bottom)
        assertTrue("and has a size (${thumb.right - thumb.left} x ${thumb.bottom - thumb.top})", thumb.right - thumb.left > 0.dp && thumb.bottom - thumb.top > 0.dp)
    }

    @Test
    fun `a card whose kept track has fewer than two points shows no thumbnail, beside one that has two`() {
        setScreen(
            listOf(TRACK_CARD, TRACK_CARD_B),
            tracks = listOf(track("tr-a", points = 2), track("tr-b", points = 1)),
        )

        inCard(entryThumbTag(TRACK_CARD.id)).assertExists()
        inCard(entryThumbTag(TRACK_CARD_B.id)).assertDoesNotExist()
    }

    @Test
    fun `a card whose kept track is not in the loaded list shows no thumbnail, beside one that is`() {
        setScreen(listOf(TRACK_CARD, TRACK_CARD_B), tracks = listOf(track("tr-a", points = 3)))

        inCard(entryThumbTag(TRACK_CARD.id)).assertExists()
        node(cardTag(TRACK_CARD_B.id)).assertExists()
        inCard(entryThumbTag(TRACK_CARD_B.id)).assertDoesNotExist()
    }

    @Test
    fun `only a kept track draws, a withheld track in the list gives no thumbnail`() {
        setScreen(listOf(TRACK_CARD, WITHHELD_TRACK_CARD), tracks = listOf(track("tr-a", points = 3), track("tr-w", points = 3)))

        inCard(entryThumbTag(TRACK_CARD.id)).assertExists()
        inCard(entryThumbTag(WITHHELD_TRACK_CARD.id)).assertDoesNotExist()
    }

    // ── F3: a kept track keeps its path (dispatch 2026-09-28-195, item 5; owner, 2026-09-29, "C: list screen loads lazily") ──
    //
    // Through JournalTab, the tab's real entry point. The lambda stands for the read of one entry's saved paths;
    // recording which entries it is asked about is what shows the read is lazy.

    @Test
    fun `a card whose kept track is gone from the loaded list draws the path saved for it`() {
        val asked = mutableListOf<String>()
        setScreen(listOf(TRACK_CARD), tracks = emptyList(), getSavedTrackPaths = { id -> asked += id; mapOf("tr-a" to SAVED_PATH) })

        inCard(entryThumbTag(TRACK_CARD.id)).assertIsDisplayed()
        assertEquals(listOf(TRACK_CARD.id), asked.distinct())
    }

    @Test
    fun `a saved path is asked for only the entries whose kept track is not in the loaded list`() {
        val asked = mutableListOf<String>()
        setScreen(
            listOf(TRACK_CARD, TRACK_CARD_B),
            tracks = listOf(track("tr-a", points = 3)),
            getSavedTrackPaths = { id -> asked += id; mapOf("tr-b" to SAVED_PATH) },
        )

        inCard(entryThumbTag(TRACK_CARD.id)).assertExists()
        inCard(entryThumbTag(TRACK_CARD_B.id)).assertExists()
        assertEquals("only the entry whose track is gone is read", listOf(TRACK_CARD_B.id), asked.distinct())
    }

    @Test
    fun `a card keeps its thumbnail when its track is deleted from the loaded list`() {
        val loaded = mutableStateOf(listOf(track("tr-a", points = 3)))
        setScreen(listOf(TRACK_CARD), tracksState = loaded, getSavedTrackPaths = { mapOf("tr-a" to SAVED_PATH) })
        inCard(entryThumbTag(TRACK_CARD.id)).assertIsDisplayed()

        composeRule.runOnIdle { loaded.value = emptyList() }
        composeRule.waitForIdle()

        inCard(entryThumbTag(TRACK_CARD.id)).assertIsDisplayed()
    }

    @Test
    fun `a withheld track's saved path gives no thumbnail and is not asked for`() {
        val asked = mutableListOf<String>()
        setScreen(listOf(WITHHELD_TRACK_CARD), tracks = emptyList(), getSavedTrackPaths = { id -> asked += id; mapOf("tr-w" to SAVED_PATH) })

        node(cardTag(WITHHELD_TRACK_CARD.id)).assertExists()
        inCard(entryThumbTag(WITHHELD_TRACK_CARD.id)).assertDoesNotExist()
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a saved path of one point gives no thumbnail, as a live track of one point does not`() {
        setScreen(listOf(TRACK_CARD), tracks = emptyList(), getSavedTrackPaths = { mapOf("tr-a" to listOf(LatLng(45.0, -122.0))) })

        node(cardTag(TRACK_CARD.id)).assertExists()
        inCard(entryThumbTag(TRACK_CARD.id)).assertDoesNotExist()
    }

    @Test
    fun `a draft card in the drafts list draws the path saved for its kept track that is gone, through the Journal tab`() {
        val draftA = CartographyEntry.draft(id = "draft-a", date = LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 2L).copy(text = "Half a loop", trackDecisions = listOf(trackDecision("tr-a", meters = 1_500.0, millis = 30 * 60_000L)))
        val draftB = CartographyEntry.draft(id = "draft-b", date = LocalDate.of(2026, 9, 11), updatedAtEpochMillis = 1L).copy(text = "Another")
        setScreen(listOf(TRACK_CARD), drafts = listOf(draftA, draftB), getSavedTrackPaths = { mapOf("tr-a" to SAVED_PATH) })

        node("entries-drafts-continue").performClick()
        composeRule.waitForIdle()

        inCard(entryThumbTag(draftA.id)).assertIsDisplayed()
    }

    // ── J4, D5: several kept tracks (owner rulings "All in one box" and "Sum, with a count") ──

    @Test
    fun `a card keeping two tracks draws them in one thumbnail`() {
        setScreen(listOf(MULTI_TRACK_CARD, TRACK_CARD_B), tracks = listOf(track("tr-m1", points = 3), track("tr-m2", points = 4), track("tr-b", points = 3)))

        inCard(entryThumbTag(MULTI_TRACK_CARD.id)).assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag(entryThumbTag(MULTI_TRACK_CARD.id)), useUnmergedTree = true).assertCountEquals(1)
        inCard(entryThumbTag(TRACK_CARD_B.id)).assertExists()
    }

    @Test
    fun `a card keeping two tracks, one of them not loaded, still draws the one it has`() {
        setScreen(listOf(MULTI_TRACK_CARD), tracks = listOf(track("tr-m2", points = 4)))

        inCard(entryThumbTag(MULTI_TRACK_CARD.id)).assertExists()
    }

    @Test
    fun `a card keeping two tracks shows their total distance and duration labelled with the count`() {
        setScreen(listOf(MULTI_TRACK_CARD, TRACK_CARD))

        // 3000 m + 2400 m, 80 min + 50 min.
        textIn(cardTag(MULTI_TRACK_CARD.id), "2 tracks · 5.4 km · 2h 10m").assertIsDisplayed()
        // One kept track keeps its plain figure, with no count.
        textIn(cardTag(TRACK_CARD.id), "1.5 km · 30m").assertIsDisplayed()
        composeRule.onNodeWithText("1 track", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a Recorded Tracks row shows its thumbnail with two or more points and none with fewer`() {
        setScreen(
            listOf(TRACK_CARD),
            tracks = listOf(track("tr-a", points = 2), track("tr-b", points = 1), track("tr-c", points = 0)),
        )
        node(SWITCH_RECORDS).performTouchInput { click(center) }
        composeRule.waitForIdle()
        node(TRACKS_CHIP).performTouchInput { click(center) }
        composeRule.waitForIdle()

        inCard(rowThumbTag("tr-a")).assertIsDisplayed()
        inCard(rowThumbTag("tr-b")).assertDoesNotExist()
        inCard(rowThumbTag("tr-c")).assertDoesNotExist()
        // Each row is still there, so the absences are the thumbnail's, not the row's.
        node("share-track-tr-b").assertExists()
        node("share-track-tr-c").assertExists()
    }

    // ── C4: one column in compact portrait (plan J9) ──

    @Test
    fun `in compact portrait the cards are one full-width column, one card per row`() {
        val three = listOf(
            committed("col-1", LocalDate.of(2026, 9, 3)).copy(text = "First"),
            committed("col-2", LocalDate.of(2026, 9, 2)).copy(text = "Second"),
            committed("col-3", LocalDate.of(2026, 9, 1)).copy(text = "Third"),
        )
        setScreen(three)

        val list = composeRule.onNode(hasScrollToIndexAction()).getUnclippedBoundsInRoot()
        val cards = three.map { node(cardTag(it.id)).getUnclippedBoundsInRoot() }
        for ((i, card) in cards.withIndex()) {
            // The list's own 16 dp side padding either side; nothing else shares the row.
            assertEquals("card $i starts at the list's start padding", (list.left + 16.dp).value, card.left.value, 1f)
            assertEquals("card $i ends at the list's end padding", (list.right - 16.dp).value, card.right.value, 1f)
        }
        for (i in 1 until cards.size) {
            assertTrue("card $i sits below card ${i - 1} (${cards[i]} vs ${cards[i - 1]})", cards[i].top >= cards[i - 1].bottom)
        }
    }

    // ── C5: the album's find badge marks saved finds only (owner: "Saved finds only (Recommended)") ──

    @Test
    fun `the album's find badge marks a photo on a saved find, and not one attached only to a draft find`() {
        setScreen(
            listOf(FULL_ENTRY),
            galleryPhotos = listOf(
                findPhoto("p-draft", listOf("find-draft")),
                findPhoto("p-saved", listOf("find-saved")),
                findPhoto("p-both", listOf("find-draft", "find-saved")),
            ),
            logState = MushroomLogUiState(draftEntries = listOf(MushroomLogEntry.draft(id = "find-draft", location = null, date = LocalDate.of(2026, 9, 26)))),
        )
        node(VIEW_ALBUM).performTouchInput { click(center) }
        composeRule.waitForIdle()

        inCard(albumPhotoTag("p-draft")).assertExists()
        inCard(findBadgeTag("p-draft")).assertDoesNotExist()
        inCard(findBadgeTag("p-saved")).assertIsDisplayed()
        inCard(findBadgeTag("p-both")).assertIsDisplayed()
    }

    @Test
    fun `touching a card at several points opens that entry`() {
        setScreen(listOf(FULL_ENTRY, AUGUST_ENTRY))

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            node(cardTag(FULL_ENTRY.id)).performTouchInput { click(Offset(width * point.x, height * point.y)) }
            composeRule.waitForIdle()
            assertEquals(List(i + 1) { FULL_ENTRY.id }, openedCartographyIds)
            node(ENTRIES_HOME).assertDoesNotExist()
            pressBack()
            node(ENTRIES_HOME).assertIsDisplayed()
        }
    }

    @Test
    fun `touching a collapsed row at several points opens that entry`() {
        setScreen(listOf(BARE_ENTRY, EMPTY_ENTRY))

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            node(rowTag(BARE_ENTRY.id)).performTouchInput { click(Offset(width * point.x, height * point.y)) }
            composeRule.waitForIdle()
            assertEquals(List(i + 1) { BARE_ENTRY.id }, openedCartographyIds)
            node(ENTRIES_HOME).assertDoesNotExist()
            pressBack()
            node(ENTRIES_HOME).assertIsDisplayed()
        }
    }
}

// Literals, not the production constants, so these compile against the base they were written before.
private const val ENTRIES_HOME = "entries-home"
private fun cardTag(entryId: String): String = "entry-card-$entryId"
private fun rowTag(entryId: String): String = "entry-row-$entryId"
private fun monthTag(yearMonth: String): String = "entries-month-$yearMonth"

private fun heroTag(entryId: String, photoId: String): String = "entry-hero-$entryId-$photoId"

private fun hasTagPrefix(prefix: String): SemanticsMatcher = SemanticsMatcher("TestTag starts with '$prefix'") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}

private fun galleryPhoto(id: String): GalleryPhoto =
    GalleryPhoto(photo = LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = null), referencingEntryIds = emptyList())

/** Three photos attached out of order; the earliest ("p-deleted") is not in the gallery any more. */
private val HERO_ENTRY: CartographyEntry = committed("hero", LocalDate.of(2026, 9, 24)).copy(
    text = "Hero walk",
    photos = listOf(
        PhotoAttachment(photoId = "p-new", attachedAtEpochMillis = 3_000L),
        PhotoAttachment(photoId = "p-deleted", attachedAtEpochMillis = 1_000L),
        PhotoAttachment(photoId = "p-mid", attachedAtEpochMillis = 2_000L),
    ),
)

/** No text, no track, one photo that exists. */
private val PHOTO_ONLY_ENTRY: CartographyEntry = committed("photo-only", LocalDate.of(2026, 9, 23)).copy(
    photos = listOf(PhotoAttachment(photoId = "p-only", attachedAtEpochMillis = 1_000L)),
)

/** No text, no track, one photo that has been deleted from the gallery. */
private val PHOTO_GONE_ENTRY: CartographyEntry = committed("photo-gone", LocalDate.of(2026, 9, 22)).copy(
    photos = listOf(PhotoAttachment(photoId = "p-gone", attachedAtEpochMillis = 1_000L)),
)

private const val SWITCH_RECORDS = "journal-switch-records"
private const val TRACKS_CHIP = "records-chip-tracks"
private fun entryThumbTag(entryId: String): String = "entry-track-thumbnail-$entryId"
private fun rowThumbTag(trackId: String): String = "track-thumbnail-$trackId"

/** A recorded track with [points] points walking north-east from a fixed start. */
private fun track(id: String, points: Int): Track = Track(
    id = id,
    name = null,
    startedAtEpochMillis = 1_758_200_000_000L,
    endedAtEpochMillis = 1_758_203_600_000L,
    points = List(points) { i ->
        TrackPoint(lat = 45.0 + i * 0.001, lng = -122.0 + i * 0.002, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_758_200_000_000L + i * 1_000L)
    },
)

private val TRACK_CARD: CartographyEntry = committed("with-track-a", LocalDate.of(2026, 9, 12)).copy(
    text = "Loop A",
    trackDecisions = listOf(trackDecision("tr-a", meters = 1_500.0, millis = 30 * 60_000L)),
)

private val TRACK_CARD_B: CartographyEntry = committed("with-track-b", LocalDate.of(2026, 9, 11)).copy(
    text = "Loop B",
    trackDecisions = listOf(trackDecision("tr-b", meters = 800.0, millis = 20 * 60_000L)),
)

/** Two kept tracks, 3000 m in 80 min and 2400 m in 50 min, and one withheld. */
private val MULTI_TRACK_CARD: CartographyEntry = committed("multi-track", LocalDate.of(2026, 9, 13)).copy(
    text = "Two loops",
    trackDecisions = listOf(
        trackDecision("tr-m1", meters = 3_000.0, millis = 80 * 60_000L),
        trackDecision("tr-m2", meters = 2_400.0, millis = 50 * 60_000L),
        trackDecision("tr-w", meters = 900.0, millis = 10 * 60_000L, kept = false),
    ),
)

private val WITHHELD_TRACK_CARD: CartographyEntry = committed("withheld-track", LocalDate.of(2026, 9, 9)).copy(
    text = "Kept nothing of the walk",
    trackDecisions = listOf(trackDecision("tr-w", meters = 800.0, millis = 20 * 60_000L, kept = false)),
)

private val SAVED_PATH = listOf(LatLng(45.0, -122.0), LatLng(45.001, -122.002), LatLng(45.002, -122.004))

private const val VIEW_ALBUM = "entries-view-album"
private fun albumPhotoTag(id: String): String = "entries-album-photo-$id"
private fun findBadgeTag(id: String): String = "entries-album-badge-find-$id"

private fun findPhoto(id: String, findIds: List<String>): GalleryPhoto =
    GalleryPhoto(photo = LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = null), referencingEntryIds = findIds)

/** Three touches spread across a control: near its start edge, its centre, near its end edge, at differing heights. */
private val TOUCH_SAMPLES = listOf(Offset(0.12f, 0.3f), Offset(0.5f, 0.5f), Offset(0.88f, 0.7f))

private fun committed(id: String, date: LocalDate, updatedAt: Long = 0L): CartographyEntry =
    CartographyEntry.draft(id = id, date = date, updatedAtEpochMillis = updatedAt).copy(isDraft = false)

private fun find(id: String, identification: String?, kept: Boolean = true) =
    FindDecision(findId = id, foundOn = LocalDate.of(2026, 9, 26), ownIdentification = identification, hasPhotos = false, kept = kept)

private fun trackDecision(id: String, meters: Double, millis: Long, kept: Boolean = true, points: Int = 10) =
    TrackDecision(trackId = id, name = null, distanceMeters = meters, durationMillis = millis, pointCount = points, kept = kept)

private fun waypoint(id: String, kept: Boolean = true) = WaypointDecision(waypointId = id, name = "Pin $id", lat = 45.0, lng = -122.0, kept = kept)

private fun region(id: Long, kept: Boolean = true) =
    OfflineRegionDecision(offlineRegionId = id, name = "Region $id", lat = 45.0, lng = -122.0, radiusKm = 5, kept = kept)

/**
 * Saturday 2026-09-26: two lines of writing, three kept finds over two species and one withheld, one
 * kept track (4200 m, 2 h 10 min) and one withheld, one kept waypoint and one withheld, two kept
 * offline maps.
 */
private val FULL_ENTRY: CartographyEntry = committed("full", LocalDate.of(2026, 9, 26)).copy(
    text = "Chanterelles along the ridge\nWet slope under Doug-fir",
    findDecisions = listOf(
        find("f1", "C. formosus"),
        find("f2", "B. edulis"),
        find("f3", "C. formosus"),
        find("f4", "A. muscaria", kept = false),
    ),
    trackDecisions = listOf(
        trackDecision("t-kept", meters = 4_200.0, millis = (2 * 60 + 10) * 60_000L),
        trackDecision("t-withheld", meters = 9_000.0, millis = 60 * 60_000L, kept = false),
    ),
    waypointDecisions = listOf(waypoint("w1"), waypoint("w2", kept = false)),
    offlineRegionDecisions = listOf(region(1), region(2)),
)

/** One of each countable type, no track, some writing (so it does not collapse). */
private val SINGLES_ENTRY: CartographyEntry = committed("singles", LocalDate.of(2026, 9, 20)).copy(
    text = "Quick loop",
    findDecisions = listOf(find("s1", null)),
    waypointDecisions = listOf(waypoint("sw", kept = false)),
    offlineRegionDecisions = listOf(region(7)),
)

/** Monday 2026-09-14: no text, no photo, no track; one kept offline map. */
private val BARE_ENTRY: CartographyEntry = committed("bare", LocalDate.of(2026, 9, 14)).copy(
    offlineRegionDecisions = listOf(region(3)),
)

/** Nothing kept, nothing written. */
private val EMPTY_ENTRY: CartographyEntry = committed("empty", LocalDate.of(2026, 9, 10))

private val AUGUST_ENTRY: CartographyEntry = committed("august", LocalDate.of(2026, 8, 30)).copy(text = "Late summer scouting")

/** Twenty-eight September entries with writing, newest first: more than the screen holds. */
private val MANY_SEPTEMBER: List<CartographyEntry> = (28 downTo 1).map { day ->
    committed("sep-$day", LocalDate.of(2026, 9, day)).copy(text = "Walk on the $day")
}

private val CARDS_EMPTY_MAP_DATA = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

private val CARDS_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
