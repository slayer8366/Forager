package com.forager.app.ui.availability

import com.forager.app.data.remote.INaturalistApi
import com.forager.app.data.remote.dto.ObservationDto
import com.forager.app.data.remote.dto.ObservationsResponseDto
import com.forager.app.data.remote.dto.SpeciesCountDto
import com.forager.app.data.remote.dto.SpeciesCountsResponseDto
import com.forager.app.data.remote.dto.TaxaAutocompleteResponseDto
import com.forager.app.data.remote.dto.TaxonDto
import com.forager.app.data.repository.INaturalistMushroomRepository
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.AppThemePreferenceRepository
import com.forager.app.domain.DistanceUnitPreferenceRepository
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTodaysForecastUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.InMemorySearchCacheRepository
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.WeatherSeries
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end headless cover of the lichen exclusion: a real chip tap on the real
 * [AvailabilityViewModel], through the real repository and use cases, asserting on the
 * ranked list the UI would render.
 *
 * The repository-level test proves the right query parameter is sent. This proves the
 * parameter actually changes what the user sees, which is the claim the change is really
 * making. It needs no emulator — the ViewModel is Android-framework-free apart from
 * [androidx.lifecycle.viewModelScope], which `Dispatchers.setMain` is enough to satisfy.
 *
 * [LichenAwareApi] is seeded with the real taxa, IDs and counts from the search that
 * prompted this change, and honours `without_taxon_id` the way the live API was observed
 * to: by dropping Lecanoromycetes and its descendants. That behaviour is copied from a
 * verified live-API result (species_counts 152 -> 113, observations 462 -> 358), not
 * assumed — a fake that simply echoed the expectation back would prove nothing.
 */
private const val LECANOROMYCETES = 54743L

private data class FakeTaxon(val id: Long, val name: String, val common: String, val count: Int, val isLichen: Boolean)

/** The real top of the reported Fungi search: three of the top five were lichens. */
private val REFERENCE_TAXA = listOf(
    FakeTaxon(48473L, "Ganoderma applanatum", "artist's bracket", 14, isLichen = false),
    FakeTaxon(55576L, "Xanthoria parietina", "Common Sunburst Lichen", 10, isLichen = true),
    FakeTaxon(182631L, "Phlyctis argena", "Whitewash Lichen", 8, isLichen = true),
    FakeTaxon(120242L, "Cyathus striatus", "fluted bird's nest fungus", 7, isLichen = false),
    FakeTaxon(54734L, "Parmelia sulcata", "Netted shield lichen", 6, isLichen = true),
    FakeTaxon(136399L, "Pleurotus pulmonarius", "pale oyster", 5, isLichen = false),
)

private class LichenAwareApi : INaturalistApi {

    /**
     * Mirrors the live API: `without_taxon_id` drops that taxon and its descendants,
     * `taxon_id` selects them. Anything else is returned whole.
     */
    private fun visible(taxonId: Long?, withoutTaxonId: Long?): List<FakeTaxon> = REFERENCE_TAXA
        .filter { if (withoutTaxonId == LECANOROMYCETES) !it.isLichen else true }
        .filter { if (taxonId == LECANOROMYCETES) it.isLichen else true }

    override suspend fun getSpeciesCounts(
        lat: Double,
        lng: Double,
        radiusKm: Int,
        month: Int,
        iconicTaxa: String?,
        taxonId: Long?,
        withoutTaxonId: Long?,
        verifiable: Boolean,
        perPage: Int,
    ): SpeciesCountsResponseDto {
        val taxa = visible(taxonId, withoutTaxonId)
        return SpeciesCountsResponseDto(
            totalResults = taxa.size,
            results = taxa.map { SpeciesCountDto(count = it.count, taxon = TaxonDto(id = it.id, name = it.name, preferredCommonName = it.common)) },
        )
    }

    override suspend fun getObservations(
        lat: Double,
        lng: Double,
        radiusKm: Int,
        month: Int,
        iconicTaxa: String?,
        taxonId: Long?,
        withoutTaxonId: Long?,
        verifiable: Boolean,
        perPage: Int,
    ): ObservationsResponseDto {
        val taxa = visible(taxonId, withoutTaxonId)
        return ObservationsResponseDto(
            totalResults = taxa.size,
            results = taxa.mapIndexed { index, taxon ->
                ObservationDto(
                    id = taxon.id,
                    taxon = TaxonDto(id = taxon.id, name = taxon.name, preferredCommonName = taxon.common),
                    location = "45.${300 + index},-122.634",
                    observedOn = "2025-08-14",
                )
            },
        )
    }

    override suspend fun searchTaxa(query: String, perPage: Int) = TaxaAutocompleteResponseDto()
}

private class FixedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation() = LocationResult.Success(lat = 45.326, lng = -122.634)
}

private object FilterTestNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private class StubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 12.0, daysSinceSignificantRain = 2))
}

/** Not exercised by this test's assertions, so a failure is the honest, low-effort stand-in. */
private object StubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

/** Not exercised by this test's assertions, so a failure is the honest, low-effort stand-in. */
private object StubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

/** Not exercised by this test's assertions; empty rather than failing, since it loads on every ViewModel init. */
private object StubPlannedTripRepository : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
    override suspend fun delete(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
}

/** Not exercised by this test's assertions; getStatus() succeeds with "nothing downloaded" since it runs on every ViewModel init. */
private object StubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object StubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
}

private object StubDistanceUnitPreferenceRepository : DistanceUnitPreferenceRepository {
    override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(DistanceUnit.MILES)
    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = Result.success(Unit)
}

private object StubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}

class AvailabilityViewModelFilterTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The offline search cache is not what this file is about. An empty in-memory one keeps the
     * ViewModel's new dependency real — every search still writes through it — without changing
     * anything these tests assert; the cache's own behaviour is covered by
     * `RoomSearchCacheRepositoryTest` and `GetAvailabilityUseCaseTest`.
     */
    private val searchCache = InMemorySearchCacheRepository()

    private fun viewModel(): AvailabilityViewModel {
        val repository = INaturalistMushroomRepository(LichenAwareApi())
        return AvailabilityViewModel(
            locationProvider = FixedLocationProvider(),
            locationTracker = FilterTestNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(repository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(repository),
            searchTaxa = SearchTaxaUseCase(repository),
            getConditions = GetConditionsUseCase(StubWeatherProvider()),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(StubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(StubPlannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(StubPlannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(StubPlannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(repository),
                StubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = StubOfflineMapRepository,
            mapPreferencesRepository = StubMapPreferencesRepository,
            distanceUnitPreferenceRepository = StubDistanceUnitPreferenceRepository,
            appThemePreferenceRepository = StubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(StubTripPlanningWeatherProvider),
        )
    }

    /** Drives the real coordinate-entry callbacks rather than reaching into state. */
    private fun AvailabilityViewModel.searchReferenceRegion() {
        onManualLatChanged("45.326")
        onManualLngChanged("-122.634")
        searchManualCoordinates()
    }

    @Test
    fun `the Fungi ranked list contains no lichens`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.searchReferenceRegion()
        vm.onCategorySelected(TaxonFilter.FUNGI)
        advanceUntilIdle()

        val names = vm.uiState.value.forecast!!.entries.map { it.species.scientificName }
        assertEquals(listOf("Ganoderma applanatum", "Cyathus striatus", "Pleurotus pulmonarius"), names)
        assertFalse("Xanthoria parietina" in names)
        assertFalse("Phlyctis argena" in names)
        assertFalse("Parmelia sulcata" in names)
    }

    /**
     * The specific regression: before the change the #2 and #3 ranked species were lichens.
     * Asserting the top of the list, not just absence, is what makes this cover the ranking
     * the user actually reads.
     */
    @Test
    fun `the top of the Fungi list is a fungus at full relative likelihood`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.searchReferenceRegion()
        vm.onCategorySelected(TaxonFilter.FUNGI)
        advanceUntilIdle()

        val top = vm.uiState.value.forecast!!.entries.first()
        assertEquals("Ganoderma applanatum", top.species.scientificName)
        assertEquals(14, top.species.observationCount)
        assertEquals(1.0f, top.relativeLikelihood, 0.0001f)
        assertEquals("Cyathus striatus", vm.uiState.value.forecast!!.entries[1].species.scientificName)
    }

    /** Nothing became unreachable: the Lichens chip still returns the lichens Fungi dropped. */
    @Test
    fun `the Lichens chip still returns lichens`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.searchReferenceRegion()
        vm.onCategorySelected(TaxonFilter.LICHENS)
        advanceUntilIdle()

        val names = vm.uiState.value.forecast!!.entries.map { it.species.scientificName }
        assertEquals(listOf("Xanthoria parietina", "Phlyctis argena", "Parmelia sulcata"), names)
        assertEquals(TaxonFilter.LICHENS, vm.uiState.value.forecast!!.filter)
    }

    /** The map is a separate endpoint and a separate call site, so it gets its own cover. */
    @Test
    fun `the Fungi map sightings contain no lichens`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.searchReferenceRegion()
        vm.onCategorySelected(TaxonFilter.FUNGI)
        advanceUntilIdle()
        vm.onMapTabSelected()
        advanceUntilIdle()

        val sightings = vm.uiState.value.sightings
        assertTrue(sightings.isNotEmpty())
        assertEquals(
            listOf("Ganoderma applanatum", "Cyathus striatus", "Pleurotus pulmonarius"),
            sightings.map { it.scientificName }.distinct(),
        )
    }

    /**
     * Switching to Lichens and back must not leave the first result set behind — the map
     * caches per region+month+filter, so a stale cache would show lichens under Fungi.
     */
    @Test
    fun `switching Lichens then back to Fungi refetches without lichens`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.searchReferenceRegion()
        vm.onCategorySelected(TaxonFilter.LICHENS)
        advanceUntilIdle()
        vm.onMapTabSelected()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sightings.any { it.scientificName == "Xanthoria parietina" })

        vm.onCategorySelected(TaxonFilter.FUNGI)
        advanceUntilIdle()
        vm.onMapTabSelected()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.sightings.any { it.scientificName == "Xanthoria parietina" })
        assertEquals(TaxonFilter.FUNGI, vm.uiState.value.taxonFilter)
    }
}
