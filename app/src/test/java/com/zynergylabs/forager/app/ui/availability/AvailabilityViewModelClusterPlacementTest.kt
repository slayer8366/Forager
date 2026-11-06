package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository
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
 * The icon cluster's stored side and height at the ViewModel (RECORD -711): `null` until the read lands
 * (the screen draws no cluster before), a real placement after it however it ends, and every ended drag
 * stored. Failures are logged (CLAUDE.md), asserted through a recording [ErrorLog]. The screen, the drags
 * and the real file are `AvailabilityScreenClusterPersistenceTest`'s.
 */
class AvailabilityViewModelClusterPlacementTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }
    private val leftAndLow = MapIconClusterPlacement(portraitOnLeft = true, portraitOffsetDp = 120f, landscapeOnPortSide = true, landscapeOffsetDp = -40f)

    private fun viewModel(placements: MapIconClusterPlacementRepository) = mapLayersViewModel(errorLog = errorLog, clusterPlacements = placements)

    @Test
    fun `nothing stored opens where it always opened, after the read, storing nothing`() = runTest(dispatcher) {
        val placements = InMemoryClusterPlacement(stored = null)
        val vm = viewModel(placements)

        assertNull("held until the read lands", vm.uiState.value.mapIconClusterPlacement)
        advanceUntilIdle()

        assertEquals(MapIconClusterPlacement.DEFAULT, vm.uiState.value.mapIconClusterPlacement)
        assertEquals(emptyList<MapIconClusterPlacement>(), placements.writes)
    }

    @Test
    fun `a stored placement is restored`() = runTest(dispatcher) {
        val vm = viewModel(InMemoryClusterPlacement(stored = leftAndLow))
        advanceUntilIdle()

        assertEquals(leftAndLow, vm.uiState.value.mapIconClusterPlacement)
    }

    @Test
    fun `a failed read opens where it always opened, is logged, and stores nothing`() = runTest(dispatcher) {
        val writes = mutableListOf<MapIconClusterPlacement>()
        val vm = viewModel(object : MapIconClusterPlacementRepository {
            override suspend fun getMapIconClusterPlacement(): Result<MapIconClusterPlacement?> = Result.failure(IllegalStateException("unreadable"))
            override suspend fun setMapIconClusterPlacement(placement: MapIconClusterPlacement): Result<Unit> = Result.success(Unit).also { writes += placement }
        })
        advanceUntilIdle()

        assertEquals("never held hidden by a failed read", MapIconClusterPlacement.DEFAULT, vm.uiState.value.mapIconClusterPlacement)
        assertEquals(emptyList<MapIconClusterPlacement>(), writes)
        assertEquals(
            listOf("Couldn't read the map icon cluster's position; opening it where it always opened."),
            logged.filter { "icon cluster" in it },
        )
    }

    @Test
    fun `an ended drag is stored and kept in the state`() = runTest(dispatcher) {
        val placements = InMemoryClusterPlacement()
        val vm = viewModel(placements)
        advanceUntilIdle()

        vm.onMapIconClusterPlacementChanged(leftAndLow)
        advanceUntilIdle()

        assertEquals(listOf(leftAndLow), placements.writes)
        assertEquals("a recreated screen restores the latest, not the launch value", leftAndLow, vm.uiState.value.mapIconClusterPlacement)
    }

    @Test
    fun `a drag ended before the read lands is not overwritten by it`() = runTest(dispatcher) {
        val vm = viewModel(InMemoryClusterPlacement(stored = MapIconClusterPlacement.DEFAULT))

        vm.onMapIconClusterPlacementChanged(leftAndLow)
        advanceUntilIdle()

        assertEquals(leftAndLow, vm.uiState.value.mapIconClusterPlacement)
    }

    @Test
    fun `a failed write is logged and the placement still holds`() = runTest(dispatcher) {
        val vm = viewModel(object : MapIconClusterPlacementRepository {
            override suspend fun getMapIconClusterPlacement(): Result<MapIconClusterPlacement?> = Result.success(null)
            override suspend fun setMapIconClusterPlacement(placement: MapIconClusterPlacement): Result<Unit> = Result.failure(IllegalStateException("disk full"))
        })
        advanceUntilIdle()

        vm.onMapIconClusterPlacementChanged(leftAndLow)
        advanceUntilIdle()

        assertEquals(leftAndLow, vm.uiState.value.mapIconClusterPlacement)
        assertEquals(listOf("Couldn't store the map icon cluster's position."), logged.filter { "icon cluster" in it })
    }
}
