package com.zynergylabs.forager.app.ui.log

import android.Manifest
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
import java.time.ZoneId
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
 * Journal redesign J2 (`prompts/preserved/2026-09-27-18.md`; plan J1, J2, J3, J7): the Entries |
 * Records switch, the Drafts banner, the timeline/album toggle and the floating button, driven
 * through the real [JournalTab] with its Cartography callbacks wired to local state, the way
 * [RecordsFilterChipsTest] wires the finds side.
 *
 * Back goes through the host Activity's real `OnBackPressedDispatcher`, the only thing that settles
 * which `BackHandler` takes a press. Every control a finger taps is touched with
 * `performTouchInput` at several points across its own bounds (CLAUDE.md, "A semantic
 * `performClick` asserts wiring, not routing"). Tags are literals, so each step's tests compile
 * against the base they were written before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class JournalEntriesTest {

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
    private var startedCartographyEntries = 0
    private var albumCameraOpens = 0

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    /**
     * Every contract launched through the Activity Result API from inside the screen (added by the
     * second J2 coder). The album's Import goes out to the system photo picker, which a Robolectric
     * test cannot drive; this records the launch itself, the real entry point the menu item reaches.
     */
    private val launchedContracts = mutableListOf<ActivityResultContract<*, *>>()
    private val recordingRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launchedContracts += contract
            }
        }
    }

    private fun photoPickerLaunches(): Int = launchedContracts.count { it is ActivityResultContracts.PickMultipleVisualMedia }

    private fun grantCamera() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
    }

    private fun setScreen(
        cartography: CartographyUiState = CartographyUiState(entries = listOf(ENTRIES_COMMITTED)),
        galleryPhotos: List<GalleryPhoto> = emptyList(),
        galleryPhotoEntryReferenceCounts: Map<String, Int> = emptyMap(),
        journalState: JournalScreenState? = null,
    ) {
        composeRule.setContent {
          CompositionLocalProvider(LocalActivityResultRegistryOwner provides recordingRegistryOwner) {
            var logState by remember { mutableStateOf(MushroomLogUiState()) }
            var cartographyState by remember { mutableStateOf(cartography) }
            val state = journalState ?: rememberJournalScreenState()
            JournalTab(
                uiState = logState,
                onOpenCameraForLogEntry = {},
                onOpenCameraForAlbum = { albumCameraOpens++ },
                onOpenCameraForCartographyEntry = {},
                mapSlot = ENTRIES_STUB_MAP,
                pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                basemap = Basemap.DEFAULT,
                onOpenEntry = { id -> logState = logState.copy(editingEntry = logState.entries.first { it.id == id }) },
                onCloseEntry = { logState = logState.copy(editingEntry = null) },
                onStartEntry = { location, date ->
                    logState = logState.copy(editingEntry = MushroomLogEntry.draft(id = "new-find", location = location, date = date))
                },
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
                galleryPhotoEntryReferenceCounts = galleryPhotoEntryReferenceCounts,
                cartographyUiState = cartographyState,
                onOpenCartographyEntry = { id ->
                    openedCartographyIds += id
                    cartographyState = cartographyState.copy(
                        editingEntry = (cartographyState.entries + cartographyState.draftEntries).first { it.id == id },
                    )
                },
                onStartCartographyEntry = { date ->
                    startedCartographyEntries++
                    val started = CartographyEntry.draft(id = "started-$startedCartographyEntries", date = date, updatedAtEpochMillis = 0L)
                    cartographyState = cartographyState.copy(editingEntry = started, draftEntries = cartographyState.draftEntries + started)
                },
                onCloseCartographyEntry = { cartographyState = cartographyState.copy(editingEntry = null, hasUnsavedChanges = false) },
                onCartographyTextChanged = {},
                onCartographyTagsChanged = {},
                onSetFindDecision = { _, _ -> },
                onSetTrackDecision = { _, _ -> },
                onSetWaypointDecision = { _, _ -> },
                onSetOfflineRegionDecision = { _, _ -> },
                onToggleKeptPhoto = {},
                // Finish commits the open draft: it leaves draftEntries, joins entries, and closes
                // (added by the second coder; no test before it tapped Finish).
                onFinishCartographyEntry = {
                    val finished = cartographyState.editingEntry
                    if (finished != null) {
                        cartographyState = cartographyState.copy(
                            editingEntry = null,
                            draftEntries = cartographyState.draftEntries.filterNot { it.id == finished.id },
                            entries = cartographyState.entries + finished.copy(isDraft = false),
                        )
                    }
                },
                onDeleteCartographyEntry = {},
                getCartographyEntryMapData = { _, _ -> ENTRIES_EMPTY_MAP_DATA },
                getCartographyEntryOfflineRegion = { _, _ -> null },
                getCartographyEntryCurrentLocation = { LocationResult.LocationUnavailable },
                availabilityUiState = AvailabilityUiState(),
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { 0L },
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
                journalState = state,
            )
          }
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** One real touch at [fraction] of the node's own width and height. */
    private fun touch(tag: String, fraction: Offset) {
        node(tag).performTouchInput { click(Offset(width * fraction.x, height * fraction.y)) }
        composeRule.waitForIdle()
    }

    // ── T1: the switch ──

    @Test
    fun `the switch shows Entries and Records, Entries selected, and no Cartography label`() {
        setScreen()

        node(SWITCH_ENTRIES).assert(hasText("Entries")).assertIsSelected()
        node(SWITCH_RECORDS).assert(hasText("Records")).assertIsNotSelected()
        composeRule.onNodeWithText("Cartography").assertDoesNotExist()
        // Entries shows Cartography's content (an entry card), not Records' chips.
        composeRule.onNode(entryMatcher(ENTRIES_COMMITTED)).assertExists()
        node(RECORDS_CHIP_ROW).assertDoesNotExist()
    }

    @Test
    fun `touching each side of the switch at several points switches between Entries and Records`() {
        setScreen()

        for (point in TOUCH_SAMPLES) {
            touch(SWITCH_RECORDS, point)
            node(SWITCH_RECORDS).assertIsSelected()
            node(SWITCH_ENTRIES).assertIsNotSelected()
            node(RECORDS_CHIP_ROW).assertExists()
            composeRule.onNode(entryMatcher(ENTRIES_COMMITTED)).assertDoesNotExist()

            touch(SWITCH_ENTRIES, point)
            node(SWITCH_ENTRIES).assertIsSelected()
            node(SWITCH_RECORDS).assertIsNotSelected()
            node(RECORDS_CHIP_ROW).assertDoesNotExist()
            composeRule.onNode(entryMatcher(ENTRIES_COMMITTED)).assertExists()
        }
    }

    @Test
    fun `Back from Records steps to Entries`() {
        setScreen()
        touch(SWITCH_RECORDS, Offset(0.5f, 0.5f))
        node(SWITCH_RECORDS).assertIsSelected()

        pressBack()

        node(SWITCH_ENTRIES).assertIsSelected()
        node(SWITCH_RECORDS).assertIsNotSelected()
        node(RECORDS_CHIP_ROW).assertDoesNotExist()
    }

    // ── T2: the Drafts banner ──

    private val oneDraft = CartographyUiState(entries = listOf(ENTRIES_COMMITTED), draftEntries = listOf(ENTRIES_DRAFT_A))
    private val threeDrafts = CartographyUiState(
        entries = listOf(ENTRIES_COMMITTED),
        draftEntries = listOf(ENTRIES_DRAFT_A, ENTRIES_DRAFT_B, ENTRIES_DRAFT_C),
    )

    @Test
    fun `with one draft the banner reads 1 unfinished entry with Continue, and the Drafts sub-tab is gone`() {
        setScreen(oneDraft)

        node(DRAFTS_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("✎ 1 unfinished entry").assertIsDisplayed()
        node(DRAFTS_CONTINUE).assert(hasText("Continue ›"))
        composeRule.onNodeWithText("Drafts", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("unfinished entries", substring = true).assertDoesNotExist()
    }

    @Test
    fun `with three drafts the banner reads 3 unfinished entries`() {
        setScreen(threeDrafts)

        composeRule.onNodeWithText("✎ 3 unfinished entries").assertIsDisplayed()
        node(DRAFTS_CONTINUE).assert(hasText("Continue ›"))
    }

    @Test
    fun `with no drafts there is no banner`() {
        setScreen(CartographyUiState(entries = listOf(ENTRIES_COMMITTED)))

        node(ENTRIES_HOME).assertIsDisplayed()
        node(DRAFTS_BANNER).assertDoesNotExist()
        composeRule.onNodeWithText("unfinished", substring = true).assertDoesNotExist()
    }

    @Test
    fun `with one draft, touching Continue at several points opens that draft in the editor`() {
        setScreen(oneDraft)

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            touch(DRAFTS_CONTINUE, point)

            assertEquals(List(i + 1) { ENTRIES_DRAFT_A.id }, openedCartographyIds)
            composeRule.onNodeWithText(EDITOR_FIELD).assertIsDisplayed()
            node(DRAFTS_LIST).assertDoesNotExist()

            pressBack()
            node(ENTRIES_HOME).assertIsDisplayed()
        }
    }

    @Test
    fun `with several drafts, touching Continue at several points opens the full-screen drafts list, and Back returns to Entries`() {
        setScreen(threeDrafts)

        for (point in TOUCH_SAMPLES) {
            touch(DRAFTS_CONTINUE, point)

            node(DRAFTS_LIST).assertIsDisplayed()
            node(ENTRIES_HOME).assertDoesNotExist()
            for (draft in threeDrafts.draftEntries) composeRule.onNode(entryMatcher(draft)).assertExists()
            // The committed entry is not in the drafts list.
            composeRule.onNode(entryMatcher(ENTRIES_COMMITTED)).assertDoesNotExist()
            assertEquals("Continue with several drafts opens no draft by itself", emptyList<String>(), openedCartographyIds)

            pressBack()

            node(DRAFTS_LIST).assertDoesNotExist()
            node(ENTRIES_HOME).assertIsDisplayed()
            node(DRAFTS_BANNER).assertIsDisplayed()
        }
    }

    @Test
    fun `the drafts list's back arrow, touched at several points, returns to Entries`() {
        setScreen(threeDrafts)

        for (point in TOUCH_SAMPLES) {
            touch(DRAFTS_CONTINUE, Offset(0.5f, 0.5f))
            node(DRAFTS_LIST).assertIsDisplayed()

            touch(DRAFTS_LIST_BACK, point)

            node(DRAFTS_LIST).assertDoesNotExist()
            node(ENTRIES_HOME).assertIsDisplayed()
        }
    }

    /**
     * Where Back from a draft opened out of the list lands is pinned by the test after this one (the
     * owner's "Back to the list"); this one re-opens the list through Continue only if it is not
     * already showing, so each sample's pick still has to be a real touch on a card in the list.
     */
    @Test
    fun `a draft picked from the list by a touch at several points opens that draft in the editor`() {
        setScreen(threeDrafts)

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            if (composeRule.onAllNodesWithTag(DRAFTS_LIST).fetchSemanticsNodes().isEmpty()) touch(DRAFTS_CONTINUE, Offset(0.5f, 0.5f))
            node(DRAFTS_LIST).assertIsDisplayed()

            composeRule.onNode(entryMatcher(ENTRIES_DRAFT_B)).performTouchInput { click(Offset(width * point.x, height * point.y)) }
            composeRule.waitForIdle()

            assertEquals(List(i + 1) { ENTRIES_DRAFT_B.id }, openedCartographyIds)
            // A draft opens straight into the editor, never the read-only view.
            composeRule.onNodeWithText(EDITOR_FIELD).assertIsDisplayed()

            pressBack()
            composeRule.onNodeWithText(EDITOR_FIELD).assertDoesNotExist()
        }
    }

    // ── T2, added by the second coder: Back from a draft opened out of the list (owner, "Back to the list (Recommended)") ──

    @Test
    fun `Back from a draft opened out of the drafts list returns to the list, and Back again returns to Entries`() {
        setScreen(threeDrafts)
        touch(DRAFTS_CONTINUE, Offset(0.5f, 0.5f))

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            node(DRAFTS_LIST).assertIsDisplayed()
            composeRule.onNode(entryMatcher(ENTRIES_DRAFT_C)).performTouchInput { click(Offset(width * point.x, height * point.y)) }
            composeRule.waitForIdle()
            assertEquals(List(i + 1) { ENTRIES_DRAFT_C.id }, openedCartographyIds)
            composeRule.onNodeWithText(EDITOR_FIELD).assertIsDisplayed()

            pressBack()

            composeRule.onNodeWithText(EDITOR_FIELD).assertDoesNotExist()
            node(DRAFTS_LIST).assertIsDisplayed()
            node(ENTRIES_HOME).assertDoesNotExist()
        }

        pressBack()

        node(DRAFTS_LIST).assertDoesNotExist()
        node(ENTRIES_HOME).assertIsDisplayed()
        node(DRAFTS_BANNER).assertIsDisplayed()
    }

    /**
     * The list is where Back lands while drafts remain; once the last one is finished there is no
     * list to return to, so the draft closes onto Entries. "Finish entry" is a semantic click: the
     * claim here is where the screen lands, not how the touch reaches the button.
     */
    @Test
    fun `finishing a draft opened from the list returns to the list while drafts remain, and to Entries when none do`() {
        setScreen(CartographyUiState(entries = listOf(ENTRIES_COMMITTED), draftEntries = listOf(ENTRIES_DRAFT_A, ENTRIES_DRAFT_B)))
        touch(DRAFTS_CONTINUE, Offset(0.5f, 0.5f))

        composeRule.onNode(entryMatcher(ENTRIES_DRAFT_A)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finish entry").performClick()
        composeRule.waitForIdle()

        node(DRAFTS_LIST).assertIsDisplayed()
        composeRule.onNode(entryMatcher(ENTRIES_DRAFT_B)).assertIsDisplayed()
        composeRule.onNode(entryMatcher(ENTRIES_DRAFT_A)).assertDoesNotExist()

        composeRule.onNode(entryMatcher(ENTRIES_DRAFT_B)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finish entry").performClick()
        composeRule.waitForIdle()

        node(DRAFTS_LIST).assertDoesNotExist()
        node(ENTRIES_HOME).assertIsDisplayed()
        node(DRAFTS_BANNER).assertDoesNotExist()

        // The list is gone, not just hidden: a new draft started now and closed lands on Entries,
        // not on a drafts list that reappears because a new draft exists.
        touch(FAB, Offset(0.5f, 0.5f))
        composeRule.onNodeWithText(EDITOR_FIELD).assertIsDisplayed()
        pressBack()
        node(DRAFTS_LIST).assertDoesNotExist()
        node(ENTRIES_HOME).assertIsDisplayed()
    }

    // ── T3: the timeline/album toggle ──

    @Test
    fun `the view toggle shows timeline and album with timeline on, and the Album sub-tab is gone`() {
        setScreen()

        node(VIEW_TIMELINE).assertIsOn()
        node(VIEW_ALBUM).assertIsOff()
        composeRule.onNodeWithText("Album").assertDoesNotExist()
        // No sub-tab row at all: "Entries" appears once, on the switch.
        composeRule.onAllNodesWithText("Entries").assertCountEquals(1)
        node(ALBUM).assertDoesNotExist()
    }

    @Test
    fun `touching album at several points shows the album, and touching timeline brings the entries back`() {
        setScreen(galleryPhotos = listOf(albumPhoto("p1", ALBUM_DAY_1)))

        for (point in TOUCH_SAMPLES) {
            touch(VIEW_ALBUM, point)
            node(VIEW_ALBUM).assertIsOn()
            node(VIEW_TIMELINE).assertIsOff()
            node(ALBUM).assertIsDisplayed()
            node(albumPhotoTag("p1")).assertExists()
            composeRule.onNode(entryMatcher(ENTRIES_COMMITTED)).assertDoesNotExist()

            touch(VIEW_TIMELINE, point)
            node(VIEW_TIMELINE).assertIsOn()
            node(ALBUM).assertDoesNotExist()
            composeRule.onNode(entryMatcher(ENTRIES_COMMITTED)).assertIsDisplayed()
        }
    }

    @Test
    fun `Back from the album returns to the timeline`() {
        setScreen()
        touch(VIEW_ALBUM, Offset(0.5f, 0.5f))
        node(ALBUM).assertIsDisplayed()

        pressBack()

        node(VIEW_TIMELINE).assertIsOn()
        node(ALBUM).assertDoesNotExist()
        node(ENTRIES_HOME).assertIsDisplayed()
        node(SWITCH_ENTRIES).assertIsSelected()
    }

    @Test
    fun `the album groups photos by day, newest day first and unknown dates last, in 3 columns with 3 dp gaps`() {
        val photos = listOf(
            albumPhoto("a1", ALBUM_DAY_1), albumPhoto("b1", ALBUM_DAY_2), albumPhoto("a2", ALBUM_DAY_1),
            albumPhoto("u1", null), albumPhoto("a3", ALBUM_DAY_1), albumPhoto("a4", ALBUM_DAY_1),
        )
        setScreen(galleryPhotos = photos)
        touch(VIEW_ALBUM, Offset(0.5f, 0.5f))

        val day1 = node(albumDayTag(ALBUM_DAY_1)).assert(hasText("Sat, Sep 26, 2026")).getUnclippedBoundsInRoot()
        val a = listOf("a1", "a2", "a3", "a4").map { node(albumPhotoTag(it)).getUnclippedBoundsInRoot() }

        // Row one: a1, a2, a3 side by side, in input order, below the day header, 3 dp apart.
        assertTrue("a1 sits below its day header", a[0].top >= day1.bottom)
        for (i in 0..1) {
            assertEquals("a${i + 2} shares a1's row", a[0].top.value, a[i + 1].top.value, 0.5f)
            assertEquals("3 dp between a${i + 1} and a${i + 2}", 3f, (a[i + 1].left - a[i].right).value, 0.5f)
        }
        // Three columns: the fourth photo wraps under the first, 3 dp below.
        assertEquals("a4 wraps to a1's column", a[0].left.value, a[3].left.value, 0.5f)
        assertEquals("3 dp between rows", 3f, (a[3].top - a[0].bottom).value, 0.5f)
        assertEquals("square tiles", (a[0].right - a[0].left).value, (a[0].bottom - a[0].top).value, 0.5f)

        // The older day, then the unknown-date group, each under its own header, in that order.
        val day2 = node(albumDayTag(ALBUM_DAY_2)).assert(hasText("Sun, Sep 20, 2026")).getUnclippedBoundsInRoot()
        assertTrue("the older day comes after the newer day's photos", day2.top >= a[3].bottom)
        node(albumPhotoTag("b1")).performScrollTo()
        val b1 = node(albumPhotoTag("b1")).getUnclippedBoundsInRoot()
        val day2AfterScroll = node(albumDayTag(ALBUM_DAY_2)).getUnclippedBoundsInRoot()
        assertTrue("b1 sits under its own header", b1.top >= day2AfterScroll.bottom)
        node(ALBUM_DAY_UNKNOWN).performScrollTo().assert(hasText("Date unknown"))
        val unknown = node(ALBUM_DAY_UNKNOWN).getUnclippedBoundsInRoot()
        assertTrue("unknown dates come last", unknown.top >= node(albumPhotoTag("b1")).getUnclippedBoundsInRoot().bottom)
        node(albumPhotoTag("u1")).assertExists()
    }

    // ── T3, added by the second coder: the album's two badges (owner, "Two badges") ──

    /**
     * One photo per case. Entry attachment arrives as the Cartography reference counts
     * (`cartographyEntryReferenceCounts`), find attachment as [GalleryPhoto.referencingEntryIds];
     * the counts carry a zero for every unattached photo, as `MushroomLogViewModel.loadGalleryPhotos`
     * builds them.
     */
    private val badgePhotos = listOf(
        albumPhoto("plain", ALBUM_DAY_1),
        albumPhoto("in-entry", ALBUM_DAY_1),
        albumPhoto("in-find", ALBUM_DAY_1, findIds = listOf("find-1")),
        albumPhoto("in-both", ALBUM_DAY_1, findIds = listOf("find-1", "find-2")),
    )
    private val badgeEntryCounts = mapOf("plain" to 0, "in-entry" to 1, "in-find" to 0, "in-both" to 2)

    @Test
    fun `the album badges each photo by what it is attached to - a journal entry, a find, both, or neither`() {
        setScreen(galleryPhotos = badgePhotos, galleryPhotoEntryReferenceCounts = badgeEntryCounts)
        touch(VIEW_ALBUM, Offset(0.5f, 0.5f))

        node(entryBadgeTag("in-entry")).assertExists()
        node(findBadgeTag("in-entry")).assertDoesNotExist()
        node(findBadgeTag("in-find")).assertExists()
        node(entryBadgeTag("in-find")).assertDoesNotExist()
        node(entryBadgeTag("in-both")).assertExists()
        node(findBadgeTag("in-both")).assertExists()
        node(entryBadgeTag("plain")).assertDoesNotExist()
        node(findBadgeTag("plain")).assertDoesNotExist()

        // Each badge sits on its own photo's tile, and the two on one tile do not overlap.
        for ((id, badge) in listOf("in-entry" to entryBadgeTag("in-entry"), "in-find" to findBadgeTag("in-find"),
            "in-both" to entryBadgeTag("in-both"), "in-both" to findBadgeTag("in-both"))) {
            val tile = bounds(albumPhotoTag(id))
            val b = bounds(badge)
            assertTrue("$badge lies inside tile $id: badge $b, tile $tile",
                b.left >= tile.left && b.right <= tile.right && b.top >= tile.top && b.bottom <= tile.bottom)
        }
        assertTrue("the two badges on one tile are distinct marks side by side",
            !overlaps(bounds(entryBadgeTag("in-both")), bounds(findBadgeTag("in-both"))))
    }

    @Test
    fun `each album badge carries a content description a screen reader reads`() {
        setScreen(galleryPhotos = badgePhotos, galleryPhotoEntryReferenceCounts = badgeEntryCounts)
        touch(VIEW_ALBUM, Offset(0.5f, 0.5f))

        node(entryBadgeTag("in-entry")).assert(hasContentDescription(ENTRY_BADGE_DESCRIPTION))
        node(findBadgeTag("in-find")).assert(hasContentDescription(FIND_BADGE_DESCRIPTION))
        node(entryBadgeTag("in-both")).assert(hasContentDescription(ENTRY_BADGE_DESCRIPTION))
        node(findBadgeTag("in-both")).assert(hasContentDescription(FIND_BADGE_DESCRIPTION))
        composeRule.onAllNodesWithContentDescription(ENTRY_BADGE_DESCRIPTION).assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription(FIND_BADGE_DESCRIPTION).assertCountEquals(2)
    }

    // ── T4: the floating button, timeline half (the album half is further down) ──

    private val manyEntries = CartographyUiState(entries = FAB_ENTRIES)

    private fun bounds(tag: String): DpRect = node(tag).getUnclippedBoundsInRoot()

    private fun cardBounds(entry: CartographyEntry): DpRect = composeRule.onNode(entryMatcher(entry)).getUnclippedBoundsInRoot()

    private fun overlaps(a: DpRect, b: DpRect): Boolean = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** Scrolls the timeline grid to its very end: to the last item, then two more swipes. */
    private fun scrollTimelineToEnd() {
        val grid = composeRule.onNode(hasScrollToIndexAction())
        grid.performScrollToIndex(FAB_ENTRIES.lastIndex)
        repeat(2) { grid.performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
    }

    @Test
    fun `the timeline has a New entry floating button in place of the plus tile`() {
        setScreen()

        node(FAB).assertIsDisplayed().assert(hasText("New entry"))
        composeRule.onNodeWithContentDescription("New Cartography entry").assertDoesNotExist()
    }

    @Test
    fun `touching the floating button at several points starts a new entry each time, never the card beneath it`() {
        setScreen(manyEntries)
        val fab = bounds(FAB)
        val beneath = FAB_ENTRIES.filter { entry ->
            composeRule.onAllNodes(entryMatcher(entry)).fetchSemanticsNodes().isNotEmpty() && overlaps(cardBounds(entry), fab)
        }
        assertTrue("the setup puts a card under the floating button (fab $fab)", beneath.isNotEmpty())

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            touch(FAB, point)

            assertEquals(i + 1, startedCartographyEntries)
            assertEquals("no card under the button was opened", emptyList<String>(), openedCartographyIds)
            composeRule.onNodeWithText(EDITOR_FIELD).assertIsDisplayed()

            pressBack()
            node(ENTRIES_HOME).assertIsDisplayed()
        }
    }

    @Test
    fun `scrolled to the end, the last cards sit clear of the floating button and take touches at several points`() {
        setScreen(manyEntries)
        val last = FAB_ENTRIES.last()
        val besideLast = FAB_ENTRIES[FAB_ENTRIES.lastIndex - 1]

        for (entry in listOf(last, besideLast)) {
            for (point in TOUCH_SAMPLES) {
                scrollTimelineToEnd()
                val fab = bounds(FAB)
                val card = cardBounds(entry)
                assertTrue("${entry.id} ends above the floating button at the end of the list: card $card, fab $fab", card.bottom <= fab.top)

                composeRule.onNode(entryMatcher(entry)).performTouchInput { click(Offset(width * point.x, height * point.y)) }
                composeRule.waitForIdle()

                assertEquals(entry.id, openedCartographyIds.last())
                composeRule.onNodeWithText(EDITOR_FIELD).assertDoesNotExist()
                pressBack()
            }
        }
        assertEquals(0, startedCartographyEntries)
        assertEquals(6, openedCartographyIds.size)
    }

    // ── T4, album half, added by the second coder (owner, "Menu of both (Recommended)") ──

    /** Thirty photos on one day: ten rows of three, more than the album shows, so a tile sits under the button. */
    private val manyPhotos: List<GalleryPhoto> = (1..30).map { albumPhoto("m$it", ALBUM_DAY_1) }

    private fun openAlbum(photos: List<GalleryPhoto> = manyPhotos) {
        setScreen(galleryPhotos = photos)
        touch(VIEW_ALBUM, Offset(0.5f, 0.5f))
        node(ALBUM).assertIsDisplayed()
    }

    @Test
    fun `the album has an Add photo floating button, and the album's own Camera and Import row is gone`() {
        openAlbum(listOf(albumPhoto("p1", ALBUM_DAY_1)))

        node(FAB).assertIsDisplayed().assert(hasText("Add photo"))
        composeRule.onNodeWithText("New entry").assertDoesNotExist()
        composeRule.onNodeWithText("Camera").assertDoesNotExist()
        composeRule.onNodeWithText("Import").assertDoesNotExist()
        // The menu is closed until the button is touched.
        node(FAB_MENU_TAKE_PHOTO).assertDoesNotExist()
        node(FAB_MENU_IMPORT).assertDoesNotExist()
    }

    @Test
    fun `touching Add photo at several points opens the menu, never the photo beneath it, and Take photo at several points opens the album's camera`() {
        grantCamera()
        openAlbum()
        val fab = bounds(FAB)
        val beneath = manyPhotos.filter { photo ->
            composeRule.onAllNodesWithTag(albumPhotoTag(photo.photo.id)).fetchSemanticsNodes().isNotEmpty() &&
                overlaps(bounds(albumPhotoTag(photo.photo.id)), fab)
        }
        assertTrue("the setup puts a photo under the floating button (fab $fab)", beneath.isNotEmpty())

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            touch(FAB, point)

            node(FAB_MENU_TAKE_PHOTO).assertIsDisplayed().assert(hasText("Take photo"))
            node(FAB_MENU_IMPORT).assertIsDisplayed().assert(hasText("Import"))
            node(PHOTO_VIEWER).assertDoesNotExist()
            assertEquals("touching the button alone opens no camera", i, albumCameraOpens)

            touch(FAB_MENU_TAKE_PHOTO, point)

            assertEquals(i + 1, albumCameraOpens)
            assertEquals("Take photo imports nothing", 0, photoPickerLaunches())
            node(FAB_MENU_TAKE_PHOTO).assertDoesNotExist()
            node(PHOTO_VIEWER).assertDoesNotExist()
        }
    }

    @Test
    fun `Import in the Add photo menu, touched at several points, launches the system photo picker`() {
        openAlbum()

        for ((i, point) in TOUCH_SAMPLES.withIndex()) {
            touch(FAB, Offset(0.5f, 0.5f))
            node(FAB_MENU_IMPORT).assertIsDisplayed()

            touch(FAB_MENU_IMPORT, point)

            assertEquals(i + 1, photoPickerLaunches())
            assertEquals("Import opens no camera", 0, albumCameraOpens)
            node(FAB_MENU_IMPORT).assertDoesNotExist()
        }
    }

    @Test
    fun `scrolled to the end, the album's last photos sit clear of Add photo and open at a touch at several points`() {
        openAlbum()
        val lastRow = manyPhotos.takeLast(3)

        for (photo in lastRow) {
            val tag = albumPhotoTag(photo.photo.id)
            for (point in TOUCH_SAMPLES) {
                val grid = composeRule.onNode(hasScrollToIndexAction())
                grid.performScrollToNode(hasTestTag(tag))
                repeat(2) { grid.performTouchInput { swipeUp() } }
                composeRule.waitForIdle()
                val fab = bounds(FAB)
                val tile = bounds(tag)
                assertTrue("${photo.photo.id} ends above the floating button at the end of the album: tile $tile, fab $fab", tile.bottom <= fab.top)

                touch(tag, point)

                node(PHOTO_VIEWER).assertIsDisplayed()
                composeRule.onNodeWithTag(PHOTO_VIEWER_COUNTER).assert(hasText("${manyPhotos.indexOf(photo) + 1} / ${manyPhotos.size}"))
                node(FAB_MENU_TAKE_PHOTO).assertDoesNotExist()
                composeRule.onNodeWithContentDescription("Close photo").performClick()
                composeRule.waitForIdle()
                node(PHOTO_VIEWER).assertDoesNotExist()
            }
        }
    }
}

private const val SWITCH_ENTRIES = "journal-switch-entries"
private const val SWITCH_RECORDS = "journal-switch-records"
private const val RECORDS_CHIP_ROW = "records-filter-chip-row"
private const val ENTRIES_HOME = "entries-home"
private const val DRAFTS_BANNER = "entries-drafts-banner"
private const val DRAFTS_CONTINUE = "entries-drafts-continue"
private const val DRAFTS_LIST = "entries-drafts-list"
private const val DRAFTS_LIST_BACK = "entries-drafts-list-back"
private const val VIEW_TIMELINE = "entries-view-timeline"
private const val VIEW_ALBUM = "entries-view-album"
private const val ALBUM = "entries-album"
private const val FAB = "entries-fab"
private const val ALBUM_DAY_UNKNOWN = "entries-album-day-unknown"
private const val FAB_MENU_TAKE_PHOTO = "entries-fab-menu-take-photo"
private const val FAB_MENU_IMPORT = "entries-fab-menu-import"
private const val PHOTO_VIEWER = "photo-viewer"
private const val PHOTO_VIEWER_COUNTER = "photo-viewer-counter"

private fun albumDayTag(day: LocalDate): String = "entries-album-day-$day"
private fun albumPhotoTag(id: String): String = "entries-album-photo-$id"
private fun entryBadgeTag(id: String): String = "entries-album-badge-entry-$id"
private fun findBadgeTag(id: String): String = "entries-album-badge-find-$id"

private const val ENTRY_BADGE_DESCRIPTION = "Attached to a journal entry"
private const val FIND_BADGE_DESCRIPTION = "Attached to a find"

private val ALBUM_DAY_1: LocalDate = LocalDate.of(2026, 9, 26)
private val ALBUM_DAY_2: LocalDate = LocalDate.of(2026, 9, 20)

/** A gallery photo taken at local noon on [day] (or with no known time), attached to nothing. */
private fun albumPhoto(id: String, day: LocalDate?, findIds: List<String> = emptyList()): GalleryPhoto = GalleryPhoto(
    photo = LogPhoto(
        id = id,
        relativePath = "photos/$id.jpg",
        createdAtEpochMillis = day?.atTime(12, 0)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
    ),
    referencingEntryIds = findIds,
)

/**
 * An entry's card or collapsed row (journal redesign J3, C1): the ISO date these tests used to find a
 * card by is no longer on it, so they find it by the card's own tag, whichever of the two shapes it has.
 */
private fun entryMatcher(entry: CartographyEntry): SemanticsMatcher =
    hasTestTag("entry-card-${entry.id}") or hasTestTag("entry-row-${entry.id}")

/** The Cartography editor's text field label: present only when an entry is open for editing. */
private const val EDITOR_FIELD = "Your own account (optional)"

/** Three touches spread across a control: near its start edge, its centre, near its end edge, at differing heights. */
private val TOUCH_SAMPLES = listOf(Offset(0.12f, 0.3f), Offset(0.5f, 0.5f), Offset(0.88f, 0.7f))

private val ENTRIES_COMMITTED = CartographyEntry.draft(id = "committed-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
    .copy(isDraft = false)

private val ENTRIES_DRAFT_A = CartographyEntry.draft(id = "draft-a", date = LocalDate.of(2026, 8, 2), updatedAtEpochMillis = 2_000L)
private val ENTRIES_DRAFT_B = CartographyEntry.draft(id = "draft-b", date = LocalDate.of(2026, 8, 3), updatedAtEpochMillis = 3_000L)
private val ENTRIES_DRAFT_C = CartographyEntry.draft(id = "draft-c", date = LocalDate.of(2026, 8, 4), updatedAtEpochMillis = 4_000L)

/**
 * Thirty committed entries: more than a 360 x 640 dp screen shows, so one sits under the floating
 * button. Twelve until J3 (C1): an entry with no text and nothing kept now collapses to a short row,
 * and twelve short rows no longer filled the screen, so the "a card lies under the button" setup check
 * failed. The count changed; every assertion stands.
 */
private val FAB_ENTRIES: List<CartographyEntry> = (1..30).map { day ->
    CartographyEntry.draft(id = "fab-$day", date = LocalDate.of(2026, 7, day), updatedAtEpochMillis = day.toLong()).copy(isDraft = false)
}

private val ENTRIES_EMPTY_MAP_DATA = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

private val ENTRIES_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
