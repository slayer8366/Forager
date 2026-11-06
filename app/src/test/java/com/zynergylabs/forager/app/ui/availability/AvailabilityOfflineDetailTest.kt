package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.InMemorySearchCacheRepository
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.NoEntryReferenceCounts
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.mapdata.AreaDetail
import com.zynergylabs.forager.app.domain.mapdata.TrailMapDetail
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Known bug 14 (dispatch 2026-11-02-02, A6 and A9, B2): the picker's detail check through the real [AvailabilityViewModel]'s own
 * entry points, `onOfflineAreaSettled` and `onOfflineMapsSignalReturned`, with the map server behind [TrailMapDetail] and the
 * clock virtual. Made-up points.
 */
class AvailabilityOfflineDetailTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val areaA = Region(41.0, -102.0, 10)
    private val areaB = Region(43.0, -104.0, 10)
    private val server = ScriptedDetail()
    private val logs = mutableListOf<String>()
    private var noSignal = false

    private val viewModel: AvailabilityViewModel by lazy {
        val searchCache = InMemorySearchCacheRepository()
        AvailabilityViewModel(
            locationProvider = object : LocationProvider {
                override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
            },
            locationTracker = object : LocationTracker {
                override val fixes: Flow<LocationFix> = emptyFlow()
            },
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(EmptySightings), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(EmptySightings),
            searchTaxa = SearchTaxaUseCase(EmptySightings),
            getConditions = GetConditionsUseCase(RigNoWeather),
            getTripWindows = GetTripWindowsUseCase(RigNoWeather, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(RigNoTrips),
            savePlannedTrip = SavePlannedTripUseCase(RigNoTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(RigNoTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(GetSightingsUseCase(EmptySightings), RigNoWeather, ComputeFruitingLagDistributionUseCase()),
            offlineMapRepository = RigNoOfflineMaps,
            errorLog = ErrorLog { _, message, _ -> logs += message },
            mapPreferencesRepository = RigNoMapPreferences,
            unitSystemPreferenceRepository = RigMetricUnits,
            appThemePreferenceRepository = RigLightTheme,
            getTodaysForecast = GetTodaysForecastUseCase(RigNoWeather),
            entryReferenceCounts = NoEntryReferenceCounts,
            noSignal = { noSignal },
            trailMapDetail = server,
        )
    }

    private fun detailLogs() = logs.filter { it.startsWith("Offline maps detail check") }

    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `an area is asked about 750 ms after it settles, and the answer is written for that area`() = runTest(dispatcher) {
        server.answer = { AreaDetail.SomeOutside }
        viewModel.onOfflineAreaSettled(areaA)
        elapse(749)
        assertEquals("nothing asked inside the wait", emptyList<GeoBoundingBox>(), server.asked)
        elapse(2)
        assertEquals("the download's own rectangle is asked about", listOf(routeAreaFor(areaA)), server.asked)
        assertEquals(OfflineDetailAnswer(areaA, someOutside = true), viewModel.uiState.value.offlineDetail)
    }

    @Test
    fun `all inside is written as no line for that area`() = runTest(dispatcher) {
        server.answer = { AreaDetail.AllInside }
        viewModel.onOfflineAreaSettled(areaA)
        elapse(800)
        assertEquals(OfflineDetailAnswer(areaA, someOutside = false), viewModel.uiState.value.offlineDetail)
    }

    @Test
    fun `latest wins - a second area inside the wait replaces the first, which is never asked`() = runTest(dispatcher) {
        viewModel.onOfflineAreaSettled(areaA)
        elapse(500)
        viewModel.onOfflineAreaSettled(areaB)
        elapse(800)
        assertEquals(listOf(routeAreaFor(areaB)), server.asked)
        assertEquals(areaB, viewModel.uiState.value.offlineDetail?.area)
    }

    @Test
    fun `a newer area cancels a check still running, whose answer never lands`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        server.gate = gate
        server.answer = { box -> if (box == routeAreaFor(areaA)) AreaDetail.SomeOutside else AreaDetail.AllInside }
        viewModel.onOfflineAreaSettled(areaA)
        elapse(800)
        assertEquals("positive control: A is being asked about", listOf(routeAreaFor(areaA)), server.asked)
        viewModel.onOfflineAreaSettled(areaB)
        elapse(800)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(routeAreaFor(areaA), routeAreaFor(areaB)), server.asked)
        assertEquals(OfflineDetailAnswer(areaB, someOutside = false), viewModel.uiState.value.offlineDetail)
        assertEquals("a check replaced by a newer area is not a failure", emptyList<String>(), detailLogs())
    }

    @Test
    fun `no answer within 8 s - not run, one log line, no line`() = runTest(dispatcher) {
        server.gate = CompletableDeferred()
        viewModel.onOfflineAreaSettled(areaA)
        elapse(8_749) // the 750 ms wait, then 7.999 s of the check
        assertEquals("positive control: still waiting at 7.999 s", emptyList<String>(), detailLogs())
        elapse(2)
        assertEquals(listOf("Offline maps detail check not run: no answer within 8 s."), detailLogs())
        assertNull(viewModel.uiState.value.offlineDetail)
    }

    @Test
    fun `no signal - nothing asked, no line, and one log line saying why`() = runTest(dispatcher) {
        noSignal = true
        viewModel.onOfflineAreaSettled(areaA)
        elapse(10_000)
        assertEquals(emptyList<GeoBoundingBox>(), server.asked)
        assertNull(viewModel.uiState.value.offlineDetail)
        assertEquals(listOf("Offline maps detail check not run: no signal."), detailLogs())
    }

    @Test
    fun `a check not run clears an earlier answer for the same area, and logs its reason once`() = runTest(dispatcher) {
        server.answer = { AreaDetail.SomeOutside }
        viewModel.onOfflineAreaSettled(areaA)
        elapse(800)
        assertEquals("positive control", OfflineDetailAnswer(areaA, someOutside = true), viewModel.uiState.value.offlineDetail)
        server.answer = { AreaDetail.NotRun("1 of 16 zoom-11 tiles could not be read (the first: HTTP 503), and none answered without detail") }
        viewModel.onOfflineAreaSettled(areaA)
        elapse(800)
        assertNull("no line from the earlier answer in place of the one that did not come", viewModel.uiState.value.offlineDetail)
        assertEquals(
            listOf("Offline maps detail check not run: 1 of 16 zoom-11 tiles could not be read (the first: HTTP 503), and none answered without detail."),
            detailLogs(),
        )
    }

    @Test
    fun `an answer already known is written at once, with no wait and no request`() = runTest(dispatcher) {
        server.knownAnswer = { AreaDetail.SomeOutside }
        viewModel.onOfflineAreaSettled(areaA)
        runCurrent()
        assertEquals(OfflineDetailAnswer(areaA, someOutside = true), viewModel.uiState.value.offlineDetail)
        elapse(10_000)
        assertEquals(emptyList<GeoBoundingBox>(), server.asked)
    }

    @Test
    fun `signal coming back asks about the last area, which could not be asked without it`() = runTest(dispatcher) {
        server.answer = { AreaDetail.SomeOutside }
        noSignal = true
        viewModel.onOfflineAreaSettled(areaA)
        elapse(800)
        assertEquals("positive control: not asked without signal", emptyList<GeoBoundingBox>(), server.asked)
        noSignal = false
        viewModel.onOfflineMapsSignalReturned()
        elapse(800)
        assertEquals(listOf(routeAreaFor(areaA)), server.asked)
        assertEquals(OfflineDetailAnswer(areaA, someOutside = true), viewModel.uiState.value.offlineDetail)
    }
}

/** The map server as the test scripts it: every area asked about is recorded, and waits for [gate] when one is set. */
private class ScriptedDetail : TrailMapDetail {
    val asked = mutableListOf<GeoBoundingBox>()
    var answer: (GeoBoundingBox) -> AreaDetail = { AreaDetail.AllInside }
    var knownAnswer: (GeoBoundingBox) -> AreaDetail? = { null }
    var gate: CompletableDeferred<Unit>? = null

    override fun known(area: GeoBoundingBox): AreaDetail? = knownAnswer(area)

    override suspend fun check(area: GeoBoundingBox): AreaDetail {
        asked += area
        gate?.await()
        return answer(area)
    }
}

private object EmptySightings : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) = Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) = Result.success(SightingsPage(sightings = emptyList(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}
