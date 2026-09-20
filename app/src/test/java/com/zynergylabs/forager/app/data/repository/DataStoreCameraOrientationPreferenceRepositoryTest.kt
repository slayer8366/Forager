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

/** A real round trip through Jetpack DataStore, same shape and per-test file deletion as [DataStorePhotoLocationPreferenceRepositoryTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreCameraOrientationPreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/camera_orientation_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    /** Off, so an install that predates the setting keeps the camera it had: controls that turn, sideways photos saved landscape. */
    @Test
    fun `an untouched install has the setting off`() = runTest {
        val repository = DataStoreCameraOrientationPreferenceRepository(context())

        assertEquals(false, repository.getLockCameraToPortrait().getOrThrow())
        assertEquals("the constant the interface documents", false, DEFAULT_LOCK_CAMERA_TO_PORTRAIT)
    }

    @Test
    fun `the setting round-trips both ways`() = runTest {
        val repository = DataStoreCameraOrientationPreferenceRepository(context())

        assertTrue(repository.setLockCameraToPortrait(true).isSuccess)
        assertEquals(true, repository.getLockCameraToPortrait().getOrThrow())

        assertTrue(repository.setLockCameraToPortrait(false).isSuccess)
        assertEquals("a second write replaces the first", false, repository.getLockCameraToPortrait().getOrThrow())
    }

    /** `true` is the value that matters here, the one the user chose; it must be distinguishable from never set, which reads as off. */
    @Test
    fun `an on value is not confused with never having been set`() = runTest {
        val repository = DataStoreCameraOrientationPreferenceRepository(context())
        assertEquals("precondition: never set reads as off", false, repository.getLockCameraToPortrait().getOrThrow())

        repository.setLockCameraToPortrait(true).getOrThrow()

        assertEquals(true, repository.getLockCameraToPortrait().getOrThrow())
    }
}
