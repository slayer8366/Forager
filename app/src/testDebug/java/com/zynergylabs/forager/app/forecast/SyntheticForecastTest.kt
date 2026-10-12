package com.zynergylabs.forager.app.forecast

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ForecastAvailability
import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCellsResult
import com.zynergylabs.forager.app.domain.parseForecastCells
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The debug build's synthetic forecast (map layers L0b, B6, as planner message 2 changed it): block
 * files in the real D55 format, deterministic, with some cells not applicable and some left out, for
 * the two groups the colour fields draw; and the Diagnostics switch that turns the store on, stored in
 * its own debug-only DataStore file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyntheticForecastTest {

    private val week = LocalDate.of(2026, 9, 28)
    private val block = ForecastBlock(45, -123)
    private val groups = COLOUR_FIELDS.map { it.group }.toSet()

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun switchFile() = File(context().filesDir, "datastore/debug_diagnostics_preferences.preferences_pb")
    private val scopes = mutableListOf<Job>()

    private fun store(): Pair<SyntheticForecastCellStore, Job> {
        val job = SupervisorJob().also { scopes += it }
        return SyntheticForecastCellStore(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before
    fun setUp() {
        switchFile().delete()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        switchFile().delete()
    }

    private fun isTenth(value: Double) = abs(value * 10 - (value * 10).roundToLong()) < 1e-6

    @Test
    fun `the same group, week and block always give the same file, and the two groups differ`() {
        val first = SyntheticForecastGenerator.blockGeoJson("chanterelles", week, block)

        assertEquals(first, SyntheticForecastGenerator.blockGeoJson("chanterelles", week, block))
        assertNotEquals(first, SyntheticForecastGenerator.blockGeoJson("chicken-of-the-woods", week, block))
        assertTrue("the file has cells", parseForecastCells(first).getOrThrow().cells.isNotEmpty())
    }

    @Test
    fun `every block file reads in the real format with nothing rejected, cells on tenths inside the block, every property filled`() {
        groups.forEach { group ->
            val parsed = parseForecastCells(SyntheticForecastGenerator.blockGeoJson(group, week, block)).getOrThrow()

            assertEquals(group, emptyList<Any>(), parsed.rejected)
            assertTrue(group, parsed.cells.isNotEmpty())
            parsed.cells.forEach { cell ->
                assertEquals(group, cell.group)
                assertEquals(week, cell.week)
                assertTrue("${cell.centre} on tenths", isTenth(cell.centre.lat) && isTenth(cell.centre.lng))
                assertEquals("${cell.centre} is in the block that holds its centre", block, ForecastBlock.containing(cell.centre))
                assertTrue("$cell", cell.uncertaintyLow <= cell.chance && cell.chance <= cell.uncertaintyHigh)
                assertTrue("$cell", cell.drivers.isNotEmpty() && cell.modelVersion.isNotBlank())
                assertTrue("$cell", cell.weatherThrough <= week.plusDays(6))
            }
        }
    }

    @Test
    fun `in every block some cells are not applicable and some are left out`() {
        listOf(block, ForecastBlock(-34, 150), ForecastBlock(0, 0)).forEach { b ->
            groups.forEach { group ->
                val cells = parseForecastCells(SyntheticForecastGenerator.blockGeoJson(group, week, b)).getOrThrow().cells
                assertTrue("$group $b: some of the 100 cells are left out (${cells.size})", cells.size in 1 until 100)
                assertTrue("$group $b: some cells carry applicable false", cells.any { !it.applicable })
                assertTrue("$group $b: most cells are scored", cells.count { it.applicable } > cells.size / 2)
            }
        }
    }

    @Test
    fun `the switch is off until turned on, and while off the store has no forecast data`() = runTest {
        val (store, _) = store()

        assertEquals(false, store.isEnabled().getOrThrow())
        assertEquals(ForecastAvailability.NoForecastData, store.availability(week))
        assertEquals(ForecastCellsResult.NoForecastData, store.cells("chanterelles", week, setOf(block)))
    }

    @Test
    fun `turned on, the two colour fields' groups have data for any block, read from the generator's files`() = runTest {
        val (store, _) = store()
        store.setEnabled(true).getOrThrow()

        assertEquals(ForecastAvailability.Groups(groups), store.availability(week))
        val result = store.cells("chanterelles", week, setOf(block, ForecastBlock(46, -123)))
        result as ForecastCellsResult.Cells
        val expected = listOf(block, ForecastBlock(46, -123)).flatMap {
            parseForecastCells(SyntheticForecastGenerator.blockGeoJson("chanterelles", week, it)).getOrThrow().cells
        }
        assertEquals(expected, result.cells)
        assertEquals(0, result.rejectedCount)
        assertEquals("a group the store does not hold", ForecastCellsResult.NoForecastData, store.cells("morels", week, setOf(block)))
    }

    @Test
    fun `the switch is stored as diagnostics-synthetic_forecast in debug_diagnostics_preferences and survives a recreated store`() = runTest {
        val (first, firstScope) = store()
        first.setEnabled(true).getOrThrow()
        firstScope.cancelAndJoin()

        val (second, secondScope) = store()
        assertEquals(true, second.isEnabled().getOrThrow())
        secondScope.cancelAndJoin()

        val rawScope = SupervisorJob().also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawScope)) { switchFile() }
        assertEquals(true, raw.data.first()[booleanPreferencesKey("diagnostics.synthetic_forecast")])
    }
}
