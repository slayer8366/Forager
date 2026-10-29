package com.zynergylabs.forager.app.diagnostics.walklog

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergylabs.forager.app.data.repository.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * The walk logger's own switch store (dispatch 2026-10-29-05, Amendment 1, RECORD -1810; the owner: "A small switch of
 * its own (Recommended)"). Until then the switch lived inside the synthetic forecast store, which owned the diagnostics
 * DataStore file; this build leaves the synthetic forecast out, so the switch has a store of its own on the **same file**
 * ([DATA_STORE_NAME]) and the **same key** ([KEY_WALK_LOGGER]), so a phone that had it on keeps it on. Off by default.
 *
 * A per-instance `PreferenceDataStoreFactory.create`, as `DataStoreMapPreferencesRepository` does; [scope] is DataStore's
 * default unless a test passes one to release the file. The app holds one instance per process, through [of].
 */
class DataStoreWalkLoggerSwitch(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : WalkLoggerSwitch {

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    override suspend fun isWalkLoggerEnabled(): Result<Boolean> = runCatchingCancellable { dataStore.data.first()[KEY_WALK_LOGGER] ?: false }

    override suspend fun setWalkLoggerEnabled(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_WALK_LOGGER] = false } // REVERT CHECK: the switch never stores "on".
    }

    companion object {
        const val DATA_STORE_NAME = "debug_diagnostics_preferences"
        val KEY_WALK_LOGGER = booleanPreferencesKey("diagnostics.walk_logger")

        private var holder: Context? = null
        private var held: WalkLoggerSwitch? = null

        /**
         * The app's one switch: one instance per application, so once per process on a phone, because DataStore refuses
         * a second live instance on a file. Keyed on the application object rather than a plain singleton so a test's
         * fresh Application gets a fresh store, the same lifetime the switch had when the container's store held it.
         * The logger and the Diagnostics panel both read this one.
         */
        @Synchronized
        fun of(context: Context): WalkLoggerSwitch {
            val app = context.applicationContext
            held?.let { switch -> if (holder === app) return switch }
            return DataStoreWalkLoggerSwitch(app).also {
                holder = app
                held = it
            }
        }
    }
}
