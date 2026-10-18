package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.map.MapMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The Maps tab's basemap at the ViewModel (dispatch 2026-09-28-708): restored at start, stored on every
 * pick, and every way the read can end releasing the map's style gate ([AvailabilityUiState.mapMode]
 * non-null). A fallback is logged (CLAUDE.md), asserted through a recording [ErrorLog]. The screen and
 * the real DataStore file are `AvailabilityScreenBasemapPersistenceTest`'s.
 */
class AvailabilityViewModelBasemapTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }

    private fun viewModel(preferences: BasemapPreferenceRepository) =
        mapLayersViewModel(errorLog = errorLog, basemapPreferences = preferences)

    @Test
    // Was "nothing stored opens on Topographical…", asserting MapMode.TOPOGRAPHIC; the opening map became Street (dispatch 2026-10-18-04, Amendment 2; RECORD -1297).
    fun `nothing stored opens on Street, stores nothing and logs nothing`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference(stored = null)
        val vm = viewModel(preferences)

        assertNull("the gate is closed until the read lands", vm.uiState.value.mapMode)
        advanceUntilIdle()

        assertEquals(MapMode.STREET, vm.uiState.value.mapMode)
        assertEquals(emptyList<String>(), preferences.writes)
        assertEquals(emptyList<String>(), logged.filter { "basemap" in it })
    }

    /** Someone who chose Topographical before the default changed keeps it (CLAUDE.md, UX defaults; RECORD -1297). */
    @Test
    fun `a stored Topographical opens on Topographical, stores nothing and logs nothing`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference(stored = "topographic")
        val vm = viewModel(preferences)
        advanceUntilIdle()

        assertEquals(MapMode.TOPOGRAPHIC, vm.uiState.value.mapMode)
        assertEquals(emptyList<String>(), preferences.writes)
        assertEquals(emptyList<String>(), logged.filter { "basemap" in it })
    }

    @Test
    fun `a stored Street opens on Street`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference(stored = "street")
        val vm = viewModel(preferences)
        advanceUntilIdle()

        assertEquals(MapMode.STREET, vm.uiState.value.mapMode)
        assertEquals(emptyList<String>(), preferences.writes)
    }

    @Test
    fun `a stored Satellite opens on Topographical, is logged, and Topographical is stored in its place`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference(stored = "satellite")
        val vm = viewModel(preferences)
        advanceUntilIdle()

        assertEquals(MapMode.TOPOGRAPHIC, vm.uiState.value.mapMode)
        assertEquals(listOf("topographic"), preferences.writes)
        assertEquals(
            listOf("The stored basemap \"satellite\" names no map type; opening on Topographical and storing that."),
            logged.filter { "basemap" in it },
        )
    }

    @Test
    // Was "a failed read opens on Topographical…": a failed read opens on the default, now Street (dispatch 2026-10-18-04, Amendment 2; RECORD -1297).
    fun `a failed read opens on Street, is logged, and stores nothing`() = runTest(dispatcher) {
        val preferences = object : BasemapPreferenceRepository {
            val writes = mutableListOf<String>()
            override suspend fun getBasemapKey(): Result<String?> = Result.failure(IllegalStateException("datastore unreadable"))
            override suspend fun setBasemapKey(key: String): Result<Unit> = Result.success(Unit).also { writes += key }
        }
        val vm = viewModel(preferences)
        advanceUntilIdle()

        assertEquals("the gate must not block forever on a failed read", MapMode.STREET, vm.uiState.value.mapMode)
        assertEquals("the stored value may be fine; nothing overwrites it", emptyList<String>(), preferences.writes)
        assertEquals(listOf("Couldn't read the stored basemap; opening on Street."), logged.filter { "basemap" in it })
    }

    @Test
    fun `a pick is shown and stored under its key`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference()
        val vm = viewModel(preferences)
        advanceUntilIdle()

        // Topographical, not Street, since Street became the opening map (Amendment 2): a pick away from what is shown.
        vm.onMapModeSelected(MapMode.TOPOGRAPHIC)
        assertEquals(MapMode.TOPOGRAPHIC, vm.uiState.value.mapMode)
        advanceUntilIdle()

        assertEquals(listOf("topographic"), preferences.writes)
    }

    @Test
    fun `a pick made before the read lands is not overwritten by it`() = runTest(dispatcher) {
        val preferences = InMemoryBasemapPreference(stored = "topographic")
        val vm = viewModel(preferences)

        vm.onMapModeSelected(MapMode.STREET)
        advanceUntilIdle()

        assertEquals(MapMode.STREET, vm.uiState.value.mapMode)
    }

    @Test
    fun `a failed write is logged and the pick still holds`() = runTest(dispatcher) {
        val preferences = object : BasemapPreferenceRepository {
            override suspend fun getBasemapKey(): Result<String?> = Result.success(null)
            override suspend fun setBasemapKey(key: String): Result<Unit> = Result.failure(IllegalStateException("disk full"))
        }
        val vm = viewModel(preferences)
        advanceUntilIdle()

        // Topographical, away from the opening Street (Amendment 2), so "the pick still holds" is not the default holding.
        vm.onMapModeSelected(MapMode.TOPOGRAPHIC)
        advanceUntilIdle()

        assertEquals(MapMode.TOPOGRAPHIC, vm.uiState.value.mapMode)
        assertEquals(listOf("Couldn't store the basemap choice."), logged.filter { "basemap" in it })
    }
}
