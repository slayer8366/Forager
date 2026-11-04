package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-03-05 B1 (RECORD -1999, -2000): the one-time move of the stored map choice, through the real
 * `map_preferences` file and the real [DataStoreMapPreferencesRepository]. A phone from before this update is made by
 * writing the file the way that build did, with no migration, then released; the repository is opened on it the way
 * the updated app opens it. Each starting value, no value and an unknown one, and the migration run twice (a second
 * repository on the same file, as a second launch).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BasemapKeyTrailStreetMigrationTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/map_preferences.preferences_pb")

    private val scopes = mutableListOf<Job>()

    private fun repository(): Pair<DataStoreMapPreferencesRepository, Job> {
        val job = SupervisorJob()
        scopes += job
        return DataStoreMapPreferencesRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    /** The file as a build before this update left it: [key] under `map.basemap`, nothing else, no migration run. */
    private suspend fun phoneFromBeforeTheUpdate(key: String?) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        store.edit { prefs ->
            if (key != null) prefs[stringPreferencesKey("map.basemap")] = key
            prefs[stringPreferencesKey("map.crowded_icons")] = "unchanged"
        }
        job.cancelAndJoin()
    }

    /** The updated app's first launch: what the repository reads, and the file released after. */
    private suspend fun firstLaunchReads(): String? {
        val (repository, scope) = repository()
        val read = repository.getBasemapKey().getOrThrow()
        scope.cancelAndJoin()
        return read
    }

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    @Test
    fun `the Trail Map's old key street becomes trail`() = runTest {
        phoneFromBeforeTheUpdate("street")
        assertEquals("trail", firstLaunchReads())
    }

    @Test
    fun `OpenStreetMap's old key becomes street, and does not go on to become trail`() = runTest {
        phoneFromBeforeTheUpdate("openstreetmap")
        assertEquals("street", firstLaunchReads())
    }

    @Test
    fun `topographic is left as it is`() = runTest {
        phoneFromBeforeTheUpdate("topographic")
        assertEquals("topographic", firstLaunchReads())
    }

    @Test
    fun `no stored choice stays no stored choice`() = runTest {
        phoneFromBeforeTheUpdate(null)
        assertNull(firstLaunchReads())
    }

    @Test
    fun `an unknown key is left for the existing logged replacement`() = runTest {
        phoneFromBeforeTheUpdate("satellite")
        assertEquals("satellite", firstLaunchReads())
    }

    @Test
    fun `run twice, a Street choice stays Street and a Trail choice stays Trail`() = runTest {
        phoneFromBeforeTheUpdate("openstreetmap")
        assertEquals("first launch", "street", firstLaunchReads())
        assertEquals("second launch: the migration does not run again", "street", firstLaunchReads())

        dataStoreFile().delete()
        phoneFromBeforeTheUpdate("street")
        assertEquals("first launch", "trail", firstLaunchReads())
        assertEquals("second launch", "trail", firstLaunchReads())
    }

    @Test
    fun `a Street choice made after the update is never moved`() = runTest {
        phoneFromBeforeTheUpdate("topographic")
        val (repository, scope) = repository()
        repository.setBasemapKey("street").getOrThrow()
        scope.cancelAndJoin()
        assertEquals("street", firstLaunchReads())
    }

    @Test
    fun `the migration touches only the map choice and records that it ran`() = runTest {
        phoneFromBeforeTheUpdate("street")
        firstLaunchReads()
        val job = SupervisorJob()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { dataStoreFile() }
        val prefs = raw.data.first()
        job.cancelAndJoin()
        assertEquals("unchanged", prefs[stringPreferencesKey("map.crowded_icons")])
        assertEquals(true, prefs[BASEMAP_KEYS_TRAIL_STREET_DONE])
        assertEquals("trail", prefs[BASEMAP_PREFERENCE_KEY])
    }
}
