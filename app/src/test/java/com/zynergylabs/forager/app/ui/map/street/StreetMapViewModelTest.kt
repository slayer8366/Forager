package com.zynergylabs.forager.app.ui.map.street

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.availability.InMemoryBasemapPreference
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The Street map's stored choice at the ViewModel (dispatch 2026-11-03-05 A2, D7): "street" opens on it, picking it stores
 * "street". Moved from `AvailabilityViewModelBasemapTest` ("a stored OpenStreetMap opens on OSM", "picking OSM stores the
 * new key"), whose keys this dispatch changed.
 */
class StreetMapViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }

    @Test
    fun `a stored street opens on the Street map, stores nothing and logs nothing`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference(stored = "street")
        val vm = mapLayersViewModel(errorLog = errorLog, basemapPreferences = preferences)
        advanceUntilIdle()

        assertEquals(StreetMap.mapMode, vm.uiState.value.mapMode)
        assertEquals(emptyList<String>(), preferences.writes)
        assertEquals(emptyList<String>(), logged.filter { "basemap" in it })
    }

    @Test
    fun `picking the Street map stores street`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference(stored = "trail")
        val vm = mapLayersViewModel(errorLog = errorLog, basemapPreferences = preferences)
        advanceUntilIdle()

        vm.onMapModeSelected(StreetMap.mapMode)
        advanceUntilIdle()

        assertEquals(StreetMap.mapMode, vm.uiState.value.mapMode)
        assertEquals(listOf("street"), preferences.writes)
    }
}
