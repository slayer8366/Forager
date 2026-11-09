package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityScreen
import com.zynergylabs.forager.app.ui.availability.AvailabilityScreenOfflineMaps
import com.zynergylabs.forager.app.ui.availability.AvailabilityScreenRecords
import com.zynergylabs.forager.app.ui.availability.AvailabilityScreenSearchInputs
import com.zynergylabs.forager.app.ui.availability.AvailabilityScreenSettings
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import org.robolectric.shadows.ShadowDialog

/**
 * Dispatch 2026-09-28-677 (data part B; the owner, RECORD -656: "Tiles + profile, raw tucked away (Recommended)"): the track
 * sheet's tiles, height profile and "Details" fold, and its times on a 24-hour phone, through the real [AvailabilityScreen]:
 * Journal, Records, the Tracks chip, a real touch on the track's row. The fold is opened and closed by real touches across
 * its header (CLAUDE.md: a semantic click asserts wiring, not routing).
 *
 * The walk: twelve points 0.001° of latitude apart (11 x 111.2 m = 1223 m, 0.76 mi), a minute apart (11 min), every one
 * with a height: 100 m rising by 10 m to 150 m, then down by 10, 10, 10, 10, 5 and 5 m to 100 m. Every step is past the 4 m
 * hysteresis, so the climb and the descent are 50 m each, 164 ft. Every interval moves (1.85 m/s, past the 0.5 m/s floor),
 * 11 min of moving time is past the 5 min bar, and 1223 m in 660 s is 1.853 m/s, 4.1 mph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class TrackSheetDataTest {

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

    private fun setScreen() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(),
                searchInputs = AvailabilityScreenSearchInputs(
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
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = STUB_MAP,
                records = AvailabilityScreenRecords(
                    tracks = listOf(HILL, FLAT),
                ),
                currentTime = CurrentTimeProvider { NOW },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.RECORDED_TRACKS)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun exists(tag: String): Boolean = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun field(key: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(recordDetailsFieldTag(key))
    private fun tile(label: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(trackTileTag(label))

    /** One real touch at [fraction] of [tag]'s own bounds, after scrolling it into view. */
    private fun touch(tag: String, fraction: Offset) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * fraction.x, height * fraction.y)) }
        composeRule.waitForIdle()
    }

    private fun openSheet(track: Track) {
        touch("track-row-${track.id}", Offset(0.4f, 0.5f))
        assertTrue("the track's sheet opens", exists(RECORD_DETAILS_SHEET_TAG))
    }

    /** A real Back key on the sheet's own window. */
    private fun backOnSheet() {
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue("a sheet window must be showing to press Back on", dialog != null && dialog.isShowing)
        composeRule.runOnUiThread {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeRule.waitForIdle()
    }

    private fun foldOpen(): Boolean = exists(recordDetailsFieldTag(FIELD_POINTS))

    @Test
    fun `a walk's sheet shows Distance, Time, Climb, Descent and Moving speed as labelled tiles`() {
        setScreen()
        openSheet(HILL)

        tile("Distance").assertTextEquals("Distance", "0.8 mi")
        tile("Time").assertTextEquals("Time", "11 min")
        tile("Climb").assertTextEquals("Climb", "164 ft")
        tile("Descent").assertTextEquals("Descent", "164 ft")
        tile("Moving speed").assertTextEquals("Moving speed", "4.1 mph")
        assertFalse("the old Distance and Duration fields are gone", exists(recordDetailsFieldTag(FIELD_DISTANCE)) || exists(recordDetailsFieldTag(FIELD_DURATION)))
    }

    @Test
    fun `a walk with heights draws its height profile, labelled with its highest and lowest and its length`() {
        setScreen()
        openSheet(HILL)

        assertTrue("the profile is drawn", exists(ENTRY_HEIGHT_PROFILE_TAG))
        assertFalse("no line saying why not", exists(ENTRY_HEIGHT_PROFILE_LINE_TAG))
        // 150 m is 492 ft, 100 m is 328 ft. The Canvas's own look is phone-only (Robolectric does not render it).
        val described = composeRule.onAllNodesWithContentDescription("Height profile: lowest 328 ft, highest 492 ft, over 0.8 mi").fetchSemanticsNodes()
        assertEquals("the profile's summary for a screen reader", 1, described.size)
    }

    @Test
    fun `a walk with no heights says so where the profile would be, and Climb and Descent say Not recorded`() {
        setScreen()
        openSheet(FLAT)

        composeRule.onNodeWithTag(ENTRY_HEIGHT_PROFILE_LINE_TAG).assertTextEquals(PROFILE_TOO_FEW_HEIGHTS_LINE)
        tile("Climb").assertTextEquals("Climb", "Not recorded")
        tile("Descent").assertTextEquals("Descent", "Not recorded")
    }

    /**
     * The fold starts closed; touches at three points across its header each toggle it; what it holds is Points and how many
     * of them carry a height.
     */
    @Test
    fun `the Details fold opens and closes from touches across its header, and holds Points and the heights`() {
        setScreen()
        openSheet(HILL)
        assertFalse("closed at first: Points is tucked away", foldOpen())

        touch(RECORD_DETAILS_FOLD_TAG, Offset(0.1f, 0.5f))
        assertTrue("a touch at its start opens it", foldOpen())
        field(FIELD_POINTS).assertTextEquals("Points", "12")
        field(FIELD_HEIGHTS).assertTextEquals("With height", "12 of 12 points")

        touch(RECORD_DETAILS_FOLD_TAG, Offset(0.5f, 0.2f))
        assertFalse("a touch at its upper middle closes it", foldOpen())

        touch(RECORD_DETAILS_FOLD_TAG, Offset(0.95f, 0.8f))
        assertTrue("a touch by its arrow opens it again", foldOpen())
    }

    /** Open, it stays open: across closing the sheet and opening another walk's, within the session (CLAUDE.md, UX defaults). */
    @Test
    fun `an open Details fold stays open on the next walk's sheet`() {
        setScreen()
        openSheet(HILL)
        touch(RECORD_DETAILS_FOLD_TAG, Offset(0.5f, 0.5f))
        assertTrue(foldOpen())

        backOnSheet()
        assertFalse("Back closes the sheet", exists(RECORD_DETAILS_SHEET_TAG))
        openSheet(FLAT)
        assertTrue("the next walk's sheet opens with the fold open", foldOpen())
        field(FIELD_POINTS).assertTextEquals("Points", "2")
    }

    /** The owner, RECORD -656: times follow the phone's 12- or 24-hour setting. The title of an unnamed walk is its start. */
    @Test
    fun `on a 24-hour phone the sheet's times are in 24-hour time`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        setScreen()
        val expected = DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm").format(Instant.ofEpochMilli(FLAT.startedAtEpochMillis).atZone(ZoneId.systemDefault()))
        // Data part D (RECORD -702): the Records row's title follows the same setting, so the row and the sheet agree on a
        // 24-hour phone (data part B's report, item 8, recorded the mismatch this closes).
        composeRule.onNode(hasText(expected) and hasAnyAncestor(hasTestTag("track-row-${FLAT.id}")), useUnmergedTree = true).assertExists()
        openSheet(FLAT)

        composeRule.onNodeWithTag(RECORD_DETAILS_TITLE_TAG).assertTextEquals(expected)
        field(FIELD_STARTED).assertTextEquals("Started", expected)
    }

    @Test
    fun `on a 12-hour phone the sheet's times are as the Records rows write them`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        setScreen()
        openSheet(FLAT)

        val expected = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").format(Instant.ofEpochMilli(FLAT.startedAtEpochMillis).atZone(ZoneId.systemDefault()))
        field(FIELD_STARTED).assertTextEquals("Started", expected)
    }
}

private const val NOW = 1_790_000_000_000L
private const val MINUTE = 60_000L

private val HILL_ALTITUDES = listOf(100.0, 110.0, 120.0, 130.0, 140.0, 150.0, 140.0, 130.0, 120.0, 110.0, 105.0, 100.0)

private val HILL = Track(
    id = "hill",
    name = "Hill loop",
    startedAtEpochMillis = NOW - 2 * 86_400_000L,
    endedAtEpochMillis = NOW - 2 * 86_400_000L + 11 * MINUTE,
    points = HILL_ALTITUDES.mapIndexed { i, altitude ->
        TrackPoint(lat = 45.0 + i * 0.001, lng = -122.0, altitude = altitude, accuracyMeters = 5f, timestampEpochMillis = NOW - 2 * 86_400_000L + i * MINUTE)
    },
)

private val FLAT = Track(
    id = "flat",
    name = null,
    startedAtEpochMillis = NOW - 3 * 86_400_000L + 14 * 60 * MINUTE,
    endedAtEpochMillis = NOW - 3 * 86_400_000L + 15 * 60 * MINUTE,
    points = listOf(
        TrackPoint(lat = 45.1, lng = -122.1, altitude = null, accuracyMeters = 5f, timestampEpochMillis = NOW - 3 * 86_400_000L + 14 * 60 * MINUTE),
        TrackPoint(lat = 45.11, lng = -122.1, altitude = null, accuracyMeters = 5f, timestampEpochMillis = NOW - 3 * 86_400_000L + 15 * 60 * MINUTE),
    ),
)

private val STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
