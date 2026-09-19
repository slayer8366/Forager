package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergylabs.forager.app.domain.CameraOrientationPreferenceRepository
import kotlinx.coroutines.flow.first

/**
 * [CameraOrientationPreferenceRepository] backed by Jetpack DataStore — its own file, built through
 * [PreferenceDataStoreFactory.create] rather than the process-wide delegate, for the Robolectric
 * isolation reason [DataStoreMapPreferencesRepository] records.
 */
class DataStoreCameraOrientationPreferenceRepository(context: Context) : CameraOrientationPreferenceRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    override suspend fun getLockCameraToPortrait(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_LOCK_TO_PORTRAIT] ?: DEFAULT_LOCK_CAMERA_TO_PORTRAIT
    }

    override suspend fun setLockCameraToPortrait(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_LOCK_TO_PORTRAIT] = enabled }
    }

    private companion object {
        const val DATA_STORE_NAME = "camera_orientation_preferences"
        val KEY_LOCK_TO_PORTRAIT = booleanPreferencesKey("camera.lock_to_portrait")
    }
}

/** Off, so an install that predates the setting keeps the camera it had — see [CameraOrientationPreferenceRepository]'s own doc comment. */
const val DEFAULT_LOCK_CAMERA_TO_PORTRAIT = false
