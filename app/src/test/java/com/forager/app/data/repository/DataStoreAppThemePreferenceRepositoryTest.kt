package com.forager.app.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.AppThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real round trip through Jetpack DataStore (Robolectric, not a fake) — same discipline
 * [DataStoreDistanceUnitPreferenceRepositoryTest] applies to the sibling preference this mirrors.
 * Also covers the migration [DataStoreAppThemePreferenceRepository]'s own doc comment describes:
 * real installs already have this preference stored as a plain boolean (`app_theme.dark`) from
 * before [AppThemeMode.SYSTEM_DEFAULT] existed, and a read has to fall back to that key rather than
 * silently reverting an existing user's Dark/Light choice to the new default.
 *
 * [dataStoreFile] is deleted before and after every test for the same isolation reason
 * [DataStoreMapPreferencesRepositoryTest] documents on itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreAppThemePreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/app_theme_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    private fun repository() = DataStoreAppThemePreferenceRepository(context())

    @Test
    fun `nothing picked yet defaults to System Default`() = runTest {
        assertEquals(AppThemeMode.SYSTEM_DEFAULT, repository().getThemeMode().getOrThrow())
    }

    @Test
    fun `a saved mode round-trips exactly`() = runTest {
        val repository = repository()

        repository.setThemeMode(AppThemeMode.SYSTEM_DEFAULT).getOrThrow()

        assertEquals(AppThemeMode.SYSTEM_DEFAULT, repository.getThemeMode().getOrThrow())
    }

    @Test
    fun `saving a second mode replaces the first`() = runTest {
        val repository = repository()
        repository.setThemeMode(AppThemeMode.DARK).getOrThrow()

        repository.setThemeMode(AppThemeMode.LIGHT).getOrThrow()

        assertEquals(AppThemeMode.LIGHT, repository.getThemeMode().getOrThrow())
    }

    /**
     * A pre-tri-state install with `app_theme.dark = true` and no `app_theme.mode` key at all yet —
     * the exact shape every existing install's DataStore file has before this change.
     */
    @Test
    fun `a pre-existing legacy dark=true value migrates to DARK`() = runTest {
        writeLegacyDarkTheme(dark = true)

        assertEquals(AppThemeMode.DARK, repository().getThemeMode().getOrThrow())
    }

    @Test
    fun `a pre-existing legacy dark=false value migrates to LIGHT`() = runTest {
        writeLegacyDarkTheme(dark = false)

        assertEquals(AppThemeMode.LIGHT, repository().getThemeMode().getOrThrow())
    }

    /** Once the tri-state key is ever written, it wins over a stale legacy boolean, not the other way around. */
    @Test
    fun `an explicit new-key choice takes priority over a legacy boolean`() = runTest {
        writeLegacyDarkTheme(dark = true)
        val repository = repository()

        repository.setThemeMode(AppThemeMode.LIGHT).getOrThrow()

        assertEquals(AppThemeMode.LIGHT, repository.getThemeMode().getOrThrow())
    }

    /**
     * A short-lived DataStore of its own, on an explicitly cancelled [CoroutineScope] — DataStore
     * itself throws if two instances are simultaneously active on the same file, so this one has to
     * be fully torn down before [repository] opens its own instance on the same
     * `app_theme_preferences` file.
     *
     * `scope.cancel()` alone only *requests* cancellation — it returns immediately, before
     * DataStore's own internal actor coroutine (launched as a child of [scope]) has necessarily
     * noticed and released its file-storage connection. That race was real, not hypothetical: it
     * passed reliably on this machine but failed deterministically in CI with
     * `IllegalStateException: There are multiple DataStores active for the same file` from the very
     * next test to construct a [DataStoreAppThemePreferenceRepository] on this same file —
     * confirmed by downloading that run's own JUnit XML rather than assumed from the summary count.
     * [cancelAndJoin] instead suspends until the cancelled job (and its children) have actually
     * finished, so the file connection is guaranteed released before this function returns.
     */
    private suspend fun writeLegacyDarkTheme(dark: Boolean) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val legacyDataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context().preferencesDataStoreFile("app_theme_preferences") },
        )
        legacyDataStore.edit { prefs -> prefs[booleanPreferencesKey("app_theme.dark")] = dark }
        scope.coroutineContext[Job]?.cancelAndJoin()
    }
}
