package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.data.repository.FakeCameraGridModeRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GridMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [CameraGridModeViewModel], headless: the grid mode it shows is the one the repository holds —
 * loaded at start, replaced only once a write has succeeded, and left alone (and logged) when a
 * read or a write fails. What the chip shows is this, so "the icon reflects the repository" is held
 * here and, through the real chip, in `CameraGridChipTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraGridModeViewModelTest {

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: FakeCameraGridModeRepository) =
        CameraGridModeViewModel(repository::getGridMode, repository::setGridMode, errorLog)

    @Test
    fun `the stored mode is what it shows from the start`() {
        assertEquals(GridMode.GridLevel, viewModel(FakeCameraGridModeRepository(GridMode.GridLevel)).mode.value)
    }

    @Test
    fun `a change is written, and shown once it is stored`() {
        val repository = FakeCameraGridModeRepository()
        val vm = viewModel(repository)

        vm.onGridModeChanged(GridMode.Grid)

        assertEquals("written", GridMode.Grid, repository.stored)
        assertEquals("and shown", GridMode.Grid, vm.mode.value)
    }

    @Test
    fun `a failed write leaves the shown mode as stored, and says so`() {
        val repository = FakeCameraGridModeRepository().apply { failWrites = true }
        val vm = viewModel(repository)

        vm.onGridModeChanged(GridMode.Grid)

        assertEquals("the write was attempted", 1, repository.writes)
        assertEquals("nothing stored, nothing shown", GridMode.Off, vm.mode.value)
        assertEquals("logged, not swallowed", 1, logged.size)
    }

    @Test
    fun `a failed read shows Off, and says so`() {
        val vm = viewModel(FakeCameraGridModeRepository(GridMode.Grid).apply { failReads = true })

        assertEquals(GridMode.Off, vm.mode.value)
        assertEquals("logged, not swallowed", 1, logged.size)
    }
}
