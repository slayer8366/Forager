package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.routenav.RouteNavigationCurrent
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
 * Dispatch 2026-11-03-03 (A4, A8): the route being navigated and whether a return was paused for it, and each route's own next
 * stop, through a real Jetpack DataStore, as [DataStoreWaypointNavigationRepositoryTest] does for the waypoint's. The
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
    fun `an untouched install has no route navigation and no route progress`() = runTest {
        val (repository, _) = repository()
        assertNull(repository.getCurrent().getOrThrow())
        assertNull(repository.getProgress("trip-1").getOrThrow())
    }

    @Test
    fun `the current trip and its paused return round-trip, and a second write replaces the first`() = runTest {
        val (repository, _) = repository()

        assertTrue(repository.setCurrent(RouteNavigationCurrent("trip-1", resumesReturn = true)).isSuccess)
        assertEquals(RouteNavigationCurrent("trip-1", resumesReturn = true), repository.getCurrent().getOrThrow())

        assertTrue(repository.setCurrent(RouteNavigationCurrent("trip-2", resumesReturn = false)).isSuccess)
        assertEquals(RouteNavigationCurrent("trip-2", resumesReturn = false), repository.getCurrent().getOrThrow())
    }

    @Test
    fun `each route keeps its own next stop`() = runTest {
        val (repository, _) = repository()
        repository.setProgress("trip-1", 2).getOrThrow()
        repository.setProgress("trip-2", 1).getOrThrow()

        assertEquals(2, repository.getProgress("trip-1").getOrThrow())
        assertEquals(1, repository.getProgress("trip-2").getOrThrow())
        assertNull("a route never navigated has none", repository.getProgress("trip-3").getOrThrow())
    }

    @Test
    fun `ending the navigation keeps the route's next stop, and clearing a route's progress keeps the others`() = runTest {
        val (repository, _) = repository()
        repository.setCurrent(RouteNavigationCurrent("trip-1", resumesReturn = false)).getOrThrow()
        repository.setProgress("trip-1", 2).getOrThrow()
        repository.setProgress("trip-2", 1).getOrThrow()

        repository.setCurrent(null).getOrThrow()
        assertNull(repository.getCurrent().getOrThrow())
        assertEquals("Stop leaves the route's next stop", 2, repository.getProgress("trip-1").getOrThrow())

        repository.setProgress("trip-1", null).getOrThrow()
        assertNull(repository.getProgress("trip-1").getOrThrow())
        assertEquals(1, repository.getProgress("trip-2").getOrThrow())
    }

    @Test
    fun `the app opened again reads what it wrote before it was closed`() = runTest {
        val (before, beforeScope) = repository()
        before.setCurrent(RouteNavigationCurrent("trip-1", resumesReturn = true)).getOrThrow()
        before.setProgress("trip-1", 3).getOrThrow()
        beforeScope.cancelAndJoin()

        val (after, _) = repository()

        assertEquals(RouteNavigationCurrent("trip-1", resumesReturn = true), after.getCurrent().getOrThrow())
        assertEquals(3, after.getProgress("trip-1").getOrThrow())
    }

    @Test
    fun `it is kept in its own file, under its own keys`() = runTest {
        val (writer, writerScope) = repository()
        writer.setCurrent(RouteNavigationCurrent("trip-1", resumesReturn = true)).getOrThrow()
        writer.setProgress("trip-1", 2).getOrThrow()
        writerScope.cancelAndJoin()

        val prefs = rawStore().data.first()

        assertEquals("trip-1", prefs[stringPreferencesKey("route_navigation.trip_id")])
        assertEquals(true, prefs[booleanPreferencesKey("route_navigation.resumes_return")])
        assertEquals(2, prefs[intPreferencesKey("route_navigation.progress.trip-1")])
        assertEquals("nothing else is kept", 3, prefs.asMap().size)
    }

    @Test
    fun `a trip kept without its return flag is a failed read, not a guessed flag`() = runTest {
        rawStore().edit { it[stringPreferencesKey("route_navigation.trip_id")] = "trip-1" }
        scopes.last().cancelAndJoin()

        val read = repository().first.getCurrent()

        assertTrue("read: $read", read.isFailure)
        assertEquals("route navigation 'trip-1' was kept without its return flag", read.exceptionOrNull()?.message)
    }

    @Test
    fun `a negative index is refused, written or read`() = runTest {
        val (repository, scope) = repository()
        assertTrue(repository.setProgress("trip-1", -1).isFailure)
        scope.cancelAndJoin()
        rawStore().edit { it[intPreferencesKey("route_navigation.progress.trip-1")] = -1 }
        scopes.last().cancelAndJoin()

        val read = repository().first.getProgress("trip-1")

        assertTrue("read: $read", read.isFailure)
        assertEquals("route 'trip-1' was kept at stop index -1", read.exceptionOrNull()?.message)
    }

    @Test
    fun `isolation between tests`() = runTest {
        val (repository, _) = repository()
        assertNull(repository.getCurrent().getOrThrow())
        assertNull(repository.getProgress("trip-1").getOrThrow())
    }
}
