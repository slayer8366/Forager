package com.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.model.Region
import java.io.File
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
 * A real round trip through Jetpack DataStore (Robolectric, not a fake) — the same "test the real
 * store, not a rewritten copy of it" discipline [com.forager.app.data.local.OfflineRegionMigrationTest]
 * uses for Room, applied to the other persistence mechanism this design doc's "Cold-start default"
 * and staleness setting depend on.
 *
 * [dataStoreFile] is deleted before and after every test, the same isolation
 * [com.forager.app.data.local.OfflineRegionMigrationTest] gives its Room file: `preferencesDataStore`
 * backs one file per name for the process's lifetime, so a leftover file from a previous test in
 * this class would otherwise leak a saved region or threshold into a test that never wrote one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreMapPreferencesRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/map_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    private fun repository() = DataStoreMapPreferencesRepository(context())

    @Test
    fun `nothing picked yet reads back null, not a fabricated region`() = runTest {
        assertNull(repository().getLastPickedRegion().getOrThrow())
    }

    @Test
    fun `a saved region round-trips exactly`() = runTest {
        val repository = repository()
        val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

        repository.setLastPickedRegion(region).getOrThrow()

        assertEquals(region, repository.getLastPickedRegion().getOrThrow())
    }

    @Test
    fun `saving a second region replaces the first`() = runTest {
        val repository = repository()
        repository.setLastPickedRegion(Region(lat = 45.326, lng = -122.634, radiusKm = 15)).getOrThrow()

        val second = Region(lat = 40.0, lng = -105.0, radiusKm = 25)
        repository.setLastPickedRegion(second).getOrThrow()

        assertEquals(second, repository.getLastPickedRegion().getOrThrow())
    }

    @Test
    fun `the stale threshold defaults until explicitly set`() = runTest {
        assertEquals(DEFAULT_STALE_THRESHOLD_DAYS, repository().getStaleThresholdDays().getOrThrow())
    }

    @Test
    fun `a saved stale threshold round-trips exactly`() = runTest {
        val repository = repository()

        repository.setStaleThresholdDays(30).getOrThrow()

        assertEquals(30, repository.getStaleThresholdDays().getOrThrow())
    }
}
