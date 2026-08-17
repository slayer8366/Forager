package com.forager.app

import android.content.Context
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.data.remote.INaturalistClient
import com.forager.app.data.remote.OpenMeteoClient
import com.forager.app.data.repository.INaturalistMushroomRepository
import com.forager.app.data.repository.OpenMeteoWeatherProvider
import com.forager.app.data.repository.RoomPlannedTripRepository
import com.forager.app.data.repository.RoomSearchCacheRepository
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchCacheRepository
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.SystemCurrentTimeProvider
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.location.AndroidLocationProvider
import com.forager.app.map.OsmdroidOfflineMapRepository

/** Hand-wired dependency graph. No DI framework: the graph is small enough not to need one. */
class AppContainer(context: Context) {
    private val api = INaturalistClient.create(debug = BuildConfig.DEBUG)
    private val weatherApi = OpenMeteoClient.create(debug = BuildConfig.DEBUG)

    val mushroomRepository: MushroomRepository = INaturalistMushroomRepository(api)

    // One object, two owned interfaces, one API call behind both — see
    // TripPlanningWeatherProvider's doc comment for why they are separate interfaces.
    private val openMeteo = OpenMeteoWeatherProvider(weatherApi)
    val weatherProvider: WeatherProvider = openMeteo
    val tripPlanningWeatherProvider: TripPlanningWeatherProvider = openMeteo
    val locationProvider: LocationProvider = AndroidLocationProvider(context.applicationContext)

    /**
     * The one clock the app reads. Injected rather than called inline so the search cache's LRU
     * stamps and the relative times rendered from them are controllable in tests — see
     * [CurrentTimeProvider].
     */
    val currentTimeProvider: CurrentTimeProvider = SystemCurrentTimeProvider

    private val predictAvailabilityUseCase = PredictAvailabilityUseCase(mushroomRepository)
    val getSightingsUseCase = GetSightingsUseCase(mushroomRepository)
    val searchTaxaUseCase = SearchTaxaUseCase(mushroomRepository)
    val getConditionsUseCase = GetConditionsUseCase(weatherProvider)
    val getTripWindowsUseCase = GetTripWindowsUseCase(
        tripPlanningWeatherProvider,
        ComputeTripWindowsUseCase(),
    )

    // No repository dependency: clustering is a pure transform of sightings already fetched.
    val clusterForagingAreasUseCase = ClusterForagingAreasUseCase()

    private val database = ForagerDatabase.create(context)
    val plannedTripRepository: PlannedTripRepository = RoomPlannedTripRepository(database.plannedTripDao())
    val getPlannedTripsUseCase = GetPlannedTripsUseCase(plannedTripRepository)
    val savePlannedTripUseCase = SavePlannedTripUseCase(plannedTripRepository)
    val deletePlannedTripUseCase = DeletePlannedTripUseCase(plannedTripRepository)

    // The ranked-list search the ViewModel actually calls: PredictAvailabilityUseCase wrapped in
    // the cache read/write-through, so the raw one is not reachable from the UI by accident.
    val searchCacheRepository: SearchCacheRepository =
        RoomSearchCacheRepository(database.cachedSearchDao(), currentTimeProvider)
    val getAvailabilityUseCase = GetAvailabilityUseCase(predictAvailabilityUseCase, searchCacheRepository)
    val getRecentSearchesUseCase = GetRecentSearchesUseCase(searchCacheRepository)

    val offlineMapRepository: OfflineMapRepository = OsmdroidOfflineMapRepository(context)
}
