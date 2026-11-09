package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GpxImportFixtures
import com.zynergylabs.forager.app.domain.GpxImportOutcome
import com.zynergylabs.forager.app.domain.ImportGpxUseCase
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.importgpx.ContentUriGpxFileSource
import com.zynergylabs.forager.app.importgpx.GpxImportViewModel
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.track.IMPORT_GPX_BUTTON_TAG
import com.zynergylabs.forager.app.ui.track.trackExportRowTag
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * Plan T16 in Records: the Tracks chip's "Import GPX" by real touches at screen coordinates, across the
 * button's own bounds (CLAUDE.md, "A semantic performClick asserts wiring, not routing"), opening the
 * system file picker for the GPX types; a picked file going through the real [GpxImportViewModel],
 * [ContentUriGpxFileSource] and [ImportGpxUseCase] into a real Room database; and what the imported track
 * then shows: its name, the "Imported" label in its row and its details, and "No times in file".
 *
 * The picker itself is the system's; it is stood in for by an [ActivityResultRegistry] that records the
 * Intent the button asks for and answers with a file, the AndroidX-documented way to test a launcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class GpxImportRecordsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
    private val tracks = RoomTrackRepository(database.trackDao())
    private val waypoints = RoomWaypointRepository(database.waypointDao())

    @After
    fun tearDown() = database.close()

    /** Every Intent the button asked the system to open. */
    private val launched = mutableListOf<Intent>()

    /** What the stand-in picker answers each launch with: `null` is backing out of the picker. */
    private var answer: Uri? = null

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += contract.createIntent(app, input)
            val picked = answer
            if (picked != null) {
                Shadows.shadowOf(app.contentResolver).registerInputStream(picked, ByteArrayInputStream(fileBytes))
            }
            dispatchResult(requestCode, picked)
        }
    }

    private var fileBytes: ByteArray = GpxImportFixtures.SHAPE_A.toByteArray()

    private val importNow = Instant.parse("2026-10-07T12:34:56Z").toEpochMilli()

    private fun setScreen(initialTracks: List<Track> = emptyList(), onPicked: ((Uri) -> Unit)? = null) {
        var shownTracks by mutableStateOf(initialTracks)
        val viewModel = GpxImportViewModel { source -> ImportGpxUseCase(tracks, waypoints, { importNow }, ErrorLog { _, _, _ -> })(source) }
        this.viewModel = viewModel
        composeRule.setContent {
            val notice by viewModel.notice.collectAsState()
            // What MainActivity does on a notice: Records reloads its tracks, and opens the new one.
            androidx.compose.runtime.LaunchedEffect(notice?.seq) {
                if (notice?.outcome is GpxImportOutcome.Imported) shownTracks = tracks.getAll().getOrThrow()
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }) {
                RecordsTab(
                    waypoints = emptyList(),
                    waypointsErrorMessage = null,
                    onDeleteWaypoint = {},
                    availabilityUiState = AvailabilityUiState(),
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { 0L },
                    mapSlot = GIR_STUB_MAP,
                    night = false,
                    onOfflineMapRegionPicked = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                    tracks = shownTracks,
                    onTracksOpened = {},
                    findsContent = {},
                    onGpxFilePicked = onPicked ?: { uri -> viewModel.importFile(ContentUriGpxFileSource(app.contentResolver, uri, ErrorLog { _, _, _ -> })); Unit },
                    openTrackDetails = (notice?.outcome as? GpxImportOutcome.Imported)?.trackIds?.firstOrNull(),
                    onOpenTrackDetailsConsumed = { notice?.let { viewModel.onNoticeShown(it.seq) } },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.RECORDED_TRACKS)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private lateinit var viewModel: GpxImportViewModel

    /** Lets the import's IO and Room threads finish, then the UI catch up. */
    private fun settle(until: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (!until() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.waitForIdle()
            Thread.sleep(10L)
        }
        composeRule.waitForIdle()
    }

    private fun stored(): List<Track> = runBlocking { tracks.getAll() }.getOrThrow()

    @Test
    fun `touches across Import GPX open the system file picker for GPX files, and backing out imports nothing`() {
        var picked = 0
        setScreen(onPicked = { picked++ })
        answer = null
        val fractions = listOf(0.1f to 0.5f, 0.9f to 0.5f, 0.5f to 0.15f, 0.5f to 0.85f, 0.5f to 0.5f)

        for ((i, f) in fractions.withIndex()) {
            composeRule.onNodeWithTag(IMPORT_GPX_BUTTON_TAG).performTouchInput { click(Offset(width * f.first, height * f.second)) }
            composeRule.waitForIdle()
            assertEquals("touch ${i + 1} at $f opened the picker", i + 1, launched.size)
        }
        val intent = launched.last()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals(
            listOf("application/gpx+xml", "application/octet-stream", "text/xml", "application/xml"),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList(),
        )
        assertEquals("backing out of the picker hands nothing on", 0, picked)
        composeRule.onNodeWithText("No recorded tracks yet.").assertExists()
    }

    @Test
    fun `a picked file is imported, lands in Records with its name and the Imported label, and its details open`() {
        setScreen()
        answer = Uri.parse("content://com.example.files/document/Morning%20Hike.gpx")

        composeRule.onNodeWithTag(IMPORT_GPX_BUTTON_TAG).performTouchInput { click(center) }
        settle { stored().isNotEmpty() && runCatching { composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertExists() }.isSuccess }

        val track = stored().single()
        assertEquals("Morning Hike", track.name)
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").format(Instant.ofEpochMilli(track.startedAtEpochMillis).atZone(ZoneId.systemDefault()))
        composeRule.onNodeWithTag(RECORD_DETAILS_TITLE_TAG).assertTextEquals("Morning Hike")
        composeRule.onNodeWithTag(RECORD_DETAILS_LABEL_TAG).assertTextEquals("Imported")
        // Dispatch 2026-09-28-677 changed this: Duration ("0m") is the Time tile now, in data part A's words.
        composeRule.onNodeWithTag(trackTileTag(TRACK_TILE_TIME)).assertTextEquals("Time", "Under 1 min")
        composeRule.onNodeWithTag(recordDetailsFieldTag(FIELD_STARTED)).assertTextEquals("Started", date)
        assertTrue("the notice was shown and cleared", viewModel.notice.value == null)
    }

    @Test
    fun `an imported track's row shows its name and Imported, and a recorded walk's row is unchanged`() {
        val start = Instant.parse("2026-09-20T15:00:00Z").toEpochMilli()
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()))
        val points = listOf(TrackPoint(45.5, -122.6, null, null, start), TrackPoint(45.6, -122.6, null, null, start + 60_000L))
        setScreen(
            initialTracks = listOf(
                Track("imp", "Ridge loop", start, start + 60_000L, points, importedAtEpochMillis = importNow),
                Track("rec", null, start, start + 60_000L, points),
            ),
        )

        composeRule.onNodeWithTag(trackExportRowTag("imp")).assertTextEquals("Ridge loop", "Imported · $date · 2 points")
        composeRule.onNodeWithTag(trackExportRowTag("rec")).assertTextEquals(date, "2 points")
    }

    @Test
    fun `a track whose file had no times says so in its details where the times would be`() {
        val start = importNow
        val points = listOf(TrackPoint(45.5, -122.6, null, null, start), TrackPoint(45.6, -122.6, null, null, start + 1_000L))
        setScreen(initialTracks = listOf(Track("imp", "Planned loop", start, start + 1_000L, points, importedAtEpochMillis = importNow, importedWithoutTimes = true)))

        composeRule.onNodeWithTag(trackExportRowTag("imp")).performTouchInput { click(center) }
        composeRule.waitForIdle()

        val imported = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").format(Instant.ofEpochMilli(importNow).atZone(ZoneId.systemDefault()))
        composeRule.onNodeWithTag(recordDetailsFieldTag(FIELD_STARTED)).assertTextEquals("Started", "No times in file")
        composeRule.onNodeWithTag(recordDetailsFieldTag(FIELD_ENDED)).assertTextEquals("Ended", "No times in file")
        // Dispatch -677 changed this: Duration is the Time tile, and Moving speed, which also needs times, says so too.
        composeRule.onNodeWithTag(trackTileTag(TRACK_TILE_TIME)).assertTextEquals("Time", "No times in file")
        composeRule.onNodeWithTag(trackTileTag(TRACK_TILE_MOVING_SPEED)).assertTextEquals("Moving speed", "No times in file")
        composeRule.onNodeWithTag(recordDetailsFieldTag(FIELD_IMPORTED)).assertTextEquals("Imported", imported)
    }
}

private val GIR_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
