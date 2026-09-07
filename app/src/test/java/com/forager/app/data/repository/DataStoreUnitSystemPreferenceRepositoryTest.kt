package com.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.UnitSystem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real round trip through Jetpack DataStore (Robolectric, not a fake) — the same discipline the
 * distance-unit repository test this replaces applied, plus the one thing that repository never
 * had to do: read its predecessor's key. The legacy key is planted here with a separate DataStore
 * handle on the same file, exactly as the old repository would have written it, never through the
 * new repository's own API.
 *
 * [dataStoreFile] is deleted before and after every test for the isolation reason
 * [DataStoreMapPreferencesRepositoryTest] documents on itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreUnitSystemPreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/distance_unit_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    private fun repository() = DataStoreUnitSystemPreferenceRepository(context())

    /**
     * Writes the key the replaced distance-unit repository used, as that repository wrote it, then
     * releases the file: DataStore allows one active instance per file per process, and an
     * instance stays active until the scope it was created in completes — so the planting handle
     * gets its own job, cancelled and joined before the repository under test opens the same file.
     */
    private suspend fun plantLegacyDistanceUnit(name: String) {
        val job = Job()
        val legacy = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { context().preferencesDataStoreFile("distance_unit_preferences") },
        )
        legacy.edit { it[stringPreferencesKey("distance_unit.selected")] = name }
        job.cancelAndJoin()
    }

    @Test
    fun `nothing picked yet defaults to imperial`() = runTest {
        assertEquals(UnitSystem.IMPERIAL, repository().getUnitSystem().getOrThrow())
    }

    @Test
    fun `a saved system round-trips exactly`() = runTest {
        val repository = repository()

        repository.setUnitSystem(UnitSystem.METRIC).getOrThrow()

        assertEquals(UnitSystem.METRIC, repository.getUnitSystem().getOrThrow())
    }

    @Test
    fun `saving a second system replaces the first`() = runTest {
        val repository = repository()
        repository.setUnitSystem(UnitSystem.METRIC).getOrThrow()

        repository.setUnitSystem(UnitSystem.IMPERIAL).getOrThrow()

        assertEquals(UnitSystem.IMPERIAL, repository.getUnitSystem().getOrThrow())
    }

    @Test
    fun `a legacy kilometres choice is carried forward as metric`() = runTest {
        plantLegacyDistanceUnit("KILOMETERS")

        assertEquals(UnitSystem.METRIC, repository().getUnitSystem().getOrThrow())
    }

    @Test
    fun `a legacy miles choice is carried forward as imperial`() = runTest {
        plantLegacyDistanceUnit("MILES")

        assertEquals(UnitSystem.IMPERIAL, repository().getUnitSystem().getOrThrow())
    }

    @Test
    fun `once a system has been saved, the legacy key no longer has a say`() = runTest {
        plantLegacyDistanceUnit("KILOMETERS")
        val repository = repository()

        repository.setUnitSystem(UnitSystem.IMPERIAL).getOrThrow()

        assertEquals(UnitSystem.IMPERIAL, repository.getUnitSystem().getOrThrow())
    }
}
