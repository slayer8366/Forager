package com.forager.app

import android.content.Context
import com.forager.app.data.remote.INaturalistClient
import com.forager.app.data.repository.INaturalistMushroomRepository
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.location.AndroidLocationProvider

/** Hand-wired dependency graph. No DI framework: the graph is small enough not to need one. */
class AppContainer(context: Context) {
    private val api = INaturalistClient.create(debug = BuildConfig.DEBUG)

    val mushroomRepository: MushroomRepository = INaturalistMushroomRepository(api)
    val locationProvider: LocationProvider = AndroidLocationProvider(context.applicationContext)
    val predictAvailabilityUseCase = PredictAvailabilityUseCase(mushroomRepository)
    val getSightingsUseCase = GetSightingsUseCase(mushroomRepository)
    val searchTaxaUseCase = SearchTaxaUseCase(mushroomRepository)
}
