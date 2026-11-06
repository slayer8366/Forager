package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.CameraFormatRepository
import com.zynergylabs.forager.app.domain.SettingsResetListener
import kotlinx.coroutines.flow.first

/**
 * [CameraFormatRepository] backed by Jetpack DataStore, built exactly as [DataStoreCameraGridModeRepository] is: its own
 * file through [settingsDataStore] (the per-instance factory, for the Robolectric isolation reason
 * [DataStoreMapPreferencesRepository] records), the format stored by name.
 *
 * Remembered across restarts because the owner asked for it (RECORD -1005, verbatim: "remember their decision, the intent
 * is to use it and having to switch it on every time can be annoying"), against the planner's first recommendation of
 * going back to JPG. What keeps a remembered RAW from surprising anyone is the size the menu shows and the low-storage
 * fallback to JPG, not a reset.
 */
class DataStoreCameraFormatRepository(
    context: Context,
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : CameraFormatRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset)

    override suspend fun getCameraFormat(): Result<CameraFormat> = runCatchingCancellable {
        dataStore.data.first()[KEY_CAMERA_FORMAT]
    }.fold(onSuccess = ::cameraFormatFromStored, onFailure = { Result.failure(it) })

    override suspend fun setCameraFormat(format: CameraFormat): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_CAMERA_FORMAT] = format.name }
    }

    private companion object {
        const val DATA_STORE_NAME = "camera_format_preferences"
        val KEY_CAMERA_FORMAT = stringPreferencesKey("camera.format")
    }
}

/** A stored name as a [CameraFormat]: never set is JPG, the first-run default (RECORD -974, -1005); a name this build does not know falls back to JPG and is logged ([decodeStoredName]). */
internal fun cameraFormatFromStored(stored: String?): Result<CameraFormat> =
    Result.success(decodeStoredName(stored, CameraFormat.entries, CameraFormat.valueOf(DEFAULT_CAMERA_FORMAT_NAME), "camera format"))

const val DEFAULT_CAMERA_FORMAT_NAME = "Jpg"
