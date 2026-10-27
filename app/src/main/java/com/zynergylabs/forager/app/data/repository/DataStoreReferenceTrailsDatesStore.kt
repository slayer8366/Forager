package com.zynergylabs.forager.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zynergylabs.forager.app.domain.SettingsResetListener
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsDatesStore
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsSourceDates
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.first

/**
 * [ReferenceTrailsDatesStore] in its own DataStore file, built through [settingsDataStore] as every settings file is
 * (namespaced keys, a per-instance store; CLAUDE.md, the Room/DataStore pitfall). Each date is kept as its ISO text;
 * a missing date is a missing key. [KEY_KEPT] marks that a copy exists, so a copy with both dates missing still reads
 * as a copy, not as "never online".
 *
 * A corrupt file is reset and logged by [settingsDataStore] but not announced to the user ([SettingsResetListener.None]):
 * it holds no choice of theirs, only a copy the next fetch replaces.
 */
class DataStoreReferenceTrailsDatesStore(context: Context) : ReferenceTrailsDatesStore {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, SettingsResetListener.None)

    override suspend fun read(): Result<ReferenceTrailsSourceDates?> = runCatchingCancellable {
        val prefs = dataStore.data.first()
        if (prefs[KEY_KEPT] != true) {
            null
        } else {
            ReferenceTrailsSourceDates(forestService = storedDate(prefs[KEY_FS_FETCHED], "Forest Service"), blm = storedDate(prefs[KEY_BLM_FETCHED], "BLM"))
        }
    }

    override suspend fun write(dates: ReferenceTrailsSourceDates): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs ->
            prefs[KEY_KEPT] = true
            val fs = dates.forestService
            if (fs != null) prefs[KEY_FS_FETCHED] = fs.toString() else prefs.remove(KEY_FS_FETCHED)
            val blm = dates.blm
            if (blm != null) prefs[KEY_BLM_FETCHED] = blm.toString() else prefs.remove(KEY_BLM_FETCHED)
        }
        Unit
    }

    private fun storedDate(stored: String?, source: String): LocalDate? {
        if (stored == null) return null
        return try {
            LocalDate.parse(stored)
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "The kept $source date '$stored' is not a date; it is left out.", e)
            null
        }
    }

    private companion object {
        const val TAG = "ReferenceTrailsDates"
        const val DATA_STORE_NAME = "reference_trails_dates"
        val KEY_KEPT = booleanPreferencesKey("reference_trails.copy_kept")
        val KEY_FS_FETCHED = stringPreferencesKey("reference_trails.fs_fetched")
        val KEY_BLM_FETCHED = stringPreferencesKey("reference_trails.blm_fetched")
    }
}
