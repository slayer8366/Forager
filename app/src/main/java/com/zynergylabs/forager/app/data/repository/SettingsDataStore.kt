package com.zynergylabs.forager.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergylabs.forager.app.domain.SettingsResetListener
import kotlinx.coroutines.CoroutineScope

/**
 * The one way this app builds a settings [DataStore] (dispatch 2026-09-28-658, Amendment 1, RECORD
 * -660, item 2; scout item D10). Still per instance through [PreferenceDataStoreFactory.create], not the
 * process-wide delegate, for the Robolectric isolation reason `DataStoreMapPreferencesRepository`
 * records.
 *
 * **A corrupt file is reset, logged, and said once.** Before, no settings file had a corruption
 * handler: a file that would not parse failed every read and write of that store, every launch, and
 * never healed. Now DataStore's [ReplaceFileCorruptionHandler] replaces only that file's contents with
 * empty preferences, so every setting in it reads as its default; the reset is logged with the file's
 * name and cause; and [onReset] is told, which raises the one-time message. The owner chose this:
 * "Reset it and say so once (Recommended)". The user's choices in that one file are lost; the other
 * files are untouched.
 *
 * [scope] is the DataStore's own where the repository already took one (tests close it between
 * instances); `null` keeps DataStore's default scope, as those repositories had.
 */
internal fun settingsDataStore(
    context: Context,
    fileName: String,
    onReset: SettingsResetListener,
    scope: CoroutineScope? = null,
): DataStore<Preferences> {
    val handler = ReplaceFileCorruptionHandler<Preferences> { error: CorruptionException ->
        Log.w(TAG, "The settings file '$fileName' could not be read; reset to its defaults.", error)
        onReset.onSettingsReset(fileName)
        emptyPreferences()
    }
    val produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) }
    return if (scope != null) {
        PreferenceDataStoreFactory.create(corruptionHandler = handler, scope = scope, produceFile = produceFile)
    } else {
        PreferenceDataStoreFactory.create(corruptionHandler = handler, produceFile = produceFile)
    }
}

/**
 * A stored enum name read back (dispatch 2026-09-28-658, Amendment 1, RECORD -660, item 3; scout
 * item D3). The owner: "Fall back and log (Recommended)". Nothing stored is [default], silently, as
 * before; a name this build does not know (a later build's value read after a downgrade, a renamed
 * entry) is logged with the name and the setting, and is [default] too. The one decode every settings
 * repository uses, in place of four (a silent default, a throw, an `error(...)`, a named failure).
 * [warn] is the log, replaceable so a plain-JVM test can read it.
 */
internal fun <E : Enum<E>> decodeStoredName(
    stored: String?,
    entries: List<E>,
    default: E,
    setting: String,
    warn: (String) -> Unit = { message -> Log.w(TAG, message) },
): E {
    if (stored == null) return default
    return entries.firstOrNull { it.name == stored } ?: default.also {
        warn("Unknown stored $setting '$stored'; using ${default.name}, the default.")
    }
}

private const val TAG = "SettingsDataStore"
