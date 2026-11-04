package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.routenav.RouteNavigation
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
 * Dispatch 2026-11-03-03 (A4, "Pick up where you left off"): the route being navigated, its next stop and whether a return was
 * paused for it, through a real Jetpack DataStore, as [DataStoreWaypointNavigationRepositoryTest] does for the waypoint's. The
 * backing file is deleted before and after every test; the app opened again is a second repository on the same file, made once
 * the first one's scope is cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreRouteNavigationRepositoryTest {

    private val scopes = mutableListOf<CompletableJob>()

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/route_navigation.preferences_pb")

    private fun repository(): Pair<DataStoreRouteNavigationRepository, CompletableJob> {
        val job = SupervisorJob().also { scopes += it }
        return DataStoreRouteNavigationRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    private fun rawStore(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        val rawScope = SupervisorJob().also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawScope)) { dataStoreFile() }
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
    fun `an untouched install has no route navigation to pick back up`() = runTest {
        assertNull(repository().first.getCurrent().getOrThrow())
    }

    @Test
    fun `the trip, its next stop and the paused return round-trip, and a second write replaces the first`() = runTest {
        val (repository, _) = repository()

        assertTrue(repository.setCurrent(RouteNavigation("trip-1", nextStopIndex = 2, resumesReturn = true)).isSuccess)
        assertEquals(RouteNavigation("trip-1", 2, resumesReturn = true), repository.getCurrent().getOrThrow())

        assertTrue(repository.setCurrent(RouteNavigation("trip-2", nextStopIndex = 0, resumesReturn = false)).isSuccess)
        assertEquals(RouteNavigation("trip-2", 0, resumesReturn = false), repository.getCurrent().getOrThrow())
    }

    @Test
    fun `clearing it leaves nothing to pick back up`() = runTest {
        val (repository, _) = repository()
        repository.setCurrent(RouteNavigation("trip-1", 1, resumesReturn = true)).getOrThrow()

        assertTrue(repository.setCurrent(null).isSuccess)

        assertNull(repository.getCurrent().getOrThrow())
    }

    @Test
    fun `the app opened again reads what it wrote before it was closed`() = runTest {
        val (before, beforeScope) = repository()
        before.setCurrent(RouteNavigation("trip-1", 3, resumesReturn = true)).getOrThrow()
        beforeScope.cancelAndJoin()

        val (after, _) = repository()

        assertEquals(RouteNavigation("trip-1", 3, resumesReturn = true), after.getCurrent().getOrThrow())
    }

    @Test
    fun `it is kept in its own file, under its own three keys`() = runTest {
        val (writer, writerScope) = repository()
        writer.setCurrent(RouteNavigation("trip-1", 2, resumesReturn = true)).getOrThrow()
        writerScope.cancelAndJoin()

        val prefs = rawStore().data.first()

        assertEquals("trip-1", prefs[stringPreferencesKey("route_navigation.trip_id")])
        assertEquals(2, prefs[intPreferencesKey("route_navigation.next_stop_index")])
        assertEquals(true, prefs[booleanPreferencesKey("route_navigation.resumes_return")])
        assertEquals("nothing else is kept", 3, prefs.asMap().size)
    }

    @Test
    fun `a trip kept without its next stop is a failed read, not a guessed stop`() = runTest {
        rawStore().edit {
            it[stringPreferencesKey("route_navigation.trip_id")] = "trip-1"
            it[booleanPreferencesKey("route_navigation.resumes_return")] = false
        }
        scopes.last().cancelAndJoin()

        val read = repository().first.getCurrent()

        assertTrue("read: $read", read.isFailure)
        assertEquals("route navigation 'trip-1' was kept without its next stop", read.exceptionOrNull()?.message)
    }

    @Test
    fun `isolation between tests`() = runTest {
        assertNull(repository().first.getCurrent().getOrThrow())
    }
}
