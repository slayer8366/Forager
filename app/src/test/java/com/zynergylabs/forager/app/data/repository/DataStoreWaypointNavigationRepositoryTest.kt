package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.NavigationTargetKind
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
import org.junit.Assert.assertFalse
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

    // ── Dispatch 2026-11-05-05, Amendment 1, A8: a trip is navigated to as itself, and the kept navigation says which ──

    private val toTrip = WaypointNavigation("trip-1", resumesReturn = false, kind = NavigationTargetKind.TRIP)

    /** The file as another build left it, written straight through DataStore, then let go so a repository can open it. */
    private suspend fun writeRaw(vararg entries: Pair<String, Any>) {
        val rawScope = SupervisorJob().also { scopes += it }
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawScope)) { dataStoreFile() }.edit { prefs ->
            entries.forEach { (key, value) ->
                when (value) {
                    is String -> prefs[stringPreferencesKey(key)] = value
                    is Boolean -> prefs[booleanPreferencesKey(key)] = value
                    else -> error("not a kept value: $value")
                }
            }
        }
        rawScope.cancelAndJoin()
    }

    private suspend fun readRaw(): Map<String, Any> {
        val rawScope = SupervisorJob().also { scopes += it }
        val prefs = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawScope)) { dataStoreFile() }.data.first()
        return prefs.asMap().mapKeys { it.key.name }
    }

    @Test
    fun `a trip navigation round-trips as a trip, and the app opened again picks it back up as one`() = runTest {
        val (before, beforeScope) = repository()
        assertTrue(before.setCurrent(toTrip).isSuccess)
        assertEquals(toTrip, before.getCurrent().getOrThrow())
        beforeScope.cancelAndJoin()

        val (after, _) = repository()

        assertEquals(toTrip, after.getCurrent().getOrThrow())
    }

    @Test
    fun `a trip navigation is kept under a third key, its kind, beside the two a waypoint's has`() = runTest {
        val (writer, writerScope) = repository()
        writer.setCurrent(toTrip).getOrThrow()
        writerScope.cancelAndJoin()

        assertEquals(
            mapOf("waypoint_navigation.target_id" to "trip-1", "waypoint_navigation.resumes_return" to false, "waypoint_navigation.target_kind" to "trip"),
            readRaw(),
        )
    }

    @Test
    fun `a waypoint chosen after a trip leaves no kind behind, so it is kept exactly as before the update`() = runTest {
        val (writer, writerScope) = repository()
        writer.setCurrent(toTrip).getOrThrow()
        writer.setCurrent(WaypointNavigation("wp-1", resumesReturn = true)).getOrThrow()
        assertEquals(WaypointNavigation("wp-1", resumesReturn = true), writer.getCurrent().getOrThrow())
        writerScope.cancelAndJoin()

        val raw = readRaw()
        assertFalse("no kind key for a waypoint: $raw", "waypoint_navigation.target_kind" in raw)
        assertEquals(2, raw.size)
    }

    @Test
    fun `clearing a trip navigation leaves nothing behind, its kind included`() = runTest {
        val (writer, writerScope) = repository()
        writer.setCurrent(toTrip).getOrThrow()
        writer.setCurrent(null).getOrThrow()
        assertNull(writer.getCurrent().getOrThrow())
        writerScope.cancelAndJoin()

        assertEquals(emptyMap<String, Any>(), readRaw())
    }

    @Test
    fun `a navigation kept before the update, with no kind, is picked back up as a waypoint`() = runTest {
        writeRaw("waypoint_navigation.target_id" to "wp-1", "waypoint_navigation.resumes_return" to true)

        val (repository, _) = repository()

        assertEquals(WaypointNavigation("wp-1", resumesReturn = true, kind = NavigationTargetKind.WAYPOINT), repository.getCurrent().getOrThrow())
    }

    @Test
    fun `a kind this build does not know is refused as a failed read, not guessed`() = runTest {
        writeRaw("waypoint_navigation.target_id" to "x-1", "waypoint_navigation.resumes_return" to false, "waypoint_navigation.target_kind" to "boat")

        val (repository, _) = repository()

        val read = repository.getCurrent()
        assertTrue("refused: $read", read.isFailure)
    }

    @Test
    fun `isolation between tests`() = runTest {
        // Runs in any order with the others: a value left by another test would show here.
        assertNull(repository().first.getCurrent().getOrThrow())
    }
}
