package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.WaypointNavigation
import java.io.File
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-502, Amendment 1 ("Pick navigation back up"): the waypoint being navigated to,
 * and whether a return was paused for it, through a real Jetpack DataStore, as
 * [DataStoreSundownPreferencesRepositoryTest] does. The backing file is deleted before and after every
 * test. The app opened again is a second repository on the same file, made once the first one's scope
 * is cancelled (DataStore refuses two live instances on one file, as [DataStoreMapLayerPreferencesTest]
 * does it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreWaypointNavigationRepositoryTest {

    private val scopes = mutableListOf<CompletableJob>()

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/waypoint_navigation.preferences_pb")

    private fun repository(): Pair<DataStoreWaypointNavigationRepository, CompletableJob> {
        val job = SupervisorJob().also { scopes += it }
        return DataStoreWaypointNavigationRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        runBlocking { scopes.forEach { it.cancelAndJoin() } }
        dataStoreFile().delete()
    }

    @Test
    fun `an untouched install has no navigation to pick back up`() = runTest {
        assertNull(repository().first.getCurrent().getOrThrow())
    }

    @Test
    fun `the waypoint and the paused return round-trip, both ways`() = runTest {
        val (repository, _) = repository()

        assertTrue(repository.setCurrent(WaypointNavigation("wp-1", resumesReturn = true)).isSuccess)
        assertEquals(WaypointNavigation("wp-1", resumesReturn = true), repository.getCurrent().getOrThrow())

        assertTrue(repository.setCurrent(WaypointNavigation("wp-2", resumesReturn = false)).isSuccess)
        assertEquals("a second write replaces the first", WaypointNavigation("wp-2", resumesReturn = false), repository.getCurrent().getOrThrow())
    }

    @Test
    fun `clearing it leaves nothing to pick back up`() = runTest {
        val (repository, _) = repository()
        repository.setCurrent(WaypointNavigation("wp-1", resumesReturn = true)).getOrThrow()

        assertTrue(repository.setCurrent(null).isSuccess)

        assertNull(repository.getCurrent().getOrThrow())
    }

    @Test
    fun `the app opened again reads what it wrote before it was closed`() = runTest {
        val (before, beforeScope) = repository()
        before.setCurrent(WaypointNavigation("wp-1", resumesReturn = true)).getOrThrow()
        beforeScope.cancelAndJoin()

        val (after, _) = repository()

        assertEquals(WaypointNavigation("wp-1", resumesReturn = true), after.getCurrent().getOrThrow())
    }

    @Test
    fun `it is kept in its own file, under its own two keys`() = runTest {
        val (writer, writerScope) = repository()
        writer.setCurrent(WaypointNavigation("wp-1", resumesReturn = true)).getOrThrow()
        writerScope.cancelAndJoin()

        val rawScope = SupervisorJob().also { scopes += it }
        val prefs = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawScope)) { dataStoreFile() }.data.first()

        assertEquals("wp-1", prefs[stringPreferencesKey("waypoint_navigation.target_id")])
        assertEquals(true, prefs[booleanPreferencesKey("waypoint_navigation.resumes_return")])
        assertEquals("nothing else is kept", 2, prefs.asMap().size)
    }

    @Test
    fun `isolation between tests`() = runTest {
        // Runs in any order with the others: a value left by another test would show here.
        assertNull(repository().first.getCurrent().getOrThrow())
    }
}
