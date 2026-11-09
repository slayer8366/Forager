package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.InMemoryKeptTrackPaths
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.EndAbandonedTracksUseCase
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import com.zynergylabs.forager.app.ui.track.trackExportRowTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

/**
 * Part 2 follow-ups F1 item 5, as replaced by continuation `2026-09-28-191`
 * (`prompts/preserved/2026-09-29-36.md`, owner "Option A"): a track can be deleted like a waypoint. Swipe
 * on its Records row (Tracks and All), and a Delete on its details, the compact sheet and the tablet's
 * pane, both with the waypoints' pending delete and Undo; never a track that is still recording; the real
 * delete is the existing [DeleteTrackUseCase] and runs only when the Undo window closes.
 *
 * Driven through the real [RecordsTab] and the real [TrackRecordingViewModel] over fake repositories that
 * record every call in one shared log, with a real [SnackbarHost] fed by the same notice builder
 * `MainActivity` uses. Swipes and the Delete are real touches. Every delete assertion reads the fake
 * repositories' own call log, not a callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class TrackDeleteTest {

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

    /** Every repository call that changes something, in order: "detach:<id>" and "delete:<id>". */
    private val calls = mutableListOf<String>()
    private val loggedFailures = mutableListOf<String>()
    private lateinit var trackViewModel: TrackRecordingViewModel

    private fun setScreen(
        chip: RecordsSubTab = RecordsSubTab.RECORDED_TRACKS,
        tracks: List<Track> = listOf(DONE_TRACK, OTHER_DONE_TRACK, RECORDING_TRACK),
        referenceCounts: Map<String, Int> = mapOf("t-done" to 2, "t-other" to 1),
    ) {
        val trackRepository = TrackDeleteTrackRepository(tracks, calls)
        val waypointRepository = TrackDeleteWaypointRepository(calls)
        trackViewModel = TrackRecordingViewModel(
            trackRepository = trackRepository,
            startTrack = StartTrackUseCase(trackRepository, currentTime = TD_TIME, idGenerator = { "track-new" }),
            getWaypoints = GetWaypointsUseCase(waypointRepository),
            createWaypoint = CreateWaypointUseCase(waypointRepository, currentTime = TD_TIME, idGenerator = { "wp-new" }),
            deleteWaypoint = DeleteWaypointUseCase(waypointRepository),
            deleteTrack = DeleteTrackUseCase(trackRepository, waypointRepository, InMemoryKeptTrackPaths()),
            getTrackOriginWaypoint = GetTrackOriginWaypointUseCase(trackRepository, waypointRepository),
            alreadyRecordingMessage = "Forager is already recording a track. Stop it before starting another.",
            abandonedTrackSweepOnce = AbandonedTrackSweepOnce(EndAbandonedTracksUseCase(trackRepository, { null }, { _, _, _ -> }), processStartedAtEpochMillis = 0L),
            computeReturnToStart = ComputeReturnToStartUseCase(),
            locationTracker = TrackDeleteNoOpLocationTracker,
            getTracks = GetTracksUseCase(trackRepository),
            returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), { _: Alert -> }),
            alertAudibility = TrackDeleteAudible,
            errorLog = ErrorLog { tag, message, _ -> loggedFailures += "$tag: $message" },
            getTrackReferenceCount = { id -> referenceCounts[id] ?: 0 },
        )
        trackViewModel.loadTracks()
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            val track by trackViewModel.uiState.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {
                RecordsTab(
                    waypoints = emptyList(),
                    waypointsErrorMessage = null,
                    onDeleteWaypoint = {},
                    availabilityUiState = AvailabilityUiState(),
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { 0L },
                    mapSlot = TD_STUB_MAP,
                    night = false,
                    onOfflineMapRegionPicked = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                    // What MainActivity passes: the visible list, and the pending-delete request.
                    tracks = track.visibleTracks,
                    onTracksOpened = trackViewModel::loadTracks,
                    onDeleteTrack = trackViewModel::requestRemoveTrack,
                    findsContent = {},
                )
                SnackbarHost(hostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
            PendingDeleteSnackbarEffects(
                notices = listOfNotNull(trackDeleteNotice(track.pendingTrackDelete, trackViewModel::undoRemoveTrack, trackViewModel::commitRemoveTrack)),
                hostState = hostState,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(chip)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun swipeRowLeft(id: String) {
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.TRACKS, id)).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    private fun letSnackbarTimeOut() {
        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MILLIS + 1_000L)
        composeRule.waitForIdle()
    }

    private fun touchUndo() {
        composeRule.onNodeWithText("Undo").assert(hasClickAction()).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    private fun swipeExists(id: String) =
        composeRule.onAllNodesWithTagCount(swipeToDeleteTag(RecordType.TRACKS, id)) > 0

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    // ── The swipe, on the Tracks chip ──

    @Test
    fun `swiping a finished track's row hides it, warns of its references, and deletes nothing yet`() {
        setScreen()

        swipeRowLeft("t-done")

        composeRule.onNodeWithText("Track deleted · used in 2 journal entries").assertIsDisplayed()
        assertEquals("the row is gone", false, swipeExists("t-done"))
        assertEquals("and nothing was deleted", emptyList<String>(), calls)
    }

    @Test
    fun `Undo brings the track row back and deletes nothing, even after the timeout would have passed`() {
        setScreen()
        swipeRowLeft("t-done")

        touchUndo()

        assertEquals(true, swipeExists("t-done"))
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), calls)
        assertEquals(true, swipeExists("t-done"))
    }

    @Test
    fun `when the snackbar times out the track's waypoints are detached and then it is deleted, once`() {
        setScreen()
        swipeRowLeft("t-other")
        assertEquals(emptyList<String>(), calls)

        letSnackbarTimeOut()

        assertEquals("the use case detaches first, then deletes, with the swiped id", listOf("detach:t-other", "delete:t-other"), calls)
        assertEquals("the neighbour was not touched", true, swipeExists("t-done"))
    }

    @Test
    fun `a second swipe commits the first track when its snackbar replaces the first`() {
        setScreen()
        swipeRowLeft("t-done")

        swipeRowLeft("t-other")

        assertEquals(listOf("detach:t-done", "delete:t-done"), calls)
        composeRule.onNodeWithText("Track deleted · used in 1 journal entry").assertIsDisplayed()
        letSnackbarTimeOut()
        assertEquals(listOf("detach:t-done", "delete:t-done", "detach:t-other", "delete:t-other"), calls)
    }

    @Test
    fun `the track row's Delete accessibility action does the same pending delete`() {
        setScreen()
        val node = composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.TRACKS, "t-other")).performScrollTo().fetchSemanticsNode()
        val delete = node.config[SemanticsActions.CustomActions].single { it.label == "Delete" }

        composeRule.runOnUiThread { delete.action() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Track deleted · used in 1 journal entry").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a track no entry uses says only Track deleted`() {
        setScreen(referenceCounts = emptyMap())

        swipeRowLeft("t-done")

        composeRule.onNodeWithText("Track deleted").assertIsDisplayed()
        composeRule.onNodeWithText("used in", substring = true).assertDoesNotExist()
    }

    // ── The swipe, in the All logbook ──

    @Test
    fun `a pending track is gone from All and from All's count`() {
        setScreen(chip = RecordsSubTab.ALL)
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.RECORDED_TRACKS)).assert(hasText("3"))
        composeRule.onNodeWithTag(logbookRowTag(RecordType.TRACKS, "t-other")).assertExists()

        swipeRowLeft("t-other")

        composeRule.onNodeWithTag(logbookRowTag(RecordType.TRACKS, "t-other")).assertDoesNotExist()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.RECORDED_TRACKS)).assert(hasText("2"))
        composeRule.onNodeWithText("Track deleted · used in 1 journal entry").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
    }

    // ── Never a track that is recording ──

    @Test
    fun `a recording track has no swipe on the Tracks chip and none in All`() {
        setScreen()
        assertEquals("finished tracks do", true, swipeExists("t-done"))
        assertEquals("the recording track's row is there without a swipe", false, swipeExists("t-rec"))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(false, swipeExists("t-rec"))
        composeRule.onNodeWithTag(logbookRowTag(RecordType.TRACKS, "t-rec")).assertExists()
    }

    @Test
    fun `a real swipe across the recording track's row deletes and pends nothing`() {
        setScreen()

        composeRule.onNodeWithTag(trackExportRowTag("t-rec")).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        letSnackbarTimeOut()

        composeRule.onNodeWithText("Track deleted", substring = true).assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `the view model refuses a delete of a recording track, logs it, and pends nothing`() {
        setScreen()
        composeRule.runOnIdle { trackViewModel.requestRemoveTrack("t-rec") }
        composeRule.waitForIdle()

        assertNull(trackViewModel.uiState.value.pendingTrackDelete)
        assertEquals(true, trackViewModel.uiState.value.visibleTracks.any { it.id == "t-rec" })
        assertTrue("the refusal is logged: $loggedFailures", loggedFailures.any { it.contains("t-rec") })
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `the view model logs a delete asked for a track it does not have`() {
        setScreen()
        composeRule.runOnIdle { trackViewModel.requestRemoveTrack("no-such-track") }

        assertNull(trackViewModel.uiState.value.pendingTrackDelete)
        assertTrue("logged: $loggedFailures", loggedFailures.any { it.contains("no-such-track") })
    }

    // ── The details' Delete: the compact sheet ──

    private fun openDetails(id: String) {
        composeRule.onNodeWithTag(trackExportRowTag(id)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the compact details sheet of a finished track has a Delete that starts the same pending delete`() {
        setScreen()
        openDetails("t-done")
        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(RECORD_DETAILS_DELETE_TAG).assertIsDisplayed().assert(hasText("Delete"))
        composeRule.onNodeWithTag(RECORD_DETAILS_DELETE_TAG).performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Track deleted · used in 2 journal entries").assertIsDisplayed()
        assertEquals("nothing deleted while Undo is up", emptyList<String>(), calls)
        touchUndo()
        assertEquals(true, swipeExists("t-done"))
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `the details Delete deletes through the use case only when the Undo window closes`() {
        setScreen()
        openDetails("t-other")

        composeRule.onNodeWithTag(RECORD_DETAILS_DELETE_TAG).performTouchInput { click() }
        composeRule.waitForIdle()
        letSnackbarTimeOut()

        assertEquals(listOf("detach:t-other", "delete:t-other"), calls)
    }

    @Test
    fun `the compact details sheet of a recording track has no Delete`() {
        setScreen()
        openDetails("t-rec")

        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RECORD_DETAILS_DELETE_TAG).assertDoesNotExist()
    }
}

private const val SNACKBAR_LONG_MILLIS = 10_000L
private val TD_TIME = CurrentTimeProvider { 1_000L }
private val TD_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

private val DONE_TRACK = Track(
    id = "t-done",
    name = "Morning loop",
    startedAtEpochMillis = 1_758_100_000_000L,
    endedAtEpochMillis = 1_758_103_600_000L,
    points = listOf(TrackPoint(45.5, -122.6, null, null, 1_758_100_000_000L), TrackPoint(45.51, -122.61, null, null, 1_758_100_060_000L)),
)
private val OTHER_DONE_TRACK = DONE_TRACK.copy(id = "t-other", name = "Ridge walk", startedAtEpochMillis = 1_758_000_000_000L, endedAtEpochMillis = 1_758_003_600_000L)

/** No end time: still recording. */
private val RECORDING_TRACK = DONE_TRACK.copy(id = "t-rec", name = "Live now", startedAtEpochMillis = 1_758_200_000_000L, endedAtEpochMillis = null)

private object TrackDeleteNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object TrackDeleteAudible : AlertAudibility {
    override fun current(): AlertAudibilityState =
        AlertAudibilityState(ringerMode = RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true)
}

/** Tracks kept in memory; every [detach] and [delete] is appended, in order, to the shared [calls] log. */
private class TrackDeleteTrackRepository(initial: List<Track>, private val calls: MutableList<String>) : TrackRepository {
    private val tracks = initial.toMutableList()

    override suspend fun getAll(): Result<List<Track>> = Result.success(tracks.toList())
    override suspend fun getById(id: String): Result<Track?> = Result.success(tracks.firstOrNull { it.id == id })
    override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> = Result.success(emptyList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.success(emptyList())
    override suspend fun create(track: Track): Result<Unit> = Result.failure(UnsupportedOperationException("recording is not part of this test's path"))
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = Result.success(Unit)
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = Result.success(Unit)
    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> {
        calls += "delete:$id"
        tracks.removeAll { it.id == id }
        return Result.success(Unit)
    }
}

private class TrackDeleteWaypointRepository(private val calls: MutableList<String>) : WaypointRepository {
    override suspend fun getAll(): Result<List<Waypoint>> = Result.success(emptyList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>> = Result.success(emptyList())
    override suspend fun getById(id: String): Result<Waypoint?> = Result.success(null)
    override suspend fun getForTrack(trackId: String): Result<List<Waypoint>> = Result.success(emptyList())
    override suspend fun detachFromTrack(trackId: String): Result<Unit> {
        calls += "detach:$trackId"
        return Result.success(Unit)
    }
    override suspend fun save(waypoint: Waypoint): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}
