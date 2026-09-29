package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.ui.availability.AvailabilityScreen
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Journal redesign J5c (`prompts/preserved/2026-09-27-28.md`; the owner: "have them display info upon
 * tapping", form "Bottom sheet of details (Recommended)"): a tap on a waypoint, recorded-track or
 * offline-region row opens a Material 3 modal bottom sheet with the record's full information and
 * its actions.
 *
 * Driven through the real [AvailabilityScreen] in the three window shapes the Records rows appear
 * in: compact portrait (`w411dp-h891dp`, `JournalTab`), a short landscape window
 * (`w823dp-h384dp-land`, `JournalTab` beside the rail) and the wide tree (`w840dp-h1024dp-mdpi`,
 * `LogPanel` in the drawer), since all three share `RecordsTab`.
 *
 * - **Row taps are real touches** (`performTouchInput`) at several points across each row's own
 *   bounds (CLAUDE.md, "A semantic `performClick` asserts wiring, not routing"), each sample a fresh
 *   attempt: tap, see the sheet, dismiss it. Getting to a screen (the bottom bar, the Records switch,
 *   a chip) may be a semantic click.
 * - **The sheet's text is asserted per field**, label and value with its unit, on each field's merged
 *   node, against fixtures whose expected strings are literal (or, for the timestamps and the MGRS
 *   grid, computed with the same pattern or converter the row itself uses, since those depend on
 *   the test machine's zone).
 * - **Back** is a real Back key sent to the sheet's own window (the sheet is a dialog with its own
 *   back dispatcher, which is what a Back press reaches while it shows). **The scrim** is touched
 *   at a point the sheet does not cover.
 * - **Navigate is not built** (the dispatch's stop rule; see `RecordDetailsSheet`'s doc comment):
 *   the waypoint test pins its absence beside Directions' presence.
 *
 * Tags are literals so these compile against the base they were written before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = PORTRAIT)
class RecordDetailsSheetTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    // FileProviderCacheReset: two tests here share a track through FileProvider (the row's Share and
    // the sheet's); without it the second fails on the first one's cached, stale data directory.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val deletedWaypointIds = mutableListOf<String>()

    private fun setScreen(waypointCounts: Map<String, Int> = DETAILS_COUNTS) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(offlineRegions = listOf(DETAILS_REGION)),
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
                mapSlot = DETAILS_STUB_MAP,
                waypoints = DETAILS_WAYPOINTS,
                waypointEntryReferenceCounts = waypointCounts,
                onDeleteWaypoint = { id -> deletedWaypointIds += id },
                tracks = DETAILS_TRACKS,
                currentTime = CurrentTimeProvider { NOW },
            )
        }
        composeRule.waitForIdle()
    }

    /** Opens Records in whichever tree the window gives: the Journal tab (compact) or the Mushroom Log drawer panel (wide). */
    private fun openRecords(wide: Boolean = false) {
        composeRule.onNodeWithText(if (wide) "Mushroom Log" else "Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
    }

    private fun selectChip(filter: RecordsSubTab) {
        composeRule.onNodeWithTag(recordsFilterChipTestTag(filter)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(filter)).assertIsSelected()
    }

    private fun sheet(): SemanticsNodeInteraction = composeRule.onNodeWithTag(SHEET)
    private fun sheetShowing(): Boolean = composeRule.onAllNodesWithTag(SHEET).fetchSemanticsNodes().isNotEmpty()
    private fun field(key: String): SemanticsNodeInteraction = composeRule.onNodeWithTag("record-details-field-$key")
    private fun exists(tag: String): Boolean = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** One real touch at [fraction] of [tag]'s own bounds, after scrolling it into view. */
    private fun touch(tag: String, fraction: Offset) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * fraction.x, height * fraction.y)) }
        composeRule.waitForIdle()
    }

    /** A real Back key, down and up, on the sheet's own window (the latest dialog shown). */
    private fun pressBackOnSheet() {
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue("a sheet window must be showing to press Back on", dialog != null && dialog.isShowing)
        composeRule.runOnUiThread {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeRule.waitForIdle()
    }

    /**
     * Taps [rowTag] at each sample point in turn; each tap must open the sheet with [expectedTitle],
     * and [whileOpen] runs while it shows; Back then closes it, so the next sample has to open it again.
     */
    private fun tapAcrossRowOpensSheet(rowTag: String, expectedTitle: String, whileOpen: () -> Unit = {}) {
        for (point in ROW_SAMPLES) {
            assertTrue("no sheet before the tap at $point", !sheetShowing())
            touch(rowTag, point)
            assertTrue("a tap at $point of $rowTag opens the details sheet", sheetShowing())
            sheet().assertIsDisplayed()
            composeRule.onNodeWithTag(TITLE).assertTextEquals(expectedTitle)
            whileOpen()
            pressBackOnSheet()
            assertTrue("Back closes the sheet (after the tap at $point)", !sheetShowing())
        }
    }

    private fun assertWaypointCreekContent() {
        field("mgrs").assertTextEquals("MGRS", CREEK_MGRS)
        field("coordinates").assertTextEquals("Coordinates", "45.3260, -122.6340")
        field("created").assertTextEquals("Created", stamp(CREEK.createdAtEpochMillis))
        field("track").assertTextEquals("Track", stamp(TRACK_T1.startedAtEpochMillis))
        field("used-in").assertTextEquals("Used in", "2 journal entries")
        // The sheet's content scrolls in a short window, so an action may start below its fold.
        composeRule.onNodeWithTag(DIRECTIONS).performScrollTo().assertIsDisplayed()
    }

    private fun assertTrackT1Content() {
        field("started").assertTextEquals("Started", stamp(TRACK_T1.startedAtEpochMillis))
        field("ended").assertTextEquals("Ended", stamp(TRACK_T1.endedAtEpochMillis!!))
        field("distance").assertTextEquals("Distance", "1.4 mi")
        field("duration").assertTextEquals("Duration", "1h 10m")
        field("points").assertTextEquals("Points", "3")
        composeRule.onNodeWithTag(NOTE).assertTextEquals("12 more not shown (network fixes)")
        composeRule.onNodeWithTag(SHARE).performScrollTo().assertIsDisplayed()
    }

    private fun assertRegionContent() {
        composeRule.onNodeWithTag("record-details-stale").assertTextEquals("Stale")
        field("radius").assertTextEquals("Radius", "9 mi")
        field("centre").assertTextEquals("Centre", "45.5000, -122.5000")
        field("tiles").assertTextEquals("Tiles", "1234")
        field("size").assertTextEquals("Size", "12.3 MB")
        field("downloaded").assertTextEquals("Downloaded", "${stamp(DETAILS_REGION.createdAtEpochMillis)} (70 days ago)")
        composeRule.onNodeWithTag(ZOOM).assertTextEquals(REGION_ZOOM_NOTE)
    }

    // ── Portrait: each row type, in All and in its own chip ──

    @Test
    fun `a waypoint row in All opens its details sheet from a tap anywhere on it, badge included`() {
        setScreen()
        openRecords()
        tapAcrossRowOpensSheet(waypointRow("W1"), "Creek pin") { assertWaypointCreekContent() }
    }

    @Test
    fun `a waypoint row in the Waypoints chip opens its details sheet from a tap anywhere on it`() {
        setScreen()
        openRecords()
        selectChip(RecordsSubTab.WAYPOINTS)
        tapAcrossRowOpensSheet(waypointRow("W1"), "Creek pin") { assertWaypointCreekContent() }
    }

    @Test
    fun `a recorded track row in All opens its details sheet with its start, end, distance, duration, points and note`() {
        setScreen()
        openRecords()
        tapAcrossRowOpensSheet(logbookRowTag(RecordType.TRACKS, "T1"), stamp(TRACK_T1.startedAtEpochMillis)) { assertTrackT1Content() }
    }

    @Test
    fun `a recorded track row in the Tracks chip opens its details sheet from a tap anywhere on it`() {
        setScreen()
        openRecords()
        selectChip(RecordsSubTab.RECORDED_TRACKS)
        tapAcrossRowOpensSheet("track-row-T1", stamp(TRACK_T1.startedAtEpochMillis)) { assertTrackT1Content() }
    }

    @Test
    fun `an offline region row in All opens its details sheet with radius, centre, tiles, size, date and zoom`() {
        setScreen()
        openRecords()
        tapAcrossRowOpensSheet(regionRow(), "Molalla Ridge") { assertRegionContent() }
    }

    @Test
    fun `an offline region row in the Offline maps chip opens its details sheet from a tap anywhere on it`() {
        setScreen()
        openRecords()
        selectChip(RecordsSubTab.OFFLINE_MAPS)
        tapAcrossRowOpensSheet(regionRow(), "Molalla Ridge") { assertRegionContent() }
    }

    @Test
    fun `a named recording track's sheet takes its name as the title and says it is still recording`() {
        setScreen()
        openRecords()
        touch(logbookRowTag(RecordType.TRACKS, "T2"), Offset(0.4f, 0.5f))
        composeRule.onNodeWithTag(TITLE).assertTextEquals("Ridge loop")
        field("started").assertTextEquals("Started", stamp(TRACK_T2.startedAtEpochMillis))
        field("ended").assertTextEquals("Ended", "Still recording")
        field("points").assertTextEquals("Points", "2")
        assertTrue("no network note on an ordinary track", !exists(NOTE))
    }

    @Test
    fun `a waypoint with no track and no count shows neither line, one with a missing track says Not loaded`() {
        setScreen()
        openRecords()
        touch(waypointRow("W2"), Offset(0.4f, 0.5f))
        composeRule.onNodeWithTag(TITLE).assertTextEquals("Oak pin")
        field("coordinates").assertTextEquals("Coordinates", "45.4000, -122.7000")
        assertTrue("a waypoint dropped with no recording has no Track line", !exists("record-details-field-track"))
        assertTrue("a waypoint with no count given has no Used in line, rather than a made-up zero", !exists("record-details-field-used-in"))
        pressBackOnSheet()

        touch(waypointRow("W3"), Offset(0.4f, 0.5f))
        composeRule.onNodeWithTag(TITLE).assertTextEquals("Ridge pin")
        field("track").assertTextEquals("Track", "Not loaded")
        field("used-in").assertTextEquals("Used in", "no journal entries")
    }

    // ── The row's own buttons keep doing what they do ──

    @Test
    fun `the waypoint row's own Directions button starts directions and does not open the sheet`() {
        setScreen()
        registerFakeMapsApp()
        openRecords()
        selectChip(RecordsSubTab.WAYPOINTS)
        composeRule.onNodeWithContentDescription("Directions to Creek pin").performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()

        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        assertEquals("geo:0,0?q=45.326,-122.634(Creek%20pin)", started?.data.toString())
        assertTrue("a tap on the row's own Directions button does not open the sheet", !sheetShowing())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `the track row's own Share button starts the GPX share and does not open the sheet`() {
        setScreen()
        openRecords()
        selectChip(RecordsSubTab.RECORDED_TRACKS)
        composeRule.onNodeWithTag("share-track-T1").performScrollTo().performTouchInput { click(center) }
        val started = awaitStartedActivity()
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        assertEquals("application/gpx+xml", started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.type)
        assertTrue("a tap on the row's own Share button does not open the sheet", !sheetShowing())
    }

    // ── J4b's rule: a tap on an open swipe row closes it and opens nothing ──

    @Test
    fun `a tap on a waypoint row whose swipe is open closes the row and does not open the sheet`() {
        setScreen()
        openRecords()
        selectChip(RecordsSubTab.WAYPOINTS)
        val row = waypointRow("W1")
        for (point in ROW_SAMPLES) {
            shortSwipeLeft(row)
            assertTrue("the short swipe left the row open", exists("$row-delete"))
            touch(row, point.copy(x = point.x * 0.5f))
            assertTrue("the tap at $point closed the open row", !exists("$row-delete"))
            assertTrue("the tap at $point on an open row does not open the sheet", !sheetShowing())
        }
        // Closed again, the same row opens the sheet on a tap, and nothing was deleted.
        touch(row, Offset(0.3f, 0.5f))
        composeRule.onNodeWithTag(TITLE).assertTextEquals("Creek pin")
        assertEquals(emptyList<String>(), deletedWaypointIds)
    }

    @Test
    fun `a tap on a region row whose swipe is open in All closes the row and does not open the sheet`() {
        setScreen()
        openRecords()
        val row = regionRow()
        shortSwipeLeft(row)
        assertTrue("the short swipe left the row open", exists("$row-delete"))
        touch(row, Offset(0.2f, 0.5f))
        assertTrue("the tap closed the open row", !exists("$row-delete"))
        assertTrue("a tap on an open row does not open the sheet", !sheetShowing())
        touch(row, Offset(0.3f, 0.5f))
        composeRule.onNodeWithTag(TITLE).assertTextEquals("Molalla Ridge")
    }

    // ── Dismissal ──

    @Test
    fun `Back closes the sheet and nothing else, so the Waypoints chip stays selected`() {
        setScreen()
        openRecords()
        selectChip(RecordsSubTab.WAYPOINTS)
        touch(waypointRow("W1"), Offset(0.3f, 0.5f))
        sheet().assertIsDisplayed()
        pressBackOnSheet()
        assertTrue("Back closed the sheet", !sheetShowing())
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).assertIsSelected()
    }

    @Test
    fun `a touch on the scrim closes the sheet`() {
        setScreen()
        openRecords()
        touch(waypointRow("W1"), Offset(0.3f, 0.5f))
        sheet().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close sheet").performTouchInput { click(Offset(width * 0.03f, height * 0.03f)) }
        composeRule.waitForIdle()
        assertTrue("a scrim touch closed the sheet", !sheetShowing())
    }

    // ── Actions ──

    @Test
    fun `the waypoint sheet's Directions starts directions to that waypoint, and there is no Navigate`() {
        setScreen()
        registerFakeMapsApp()
        openRecords()
        touch(waypointRow("W1"), Offset(0.3f, 0.5f))
        composeRule.onNodeWithTag(DIRECTIONS).performScrollTo().assertIsDisplayed()
        // The dispatch's stop rule: Navigate needs an in-app navigation entry point the app does not
        // have, so the sheet ships with Directions only (RecordDetailsSheet's doc comment).
        assertTrue("no Navigate action is offered", composeRule.onAllNodesWithText("Navigate", substring = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag(DIRECTIONS).performTouchInput { click(Offset(width * 0.3f, height * 0.5f)) }
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("geo:0,0?q=45.326,-122.634(Creek%20pin)", started?.data.toString())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `the track sheet's Share starts the same GPX share the row's button does`() {
        setScreen()
        openRecords()
        touch(logbookRowTag(RecordType.TRACKS, "T1"), Offset(0.4f, 0.5f))
        composeRule.onNodeWithTag(SHARE).performScrollTo().performTouchInput { click(center) }
        val started = awaitStartedActivity()
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val inner = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, inner?.action)
        assertEquals("application/gpx+xml", inner?.type)
    }

    // ── TalkBack ──

    @Test
    fun `each row type carries a Details for click label, in All and in its own chip`() {
        setScreen()
        openRecords()
        for (label in listOf("Details for Creek pin", "Details for ${stamp(TRACK_T1.startedAtEpochMillis)}", "Details for Molalla Ridge")) {
            composeRule.onNode(hasClickLabel(label)).assertExists()
        }
        selectChip(RecordsSubTab.WAYPOINTS)
        composeRule.onNode(hasClickLabel("Details for Creek pin")).assertExists()
        selectChip(RecordsSubTab.RECORDED_TRACKS)
        composeRule.onNode(hasClickLabel("Details for ${stamp(TRACK_T1.startedAtEpochMillis)}")).assertExists()
        selectChip(RecordsSubTab.OFFLINE_MAPS)
        composeRule.onNode(hasClickLabel("Details for Molalla Ridge")).assertExists()
    }

    // ── Short landscape window ──

    @Config(qualifiers = SHORT_LANDSCAPE)
    @Test
    fun `short window - each row type in All opens its details sheet from taps across it`() {
        setScreen()
        openRecords()
        tapAcrossRowOpensSheet(waypointRow("W1"), "Creek pin") { assertWaypointCreekContent() }
        tapAcrossRowOpensSheet(logbookRowTag(RecordType.TRACKS, "T1"), stamp(TRACK_T1.startedAtEpochMillis)) { assertTrackT1Content() }
        tapAcrossRowOpensSheet(regionRow(), "Molalla Ridge") { assertRegionContent() }
    }

    @Config(qualifiers = SHORT_LANDSCAPE)
    @Test
    fun `short window - each row type in its own chip opens its details sheet`() {
        setScreen()
        openRecords()
        selectChip(RecordsSubTab.WAYPOINTS)
        tapAcrossRowOpensSheet(waypointRow("W1"), "Creek pin") { assertWaypointCreekContent() }
        selectChip(RecordsSubTab.RECORDED_TRACKS)
        tapAcrossRowOpensSheet("track-row-T1", stamp(TRACK_T1.startedAtEpochMillis)) { assertTrackT1Content() }
        selectChip(RecordsSubTab.OFFLINE_MAPS)
        tapAcrossRowOpensSheet(regionRow(), "Molalla Ridge") { assertRegionContent() }
    }

    @Config(qualifiers = SHORT_LANDSCAPE)
    @Test
    fun `short window - an open swipe row takes the tap, and the scrim closes the sheet`() {
        setScreen()
        openRecords()
        val row = waypointRow("W1")
        shortSwipeLeft(row)
        assertTrue("the short swipe left the row open", exists("$row-delete"))
        touch(row, Offset(0.2f, 0.5f))
        assertTrue("the tap closed the open row", !exists("$row-delete"))
        assertTrue("a tap on an open row does not open the sheet", !sheetShowing())

        touch(row, Offset(0.3f, 0.5f))
        sheet().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close sheet").performTouchInput { click(Offset(width * 0.03f, height * 0.03f)) }
        composeRule.waitForIdle()
        assertTrue("a scrim touch closed the sheet", !sheetShowing())
    }

    // ── The wide tree (LogPanel shares RecordsTab) ──

    /**
     * J6a (ruling 6.1, "The record details open in the right side, not as a sheet"): on the wide tree a
     * Records row opens its details in the detail pane, not `RecordDetailsSheet`, with the same content
     * (the body is shared) and closes on Back. The compact Journal's sheet tests above are unchanged.
     */
    @Config(qualifiers = WIDE)
    @Test
    fun `wide tree - a waypoint row in LogPanel's Records opens its details in the right side pane`() {
        setScreen()
        openRecords(wide = true)
        tapAcrossRowOpensPane(waypointRow("W1"), "Creek pin") { assertWaypointCreekContent() }
        tapAcrossRowOpensPane(regionRow(), "Molalla Ridge") { assertRegionContent() }
    }

    private fun paneShowing(): Boolean = composeRule.onAllNodesWithTag(RECORD_DETAILS_PANE_TAG).fetchSemanticsNodes().isNotEmpty()

    private fun tapAcrossRowOpensPane(rowTag: String, expectedTitle: String, whileOpen: () -> Unit = {}) {
        for (point in ROW_SAMPLES) {
            assertTrue("no pane before the tap at $point", !paneShowing())
            touch(rowTag, point)
            assertTrue("a tap at $point of $rowTag opens the details pane", paneShowing())
            assertTrue("not as a sheet", !sheetShowing())
            composeRule.onNodeWithTag(RECORD_DETAILS_PANE_TAG).assertIsDisplayed()
            composeRule.onNodeWithTag(TITLE).assertTextEquals(expectedTitle)
            whileOpen()
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.waitForIdle()
            assertTrue("Back closes the pane (after the tap at $point)", !paneShowing())
        }
    }

    // ── Helpers ──

    private fun shortSwipeLeft(tag: String, distanceDp: Float = 64f) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTouchInput {
            val y = centerY
            val startX = right - 8f
            swipe(start = Offset(startX, y), end = Offset(startX - distanceDp.dp.toPx(), y), durationMillis = 600)
        }
        composeRule.waitForIdle()
    }

    private fun awaitStartedActivity(): Intent {
        // The GPX file is written on Dispatchers.IO before the chooser starts; waitForIdle does not
        // cover that hop, so poll (as AvailabilityScreenSettingsPanelTest's share test does).
        var started: Intent? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            started = started ?: Shadows.shadowOf(composeRule.activity).nextStartedActivity
            started != null
        }
        return started!!
    }

    private fun registerFakeMapsApp() {
        val activity = composeRule.activity
        val componentName = ComponentName(activity, "com.example.fakemaps.MapsActivity")
        val shadowPackageManager = Shadows.shadowOf(activity.packageManager)
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("geo")
            },
        )
        assertNull("nothing started before the tap", Shadows.shadowOf(activity).nextStartedActivity)
    }

    private fun hasClickLabel(label: String): SemanticsMatcher =
        SemanticsMatcher("has click label '$label'") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
}

private const val PORTRAIT = "w411dp-h891dp"
private const val SHORT_LANDSCAPE = "w823dp-h384dp-land"
private const val WIDE = "w840dp-h1024dp-mdpi"

private const val SHEET = "record-details-sheet"
private const val TITLE = "record-details-title"
private const val NOTE = "record-details-note"
private const val ZOOM = "record-details-zoom"
private const val DIRECTIONS = "record-details-directions"
private const val SHARE = "record-details-share"

/** Three touch points across a row: its start edge (the badge, in All), upper middle, lower right short of its buttons. */
private val ROW_SAMPLES = listOf(Offset(0.05f, 0.5f), Offset(0.4f, 0.25f), Offset(0.7f, 0.8f))

private fun waypointRow(id: String) = "records-swipe-waypoints-$id"
private fun regionRow() = "records-swipe-offline-maps-${DETAILS_REGION.id}"

private const val NOW = 1_790_000_000_000L
private const val DAY = 86_400_000L
private const val MINUTE = 60_000L

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")
private fun stamp(epochMillis: Long): String = DISPLAY_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val DETAILS_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

private val TRACK_T1 = Track(
    id = "T1",
    name = null,
    startedAtEpochMillis = NOW - 3 * DAY,
    endedAtEpochMillis = NOW - 3 * DAY + 75 * MINUTE,
    // Three points 0.01 degrees of latitude apart: 2 x 1111.95 m = 2223.9 m, 1.38 mi; 70 minutes first to last.
    points = listOf(
        TrackPoint(lat = 45.00, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = NOW - 3 * DAY),
        TrackPoint(lat = 45.01, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = NOW - 3 * DAY + 30 * MINUTE),
        TrackPoint(lat = 45.02, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = NOW - 3 * DAY + 70 * MINUTE),
    ),
    // 12 of 15 stored points excluded as network fixes: past the 75% line, so the row and the sheet carry the note.
    excludedPointCount = 12,
)

private val TRACK_T2 = Track(
    id = "T2",
    name = "Ridge loop",
    startedAtEpochMillis = NOW - 5 * DAY,
    endedAtEpochMillis = null,
    points = listOf(
        TrackPoint(lat = 45.10, lng = -122.1, altitude = null, accuracyMeters = 5f, timestampEpochMillis = NOW - 5 * DAY),
        TrackPoint(lat = 45.11, lng = -122.1, altitude = null, accuracyMeters = 5f, timestampEpochMillis = NOW - 5 * DAY + 20 * MINUTE),
    ),
)

private val DETAILS_TRACKS = listOf(TRACK_T1, TRACK_T2)

private val CREEK = Waypoint(
    id = "W1", lat = 45.3260, lng = -122.6340, altitude = null, name = "Creek pin", note = "",
    createdAtEpochMillis = NOW - 2 * DAY, trackId = "T1",
)
private val OAK = Waypoint(
    id = "W2", lat = 45.4000, lng = -122.7000, altitude = null, name = "Oak pin", note = "",
    createdAtEpochMillis = NOW - 1 * DAY,
)
private val RIDGE = Waypoint(
    id = "W3", lat = 45.4500, lng = -122.6500, altitude = null, name = "Ridge pin", note = "",
    createdAtEpochMillis = NOW - 4 * DAY, trackId = "T-not-in-the-list",
)
private val DETAILS_WAYPOINTS = listOf(OAK, CREEK, RIDGE)

/** Creek pin keeps 2 entries, Ridge pin 0; Oak pin has no count at all. */
private val DETAILS_COUNTS = mapOf("W1" to 2, "W3" to 0)

private val CREEK_MGRS: String = (MgrsConverter.convert(LatLng(CREEK.lat, CREEK.lng)) as MgrsCoordinate.Grid).value

private val DETAILS_REGION = OfflineRegionSummary(
    id = 7L,
    name = "Molalla Ridge",
    region = Region(lat = 45.5, lng = -122.5, radiusKm = 15),
    minZoom = 0.0,
    maxZoom = 15.0,
    tileCount = 1234,
    sizeBytes = 12_345_678L,
    // 70 days old: past the default 60-day stale threshold.
    createdAtEpochMillis = NOW - 70 * DAY,
)

private const val REGION_ZOOM_NOTE =
    "Ready to zoom 15: zoom 0–14 from the archive, zoom 15 detail fetched live from Protomaps when this " +
        "region downloaded — a region that shows here has both, since a zoom-15 fetch failure fails the " +
        "whole download rather than silently completing without it."
