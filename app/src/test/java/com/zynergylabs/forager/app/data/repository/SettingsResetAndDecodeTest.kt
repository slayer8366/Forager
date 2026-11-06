package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.BackupFrequency
import com.zynergylabs.forager.app.domain.SettingsResetNotice
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.UnitSystem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECORD -660 (Amendment 1 to dispatch -658), items 2 and 3, through the real repositories over real
 * DataStore files:
 *
 * - **A corrupt settings file is reset, and the one-time message is raised.** The file is written as
 *   bytes that are not a preferences file, as a torn write or a bad sector leaves one. Before the
 *   corruption handler, the read failed (and every later one did too); now it reads as the defaults,
 *   the notice is pending, and the file takes writes again.
 * - **An unknown stored name falls back to the default.** Planted the way a later build would have
 *   written it; before, the units read and the backup schedule read failed outright.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsResetAndDecodeTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun file(name: String) = File(context().filesDir, "datastore/$name.preferences_pb")
    private val names = listOf("app_theme_preferences", "backup_schedule_preferences", "distance_unit_preferences")

    @Before fun setUp() = names.forEach { file(it).delete() }
    @After fun tearDown() = names.forEach { file(it).delete() }

    @Test
    fun `a corrupt theme file reads as the default, raises the notice once, and takes writes again`() = runTest {
        file("app_theme_preferences").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(0x0A, 0x7F, 0x13, 0x00, 0x55, 0x2E)) }
        val notice = SettingsResetNotice()
        val repository = DataStoreAppThemePreferenceRepository(context(), settingsReset = notice)

        val read = repository.getThemeMode()

        assertTrue("the read works rather than failing: $read", read.isSuccess)
        assertEquals(AppThemeMode.SYSTEM_DEFAULT, read.getOrThrow())
        assertTrue("the one-time message is owed", notice.pending.value)
        repository.setThemeMode(AppThemeMode.DARK).getOrThrow()
        assertEquals("the reset file takes writes", AppThemeMode.DARK, repository.getThemeMode().getOrThrow())
        notice.shown()
        assertFalse(notice.pending.value)
    }

    @Test
    fun `an intact file raises nothing`() = runTest {
        val notice = SettingsResetNotice()
        val repository = DataStoreAppThemePreferenceRepository(context(), settingsReset = notice)
        repository.setThemeMode(AppThemeMode.LIGHT).getOrThrow()
        assertEquals(AppThemeMode.LIGHT, repository.getThemeMode().getOrThrow())
        assertFalse(notice.pending.value)
    }

    @Test
    fun `an unknown stored backup frequency reads as the default, not a failed read`() = runTest {
        plant("backup_schedule_preferences", "backup.frequency", "FORTNIGHTLY")

        val read = DataStoreBackupSchedulePreferences(context()).get()

        assertTrue("the read works: $read", read.isSuccess)
        assertEquals(BackupFrequency.WEEKLY, read.getOrThrow().frequency)
    }

    @Test
    fun `an unknown stored unit system reads as imperial, not a failed read`() = runTest {
        plant("distance_unit_preferences", "unit_system.selected", "NAUTICAL")

        val read = DataStoreUnitSystemPreferenceRepository(context()).getUnitSystem()

        assertTrue("the read works: $read", read.isSuccess)
        assertEquals(UnitSystem.IMPERIAL, read.getOrThrow())
    }

    @Test
    fun `the shared decode logs only a name it does not know`() {
        val logged = mutableListOf<String>()
        val warn: (String) -> Unit = { logged += it }
        assertEquals(UnitSystem.METRIC, decodeStoredName("METRIC", UnitSystem.entries, UnitSystem.IMPERIAL, "unit system", warn))
        assertEquals(UnitSystem.IMPERIAL, decodeStoredName(null, UnitSystem.entries, UnitSystem.IMPERIAL, "unit system", warn))
        assertEquals(emptyList<String>(), logged)
        assertEquals(UnitSystem.IMPERIAL, decodeStoredName("NAUTICAL", UnitSystem.entries, UnitSystem.IMPERIAL, "unit system", warn))
        assertEquals(listOf("Unknown stored unit system 'NAUTICAL'; using IMPERIAL, the default."), logged)
    }

    /** Writes [value] under [key] in [fileName] and releases the file, as `DataStoreUnitSystemPreferenceRepositoryTest` plants its legacy key. */
    private suspend fun plant(fileName: String, key: String, value: String) {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { context().preferencesDataStoreFile(fileName) },
        )
        store.edit { it[stringPreferencesKey(key)] = value }
        job.cancelAndJoin()
    }
}
