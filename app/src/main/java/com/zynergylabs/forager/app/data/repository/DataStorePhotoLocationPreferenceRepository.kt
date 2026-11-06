package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.SettingsResetListener
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.zynergylabs.forager.app.domain.PhotoLocationPreferenceRepository
import kotlinx.coroutines.flow.first

/**
 * [PhotoLocationPreferenceRepository] backed by Jetpack DataStore — its own file, and built through
 * [PreferenceDataStoreFactory.create] rather than the process-wide `by preferencesDataStore`
 * delegate, for the Robolectric-isolation reason [DataStoreMapPreferencesRepository] records: the
 * delegate caches per process, so deleting the backing file between tests would not actually reset
 * it.
 */
class DataStorePhotoLocationPreferenceRepository(
    context: Context,
    /** Told when this file was corrupt and has been reset (RECORD -660); `AppContainer` passes its notice. */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : PhotoLocationPreferenceRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset)

    override suspend fun getAutoSaveLocationToPhotos(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_AUTO_SAVE] ?: DEFAULT_AUTO_SAVE_LOCATION_TO_PHOTOS
    }

    override suspend fun setAutoSaveLocationToPhotos(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_AUTO_SAVE] = enabled }
    }

    private companion object {
        const val DATA_STORE_NAME = "photo_location_preferences"
        val KEY_AUTO_SAVE = booleanPreferencesKey("photo_location.auto_save")
    }
}

/** On, so an install that predates the setting keeps the behaviour it had — see [PhotoLocationPreferenceRepository]'s own doc comment. */
const val DEFAULT_AUTO_SAVE_LOCATION_TO_PHOTOS = true
