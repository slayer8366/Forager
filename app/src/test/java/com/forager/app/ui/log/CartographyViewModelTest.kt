package com.forager.app.ui.log

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.data.repository.RoomCartographyEntryRepository
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.data.repository.RoomWaypointRepository
import com.forager.app.domain.CommitCartographyEntryUseCase
import com.forager.app.domain.ComputeTrackStatisticsUseCase
import com.forager.app.domain.CreateCartographyEntryUseCase
import com.forager.app.domain.DeleteCartographyEntryUseCase
import com.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.forager.app.domain.GetCartographyEntriesUseCase
import com.forager.app.domain.GetDerivedTripUseCase
import com.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.SaveCartographyEntryUseCase
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CartographyViewModel] over a real, in-memory Room database — same reasoning
 * [GetDerivedTripUseCaseTest] gives for using the real repositories rather than hand-built fakes:
 * the withhold/keep curation logic reads real day-scoped queries, and a fake could silently
 * re-implement the same mistake those queries might have.
 *
 * Covers: every trip-report candidate starts kept (owner decision — "withholding is a first-class
 * operation, not a filter"); [CartographyViewModel.onToggleKeptTrack] withholds then restores;
 * finishing and deleting an entry never gates on [com.forager.app.domain.model.CartographyEntry.text]
 * or any kept-item count (`amendment-2b-optional-writing.md`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var database: ForagerDatabase
    private lateinit var viewModel: CartographyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // A direct (synchronous, same-thread) executor for both query and transaction work — Room's
        // real executors run on genuine background threads, which advanceUntilIdle()'s *virtual*
        // scheduler cannot wait for once a viewModelScope.launch body crosses that boundary (unlike
        // GetDerivedTripUseCaseTest, which awaits the same real Room work directly in the test's own
        // suspend body, with no separate launched coroutine or virtual-time scheduler in the way).
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        )
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        val cartographyEntryRepository = RoomCartographyEntryRepository(database.cartographyEntryDao())
        val mushroomLogRepository = RoomMushroomLogRepository(database.mushroomLogDao())
        val trackRepository = RoomTrackRepository(database.trackDao())
        val waypointRepository = RoomWaypointRepository(database.waypointDao())

        viewModel = CartographyViewModel(
            getEntries = GetCartographyEntriesUseCase(cartographyEntryRepository),
            getDraftEntries = GetCartographyDraftEntriesUseCase(cartographyEntryRepository),
            createEntry = CreateCartographyEntryUseCase(cartographyEntryRepository, now = { FIXED_NOW }, idGenerator = { "entry-1" }),
            saveEntry = SaveCartographyEntryUseCase(cartographyEntryRepository),
            commitEntry = CommitCartographyEntryUseCase(cartographyEntryRepository, now = { FIXED_NOW }),
            deleteEntry = DeleteCartographyEntryUseCase(cartographyEntryRepository),
            getDerivedTrip = GetDerivedTripUseCase(
                mushroomLogRepository = mushroomLogRepository,
                trackRepository = trackRepository,
                waypointRepository = waypointRepository,
                offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
            ),
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(StubOfflineMapRepository),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            now = { FIXED_NOW },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `starting a new entry keeps every one of that day's candidates by default`() = runTest(dispatcher) {
        val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.5, -122.6), date = DAY).copy(isDraft = false)
        RoomMushroomLogRepository(database.mushroomLogDao()).save(find).getOrThrow()

        val waypoint = Waypoint(id = "waypoint-1", lat = 45.5, lng = -122.6, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = dayStartMillis())
        RoomWaypointRepository(database.waypointDao()).save(waypoint).getOrThrow()

        val trackRepository = RoomTrackRepository(database.trackDao())
        val track = Track(id = "track-1", name = "Ridge Loop", startedAtEpochMillis = dayStartMillis(), endedAtEpochMillis = dayStartMillis() + 60_000L, points = emptyList())
        trackRepository.create(track).getOrThrow()
        trackRepository.appendPoints(
            trackId = track.id,
            points = listOf(TrackPoint(lat = 45.5, lng = -122.6, altitude = null, accuracyMeters = null, timestampEpochMillis = dayStartMillis())),
        ).getOrThrow()
        trackRepository.end(track.id, dayStartMillis() + 60_000L).getOrThrow()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()

        val entry = viewModel.uiState.value.editingEntry
        assertEquals(listOf("find-1"), entry?.keptFinds?.map { it.findId })
        assertEquals(listOf("waypoint-1"), entry?.keptWaypoints?.map { it.waypointId })
        assertEquals(listOf("track-1"), entry?.keptTracks?.map { it.trackId })
    }

    @Test
    fun `withholding a kept track removes it, and toggling again restores it`() = runTest(dispatcher) {
        val trackRepository = RoomTrackRepository(database.trackDao())
        val track = Track(id = "track-1", name = "Ridge Loop", startedAtEpochMillis = dayStartMillis(), endedAtEpochMillis = dayStartMillis() + 60_000L, points = emptyList())
        trackRepository.create(track).getOrThrow()
        trackRepository.end(track.id, dayStartMillis() + 60_000L).getOrThrow()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        assertEquals(listOf("track-1"), viewModel.uiState.value.editingEntry?.keptTracks?.map { it.trackId })

        viewModel.onToggleKeptTrack("track-1")
        advanceUntilIdle()
        assertTrue("withholding is a deliberate act, not a filter default", viewModel.uiState.value.editingEntry?.keptTracks.orEmpty().isEmpty())

        viewModel.onToggleKeptTrack("track-1")
        advanceUntilIdle()
        assertEquals(listOf("track-1"), viewModel.uiState.value.editingEntry?.keptTracks?.map { it.trackId })
    }

    /** `amendment-2b-optional-writing.md`: selection alone is a complete act of authorship — finishing a wordless entry must succeed, not be blocked or read as incomplete. */
    @Test
    fun `finishing a wordless entry with kept items succeeds and commits it`() = runTest(dispatcher) {
        val waypoint = Waypoint(id = "waypoint-1", lat = 45.5, lng = -122.6, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = dayStartMillis())
        RoomWaypointRepository(database.waypointDao()).save(waypoint).getOrThrow()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val draft = viewModel.uiState.value.editingEntry!!
        assertEquals("", draft.text)
        assertTrue(draft.isDraft)

        viewModel.onFinishEntry()
        advanceUntilIdle()

        val committed = viewModel.uiState.value.editingEntry!!
        assertFalse(committed.isDraft)
        assertEquals("", committed.text)
        assertEquals(listOf("waypoint-1"), committed.keptWaypoints.map { it.waypointId })
        assertEquals(listOf(committed), viewModel.uiState.value.entries)
        assertTrue(viewModel.uiState.value.draftEntries.isEmpty())
    }

    @Test
    fun `deleting an entry removes it from both entries and drafts`() = runTest(dispatcher) {
        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val draftId = viewModel.uiState.value.editingEntry!!.id

        viewModel.onDeleteEntry(draftId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.none { it.id == draftId })
        assertTrue(viewModel.uiState.value.draftEntries.none { it.id == draftId })
    }

    private fun dayStartMillis(): Long = DAY.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 8, 1)
        const val FIXED_NOW = 10_000L
    }
}

/** Reports no regions on disk — this test file never exercises offline-region trip-report coverage, only tracks/waypoints/finds. */
private object StubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> =
        error("not used by this test")

    override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used by this test")

    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}
