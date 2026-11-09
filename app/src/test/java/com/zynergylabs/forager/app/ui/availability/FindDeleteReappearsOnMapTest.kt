package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.AddPhotoToGalleryUseCase
import com.zynergylabs.forager.app.domain.AddPhotoToLogEntryUseCase
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.CommitDraftEntryUseCase
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.CreateMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeleteGalleryPhotoUseCase
import com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.HistoricalWeatherProvider
import com.zynergylabs.forager.app.domain.InMemorySearchCacheRepository
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.MushroomLogRepository
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PhotoStore
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.PullPhotoIntoEntryUseCase
import com.zynergylabs.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.zynergylabs.forager.app.domain.SaveMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.StartEditingLogEntryUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.TripPlanningWeatherProvider
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.UpdatePhotoLocationUseCase
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-291 (find-delete-reappears): once a find is deleted the Maps tab never draws it
 * again. The real [MushroomLogViewModel] (request, Undo and commit calls) and the real
 * [AvailabilityViewModel] (`onMapShown`, its records read from the same in-memory find store the
 * delete removes from), wired as `MainActivity` wires them (the log ViewModel's `onFindDeleted` to the availability one's).
 *
 * What the map draws is [drawn]: `AvailabilityScreen`'s own `mapRecordsDrawn` expression
 * (`uiState.mapRecords.withoutPending(findId = logUiState.pendingDelete?.item?.id, ...)`), which is
 * computed inside a composable, so it is repeated here rather than called. A test of it that passes
 * while the screen's line differs would be a check decoupled from its subject, so the line is
 * quoted in the report and compared by eye.
 *
 * The owner's path is the second test: back from the find page runs `onMapShown` while the delete is
 * still pending, so the new snapshot contains the find; Undo ends and the delete commits; nothing else
 * happens. The filter alone hid the find, so when the commit clears the pending slot the stale snapshot
 * draws it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FindDeleteReappearsOnMapTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val findA = find("find-a", 45.30)
    private val findB = find("find-b", 45.31)

    private class Rig(
        val repository: FindStore,
        val log: MushroomLogViewModel,
        val availability: AvailabilityViewModel,
    ) {
        /** What `AvailabilityScreen` hands the map: its `mapRecordsDrawn` line. */
        fun drawn(): List<String> = availability.uiState.value.mapRecords.withoutPending(
            findId = log.uiState.value.pendingDelete?.item?.id,
            photoId = log.uiState.value.pendingPhotoDelete?.item?.photo?.id,
            offlineRegionId = availability.uiState.value.pendingOfflineRegionDelete?.item?.id?.toString(),
        ).findMarkers.map { it.recordId }
    }

    private fun rig(vararg finds: MushroomLogEntry, deleteShouldFail: Boolean = false): Rig {
        val repository = FindStore(finds.toList(), deleteShouldFail)
        val photoStore = NoPhotoStore
        val searchCache = InMemorySearchCacheRepository()
        val availability = AvailabilityViewModel(
            locationProvider = NoLocationProvider,
            locationTracker = NoLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(NoMushroomData), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(NoMushroomData),
            searchTaxa = SearchTaxaUseCase(NoMushroomData),
            getConditions = GetConditionsUseCase(NoWeather),
            getTripWindows = GetTripWindowsUseCase(NoWeather, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(NoTrips),
            savePlannedTrip = SavePlannedTripUseCase(NoTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(NoTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(GetSightingsUseCase(NoMushroomData), NoWeather, ComputeFruitingLagDistributionUseCase()),
            offlineMapRepository = NoOfflineRegions,
            mapPreferencesRepository = NoMapPreferences,
            unitSystemPreferenceRepository = ImperialUnits,
            appThemePreferenceRepository = LightTheme,
            getTodaysForecast = GetTodaysForecastUseCase(NoWeather),
            // The real read is the database the delete removes from: the same store, read at the moment it is asked.
            getMapRecords = { MapRecords.NONE.copy(findMarkers = repository.markers()) },
        )
        val log = MushroomLogViewModel(
            getEntries = GetMushroomLogEntriesUseCase(repository),
            getDraftEntries = GetDraftEntriesUseCase(repository),
            createEntry = CreateMushroomLogEntryUseCase(repository, today = { LocalDate.of(2026, 9, 30) }, idGenerator = { "find-new" }),
            startEditingEntry = StartEditingLogEntryUseCase(repository, idGenerator = { "draft-of-find" }),
            saveEntry = SaveMushroomLogEntryUseCase(repository),
            commitDraftEntry = CommitDraftEntryUseCase(repository),
            deleteEntry = DeleteMushroomLogEntryUseCase(repository),
            addPhoto = AddPhotoToLogEntryUseCase(photoStore, repository),
            addPhotoToGallery = AddPhotoToGalleryUseCase(photoStore, repository),
            removePhoto = RemovePhotoFromLogEntryUseCase(repository),
            getGalleryPhotos = GetGalleryPhotosUseCase(repository),
            pullPhotoIntoEntry = PullPhotoIntoEntryUseCase(repository),
            deleteGalleryPhoto = DeleteGalleryPhotoUseCase(repository, photoStore),
            locationProvider = NoLocationProvider,
            updatePhotoLocation = UpdatePhotoLocationUseCase(repository),
            // As MainActivity wires it.
            onFindDeleted = { id -> availability.onFindDeleted(id) },
        )
        return Rig(repository, log, availability)
    }

    @Test
    fun `a deleted find is not drawn after its delete commits`() = runTest(dispatcher) {
        val rig = rig(findA, findB)
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals("positive control: both finds are drawn", listOf("find-a", "find-b"), rig.drawn())

        rig.log.requestDeleteEntry("find-a")
        advanceUntilIdle()
        assertEquals("the Undo window hides it", listOf("find-b"), rig.drawn())

        rig.log.commitDeleteEntry("find-a")
        advanceUntilIdle()

        assertEquals("the delete ran, once", listOf("find-a"), rig.repository.deletedIds)
        assertEquals("find-a was deleted and is still drawn", listOf("find-b"), rig.drawn())
    }

    @Test
    fun `a find deleted while the map was re-shown is not drawn after the Undo window ends, with nothing tapped`() = runTest(dispatcher) {
        val rig = rig(findA, findB)
        rig.availability.onMapShown()
        advanceUntilIdle()

        // The owner's path: from the map's fan to the find page, delete it there...
        rig.log.requestDeleteEntry("find-a")
        advanceUntilIdle()
        // ...and back to the map: the tab is shown again while the delete is still only pending.
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals(
            "positive control: the new snapshot was read while the row was still in the database, so it holds the find",
            listOf("find-a", "find-b"),
            rig.availability.uiState.value.mapRecords.findMarkers.map { it.recordId },
        )
        assertEquals("the Undo window hides it", listOf("find-b"), rig.drawn())

        // The snackbar times out. No tap, no other find highlighted.
        rig.log.commitDeleteEntry("find-a")
        advanceUntilIdle()

        assertEquals("find-a was deleted and is still drawn", listOf("find-b"), rig.drawn())
    }

    @Test
    fun `a second delete that commits the first leaves the first undrawn`() = runTest(dispatcher) {
        val rig = rig(findA, findB)
        rig.availability.onMapShown()
        advanceUntilIdle()

        rig.log.requestDeleteEntry("find-a")
        advanceUntilIdle()
        rig.log.requestDeleteEntry("find-b")
        advanceUntilIdle()

        assertEquals("the second delete committed the first", listOf("find-a"), rig.repository.deletedIds)
        assertEquals("find-a was deleted and is still drawn (find-b is the one pending)", emptyList<String>(), rig.drawn())
    }

    @Test
    fun `Undo brings the find back on the map`() = runTest(dispatcher) {
        val rig = rig(findA, findB)
        rig.availability.onMapShown()
        advanceUntilIdle()
        rig.log.requestDeleteEntry("find-a")
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals("positive control: hidden while pending", listOf("find-b"), rig.drawn())

        rig.log.undoDeleteEntry("find-a")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), rig.repository.deletedIds)
        assertEquals(listOf("find-a", "find-b"), rig.drawn())
    }

    @Test
    fun `a delete that fails leaves the find drawn`() = runTest(dispatcher) {
        val rig = rig(findA, findB, deleteShouldFail = true)
        rig.availability.onMapShown()
        advanceUntilIdle()
        rig.log.requestDeleteEntry("find-a")
        advanceUntilIdle()

        rig.log.commitDeleteEntry("find-a")
        advanceUntilIdle()

        assertEquals("the delete was tried", listOf("find-a"), rig.repository.deletedIds)
        assertEquals("the find is still saved, so it is still drawn", listOf("find-a", "find-b"), rig.drawn())
    }
}

private fun find(id: String, lat: Double) =
    MushroomLogEntry.draft(id = id, location = LatLng(lat, -122.6), date = LocalDate.of(2026, 9, 20)).copy(isDraft = false)

/** Finds kept in memory; [markers] is what the map's read makes of them, and every [delete] call is recorded. */
private class FindStore(initial: List<MushroomLogEntry>, private val deleteShouldFail: Boolean) : MushroomLogRepository {
    private val entries = initial.associateByTo(LinkedHashMap()) { it.id }
    val deletedIds = mutableListOf<String>()

    fun markers(): List<RecordPoint> = entries.values.mapNotNull { e -> e.foundAt?.let { RecordPoint(e.id, it) } }

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(entries.values.toList())
    override suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>> =
        Result.success(entries.values.filter { it.foundOn.toString() == foundOnKey })
    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = Result.success(emptyList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = Result.success(Unit).also { entries[entry.id] = entry }
    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> =
        Result.failure(UnsupportedOperationException("drafts are not part of this test's path"))
    override suspend fun delete(id: String): Result<Unit> {
        deletedIds += id
        if (deleteShouldFail) return Result.failure(RuntimeException("delete failed"))
        entries.remove(id)
        return Result.success(Unit)
    }
    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> = unsupported()
    override suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit> = unsupported()
    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> = unsupported()
    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> = unsupported()
    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> = unsupported()

    private fun unsupported(): Result<Unit> = Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
}

private object NoPhotoStore : PhotoStore {
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
    override suspend fun delete(photo: LogPhoto): Result<Unit> = Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
}

private object NoLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object NoLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object NoMushroomData : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) = Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object NoWeather : WeatherProvider, TripPlanningWeatherProvider, HistoricalWeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("not part of this test's path"))
}

private object NoTrips : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun delete(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this test's path"))
}

private object NoOfflineRegions : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun deleteRegion(id: Long): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object NoMapPreferences : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.success(false)
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.success(Unit)
}

private object ImperialUnits : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object LightTheme : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
