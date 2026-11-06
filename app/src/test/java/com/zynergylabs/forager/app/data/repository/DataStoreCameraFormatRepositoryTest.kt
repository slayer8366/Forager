package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraFormat
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
import org.robolectric.shadows.ShadowLog

/** The format chip's remembered choice (RECORD -1005), on disk, as `DataStoreCameraGridModeRepositoryTest` checks the grid mode's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreCameraFormatRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/camera_format_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    @Test
    fun `an untouched install takes JPG`() = runTest {
        assertEquals(CameraFormat.Jpg, DataStoreCameraFormatRepository(context()).getCameraFormat().getOrThrow())
        assertEquals("the constant the default is built from", CameraFormat.Jpg.name, DEFAULT_CAMERA_FORMAT_NAME)
    }

    @Test
    fun `each format round-trips through DataStore to a file on disk, and a second write replaces the first`() = runTest {
        // One repository per file per process, as DataStore requires (a second one on the same file throws); that the
        // file outlives the process is DataStore's own guarantee, so what is checked here is that the choice is on disk.
        val repository = DataStoreCameraFormatRepository(context())
        for (format in listOf(CameraFormat.Raw, CameraFormat.Jpg, CameraFormat.Raw)) {
            assertTrue(repository.setCameraFormat(format).isSuccess)
            assertEquals(format, repository.getCameraFormat().getOrThrow())
        }
        assertTrue("written to disk, not held in memory", dataStoreFile().exists())
    }

    @Test
    fun `a stored name this build does not know falls back to JPG and is logged`() {
        ShadowLog.clear()
        assertEquals(CameraFormat.Raw, cameraFormatFromStored("Raw").getOrThrow())
        assertEquals(CameraFormat.Jpg, cameraFormatFromStored(null).getOrThrow())
        assertEquals(CameraFormat.Jpg, cameraFormatFromStored("Heif").getOrThrow())
        assertEquals(
            listOf("Unknown stored camera format 'Heif'; using Jpg, the default."),
            ShadowLog.getLogsForTag("SettingsDataStore").filter { it.type == android.util.Log.WARN }.map { it.msg },
        )
    }
}
