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
import com.zynergylabs.forager.app.domain.model.RecordRegion
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
 * Dispatch 2026-09-28-297 (delete-siblings): the siblings of [FindDeleteReappearsOnMapTest]. Once an album
 * photo or an offline region is deleted, the Maps tab never draws it again. The real [MushroomLogViewModel]
 * (photo request, Undo and commit calls) and the real [AvailabilityViewModel] (region request, Undo and
 * commit calls, and `onMapShown`), each reading its records from the in-memory store the delete removes
 * from, wired as `MainActivity` wires them (the log ViewModel's `onPhotoDeleted` to the availability one's).
 *
 * What the map draws is [Rig.drawnPhotos] / [Rig.drawnRegions]: `AvailabilityScreen`'s own `mapRecordsDrawn`
 * expression, repeated here because it is computed inside a composable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SiblingDeletesReappearOnMapTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Rig(
        val photos: PhotoRepo,
        val regions: RegionStore,
        val log: MushroomLogViewModel,
        val availability: AvailabilityViewModel,
    ) {
        private fun drawn() = availability.uiState.value.mapRecords.withoutPending(
            findId = log.uiState.value.pendingDelete?.item?.id,
            photoId = log.uiState.value.pendingPhotoDelete?.item?.photo?.id,
            offlineRegionId = availability.uiState.value.pendingOfflineRegionDelete?.item?.id?.toString(),
        )

        /** What `AvailabilityScreen` hands the map: its `mapRecordsDrawn` line. */
        fun drawnPhotos(): List<String> = drawn().photoMarkers.map { it.recordId }
        fun drawnRegions(): List<String> = drawn().offlineRegionCircles.map { it.recordId }
    }

    private fun rig(
        photos: List<String> = emptyList(),
        regions: List<Long> = emptyList(),
        photoDeleteShouldFail: Boolean = false,
        regionDeleteShouldFail: Boolean = false,
    ): Rig {
        val photoRepo = PhotoRepo(photos, photoDeleteShouldFail)
        val regionStore = RegionStore(regions, regionDeleteShouldFail)
        val photoStore = SucceedingPhotoStore
        val searchCache = InMemorySearchCacheRepository()
        val availability = AvailabilityViewModel(
            locationProvider = SibNoLocationProvider,
            locationTracker = SibNoLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(SibNoMushroomData), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(SibNoMushroomData),
            searchTaxa = SearchTaxaUseCase(SibNoMushroomData),
            getConditions = GetConditionsUseCase(SibNoWeather),
            getTripWindows = GetTripWindowsUseCase(SibNoWeather, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(SibNoTrips),
            savePlannedTrip = SavePlannedTripUseCase(SibNoTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(SibNoTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(GetSightingsUseCase(SibNoMushroomData), SibNoWeather, ComputeFruitingLagDistributionUseCase()),
            offlineMapRepository = regionStore,
            mapPreferencesRepository = SibNoMapPreferences,
            unitSystemPreferenceRepository = SibImperialUnits,
            appThemePreferenceRepository = SibLightTheme,
            getTodaysForecast = GetTodaysForecastUseCase(SibNoWeather),
            // The real reads are the stores the deletes remove from, read at the moment they are asked.
            getMapRecords = { MapRecords.NONE.copy(photoMarkers = photoRepo.markers(), offlineRegionCircles = regionStore.circles()) },
        )
        val log = MushroomLogViewModel(
            getEntries = GetMushroomLogEntriesUseCase(photoRepo),
            getDraftEntries = GetDraftEntriesUseCase(photoRepo),
            createEntry = CreateMushroomLogEntryUseCase(photoRepo, today = { LocalDate.of(2026, 9, 30) }, idGenerator = { "find-new" }),
            startEditingEntry = StartEditingLogEntryUseCase(photoRepo, idGenerator = { "draft-of-find" }),
            saveEntry = SaveMushroomLogEntryUseCase(photoRepo),
            commitDraftEntry = CommitDraftEntryUseCase(photoRepo),
            deleteEntry = DeleteMushroomLogEntryUseCase(photoRepo),
            addPhoto = AddPhotoToLogEntryUseCase(photoStore, photoRepo),
            addPhotoToGallery = AddPhotoToGalleryUseCase(photoStore, photoRepo),
            removePhoto = RemovePhotoFromLogEntryUseCase(photoRepo),
            getGalleryPhotos = GetGalleryPhotosUseCase(photoRepo),
            pullPhotoIntoEntry = PullPhotoIntoEntryUseCase(photoRepo),
            deleteGalleryPhoto = DeleteGalleryPhotoUseCase(photoRepo, photoStore),
            locationProvider = SibNoLocationProvider,
            updatePhotoLocation = UpdatePhotoLocationUseCase(photoRepo),
            // As MainActivity wires it.
            onPhotoDeleted = { id -> availability.onPhotoDeleted(id) },
        )
        return Rig(photoRepo, regionStore, log, availability)
    }

    // ---- photos ----

    @Test
    fun `a deleted photo is not drawn after its delete commits, with nothing tapped`() = runTest(dispatcher) {
        val rig = rig(photos = listOf("photo-a", "photo-b"))
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals("positive control: both photos are drawn", listOf("photo-a", "photo-b"), rig.drawnPhotos())

        rig.log.requestDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()
        assertEquals("the Undo window hides it", listOf("photo-b"), rig.drawnPhotos())

        rig.log.commitDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()

        assertEquals("the delete ran, once", listOf("photo-a"), rig.photos.deletedIds)
        assertEquals("photo-a was deleted and is still drawn", listOf("photo-b"), rig.drawnPhotos())
    }

    @Test
    fun `a photo deleted while the map was re-shown is not drawn after the Undo window ends`() = runTest(dispatcher) {
        val rig = rig(photos = listOf("photo-a", "photo-b"))
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()

        rig.log.requestDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals(
            "positive control: the new snapshot was read while the row was still stored, so it holds the photo",
            listOf("photo-a", "photo-b"),
            rig.availability.uiState.value.mapRecords.photoMarkers.map { it.recordId },
        )
        assertEquals("the Undo window hides it", listOf("photo-b"), rig.drawnPhotos())

        rig.log.commitDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()

        assertEquals("photo-a was deleted and is still drawn", listOf("photo-b"), rig.drawnPhotos())
    }

    @Test
    fun `Undo brings the photo back on the map`() = runTest(dispatcher) {
        val rig = rig(photos = listOf("photo-a", "photo-b"))
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        rig.log.requestDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()
        assertEquals("positive control: hidden while pending", listOf("photo-b"), rig.drawnPhotos())

        rig.log.undoDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), rig.photos.deletedIds)
        assertEquals(listOf("photo-a", "photo-b"), rig.drawnPhotos())
    }

    @Test
    fun `a photo delete that fails leaves the photo drawn`() = runTest(dispatcher) {
        val rig = rig(photos = listOf("photo-a", "photo-b"), photoDeleteShouldFail = true)
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        rig.log.requestDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()

        rig.log.commitDeleteGalleryPhoto("photo-a")
        advanceUntilIdle()

        assertEquals("the delete was tried", listOf("photo-a"), rig.photos.deletedIds)
        assertEquals("the photo is still saved, so it is still drawn", listOf("photo-a", "photo-b"), rig.drawnPhotos())
    }

    @Test
    fun `a photo deleted at once by the drawer gallery or the album trash button is not drawn`() = runTest(dispatcher) {
        val rig = rig(photos = listOf("photo-a", "photo-b"))
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals("positive control: both photos are drawn", listOf("photo-a", "photo-b"), rig.drawnPhotos())

        rig.log.onDeleteGalleryPhoto(rig.log.uiState.value.galleryPhotos.first { it.photo.id == "photo-a" })
        advanceUntilIdle()

        assertEquals("the delete ran, once", listOf("photo-a"), rig.photos.deletedIds)
        assertEquals("photo-a was deleted and is still drawn", listOf("photo-b"), rig.drawnPhotos())
    }

    // ---- offline regions ----

    @Test
    fun `a deleted offline region is not drawn after its delete commits, with nothing tapped`() = runTest(dispatcher) {
        val rig = rig(regions = listOf(1L, 2L))
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals("positive control: both regions are drawn", listOf("1", "2"), rig.drawnRegions())

        rig.availability.requestDeleteOfflineRegion(1L)
        advanceUntilIdle()
        assertEquals("the Undo window hides it", listOf("2"), rig.drawnRegions())

        rig.availability.commitDeleteOfflineRegion(1L)
        advanceUntilIdle()

        assertEquals("the delete ran, once", listOf(1L), rig.regions.deletedIds)
        assertEquals("region 1 was deleted and is still drawn", listOf("2"), rig.drawnRegions())
    }

    @Test
    fun `a region deleted while the map was re-shown is not drawn after the Undo window ends`() = runTest(dispatcher) {
        val rig = rig(regions = listOf(1L, 2L))
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()

        rig.availability.requestDeleteOfflineRegion(1L)
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        assertEquals(
            "positive control: the new snapshot was read while the region was still stored, so it holds it",
            listOf("1", "2"),
            rig.availability.uiState.value.mapRecords.offlineRegionCircles.map { it.recordId },
        )
        assertEquals("the Undo window hides it", listOf("2"), rig.drawnRegions())

        rig.availability.commitDeleteOfflineRegion(1L)
        advanceUntilIdle()

        assertEquals("region 1 was deleted and is still drawn", listOf("2"), rig.drawnRegions())
    }

    @Test
    fun `Undo brings the offline region back on the map`() = runTest(dispatcher) {
        val rig = rig(regions = listOf(1L, 2L))
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        rig.availability.requestDeleteOfflineRegion(1L)
        advanceUntilIdle()
        assertEquals("positive control: hidden while pending", listOf("2"), rig.drawnRegions())

        rig.availability.undoDeleteOfflineRegion(1L)
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), rig.regions.deletedIds)
        assertEquals(listOf("1", "2"), rig.drawnRegions())
    }

    @Test
    fun `an offline region delete that fails leaves the region drawn`() = runTest(dispatcher) {
        val rig = rig(regions = listOf(1L, 2L), regionDeleteShouldFail = true)
        advanceUntilIdle()
        rig.availability.onMapShown()
        advanceUntilIdle()
        rig.availability.requestDeleteOfflineRegion(1L)
        advanceUntilIdle()

        rig.availability.commitDeleteOfflineRegion(1L)
        advanceUntilIdle()

        assertEquals("the delete was tried", listOf(1L), rig.regions.deletedIds)
        assertEquals("the region is still stored, so it is still drawn", listOf("1", "2"), rig.drawnRegions())
    }
}

private fun galleryPhoto(id: String) = GalleryPhoto(
    photo = LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1L, latitude = 45.3, longitude = -122.6),
    referencingEntryIds = emptyList(),
)

/** Album photos kept in memory; [markers] is what the map's read makes of them, and every delete call is recorded. */
private class PhotoRepo(initial: List<String>, private val deleteShouldFail: Boolean) : MushroomLogRepository {
    private val photos = initial.map(::galleryPhoto).associateByTo(LinkedHashMap()) { it.photo.id }
    val deletedIds = mutableListOf<String>()

    fun markers(): List<RecordPoint> = photos.values.map { RecordPoint(it.photo.id, LatLng(it.photo.latitude!!, it.photo.longitude!!)) }

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = Result.success(photos.values.toList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = unsupported()
    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> = unsupported()
    override suspend fun delete(id: String): Result<Unit> = unsupported()
    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> = unsupported()
    override suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit> = unsupported()
    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> = unsupported()
    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> = unsupported()
    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> {
        deletedIds += photoId
        if (deleteShouldFail) return Result.failure(RuntimeException("delete failed"))
        photos.remove(photoId)
        return Result.success(Unit)
    }

    private fun unsupported(): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this test's path"))
}

private object SucceedingPhotoStore : PhotoStore {
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun delete(photo: LogPhoto): Result<Unit> = Result.success(Unit)
}

/** Offline regions kept in memory; [circles] is what the map's read makes of them, and every [deleteRegion] call is recorded. */
private class RegionStore(initial: List<Long>, private val deleteShouldFail: Boolean) : OfflineMapRepository {
    private val regions = initial.map {
        OfflineRegionSummary(
            id = it, name = "region $it", region = Region(45.3, -122.6, 10), minZoom = 8.0, maxZoom = 12.0,
            tileCount = 1, sizeBytes = 1L, createdAtEpochMillis = 1L,
        )
    }.associateByTo(LinkedHashMap()) { it.id }
    val deletedIds = mutableListOf<Long>()

    fun circles(): List<RecordRegion> = regions.values.map { RecordRegion(it.id.toString(), it.region) }

    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun deleteRegion(id: Long): Result<Unit> {
        deletedIds += id
        if (deleteShouldFail) return Result.failure(RuntimeException("delete failed"))
        regions.remove(id)
        return Result.success(Unit)
    }
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions.values.toList())
}

private object SibNoLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object SibNoLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object SibNoMushroomData : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) = Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object SibNoWeather : WeatherProvider, TripPlanningWeatherProvider, HistoricalWeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("not part of this test's path"))
}

private object SibNoTrips : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun delete(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this test's path"))
}

private object SibNoMapPreferences : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.success(false)
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.success(Unit)
}

private object SibImperialUnits : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object SibLightTheme : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
