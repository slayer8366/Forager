package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real round trip through Jetpack DataStore, not a fake — same shape and same per-test file
 * deletion as [DataStoreSundownPreferencesRepositoryTest], which records why the repository builds
 * its own `DataStore` per instance rather than using the process-wide delegate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStorePhotoLocationPreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/photo_location_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    /**
     * The owner's ruling: on, so an install that predates the setting keeps the behaviour it had.
     * A privacy toggle that silently defaults to off would take away the location features built
     * the day before without telling anyone.
     */
    @Test
    fun `an untouched install has the setting on`() = runTest {
        val repository = DataStorePhotoLocationPreferenceRepository(context())

        assertEquals(true, repository.getAutoSaveLocationToPhotos().getOrThrow())
        assertEquals("the constant the interface documents", true, DEFAULT_AUTO_SAVE_LOCATION_TO_PHOTOS)
    }

    @Test
    fun `the setting round-trips both ways`() = runTest {
        val repository = DataStorePhotoLocationPreferenceRepository(context())

        assertTrue(repository.setAutoSaveLocationToPhotos(false).isSuccess)
        assertEquals(false, repository.getAutoSaveLocationToPhotos().getOrThrow())

        assertTrue(repository.setAutoSaveLocationToPhotos(true).isSuccess)
        assertEquals("a second write replaces the first", true, repository.getAutoSaveLocationToPhotos().getOrThrow())
    }

    /**
     * `false` is the value that matters: it must be distinguishable from "never set", which reads
     * as `true`. A store that lost the write would read back on, and the user would go on having
     * positions captured after switching them off. The two reads below are the same instance,
     * deliberately — see the next test for why a second instance is not an option.
     */
    @Test
    fun `an off value is not confused with never having been set`() = runTest {
        val repository = DataStorePhotoLocationPreferenceRepository(context())
        assertEquals("precondition: never set reads as on", true, repository.getAutoSaveLocationToPhotos().getOrThrow())

        repository.setAutoSaveLocationToPhotos(false).getOrThrow()

        assertEquals(false, repository.getAutoSaveLocationToPhotos().getOrThrow())
    }

    /**
     * **A finding, not a design choice, and it constrains production.** This test was first written
     * as "an off value survives a fresh repository instance", constructing a second repository to
     * read back what the first wrote. It cannot work: DataStore throws
     * `IllegalStateException: There are multiple DataStores active for the same file` when a second
     * instance is created while the first is still alive, and nothing in this version exposes a
     * close. So cross-instance persistence is not testable in one method, and the claim above is
     * made against one instance instead rather than being quietly dropped.
     *
     * What it means for production: `AppContainer` must construct this repository exactly once,
     * which it does — a single `val` on a container that is itself one per process. A second
     * construction anywhere would throw at first use, not silently diverge, which is the safer of
     * the two failure modes but is worth knowing before someone adds one.
     */
    @Test
    fun `a second live instance on the same file is rejected by DataStore`() = runTest {
        val first = DataStorePhotoLocationPreferenceRepository(context())
        first.getAutoSaveLocationToPhotos().getOrThrow()

        val second = DataStorePhotoLocationPreferenceRepository(context())

        val failure = second.getAutoSaveLocationToPhotos().exceptionOrNull()
        assertTrue("expected a multiple-DataStores failure, got $failure", failure is IllegalStateException)
        assertTrue("and it should name the cause: $failure", failure!!.message.orEmpty().contains("multiple DataStores"))
    }

    @Test
    fun `isolation between tests`() = runTest {
        assertEquals("the file deletion in setUp must actually reset the store", true, DataStorePhotoLocationPreferenceRepository(context()).getAutoSaveLocationToPhotos().getOrThrow())
    }
}
