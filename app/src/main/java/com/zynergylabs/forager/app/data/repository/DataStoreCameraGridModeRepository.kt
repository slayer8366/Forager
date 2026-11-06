package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.SettingsResetListener
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zynergylabs.forager.app.domain.CameraGridModeRepository
import com.zynergylabs.forager.app.domain.GridMode
import kotlinx.coroutines.flow.first

/**
 * [CameraGridModeRepository] backed by Jetpack DataStore: its own file, built through
 * [PreferenceDataStoreFactory.create] rather than the process-wide delegate, for the Robolectric
 * isolation reason [DataStoreMapPreferencesRepository] records. The mode is stored by name.
 */
class DataStoreCameraGridModeRepository(
    context: Context,
    /** Told when this file was corrupt and has been reset (RECORD -660); `AppContainer` passes its notice. */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : CameraGridModeRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset)

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
 * later build's mode, read after a downgrade) is logged and is the default too (RECORD -660, the
 * owner: "Fall back and log"; it was a failure carrying the name before). Still a [Result], always a
 * success now; `DataStoreCameraGridModeRepositoryTest` asserts the fallback and the log line (RECORD -661).
 */
internal fun gridModeFromStored(stored: String?): Result<GridMode> =
    Result.success(decodeStoredName(stored, GridMode.entries, GridMode.valueOf(DEFAULT_CAMERA_GRID_MODE_NAME), "camera grid mode"))

/** Off, so an install that predates the grid sees the preview it had — see [CameraGridModeRepository]. */
const val DEFAULT_CAMERA_GRID_MODE_NAME = "Off"
