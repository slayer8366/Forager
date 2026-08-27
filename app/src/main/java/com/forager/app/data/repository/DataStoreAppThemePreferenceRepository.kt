package com.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.forager.app.domain.AppThemePreferenceRepository
import kotlinx.coroutines.flow.first

/**
 * [AppThemePreferenceRepository] backed by Jetpack DataStore — see that interface's doc comment
 * for why. Built via [PreferenceDataStoreFactory.create] directly rather than the
 * `by preferencesDataStore(name = ...)` singleton delegate, for the same Robolectric
 * test-isolation reason [DataStoreMapPreferencesRepository] documents on itself.
 */
class DataStoreAppThemePreferenceRepository(context: Context) : AppThemePreferenceRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    override suspend fun getDarkTheme(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_DARK_THEME] ?: false
    }

    override suspend fun setDarkTheme(dark: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_DARK_THEME] = dark }
    }

    private companion object {
        const val DATA_STORE_NAME = "app_theme_preferences"
        val KEY_DARK_THEME = booleanPreferencesKey("app_theme.dark")
    }
}
