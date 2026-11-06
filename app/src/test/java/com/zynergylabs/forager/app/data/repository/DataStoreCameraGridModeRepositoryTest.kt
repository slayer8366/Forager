package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real round trip through Jetpack DataStore, the same shape and per-test file deletion as
 * [DataStorePhotoLocationPreferenceRepositoryTest] (the camera-orientation test it named was removed with that setting,
 * dispatch 2026-10-13-03), plus the fake the camera's tests use, held
 * to the same round trip so the two cannot drift apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreCameraGridModeRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/camera_grid_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    @Test
    fun `an untouched install has the grid off`() = runTest {
        assertEquals(GridMode.Off, DataStoreCameraGridModeRepository(context()).getGridMode().getOrThrow())
        assertEquals("the constant the interface documents", GridMode.Off.name, DEFAULT_CAMERA_GRID_MODE_NAME)
    }

    @Test
    fun `every mode round-trips through DataStore, and a second write replaces the first`() = runTest {
        val repository = DataStoreCameraGridModeRepository(context())
        for (mode in listOf(GridMode.Grid, GridMode.GridLevel, GridMode.Off, GridMode.GridLevel)) {
            assertTrue(repository.setGridMode(mode).isSuccess)
            assertEquals(mode, repository.getGridMode().getOrThrow())
        }
        assertTrue("written to disk, not held in memory", dataStoreFile().exists())
    }

    /**
     * Rewritten for RECORD -660/-661 (the owner: "Fall back and log"): an unknown name used to be a failed
     * read; it is now Off, the default, with one warning naming the name and the setting. Never set is
     * Off with no warning.
     */
    @Test
    fun `a stored name this build does not know falls back to Off and is logged, not silent`() {
        ShadowLog.clear()
        assertEquals(GridMode.Grid, gridModeFromStored("Grid").getOrThrow())
        assertEquals("never set is the default", GridMode.Off, gridModeFromStored(null).getOrThrow())
        assertEquals("a known or absent name logs nothing", emptyList<String>(), settingsWarnings())

        assertEquals("an unknown name is the default", GridMode.Off, gridModeFromStored("Crosshair").getOrThrow())

        assertEquals(
            listOf("Unknown stored camera grid mode 'Crosshair'; using Off, the default."),
            settingsWarnings(),
        )
    }

    private fun settingsWarnings(): List<String> =
        ShadowLog.getLogsForTag("SettingsDataStore").filter { it.type == android.util.Log.WARN }.map { it.msg }

    @Test
    fun `the fake round-trips the same way, and can be made to fail`() = runTest {
        val fake = FakeCameraGridModeRepository()
        for (mode in listOf(GridMode.Grid, GridMode.GridLevel, GridMode.Off)) {
            fake.setGridMode(mode).getOrThrow()
            assertEquals(mode, fake.getGridMode().getOrThrow())
        }
        assertEquals(3, fake.writes)

        fake.failWrites = true
        assertTrue("a write the fake was told to fail reports failure", fake.setGridMode(GridMode.Grid).isFailure)
        assertEquals("a failed write stores nothing", GridMode.Off, fake.getGridMode().getOrThrow())
        fake.failReads = true
        assertTrue("a read the fake was told to fail reports failure", fake.getGridMode().isFailure)
    }
}
