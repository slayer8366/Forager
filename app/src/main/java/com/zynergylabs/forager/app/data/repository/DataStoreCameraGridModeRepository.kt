package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergylabs.forager.app.domain.CameraGridModeRepository
import com.zynergylabs.forager.app.domain.GridMode
import kotlinx.coroutines.flow.first

/**
 * [CameraGridModeRepository] backed by Jetpack DataStore: its own file, built through
 * [PreferenceDataStoreFactory.create] rather than the process-wide delegate, for the Robolectric
 * isolation reason [DataStoreMapPreferencesRepository] records. The mode is stored by name.
 */
class DataStoreCameraGridModeRepository(context: Context) : CameraGridModeRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    override suspend fun getGridMode(): Result<GridMode> = runCatchingCancellable {
        dataStore.data.first()[KEY_GRID_MODE]
    }.fold(onSuccess = ::gridModeFromStored, onFailure = { Result.failure(it) })

    override suspend fun setGridMode(mode: GridMode): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_GRID_MODE] = mode.name }
    }

    private companion object {
        const val DATA_STORE_NAME = "camera_grid_preferences"
        val KEY_GRID_MODE = stringPreferencesKey("camera.grid_mode")
    }
}

/**
 * A stored name as a [GridMode]: never set is the default, and a name this build does not know (a
 * later build's mode, read after a downgrade) is a failure carrying the name, not a silent Off.
 */
internal fun gridModeFromStored(stored: String?): Result<GridMode> {
    if (stored == null) return Result.success(GridMode.valueOf(DEFAULT_CAMERA_GRID_MODE_NAME))
    return GridMode.entries.firstOrNull { it.name == stored }?.let { Result.success(it) }
        ?: Result.failure(IllegalStateException("Stored camera grid mode '$stored' is not one this build knows."))
}

/** Off, so an install that predates the grid sees the preview it had — see [CameraGridModeRepository]. */
const val DEFAULT_CAMERA_GRID_MODE_NAME = "Off"
